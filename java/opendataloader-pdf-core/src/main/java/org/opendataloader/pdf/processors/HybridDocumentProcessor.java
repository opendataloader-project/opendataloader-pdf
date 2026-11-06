/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.autotagging.PagePaints;
import org.opendataloader.pdf.contentstream.ContentStreamTokens;
import org.opendataloader.pdf.entities.DrawnTextChunk;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.hybrid.DoclingSchemaTransformer;
import org.opendataloader.pdf.hybrid.ElementMetadata;
import org.opendataloader.pdf.hybrid.HybridClient;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.opendataloader.pdf.hybrid.HybridClient.OutputFormat;
import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.hybrid.HybridSchemaTransformer;
import org.opendataloader.pdf.hybrid.TriageLogger;
import org.opendataloader.pdf.hybrid.TriageProcessor;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.utils.FileUtils;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSBase;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSDocument;
import org.verapdf.cos.COSInteger;
import org.verapdf.cos.COSName;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSReal;
import org.verapdf.cos.COSString;
import org.verapdf.operator.Operator;
import org.verapdf.parser.Operators;
import org.verapdf.pd.PDContentStream;
import org.verapdf.pd.PDPage;
import org.verapdf.pd.PDResources;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Hybrid document processor that routes pages to Java or external AI backend based on triage.
 *
 * <p>The processing flow:
 * <ol>
 *   <li>Filter all pages using ContentFilterProcessor</li>
 *   <li>Triage all pages to determine JAVA vs BACKEND routing</li>
 *   <li>Process JAVA pages using Java processors (parallel)</li>
 *   <li>Process BACKEND pages via external API (batch async)</li>
 *   <li>Merge results maintaining page order</li>
 * </ol>
 *
 * <p>The Java and Backend paths run concurrently for optimal performance.
 */
public class HybridDocumentProcessor {

    private static final Logger LOGGER = Logger.getLogger(HybridDocumentProcessor.class.getCanonicalName());

    /** The folder of the triage logs in the output folder: {@code triage/<name>.json}. */
    private static final String TRIAGE_LOG_FOLDER = "triage";

    /**
     * Stores the last hybrid server timings collected during {@link #processBackendPath}.
     * Accumulated across all chunks. Reset at the start of each {@code processDocument} call.
     */
    private static volatile JsonNode lastHybridTimings;

    /**
     * Cumulative client-side wall-clock for {@code client.convert(...)} calls during
     * the most recent {@link #processDocument} run. Includes network RTT, JSON
     * (de)serialization, and any queueing — i.e. the time the user actually waited
     * on the backend. May exceed the server-reported {@code total_ms} (which
     * counts only on-GPU work) and is the truer figure for SLA / throughput
     * reasoning. {@code null} when no backend chunks were attempted.
     */
    private static volatile Long lastHybridClientMs;

    /**
     * Stores element metadata from the most recent hybrid backend processing.
     * Reset at the start of each {@code processDocument} call.
     */
    private static volatile Map<Long, ElementMetadata> lastElementMetadata;

    /**
     * Stores the raw merged JSON returned by the hybrid backend's most recent
     * {@code HybridResponse.getJson()}. Downstream tools need the per-module raw
     * output to record alongside their own results.
     *
     * <p>Single-threaded by contract: this static is overwritten on each
     * {@code processDocument} call, so concurrent invocations would race. Callers
     * that need per-document raw JSON must serialize {@code processDocument}
     * invocations (or wrap with their own ThreadLocal).
     *
     * <p>Multi-chunk documents keep only the last chunk's JSON; single-chunk
     * documents capture the full response.
     */
    private static volatile JsonNode lastHybridRawJson;

    /**
     * Snapshot of the hybrid backend's {@code /health} response taken at the
     * start of the most recent {@link #processDocument} call. Downstream
     * tools surface this so server-side timings can be interpreted against
     * the hardware that produced them. {@code null} if hybrid was off or
     * the backend did not provide a health endpoint.
     */
    private static volatile JsonNode lastHybridHealth;

    /** Returns the hybrid server timings from the most recent {@link #processDocument} call. */
    public static JsonNode getLastHybridTimings() {
        return lastHybridTimings;
    }

    /**
     * Returns cumulative client-side wall-clock for hybrid backend calls during
     * the most recent {@link #processDocument} call, or {@code null} if hybrid
     * was off or the backend was never invoked. Always measured client-side, so
     * mock and real backends are directly comparable on the same scale.
     */
    public static Long getLastHybridClientMs() {
        return lastHybridClientMs;
    }

    /**
     * Returns the raw merged hybrid backend JSON from the most recent
     * {@link #processDocument} call, or {@code null} if hybrid was not used.
     */
    public static JsonNode getLastHybridRawJson() {
        return lastHybridRawJson;
    }

    /**
     * Returns the hybrid backend's reported {@code /health} payload
     * (hardware + models + version) from the most recent processing run,
     * or {@code null} if hybrid was off or the backend didn't report one.
     */
    public static JsonNode getLastHybridHealth() {
        return lastHybridHealth;
    }

    /** Returns the element metadata from the most recent {@link #processDocument} call. */
    public static Map<Long, ElementMetadata> getLastElementMetadata() {
        return lastElementMetadata;
    }

    /**
     * Pages per backend call, capped at the page count.
     *
     * <p>The configured chunk size comes from {@code HybridConfig} and defaults
     * to {@link org.opendataloader.pdf.hybrid.HybridConfig#DEFAULT_CHUNK_SIZE}.
     * Clamped to at least 1 so an empty page set still yields a usable loop
     * step rather than 0, which would never advance.
     *
     * @param pageCount           how many pages are going to the backend
     * @param configuredChunkSize the configured pages per backend request
     * @return pages per call, at least 1
     */
    static int effectiveChunkSize(int pageCount, int configuredChunkSize) {
        return Math.min(Math.max(configuredChunkSize, 1), Math.max(pageCount, 1));
    }

    /**
     * Font resource name reserved for the synthesized invisible OCR-fallback text (see
     * {@link #buildSynthesizedBlock}). A dedicated, always-added Standard-14 Helvetica
     * resource — not a font actually embedded in the source document — since this text is never
     * rendered visibly (render mode 3) and Standard-14 base fonts need no font-file embedding at
     * all, sidestepping re-encoding into whatever complex (e.g. CID/Identity-H) fonts the original
     * document's own visible text already uses. Namespaced to avoid any realistic collision with a
     * real document's own resource names.
     */
    private static final ASAtom OCR_FALLBACK_FONT_NAME = ASAtom.getASAtom("ODLOcrFallbackHelv");

    // PDF dictionary keys and names used to add the fallback font to a page's resources.
    private static final ASAtom FONT = ASAtom.getASAtom("Font");
    private static final ASAtom SUBTYPE = ASAtom.getASAtom("Subtype");
    private static final ASAtom TYPE1 = ASAtom.getASAtom("Type1");
    private static final ASAtom BASE_FONT = ASAtom.getASAtom("BaseFont");
    private static final ASAtom HELVETICA = ASAtom.getASAtom("Helvetica");
    private static final float OCR_FALLBACK_FONT_SIZE = 10f;

    // Text-state operators reset before the synthesized text is shown (see addTextStateReset).
    private static final String CHAR_SPACING_OPERATOR = "Tc";
    private static final String WORD_SPACING_OPERATOR = "Tw";
    private static final String HORIZONTAL_SCALING_OPERATOR = "Tz";
    private static final String TEXT_RISE_OPERATOR = "Ts";

    private HybridDocumentProcessor() {
        // Static utility class
    }

    /**
     * Processes a document using hybrid mode with triage-based routing. With
     * {@link org.opendataloader.pdf.hybrid.HybridConfig#isWriteTriageLog()}, the triage decision of each page goes
     * to {@code triage/<name>.json} in the output folder.
     *
     * @param inputPdfName   The path to the input PDF file.
     * @param config         The configuration settings.
     * @param pagesToProcess The set of 0-indexed page numbers to process, or null for all pages.
     * @return List of IObject lists, one per page.
     * @throws IOException If an error occurs during processing.
     */
    public static List<List<IObject>> processDocument(
            String inputPdfName,
            Config config,
            Set<Integer> pagesToProcess) throws IOException {
        Path triageLog = config.getHybridConfig().isWriteTriageLog() ? getTriageLogPath(inputPdfName, config) : null;
        return processDocumentWithTriageLog(inputPdfName, config, pagesToProcess, triageLog);
    }

    /**
     * Processes a document using hybrid mode with triage-based routing and optional triage logging.
     *
     * @param inputPdfName   The path to the input PDF file.
     * @param config         The configuration settings.
     * @param pagesToProcess The set of 0-indexed page numbers to process, or null for all pages.
     * @param outputDir      The output directory for triage logging, or null to skip logging.
     * @return List of IObject lists, one per page.
     * @throws IOException If an error occurs during processing.
     */
    public static List<List<IObject>> processDocument(
            String inputPdfName,
            Config config,
            Set<Integer> pagesToProcess,
            Path outputDir) throws IOException {
        return processDocumentWithTriageLog(inputPdfName, config, pagesToProcess,
            outputDir == null ? null : outputDir.resolve(TriageLogger.DEFAULT_FILENAME));
    }

    /**
     * Returns the triage log of the document: {@code triage/<name>.json} in the output folder. Each document has
     * its own log, so the documents of one run do not write over each other's logs (#741). The folder keeps the
     * logs apart from the outputs: {@code <name>_triage.json} is also the JSON output of {@code <name>_triage.pdf}.
     */
    private static Path getTriageLogPath(String inputPdfName, Config config) {
        Path input = Path.of(inputPdfName).toAbsolutePath();
        Path inputName = input.getFileName();
        String fileName = inputName != null ? inputName.toString() : "document";
        String name = FileUtils.getOutputBaseName(fileName);
        Path folder = config.getOutputFolder() != null ? Path.of(config.getOutputFolder()) : input.getParent();
        Path logs = folder != null ? folder.resolve(TRIAGE_LOG_FOLDER) : Path.of(TRIAGE_LOG_FOLDER);
        return logs.resolve(name + ".json");
    }

    private static List<List<IObject>> processDocumentWithTriageLog(
            String inputPdfName,
            Config config,
            Set<Integer> pagesToProcess,
            Path triageLog) throws IOException {

        lastHybridTimings = null; // Reset for this processing run
        lastElementMetadata = null;
        lastHybridRawJson = null;
        lastHybridHealth = null;
        lastHybridClientMs = null;

        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        LOGGER.log(Level.INFO, "Starting hybrid processing for {0} pages", totalPages);

        if (pagesToProcess != null && pagesToProcess.isEmpty()) {
            LOGGER.log(Level.INFO, "Skipping hybrid processing because no valid pages were selected");
            return createEmptyContents(totalPages);
        }

        // Phase 0: Check backend availability before any processing.
        // Runs before triage intentionally — if the user explicitly requested hybrid mode,
        // they expect the server to be available regardless of how pages would be routed.
        // When the health check fails and --hybrid-fallback is enabled, route every page
        // through the Java path instead of aborting the whole run (PDFDLOSP-21).
        try {
            getClient(config).checkAvailability();
        } catch (IOException e) {
            if (config.getHybridConfig().isFallbackToJava()) {
                LOGGER.log(Level.WARNING,
                    "Hybrid backend unavailable; falling back to Java-only processing: {0}",
                    e.getMessage());
                return processAllPagesAsJavaFallback(
                    inputPdfName, config, pagesToProcess, totalPages);
            }
            throw e;
        }

        // Phase 1: Filter all pages and collect filtered contents
        Map<Integer, List<IObject>> filteredContents = filterAllPages(inputPdfName, config, pagesToProcess, totalPages);

        // Phase 2: Triage all pages (or skip if full mode)
        Map<Integer, TriageResult> triageResults;
        if (config.getHybridConfig().isFullMode()) {
            // Full mode: skip triage, route all pages to backend
            LOGGER.log(Level.INFO, "Hybrid mode=full: skipping triage, all pages to backend");
            triageResults = new HashMap<>();
            for (int pageNumber : filteredContents.keySet()) {
                if (shouldProcessPage(pageNumber, pagesToProcess)) {
                    triageResults.put(pageNumber,
                        TriageResult.backend(pageNumber, 1.0, TriageProcessor.TriageSignals.empty()));
                }
            }
        } else {
            // Auto mode: dynamic triage based on page content
            triageResults = TriageProcessor.triageAllPages(
                filteredContents, config.getHybridConfig()
            );
        }

        // Log triage summary
        logTriageSummary(triageResults);

        // Log triage results to JSON file if a triage log is specified
        if (triageLog != null) {
            logTriageToFile(inputPdfName, config.getHybrid(), triageResults, triageLog);
        }

        // Phase 3: Split pages by decision
        Set<Integer> javaPages = filterByDecision(triageResults, TriageDecision.JAVA);
        Set<Integer> backendPages = filterByDecision(triageResults, TriageDecision.BACKEND);

        LOGGER.log(Level.INFO, "Routing: {0} pages to Java, {1} pages to Backend",
            new Object[]{javaPages.size(), backendPages.size()});

        // Phase 4: Process sequentially (Java first, then backend)
        List<List<IObject>> contents = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            contents.add(new ArrayList<>());
        }

        // Process Java path first
        Map<Integer, List<IObject>> javaResults = processJavaPath(
            filteredContents, javaPages, config, totalPages
        );

        // Process backend path (synchronous)
        Map<Integer, List<IObject>> backendResults;
        Set<Integer> backendFailedPages = new HashSet<>();
        // Track SemanticPicture→EnrichedImageChunk swaps so we can rekey
        // ElementMetadata after Phase 6 cross-page processors (HeaderFooter,
        // List, etc.) re-run setIDs and mutate the picture's structure id.
        Map<EnrichedImageChunk, Long> pictureSwapOriginalIds = new IdentityHashMap<>();
        try {
            backendResults = processBackendPath(inputPdfName, backendPages, config, backendFailedPages);
            // Enrich backend results: copy StreamInfos from Java-extracted content for MCID linkage
            enrichBackendResults(backendResults, filteredContents, config.getHybridConfig(),
                config.isGenerateTaggedPDF(), pictureSwapOriginalIds);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Backend processing failed: {0}", e.getMessage());
            if (config.getHybridConfig().isFallbackToJava()) {
                LOGGER.log(Level.INFO, "Falling back to Java processing for backend pages");
                backendResults = processJavaPath(filteredContents, backendPages, config, totalPages);
            } else {
                throw new IOException("Backend processing failed and fallback is disabled", e);
            }
        }

        // Fallback: reprocess backend-failed pages through Java path
        if (!backendFailedPages.isEmpty()) {
            // Log 1-indexed page numbers for human readability
            List<Integer> failedPages1Indexed = backendFailedPages.stream()
                .map(p -> p + 1).sorted().collect(Collectors.toList());
            if (config.getHybridConfig().isFallbackToJava()) {
                LOGGER.log(Level.WARNING, "Backend returned partial_success: {0} page(s) failed (pages {1}), falling back to Java path",
                    new Object[]{backendFailedPages.size(), failedPages1Indexed});
                Map<Integer, List<IObject>> fallbackResults = processJavaPath(
                    filteredContents, backendFailedPages, config, totalPages
                );
                backendResults.putAll(fallbackResults);
            } else {
                LOGGER.log(Level.WARNING, "Backend returned partial_success: {0} page(s) failed (pages {1}), fallback disabled — failing fast",
                    new Object[]{backendFailedPages.size(), failedPages1Indexed});
                failFastIfBackendFailedWithoutFallback(backendFailedPages, config.getHybridConfig());
            }
        }

        // Phase 5: Merge results
        mergeResults(contents, javaResults, backendResults, pagesToProcess, totalPages);

        // Phase 6: Post-processing (cross-page operations)
        postProcess(contents, config, pagesToProcess, totalPages);

        // Phase 7: Final metadata rekey. setIDs runs inside HeaderFooterProcessor /
        // ListProcessor / TableBorderProcessor during Phase 6 and may rewrite the
        // structure id of any IObject on the page — including EnrichedImageChunks
        // we just created from SemanticPicture. Without this final pass, an image
        // node downstream of a list (or header/footer container that triggers
        // setIDs) drops ai_score / source label / caption metadata.
        if (!pictureSwapOriginalIds.isEmpty() && lastElementMetadata != null
                && !lastElementMetadata.isEmpty()) {
            Map<Long, Long> oldToFinal = new HashMap<>(pictureSwapOriginalIds.size());
            for (Map.Entry<EnrichedImageChunk, Long> e : pictureSwapOriginalIds.entrySet()) {
                Long oldId = e.getValue();
                Long finalId = e.getKey().getRecognizedStructureId();
                if (oldId != null && finalId != null && !oldId.equals(finalId)) {
                    oldToFinal.put(oldId, finalId);
                }
            }
            if (!oldToFinal.isEmpty()) {
                Map<Long, ElementMetadata> rebuilt = new HashMap<>(lastElementMetadata);
                // Two-phase apply: first detach every (oldId → meta) we plan
                // to move, then re-attach under finalId. This prevents a
                // finalId that coincides with another picture's oldId from
                // clobbering unrelated metadata.
                Map<Long, ElementMetadata> detached = new HashMap<>(oldToFinal.size());
                for (Long oldId : oldToFinal.keySet()) {
                    ElementMetadata meta = rebuilt.remove(oldId);
                    if (meta != null) {
                        detached.put(oldId, meta);
                    }
                }
                for (Map.Entry<Long, Long> e : oldToFinal.entrySet()) {
                    Long oldId = e.getKey();
                    ElementMetadata meta = detached.get(oldId);
                    if (meta == null) continue;
                    Long finalId = e.getValue();
                    if (rebuilt.containsKey(finalId)) {
                        // Another node already owns finalId after setIDs ran.
                        // Re-attaching here would silently overwrite that
                        // node's metadata. Try to restore the detached entry
                        // under its original key — but only if oldId is also
                        // unowned. With HashMap iteration order, an earlier
                        // iteration in this same loop may have migrated some
                        // other picture *into* oldId (its finalId == this
                        // oldId), and rolling back would clobber that
                        // legitimately-migrated entry. Permanent metadata
                        // loss is the lesser evil there: the WARNING flags
                        // it for investigation.
                        if (!rebuilt.containsKey(oldId)) {
                            LOGGER.log(Level.WARNING,
                                "PhaseRekey: finalId {0} already owned in metadata map, keeping picture entry at oldId {1} (alt may not surface in JSON output)",
                                new Object[]{finalId, oldId});
                            rebuilt.put(oldId, meta);
                        } else {
                            LOGGER.log(Level.WARNING,
                                "PhaseRekey: both finalId {0} and oldId {1} already owned in metadata map; dropping picture metadata to preserve migrated entries",
                                new Object[]{finalId, oldId});
                        }
                        continue;
                    }
                    rebuilt.put(finalId, meta);
                }
                lastElementMetadata = Collections.unmodifiableMap(rebuilt);
            }
        }

        return contents;
    }

    /**
     * Runs the full document through the Java path when the hybrid backend health check
     * fails and {@code --hybrid-fallback} is enabled. Mirrors the structure of
     * {@link #processDocument} so the caller still gets a per-page result list, but
     * skips triage and the backend chunk loop entirely.
     */
    private static List<List<IObject>> processAllPagesAsJavaFallback(
            String inputPdfName,
            Config config,
            Set<Integer> pagesToProcess,
            int totalPages) throws IOException {

        Map<Integer, List<IObject>> filteredContents =
            filterAllPages(inputPdfName, config, pagesToProcess, totalPages);

        Set<Integer> allPages = new HashSet<>();
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (shouldProcessPage(pageNumber, pagesToProcess)) {
                allPages.add(pageNumber);
            }
        }

        Map<Integer, List<IObject>> javaResults =
            processJavaPath(filteredContents, allPages, config, totalPages);

        List<List<IObject>> contents = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            contents.add(new ArrayList<>());
        }
        mergeResults(contents, javaResults, new HashMap<>(), pagesToProcess, totalPages);
        postProcess(contents, config, pagesToProcess, totalPages);
        return contents;
    }

    private static List<List<IObject>> createEmptyContents(int totalPages) {
        List<List<IObject>> contents = new ArrayList<>(totalPages);
        for (int i = 0; i < totalPages; i++) {
            contents.add(new ArrayList<>());
        }
        return contents;
    }

    /**
     * Fails fast when the backend left pages unprocessed and fallback to Java is disabled.
     * Without this, the CLI would exit 0 with a sparse JSON that drops failed pages,
     * making backend failures invisible to automation.
     *
     * @param backendFailedPages 0-indexed pages that the backend failed to process.
     * @param hybridConfig       Hybrid configuration; consulted for the fallback flag.
     * @throws IOException if {@code backendFailedPages} is non-empty and
     *                     {@code hybridConfig.isFallbackToJava()} returns false. The
     *                     exception message lists the 1-indexed failed page numbers.
     */
    static void failFastIfBackendFailedWithoutFallback(
            Set<Integer> backendFailedPages,
            HybridConfig hybridConfig) throws IOException {
        Objects.requireNonNull(backendFailedPages, "backendFailedPages");
        Objects.requireNonNull(hybridConfig, "hybridConfig");
        if (backendFailedPages.isEmpty() || hybridConfig.isFallbackToJava()) {
            return;
        }
        List<Integer> failedPages1Indexed = backendFailedPages.stream()
            .map(p -> p + 1).sorted().collect(Collectors.toList());
        throw new IOException(String.format(
            "Backend processing failed for %d page(s) with fallback disabled: pages %s",
            backendFailedPages.size(), failedPages1Indexed));
    }

    /**
     * Filters all pages using ContentFilterProcessor.
     */
    private static Map<Integer, List<IObject>> filterAllPages(
            String inputPdfName,
            Config config,
            Set<Integer> pagesToProcess,
            int totalPages) throws IOException {

        Map<Integer, List<IObject>> filteredContents = new HashMap<>();

        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                filteredContents.put(pageNumber, new ArrayList<>());
                continue;
            }

            List<IObject> pageContents = ContentFilterProcessor.getFilteredContents(
                inputPdfName,
                StaticContainers.getDocument().getArtifacts(pageNumber),
                pageNumber,
                config
            );
            filteredContents.put(pageNumber, pageContents);
        }

        return filteredContents;
    }

    /**
     * Filters triage results by decision type.
     */
    private static Set<Integer> filterByDecision(
            Map<Integer, TriageResult> triageResults,
            TriageDecision decision) {

        return triageResults.entrySet().stream()
            .filter(e -> e.getValue().getDecision() == decision)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    /**
     * Processes pages using the Java processing path.
     */
    private static Map<Integer, List<IObject>> processJavaPath(
            Map<Integer, List<IObject>> filteredContents,
            Set<Integer> pageNumbers,
            Config config,
            int totalPages) {

        if (pageNumbers.isEmpty()) {
            return new HashMap<>();
        }

        LOGGER.log(Level.FINE, "Processing {0} pages via Java path", pageNumbers.size());

        // Create a working copy of contents for Java processing
        List<List<IObject>> workingContents = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            if (pageNumbers.contains(i)) {
                workingContents.add(new ArrayList<>(filteredContents.get(i)));
            } else {
                workingContents.add(new ArrayList<>());
            }
        }

        // Apply cluster table processing if enabled
        if (config.isClusterTableMethod()) {
            new ClusterTableProcessor().processTables(workingContents);
        }

        // Process each page through the standard Java pipeline
        // Note: Sequential processing is required because StaticContainers uses ThreadLocal
        for (int pageNumber : pageNumbers) {
            try {
                List<IObject> pageContents = workingContents.get(pageNumber);
                TextDecorationProcessor.processStrikethroughAndUnderlinedText(pageContents, pageNumber, config.isDetectStrikethrough());
                pageContents = TableBorderProcessor.processTableBorders(pageContents, pageNumber);
                pageContents = pageContents.stream()
                    .filter(x -> !(x instanceof LineChunk))
                    .collect(Collectors.toList());
                pageContents = TextLineProcessor.processTextLines(pageContents);
                pageContents = SpecialTableProcessor.detectSpecialTables(pageContents);
                workingContents.set(pageNumber, pageContents);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing page {0}: {1}",
                    new Object[]{pageNumber, e.getMessage()});
            }
        }

        // Apply cross-page processing for Java pages only
        applyJavaPagePostProcessing(workingContents, pageNumbers);

        // Extract results
        Map<Integer, List<IObject>> results = new HashMap<>();
        for (int pageNumber : pageNumbers) {
            results.put(pageNumber, workingContents.get(pageNumber));
        }

        return results;
    }

    /**
     * Applies post-processing to Java-processed pages.
     */
    private static void applyJavaPagePostProcessing(List<List<IObject>> contents, Set<Integer> pageNumbers) {
        // Process paragraphs, lists, and headings for each page
        for (int pageNumber : pageNumbers) {
            List<IObject> pageContents = contents.get(pageNumber);
            pageContents = ParagraphProcessor.processParagraphs(pageContents);
            pageContents = ListProcessor.processListsFromTextNodes(pageContents);
            HeadingProcessor.processHeadings(pageContents, false);
            DocumentProcessor.setIDs(pageContents);
            CaptionProcessor.processCaptions(pageContents);
            contents.set(pageNumber, pageContents);
        }
    }

    /**
     * Processes pages using the external backend.
     *
     * @param inputPdfName     The path to the input PDF file.
     * @param pageNumbers      Set of 0-indexed page numbers to process.
     * @param config           The configuration settings.
     * @param backendFailedPages Output parameter: populated with 0-indexed page numbers that
     *                           failed during backend processing (e.g., due to Invalid code point).
     *                           These pages can be retried via the Java processing path.
     * @return Map of page number to IObject list for successfully processed pages.
     * @throws IOException If an error occurs during processing.
     */
    private static Map<Integer, List<IObject>> processBackendPath(
            String inputPdfName,
            Set<Integer> pageNumbers,
            Config config,
            Set<Integer> backendFailedPages) throws IOException {

        if (pageNumbers.isEmpty()) {
            return new HashMap<>();
        }

        LOGGER.log(Level.INFO, "Processing {0} pages via {1} backend",
            new Object[]{pageNumbers.size(), config.getHybrid()});

        // Get or create cached client
        HybridClient client = getClient(config);

        // Best-effort: snapshot backend health (hardware, models, version)
        // so downstream tooling can interpret server timings against the
        // environment that produced them. Narrow catch to Exception so
        // JVM-fatal errors (OutOfMemoryError etc.) still propagate.
        try {
            lastHybridHealth = client.fetchHealth();
        } catch (Exception e) {
            lastHybridHealth = null;
            LOGGER.log(Level.FINE, "fetchHealth failed");
        }

        // Read PDF bytes
        byte[] pdfBytes = Files.readAllBytes(Path.of(inputPdfName));

        // Determine required output formats based on config
        Set<OutputFormat> outputFormats = determineOutputFormats(config);

        // Get page heights for coordinate transformation
        Map<Integer, Double> pageHeights = getPageHeights(pageNumbers);

        HybridSchemaTransformer transformer = createTransformer(config);
        Map<Integer, List<IObject>> results = new HashMap<>();

        // Split backend pages into chunks to prevent hang on large documents (#352).
        // Pages are sorted so that page_ranges sent to the server are contiguous.
        List<Integer> sortedPages = new ArrayList<>(new TreeSet<>(pageNumbers));
        int chunkSize = effectiveChunkSize(sortedPages.size(), config.getHybridConfig().getChunkSize());

        for (int chunkStart = 0; chunkStart < sortedPages.size(); chunkStart += chunkSize) {
            int chunkEnd = Math.min(chunkStart + chunkSize, sortedPages.size());
            List<Integer> chunkPages = sortedPages.subList(chunkStart, chunkEnd);

            // Convert 0-indexed page numbers to 1-indexed for the server API
            Set<Integer> chunkPages1Indexed = new HashSet<>();
            for (int page0 : chunkPages) {
                chunkPages1Indexed.add(page0 + 1);
            }

            if (sortedPages.size() > chunkSize) {
                LOGGER.log(Level.INFO, "Sending pages {0}-{1} of {2} backend pages",
                    new Object[]{chunkPages.get(0) + 1, chunkPages.get(chunkPages.size() - 1) + 1,
                                 sortedPages.size()});
            }

            try {
                HybridRequest request = HybridRequest.forPages(pdfBytes, chunkPages1Indexed, outputFormats);
                long convertStartNs = System.nanoTime();
                HybridResponse response;
                try {
                    response = client.convert(request);
                } finally {
                    // Accumulate client wall-clock for this convert call whether
                    // it succeeded, failed with IOException, or otherwise unwound
                    // — the user waited that long regardless of outcome, and
                    // that's what this metric exists to measure (SLA / throughput).
                    // nanoTime() is monotonic; safe against wall-clock jumps
                    // (NTP / DST / manual changes).
                    long convertElapsedMs = TimeUnit.NANOSECONDS.toMillis(
                        System.nanoTime() - convertStartNs);
                    lastHybridClientMs = (lastHybridClientMs == null ? 0L : lastHybridClientMs)
                        + convertElapsedMs;
                }

                // Capture hybrid server pipeline timings (last chunk wins for now;
                // in single-chunk documents this is exact)
                if (response.getTimings() != null) {
                    lastHybridTimings = response.getTimings();
                }
                if (response.getJson() != null) {
                    lastHybridRawJson = response.getJson();
                }

                // Collect failed pages (convert from 1-indexed to 0-indexed)
                if (response.hasFailedPages()) {
                    for (int failedPage1Indexed : response.getFailedPages()) {
                        int failedPage0Indexed = failedPage1Indexed - 1;
                        if (pageNumbers.contains(failedPage0Indexed)) {
                            backendFailedPages.add(failedPage0Indexed);
                        }
                    }
                }

                // Build page heights subset for this chunk (1-indexed keys, matching getPageHeights)
                Map<Integer, Double> chunkPageHeights = new HashMap<>();
                for (int page1 : chunkPages1Indexed) {
                    Double height = pageHeights.get(page1);
                    if (height != null) {
                        chunkPageHeights.put(page1, height);
                    }
                }

                // Transform response to IObjects.
                // Contract: transform() returns a list indexed by absolute page number (pageNo - 1).
                // For chunk pages 51-100, the list has 100 entries with content at indices 50-99.
                // This matches page0 values used below for extraction.
                List<List<IObject>> transformedContents = transformer.transform(response, chunkPageHeights);

                // Extract results for this chunk's pages (excluding failed pages)
                for (int page0 : chunkPages) {
                    if (backendFailedPages.contains(page0)) {
                        continue; // Skip failed pages — they will be retried via Java path
                    }
                    if (page0 < transformedContents.size()) {
                        List<IObject> pageContents = transformedContents.get(page0);
                        TextProcessor.replaceUndefinedCharacters(pageContents, config.getReplaceInvalidChars());
                        // Capture transformer-assigned IDs before setIDs rewrites them
                        // so ElementMetadata keyed by the original ID can be migrated
                        // to the renumbered structure ID.
                        List<Long> oldIds = new ArrayList<>(pageContents.size());
                        for (IObject obj : pageContents) {
                            oldIds.add(obj.getRecognizedStructureId());
                        }
                        DocumentProcessor.setIDs(pageContents, true);
                        rekeyMetadata(transformer, oldIds, pageContents);
                        results.put(page0, pageContents);
                    } else {
                        results.put(page0, new ArrayList<>());
                    }
                }
            } catch (IOException e) {
                // Isolate chunk failures — mark pages as failed so they can be retried
                // via the Java path, and continue processing remaining chunks.
                LOGGER.log(Level.WARNING, "Backend chunk failed (pages {0}-{1}): {2}",
                    new Object[]{chunkPages.get(0) + 1, chunkPages.get(chunkPages.size() - 1) + 1,
                                 e.getMessage()});
                for (int page0 : chunkPages) {
                    backendFailedPages.add(page0);
                }
            }
        }

        // Capture element metadata and OCR words from the transformer
        lastElementMetadata = transformer.getElementMetadata();

        // Note: Client is cached and reused across documents.
        // HybridClientFactory.shutdown() should be called at CLI exit.

        return results;
    }

    /**
     * Gets or creates a hybrid client based on configuration.
     *
     * <p>Uses HybridClientFactory to cache and reuse clients across documents.
     */
    private static HybridClient getClient(Config config) {
        return HybridClientFactory.getOrCreate(config.getHybrid(), config.getHybridConfig());
    }

    /**
     * Migrate ElementMetadata entries from transformer-assigned IDs onto the
     * structure IDs that {@code setIDs} just renumbered each IObject with.
     * Without this, the metadata map keeps pointing at the throwaway IDs the
     * transformer minted and downstream metadata lookups all miss.
     */
    private static void rekeyMetadata(HybridSchemaTransformer transformer,
                                       List<Long> oldIds, List<IObject> pageContents) {
        Map<Long, Long> oldToNew = new java.util.HashMap<>(pageContents.size());
        for (int i = 0; i < pageContents.size(); i++) {
            Long oldId = oldIds.get(i);
            Long newId = pageContents.get(i).getRecognizedStructureId();
            if (oldId == null || newId == null || oldId.equals(newId)) continue;
            oldToNew.put(oldId, newId);
        }
        if (!oldToNew.isEmpty()) {
            transformer.rekeyMetadata(oldToNew);
        }
    }

    /**
     * Creates a schema transformer based on configuration.
     */
    private static HybridSchemaTransformer createTransformer(Config config) {
        String hybrid = config.getHybrid();

        if (Config.HYBRID_DOCLING_FAST.equals(hybrid)) {
            return new DoclingSchemaTransformer();
        }

        throw new IllegalArgumentException("Unsupported hybrid backend: " + hybrid);
    }

    /**
     * Gets page heights for coordinate transformation.
     */
    private static Map<Integer, Double> getPageHeights(Set<Integer> pageNumbers) {
        Map<Integer, Double> pageHeights = new HashMap<>();

        for (int pageNumber : pageNumbers) {
            BoundingBox pageBbox = DocumentProcessor.getPageBoundingBox(pageNumber);
            if (pageBbox != null) {
                pageHeights.put(pageNumber + 1, pageBbox.getHeight()); // 1-indexed for transformer
            }
        }

        return pageHeights;
    }

    /**
     * Merges Java and backend results into the final contents list.
     */
    /**
     * Enriches backend results with MCID/StreamInfo data from Java-extracted content.
     *
     * <p>Backend-generated IObjects (from docling) lack StreamInfo, which is required
     * for PDF struct-tree tagging. This method:
     * <ol>
     *   <li>Replaces SemanticPicture with EnrichedImageChunk (copies StreamInfo + description)</li>
     *   <li>Copies StreamInfo from Java TextChunks to backend TextChunks by bbox overlap</li>
     * </ol>
     *
     * @param backendResults the backend's elements per page; enriched in place
     * @param filteredContents the natively extracted content per page, which carries the StreamInfos
     * @param hybridConfig the hybrid settings
     * @param pictureSwapOriginalIds filled with the original id of each picture that is swapped
     */
    private static void enrichBackendResults(
            Map<Integer, List<IObject>> backendResults,
            Map<Integer, List<IObject>> filteredContents,
            HybridConfig hybridConfig,
            Map<EnrichedImageChunk, Long> pictureSwapOriginalIds) {
        // Kept with this signature: HybridDrawnTextTest and HybridMissedStreamTextTest look the
        // method up by reflection.
        enrichBackendResults(backendResults, filteredContents, hybridConfig, true, pictureSwapOriginalIds);
    }

    /**
     * As above; {@code generateTaggedPdf} says whether a tagged PDF is requested, which is the only
     * output that reads the page content stream the OCR-fallback synthesis rewrites (the plain PDF
     * output loads the original file again).
     *
     * @param backendResults the backend's elements per page; enriched in place
     * @param filteredContents the natively extracted content per page, which carries the StreamInfos
     * @param hybridConfig the hybrid settings
     * @param generateTaggedPdf whether the OCR-fallback text is synthesized into the content stream
     * @param pictureSwapOriginalIds filled with the original id of each picture that is swapped
     */
    private static void enrichBackendResults(
            Map<Integer, List<IObject>> backendResults,
            Map<Integer, List<IObject>> filteredContents,
            HybridConfig hybridConfig,
            boolean generateTaggedPdf,
            Map<EnrichedImageChunk, Long> pictureSwapOriginalIds) {

        for (Map.Entry<Integer, List<IObject>> entry : backendResults.entrySet()) {
            int pageNumber = entry.getKey();
            List<IObject> backendPage = entry.getValue();

            List<IObject> javaPage = filteredContents.getOrDefault(pageNumber, List.of());

            // Collect Java-extracted ImageChunks and TextChunks for matching
            List<ImageChunk> javaImageChunks = new ArrayList<>();
            List<TextChunk> javaTextChunks = new ArrayList<>();
            collectJavaChunks(javaPage, javaImageChunks, javaTextChunks);

            // Replace SemanticPicture entries with matched EnrichedImageChunk
            if (!javaImageChunks.isEmpty()) {
                List<IObject> enriched = new ArrayList<>(backendPage.size());
                for (IObject obj : backendPage) {
                    if (obj instanceof SemanticPicture) {
                        SemanticPicture picture = (SemanticPicture) obj;
                        ImageChunk matched = findMatchingImageChunk(picture, javaImageChunks);
                        if (matched != null) {
                            // Author-authored /Alt wins over AI caption. If the
                            // matched chunk is already an EnrichedImageChunk with
                            // AltSource.ORIGINAL (TaggedDocumentProcessor extracts
                            // the source PDF's /Alt that way), preserve it; the
                            // backend caption is discarded because a human-authored
                            // /Alt is more trustworthy than any AI description.
                            String alt;
                            EnrichedImageChunk.AltSource altSource;
                            if (matched instanceof EnrichedImageChunk
                                    && ((EnrichedImageChunk) matched).getAltSource()
                                        == EnrichedImageChunk.AltSource.ORIGINAL
                                    && ((EnrichedImageChunk) matched).hasDescription()) {
                                alt = ((EnrichedImageChunk) matched).getDescription();
                                altSource = EnrichedImageChunk.AltSource.ORIGINAL;
                                // Author /Alt wins; AI caption is intentionally
                                // discarded here. Logged so a future regression
                                // (e.g. whitespace-only original /Alt that
                                // passes hasDescription) is visible during
                                // hybrid-pipeline debugging.
                                if (picture.getDescription() != null
                                        && !picture.getDescription().isEmpty()) {
                                    LOGGER.log(Level.FINE,
                                        "Page {0}: kept original /Alt over AI caption (orig len={1}, ai len={2})",
                                        new Object[]{pageNumber, alt.length(), picture.getDescription().length()});
                                }
                            } else {
                                alt = picture.getDescription();
                                altSource = EnrichedImageChunk.AltSource.AI_GENERATED;
                            }
                            EnrichedImageChunk replacement = new EnrichedImageChunk(
                                matched, alt, altSource);
                            // Preserve the SemanticPicture's structure id so that
                            // ElementMetadata keyed by it (ai_score, source label,
                            // caption) survives the SemanticPicture → EnrichedImageChunk
                            // swap. Without this, downstream metadata lookups miss
                            // and the JSON output drops every picture-level metadata
                            // field except `alt`.
                            Long originalId = picture.getRecognizedStructureId();
                            replacement.setRecognizedStructureId(originalId);
                            if (originalId != null) {
                                pictureSwapOriginalIds.put(replacement, originalId);
                            }
                            enriched.add(replacement);
                        } else {
                            // No Java ImageChunk overlapped this backend Figure.
                            // We preserve the SemanticPicture rather than dropping
                            // it: dropping silently discards the backend's caption
                            // and the corresponding ai-raw FIGURE evidence, and
                            // the page would no longer mention a region the
                            // backend definitively classified as a figure.
                            // Downstream PDF struct-tree tagging tolerates a
                            // missing StreamInfo (the figure is tagged from its
                            // bounding box) but the alt text and evidence remain.
                            LOGGER.fine(() -> "Page " + pageNumber + ": kept SemanticPicture without StreamInfo (no matching Java ImageChunk) at bbox ["
                                + String.format("%.1f,%.1f,%.1f,%.1f", picture.getLeftX(), picture.getBottomY(), picture.getRightX(), picture.getTopY()) + "]");
                            enriched.add(picture);
                        }
                    } else {
                        enriched.add(obj);
                    }
                }
                backendPage = enriched;
                entry.setValue(enriched);
            }

            // Replace backend TextChunks with Java TextChunks that carry StreamInfo,
            // and copy StreamInfos to SemanticFormula objects
            // Run even with no Java text: a page that draws its words as outlines
            // has none, and its nodes still take the outlines they lie over.
            Set<Integer> usedJavaIndices =
                enrichTextStreamInfos(backendPage, javaTextChunks, new PagePaints(pageNumber));
            if (!javaTextChunks.isEmpty()) {
                enrichFormulaStreamInfos(backendPage, javaTextChunks);
                List<IObject> missed = paragraphsOfTextTheBackendMissed(backendPage, javaTextChunks, usedJavaIndices);
                if (!missed.isEmpty()) {
                    backendPage = new ArrayList<>(backendPage);
                    backendPage.addAll(missed);
                    entry.setValue(backendPage);
                }
            }

            final int pg = pageNumber;
            final int javaTotal = javaTextChunks.size();
            LOGGER.fine(() -> "Page " + pg + ": enrichment complete — "
                + javaTotal + " Java TextChunks available");
        }

        // OCR fallback: synthesize real (invisible) content for backend elements that still
        // lack StreamInfo after enrichment — see synthesizeInvisibleTextForOcrFallback.
        // Only when a tagged PDF is requested: the JSON, Markdown, HTML and text writers, and the
        // plain PDF writer (it loads the original file), do not read the rewritten content stream,
        // so rewriting it would be wasted work (and a risk).
        // A second pass, after every page is enriched: if enriching a later page fails, the caller
        // falls back to the Java path, which has to find the content streams as they were.
        if (generateTaggedPdf) {
            for (Map.Entry<Integer, List<IObject>> entry : backendResults.entrySet()) {
                synthesizeInvisibleTextForOcrFallback(entry.getValue(), entry.getKey());
            }
        }
    }

    /**
     * The tokens that show one OCR-fallback chunk as invisible text, {@code q BT /ODLOcrFallbackHelv
     * 10 Tf 3 Tr 1 0 0 1 x y Tm (text) Tj ET Q}. They are built before the page is changed, so the
     * shift an insertion causes in the tokens after it is the length of the block itself, with no
     * count to keep in step with the code that builds it.
     */
    private static final class SynthesizedBlock {
        final TextChunk chunk;
        final List<Object> tokens;
        /** Index of the {@code Tj} operator within {@link #tokens}. */
        final int showOperatorIndex;
        /** Length of the text shown, which is the length of the chunk's {@link StreamInfo}. */
        final int textLength;

        SynthesizedBlock(TextChunk chunk, List<Object> tokens, int showOperatorIndex, int textLength) {
            this.chunk = chunk;
            this.tokens = tokens;
            this.showOperatorIndex = showOperatorIndex;
            this.textLength = textLength;
        }
    }

    /** Text render mode 3: neither filled, stroked nor clipped, so the text is not drawn. */
    private static final int INVISIBLE_TEXT_RENDER_MODE = 3;

    /** The non-embedded Standard-14 font encodes printable ASCII, 0x20 to 0x7E, and nothing wider. */
    private static final char PRINTABLE_ASCII_MIN = 0x20;
    private static final char PRINTABLE_ASCII_MAX = 0x7E;
    private static final char REPLACEMENT_CHAR = '?';

    /** Path-construction operators — starting or extending a path that isn't painted/cleared yet. */
    private static final Set<String> PATH_CONSTRUCTION_OPERATORS = Set.of(
        Operators.M_MOVE_TO, Operators.L_LINE_TO, Operators.C_CURVE_TO, Operators.V, Operators.Y,
        Operators.H_CLOSEPATH, Operators.RE);

    /**
     * Path-painting (and no-op path-clearing, {@code n}) operators — every operator that consumes
     * and terminates whatever path is currently under construction.
     */
    private static final Set<String> PATH_PAINTING_OPERATORS = Set.of(
        Operators.S_STROKE, Operators.S_CLOSE_STROKE, Operators.F_FILL, Operators.F_FILL_OBSOLETE,
        Operators.F_STAR_FILL, Operators.B_FILL_STROKE, Operators.B_STAR_EOFILL_STROKE,
        Operators.B_CLOSEPATH_FILL_STROKE, Operators.B_STAR_CLOSEPATH_EOFILL_STROKE, Operators.N);

    /**
     * Walks the IObject tree (see {@link #forEachIObject}) collecting every TextChunk in
     * reading order, and for each one that still has no StreamInfo after enrichment, records where
     * a synthesized invisible operator for it should be inserted into the page's content stream —
     * immediately after the nearest preceding TextChunk that does have real StreamInfo (i.e., the
     * closest native/already-tagged content before it in reading order), or at the very start of
     * the stream if no such content precedes it on the page.
     *
     * <p>The operators go next to their anchor, not at the end of the stream: anything that reads
     * the raw content-stream order (a plain text extractor, "select all" in a viewer) then sees the
     * fallback text where it belongs on the page. All of a page's insertions are made in one
     * rewrite of the content stream (see {@link #rewritePageContentStream}).
     *
     * @param objects the page's content tree after enrichment; chunks without a StreamInfo get one
     * @param pageNumber the page the tree belongs to
     */
    private static void synthesizeInvisibleTextForOcrFallback(List<IObject> objects, int pageNumber) {
        try {
            addOcrFallbackText(objects, pageNumber);
        } catch (IOException | RuntimeException e) {
            // Nothing the tagger reads is changed until a page's new content stream exists, so a page
            // that fails is skipped as it was. Nothing may escape from here: the caller would fall back
            // to the Java path for every page, with the pages already done rewritten.
            LOGGER.log(Level.WARNING,
                "Failed to synthesize invisible text for OCR-fallback chunks on page {0}: {1}",
                new Object[]{pageNumber, e.getMessage()});
        }
    }

    /**
     * Finds the chunks of one page that need invisible text and writes it into the page's content
     * stream; see {@link #synthesizeInvisibleTextForOcrFallback}.
     *
     * @param objects the page's content tree after enrichment
     * @param pageNumber the page the tree belongs to
     * @throws IOException if the new content stream cannot be written
     */
    private static void addOcrFallbackText(List<IObject> objects, int pageNumber) throws IOException {
        List<TextChunk> orderedChunks = new ArrayList<>();
        // By identity: a chunk reached twice must get one operator, not two.
        Set<TextChunk> seenChunks = Collections.newSetFromMap(new IdentityHashMap<>());
        forEachIObject(objects, obj -> {
            if (obj instanceof TextChunk && seenChunks.add((TextChunk) obj)) {
                orderedChunks.add((TextChunk) obj);
            }
        });

        Map<Integer, List<TextChunk>> pendingByInsertIndex = new HashMap<>();
        int anchorTokenIndex = 0;
        for (TextChunk chunk : orderedChunks) {
            List<StreamInfo> streamInfos = chunk.getStreamInfos();
            if (!streamInfos.isEmpty()) {
                int anchorOperatorIndex = -1;
                for (StreamInfo streamInfo : streamInfos) {
                    if (streamInfo.getXObjectName() == null) {
                        anchorOperatorIndex = Math.max(anchorOperatorIndex, streamInfo.getOperatorIndex());
                    }
                }
                if (anchorOperatorIndex >= 0) {
                    anchorTokenIndex = anchorOperatorIndex + 1;
                }
                continue;
            }
            if (toAsciiOrReplacementChar(chunk.getValue()).isEmpty()) {
                continue;
            }
            // The position comes from the backend's box: a NaN or infinite value would write an
            // invalid number into the content stream.
            if (!Double.isFinite(chunk.getLeftX()) || !Double.isFinite(chunk.getBottomY())) {
                continue;
            }
            pendingByInsertIndex.computeIfAbsent(anchorTokenIndex, k -> new ArrayList<>()).add(chunk);
        }

        if (pendingByInsertIndex.isEmpty()) {
            return;
        }
        rewritePageContentStream(pageNumber, objects, pendingByInsertIndex);
    }

    /**
     * Visits every IObject reachable from {@code objects} — each top-level node itself, plus
     * (recursively) SemanticTextNode's TextChunks, TableBorder cells' contents in row/column order,
     * and PDFList items' nested contents. {@code getStreamInfos()} is declared on {@code IObject}
     * itself, so this single generic walk is enough both to collect TextChunks (see
     * {@link #synthesizeInvisibleTextForOcrFallback}) and to reach every StreamInfo-bearing object
     * on a page — text, image, and formula alike — when that page's content stream is about to be
     * rewritten (see {@link #rewritePageContentStream}).
     *
     * <p>These are the containers {@link #enrichTextStreamInfosRecursive} walks, and that is what
     * gives a chunk its StreamInfo. A node enrichment does not enter, such as the header or footer
     * node the backend returns for a page header (a {@code SemanticHeaderOrFooter}), holds no
     * StreamInfo for this walk to shift or to anchor on, so skipping it is consistent. A new
     * container type has to be added to both walks.
     *
     * @param objects the nodes to walk
     * @param visitor called once for each object reached
     */
    private static void forEachIObject(List<IObject> objects, Consumer<IObject> visitor) {
        for (IObject obj : objects) {
            visitor.accept(obj);
            if (obj instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) obj;
                for (TextColumn col : textNode.getColumns()) {
                    for (TextLine line : col.getLines()) {
                        for (TextChunk chunk : line.getTextChunks()) {
                            visitor.accept(chunk);
                        }
                    }
                }
            } else if (obj instanceof TableBorder) {
                TableBorder table = (TableBorder) obj;
                for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
                    TableBorderRow row = table.getRow(rowNumber);
                    for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                        TableBorderCell cell = row.getCell(colNumber);
                        // A merged cell is returned at every grid position it covers: visit it once, at its origin.
                        if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                            forEachIObject(cell.getContents(), visitor);
                        }
                    }
                }
            } else if (obj instanceof PDFList) {
                PDFList list = (PDFList) obj;
                for (ListItem item : list.getListItems()) {
                    // ListItem (via TextBlock) can hold TextChunks directly in getLines(), separate
                    // from getContents() — miss these and their OCR-fallback text/StreamInfo remap
                    // never happens.
                    for (TextLine line : item.getLines()) {
                        for (TextChunk chunk : line.getTextChunks()) {
                            visitor.accept(chunk);
                        }
                    }
                    forEachIObject(item.getContents(), visitor);
                }
            }
        }
    }

    /**
     * Rewrites a page's content stream once, splicing a synthesized, invisible (render mode 3)
     * text-showing operator sequence in for each pending OCR-fallback chunk, at the token position
     * {@link #synthesizeInvisibleTextForOcrFallback} anchored it to, and gives each chunk a
     * {@link StreamInfo} pointing at its operator in the rewritten stream. The tagger then wraps
     * these operators like native content, so it needs no change.
     *
     * <p>An insertion shifts the token index of everything after it. Every pre-existing
     * {@link StreamInfo} of the page's main content stream is therefore moved to where its operator
     * lands (one scoped to a Form XObject, with a non-null {@code xObjectName}, lives in another
     * stream and is left alone). Left stale, these indices tag the wrong content, or make
     * {@code ChunksWriter} fail when two entries point at the same operator.
     *
     * <p>All or nothing: the new stream is built first, and the StreamInfos and the page are
     * changed only after every step that can fail has succeeded.
     *
     * @param pageNumber the page to rewrite
     * @param objects the page's content tree, whose existing {@link StreamInfo}s are shifted
     * @param pendingByInsertIndex the chunks to add, keyed by the token index they are anchored to
     * @throws IOException if the new content stream cannot be written
     */
    private static void rewritePageContentStream(
            int pageNumber, List<IObject> objects, Map<Integer, List<TextChunk>> pendingByInsertIndex)
            throws IOException {
        PDPage page = StaticResources.getDocument().getPage(pageNumber);
        PDContentStream contentStream = page.getContent();
        if (contentStream == null) {
            // A page with no /Contents has no stream to splice into (and nothing for OCR to recover).
            LOGGER.log(Level.FINE, "Page {0} has no content stream; skipping OCR-fallback synthesis", pageNumber);
            return;
        }
        List<Object> originalTokens = ContentStreamTokens.getTokens(contentStream);
        int originalLength = originalTokens.size();

        // A native chunk's anchor (its Tj/TJ operator index + 1) is usually still inside the
        // enclosing BT/ET text object. PDF forbids nesting a text object in another, and readers
        // then break or skip content, so real text would vanish. Snap every insertion point
        // forward to the nearest boundary outside any BT/ET span, open path and marked-content span.
        Map<Integer, List<TextChunk>> boundaryAlignedPending =
            alignToTopLevelBoundaries(originalTokens, pendingByInsertIndex);

        // The shift an insertion causes is the length of the block, so build the blocks first.
        ASAtom fontName = chooseOcrFallbackFontName(page);
        AffineTransform[] ctmBefore = ctmBeforeEachToken(originalTokens);
        int[] tokensInsertedBefore = new int[originalLength + 1];
        Map<Integer, List<SynthesizedBlock>> blocksBefore = new HashMap<>();
        for (Map.Entry<Integer, List<TextChunk>> entry : boundaryAlignedPending.entrySet()) {
            // The block places its text in page coordinates, so it has to undo the matrix that q ... cm
            // has set where it lands. A matrix that cannot be undone has no place for the text.
            AffineTransform ctm = ctmBefore[entry.getKey()];
            AffineTransform undo = null;
            if (!ctm.isIdentity()) {
                undo = inverseOrNull(ctm);
                if (undo == null) {
                    LOGGER.log(Level.FINE,
                        "Page {0}: no OCR-fallback text where the transformation matrix cannot be undone", pageNumber);
                    continue;
                }
            }
            List<SynthesizedBlock> blocks = new ArrayList<>();
            for (TextChunk chunk : entry.getValue()) {
                SynthesizedBlock block = buildSynthesizedBlock(chunk, undo, fontName);
                blocks.add(block);
                tokensInsertedBefore[entry.getKey()] += block.tokens.size();
            }
            blocksBefore.put(entry.getKey(), blocks);
        }
        // An operator at original index k moves to k + shiftOfOperator[k].
        int[] shiftOfOperator = new int[originalLength + 1];
        int runningShift = 0;
        for (int i = 0; i <= originalLength; i++) {
            runningShift += tokensInsertedBefore[i];
            shiftOfOperator[i] = runningShift;
        }

        List<Object> finalTokens = new ArrayList<>(originalLength + runningShift);
        List<TextChunk> synthesizedChunks = new ArrayList<>();
        List<StreamInfo> synthesizedInfos = new ArrayList<>();
        for (int index = 0; index <= originalLength; index++) {
            List<SynthesizedBlock> blocksHere = blocksBefore.get(index);
            if (blocksHere != null) {
                for (SynthesizedBlock block : blocksHere) {
                    int blockStart = finalTokens.size();
                    finalTokens.addAll(block.tokens);
                    synthesizedInfos.add(
                        new StreamInfo(blockStart + block.showOperatorIndex, null, 0, block.textLength));
                    synthesizedChunks.add(block.chunk);
                }
            }
            if (index < originalLength) {
                finalTokens.add(originalTokens.get(index));
            }
        }

        // Before the new stream is registered: a font left on the page is harmless, an orphan stream is not.
        ensureOcrFallbackFont(page, fontName);
        COSDocument cosDocument = StaticResources.getDocument().getDocument();
        COSObject newContents = AutoTaggingProcessor.createContentsIndirect(cosDocument, finalTokens);

        // The new stream exists. Nothing the tagger reads has been changed until now: shift the
        // pre-existing StreamInfos, then give each synthesized chunk its own, in this order so the
        // shift never touches the new entries.
        remapExistingStreamInfoIndexes(objects, shiftOfOperator);
        for (int i = 0; i < synthesizedChunks.size(); i++) {
            synthesizedChunks.get(i).getStreamInfos().add(synthesizedInfos.get(i));
        }
        // Both the cached PDContentStream wrapper (what ChunksWriter.getTokens reads) and the
        // page dictionary's own /Contents key need updating — PDPage#getContent() returns a
        // cached field, not a live read of /Contents, so mutating only the dictionary would be
        // invisible to the later tagging pass.
        contentStream.setContents(newContents);
        page.getObject().setKey(ASAtom.CONTENTS, newContents);
        cosDocument.addChangedObject(page.getObject());
    }

    /**
     * The current transformation matrix before each token of the stream: what {@code q}, {@code Q}
     * and {@code cm} have made of it by then. It is the matrix a block inserted before that token
     * runs under. A {@code Q} with no {@code q} is ignored, and so is an operand that is not a
     * number, as in {@code PaintBounds}. The transforms are never changed once stored, so entries
     * can share an instance while the matrix does not change.
     *
     * @param tokens the page's content-stream tokens
     * @return the matrix before each token and after the last one, so one more entry than there are
     *     tokens
     */
    private static AffineTransform[] ctmBeforeEachToken(List<Object> tokens) {
        AffineTransform[] before = new AffineTransform[tokens.size() + 1];
        Deque<AffineTransform> saved = new ArrayDeque<>();
        AffineTransform current = new AffineTransform();
        List<Double> operands = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            before[i] = current;
            Object token = tokens.get(i);
            if (!(token instanceof Operator)) {
                if (token instanceof COSBase) {
                    Double number = ((COSBase) token).getReal();
                    if (number != null) {
                        operands.add(number);
                    }
                }
                continue;
            }
            String operator = ((Operator) token).getOperator();
            if (Operators.Q_GSAVE.equals(operator)) {
                saved.push(current);
            } else if (Operators.Q_GRESTORE.equals(operator)) {
                if (!saved.isEmpty()) {
                    current = saved.pop();
                }
            } else if (Operators.CM_CONCAT.equals(operator) && operands.size() >= 6) {
                // A cm with fewer than six numbers is ignored. The new matrix applies to a point
                // first and the matrix in force before it second, which is concatenate().
                int first = operands.size() - 6;
                AffineTransform next = new AffineTransform(current);
                next.concatenate(new AffineTransform(operands.get(first), operands.get(first + 1),
                    operands.get(first + 2), operands.get(first + 3), operands.get(first + 4),
                    operands.get(first + 5)));
                current = next;
            }
            // The operands belong to the operator just read; left in the list they would leak into
            // the next cm.
            operands.clear();
        }
        before[tokens.size()] = current;
        return before;
    }

    /**
     * @param transform the matrix to undo
     * @return the matrix that undoes it, or null when it has none or the result is not finite (a
     *     page can state a matrix whose inverse overflows)
     */
    private static AffineTransform inverseOrNull(AffineTransform transform) {
        try {
            AffineTransform inverse = transform.createInverse();
            double[] entries = new double[6];
            inverse.getMatrix(entries);
            for (double entry : entries) {
                if (!Double.isFinite(entry)) {
                    return null;
                }
            }
            return inverse;
        } catch (NoninvertibleTransformException e) {
            return null;
        }
    }

    /**
     * Remaps every key in {@code pendingByInsertIndex} forward to the nearest index that is both
     * outside any BT/ET (text object) span and outside any open (unpainted) path-construction
     * sequence in {@code originalTokens}, merging insertion lists together whenever more than one
     * original key lands on the same aligned boundary.
     *
     * <p>An insertion point is unsafe in three places:
     * <ul>
     *   <li>Inside an open BT: PDF forbids nesting a text object in another, and a reader then
     *   drops or misrenders the native text, although the inserted operators are invisible.</li>
     *   <li>In the middle of a path built over several construction operators (e.g.
     *   {@code m l l l h}) and painted only at the end (e.g. {@code f}): even a self-contained
     *   {@code q ... Q} block spliced in there has no defined meaning, and the shape is lost.
     *   Pages saved with "Print to PDF" draw headings and bullets as such paths.</li>
     *   <li>Inside a BMC/BDC ... EMC marked-content span: the inserted block would be nested in
     *   unrelated marked content instead of staying top-level.</li>
     * </ul>
     * BT/ET is not nestable per spec (at most one is open at a time) and a path is either open or
     * not, so a single forward scan tracking those two as booleans is enough to find every span's end.
     * Marked content *is* nestable, so that's tracked as a depth counter instead of a boolean.
     *
     * @param originalTokens the page's content-stream tokens before any insertion
     * @param pendingByInsertIndex the chunks to insert, keyed by the token index they are anchored to
     * @return the same chunks keyed by the nearest safe token index at or after their anchor
     */
    private static Map<Integer, List<TextChunk>> alignToTopLevelBoundaries(
            List<Object> originalTokens, Map<Integer, List<TextChunk>> pendingByInsertIndex) {
        int originalLength = originalTokens.size();
        boolean[] safeToInsertBefore = new boolean[originalLength + 1];
        boolean inTextObject = false;
        boolean pathOpen = false;
        int markedContentDepth = 0;
        safeToInsertBefore[0] = true;
        for (int i = 0; i < originalLength; i++) {
            Object token = originalTokens.get(i);
            if (token instanceof Operator) {
                String operatorName = ((Operator) token).getOperator();
                if (Operators.BT.equals(operatorName)) {
                    inTextObject = true;
                } else if (Operators.ET.equals(operatorName)) {
                    inTextObject = false;
                } else if (PATH_CONSTRUCTION_OPERATORS.contains(operatorName)) {
                    pathOpen = true;
                } else if (PATH_PAINTING_OPERATORS.contains(operatorName)) {
                    pathOpen = false;
                } else if (Operators.BMC.equals(operatorName) || Operators.BDC.equals(operatorName)) {
                    markedContentDepth++;
                } else if (Operators.EMC.equals(operatorName)) {
                    // A stray EMC in a malformed stream must not leave the depth negative, which would
                    // make every later insertion point unsafe.
                    markedContentDepth = Math.max(0, markedContentDepth - 1);
                }
            }
            safeToInsertBefore[i + 1] = !inTextObject && !pathOpen && markedContentDepth == 0;
        }

        Map<Integer, List<TextChunk>> aligned = new HashMap<>();
        for (Map.Entry<Integer, List<TextChunk>> entry : pendingByInsertIndex.entrySet()) {
            int target = Math.max(0, Math.min(entry.getKey(), originalLength));
            while (target < originalLength && !safeToInsertBefore[target]) {
                target++;
            }
            aligned.computeIfAbsent(target, k -> new ArrayList<>()).addAll(entry.getValue());
        }
        return aligned;
    }

    /**
     * Gives every pre-existing, main-content-stream StreamInfo reachable from {@code objects} (text,
     * image, and formula alike — see {@link #forEachIObject}) the operator index it has in the
     * rewritten stream, via {@code shiftOfOperator} (see {@link #rewritePageContentStream}).
     * Entries scoped to a Form XObject's own stream ({@code xObjectName != null}) are left alone,
     * since their indices are relative to a stream this method never touches.
     *
     * <p>{@code StreamInfo.operatorIndex} is final, so each moved StreamInfo is replaced in its
     * chunk's list by a copy with the new index. One copy is made per original instance and used
     * wherever that instance appeared, so chunks that share a StreamInfo still share it, and a copy
     * is never moved a second time. No MCID is assigned yet at this point (the tagger does that
     * later, when it reads these lists), and the copy keeps every other field of the original.
     *
     * @param objects the page's content tree
     * @param shiftOfOperator for each original operator index, how far the insertions move it
     */
    private static void remapExistingStreamInfoIndexes(List<IObject> objects, int[] shiftOfOperator) {
        Map<StreamInfo, StreamInfo> copyOf = new IdentityHashMap<>();
        Set<StreamInfo> copies = Collections.newSetFromMap(new IdentityHashMap<>());
        forEachIObject(objects, obj -> {
            List<StreamInfo> streamInfos = obj.getStreamInfos();
            for (int i = 0; i < streamInfos.size(); i++) {
                StreamInfo streamInfo = streamInfos.get(i);
                if (streamInfo.getXObjectName() != null || copies.contains(streamInfo)) {
                    continue;
                }
                int original = streamInfo.getOperatorIndex();
                if (original < 0 || original >= shiftOfOperator.length) {
                    continue;
                }
                int shifted = original + shiftOfOperator[original];
                if (shifted == original) {
                    continue;
                }
                StreamInfo copy = copyOf.computeIfAbsent(streamInfo, info -> new StreamInfo(shifted,
                    info.getXObjectName(), info.getXImageObjectKey(), info.getStartIndex(), info.getEndIndex(),
                    info.getLength(), info.getMcid()));
                copies.add(copy);
                streamInfos.set(i, copy);
            }
        });
    }

    /**
     * Builds the invisible text-showing operator sequence for one chunk. The caller splices it into
     * the page's new content stream (see {@link #rewritePageContentStream}) and points the chunk's
     * {@link StreamInfo} at the {@code Tj} operator, which is how {@code ChunksWriter.processTokens}
     * indexes operators when it later re-parses the serialized stream.
     *
     * <p>ASCII-only: non-ASCII characters become {@code '?'}, because the non-embedded Standard-14
     * font has no wider encoding. Embedding a font for full Unicode is out of scope here.
     *
     * <p>Wrapped in {@code q ... Q}, not just {@code BT ... ET}: font ({@code Tf}) and rendering
     * mode ({@code Tr}) are graphics state and survive {@code ET}. Without {@code q}/{@code Q} the
     * {@code 3 Tr} (invisible) would leak into the native content that follows and hide it.
     *
     * <p>The chunk's position is in page coordinates, so where a {@code cm} is in force at the
     * insertion point the block starts with the matrix that undoes it (inside the {@code q}, so it
     * ends with the block).
     *
     * @param chunk the OCR-fallback chunk; its position comes from its bounding box
     * @param undoCtm the inverse of the transformation matrix at the insertion point, or null when
     *     that matrix is the identity
     * @param fontName the page's font resource name for the fallback font
     * @return the tokens, and where the {@code Tj} operator is among them
     */
    private static SynthesizedBlock buildSynthesizedBlock(TextChunk chunk, AffineTransform undoCtm,
            ASAtom fontName) {
        String text = toAsciiOrReplacementChar(chunk.getValue());
        List<Object> tokens = new ArrayList<>();
        tokens.add(Operator.getOperator(Operators.Q_GSAVE));
        if (undoCtm != null) {
            double[] entries = new double[6]; // a b c d e f, the order cm takes
            undoCtm.getMatrix(entries);
            for (double entry : entries) {
                tokens.add(COSReal.construct(entry).getDirectBase());
            }
            tokens.add(Operator.getOperator(Operators.CM_CONCAT));
        }
        tokens.add(Operator.getOperator(Operators.BT));
        tokens.add(COSName.construct(fontName).getDirectBase());
        tokens.add(COSReal.construct(OCR_FALLBACK_FONT_SIZE).getDirectBase());
        tokens.add(Operator.getOperator(Operators.TF));
        tokens.add(COSInteger.construct(INVISIBLE_TEXT_RENDER_MODE).getDirectBase());
        tokens.add(Operator.getOperator(Operators.TR));
        // Text state outlives ET: spacing, scaling and rise set by earlier native text would shift or
        // stretch this text, so each is set to its default.
        addTextStateReset(tokens);
        tokens.add(COSReal.construct(1).getDirectBase());
        tokens.add(COSReal.construct(0).getDirectBase());
        tokens.add(COSReal.construct(0).getDirectBase());
        tokens.add(COSReal.construct(1).getDirectBase());
        tokens.add(COSReal.construct(chunk.getLeftX()).getDirectBase());
        tokens.add(COSReal.construct(chunk.getBottomY()).getDirectBase());
        tokens.add(Operator.getOperator(Operators.TM));
        tokens.add(COSString.construct(text.getBytes(StandardCharsets.US_ASCII), false).getDirectBase());
        int showOperatorIndex = tokens.size();
        tokens.add(Operator.getOperator(Operators.TJ_SHOW));
        tokens.add(Operator.getOperator(Operators.ET));
        tokens.add(Operator.getOperator(Operators.Q_GRESTORE));
        return new SynthesizedBlock(chunk, tokens, showOperatorIndex, text.length());
    }

    /**
     * Sets character spacing, word spacing, horizontal scaling and text rise to their defaults.
     *
     * @param tokens the block being built; the operators are appended to it
     */
    private static void addTextStateReset(List<Object> tokens) {
        tokens.add(COSReal.construct(0).getDirectBase());
        tokens.add(Operator.getOperator(CHAR_SPACING_OPERATOR));
        tokens.add(COSReal.construct(0).getDirectBase());
        tokens.add(Operator.getOperator(WORD_SPACING_OPERATOR));
        tokens.add(COSReal.construct(100).getDirectBase());
        tokens.add(Operator.getOperator(HORIZONTAL_SCALING_OPERATOR));
        tokens.add(COSReal.construct(0).getDirectBase());
        tokens.add(Operator.getOperator(TEXT_RISE_OPERATOR));
    }

    /**
     * Picks the font resource name the synthesized text will use: {@link #OCR_FALLBACK_FONT_NAME}, or
     * that name with a number after it when the page already has a font under it (say, a document
     * this tool has converted before), so the text is never shown in the page's own font.
     *
     * @param page the page that will show the synthesized text
     * @return a name no font on the page uses
     */
    private static ASAtom chooseOcrFallbackFontName(PDPage page) {
        COSObject fonts = fontDictionaryOf(page);
        ASAtom name = OCR_FALLBACK_FONT_NAME;
        if (fonts == null || fonts.empty()) {
            return name;
        }
        // getKey gives an empty object, not null, for a name nothing uses. A Helvetica already under the
        // name (left by an earlier conversion, or added through a /Font dictionary pages share) is reused.
        for (int n = 1; !fonts.getKey(name).empty() && !isHelvetica(fonts.getKey(name)); n++) {
            name = ASAtom.getASAtom(OCR_FALLBACK_FONT_NAME.getValue() + n);
        }
        return name;
    }

    /**
     * @param page the page whose font resources are wanted
     * @return the page's {@code /Font} dictionary (inherited included), or null when it has none
     */
    private static COSObject fontDictionaryOf(PDPage page) {
        PDResources resources = page.getResources();
        return resources == null ? null : resources.getObject().getKey(FONT);
    }

    private static boolean isHelvetica(COSObject font) {
        return TYPE1.equals(font.getNameKey(SUBTYPE)) && HELVETICA.equals(font.getNameKey(BASE_FONT));
    }

    /**
     * Adds a minimal Standard-14 Helvetica font resource under {@code fontName} to the page.
     *
     * <p>If the page has no {@code /Resources} of its own (inherited from an ancestor /Pages node,
     * or missing entirely), {@link #localizePageResources} gives it a page-owned copy first, so the
     * font is not added to the resources that sibling pages share.
     *
     * @param page the page that will show the synthesized text
     * @param fontName the name from {@link #chooseOcrFallbackFontName}, which no font on the page has
     */
    private static void ensureOcrFallbackFont(PDPage page, ASAtom fontName) {
        PDResources resources = page.getResources();
        COSObject existingFonts = fontDictionaryOf(page);
        if (existingFonts != null && !existingFonts.empty() && !existingFonts.getKey(fontName).empty()) {
            return; // chooseOcrFallbackFontName only leaves a name that is taken when it holds Helvetica
        }
        if (resources == null || Boolean.TRUE.equals(page.isInheritedResources())) {
            resources = localizePageResources(page, resources);
        }
        COSObject resourcesObj = resources.getObject();
        COSObject fontDictObj = resourcesObj.getKey(FONT);
        if (fontDictObj == null || fontDictObj.empty()) {
            fontDictObj = COSDictionary.construct();
            resourcesObj.setKey(FONT, fontDictObj);
        }
        COSObject fontEntry = COSDictionary.construct();
        fontEntry.setKey(ASAtom.TYPE, COSName.construct(FONT));
        fontEntry.setKey(SUBTYPE, COSName.construct(TYPE1));
        fontEntry.setKey(BASE_FONT, COSName.construct(HELVETICA));
        fontDictObj.setKey(fontName, fontEntry);
    }

    /**
     * Gives {@code page} its own {@code /Resources} dictionary — a copy of {@code inherited} (or
     * empty, if the page had none) — and assigns it via {@code page.setResources}, which also
     * writes the page's own {@code /Resources} key. Top-level entries are copied by reference
     * (their targets aren't mutated, so sharing them back is fine); {@code /Font} is deep-copied
     * one level so {@link #ensureOcrFallbackFont}'s write can't land in a Font dict another page
     * still shares.
     *
     * @param page the page to give its own resources
     * @param inherited the resources the page currently resolves to, or null if it has none
     * @return the page's new own resources
     */
    private static PDResources localizePageResources(PDPage page, PDResources inherited) {
        COSObject localResourcesObj = COSDictionary.construct();
        if (inherited != null) {
            COSObject inheritedObj = inherited.getObject();
            for (ASAtom key : inheritedObj.getKeySet()) {
                localResourcesObj.setKey(key, inheritedObj.getKey(key));
            }
            COSObject inheritedFontDict = inheritedObj.getKey(FONT);
            if (inheritedFontDict != null && !inheritedFontDict.empty()) {
                COSObject localFontDict = COSDictionary.construct();
                for (ASAtom fontName : inheritedFontDict.getKeySet()) {
                    localFontDict.setKey(fontName, inheritedFontDict.getKey(fontName));
                }
                localResourcesObj.setKey(FONT, localFontDict);
            }
        }
        PDResources localResources = new PDResources(localResourcesObj);
        page.setResources(localResources);
        return localResources;
    }

    /**
     * Whether the non-embedded Standard-14 font can show this character.
     *
     * @param c the character to test
     * @return true for printable ASCII, 0x20 to 0x7E
     */
    private static boolean isPrintableAscii(char c) {
        return c >= PRINTABLE_ASCII_MIN && c <= PRINTABLE_ASCII_MAX;
    }

    /**
     * Replaces every character outside printable ASCII with {@code '?'}. A non-embedded Standard-14
     * base font's default encoding (used by {@link #buildSynthesizedBlock}) has no broader
     * coverage — full Unicode font embedding is out of scope
     * here.
     *
     * @param text the text to convert, or null
     * @return the text with each character outside printable ASCII replaced; empty for null
     */
    private static String toAsciiOrReplacementChar(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append(isPrintableAscii(c) ? c : REPLACEMENT_CHAR);
        }
        return sb.toString();
    }

    /**
     * Collects ImageChunks and TextChunks from Java-filtered page contents.
     */
    private static void collectJavaChunks(List<IObject> javaPage,
                                           List<ImageChunk> imageChunks,
                                           List<TextChunk> textChunks) {
        for (IObject obj : javaPage) {
            if (obj instanceof ImageChunk) {
                imageChunks.add((ImageChunk) obj);
            } else if (obj instanceof TextChunk) {
                // Raw TextChunk from ContentFilterProcessor (pre-paragraph processing)
                textChunks.add((TextChunk) obj);
            } else if (obj instanceof SemanticTextNode) {
                // TextChunks wrapped in SemanticTextNode (post-paragraph processing)
                SemanticTextNode textNode = (SemanticTextNode) obj;
                for (TextColumn col : textNode.getColumns()) {
                    for (TextLine line : col.getLines()) {
                        textChunks.addAll(line.getTextChunks());
                    }
                }
            }
        }
    }

    /**
     * Replaces backend TextChunks with Java TextChunks that have StreamInfo.
     *
     * <p>Backend-generated TextChunks lack StreamInfo (needed for MCID/struct-tree).
     * For each backend SemanticTextNode, we find Java TextChunks whose centers fall
     * within the node's bbox and replace the backend TextChunk with the Java ones.
     * This preserves the backend's structural decisions (heading/paragraph/reading order)
     * while using the Java TextChunks that carry StreamInfo.
     */
    private static Set<Integer> enrichTextStreamInfos(List<IObject> backendPage, List<TextChunk> javaTextChunks,
                                                      PagePaints paints) {
        Set<Integer> usedJavaIndices = new HashSet<>();
        enrichTextStreamInfosRecursive(backendPage, javaTextChunks, usedJavaIndices, paints);
        return usedJavaIndices;
    }

    /**
     * Builds paragraphs from the stream text the backend returned no element for.
     *
     * <p>A backend can miss text the content stream holds. Docling under --force-ocr, with
     * an OCR engine that cannot read Hangul, returned no item for Korean paragraphs. Their
     * chunks matched nothing, were left out of the page, and the tagger wrote them as
     * /Artifact, out of reach of assistive technology (PDFDLOSP-41). Text inside an element
     * the backend did return (a picture, a table, a page header) stays as it is: the backend
     * said what that area holds.
     */
    private static List<IObject> paragraphsOfTextTheBackendMissed(
            List<IObject> backendPage, List<TextChunk> javaTextChunks, Set<Integer> usedJavaIndices) {
        List<IObject> missed = new ArrayList<>();
        for (int i = 0; i < javaTextChunks.size(); i++) {
            TextChunk chunk = javaTextChunks.get(i);
            if (!usedJavaIndices.contains(i) && !chunk.getStreamInfos().isEmpty()
                    && !insideAnyElement(chunk, backendPage)) {
                missed.add(chunk);
            }
        }
        if (missed.isEmpty()) {
            return missed;
        }
        return ParagraphProcessor.processParagraphs(TextLineProcessor.processTextLines(missed));
    }

    private static boolean insideAnyElement(TextChunk chunk, List<IObject> backendPage) {
        double tol = 5.0;
        double cx = chunk.getCenterX();
        double cy = chunk.getCenterY();
        for (IObject element : backendPage) {
            if (cx >= element.getLeftX() - tol && cx <= element.getRightX() + tol
                    && cy >= element.getBottomY() - tol && cy <= element.getTopY() + tol) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recursively walks the IObject tree and replaces TextChunks in SemanticTextNodes
     * with Java TextChunks that carry StreamInfo. Handles TableBorder cells, PDFList items,
     * and any other container that holds nested IObjects.
     */
    private static void enrichTextStreamInfosRecursive(
            List<IObject> objects, List<TextChunk> javaTextChunks, Set<Integer> usedJavaIndices, PagePaints paints) {

        for (IObject obj : objects) {
            if (obj instanceof SemanticFormula) {
                // Formula inside table/list — enrich StreamInfos by bbox overlap
                enrichSingleFormula((SemanticFormula) obj, javaTextChunks);
            } else if (obj instanceof SemanticTextNode) {
                enrichSingleTextNode((SemanticTextNode) obj, javaTextChunks, usedJavaIndices, paints);
            } else if (obj instanceof TableBorder) {
                TableBorder table = (TableBorder) obj;
                for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
                    TableBorderRow row = table.getRow(rowNumber);
                    for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                        TableBorderCell cell = row.getCell(colNumber);
                        if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                            enrichTextStreamInfosRecursive(cell.getContents(), javaTextChunks, usedJavaIndices, paints);
                        }
                    }
                }
            } else if (obj instanceof PDFList) {
                PDFList list = (PDFList) obj;
                for (ListItem item : list.getListItems()) {
                    // Enrich ListItem's own text lines (used by AutoTaggingProcessor for Lbl/LBody)
                    for (TextLine line : item.getLines()) {
                        replaceLineChunksWithJava(line, javaTextChunks, usedJavaIndices);
                    }
                    // Enrich nested contents (images, paragraphs, sub-lists)
                    enrichTextStreamInfosRecursive(item.getContents(), javaTextChunks, usedJavaIndices, paints);
                }
            }
        }
    }

    /**
     * Replaces a single SemanticTextNode's TextChunks with the Java TextChunks
     * drawn inside its bounding box, and records the text source in
     * ElementMetadata: "stream" when chunks matched, "outlines" when the page draws
     * the node's text as paths, "ocr-fallback" when nothing on the page matched.
     */
    private static void enrichSingleTextNode(
            SemanticTextNode textNode, List<TextChunk> javaTextChunks, Set<Integer> usedJavaIndices,
            PagePaints paints) {

        double nLeft = textNode.getLeftX();
        double nRight = textNode.getRightX();
        double nBottom = textNode.getBottomY();
        double nTop = textNode.getTopY();

        List<TextChunk> matched = new ArrayList<>();
        double tol = 5.0;
        for (int i = 0; i < javaTextChunks.size(); i++) {
            if (usedJavaIndices.contains(i)) continue;
            TextChunk javaChunk = javaTextChunks.get(i);
            if (javaChunk.getStreamInfos().isEmpty()) continue;

            double jCx = javaChunk.getCenterX();
            double jCy = javaChunk.getCenterY();

            if (jCx >= nLeft - tol && jCx <= nRight + tol && jCy >= nBottom - tol && jCy <= nTop + tol) {
                matched.add(javaChunk);
                usedJavaIndices.add(i);
            }
        }

        if (matched.isEmpty()) {
            // The text may be drawn as outlines: no text operator, a filled path per
            // letter. Those paths are its content, and what the backend read is
            // what they say.
            // Real text in the box means the words are on the page as text, read
            // by a node that matched it: a path here is an underline or a bullet.
            List<StreamInfo> outlines = holdsJavaText(textNode.getBoundingBox(), javaTextChunks)
                ? List.of() : paints.takeInside(textNode.getBoundingBox(), tol);
            String text = textNode.getValue();
            if (!outlines.isEmpty() && text != null && !text.isBlank()) {
                DrawnTextChunk drawn = new DrawnTextChunk(new BoundingBox(textNode.getBoundingBox()), text);
                drawn.getStreamInfos().addAll(outlines);
                textNode.getColumns().clear();
                TextColumn column = new TextColumn();
                column.add(new TextLine(drawn));
                textNode.getColumns().add(column);
                recordTextSource(textNode, "outlines", null);
                return;
            }
            LOGGER.fine(() -> "enrichSingleTextNode: no Java TextChunk matched for backend node at bbox ["
                + String.format("%.1f,%.1f,%.1f,%.1f", nLeft, nBottom, nRight, nTop) + "]");
            recordTextSource(textNode, "ocr-fallback", null);
            return;
        }

        recordTextSource(textNode, "stream", null);

        // Replace the backend's text with the Java TextChunks that carry StreamInfo
        textNode.getColumns().clear();
        TextColumn newCol = new TextColumn();
        for (TextChunk tc : matched) {
            newCol.add(new TextLine(tc));
        }
        textNode.getColumns().add(newCol);
    }

    /** Whether a Java text chunk that carries stream info has its centre in the box. */
    private static boolean holdsJavaText(BoundingBox box, List<TextChunk> javaTextChunks) {
        for (TextChunk chunk : javaTextChunks) {
            if (!chunk.getStreamInfos().isEmpty()
                && chunk.getCenterX() >= box.getLeftX() && chunk.getCenterX() <= box.getRightX()
                && chunk.getCenterY() >= box.getBottomY() && chunk.getCenterY() <= box.getTopY()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records the text source decision in ElementMetadata for a SemanticTextNode.
     *
     * @param textNode   the text node whose metadata to update
     * @param source     "stream", "outlines" or "ocr-fallback"
     * @param similarity the stream-OCR similarity score, or null if not applicable
     */
    private static void recordTextSource(SemanticTextNode textNode, String source, Double similarity) {
        if (lastElementMetadata == null || textNode.getRecognizedStructureId() == null) return;
        ElementMetadata meta = lastElementMetadata.get(textNode.getRecognizedStructureId());
        if (meta == null) return;
        meta.setTextSource(source);
        if (similarity != null) {
            meta.setStreamOcrSimilarity(similarity);
        }
    }

    /**
     * Swaps a list line's backend chunks for the Java chunks drawn inside it.
     *
     * <p>Two things were wrong with matching each backend chunk to one Java chunk
     * by centre proximity. A backend chunk is a whole OCR line while a Java chunk
     * is often a single word, so their centres sit far apart even when the word
     * lies inside the line -- measured 5-12pt on 01030000000115, where the chunk
     * "1. When changing objectives..." had "scanning" as its nearest Java chunk.
     * Every match failed, the chunk kept no StreamInfo, and Lbl/LBody were built
     * with no MCID: empty shells whose text fell through to /Artifact and out of
     * the structure tree.
     *
     * <p>Copying StreamInfos onto the backend chunk is not enough either. The Lbl
     * and LBody split in AutoTaggingProcessor slices the first line by character
     * offset, which needs the per-symbol coordinates only a Java chunk carries; a
     * backend chunk has none, so slicing it fails outright.
     *
     * <p>So replace the line's chunks with the matched Java chunks, exactly as
     * {@link #enrichSingleTextNode} already does for paragraphs. That asymmetry
     * between paragraphs and lists was the defect.
     */
    private static void replaceLineChunksWithJava(
            TextLine line, List<TextChunk> javaTextChunks, Set<Integer> usedJavaIndices) {
        if (line.getTextChunks().isEmpty()) {
            return;
        }
        // Already carrying stream provenance: nothing to recover.
        boolean allHaveStreamInfo = true;
        for (TextChunk c : line.getTextChunks()) {
            if (c.getStreamInfos().isEmpty()) {
                allHaveStreamInfo = false;
                break;
            }
        }
        if (allHaveStreamInfo) {
            return;
        }

        double lLeft = line.getLeftX();
        double lRight = line.getRightX();
        double lBottom = line.getBottomY();
        double lTop = line.getTopY();
        double tol = 5.0;

        List<TextChunk> matched = new ArrayList<>();
        List<Integer> matchedIndices = new ArrayList<>();
        for (int i = 0; i < javaTextChunks.size(); i++) {
            if (usedJavaIndices.contains(i)) continue;
            TextChunk javaChunk = javaTextChunks.get(i);
            if (javaChunk.getStreamInfos().isEmpty()) continue;
            double jCx = javaChunk.getCenterX();
            double jCy = javaChunk.getCenterY();
            if (jCx >= lLeft - tol && jCx <= lRight + tol
                    && jCy >= lBottom - tol && jCy <= lTop + tol) {
                matched.add(javaChunk);
                matchedIndices.add(i);
            }
        }
        if (matched.isEmpty()) {
            LOGGER.fine(() -> "replaceLineChunksWithJava: no Java TextChunk inside list line");
            return;
        }

        line.getTextChunks().clear();
        line.getTextChunks().addAll(matched);
        usedJavaIndices.addAll(matchedIndices);
    }

    /**
     * Copies StreamInfos from Java TextChunks to SemanticFormula objects by bbox overlap.
     *
     * <p>SemanticFormula (from backend) has no StreamInfo. We find all Java TextChunks
     * whose centers fall within the formula's bbox and copy their StreamInfos directly
     * to the formula's StreamInfo list (inherited from BaseObject).
     */
    private static void enrichFormulaStreamInfos(List<IObject> backendPage, List<TextChunk> javaTextChunks) {
        for (IObject obj : backendPage) {
            if (obj instanceof SemanticFormula) {
                enrichSingleFormula((SemanticFormula) obj, javaTextChunks);
            }
        }
    }

    /**
     * Copies StreamInfos from Java TextChunks to a SemanticFormula by bbox overlap.
     * Allows reuse of Java chunks since formula content often IS the text content.
     */
    private static void enrichSingleFormula(SemanticFormula formula, List<TextChunk> javaTextChunks) {
        if (!formula.getStreamInfos().isEmpty()) return;

        double fLeft = formula.getLeftX();
        double fRight = formula.getRightX();
        double fBottom = formula.getBottomY();
        double fTop = formula.getTopY();
        double tol = 5.0;

        for (TextChunk javaChunk : javaTextChunks) {
            if (javaChunk.getStreamInfos().isEmpty()) continue;
            double jCx = javaChunk.getCenterX();
            double jCy = javaChunk.getCenterY();

            if (jCx >= fLeft - tol && jCx <= fRight + tol && jCy >= fBottom - tol && jCy <= fTop + tol) {
                formula.getStreamInfos().addAll(javaChunk.getStreamInfos());
            }
        }
    }

    /**
     * Finds the ImageChunk whose center point lies within the SemanticPicture's bounding box.
     * Returns null if no candidate's center is contained (with 1pt tolerance).
     */
    private static ImageChunk findMatchingImageChunk(SemanticPicture picture, List<ImageChunk> candidates) {
        double picLeft = picture.getLeftX();
        double picRight = picture.getRightX();
        double picBottom = picture.getBottomY();
        double picTop = picture.getTopY();

        ImageChunk best = null;
        double bestDist = Double.MAX_VALUE;

        for (ImageChunk chunk : candidates) {
            double cx = chunk.getCenterX();
            double cy = chunk.getCenterY();
            // Center-point containment (with 1pt tolerance)
            if (cx >= picLeft - 1 && cx <= picRight + 1 && cy >= picBottom - 1 && cy <= picTop + 1) {
                double dist = Math.hypot(cx - picture.getCenterX(), cy - picture.getCenterY());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = chunk;
                }
            }
        }
        return best;
    }

    private static void mergeResults(
            List<List<IObject>> contents,
            Map<Integer, List<IObject>> javaResults,
            Map<Integer, List<IObject>> backendResults,
            Set<Integer> pagesToProcess,
            int totalPages) {

        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                continue;
            }

            List<IObject> pageContents;
            if (javaResults.containsKey(pageNumber)) {
                pageContents = javaResults.get(pageNumber);
            } else if (backendResults.containsKey(pageNumber)) {
                pageContents = backendResults.get(pageNumber);
            } else {
                pageContents = new ArrayList<>();
            }

            contents.set(pageNumber, pageContents);
        }
    }

    /**
     * Applies post-processing operations that span multiple pages.
     */
    private static void postProcess(
            List<List<IObject>> contents,
            Config config,
            Set<Integer> pagesToProcess,
            int totalPages) {

        // Cross-page operations
        HeaderFooterProcessor.processHeadersAndFooters(contents, false);
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            contents.set(pageNumber, ListProcessor.processListsFromTextNodes(contents.get(pageNumber)));
        }
        ListProcessor.checkNeighborLists(contents);
        TableBorderProcessor.checkNeighborTables(contents);
        HeadingProcessor.detectHeadingsLevels();
        LevelProcessor.detectLevels(contents);
    }

    /**
     * Checks if a page should be processed.
     */
    private static boolean shouldProcessPage(int pageNumber, Set<Integer> pagesToProcess) {
        return pagesToProcess == null || pagesToProcess.contains(pageNumber);
    }

    /**
     * Determines the output formats to request from the hybrid backend.
     *
     * <p>Only JSON is requested. Markdown and HTML are generated by Java processors
     * from the IObject structure, which allows consistent application of:
     * <ul>
     *   <li>Reading order algorithms (XYCutPlusPlusSorter)</li>
     *   <li>Page separators and other formatting options</li>
     * </ul>
     *
     * @param config The configuration settings (unused, kept for API compatibility).
     * @return Set containing only JSON format.
     */
    private static Set<OutputFormat> determineOutputFormats(Config config) {
        return EnumSet.of(OutputFormat.JSON);
    }

    /**
     * Logs a summary of triage decisions.
     */
    private static void logTriageSummary(Map<Integer, TriageResult> triageResults) {
        long javaCount = triageResults.values().stream()
            .filter(r -> r.getDecision() == TriageDecision.JAVA)
            .count();
        long backendCount = triageResults.values().stream()
            .filter(r -> r.getDecision() == TriageDecision.BACKEND)
            .count();

        LOGGER.log(Level.INFO, "Triage summary: JAVA={0}, BACKEND={1}", new Object[]{javaCount, backendCount});

        // Log individual decisions at FINE level
        for (Map.Entry<Integer, TriageResult> entry : triageResults.entrySet()) {
            TriageResult result = entry.getValue();
            LOGGER.log(Level.FINE, "Page {0}: {1} (confidence={2})",
                new Object[]{entry.getKey(), result.getDecision(), result.getConfidence()});
        }
    }

    /**
     * Logs triage results to a JSON file for benchmark evaluation.
     *
     * @param inputPdfName   The path to the input PDF file.
     * @param hybridBackend  The hybrid backend used.
     * @param triageResults  Map of page number to triage result.
     * @param triageLog      The path of the triage log.
     */
    private static void logTriageToFile(
            String inputPdfName,
            String hybridBackend,
            Map<Integer, TriageResult> triageResults,
            Path triageLog) {

        try {
            String documentName = Path.of(inputPdfName).getFileName().toString();
            TriageLogger triageLogger = new TriageLogger();
            triageLogger.writeToFile(triageLog, documentName, hybridBackend, triageResults);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write triage log: {0}", e.getMessage());
        }
    }
}

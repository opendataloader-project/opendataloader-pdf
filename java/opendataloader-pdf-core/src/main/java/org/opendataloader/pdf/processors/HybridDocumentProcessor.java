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
import org.opendataloader.pdf.autotagging.ChunksWriter;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.hybrid.DoclingSchemaTransformer;
import org.opendataloader.pdf.hybrid.ElementMetadata;
import org.opendataloader.pdf.hybrid.HancomAISchemaTransformer;
import org.opendataloader.pdf.hybrid.HancomSchemaTransformer;
import org.opendataloader.pdf.hybrid.HybridClient;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.opendataloader.pdf.hybrid.HybridClient.OutputFormat;
import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.hybrid.HybridSchemaTransformer;
import org.opendataloader.pdf.hybrid.TextSimilarity;
import org.opendataloader.pdf.hybrid.OcrWordInfo;
import org.opendataloader.pdf.hybrid.TriageLogger;
import org.opendataloader.pdf.hybrid.TriageProcessor;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.verapdf.as.ASAtom;
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
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
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
     * Stores per-page OCR word data from the most recent hybrid backend processing.
     * Used for OCR enrichment fallback when Java TextChunks are not available.
     * Reset at the start of each {@code processDocument} call.
     */
    private static volatile Map<Integer, List<OcrWordInfo>> lastOcrWordsByPage;

    /**
     * Stores the raw merged JSON returned by the hybrid backend's most recent
     * {@code HybridResponse.getJson()}. Downstream tools (e.g. opendataloader-pdfua
     * evidence reports) need per-module raw outputs to file as L2 evidence.
     *
     * <p>Single-threaded by contract: this static is overwritten on each
     * {@code processDocument} call, so concurrent invocations would race. Callers
     * that need per-document raw JSON must serialize {@code processDocument}
     * invocations (or wrap with their own ThreadLocal).
     *
     * <p>Multi-chunk documents (&gt;{@link #BACKEND_CHUNK_SIZE} backend pages) currently
     * keep only the last chunk's JSON. Single-chunk documents capture the full
     * response.
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

    /** Returns the OCR word data from the most recent {@link #processDocument} call. */
    public static Map<Integer, List<OcrWordInfo>> getLastOcrWordsByPage() {
        return lastOcrWordsByPage;
    }

    /**
     * Maximum number of pages to send to the backend in a single request.
     * Large scanned PDFs (100+ pages) cause the backend to hang when sent all at once
     * due to non-linear memory/processing scaling in the AI pipeline.
     * Chunking into smaller batches avoids this while adding negligible overhead
     * (the model is loaded once at server startup, not per-request).
     *
     * @see <a href="https://github.com/opendataloader-project/opendataloader-pdf/issues/352">#352</a>
     */
    static final int BACKEND_CHUNK_SIZE = 50;

    /**
     * Font resource name reserved for Phase B's synthesized invisible text (see
     * {@link #appendSynthesizedTextOperator}). A dedicated, always-added Standard-14 Helvetica
     * resource — not a font actually embedded in the source document — since this text is never
     * rendered visibly (render mode 3) and Standard-14 base fonts need no font-file embedding at
     * all, sidestepping re-encoding into whatever complex (e.g. CID/Identity-H) fonts the original
     * document's own visible text already uses. Namespaced to avoid any realistic collision with a
     * real document's own resource names.
     */
    private static final ASAtom OCR_FALLBACK_FONT_NAME = ASAtom.getASAtom("ODLOcrFallbackHelv");
    private static final float OCR_FALLBACK_FONT_SIZE = 10f;

    private HybridDocumentProcessor() {
        // Static utility class
    }

    /**
     * Processes a document using hybrid mode with triage-based routing.
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
        return processDocument(inputPdfName, config, pagesToProcess, null);
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

        lastHybridTimings = null; // Reset for this processing run
        lastElementMetadata = null;
        lastOcrWordsByPage = null;
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

        // Log triage results to JSON file if output directory is specified
        if (outputDir != null) {
            logTriageToFile(inputPdfName, config.getHybrid(), triageResults, outputDir);
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
                pictureSwapOriginalIds);
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

        for (int chunkStart = 0; chunkStart < sortedPages.size(); chunkStart += BACKEND_CHUNK_SIZE) {
            int chunkEnd = Math.min(chunkStart + BACKEND_CHUNK_SIZE, sortedPages.size());
            List<Integer> chunkPages = sortedPages.subList(chunkStart, chunkEnd);

            // Convert 0-indexed page numbers to 1-indexed for the server API
            Set<Integer> chunkPages1Indexed = new HashSet<>();
            for (int page0 : chunkPages) {
                chunkPages1Indexed.add(page0 + 1);
            }

            if (sortedPages.size() > BACKEND_CHUNK_SIZE) {
                LOGGER.log(Level.INFO, "Sending pages {0}-{1} of {2} backend pages",
                    new Object[]{chunkPages.get(0) + 1, chunkPages.get(chunkPages.size() - 1) + 1,
                                 sortedPages.size()});
            }

            try {
                HybridRequest request = HybridRequest.forPages(pdfBytes, chunkPages1Indexed, outputFormats)
                    .withCropOutput(cropOutputFor(config));
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
                        DocumentProcessor.setIDs(pageContents);
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

        // Capture element metadata and OCR words from the transformer (e.g., HancomAISchemaTransformer)
        lastElementMetadata = transformer.getElementMetadata();
        lastOcrWordsByPage = transformer.getOcrWordsByPage();

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
     * Resolve the per-document crop / page-image destination from the config.
     *
     * <p>The cached client cannot hold this — it is reused across documents and
     * its config reflects only the first one. Carrying it on the per-document
     * {@link HybridClient.HybridRequest} keeps the destination correct (and
     * needs no shared mutable state) for the document being processed.
     */
    private static HybridClient.CropOutput cropOutputFor(Config config) {
        HybridConfig hc = config.getHybridConfig();
        if (hc == null || !hc.isSaveCrops() || hc.getCropOutputDir() == null) {
            return HybridClient.CropOutput.DISABLED;
        }
        return new HybridClient.CropOutput(true, hc.getCropOutputDir());
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

        // docling and docling-fast (deprecated) use DoclingSchemaTransformer
        if (Config.HYBRID_DOCLING.equals(hybrid) || Config.HYBRID_DOCLING_FAST.equals(hybrid)) {
            return new DoclingSchemaTransformer();
        }

        // hancom uses HancomSchemaTransformer
        if (Config.HYBRID_HANCOM.equals(hybrid)) {
            return new HancomSchemaTransformer();
        }

        // hancom-ai uses HancomAISchemaTransformer. Thread the regionlist strategy
        // from HybridConfig so --regionlist-strategy is honoured instead of silently
        // falling back to the transformer's default.
        if (Config.HYBRID_HANCOM_AI.equals(hybrid)) {
            HancomAISchemaTransformer transformer = new HancomAISchemaTransformer();
            String regionlistStrategy = config.getHybridConfig() != null
                ? config.getHybridConfig().getRegionlistStrategy() : null;
            if (regionlistStrategy != null) {
                transformer.setRegionlistStrategy(regionlistStrategy);
            }
            return transformer;
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
     */
    private static void enrichBackendResults(
            Map<Integer, List<IObject>> backendResults,
            Map<Integer, List<IObject>> filteredContents,
            HybridConfig hybridConfig,
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
            if (!javaTextChunks.isEmpty()) {
                enrichTextStreamInfos(backendPage, javaTextChunks, hybridConfig);
                enrichFormulaStreamInfos(backendPage, javaTextChunks);
            } else if (hybridConfig.isOcrAuto() || hybridConfig.isOcrForce()) {
                // OCR-only (scanned) page: no Java TextChunks to compare against,
                // so text_source cannot be inferred from stream/OCR similarity.
                // Record "ocr" for every SemanticTextNode so the JSON output still
                // reflects that the text came from OCR rather than the PDF stream.
                markAllTextSourcesAsOcr(backendPage);
            }

            // OCR fallback: log elements that still lack StreamInfo after enrichment, then
            // synthesize real (invisible) content for them — see synthesizeInvisibleTextForOcrFallback.
            if (hybridConfig.isOcrAuto() || hybridConfig.isOcrForce()) {
                Map<Integer, List<OcrWordInfo>> ocrWords = lastOcrWordsByPage;
                if (ocrWords != null) {
                    List<OcrWordInfo> pageOcrWords = ocrWords.getOrDefault(pageNumber, List.of());
                    logOcrFallbackCandidates(backendPage, pageNumber, pageOcrWords);
                }
                synthesizeInvisibleTextForOcrFallback(backendPage, pageNumber);
            }

            final int pg = pageNumber;
            final int javaTotal = javaTextChunks.size();
            LOGGER.fine(() -> "Page " + pg + ": enrichment complete — "
                + javaTotal + " Java TextChunks available");
        }
    }

    /**
     * Logs backend elements that still lack StreamInfo after enrichment and could
     * benefit from OCR fallback. Walks the IObject tree recursively to find
     * SemanticTextNodes with no StreamInfo in any of their TextChunks.
     *
     * <p>Diagnostic logging only — {@link #synthesizeInvisibleTextForOcrFallback} is the pass
     * that actually inserts invisible text operators into the content stream for this content
     * (called alongside this method, not from within it, so a failure/no-op in one doesn't
     * affect the other).
     */
    private static void logOcrFallbackCandidates(
            List<IObject> backendPage, int pageNumber, List<OcrWordInfo> ocrWords) {
        int candidateCount = 0;
        int ocrWordMatchCount = 0;

        for (IObject obj : backendPage) {
            if (obj instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) obj;
                boolean hasStreamInfo = false;
                for (TextColumn col : textNode.getColumns()) {
                    for (TextLine line : col.getLines()) {
                        for (TextChunk chunk : line.getTextChunks()) {
                            if (!chunk.getStreamInfos().isEmpty()) {
                                hasStreamInfo = true;
                                break;
                            }
                        }
                        if (hasStreamInfo) break;
                    }
                    if (hasStreamInfo) break;
                }
                if (!hasStreamInfo) {
                    candidateCount++;
                    // Count OCR words that overlap with this text node's bbox
                    double nLeft = textNode.getLeftX();
                    double nRight = textNode.getRightX();
                    double nBottom = textNode.getBottomY();
                    double nTop = textNode.getTopY();
                    double tol = 5.0;
                    int matchedWords = 0;
                    for (OcrWordInfo word : ocrWords) {
                        BoundingBox wb = word.getBbox();
                        double cx = (wb.getLeftX() + wb.getRightX()) / 2.0;
                        double cy = (wb.getBottomY() + wb.getTopY()) / 2.0;
                        if (cx >= nLeft - tol && cx <= nRight + tol
                                && cy >= nBottom - tol && cy <= nTop + tol) {
                            matchedWords++;
                        }
                    }
                    if (matchedWords > 0) {
                        ocrWordMatchCount += matchedWords;
                        final int mw = matchedWords;
                        LOGGER.fine(() -> "Page " + pageNumber + ": OCR fallback candidate — "
                            + "SemanticTextNode at ["
                            + String.format("%.1f,%.1f,%.1f,%.1f", nLeft, nBottom, nRight, nTop)
                            + "] has " + mw + " matching OCR words");
                    }
                }
            }
        }

        if (candidateCount > 0) {
            final int cc = candidateCount;
            final int owm = ocrWordMatchCount;
            LOGGER.info(() -> "Page " + pageNumber + ": " + cc
                + " element(s) need OCR fallback, " + owm + " OCR words available");
        }
    }

    /**
     * Number of tokens {@link #appendSynthesizedTextOperator} appends per chunk — {@code q BT
     * /ODLOcrFallbackHelv 10 Tf 3 Tr 1 0 0 1 x y Tm (text) Tj ET Q}. Used to size the index shift
     * every insertion causes for whatever pre-existing content follows it — see
     * {@link #rewritePageContentStream}.
     */
    private static final int TOKENS_PER_SYNTHESIZED_CHUNK = 18;

    /**
     * Phase B: walks the IObject tree (see {@link #forEachIObject}) collecting every TextChunk in
     * reading order, and for each one that still has no StreamInfo after enrichment, records where
     * a synthesized invisible operator for it should be inserted into the page's content stream —
     * immediately after the nearest preceding TextChunk that does have real StreamInfo (i.e., the
     * closest native/already-tagged content before it in reading order), or at the very start of
     * the stream if no such content precedes it on the page.
     *
     * <p><b>Fixes a confirmed bug</b>: this previously appended every fallback chunk's operator to
     * the *end* of the content stream, one chunk at a time, regardless of where that chunk actually
     * belongs. Since a page's fallback chunks are scattered across many different tree branches,
     * that systematically scrambled physical content-stream order — anything that follows raw
     * content-stream order (a naive text extractor, "select all" in many viewers, `pdftotext -raw`)
     * would see a page's fallback content as one big, out-of-place, reading-order-scrambled block
     * tacked onto the end, even though the struct-tree/MCID order (and therefore the JSON model) was
     * already correct. Confirmed via direct content-stream inspection of a previously-patched run
     * against WetlandsTest.pdf: native text stayed in order, but every OCR-fallback bullet/heading/
     * paragraph on a page reappeared, out of place and in tree-walk order, after all of that page's
     * native text — including some content duplicated (once natively, once as an unmatched fallback
     * chunk for a sibling run of the same text).
     *
     * <p>Batches all of a page's insertions into one content-stream rewrite (see
     * {@link #rewritePageContentStream}) instead of one rewrite per chunk — also fixes the O(n²)
     * per-chunk-rewrite cost previously called out here as a known inefficiency.
     */
    private static void synthesizeInvisibleTextForOcrFallback(List<IObject> objects, int pageNumber) {
        List<TextChunk> orderedChunks = new ArrayList<>();
        forEachIObject(objects, obj -> {
            if (obj instanceof TextChunk) {
                orderedChunks.add((TextChunk) obj);
            }
        });

        Map<Integer, List<TextChunk>> pendingByInsertIndex = new HashMap<>();
        int insertIndex = 0; // token index to anchor the next fallback chunk after; 0 = start of stream
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
                    insertIndex = anchorOperatorIndex + 1;
                }
                continue;
            }
            if (toAsciiOrReplacementChar(chunk.getValue()).isEmpty()) {
                continue;
            }
            pendingByInsertIndex.computeIfAbsent(insertIndex, k -> new ArrayList<>()).add(chunk);
        }

        if (pendingByInsertIndex.isEmpty()) {
            return;
        }
        try {
            rewritePageContentStream(pageNumber, objects, pendingByInsertIndex);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                "Failed to synthesize invisible text for OCR-fallback chunks on page {0}: {1}",
                new Object[]{pageNumber, e.getMessage()});
        }
    }

    /**
     * Visits every IObject reachable from {@code objects} — each top-level node itself, plus
     * (recursively) SemanticTextNode's TextChunks, TableBorder cells' contents in row/column order,
     * and PDFList items' nested contents. {@code getStreamInfos()} is declared on {@code IObject}
     * itself, so this single generic walk is enough both to collect TextChunks (see
     * {@link #synthesizeInvisibleTextForOcrFallback}) and to reach every StreamInfo-bearing object
     * on a page — text, image, and formula alike — when that page's content stream is about to be
     * rewritten (see {@link #rewritePageContentStream}).
     */
    private static void forEachIObject(List<IObject> objects, java.util.function.Consumer<IObject> visitor) {
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
     * text-showing operator sequence — {@code BT /ODLOcrFallbackHelv 10 Tf 3 Tr 1 0 0 1 x y Tm
     * (text) Tj ET} — in for each pending OCR-fallback chunk at the token position it was anchored
     * to by {@link #synthesizeInvisibleTextForOcrFallback}, and attaching a matching
     * {@link StreamInfo} to each chunk pointing at its operator's resulting index in the rewritten
     * stream. {@link AutoTaggingProcessor}/{@link ChunksWriter} need no changes to pick these up:
     * they generically wrap any operator that has a matching StreamInfo, exactly the same as native
     * content — the only thing missing for OCR-recovered content was a real operator to point at,
     * since it never existed in the original content stream (that's precisely why OCR was needed
     * for it in the first place).
     *
     * <p>Inserting anywhere but the tail shifts the token index of everything that follows the
     * insertion point — so before splicing anything in, every <em>pre-existing</em> StreamInfo on
     * the page whose operator lives in this same main content stream (i.e., not one scoped to a
     * Form XObject's own separate stream via a non-null {@code xObjectName} — those are untouched,
     * since this method never rewrites an XObject's stream) gets remapped to where its operator will
     * land in the rewritten stream. Skipping this step was the bug in an earlier version of this
     * fix: newly-inserted chunks anchored mid-stream, but every pre-existing StreamInfo positioned
     * after the insertion point was left pointing at its old (now wrong) index — silently mis-tagging
     * unrelated content, or crashing {@code ChunksWriter} outright when two StreamInfo entries
     * collided on the same now-stale index.
     */
    private static void rewritePageContentStream(
            int pageNumber, List<IObject> objects, Map<Integer, List<TextChunk>> pendingByInsertIndex)
            throws IOException {
        PDPage page = StaticResources.getDocument().getPage(pageNumber);
        ensureOcrFallbackFont(page);

        PDContentStream contentStream = page.getContent();
        List<Object> originalTokens = ChunksWriter.getTokens(contentStream);
        int originalLength = originalTokens.size();

        // A native chunk's anchor is its own Tj/TJ operatorIndex + 1 — almost always still inside
        // that operator's enclosing BT/ET text object (a single BT commonly wraps several
        // consecutive show operators). Splicing a self-contained BT..ET sequence in there would
        // nest one text object inside another, which PDF forbids — content-stream interpreters
        // handle that by breaking or silently skipping content, which is exactly what a visual diff
        // against the source PDF caught: real, visible native bullets disappearing from the
        // rendered page even though the inserted text itself is invisible (Tr 3). Snap every
        // pending insertion point forward to the nearest boundary that is not inside any BT/ET
        // span before doing anything else with it.
        Map<Integer, List<TextChunk>> boundaryAlignedPending =
            alignToTopLevelBoundaries(originalTokens, pendingByInsertIndex);

        // cumulativeShiftThrough[k] = total tokens that will end up inserted at or before original
        // index k — i.e. a pre-existing StreamInfo whose operatorIndex is k needs to become
        // k + cumulativeShiftThrough[k] to still point at the same physical operator afterward.
        int[] insertedCountAt = new int[originalLength + 1];
        for (Map.Entry<Integer, List<TextChunk>> entry : boundaryAlignedPending.entrySet()) {
            insertedCountAt[entry.getKey()] += entry.getValue().size() * TOKENS_PER_SYNTHESIZED_CHUNK;
        }
        int[] cumulativeShiftThrough = new int[originalLength + 1];
        int runningShift = 0;
        for (int i = 0; i <= originalLength; i++) {
            runningShift += insertedCountAt[i];
            cumulativeShiftThrough[i] = runningShift;
        }
        remapExistingStreamInfoIndexes(objects, cumulativeShiftThrough);

        List<Object> finalTokens = new ArrayList<>(originalLength + runningShift);
        for (int index = 0; index <= originalLength; index++) {
            List<TextChunk> chunksHere = boundaryAlignedPending.get(index);
            if (chunksHere != null) {
                for (TextChunk chunk : chunksHere) {
                    appendSynthesizedTextOperator(finalTokens, chunk);
                }
            }
            if (index < originalLength) {
                finalTokens.add(originalTokens.get(index));
            }
        }

        COSDocument cosDocument = StaticResources.getDocument().getDocument();
        COSObject newContents = AutoTaggingProcessor.createContentsIndirect(cosDocument, finalTokens);
        // Both the cached PDContentStream wrapper (what ChunksWriter.getTokens reads) and the
        // page dictionary's own /Contents key need updating — PDPage#getContent() returns a
        // cached field, not a live read of /Contents, so mutating only the dictionary would be
        // invisible to the later tagging pass.
        contentStream.setContents(newContents);
        page.getObject().setKey(ASAtom.CONTENTS, newContents);
        cosDocument.addChangedObject(page.getObject());
    }

    /** Path-construction operators — starting or extending a path that isn't painted/cleared yet. */
    private static final java.util.Set<String> PATH_CONSTRUCTION_OPERATORS =
        new HashSet<>(java.util.Arrays.asList("m", "l", "c", "v", "y", "h", "re"));

    /**
     * Path-painting (and no-op path-clearing, {@code n}) operators — every operator that consumes
     * and terminates whatever path is currently under construction.
     */
    private static final java.util.Set<String> PATH_PAINTING_OPERATORS = new HashSet<>(java.util.Arrays.asList(
        "S", "s", "f", "F", "f*", "B", "B*", "b", "b*", "n"));

    /**
     * Remaps every key in {@code pendingByInsertIndex} forward to the nearest index that is both
     * outside any BT/ET (text object) span and outside any open (unpainted) path-construction
     * sequence in {@code originalTokens}, merging insertion lists together whenever more than one
     * original key lands on the same aligned boundary.
     *
     * <p>Two distinct ways a naive insertion point corrupts real content, both found by rendering a
     * fixed page and diffing it pixel-for-pixel against the source PDF:
     * <ul>
     *   <li>PDF forbids nesting one text object inside another — an insertion point still inside an
     *   open BT would split a native text object in two, silently dropped or misrendered by a real
     *   content-stream interpreter even though the inserted operators are themselves invisible.</li>
     *   <li>A path built across several construction operators (e.g. {@code m l l l h}) and only
     *   painted at the very end (e.g. {@code f}) has no defined meaning if something else — even a
     *   fully self-contained, state-restoring {@code q ... Q} block — is spliced into the middle of
     *   it; this document's "Print to PDF" output draws headings/bullets as vector paths rather than
     *   font glyphs in several places, and splitting one of those silently dropped the visible shape
     *   entirely (observed as real bullet text vanishing from the rendered page).</li>
     *   <li>A splice inside an existing BMC/BDC ... EMC marked-content span would nest the inserted
     *   {@code q BT ... ET Q} block inside unrelated marked content instead of leaving it top-level.</li>
     * </ul>
     * BT/ET is not nestable per spec (at most one is open at a time) and a path is either open or
     * not, so a single forward scan tracking both as booleans is enough to find every span's end.
     * Marked content *is* nestable, so that's tracked as a depth counter instead of a boolean.
     */
    private static Map<Integer, List<TextChunk>> alignToTopLevelBoundaries(
            List<Object> originalTokens, Map<Integer, List<TextChunk>> pendingByInsertIndex) {
        int originalLength = originalTokens.size();
        // safeToInsertBefore[i] == true means index i (0..originalLength) is not inside a text
        // object, not in the middle of an unpainted path, and not inside marked content — a valid
        // place to splice.
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
                    markedContentDepth--;
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
     * Reflective handle onto {@code StreamInfo.operatorIndex} — see
     * {@link #remapExistingStreamInfoIndexes} for why this is mutated in place via reflection rather
     * than replaced with a new {@code StreamInfo} instance.
     *
     * <p>Resolved lazily by {@link #getStreamInfoOperatorIndexField()}, not eagerly at class-load —
     * deliberately, so that a verapdf-library version whose {@code StreamInfo} shape no longer
     * matches only breaks the one remap call that actually needed this field, not every code path
     * through this class (plain startup and OCR-disabled runs have no reason to care whether this
     * internal field still exists).
     */
    private static java.lang.reflect.Field streamInfoOperatorIndexField;

    /**
     * Lazily resolves and caches {@link #streamInfoOperatorIndexField}, called only when
     * {@link #remapExistingStreamInfoIndexes} actually needs to shift a {@code StreamInfo}.
     *
     * <p><b>Fails closed</b>: a lookup or {@code setAccessible} failure throws
     * {@code IllegalStateException} instead of silently skipping the remap, which would leave a
     * stale index and quietly reintroduce this file's reading-order bug. Also catches
     * {@code RuntimeException} to cover {@code InaccessibleObjectException} ({@code setAccessible}
     * can throw this unchecked on Java 9+ for an unopened module).
     */
    private static synchronized java.lang.reflect.Field getStreamInfoOperatorIndexField() {
        if (streamInfoOperatorIndexField == null) {
            try {
                java.lang.reflect.Field field = StreamInfo.class.getDeclaredField("operatorIndex");
                field.setAccessible(true);
                streamInfoOperatorIndexField = field;
            } catch (NoSuchFieldException | RuntimeException e) {
                throw new IllegalStateException(
                    "org.verapdf.wcag.algorithms.entities.content.StreamInfo no longer exposes a "
                    + "reflectively-accessible 'operatorIndex' field on this verapdf-library version — "
                    + "incompatible with HybridDocumentProcessor's OCR-fallback content-stream remap. "
                    + "Update the reflection target (or the remap strategy) for the new StreamInfo shape.",
                    e);
            }
        }
        return streamInfoOperatorIndexField;
    }

    /**
     * Remaps the {@code operatorIndex} of every pre-existing, main-content-stream StreamInfo
     * reachable from {@code objects} (text, image, and formula alike — see {@link #forEachIObject})
     * via {@code cumulativeShiftThrough} (see {@link #rewritePageContentStream}). Entries scoped to
     * a Form XObject's own stream ({@code xObjectName != null}) are left alone, since their indices
     * are relative to a stream this method never touches.
     *
     * <p><b>Mutates the existing StreamInfo object's field via reflection, in place — deliberately,
     * not replaces it with a new instance.</b> {@code StreamInfo} exposes no
     * {@code setOperatorIndex}, so replacing each shifted entry with
     * {@code new StreamInfo(shifted, ...)} swapped into the chunk's own list (via
     * {@code streamInfos.set(i, ...)}) looks like the natural fix, and the shift arithmetic itself
     * is correct under that approach too (verified exhaustively — every original token really does
     * land at {@code original + cumulativeShiftThrough[original]} in the rewritten stream). But it
     * silently broke real content anyway: rendering a fixed page and diffing it against the source
     * PDF showed native, visible bullet text vanishing specifically in regions *after* at least one
     * insertion had already happened earlier on the same page — i.e., exactly the entries whose
     * StreamInfo actually got replaced (an entry with zero shift is left untouched either way).
     * Something later in the pipeline evidently holds its own reference to the original
     * {@code StreamInfo} instance rather than re-reading {@code chunk.getStreamInfos()} fresh, so a
     * replacement orphans that reference on the stale, pre-shift index while the chunk's own list
     * moves on — silently splitting or dropping whatever content that stale index ends up
     * mis-resolving to once {@code AutoTaggingProcessor} re-parses the final stream. Mutating in
     * place keeps every existing reference — wherever it lives — pointing at the same object with
     * the corrected value, side-stepping the identity mismatch entirely. MCID has not been assigned
     * to any StreamInfo yet at this point in the pipeline (that happens later, during
     * {@code AutoTaggingProcessor.tagDocument}), so there's no other field this could disturb.
     *
     * <p>Tracks visited instances by identity ({@code visited}) so a {@code StreamInfo} reachable
     * more than once in a single {@link #forEachIObject} walk is shifted exactly once. The same
     * aliasing this method's in-place mutation is designed around (see above — some other part of
     * the pipeline holds its own reference to a given {@code StreamInfo} rather than re-reading the
     * owning chunk's list fresh) means the reverse can happen too: two different {@code IObject}s
     * visited by this same walk can point at the *same* {@code StreamInfo} instance. Without the
     * dedup, a second visit would read the already-shifted index as {@code original} and shift it
     * again, silently displacing that content a second time.
     */
    private static void remapExistingStreamInfoIndexes(List<IObject> objects, int[] cumulativeShiftThrough) {
        Set<StreamInfo> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        forEachIObject(objects, obj -> {
            for (StreamInfo streamInfo : obj.getStreamInfos()) {
                if (streamInfo.getXObjectName() != null) {
                    continue;
                }
                if (!visited.add(streamInfo)) {
                    continue;
                }
                int original = streamInfo.getOperatorIndex();
                if (original < 0 || original >= cumulativeShiftThrough.length) {
                    continue;
                }
                int shifted = original + cumulativeShiftThrough[original];
                if (shifted != original) {
                    try {
                        getStreamInfoOperatorIndexField().setInt(streamInfo, shifted);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(
                            "Failed to remap StreamInfo.operatorIndex via reflection", e);
                    }
                }
            }
        });
    }

    /**
     * Appends one chunk's synthesized invisible text-showing operator sequence to {@code tokens}
     * (being built into the page's new content stream — see {@link #rewritePageContentStream}) and
     * attaches a matching {@link StreamInfo} to the chunk pointing at the resulting {@code Tj}
     * operator's index in that same list — matches how {@code ChunksWriter.processTokens} later
     * indexes operators when it re-parses this exact list once serialized to bytes and back.
     *
     * <p>ASCII-only: non-ASCII characters are replaced with {@code '?'}, since a non-embedded
     * Standard-14 base font's default encoding has no wider coverage, and full Unicode font
     * embedding is out of scope for this fix (the same problem the retired downstream
     * `TaggedPdfWriterAdapter` tool needed a bundled TrueType font for).
     *
     * <p>Wrapped in {@code q ... Q} (save/restore graphics state), not just {@code BT ... ET}: text
     * state parameters set inside a text object — font ({@code Tf}), rendering mode ({@code Tr}),
     * the text matrix — are graphics state and persist across {@code ET}; {@code BT} only resets the
     * text matrix, nothing else. Without {@code q}/{@code Q}, this block's {@code 3 Tr} (invisible)
     * and font selection would leak into whatever native content follows it in the rewritten stream,
     * silently turning subsequent real, visible text invisible until the next operator that happens
     * to set {@code Tr} again. Confirmed by rendering a fixed page and diffing it against the source
     * PDF: real bullet text that should have been visible was missing exactly where a fallback
     * insertion preceded it, even though the fallback text itself is correctly invisible.
     */
    private static void appendSynthesizedTextOperator(List<Object> tokens, TextChunk chunk) {
        String text = toAsciiOrReplacementChar(chunk.getValue());
        tokens.add(Operator.getOperator(Operators.Q_GSAVE));
        tokens.add(Operator.getOperator(Operators.BT));
        tokens.add(COSName.construct(OCR_FALLBACK_FONT_NAME).getDirectBase());
        tokens.add(COSReal.construct(OCR_FALLBACK_FONT_SIZE).getDirectBase());
        tokens.add(Operator.getOperator(Operators.TF));
        tokens.add(COSInteger.construct(3).getDirectBase()); // Tr 3 = invisible: no fill, no stroke, no clip
        tokens.add(Operator.getOperator(Operators.TR));
        tokens.add(COSReal.construct(1).getDirectBase());
        tokens.add(COSReal.construct(0).getDirectBase());
        tokens.add(COSReal.construct(0).getDirectBase());
        tokens.add(COSReal.construct(1).getDirectBase());
        tokens.add(COSReal.construct(chunk.getLeftX()).getDirectBase());
        tokens.add(COSReal.construct(chunk.getBottomY()).getDirectBase());
        tokens.add(Operator.getOperator(Operators.TM));
        tokens.add(COSString.construct(text.getBytes(StandardCharsets.US_ASCII), false).getDirectBase());
        int operatorIndex = tokens.size();
        tokens.add(Operator.getOperator(Operators.TJ_SHOW));
        tokens.add(Operator.getOperator(Operators.ET));
        tokens.add(Operator.getOperator(Operators.Q_GRESTORE));

        // Single StreamInfo covering the whole synthesized string — no splitting needed since
        // this operator exists solely for this one chunk.
        chunk.getStreamInfos().add(new StreamInfo(operatorIndex, null, 0, text.length()));
    }

    /**
     * Ensures the page has an {@link #OCR_FALLBACK_FONT_NAME} font resource, adding a minimal
     * Standard-14 Helvetica entry if one isn't already present.
     *
     * <p>If the page has no {@code /Resources} of its own (inherited from an ancestor /Pages node,
     * or missing entirely), {@link #localizePageResources} gives it a page-owned copy first — this
     * used to mutate whatever {@code PDPage#getResources()} returned in place, which silently
     * leaked this font (and a page-owned {@code /Font} dict) onto every sibling page sharing that
     * ancestor's resources.
     */
    private static void ensureOcrFallbackFont(PDPage page) {
        PDResources resources = page.getResources();
        if (resources != null && resources.getFont(OCR_FALLBACK_FONT_NAME) != null) {
            return;
        }
        if (resources == null || Boolean.TRUE.equals(page.isInheritedResources())) {
            resources = localizePageResources(page, resources);
        }
        COSObject resourcesObj = resources.getObject();
        COSObject fontDictObj = resourcesObj.getKey(ASAtom.getASAtom("Font"));
        if (fontDictObj == null || fontDictObj.empty()) {
            fontDictObj = COSDictionary.construct();
            resourcesObj.setKey(ASAtom.getASAtom("Font"), fontDictObj);
        }
        COSObject fontEntry = COSDictionary.construct();
        fontEntry.setKey(ASAtom.TYPE, COSName.construct(ASAtom.getASAtom("Font")));
        fontEntry.setKey(ASAtom.getASAtom("Subtype"), COSName.construct(ASAtom.getASAtom("Type1")));
        fontEntry.setKey(ASAtom.getASAtom("BaseFont"), COSName.construct(ASAtom.getASAtom("Helvetica")));
        fontDictObj.setKey(OCR_FALLBACK_FONT_NAME, fontEntry);
    }

    /**
     * Gives {@code page} its own {@code /Resources} dictionary — a copy of {@code inherited} (or
     * empty, if the page had none) — and assigns it via {@code page.setResources}, which also
     * writes the page's own {@code /Resources} key. Top-level entries are copied by reference
     * (their targets aren't mutated, so sharing them back is fine); {@code /Font} is deep-copied
     * one level so {@link #ensureOcrFallbackFont}'s write can't land in a Font dict another page
     * still shares.
     */
    private static PDResources localizePageResources(PDPage page, PDResources inherited) {
        COSObject localResourcesObj = COSDictionary.construct();
        if (inherited != null) {
            COSObject inheritedObj = inherited.getObject();
            for (ASAtom key : inheritedObj.getKeySet()) {
                localResourcesObj.setKey(key, inheritedObj.getKey(key));
            }
            COSObject inheritedFontDict = inheritedObj.getKey(ASAtom.getASAtom("Font"));
            if (inheritedFontDict != null && !inheritedFontDict.empty()) {
                COSObject localFontDict = COSDictionary.construct();
                for (ASAtom fontName : inheritedFontDict.getKeySet()) {
                    localFontDict.setKey(fontName, inheritedFontDict.getKey(fontName));
                }
                localResourcesObj.setKey(ASAtom.getASAtom("Font"), localFontDict);
            }
        }
        PDResources localResources = new PDResources(localResourcesObj);
        page.setResources(localResources);
        return localResources;
    }

    /**
     * Replaces every character outside printable ASCII with {@code '?'}. A non-embedded Standard-14
     * base font's default encoding (used by {@link #appendSynthesizedTextOperator}) has no
     * broader coverage — see that method's doc comment for why full Unicode font embedding is out
     * of scope here.
     */
    private static String toAsciiOrReplacementChar(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sb.append((c >= 0x20 && c <= 0x7E) ? c : '?');
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
    private static void enrichTextStreamInfos(List<IObject> backendPage, List<TextChunk> javaTextChunks,
                                               HybridConfig config) {
        if (javaTextChunks.isEmpty()) return;

        Set<Integer> usedJavaIndices = new HashSet<>();
        enrichTextStreamInfosRecursive(backendPage, javaTextChunks, usedJavaIndices, config);
    }

    /**
     * Recursively walks the IObject tree and replaces TextChunks in SemanticTextNodes
     * with Java TextChunks that carry StreamInfo. Handles TableBorder cells, PDFList items,
     * and any other container that holds nested IObjects.
     */
    private static void enrichTextStreamInfosRecursive(
            List<IObject> objects, List<TextChunk> javaTextChunks, Set<Integer> usedJavaIndices,
            HybridConfig config) {

        for (IObject obj : objects) {
            if (obj instanceof SemanticFormula) {
                // Formula inside table/list — enrich StreamInfos by bbox overlap
                enrichSingleFormula((SemanticFormula) obj, javaTextChunks);
            } else if (obj instanceof SemanticTextNode) {
                enrichSingleTextNode((SemanticTextNode) obj, javaTextChunks, usedJavaIndices, config);
            } else if (obj instanceof TableBorder) {
                TableBorder table = (TableBorder) obj;
                for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
                    TableBorderRow row = table.getRow(rowNumber);
                    for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                        TableBorderCell cell = row.getCell(colNumber);
                        if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                            enrichTextStreamInfosRecursive(cell.getContents(), javaTextChunks, usedJavaIndices, config);
                        }
                    }
                }
            } else if (obj instanceof PDFList) {
                PDFList list = (PDFList) obj;
                for (ListItem item : list.getListItems()) {
                    // Enrich ListItem's own text lines (used by AutoTaggingProcessor for Lbl/LBody)
                    if (config == null || !config.isOcrForce()) {
                        for (TextLine line : item.getLines()) {
                            for (TextChunk backendChunk : line.getTextChunks()) {
                                if (backendChunk.getStreamInfos().isEmpty()) {
                                    matchAndReplaceStreamInfos(backendChunk, javaTextChunks, usedJavaIndices, config);
                                }
                            }
                        }
                    }
                    // Enrich nested contents (images, paragraphs, sub-lists)
                    enrichTextStreamInfosRecursive(item.getContents(), javaTextChunks, usedJavaIndices, config);
                }
            }
        }
    }

    /**
     * Replaces a single SemanticTextNode's TextChunks with matching Java TextChunks.
     * In OCR auto mode, compares stream vs OCR text similarity before deciding.
     * In OCR force mode, always keeps OCR text (backend TextChunks).
     * Records the text source decision in ElementMetadata.
     */
    private static void enrichSingleTextNode(
            SemanticTextNode textNode, List<TextChunk> javaTextChunks, Set<Integer> usedJavaIndices,
            HybridConfig config) {

        // Force mode: always keep OCR text, don't replace with stream
        if (config != null && config.isOcrForce()) {
            recordTextSource(textNode, "ocr", null);
            return;
        }

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
            LOGGER.fine(() -> "enrichSingleTextNode: no Java TextChunk matched for backend node at bbox ["
                + String.format("%.1f,%.1f,%.1f,%.1f", nLeft, nBottom, nRight, nTop) + "]");
            recordTextSource(textNode, "ocr-fallback", null);
            return;
        }

        // Auto mode: compare stream vs OCR text similarity
        if (config != null && config.isOcrAuto()) {
            String streamText = extractTextFromChunks(matched);
            String ocrText = extractTextFromNode(textNode);
            double sim = TextSimilarity.similarity(streamText, ocrText);

            if (!TextSimilarity.trustStream(streamText, ocrText, TextSimilarity.DEFAULT_THRESHOLD)) {
                // Stream text is corrupted — keep OCR text (backend TextChunks)
                LOGGER.fine(() -> "OCR auto: stream text untrusted (sim="
                    + String.format("%.2f", sim)
                    + "), keeping OCR for node at ["
                    + String.format("%.1f,%.1f,%.1f,%.1f", nLeft, nBottom, nRight, nTop) + "]");
                recordTextSource(textNode, "ocr", sim);
                return;
            }
            recordTextSource(textNode, "stream", sim);
        } else {
            recordTextSource(textNode, "stream", null);
        }

        // Off mode or auto mode with trusted stream: replace with Java TextChunks
        textNode.getColumns().clear();
        TextColumn newCol = new TextColumn();
        for (TextChunk tc : matched) {
            newCol.add(new TextLine(tc));
        }
        textNode.getColumns().add(newCol);
    }

    /**
     * Records "ocr" as the text source for every SemanticTextNode in the page,
     * used on scanned pages where there are no Java TextChunks to compare with.
     * Mirrors {@link #enrichTextStreamInfosRecursive} in how it descends into
     * TableBorder/PDFList — the shared IObject tree has no generic children API,
     * so both walks enumerate the containers they know about.
     */
    private static void markAllTextSourcesAsOcr(List<IObject> objects) {
        if (objects == null) return;
        for (IObject obj : objects) {
            if (obj instanceof SemanticTextNode) {
                recordTextSource((SemanticTextNode) obj, "ocr", null);
            } else if (obj instanceof TableBorder) {
                TableBorder table = (TableBorder) obj;
                for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
                    TableBorderRow row = table.getRow(rowNumber);
                    for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                        TableBorderCell cell = row.getCell(colNumber);
                        if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                            markAllTextSourcesAsOcr(cell.getContents());
                        }
                    }
                }
            } else if (obj instanceof PDFList) {
                PDFList list = (PDFList) obj;
                for (ListItem item : list.getListItems()) {
                    markAllTextSourcesAsOcr(item.getContents());
                }
            }
        }
    }

    /**
     * Records the text source decision in ElementMetadata for a SemanticTextNode.
     *
     * @param textNode   the text node whose metadata to update
     * @param source     "stream", "ocr", or "ocr-fallback"
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
     * Extracts concatenated text from a list of TextChunks.
     */
    private static String extractTextFromChunks(List<TextChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        for (TextChunk tc : chunks) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(tc.getValue());
        }
        return sb.toString().trim();
    }

    /**
     * Extracts concatenated text from a SemanticTextNode's columns/lines/chunks.
     */
    private static String extractTextFromNode(SemanticTextNode node) {
        StringBuilder sb = new StringBuilder();
        for (TextColumn col : node.getColumns()) {
            for (TextLine line : col.getLines()) {
                for (TextChunk chunk : line.getTextChunks()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(chunk.getValue());
                }
            }
        }
        return sb.toString().trim();
    }

    /**
     * Copies StreamInfos from matching Java TextChunks to a backend TextChunk that lacks them.
     * Used for ListItem lines where the text is stored directly in TextLine/TextChunk
     * rather than in a SemanticTextNode wrapper.
     * In OCR auto mode, compares stream vs OCR text similarity before copying.
     */
    private static void matchAndReplaceStreamInfos(
            TextChunk backendChunk, List<TextChunk> javaTextChunks, Set<Integer> usedJavaIndices,
            HybridConfig config) {
        double bCx = backendChunk.getCenterX();
        double bCy = backendChunk.getCenterY();
        double tol = 5.0;
        for (int i = 0; i < javaTextChunks.size(); i++) {
            if (usedJavaIndices.contains(i)) continue;
            TextChunk javaChunk = javaTextChunks.get(i);
            if (javaChunk.getStreamInfos().isEmpty()) continue;
            double jCx = javaChunk.getCenterX();
            double jCy = javaChunk.getCenterY();
            if (Math.abs(bCx - jCx) <= tol && Math.abs(bCy - jCy) <= tol) {
                // In auto mode, check if stream text is trustworthy
                if (config != null && config.isOcrAuto()) {
                    String streamText = javaChunk.getValue();
                    String ocrText = backendChunk.getValue();
                    if (!TextSimilarity.trustStream(streamText, ocrText, TextSimilarity.DEFAULT_THRESHOLD)) {
                        LOGGER.fine(() -> "OCR auto: stream text untrusted for ListItem chunk, keeping OCR");
                        return;
                    }
                }
                backendChunk.getStreamInfos().addAll(javaChunk.getStreamInfos());
                usedJavaIndices.add(i);
                return;
            }
        }
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
     * @param outputDir      The output directory for the triage log.
     */
    private static void logTriageToFile(
            String inputPdfName,
            String hybridBackend,
            Map<Integer, TriageResult> triageResults,
            Path outputDir) {

        try {
            String documentName = Path.of(inputPdfName).getFileName().toString();
            TriageLogger triageLogger = new TriageLogger();
            triageLogger.logToFile(outputDir, documentName, hybridBackend, triageResults);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to write triage log: {0}", e.getMessage());
        }
    }
}

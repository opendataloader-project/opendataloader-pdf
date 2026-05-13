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
import org.opendataloader.pdf.containers.StaticLayoutContainers;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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

    /** Returns the hybrid server timings from the most recent {@link #processDocument} call. */
    public static JsonNode getLastHybridTimings() {
        return lastHybridTimings;
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

        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        LOGGER.log(Level.INFO, "Starting hybrid processing for {0} pages", totalPages);

        if (pagesToProcess != null && pagesToProcess.isEmpty()) {
            LOGGER.log(Level.INFO, "Skipping hybrid processing because no valid pages were selected");
            return createEmptyContents(totalPages);
        }

        // Phase 0: Check backend availability before any processing.
        // Runs before triage intentionally — if the user explicitly requested hybrid mode,
        // they expect the server to be available regardless of how pages would be routed.
        getClient(config).checkAvailability();

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
        try {
            backendResults = processBackendPath(inputPdfName, backendPages, config, backendFailedPages);
            // Enrich backend results: copy StreamInfos from Java-extracted content for MCID linkage
            enrichBackendResults(backendResults, filteredContents, config.getHybridConfig());
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
     * making backend failures invisible to automation. See PDFDLOSP-11.
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
                pageContents = TableBorderProcessor.processTableBorders(pageContents, pageNumber);
                if (config.isDetectStrikethrough()) {
                    StrikethroughProcessor.processStrikethroughs(pageContents);
                }
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
                HybridRequest request = HybridRequest.forPages(pdfBytes, chunkPages1Indexed, outputFormats);
                HybridResponse response = client.convert(request);

                // Capture hybrid server pipeline timings (last chunk wins for now;
                // in single-chunk documents this is exact)
                if (response.getTimings() != null) {
                    lastHybridTimings = response.getTimings();
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
                        DocumentProcessor.setIDs(pageContents);
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
            HybridConfig hybridConfig) {

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
                            enriched.add(new EnrichedImageChunk(matched, picture.getDescription()));
                        } else {
                            LOGGER.fine(() -> "Page " + pageNumber + ": dropped SemanticPicture (no matching ImageChunk) at bbox ["
                                + String.format("%.1f,%.1f,%.1f,%.1f", picture.getLeftX(), picture.getBottomY(), picture.getRightX(), picture.getTopY()) + "]");
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

            // OCR fallback: log elements that still lack StreamInfo after enrichment
            if (hybridConfig.isOcrAuto() || hybridConfig.isOcrForce()) {
                Map<Integer, List<OcrWordInfo>> ocrWords = lastOcrWordsByPage;
                if (ocrWords != null) {
                    List<OcrWordInfo> pageOcrWords = ocrWords.getOrDefault(pageNumber, List.of());
                    logOcrFallbackCandidates(backendPage, pageNumber, pageOcrWords);
                }
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
     * <p>This is Phase A (logging only). Phase B will actually insert invisible
     * text operators into the content stream for these elements.
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

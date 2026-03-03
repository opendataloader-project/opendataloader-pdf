/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.hybrid.DoclingSchemaTransformer;
import org.opendataloader.pdf.hybrid.HancomSchemaTransformer;
import org.opendataloader.pdf.hybrid.HybridClient;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.opendataloader.pdf.hybrid.HybridClient.OutputFormat;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.hybrid.HybridSchemaTransformer;
import org.opendataloader.pdf.hybrid.TriageLogger;
import org.opendataloader.pdf.hybrid.TriageProcessor;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
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

        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        LOGGER.log(Level.INFO, "Starting hybrid processing for {0} pages", totalPages);

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

        // Phase 4: Process Java path and Backend path concurrently
        List<List<IObject>> contents = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            contents.add(new ArrayList<>());
        }

        // Start backend fetch FIRST as async (pure IO, no StaticContainers dependency)
        CompletableFuture<BackendFetchResult> backendFuture;
        if (!backendPages.isEmpty()) {
            backendFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return fetchFromBackend(inputPdfName, backendPages, config);
                } catch (IOException e) {
                    throw new CompletionException(e);
                }
            });
        } else {
            backendFuture = CompletableFuture.completedFuture(BackendFetchResult.empty());
        }

        // Run Java path on the MAIN thread (preserves StaticContainers state)
        Map<Integer, List<IObject>> javaResults = processJavaPath(
            filteredContents, javaPages, config, totalPages
        );

        // Join backend result on main thread, then process response
        Map<Integer, List<IObject>> backendResults;
        Set<Integer> backendFailedPages = new HashSet<>();
        try {
            BackendFetchResult fetchResult = backendFuture.join();
            backendResults = processBackendResponse(fetchResult, config, backendFailedPages);
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOGGER.log(Level.WARNING, "Backend processing failed: {0}", cause.getMessage());
            if (config.getHybridConfig().isFallbackToJava()) {
                LOGGER.log(Level.INFO, "Falling back to Java processing for backend pages");
                backendResults = processJavaPath(filteredContents, backendPages, config, totalPages);
            } else {
                throw new IOException("Backend processing failed and fallback is disabled", cause);
            }
        }

        // Fallback: reprocess backend-failed pages through Java path
        if (!backendFailedPages.isEmpty()) {
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
                LOGGER.log(Level.WARNING, "Backend returned partial_success: {0} page(s) failed (pages {1}), fallback disabled — skipping failed pages",
                    new Object[]{backendFailedPages.size(), failedPages1Indexed});
            }
        }

        // Phase 5: Merge results
        mergeResults(contents, javaResults, backendResults, pagesToProcess, totalPages);

        // Phase 6: Post-processing (cross-page operations)
        postProcess(contents, config, pagesToProcess, totalPages);

        return contents;
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

        // Phase A-1: Table borders — sequential (shared TableBordersCollection state)
        for (int pageNumber : pageNumbers) {
            try {
                List<IObject> pageContents = workingContents.get(pageNumber);
                pageContents = TableBorderProcessor.processTableBorders(pageContents, pageNumber);
                pageContents = pageContents.stream()
                    .filter(x -> !(x instanceof LineChunk))
                    .collect(Collectors.toList());
                workingContents.set(pageNumber, pageContents);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing table borders for page {0}: {1}",
                    new Object[]{pageNumber, e.getMessage()});
            }
        }

        // Phase A-2: Text line processing — parallel (independent per page, most expensive)
        pageNumbers.parallelStream().forEach(pageNumber -> {
            try {
                workingContents.set(pageNumber,
                    TextLineProcessor.processTextLines(workingContents.get(pageNumber)));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing text lines for page {0}: {1}",
                    new Object[]{pageNumber, e.getMessage()});
            }
        });

        // Phase A-3: Special tables — sequential (calls TableBorderProcessor internally)
        for (int pageNumber : pageNumbers) {
            try {
                workingContents.set(pageNumber,
                    SpecialTableProcessor.detectSpecialTables(workingContents.get(pageNumber)));
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error detecting special tables for page {0}: {1}",
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
     * Paragraph and list processing run in parallel; heading, ID, and caption
     * processing run sequentially due to ThreadLocal order dependencies.
     */
    private static void applyJavaPagePostProcessing(List<List<IObject>> contents, Set<Integer> pageNumbers) {
        // Phase B-1: Paragraph + List — parallel (independent per page)
        pageNumbers.parallelStream().forEach(pageNumber -> {
            List<IObject> pageContents = contents.get(pageNumber);
            pageContents = ParagraphProcessor.processParagraphs(pageContents);
            pageContents = ListProcessor.processListsFromTextNodes(pageContents);
            contents.set(pageNumber, pageContents);
        });

        // Phase B-2: Headings + IDs + Captions — sequential (ThreadLocal order dependency)
        for (int pageNumber : pageNumbers) {
            List<IObject> pageContents = contents.get(pageNumber);
            HeadingProcessor.processHeadings(pageContents, false);
            DocumentProcessor.setIDs(pageContents);
            CaptionProcessor.processCaptions(pageContents);
            contents.set(pageNumber, pageContents);
        }
    }

    /**
     * Fetches conversion results from the backend. Thread-safe — only performs IO.
     *
     * <p>When the number of pages exceeds maxConcurrentRequests, pages are split into
     * chunks and sent as concurrent HTTP requests for better throughput.
     *
     * @param inputPdfName The path to the input PDF file.
     * @param pageNumbers  Set of 0-indexed page numbers to process.
     * @param config       The configuration settings.
     * @return The backend fetch result containing the response(s).
     * @throws IOException If an error occurs during the request.
     */
    private static BackendFetchResult fetchFromBackend(
            String inputPdfName,
            Set<Integer> pageNumbers,
            Config config) throws IOException {

        if (pageNumbers.isEmpty()) {
            return BackendFetchResult.empty();
        }

        LOGGER.log(Level.INFO, "Processing {0} pages via {1} backend",
            new Object[]{pageNumbers.size(), config.getHybrid()});

        HybridClient client = getClient(config);
        byte[] pdfBytes = Files.readAllBytes(Path.of(inputPdfName));
        Set<OutputFormat> outputFormats = determineOutputFormats(config);

        int maxConcurrent = config.getHybridConfig().getMaxConcurrentRequests();

        if (pageNumbers.size() <= maxConcurrent) {
            // Single request for small page sets
            HybridRequest request = HybridRequest.allPages(pdfBytes, outputFormats);
            HybridResponse response = client.convert(request);
            return new BackendFetchResult(List.of(response), pageNumbers);
        }

        // Split pages into chunks and send concurrent requests
        List<Set<Integer>> chunks = splitIntoChunks(pageNumbers, maxConcurrent);
        LOGGER.log(Level.INFO, "Splitting {0} backend pages into {1} concurrent requests",
            new Object[]{pageNumbers.size(), chunks.size()});

        List<CompletableFuture<HybridResponse>> futures = new ArrayList<>();
        for (Set<Integer> chunk : chunks) {
            // Convert 0-indexed to 1-indexed for HybridRequest
            Set<Integer> chunk1Indexed = chunk.stream()
                .map(p -> p + 1)
                .collect(Collectors.toSet());
            HybridRequest request = HybridRequest.forPages(pdfBytes, chunk1Indexed, outputFormats);
            futures.add(client.convertAsync(request));
        }

        // Wait for all chunk requests to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<HybridResponse> responses = new ArrayList<>();
        for (CompletableFuture<HybridResponse> future : futures) {
            responses.add(future.join());
        }

        return new BackendFetchResult(responses, pageNumbers);
    }

    /**
     * Splits a set of page numbers into N approximately equal chunks.
     */
    private static List<Set<Integer>> splitIntoChunks(Set<Integer> pageNumbers, int numChunks) {
        List<Integer> sorted = pageNumbers.stream().sorted().collect(Collectors.toList());
        List<Set<Integer>> chunks = new ArrayList<>();
        int chunkSize = Math.max(1, (sorted.size() + numChunks - 1) / numChunks);

        for (int i = 0; i < sorted.size(); i += chunkSize) {
            Set<Integer> chunk = new HashSet<>(sorted.subList(i, Math.min(i + chunkSize, sorted.size())));
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * Processes the backend fetch result into IObjects. Must run on the main thread
     * because setIDs() uses StaticLayoutContainers (ThreadLocal).
     *
     * @param fetchResult        The result from fetchFromBackend().
     * @param config             The configuration settings.
     * @param backendFailedPages Output parameter: populated with 0-indexed page numbers
     *                           that failed during backend processing.
     * @return Map of page number to IObject list for successfully processed pages.
     */
    private static Map<Integer, List<IObject>> processBackendResponse(
            BackendFetchResult fetchResult,
            Config config,
            Set<Integer> backendFailedPages) {

        if (fetchResult.isEmpty()) {
            return new HashMap<>();
        }

        Set<Integer> pageNumbers = fetchResult.getPageNumbers();

        // Collect failed pages from all responses (convert from 1-indexed to 0-indexed)
        for (HybridResponse response : fetchResult.getResponses()) {
            if (response.hasFailedPages()) {
                for (int failedPage1Indexed : response.getFailedPages()) {
                    int failedPage0Indexed = failedPage1Indexed - 1;
                    if (pageNumbers.contains(failedPage0Indexed)) {
                        backendFailedPages.add(failedPage0Indexed);
                    }
                }
            }
        }

        // Get page heights for coordinate transformation
        Map<Integer, Double> pageHeights = getPageHeights(pageNumbers);

        // Transform each response and merge results
        HybridSchemaTransformer transformer = createTransformer(config);
        Map<Integer, List<IObject>> results = new HashMap<>();

        for (HybridResponse response : fetchResult.getResponses()) {
            List<List<IObject>> transformedContents = transformer.transform(response, pageHeights);

            for (int pageNumber : pageNumbers) {
                if (results.containsKey(pageNumber) || backendFailedPages.contains(pageNumber)) {
                    continue;
                }
                if (pageNumber < transformedContents.size()) {
                    List<IObject> pageContents = transformedContents.get(pageNumber);
                    if (pageContents != null && !pageContents.isEmpty()) {
                        TextProcessor.replaceUndefinedCharacters(pageContents, config.getReplaceInvalidChars());
                        DocumentProcessor.setIDs(pageContents);
                        results.put(pageNumber, pageContents);
                    }
                }
            }
        }

        // Fill missing pages with empty lists
        for (int pageNumber : pageNumbers) {
            if (!results.containsKey(pageNumber) && !backendFailedPages.contains(pageNumber)) {
                results.put(pageNumber, new ArrayList<>());
            }
        }

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

    /**
     * Holds the result of fetching from the backend, which may include multiple
     * responses when pages are split into concurrent chunk requests.
     */
    private static class BackendFetchResult {
        private final List<HybridResponse> responses;
        private final Set<Integer> pageNumbers;

        BackendFetchResult(List<HybridResponse> responses, Set<Integer> pageNumbers) {
            this.responses = responses;
            this.pageNumbers = pageNumbers;
        }

        static BackendFetchResult empty() {
            return new BackendFetchResult(Collections.emptyList(), Collections.emptySet());
        }

        boolean isEmpty() {
            return responses.isEmpty() || pageNumbers.isEmpty();
        }

        List<HybridResponse> getResponses() {
            return responses;
        }

        Set<Integer> getPageNumbers() {
            return pageNumbers;
        }
    }
}

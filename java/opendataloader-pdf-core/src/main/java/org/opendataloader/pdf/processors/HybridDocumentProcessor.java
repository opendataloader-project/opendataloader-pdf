/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.hybrid.DoclingClient;
import org.opendataloader.pdf.hybrid.DoclingSchemaTransformer;
import org.opendataloader.pdf.hybrid.HybridClient;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

        // Phase 1: Filter all pages and collect filtered contents
        Map<Integer, List<IObject>> filteredContents = filterAllPages(inputPdfName, config, pagesToProcess, totalPages);

        // Phase 2: Triage all pages
        Map<Integer, TriageResult> triageResults = TriageProcessor.triageAllPages(
            filteredContents, config.getHybridConfig()
        );

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

        // Phase 4: Process in parallel
        List<List<IObject>> contents = new ArrayList<>();
        for (int i = 0; i < totalPages; i++) {
            contents.add(new ArrayList<>());
        }

        // Start backend processing asynchronously
        CompletableFuture<Map<Integer, List<IObject>>> backendFuture = processBackendPathAsync(
            inputPdfName, backendPages, config, totalPages
        );

        // Process Java path while backend is running
        Map<Integer, List<IObject>> javaResults = processJavaPath(
            filteredContents, javaPages, config, totalPages
        );

        // Wait for backend results
        Map<Integer, List<IObject>> backendResults;
        try {
            backendResults = backendFuture.join();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Backend processing failed: {0}", e.getMessage());
            if (config.getHybridConfig().isFallbackToJava()) {
                LOGGER.log(Level.INFO, "Falling back to Java processing for backend pages");
                backendResults = processJavaPath(filteredContents, backendPages, config, totalPages);
            } else {
                throw new IOException("Backend processing failed and fallback is disabled", e);
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

        // Process each page through the standard Java pipeline
        ExecutorService executor = Executors.newFixedThreadPool(
            Math.min(pageNumbers.size(), Runtime.getRuntime().availableProcessors())
        );

        try {
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int pageNumber : pageNumbers) {
                final int pn = pageNumber;
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        List<IObject> pageContents = workingContents.get(pn);
                        pageContents = TableBorderProcessor.processTableBorders(pageContents, pn);
                        pageContents = pageContents.stream()
                            .filter(x -> !(x instanceof LineChunk))
                            .collect(Collectors.toList());
                        pageContents = TextLineProcessor.processTextLines(pageContents);
                        pageContents = SpecialTableProcessor.detectSpecialTables(pageContents);
                        workingContents.set(pn, pageContents);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error processing page {0}: {1}",
                            new Object[]{pn, e.getMessage()});
                    }
                }, executor));
            }

            // Wait for all page processing to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } finally {
            executor.shutdown();
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
     * Processes pages using the external backend asynchronously.
     */
    private static CompletableFuture<Map<Integer, List<IObject>>> processBackendPathAsync(
            String inputPdfName,
            Set<Integer> pageNumbers,
            Config config,
            int totalPages) {

        if (pageNumbers.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                return processBackendPath(inputPdfName, pageNumbers, config, totalPages);
            } catch (IOException e) {
                throw new RuntimeException("Backend processing failed", e);
            }
        });
    }

    /**
     * Processes pages using the external backend.
     */
    private static Map<Integer, List<IObject>> processBackendPath(
            String inputPdfName,
            Set<Integer> pageNumbers,
            Config config,
            int totalPages) throws IOException {

        LOGGER.log(Level.INFO, "Processing {0} pages via {1} backend",
            new Object[]{pageNumbers.size(), config.getHybrid()});

        // Create client based on backend type
        HybridClient client = createClient(config);

        // Check if backend is available
        if (!client.isAvailable()) {
            LOGGER.log(Level.WARNING, "Backend {0} is not available", config.getHybrid());
            if (config.getHybridConfig().isFallbackToJava()) {
                throw new IOException("Backend not available");
            }
        }

        // Read PDF bytes
        byte[] pdfBytes = Files.readAllBytes(Path.of(inputPdfName));

        // Convert page numbers to 1-indexed for API
        Set<Integer> oneIndexedPages = pageNumbers.stream()
            .map(p -> p + 1)
            .collect(Collectors.toSet());

        // Make API request
        HybridRequest request = HybridRequest.forPages(pdfBytes, oneIndexedPages);
        HybridResponse response = client.convert(request);

        // Get page heights for coordinate transformation
        Map<Integer, Double> pageHeights = getPageHeights(pageNumbers, totalPages);

        // Transform response to IObjects
        HybridSchemaTransformer transformer = createTransformer(config);
        List<List<IObject>> transformedContents = transformer.transform(response, pageHeights);

        // Extract results for requested pages
        Map<Integer, List<IObject>> results = new HashMap<>();
        for (int pageNumber : pageNumbers) {
            if (pageNumber < transformedContents.size()) {
                List<IObject> pageContents = transformedContents.get(pageNumber);
                // Set IDs for backend-generated objects
                DocumentProcessor.setIDs(pageContents);
                results.put(pageNumber, pageContents);
            } else {
                results.put(pageNumber, new ArrayList<>());
            }
        }

        // Shutdown client if it has resources to release
        if (client instanceof DoclingClient) {
            ((DoclingClient) client).shutdown();
        }

        return results;
    }

    /**
     * Creates a hybrid client based on configuration.
     */
    private static HybridClient createClient(Config config) {
        String hybrid = config.getHybrid();
        HybridConfig hybridConfig = config.getHybridConfig();

        if (Config.HYBRID_DOCLING.equals(hybrid)) {
            return new DoclingClient(hybridConfig);
        }

        throw new IllegalArgumentException("Unsupported hybrid backend: " + hybrid);
    }

    /**
     * Creates a schema transformer based on configuration.
     */
    private static HybridSchemaTransformer createTransformer(Config config) {
        String hybrid = config.getHybrid();

        if (Config.HYBRID_DOCLING.equals(hybrid)) {
            return new DoclingSchemaTransformer();
        }

        throw new IllegalArgumentException("Unsupported hybrid backend: " + hybrid);
    }

    /**
     * Gets page heights for coordinate transformation.
     */
    private static Map<Integer, Double> getPageHeights(Set<Integer> pageNumbers, int totalPages) {
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
        ListProcessor.processLists(contents, false);
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

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

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.processors.readingorder.XYCutPlusPlusSorter;
import org.opendataloader.pdf.json.JsonWriter;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.opendataloader.pdf.markdown.MarkdownGeneratorFactory;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.html.HtmlGeneratorFactory;
import org.opendataloader.pdf.pdf.PDFWriter;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.text.TextGenerator;
import org.opendataloader.pdf.utils.ContentSanitizer;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.opendataloader.pdf.utils.TextNodeUtils;
import org.verapdf.as.ASAtom;
import org.verapdf.containers.StaticCoreContainers;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSTrailer;
import org.verapdf.gf.model.impl.containers.StaticStorages;
import org.verapdf.gf.model.impl.cos.GFCosInfo;
import org.verapdf.gf.model.impl.sa.GFSAPDFDocument;
import org.verapdf.parser.PDFFlavour;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.xmp.containers.StaticXmpCoreContainers;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main processor for PDF document analysis and output generation.
 * Coordinates the extraction, processing, and generation of various output formats.
 */
public class DocumentProcessor {
    private static final Logger LOGGER = Logger.getLogger(DocumentProcessor.class.getCanonicalName());

    /**
     * Processes a PDF file and generates the configured outputs.
     *
     * @param inputPdfName the path to the input PDF file
     * @param config the configuration settings
     * @throws IOException if unable to process the file
     */
    public static void processFile(String inputPdfName, Config config) throws IOException {
        processFileWithResult(inputPdfName, config);
    }

    /**
     * Processes a PDF file and returns a {@link ProcessingResult} containing
     * metadata collected during processing (e.g., hybrid server timings).
     *
     * @param inputPdfName the path to the input PDF file
     * @param config the configuration settings
     * @return processing result with optional metadata
     * @throws IOException if unable to process the file
     */
    public static ProcessingResult processFileWithResult(String inputPdfName, Config config) throws IOException {
        // --- Extraction (steps 1-5): parsing + layout analysis + content extraction ---
        long t0 = System.nanoTime();
        preprocessing(inputPdfName, config);
        calculateDocumentInfo();
        Set<Integer> pagesToProcess = getValidPageNumbers(config);
        List<List<IObject>> contents;
        if (StaticLayoutContainers.isUseStructTree()) {
            contents = TaggedDocumentProcessor.processDocument(inputPdfName, config, pagesToProcess);
        } else if (config.isHybridEnabled()) {
            contents = HybridDocumentProcessor.processDocument(inputPdfName, config, pagesToProcess);
        } else {
            contents = processDocument(inputPdfName, config, pagesToProcess);
        }
        if (config.needsStructuredProcessing()) {
            sortContents(contents, config);
        }
        ContentSanitizer contentSanitizer = new ContentSanitizer(config.getFilterConfig().getFilterRules(),
            config.getFilterConfig().isFilterSensitiveData());
        contentSanitizer.sanitizeContents(contents);
        long extractionNs = System.nanoTime() - t0;

        // --- Output (step 6): auto-tagging + JSON/MD/HTML export ---
        t0 = System.nanoTime();
        generateOutputs(inputPdfName, contents, config);
        long outputNs = System.nanoTime() - t0;

        return new ProcessingResult(HybridDocumentProcessor.getLastHybridTimings(), extractionNs, outputNs);
    }

    /**
     * Run the extraction pipeline only (preprocessing + content extraction + sanitization).
     * Does not generate any output files. Used by {@link org.opendataloader.pdf.api.AutoTagger}.
     *
     * @param inputPdfName path to the input PDF file
     * @param config       configuration
     * @return extraction result with contents and timing metadata
     */
    public static ExtractionResult extractContents(String inputPdfName, Config config) throws IOException {
        // Auto-tagging always needs structured processing (headings, lists, tables, captions).
        // Force a structured output flag so processDocument() runs HeadingProcessor etc.
        boolean hadStructured = config.needsStructuredProcessing();
        if (!hadStructured) {
            config.setGenerateJSON(true);
        }

        long t0 = System.nanoTime();
        preprocessing(inputPdfName, config);
        calculateDocumentInfo();
        Set<Integer> pagesToProcess = getValidPageNumbers(config);
        List<List<IObject>> contents;
        if (StaticLayoutContainers.isUseStructTree()) {
            contents = TaggedDocumentProcessor.processDocument(inputPdfName, config, pagesToProcess);
        } else if (config.isHybridEnabled()) {
            contents = HybridDocumentProcessor.processDocument(inputPdfName, config, pagesToProcess);
        } else {
            contents = processDocument(inputPdfName, config, pagesToProcess);
        }
        sortContents(contents, config);

        // Restore config
        if (!hadStructured) {
            config.setGenerateJSON(false);
        }
        ContentSanitizer contentSanitizer = new ContentSanitizer(config.getFilterConfig().getFilterRules(),
            config.getFilterConfig().isFilterSensitiveData());
        contentSanitizer.sanitizeContents(contents);
        long extractionNs = System.nanoTime() - t0;

        return new ExtractionResult(contents, extractionNs, HybridDocumentProcessor.getLastHybridTimings());
    }

    /**
     * Validates and filters page numbers from config against actual document pages.
     * Logs warnings for pages that don't exist in the document.
     *
     * @param config the configuration containing page selection
     * @return Set of valid 0-indexed page numbers to process, or null for all pages
     */
    private static Set<Integer> getValidPageNumbers(Config config) {
        List<Integer> requestedPages = config.getPageNumbers();
        if (requestedPages.isEmpty()) {
            return null; // null means process all pages
        }

        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        Set<Integer> validPages = new LinkedHashSet<>();
        List<Integer> invalidPages = new ArrayList<>();

        for (Integer page : requestedPages) {
            int zeroIndexed = page - 1; // Convert 1-based to 0-based
            if (zeroIndexed >= 0 && zeroIndexed < totalPages) {
                validPages.add(zeroIndexed);
            } else {
                invalidPages.add(page);
            }
        }

        if (!invalidPages.isEmpty()) {
            LOGGER.log(Level.WARNING,
                "Requested pages {0} do not exist in document (total pages: {1}). Processing only existing pages: {2}",
                new Object[]{invalidPages, totalPages,
                    validPages.stream().map(p -> p + 1).collect(Collectors.toList())});
        }

        if (validPages.isEmpty()) {
            LOGGER.log(Level.WARNING,
                "No valid pages to process. Document has {0} pages but requested: {1}",
                new Object[]{totalPages, requestedPages});
        }

        return validPages;
    }

    @SuppressWarnings("unchecked")
    private static List<List<IObject>> processDocument(String inputPdfName, Config config, Set<Integer> pagesToProcess) throws IOException {
        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        List<List<IObject>> contents = new ArrayList<>(Collections.nCopies(totalPages, null));

        // Capture ALL ThreadLocal state from main thread for propagation to workers
        final var document = StaticContainers.getDocument();
        final var tableBordersCollection = StaticContainers.getTableBordersCollection();
        final var accumulatedNodeMapper = StaticContainers.getAccumulatedNodeMapper();
        final var objectKeyMapper = StaticContainers.getObjectKeyMapper();
        final boolean keepLineBreaks = StaticContainers.isKeepLineBreaks();
        final boolean isDataLoader = StaticContainers.isDataLoader();
        final var isIgnoreCharsWithoutUnicode = StaticContainers.getIsIgnoreCharactersWithoutUnicode();

        // Capture StaticLayoutContainers state (shared mutable — synchronized list for headings)
        final var headings = StaticLayoutContainers.getHeadings();
        final long contentId = StaticLayoutContainers.getCurrentContentId();
        final boolean useStructTree = StaticLayoutContainers.isUseStructTree();

        // Runnable that propagates ThreadLocal state to the current (worker) thread
        final Runnable propagateState = () -> {
            // veraPDF StaticContainers
            StaticContainers.setDocument(document);
            StaticContainers.setTableBordersCollection(tableBordersCollection);
            StaticContainers.setAccumulatedNodeMapper(accumulatedNodeMapper);
            StaticContainers.setObjectKeyMapper(objectKeyMapper);
            StaticContainers.setKeepLineBreaks(keepLineBreaks);
            StaticContainers.setIsDataLoader(isDataLoader);
            StaticContainers.setIsIgnoreCharactersWithoutUnicode(isIgnoreCharsWithoutUnicode);
            // Project StaticLayoutContainers — share the same headings list across workers
            StaticLayoutContainers.setHeadings(headings);
            StaticLayoutContainers.setCurrentContentId(contentId);
            StaticLayoutContainers.setIsUseStructTree(useStructTree);
        };

        // Pre-fetch all page artifacts on main thread (document access is ThreadLocal)
        List<?>[] pageArtifacts = new List<?>[totalPages];
        for (int i = 0; i < totalPages; i++) {
            pageArtifacts[i] = document.getArtifacts(i);
        }

        int parallelism = Runtime.getRuntime().availableProcessors();
        ForkJoinPool pool = new ForkJoinPool(parallelism);
        LOGGER.log(Level.INFO, "Processing {0} pages with {1} threads", new Object[]{totalPages, parallelism});

        try {
            // Loop 1: ContentFilter per-page (largest bottleneck)
            pool.submit(() ->
                IntStream.range(0, totalPages).parallel().forEach(pageNumber -> {
                    try {
                        propagateState.run();
                        if (shouldProcessPage(pageNumber, pagesToProcess)) {
                            List<IObject> pageContents = ContentFilterProcessor.getFilteredContents(inputPdfName,
                                (List) pageArtifacts[pageNumber], pageNumber, config);
                            contents.set(pageNumber, pageContents);
                        } else {
                            contents.set(pageNumber, new ArrayList<>());
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })
            ).get();

            // Hidden text detection: sequential post-processing (requires ContrastRatioConsumer
            // which renders PDF pages — not safe to parallelize due to per-thread PDF file I/O)
            if (config.getFilterConfig().isFilterHiddenText()) {
                for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
                    if (shouldProcessPage(pageNumber, pagesToProcess)) {
                        List<IObject> pageContents = HiddenTextProcessor.findHiddenText(
                            inputPdfName, contents.get(pageNumber), true, config.getPassword());
                        contents.set(pageNumber, pageContents);
                    }
                }
            }

            boolean structured = config.needsStructuredProcessing();

            // ClusterTableProcessor: whole-document (must be sequential)
            if (structured && config.isClusterTableMethod()) {
                new ClusterTableProcessor().processTables(contents);
            }

            // Loop 2: TableBorder + TextLine per-page
            pool.submit(() ->
                IntStream.range(0, totalPages).parallel().forEach(pageNumber -> {
                    if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                        return;
                    }
                    propagateState.run();
                    List<IObject> pageContents = contents.get(pageNumber);
                    if (structured) {
                        pageContents = TableBorderProcessor.processTableBorders(pageContents, pageNumber);
                        if (config.isDetectStrikethrough()) {
                            StrikethroughProcessor.processStrikethroughs(pageContents);
                        }
                        pageContents = pageContents.stream().filter(x -> !(x instanceof LineChunk)).collect(Collectors.toList());
                        pageContents = SpecialTableProcessor.detectSpecialTables(pageContents);
                    }
                    pageContents = TextLineProcessor.processTextLines(pageContents);
                    contents.set(pageNumber, pageContents);
                })
            ).get();

            if (structured) {
                // Cross-page operations (must be sequential)
                HeaderFooterProcessor.processHeadersAndFooters(contents, false);
                ListProcessor.processLists(contents, false);
            }

            // Loop 3: Paragraph + Heading per-page (always need ParagraphProcessor for text output)
            pool.submit(() ->
                IntStream.range(0, totalPages).parallel().forEach(pageNumber -> {
                    if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                        return;
                    }
                    propagateState.run();
                    List<IObject> pageContents = contents.get(pageNumber);
                    pageContents = ParagraphProcessor.processParagraphs(pageContents);
                    if (structured) {
                        pageContents = ListProcessor.processListsFromTextNodes(pageContents);
                        HeadingProcessor.processHeadings(pageContents, false);
                    }
                    contents.set(pageNumber, pageContents);
                })
            ).get();

            // Sequential ID assignment (must be in page order, before CaptionProcessor)
            for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
                if (shouldProcessPage(pageNumber, pagesToProcess)) {
                    setIDs(contents.get(pageNumber));
                }
            }

            // Caption detection runs after setIDs so that recognizedStructureId is available
            // for linking captions to figures/tables
            if (structured) {
                for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
                    if (shouldProcessPage(pageNumber, pagesToProcess)) {
                        CaptionProcessor.processCaptions(contents.get(pageNumber));
                    }
                }
            }

            if (structured) {
                // Cross-page post-processing (must be sequential)
                ListProcessor.checkNeighborLists(contents);
                TableBorderProcessor.checkNeighborTables(contents);
                HeadingProcessor.detectHeadingsLevels();
                LevelProcessor.detectLevels(contents);
            }
        } catch (Exception e) {
            throw new IOException("Parallel page processing failed", e);
        } finally {
            pool.shutdown();
        }
        return contents;
    }

    /**
     * Checks if a page should be processed based on the filter.
     *
     * @param pageNumber 0-indexed page number
     * @param pagesToProcess set of valid page numbers to process, or null for all pages
     * @return true if the page should be processed
     */
    private static boolean shouldProcessPage(int pageNumber, Set<Integer> pagesToProcess) {
        return pagesToProcess == null || pagesToProcess.contains(pageNumber);
    }

    private static void generateOutputs(String inputPdfName, List<List<IObject>> contents, Config config) throws IOException {
        // Stdout mode: write primary format to stdout, skip file I/O
        if (config.isOutputStdout()) {
            java.io.Writer stdoutWriter = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(System.out, java.nio.charset.StandardCharsets.UTF_8));
            if (config.isGenerateText()) {
                TextGenerator textGenerator = new TextGenerator(stdoutWriter, config);
                textGenerator.writeToText(contents);
                stdoutWriter.flush();
            } else if (config.isGenerateMarkdown()) {
                MarkdownGenerator markdownGenerator = new MarkdownGenerator(stdoutWriter, config);
                markdownGenerator.writeToMarkdown(contents);
                stdoutWriter.flush();
            }
            // JSON and HTML stdout not yet supported
            return;
        }

        File inputPDF = new File(inputPdfName);
        new File(config.getOutputFolder()).mkdirs();
        if (!config.isImageOutputOff() && (config.isGenerateHtml() || config.isGenerateMarkdown() || config.isGenerateJSON())) {
            String imagesDirectory;
            if (config.getImageDir() != null && !config.getImageDir().isEmpty()) {
                imagesDirectory = config.getImageDir();
            } else {
                String fileName = Paths.get(inputPdfName).getFileName().toString();
                String baseName = fileName.substring(0, fileName.length() - 4);
                imagesDirectory = config.getOutputFolder() + File.separator + baseName + MarkdownSyntax.IMAGES_DIRECTORY_SUFFIX;
            }
            StaticLayoutContainers.setImagesDirectory(imagesDirectory);
            ImagesUtils imagesUtils = new ImagesUtils();
            imagesUtils.write(contents, inputPdfName, config.getPassword());
        }
        if (config.isGeneratePDF()) {
            PDFWriter pdfWriter = new PDFWriter();
            pdfWriter.updatePDF(inputPDF, config.getPassword(), config.getOutputFolder(), contents);
        }
        if (config.isGenerateJSON()) {
            JsonWriter.writeToJson(inputPDF, config.getOutputFolder(), contents);
        }
        if (config.isGenerateMarkdown()) {
            try (MarkdownGenerator markdownGenerator = MarkdownGeneratorFactory.getMarkdownGenerator(inputPDF,
                config)) {
                markdownGenerator.writeToMarkdown(contents);
            }
        }
        if (config.isGenerateHtml()) {
            try (HtmlGenerator htmlGenerator = HtmlGeneratorFactory.getHtmlGenerator(inputPDF, config)) {
                htmlGenerator.writeToHtml(contents);
            }
        }
        if (config.isGenerateText()) {
            try (TextGenerator textGenerator = new TextGenerator(inputPDF, config)) {
                textGenerator.writeToText(contents);
            }
        }
    }

    /**
     * Performs preprocessing on a PDF document.
     * Initializes static containers and parses the document structure.
     *
     * @param pdfName the path to the PDF file
     * @param config the configuration settings
     * @throws IOException if unable to read the PDF file
     */
    public static void preprocessing(String pdfName, Config config) throws IOException {
        LOGGER.log(Level.INFO, () -> "File name: " + pdfName);
        updateStaticContainers(config);
        PDDocument pdDocument = new PDDocument(pdfName);
        StaticResources.setDocument(pdDocument);
        GFSAPDFDocument document = new GFSAPDFDocument(pdDocument);
//        org.verapdf.gf.model.impl.containers.StaticContainers.setFlavour(Collections.singletonList(PDFAFlavour.WCAG_2_2));
        StaticResources.setFlavour(Collections.singletonList(PDFFlavour.WCAG_2_2_HUMAN));
        StaticStorages.setIsFilterInvisibleLayers(config.getFilterConfig().isFilterHiddenOCG());
        StaticContainers.setDocument(document);
        if (config.isUseStructTree()) {
            document.parseStructureTreeRoot();
            if (document.getTree() != null) {
                StaticLayoutContainers.setIsUseStructTree(true);
            } else {
                StaticLayoutContainers.setIsUseStructTree(false);
                LOGGER.log(Level.WARNING, "The document has no structure tree. The 'use-struct-tree' option will be ignored.");
            }
        }
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticResources.setIsFontProgramsParsing(true);
        StaticStorages.setIsIgnoreMCIDs(!StaticLayoutContainers.isUseStructTree());
        StaticStorages.setIsAddSpacesBetweenTextPieces(true);
        document.parseChunks();
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
    }

    private static void updateStaticContainers(Config config) {
        StaticResources.clear();
        StaticContainers.updateContainers(null);
        StaticLayoutContainers.clearContainers();
        org.verapdf.gf.model.impl.containers.StaticContainers.clearAllContainers();
        StaticCoreContainers.clearAllContainers();
        StaticXmpCoreContainers.clearAllContainers();
        StaticContainers.setKeepLineBreaks(config.isKeepLineBreaks());
        StaticLayoutContainers.setCurrentContentId(1);
        StaticLayoutContainers.setEmbedImages(config.isEmbedImages());
        StaticLayoutContainers.setImageFormat(config.getImageFormat());
        StaticResources.setPassword(config.getPassword());
    }

    /**
     * Assigns unique IDs to each content object.
     *
     * @param contents the list of content objects
     */
    public static void setIDs(List<IObject> contents) {
        for (IObject object : contents) {
            object.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        }
    }

    /**
     * Sets index values for all content objects across all pages.
     *
     * @param contents the document contents organized by page
     */
    public static void setIndexesForDocumentContents(List<List<IObject>> contents) {
        for (List<IObject> pageContents : contents) {
            setIndexesForContentsList(pageContents);
        }
    }

    /**
     * Sets index values for content objects in a list.
     *
     * @param contents the list of content objects
     */
    public static void setIndexesForContentsList(List<IObject> contents) {
        for (int index = 0; index < contents.size(); index++) {
            contents.get(index).setIndex(index);
        }
    }

    /**
     * Creates a new list with null objects removed.
     *
     * @param contents the list that may contain null objects
     * @return a new list without null objects
     */
    public static List<IObject> removeNullObjectsFromList(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        for (IObject content : contents) {
            if (content != null) {
                newContents.add(content);
            }
        }
        return newContents;
    }

    private static void calculateDocumentInfo() {
        PDDocument document = StaticResources.getDocument();
        LOGGER.log(Level.INFO, () -> "Number of pages: " + document.getNumberOfPages());
        COSTrailer trailer = document.getDocument().getTrailer();
        GFCosInfo info = getInfo(trailer);
        LOGGER.log(Level.INFO, () -> "Author: " + (info.getAuthor() != null ? info.getAuthor() : info.getXMPCreator()));
        LOGGER.log(Level.INFO, () -> "Title: " + (info.getTitle() != null ? info.getTitle() : info.getXMPTitle()));
        LOGGER.log(Level.INFO, () -> "Creation date: " + (info.getCreationDate() != null ? info.getCreationDate() : info.getXMPCreateDate()));
        LOGGER.log(Level.INFO, () -> "Modification date: " + (info.getModDate() != null ? info.getModDate() : info.getXMPModifyDate()));
    }

    private static GFCosInfo getInfo(COSTrailer trailer) {
        COSObject object = trailer.getKey(ASAtom.INFO);
        return new GFCosInfo((COSDictionary) (object != null && object.getType() == COSObjType.COS_DICT ? object.getDirectBase() : COSDictionary.construct().get()));
    }

    /**
     * Gets a debug string representation of a text node.
     *
     * @param textNode the text node to describe
     * @return a string with font, size, color, and content information
     */
    public static String getContentsValueForTextNode(SemanticTextNode textNode) {
        return String.format("%s: font %s, text size %.2f, text color %s, text content \"%s\"",
                textNode.getSemanticType().getValue(), textNode.getFontName(),
                textNode.getFontSize(), Arrays.toString(TextNodeUtils.getTextColorOrDefault(textNode)),
                textNode.getValue().length() > 15 ? textNode.getValue().substring(0, 15) + "..." : textNode.getValue());
    }

    /**
     * Gets the bounding box for a page.
     *
     * @param pageNumber the page number (0-indexed)
     * @return the page bounding box, or null if not available
     */
    public static BoundingBox getPageBoundingBox(int pageNumber) {
        PDDocument document = StaticResources.getDocument();
        if (document == null) {
            return null;
        }
        double[] cropBox = document.getPage(pageNumber).getCropBox();
        if (cropBox == null) {
            return null;
        }
        return new BoundingBox(pageNumber, cropBox);
    }

    /**
     * Sorts page contents by their bounding box positions.
     *
     * @param contents the list of content objects to sort
     * @return a new sorted list of content objects
     */
    public static List<IObject> sortPageContents(List<IObject> contents) {
        if (contents == null || contents.isEmpty()) {
            return contents;
        }
        List<IObject> sortedContents = new ArrayList<>(contents);
        sortedContents.sort((o1, o2) -> {
            BoundingBox b1 = o1.getBoundingBox();
            BoundingBox b2 = o2.getBoundingBox();
            if (b1 == null && b2 == null) {
                return 0;
            }
            if (b1 == null) {
                return 1;
            }
            if (b2 == null) {
                return -1;
            }
            if (!Objects.equals(b1.getPageNumber(), b2.getPageNumber())) {
                return b1.getPageNumber() - b2.getPageNumber();
            }
            if (!Objects.equals(b1.getLastPageNumber(), b2.getLastPageNumber())) {
                return b1.getLastPageNumber() - b2.getLastPageNumber();
            }
            if (!Objects.equals(b1.getTopY(), b2.getTopY())) {
                return b2.getTopY() - b1.getTopY() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getLeftX(), b2.getLeftX())) {
                return b1.getLeftX() - b2.getLeftX() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getBottomY(), b2.getBottomY())) {
                return b1.getBottomY() - b2.getBottomY() > 0 ? 1 : -1;
            }
            if (!Objects.equals(b1.getRightX(), b2.getRightX())) {
                return b1.getRightX() - b2.getRightX() > 0 ? 1 : -1;
            }
            return 0;
        });
        return sortedContents;
    }

    /**
     * Sorts document contents according to the configured reading order.
     *
     * @param contents the document contents organized by page
     * @param config the configuration containing reading order settings
     */
    public static void sortContents(List<List<IObject>> contents, Config config) {
        String readingOrder = config.getReadingOrder();

        // xycut: XY-Cut++ sorting (per-page, stateless — safe to parallelize)
        if (Config.READING_ORDER_XYCUT.equals(readingOrder)) {
            IntStream.range(0, StaticContainers.getDocument().getNumberOfPages()).parallel().forEach(pageNumber ->
                contents.set(pageNumber, XYCutPlusPlusSorter.sort(contents.get(pageNumber)))
            );
            return;
        }

        // Log warning for unknown reading order values
        if (!Config.READING_ORDER_OFF.equals(readingOrder)) {
            LOGGER.log(Level.WARNING, "Unknown reading order value ''{0}'', using default ''off''", readingOrder);
        }

        // off: skip sorting (keep PDF COS object order)
    }
}

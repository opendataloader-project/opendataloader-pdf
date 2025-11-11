/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.json.JsonWriter;
import org.opendataloader.pdf.json.ObjectMapperHolder;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.opendataloader.pdf.markdown.MarkdownGeneratorFactory;
import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.html.HtmlGeneratorFactory;
import org.opendataloader.pdf.pdf.PDFWriter;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.text.TextGenerator;
import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import org.codehaus.plexus.util.FileUtils;
import org.opendataloader.pdf.utils.TimeHelper;
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
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;
import org.verapdf.xmp.containers.StaticXmpCoreContainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocumentProcessor {
    private static final Logger LOGGER = Logger.getLogger(DocumentProcessor.class.getCanonicalName());

    public static final boolean generateTableResults = true;
    public static TimeHelper timeHelper = new TimeHelper();

    public static void processFile(String inputPdfName, Config config) throws IOException {
        preprocessing(inputPdfName, config);
        calculateDocumentInfo();
        List<List<IObject>> contents = StaticLayoutContainers.isUseStructTree() ?
            TaggedDocumentProcessor.processDocument(inputPdfName, config) :
            processDocument(inputPdfName, config);
        generateOutputs(inputPdfName, contents, config);
    }

    private static List<List<IObject>> processDocument(String inputPdfName, Config config) throws IOException {
        List<List<IObject>> contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = ContentFilterProcessor.getFilteredContents(inputPdfName,
                StaticContainers.getDocument().getArtifacts(pageNumber), pageNumber, config);
            contents.add(pageContents);
        }
        // This is called after text is processed in case we need to provide them to TATR
        new TableTransformerProcessor(inputPdfName, config).processTables(contents);

        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = contents.get(pageNumber);
            pageContents = TableBorderProcessor.processTableBorders(pageContents, pageNumber);
            pageContents = pageContents.stream().filter(x -> !(x instanceof LineChunk)).collect(Collectors.toList());
            pageContents = TextLineProcessor.processTextLines(pageContents);
            pageContents = SpecialTableProcessor.detectSpecialTables(pageContents);
            contents.set(pageNumber, pageContents);
        }
        HeaderFooterProcessor.processHeadersAndFooters(contents, false);
        ListProcessor.processLists(contents, false);
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = contents.get(pageNumber);
            pageContents = ParagraphProcessor.processParagraphs(pageContents);
            pageContents = ListProcessor.processListsFromTextNodes(pageContents);
            HeadingProcessor.processHeadings(pageContents);
            setIDs(pageContents);
            CaptionProcessor.processCaptions(pageContents);
            contents.set(pageNumber, pageContents);
        }
        ListProcessor.checkNeighborLists(contents);
        TableBorderProcessor.checkNeighborTables(contents);
        HeadingProcessor.detectHeadingsLevels();
        LevelProcessor.detectLevels(contents);
        return contents;
    }

    private static void generateOutputs(String inputPdfName, List<List<IObject>> contents, Config config) throws IOException {
        File inputPDF = new File(inputPdfName);
        new File(config.getOutputFolder()).mkdirs();
        if (config.isGeneratePDF()) {
            PDFWriter.updatePDF(inputPDF, config.getPassword(), config.getOutputFolder(), contents);
        }
        if (config.isGenerateJSON()) {
            JsonWriter.writeToJson(inputPDF, config.getOutputFolder(), contents);
        }
        if (config.isGenerateMarkdown()) {
            try (MarkdownGenerator markdownGenerator = MarkdownGeneratorFactory.getMarkdownGenerator(inputPDF,
                config.getOutputFolder(), config)) {
                markdownGenerator.writeToMarkdown(contents);
            }
        }
        if (config.isGenerateHtml()) {
            try (HtmlGenerator htmlGenerator = HtmlGeneratorFactory.getHtmlGenerator(inputPDF, config.getOutputFolder(), config)) {
                htmlGenerator.writeToHtml(contents);
            }
        }
        if (config.isGenerateText()) {
            try (TextGenerator textGenerator = new TextGenerator(inputPDF, config.getOutputFolder())) {
                textGenerator.writeToText(contents);
            }
        }
    }

    public static void generateTableResults(File file, Config config) throws IOException {
        timeHelper = new TimeHelper();
        preprocessing(file.getAbsolutePath(), config);
        List<List<IObject>> contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = new ArrayList<>(StaticContainers.getDocument().getArtifacts(pageNumber));
            TextProcessor.removeSameTextChunks(pageContents);
            pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
            TextProcessor.trimTextChunksWhiteSpaces(pageContents);
            contents.add(pageContents);
        }

        DocumentProcessor.timeHelper.start();
        List<List<TableBorder>> javaTables = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            javaTables.add(TableBorderProcessor.processTableBorders(contents.get(pageNumber)));
        }
        DocumentProcessor.timeHelper.endJava();
        serializeResults(file, new File("java"), javaTables);

        StaticContainers.getTableBordersCollection().clearContentsOfTable();

        DocumentProcessor.timeHelper.start();
        List<Integer> pageNumbers = AbstractTableProcessor.getPagesWithPossibleTables(contents);
        DocumentProcessor.timeHelper.endJavaAndFilteredPython();
        List<Integer> otherPageNumbers = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            if (!pageNumbers.contains(pageNumber)) {
                otherPageNumbers.add(pageNumber);
            }
        }

        if (!pageNumbers.isEmpty()) {
            DocumentProcessor.timeHelper.start();
            List<List<TableBorder>> filterTatrAndJavaTables = new ArrayList<>();
            List<List<TableBorder>> tatrTables = TableTransformerProcessor.callTATR(file.getAbsolutePath(), config, contents, pageNumbers);
            AbstractTableProcessor.addTablesToTableCollection(tatrTables);
            DocumentProcessor.timeHelper.endJavaAndPython();
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                filterTatrAndJavaTables.add(TableBorderProcessor.processTableBorders(contents.get(pageNumber)));
            }
            DocumentProcessor.timeHelper.endJavaAndFilteredPython();
            serializeResults(file, new File("java_and_filtered_tatr"), filterTatrAndJavaTables);
        }

        StaticContainers.getTableBordersCollection().clearContentsOfTable();

        if (!otherPageNumbers.isEmpty()) {
            DocumentProcessor.timeHelper.start();
            List<List<TableBorder>> tatrAndJavaTables = new ArrayList<>();
            List<List<TableBorder>> filterTatrTables = TableTransformerProcessor.callTATR(file.getAbsolutePath(), config, contents, otherPageNumbers);
            AbstractTableProcessor.addTablesToTableCollection(filterTatrTables);
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                tatrAndJavaTables.add(TableBorderProcessor.processTableBorders(contents.get(pageNumber)));
            }
            DocumentProcessor.timeHelper.endJavaAndPython();
            serializeResults(file, new File("java_and_tatr"), tatrAndJavaTables);
            for (int index = 0; index < filterTatrTables.size(); index++) {
                List<TableBorder> tables = filterTatrTables.get(index);
                if (!tables.isEmpty()) {
                    LOGGER.log(Level.WARNING, String.format("Page %s contains tables from tatr, but we filter this page", otherPageNumbers.get(index) + 1));
                }
            }
        }

//        StaticContainers.getTableBordersCollection().clear();
//        DocumentProcessor.timeHelper.start();
//        addTablesFromTATR(tatrTables);
//        DocumentProcessor.timeHelper.endPython();
//        StaticContainers.getTableBordersCollection().clearContentsOfTable();

//        List<List<TableBorder>> pythonTables = new ArrayList<>();
//        DocumentProcessor.timeHelper.start();
//        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
//            pythonTables.add(TableBorderProcessor.processTableBorders(contents.get(pageNumber)));
//        }
//        DocumentProcessor.timeHelper.endPython();
//        serializeResults(file, new File("tatr"), pythonTables);

        DocumentProcessor.timeHelper.print(file);
    }

    public static List<List<TableBorder>> callTATR(String inputPdfName, Config config, List<List<IObject>> contents) {
        List<Integer> pageNumbers = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            pageNumbers.add(pageNumber);
        }
        return TableTransformerProcessor.callTATR(inputPdfName, config, contents, pageNumbers);
    }

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
            }
        }
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticResources.setIsFontProgramsParsing(true);
        StaticStorages.setIsIgnoreMCIDs(!StaticLayoutContainers.isUseStructTree());
        StaticStorages.setIsAddSpacesBetweenTextPieces(true);
        document.parseChunks();
        DocumentProcessor.timeHelper.start();
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
        DocumentProcessor.timeHelper.endJava();
        DocumentProcessor.timeHelper.endJavaAndPython();
        DocumentProcessor.timeHelper.endJavaAndFilteredPython();
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
        StaticResources.setPassword(config.getPassword());
    }

    public static void setIDs(List<IObject> contents) {
        for (IObject object : contents) {
            object.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        }
    }

    public static void setIndexesForDocumentContents(List<List<IObject>> contents) {
        for (List<IObject> pageContents : contents) {
            setIndexesForContentsList(pageContents);
        }
    }

    public static void setIndexesForContentsList(List<IObject> contents) {
        for (int index = 0; index < contents.size(); index++) {
            contents.get(index).setIndex(index);
        }
    }

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

    public static String getContentsValueForTextNode(SemanticTextNode textNode) {
        return String.format("%s: font %s, text size %.2f, text color %s, text content \"%s\"",
                textNode.getSemanticType().getValue(), textNode.getFontName(),
                textNode.getFontSize(), Arrays.toString(textNode.getTextColor()),
                textNode.getValue().length() > 15 ? textNode.getValue().substring(0, 15) + "..." : textNode.getValue());
    }

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

    public static List<IObject> sortContents(List<IObject> contents) {
        List<IObject> sortedContents = new ArrayList<>(contents);
        sortedContents.sort((o1, o2) -> {
            BoundingBox b1 = o1.getBoundingBox();
            BoundingBox b2 = o2.getBoundingBox();
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
                return b2.getLeftX() - b1.getLeftX() > 0 ? 1 : -1;
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

    public static void serializeResults(File file, File folder, List<List<TableBorder>> tableBorders) {
        String fileName = file.getName().substring(0, file.getName().length() - 4);
        folder.mkdir();
        String imagesFolder = folder + File.separator + "images";
        new File(imagesFolder).mkdir();
        String wordsFolder = folder + File.separator + "words";
        new File(wordsFolder).mkdir();
        String structuresFolder = folder + File.separator + "structures";
        new File(structuresFolder).mkdir();
        try (ContrastRatioConsumer contrastRatioConsumer = new ContrastRatioConsumer(file.getAbsolutePath(), null/*password*/, true, 1000f)) {
            StaticLayoutContainers.setContrastRatioConsumer(contrastRatioConsumer);
            for (int pageNumber = 0; pageNumber < StaticResources.getDocument().getNumberOfPages(); pageNumber++) {
                List<TableBorder> pageTableBorders = tableBorders.get(pageNumber);
                int tableIndex = 0;
                for (IObject object : pageTableBorders) {
                    if (!(object instanceof TableBorder)) {
                        continue;
                    }
                    TableBorder tableBorder = (TableBorder) object;
                    if (tableBorder.isOneCellTable()) {
                        continue;
                    }
                    if (tableIndex == 0) {
                        BufferedImage image = contrastRatioConsumer.getRenderPage(pageNumber);
                        File imageFile = new File(imagesFolder + File.separator + fileName + "_page_" + (pageNumber + 1) + ".jpg");
                        ImageIO.write(image, "jpg", imageFile);
                    }
                    String jsonFileName = structuresFolder + File.separator + fileName + "_page_" + (pageNumber + 1) + "_table_" + tableIndex + "_objects.json";
                    JsonFactory jsonFactory = new JsonFactory();
                    try (JsonGenerator jsonGenerator = jsonFactory.createGenerator(new File(jsonFileName), JsonEncoding.UTF8)
                            .setPrettyPrinter(new DefaultPrettyPrinter())
                            .setCodec(ObjectMapperHolder.getTableTransformerObjectMapper())) {
                        jsonGenerator.writePOJO(tableBorder);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    File wordsFile = new File(wordsFolder + File.separator + fileName + "_page_" + (pageNumber + 1) + "_table_" + tableIndex + "_words.json");
                    TableTransformerProcessor.textContentsToJSON(wordsFile,
                            TableTransformerProcessor.getTextChunksForTableBorder(tableBorder),
                            DocumentProcessor.getPageBoundingBox(pageNumber),
                            StaticLayoutContainers.getContrastRatioConsumer().getDpiScalingForPage(pageNumber));
                    tableIndex++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

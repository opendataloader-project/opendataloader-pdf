/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.processors;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.hancom.opendataloader.pdf.containers.StaticLayoutContainers;
import com.hancom.opendataloader.pdf.json.JsonWriter;
import com.hancom.opendataloader.pdf.json.ObjectMapperHolder;
import com.hancom.opendataloader.pdf.markdown.MarkdownGenerator;
import com.hancom.opendataloader.pdf.markdown.MarkdownGeneratorFactory;
import com.hancom.opendataloader.pdf.pdf.PDFWriter;
import com.hancom.opendataloader.pdf.utils.Config;
import com.hancom.opendataloader.pdf.utils.TimeHelper;
import org.codehaus.plexus.util.FileUtils;
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
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.xmp.containers.StaticXmpCoreContainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class DocumentProcessor {
    private static final Logger LOGGER = Logger.getLogger(DocumentProcessor.class.getCanonicalName());
    
    public static final boolean generateTableResults = true;
    public static TimeHelper timeHelper;

    public static void processFile(String inputPdfName, Config config) throws IOException {
        preprocessing(inputPdfName, config);
        calculateDocumentInfo();
        List<List<IObject>> contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = new ArrayList<>(StaticContainers.getDocument().getArtifacts(pageNumber));
            TextProcessor.removeSameTextChunks(pageContents);
            pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
            TextProcessor.removeTextDecorationImages(pageContents);
            pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
            TextProcessor.trimTextChunksWhiteSpaces(pageContents);
            pageContents = HiddenTextProcessor.findHiddenText(inputPdfName, pageContents, config.getPassword());
            processBackgrounds(pageNumber, pageContents);
            contents.add(pageContents);
        }
        // This is called after text is processed in case we need to provide them to TATR
        addTablesFromTATR(inputPdfName, config, contents);

        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = contents.get(pageNumber);
            pageContents = TableBorderProcessor.processTableBorders(pageContents, pageNumber);
            pageContents = pageContents.stream().filter(x -> !(x instanceof LineChunk)).collect(Collectors.toList());
            pageContents = TextLineProcessor.processTextLines(pageContents);
            pageContents = SpecialTableProcessor.detectSpecialTables(pageContents);
            contents.set(pageNumber, pageContents);
        }

        HeaderFooterProcessor.processHeadersAndFooters(contents);
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
        File inputPDF = new File(inputPdfName);
        new File(config.getOutputFolder()).mkdirs();
        if (config.isGeneratePDF()) {
            PDFWriter.updatePDF(inputPDF, config.getPassword(), config.getOutputFolder(), contents);
        }
        if (config.isGenerateJSON()) {
            JsonWriter.writeToJson(inputPDF, config.getOutputFolder(), contents);
        }
        if (config.isGenerateMarkdown()) {
            try (MarkdownGenerator markdownGenerator = MarkdownGeneratorFactory.getMarkdownGenerator(inputPDF, config.getOutputFolder(), config)) {
                markdownGenerator.writeToMarkdown(contents);
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
        DocumentProcessor.timeHelper.endJavaAndPython();
        serializeResults(file, new File("java"), javaTables);

        
        StaticContainers.getTableBordersCollection().clearContentsOfTable();
        DocumentProcessor.timeHelper.start();
        List<List<TableBorder>> tatrTables = callTATR(file.getAbsolutePath(), config, contents);
        DocumentProcessor.timeHelper.endPython();
        addTablesFromTATR(tatrTables);
        DocumentProcessor.timeHelper.endJavaAndPython();

        List<List<TableBorder>> pythonAndJavaTables = new ArrayList<>();
        DocumentProcessor.timeHelper.start();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            pythonAndJavaTables.add(TableBorderProcessor.processTableBorders(contents.get(pageNumber)));
        }
        DocumentProcessor.timeHelper.endJavaAndPython();
        serializeResults(file, new File("java_tatr"), pythonAndJavaTables);
        
        StaticContainers.getTableBordersCollection().clear();
        DocumentProcessor.timeHelper.start();
        addTablesFromTATR(tatrTables);
        DocumentProcessor.timeHelper.endPython();
        StaticContainers.getTableBordersCollection().clearContentsOfTable();
        
        List<List<TableBorder>> pythonTables = new ArrayList<>();
        DocumentProcessor.timeHelper.start();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            pythonTables.add(TableBorderProcessor.processTableBorders(contents.get(pageNumber)));
        }
        DocumentProcessor.timeHelper.endPython();
        serializeResults(file, new File("tatr"), pythonTables);
        DocumentProcessor.timeHelper.print(file);
    }

    private static void addTablesFromTATR(String inputPdfName, Config config, List<List<IObject>> contents) {
        List<List<TableBorder>> tatrTables = callTATR(inputPdfName, config, contents);
        addTablesFromTATR(tatrTables);
    }

    private static void addTablesFromTATR(List<List<TableBorder>> tatrTables) {
        if (tatrTables != null) {
            TableBordersCollection javaCollection = StaticContainers.getTableBordersCollection();
            List<SortedSet<TableBorder>> javaBorders = javaCollection.getTableBorders();
            Iterator<SortedSet<TableBorder>> javaI = javaBorders.iterator();
            Iterator<List<TableBorder>> pythonI = tatrTables.iterator();
            while (pythonI.hasNext()) {
                List<TableBorder> pythonList = pythonI.next();
                SortedSet<TableBorder> javaSet = javaI.next();
                for (TableBorder border : pythonList) {
                    // Add tables from TATR that Java failed to detect
                    if (javaCollection.getTableBorder(border.getBoundingBox()) == null) {
                        javaSet.add(border);
                    }
                }
            }
        }
    }

    public static List<List<TableBorder>> callTATR(String inputPdfName, Config config, List<List<IObject>> contents) {
        File tempDir = null;
        List<List<TableBorder>> tables = null;
        try {
            Path scriptFolder = Paths.get("");
            tempDir = Files.createTempDirectory(scriptFolder, "out-java").toFile();
            tables = TableTransformerProcessor.processTableTransformer(
                    inputPdfName, config.getPassword(), scriptFolder.toFile(), config.getPythonExecutable(),
                    tempDir, contents);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to process document using TATR: " + e.getMessage());
        }
        if (tempDir != null) {
            try {
                FileUtils.deleteDirectory(tempDir);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to clean up temp data of TATR: " + e.getMessage());
            }
        }
        return tables;
    }

    public static void preprocessing(String pdfName, Config config) throws IOException {
        System.out.println("File name: " + pdfName);
        updateStaticContainers(config);
        PDDocument pdDocument = new PDDocument(pdfName);
        StaticResources.setDocument(pdDocument);
        GFSAPDFDocument document = new GFSAPDFDocument(pdDocument);
//        org.verapdf.gf.model.impl.containers.StaticContainers.setFlavour(Collections.singletonList(PDFAFlavour.WCAG_2_2));
        StaticResources.setFlavour(Collections.singletonList(PDFFlavour.WCAG_2_2_HUMAN));
        StaticContainers.setDocument(document);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticResources.setIsFontProgramsParsing(true);
        StaticStorages.setIsIgnoreMCIDs(true);
        StaticStorages.setIsAddSpacesBetweenTextPieces(true);
        document.parseChunks();
        DocumentProcessor.timeHelper.start();
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
        DocumentProcessor.timeHelper.endJava();
        DocumentProcessor.timeHelper.endJavaAndPython();
    }

    private static void updateStaticContainers(Config config) {
        StaticResources.clear();
        StaticContainers.updateContainers(null);
        StaticLayoutContainers.clearContainers();
        org.verapdf.gf.model.impl.containers.StaticContainers.clearAllContainers();
        StaticCoreContainers.clearAllContainers();
        StaticXmpCoreContainers.clearAllContainers();
        StaticLayoutContainers.setFindHiddenText(config.isFindHiddenText());
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
        System.out.println("Number of pages: " + document.getNumberOfPages());
        COSTrailer trailer = document.getDocument().getTrailer();
        GFCosInfo info = getInfo(trailer);
        System.out.println("Author: " + (info.getAuthor() != null ? info.getAuthor() : info.getXMPCreator()));
        System.out.println("Title: " + (info.getTitle() != null ? info.getTitle() : info.getXMPTitle()));
        System.out.println("Creation date: " + (info.getCreationDate() != null ? info.getCreationDate() : info.getXMPCreateDate()));
        System.out.println("Modification date: " + (info.getModDate() != null ? info.getModDate() : info.getXMPModifyDate()));
    }

    private static GFCosInfo getInfo(COSTrailer trailer) {
        COSObject object = trailer.getKey(ASAtom.INFO);
        return new GFCosInfo((COSDictionary) (object != null && object.getType() == COSObjType.COS_DICT ? object.getDirectBase() : COSDictionary.construct().get()));
    }

    public static void replaceContentsToResult(List<IObject> contents, IObject result) {
        List<IObject> replacedContents = new LinkedList<>();
        Integer index = null;
        int i = 0;
        for (IObject content : contents) {
            if (contains(result.getBoundingBox(), content.getBoundingBox())) {
//            if (content.getBoundingBox().getIntersectionPercent(result.getBoundingBox()) > 0.5) {
                replacedContents.add(content);
                if (index == null) {
                    index = i;
                }
            }
            i++;
        }
        if (index == null) {
            return;
        }
        contents.set(index, result);
        contents.removeAll(replacedContents);
    }

    public static boolean contains(BoundingBox box, BoundingBox box2) {
        if (box instanceof MultiBoundingBox) {
            for (BoundingBox b : ((MultiBoundingBox) box).getBoundingBoxes()) {
                if (b.contains(box2, 0.6, 0.6)) {
                    return true;
                }
            }
        } else {
            return box.contains(box2, 0.6, 0.6);
        }
        return false;
    }

    public static String getContentsValueForTextNode(SemanticTextNode textNode) {
        return String.format("%s: font %s, text size %.2f, text color %s, text content \"%s\"",
                textNode.getSemanticType().getValue(), textNode.getFontName(),
                textNode.getFontSize(), Arrays.toString(textNode.getTextColor()),
                textNode.getValue().length() > 15 ? textNode.getValue().substring(0, 15) + "..." : textNode.getValue());
    }

    public static void processBackgrounds(int pageNumber, List<IObject> contents) {
        BoundingBox pageBoundingBox = getPageBoundingBox(pageNumber);
        if (pageBoundingBox == null) {
            return;
        }
        Set<LineArtChunk> backgrounds = new HashSet<>();
        for (IObject content : contents) {
            if (content instanceof LineArtChunk) {
                if (isBackground(content, pageBoundingBox)) {
                    backgrounds.add((LineArtChunk) content);
                }
            }
        }
        if (!backgrounds.isEmpty()) {
            LOGGER.log(Level.WARNING, "Detected background on page " + pageNumber);
            contents.removeAll(backgrounds);
        }
    }

    public static BoundingBox getPageBoundingBox(int pageNumber) {
        double[] cropBox = StaticResources.getDocument().getPage(pageNumber).getCropBox();
        if (cropBox == null) {
            return null;
        }
        return new BoundingBox(pageNumber, cropBox);
    }

    private static boolean isBackground(IObject content, BoundingBox pageBoundingBox) {
        return (content.getBoundingBox().getWidth() > 0.5 * pageBoundingBox.getWidth() &&
                content.getBoundingBox().getHeight() > 0.1 * pageBoundingBox.getHeight()) ||
                (content.getBoundingBox().getWidth() > 0.1 * pageBoundingBox.getWidth() &&
                        content.getBoundingBox().getHeight() > 0.5 * pageBoundingBox.getHeight());
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
                        File imageFile = new File(imagesFolder + File.separator + fileName + "_page_" + pageNumber + ".jpg");
                        ImageIO.write(image, "jpg", imageFile);
                    }
                    String jsonFileName = structuresFolder + File.separator + fileName + "_page_" + pageNumber + "_table_" + tableIndex + "_objects.json";
                    JsonFactory jsonFactory = new JsonFactory();
                    try (JsonGenerator jsonGenerator = jsonFactory.createGenerator(new File(jsonFileName), JsonEncoding.UTF8)
                            .setPrettyPrinter(new DefaultPrettyPrinter())
                            .setCodec(ObjectMapperHolder.getTableTransformerObjectMapper())) {
                        jsonGenerator.writePOJO(tableBorder);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    File wordsFile = new File(wordsFolder + File.separator + fileName + "_page_" + pageNumber + "_table_" + tableIndex + "_words.json");
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

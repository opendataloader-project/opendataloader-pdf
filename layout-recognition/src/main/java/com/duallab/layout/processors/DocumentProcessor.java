package com.duallab.layout.processors;

import com.duallab.layout.json.JsonWriter;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.markdown.MarkdownGeneratorFactory;
import com.duallab.layout.markdown.MarkdownGenerator;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.as.ASAtom;
import org.verapdf.containers.StaticCoreContainers;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSTrailer;
import org.verapdf.gf.model.impl.cos.GFCosInfo;
import org.verapdf.gf.model.impl.sa.GFSAPDFDocument;
import org.verapdf.parser.PDFFlavour;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.xmp.containers.StaticXmpCoreContainers;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DocumentProcessor {
    private static final Logger LOGGER = Logger.getLogger(DocumentProcessor.class.getCanonicalName());

    public static void processFile(String inputPdfName, String outputFolder, Config config) throws IOException {
        preprocessing(inputPdfName, config);
        calculateDocumentInfo(inputPdfName);
        List<TextChunk> hiddenTexts = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = new ArrayList<>(StaticContainers.getDocument().getArtifacts(pageNumber));
            trimTextChunksWhiteSpaces(pageContents);
            if (StaticLayoutContainers.isFindHiddenText()) {
                hiddenTexts.addAll(HiddenTextProcessor.findHiddenText(inputPdfName, pageContents));
            }
            pageContents = TableBorderProcessor.processTableBorders(pageContents, pageNumber);
            processBackgrounds(pageNumber, pageContents);
            pageContents = TextLineProcessor.processTextLines(pageContents);
            pageContents = ListProcessor.processLists(pageContents);
            pageContents = ParagraphProcessor.processParagraphs(pageContents);
            HeadingProcessor.processHeadings(pageContents);
            setIDs(pageContents);
            CaptionProcessor.processCaptions(pageContents);
            contents.add(pageContents);
        }

        HeaderFooterProcessor.processHeadersAndFooters(contents);
        ListProcessor.checkNeighborLists(contents);
        HeadingProcessor.detectHeadingsLevels(contents);
        File inputPDF = new File(inputPdfName);
        if (config.isGeneratePDF()) {
            PDFWriter.updatePDF(inputPDF, config.getPassword(), outputFolder, contents, hiddenTexts);
        }
        if (config.isGenerateJSON()) {
            JsonWriter.writeToJson(inputPDF, outputFolder, contents);
        }
        if (config.isGenerateMarkdown()) {
            MarkdownGenerator markdownGenerator = MarkdownGeneratorFactory.getMarkdownGenerator(inputPDF, outputFolder, config);
            markdownGenerator.writeToMarkdown(contents);
        }
    }

    public static void preprocessing(String pdfName, Config config) throws IOException {
        updateStaticContainers(config);
        PDDocument pdDocument = new PDDocument(pdfName);
        StaticResources.setDocument(pdDocument);
        GFSAPDFDocument document = new GFSAPDFDocument(pdDocument);
//        org.verapdf.gf.model.impl.containers.StaticContainers.setFlavour(Collections.singletonList(PDFAFlavour.WCAG_2_2));
        StaticResources.setFlavour(Collections.singletonList(PDFFlavour.WCAG_2_2));
        document.parseChunks();
        StaticContainers.updateContainers(document);
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
    }

    private static void updateStaticContainers(Config config) {
        StaticResources.clear();
        StaticLayoutContainers.clearContainers();
        org.verapdf.gf.model.impl.containers.StaticContainers.clearAllContainers();
        StaticCoreContainers.clearAllContainers();
        StaticXmpCoreContainers.clearAllContainers();
        StaticLayoutContainers.setFindHiddenText(config.isFindHiddenText());
        StaticContainers.setTextFormatting(config.isTextFormatted());
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

    private static void calculateDocumentInfo(String pdfName) {
        PDDocument document = StaticResources.getDocument();
        System.out.println("File name: " + pdfName);
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
            for (BoundingBox b : ((MultiBoundingBox)box).getBoundingBoxes()) {
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
                textNode.getSemanticType().getValue(), textNode.getFirstLine().getFirstTextChunk().getFontName(), 
                textNode.getFirstLine().getFirstTextChunk().getFontSize(), Arrays.toString(textNode.getTextColor()), 
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
                    backgrounds.add((LineArtChunk)content);
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

    private static void trimTextChunksWhiteSpaces(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject object = contents.get(i);
            if (object instanceof TextChunk) {
                contents.set(i, ChunksMergeUtils.getTrimTextChunk((TextChunk) object));
            }
        }
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
}

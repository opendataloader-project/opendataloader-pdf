package com.duallab.layout.processors;

import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSTrailer;
import org.verapdf.gf.model.impl.cos.GFCosInfo;
import org.verapdf.gf.model.impl.sa.GFSAPDFDocument;
import org.verapdf.model.coslayer.CosInfo;
import org.verapdf.parser.PDFFlavour;
import org.verapdf.pd.PDDocument;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.LinesPreprocessingConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.duallab.layout.processors.TableBorderProcessor.processTableBorders;

public class DocumentProcessor {
    private static final Logger LOGGER = Logger.getLogger(DocumentProcessor.class.getCanonicalName());

    public static void processFile(String pdfName, String outputName, String password) throws IOException {
//        System.out.println("Process " + pdfName);
        preprocessing(pdfName, password);
        calculateInfo(pdfName);
        List<TextChunk> hiddenTexts = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = new ArrayList<>(StaticContainers.getDocument().getArtifacts(pageNumber));
            contents.add(pageContents);
            if (StaticLayoutContainers.findHiddenText) {
                hiddenTexts.addAll(HiddenTextProcessor.findHiddenText(pdfName, pageContents));
            }
//        System.out.println("Table borders");
            /*List<TableBorderCell> tableBorderCells =*/ processTableBorders(pageContents, pageNumber);
//        System.out.println("Lists");
//        processClusterDetectionLists(contents);
//        System.out.println("Paragraphs");
            processBackgrounds(pageContents);
            removeSpaces(pageContents);
            TextLineProcessor.processLines(pageContents);
            ListProcessor.processExperimentalLists(pageContents);
            ParagraphProcessor.processParagraphs(pageContents);
//        System.out.println("Headings");
            HeadingProcessor.processHeadings(pageContents);
//        System.out.println("Lists");
//       processLists(contents);
            setIDs(pageContents);
//        System.out.println("Captions");
            CaptionProcessor.processCaptions(pageContents);
//        System.out.println("Images");
            ImageProcessor.processImages(pageContents);
        }
        HeaderFooterProcessor.processHeadersAndFooters(contents);
        PDFWriter.updatePDF(pdfName, password, outputName, contents, hiddenTexts/*, tableBorderCells*/);
    }

    public static void preprocessing(String pdfName, String password) throws IOException {
        StaticResources.clear();
        StaticLayoutContainers.setId(1);
        org.verapdf.gf.model.impl.containers.StaticContainers.clearAllContainers();
        StaticResources.setPassword(password);
        PDDocument pdDocument = new PDDocument(pdfName);
        StaticResources.setDocument(pdDocument);
        GFSAPDFDocument document = new GFSAPDFDocument(pdDocument);
        org.verapdf.gf.model.impl.containers.StaticContainers.setFlavour(Collections.singletonList(PDFAFlavour.WCAG_2_2));
        StaticResources.setFlavour(Collections.singletonList(PDFFlavour.WCAG_2_2));
        document.parseChunks();
        StaticContainers.updateContainers(document);
        LinesPreprocessingConsumer linesPreprocessingConsumer = new LinesPreprocessingConsumer();
        linesPreprocessingConsumer.findTableBorders();
        StaticContainers.setTableBordersCollection(new TableBordersCollection(linesPreprocessingConsumer.getTableBorders()));
    }

    private static void setIDs(List<IObject> contents) {
        for (IObject object : contents) {
            object.setRecognizedStructureId(StaticLayoutContainers.incrementId());
        }
    }

    private static void calculateInfo(String pdfName) {
        PDDocument document = StaticResources.getDocument();
        System.out.println("File name: " + pdfName);
        System.out.println("Number of pages: " + document.getNumberOfPages());
        COSTrailer trailer = document.getDocument().getTrailer();
        CosInfo info = getInfo(trailer);
        System.out.println("Author: " + (info.getAuthor() != null ? info.getAuthor() : info.getXMPCreator()));
        System.out.println("Title: " + (info.getTitle() != null ? info.getTitle() : info.getXMPTitle()));
        System.out.println("Creation date: " + (info.getCreationDate() != null ? info.getCreationDate() : info.getXMPCreateDate()));
        System.out.println("Modification date: " + (info.getModDate() != null ? info.getModDate() : info.getXMPModifyDate()));
    }

    private static CosInfo getInfo(COSTrailer trailer) {
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
//            System.out.println("Some problem");
            return;
        }
        contents.set(index, result);
        contents.removeAll(replacedContents);
    }

    public static void replaceContentsToResult(List<IObject> contents, IObject result, List<IObject> replacedContents) {
        Integer index = null;
        int i = 0;
        for (IObject content : contents) {
            if (replacedContents.contains(content)) {
                index = i;
                break;
            }
            i++;
        }
        if (index != null) {
            contents.set(index, result);
            contents.removeAll(replacedContents);
        }
    }

    public static void replaceContentsToResult(List<IObject> contents, List<? extends IObject> newContents,
                                               Stack<Integer> replaceIndexes, Stack<Integer> insertIndexes) {
        int listIndex = newContents.size() - 1;
        while (!replaceIndexes.isEmpty() && !insertIndexes.isEmpty()) {
            if (replaceIndexes.peek() < insertIndexes.peek()) {
                contents.add(insertIndexes.pop(), newContents.get(listIndex--));
            } else if (replaceIndexes.peek() > insertIndexes.peek()) {
                contents.remove((int)replaceIndexes.pop());
            } else {
                contents.set(insertIndexes.pop(), newContents.get(listIndex--));
                replaceIndexes.pop();
            }
        }
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
        return String.format("%s: font %s, text size %.2f, text color %s, text content \"%s\"", textNode.getSemanticType().getValue(),
                textNode.getFirstLine().getFirstTextChunk().getFontName(), textNode.getFirstLine().getFirstTextChunk().getFontSize(),
                Arrays.toString(textNode.getTextColor()), textNode.getValue().length() > 15 ? textNode.getValue().substring(0, 15) + "..." : textNode.getValue());
    }

    public static void processBackgrounds(List<IObject> contents) {
        Set<LineArtChunk> backgrounds = new HashSet<>();
        for (IObject content : contents) {
            if (content instanceof LineArtChunk) {
                double[] cropBox = StaticResources.getDocument().getPage(content.getPageNumber()).getCropBox();
                if (cropBox != null) {
                    BoundingBox pageBoundingBox = new BoundingBox(content.getPageNumber(), cropBox);
                    if ((content.getBoundingBox().getWidth() > 0.5 * pageBoundingBox.getWidth() &&
                            content.getBoundingBox().getHeight() > 0.1 * pageBoundingBox.getHeight()) ||
                            (content.getBoundingBox().getWidth() > 0.1 * pageBoundingBox.getWidth() &&
                                    content.getBoundingBox().getHeight() > 0.5 * pageBoundingBox.getHeight())) {
                        backgrounds.add((LineArtChunk)content);
                    }
                }
            }
        }
        if (!backgrounds.isEmpty()) {
            LOGGER.log(Level.WARNING, "Detected background on page " + backgrounds.iterator().next().getPageNumber());
            contents.removeAll(backgrounds);
        }
    }

    public static void removeSpaces(List<IObject> contents) {
        Set<TextChunk> spaceTextChunks = new HashSet<>();
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                if (((TextChunk)content).isWhiteSpaceChunk()) {
                    spaceTextChunks.add((TextChunk)content);
                }
            }
        }
        contents.removeAll(spaceTextChunks);
    }

    public static TextLine getTextLine(TextLine line) {
        return line;
//        int index = 0;
//        for (TextChunk textChunk : line.getTextChunks()) {
//            if (!textChunk.isWhiteSpaceChunk()) {
//                break;
//            }
//            index++;
//        }
//        if (index == 0) {
//            return line;
//        }
//        TextLine newLine = new TextLine();
//        int i = 0;
//        for (TextChunk chunk : line.getTextChunks()) {
//            if (i >= index) {
//                newLine.add(chunk);
//            }
//            i++;
//        }
//        return newLine;
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

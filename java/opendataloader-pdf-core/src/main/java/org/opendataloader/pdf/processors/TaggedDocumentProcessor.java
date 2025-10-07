package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TaggedDocumentProcessor {

    private static List<List<IObject>> contents;

    public static List<List<IObject>> processDocument(String inputPdfName, Config config) throws IOException {
        contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            contents.add(new ArrayList<>());
        }
        ITree tree = StaticContainers.getDocument().getTree();
        processStructElem(tree.getRoot());
        return contents;
    }

    private static void processStructElem(INode node) {
        if (node instanceof SemanticFigure) {
            processImage((SemanticFigure) node);
            return;
        }
        if (node.getInitialSemanticType() == null) {
            for (INode child : node.getChildren()) {
                processStructElem(child);
            }
            return;
        }
        switch (node.getInitialSemanticType()) {
            case CAPTION:
                processCaption(node);
                break;
            case HEADING:
                processHeading(node);
                break;
            case LIST:
                processList(node);
                break;
            case NUMBER_HEADING:
                processHeading(node);
                break;
            case PARAGRAPH:
                processParagraph(node);
                break;
            case TABLE:
                processTable(node);
                break;
//            case TABLE_OF_CONTENT:
//                processTOC(node);
//                break;
            case TITLE:
                processHeading(node);
                break;
            default:
                for (INode child : node.getChildren()) {
                    processStructElem(child);
                }
        }
    }

    private static void addObjectToContent(IObject object) {
        contents.get(object.getPageNumber()).add(object);
    }

    private static void processParagraph(INode paragraph) {
        addObjectToContent(createParagraph(paragraph));
    }

    private static SemanticParagraph createParagraph(INode paragraph) {
        List<IObject> contents = getContents(paragraph);
        contents = TextLineProcessor.processTextLines(contents);
        TextBlock textBlock = new TextBlock();
        for (IObject line : contents) {
            if (line instanceof TextLine) {
                textBlock.add((TextLine)line);
            }
        }
        return ParagraphProcessor.createParagraphFromTextBlock(textBlock);
    }

    private static void processHeading(INode node) {
        SemanticHeading heading = new SemanticHeading(createParagraph(node));
        heading.setHeadingLevel(1);//update
        addObjectToContent(heading);
    }

    private static void processList(INode node) {
        PDFList list = new PDFList();
        for (INode child : node.getChildren()) {
            if (child.getInitialSemanticType() == SemanticType.LIST) {
                //todo
            } else {
                ListItem listItem = new ListItem(new BoundingBox(), null);
                List<IObject> contents = getContents(child);
                contents = TextLineProcessor.processTextLines(contents);
                for (IObject line : contents) {
                    if (line instanceof TextLine) {
                        listItem.add((TextLine)line);
                    }
                }
                list.add(listItem);
            }
        }
        if (Objects.equals(list.getPageNumber(), list.getLastPageNumber())) {
            addObjectToContent(list);
        } else {
            processParagraph(node);
        }
    }

    private static void processTable(INode table) {
        TableBorder tableBorder = new TableBorder(table);
        addObjectToContent(tableBorder);
    }

    private static void processCaption(INode node) {
        SemanticCaption caption = new SemanticCaption(createParagraph(node));
        addObjectToContent(caption);
    }

    private static void processTOC(INode toc) {

    }

    private static void processImage(SemanticFigure image) {
        List<ImageChunk> images = image.getImages();
        if (!images.isEmpty()) {
            addObjectToContent(images.get(0));
        }
    }

    private static List<IObject> getContents(INode node) {
        List<IObject> result = new ArrayList<>();
        for (INode child : node.getChildren()) {
            if (child instanceof SemanticSpan) {
                result.add(((SemanticSpan)child).getColumns().get(0).getFirstLine().getFirstTextChunk());
            } else if (child instanceof SemanticFigure) {
                processImage((SemanticFigure)child);
//                result.addAll(((SemanticFigure)child).getImages());
            } else {
                result.addAll(getContents(child));
            }
        }
        return result;
    }
}

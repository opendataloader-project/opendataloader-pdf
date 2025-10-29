package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.api.Config;
import org.verapdf.gf.model.impl.sa.GFSANode;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.util.*;

public class TaggedDocumentProcessor {

    private static List<List<IObject>> contents;
    private static Stack<List<IObject>> contentsStack = new Stack<>();

    public static List<List<IObject>> processDocument(String inputPdfName, Config config) throws IOException {
        contents = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            contents.add(new ArrayList<>());
        }
        ITree tree = StaticContainers.getDocument().getTree();
        processStructElem(tree.getRoot());
        List<List<IObject>> artifacts = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            artifacts.add(new ArrayList<>());
            for (IObject content : StaticContainers.getDocument().getArtifacts(pageNumber)) {
                if (content instanceof ImageChunk) {
                    addObjectToContent(content);
                } else if (content instanceof TextChunk) {
                    artifacts.get(pageNumber).add(content);
                }
            }
        }
        HeaderFooterProcessor.processHeadersAndFooters(artifacts);
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            contents.get(pageNumber).addAll(artifacts.get(pageNumber));
        }
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            List<IObject> pageContents = TextLineProcessor.processTextLines(contents.get(pageNumber));
            contents.set(pageNumber, ParagraphProcessor.processParagraphs(pageContents));
        }
        return contents;
    }

    private static void processStructElem(INode node) {
        if (node instanceof SemanticFigure) {
            processImage((SemanticFigure) node);
            return;
        }
        if (node instanceof SemanticSpan) {
            processTextChunk((SemanticSpan) node);
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
                processNumberedHeading(node);
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
        Integer pageNumber = object.getPageNumber();
        if (pageNumber != null) {
            if (contentsStack.isEmpty()) {
                contents.get(pageNumber).add(object);
            } else {
                contentsStack.peek().add(object);
            }
        }
    }

    private static void processParagraph(INode paragraph) {
        addObjectToContent(createParagraph(paragraph));
    }

    private static SemanticParagraph createParagraph(INode paragraph) {
        List<IObject> contents = getContents(paragraph);
        contents = TextLineProcessor.processTextLines(contents);
        TextBlock textBlock = new TextBlock(new MultiBoundingBox());
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

    private static void processNumberedHeading(INode node) {
        SemanticHeading heading = new SemanticHeading(createParagraph(node));
        GFSANode gfsaNode = (GFSANode) node;
        String headingLevel = gfsaNode.getStructElem().getstandardType();
        heading.setHeadingLevel(Integer.parseInt(headingLevel.substring(1)));
        addObjectToContent(heading);
    }

    private static void processList(INode node) {
        PDFList list = new PDFList();
        list.setBoundingBox(new MultiBoundingBox());
        for (INode child : node.getChildren()) {
            if (child.getInitialSemanticType() == SemanticType.LIST) {
                //todo
            } else {
                ListItem listItem = new ListItem(new MultiBoundingBox(), null);
                List<IObject> contents = getContents(child);
                contents = TextLineProcessor.processTextLines(contents);
                for (IObject line : contents) {
                    if (line instanceof TextLine) {
                        listItem.add((TextLine)line);
                    }
                }
                if (listItem.getPageNumber() != null) {
                    list.add(listItem);
                }
            }
        }
        addObjectToContent(list);
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

    private static void processTextChunk(SemanticSpan semanticSpan) {
        addObjectToContent(semanticSpan.getColumns().get(0).getFirstLine().getFirstTextChunk());
    }

    private static List<IObject> getContents(INode node) {
        List<IObject> result = new ArrayList<>();
        for (INode child : node.getChildren()) {
            if (child instanceof SemanticSpan) {
                result.add(((SemanticSpan)child).getColumns().get(0).getFirstLine().getFirstTextChunk());
            } else if (child instanceof SemanticFigure) {
                processImage((SemanticFigure)child);
            } else {
                result.addAll(getContents(child));
            }
        }
        return result;
    }
}

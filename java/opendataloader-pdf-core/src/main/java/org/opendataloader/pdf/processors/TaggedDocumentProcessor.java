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
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.TableChecker;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.*;

public class TaggedDocumentProcessor {

    private static List<List<IObject>> contents;
    private static Stack<List<IObject>> contentsStack = new Stack<>();
    private static Set<Integer> pagesToProcess;

    public static List<List<IObject>> processDocument(String inputPdfName, Config config, Set<Integer> pages) {
        pagesToProcess = pages;
        contentsStack.clear();
        contents = new ArrayList<>();
        int totalPages = StaticContainers.getDocument().getNumberOfPages();
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            contents.add(new ArrayList<>());
        }
        ITree tree = StaticContainers.getDocument().getTree();
        processStructElem(tree.getRoot());
        List<List<IObject>> artifacts = collectArtifacts(totalPages);
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (!shouldProcessPage(pageNumber)) {
                continue;
            }
            artifacts.set(pageNumber, TextLineProcessor.processTextLines(artifacts.get(pageNumber)));
        }

        HeaderFooterProcessor.processHeadersAndFooters(artifacts, true);
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (!shouldProcessPage(pageNumber)) {
                continue;
            }
            contents.get(pageNumber).addAll(artifacts.get(pageNumber));
        }
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            if (!shouldProcessPage(pageNumber)) {
                continue;
            }
            List<IObject> pageContents = TextLineProcessor.processTextLines(contents.get(pageNumber));
            contents.set(pageNumber, ParagraphProcessor.processParagraphs(pageContents));
        }
        return contents;
    }

    private static List<List<IObject>> collectArtifacts(int totalPages) {
        List<List<IObject>> artifacts = new ArrayList<>();
        for (int pageNumber = 0; pageNumber < totalPages; pageNumber++) {
            artifacts.add(new ArrayList<>());
            if (!shouldProcessPage(pageNumber)) {
                continue;
            }
            for (IObject content : StaticContainers.getDocument().getArtifacts(pageNumber)) {
                if (content instanceof ImageChunk) {
                    artifacts.get(pageNumber).add(content);
                } else if (content instanceof TextChunk) {
                    TextChunk textChunk = (TextChunk) content;
                    if (!textChunk.isWhiteSpaceChunk() && !textChunk.isEmpty()) {
                        artifacts.get(pageNumber).add(content);
                    }
                }
            }
        }
        return artifacts;
    }

    /**
     * Checks if a page should be processed based on the filter.
     *
     * @param pageNumber 0-indexed page number
     * @return true if the page should be processed
     */
    private static boolean shouldProcessPage(int pageNumber) {
        return pagesToProcess == null || pagesToProcess.contains(pageNumber);
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
        if (pageNumber != null && shouldProcessPage(pageNumber)) {
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
        List<IObject> contents = new ArrayList<>();
        processChildContents(paragraph, contents);
        contents = TextLineProcessor.processTextLines(contents);
        TextBlock textBlock = new TextBlock(new MultiBoundingBox());
        for (IObject content : contents) {
            if (content instanceof TextLine) {
                textBlock.add((TextLine)content);
            } else {
                addObjectToContent(content);
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
                processList(child);
            } else if (child.getInitialSemanticType() == SemanticType.LIST_ITEM) {
                ListItem listItem = processListItem(child);
                if (listItem.getPageNumber() != null) {
                    list.add(listItem);
                }
            } else {
                processStructElem(child);
            }
        }
        addObjectToContent(list);
    }

    private static ListItem processListItem(INode node) {
        ListItem listItem = new ListItem(new MultiBoundingBox(), null);
        List<IObject> contents = new ArrayList<>();
        processChildContents(node, contents);
        contents = TextLineProcessor.processTextLines(contents);
        for (IObject content : contents) {
            if (content instanceof TextLine) {
                listItem.add((TextLine)content);
            } else {
                listItem.getContents().add(content);
            }
        }
        return listItem;
    }

    private static void processTable(INode tableNode) {
        List<INode> tableRows = processTableRows(tableNode);
        if (tableRows.isEmpty()) {
            return;
        }
        int numberOfRows = tableRows.size();
        int numberOfColumns = TableChecker.getNumberOfColumns(tableRows.get(0));
        List<List<TableBorderCell>> table = new ArrayList<>(numberOfRows);
        for (int rowNumber = 0; rowNumber < numberOfRows; rowNumber++) {
            addTableRow(numberOfColumns, table);
        }
        BoundingBox tableBoundingBox = new MultiBoundingBox();
        for (int rowNumber = 0; rowNumber < tableRows.size(); rowNumber++) {
            int columnNumber = 0;
            for (INode elem : tableRows.get(rowNumber).getChildren()) {
                SemanticType type = elem.getInitialSemanticType();
                if (SemanticType.TABLE_CELL != type && SemanticType.TABLE_HEADER != type) {
                    continue;
                }
                while (columnNumber < numberOfColumns && table.get(rowNumber).get(columnNumber) != null) {
                    ++columnNumber;
                }
                TableBorderCell cell = new TableBorderCell(elem, rowNumber, columnNumber);
                processTableCell(cell, elem);
                tableBoundingBox.union(cell.getBoundingBox());
                for (int i = 0; i < cell.getRowSpan(); i++) {
                    if (rowNumber + i >= numberOfRows) {
                        numberOfRows++;
                        addTableRow(numberOfColumns, table);
                    }
                    for (int j = 0; j < cell.getColSpan(); j++) {
                        if (columnNumber + j >= numberOfColumns) {
                            addTableColumn(table);
                            numberOfColumns++;
                        }
                        table.get(rowNumber + i).set(columnNumber + j, cell);
                    }
                }
                columnNumber += cell.getColSpan();
            }
        }
        if (tableBoundingBox.isEmpty()) {
            //empty table
            return;
        }
        TableBorder tableBorder = new TableBorder(tableBoundingBox, createRowsForTable(table, numberOfRows, numberOfColumns),
            numberOfRows, numberOfColumns);
        setBoundingBoxesForTableRowsAndTableCells(tableBorder);
        addObjectToContent(tableBorder);
    }

    private static List<INode> processTableRows(INode table) {
        List<INode> listTR = new LinkedList<>();
        for (INode elem : table.getChildren()) {
            SemanticType type = elem.getInitialSemanticType();
            if (SemanticType.TABLE_ROW == type) {
                listTR.add(elem);
                processTableRowsChildren(elem);
            } else if (SemanticType.TABLE_FOOTER == type || SemanticType.TABLE_BODY == type ||
                SemanticType.TABLE_HEADERS == type) {
                for (INode child : elem.getChildren()) {
                    if (SemanticType.TABLE_ROW == child.getInitialSemanticType()) {
                        listTR.add(child);
                        processTableRowsChildren(child);
                    } else {
                        processStructElem(child);
                    }
                }
            } else {
                processStructElem(elem);
            }
        }
        return listTR;
    }

    private static void processTableRowsChildren(INode tableRow) {
        for (INode tableCell : tableRow.getChildren()) {
            SemanticType tableCellType = tableCell.getInitialSemanticType();
            if (SemanticType.TABLE_CELL != tableCellType && SemanticType.TABLE_HEADER != tableCellType) {
                processStructElem(tableCell);
            }
        }
    }

    private static void addTableRow(int numberOfColumns, List<List<TableBorderCell>> table) {
        List<TableBorderCell> row = new ArrayList<>(numberOfColumns);
        table.add(row);
        for (int columnNumber = 0; columnNumber < numberOfColumns; columnNumber++) {
            row.add(null);
        }
    }

    private static void addTableColumn(List<List<TableBorderCell>> table) {
        for (List<TableBorderCell> tableBorderCells : table) {
            tableBorderCells.add(null);
        }
    }

    private static void processTableCell(TableBorderCell cell, INode elem) {
        processChildContents(elem, cell.getContents());
        BoundingBox cellBoundingBox = new MultiBoundingBox();
        for (IObject content : cell.getContents()) {
            cellBoundingBox.union(content.getBoundingBox());
        }
        cell.setBoundingBox(cellBoundingBox);
    }

    private static void processChildContents(INode elem, List<IObject> contents) {
        contentsStack.add(contents);
        for (INode childChild : elem.getChildren()) {
            processStructElem(childChild);
        }
        contentsStack.pop();
    }

    private static TableBorderRow[] createRowsForTable(List<List<TableBorderCell>> table, int numberOfRows, int numberOfColumns) {
        TableBorderRow[] rows = new TableBorderRow[numberOfRows];
        for (int rowNumber = 0; rowNumber < numberOfRows; rowNumber++) {
            rows[rowNumber] = new TableBorderRow(rowNumber, numberOfColumns, null);
        }
        for (int rowNumber = 0; rowNumber < numberOfRows; rowNumber++) {
            for (int colNumber = 0; colNumber < numberOfColumns; colNumber++) {
                rows[rowNumber].getCells()[colNumber] = table.get(rowNumber).get(colNumber);
                if (rows[rowNumber].getCell(colNumber) == null) {
                    rows[rowNumber].getCells()[colNumber] = new TableBorderCell(rowNumber, colNumber, 1, 1, 0L);
                }
            }
        }
        return rows;
    }

    private static void setBoundingBoxesForTableRowsAndTableCells(TableBorder tableBorder) {
        BoundingBox boundingBox = new BoundingBox(tableBorder.getPageNumber(),
            tableBorder.getTopY(), tableBorder.getLeftX(), tableBorder.getTopY(), tableBorder.getLeftX());
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            BoundingBox rowBoundingBox = new MultiBoundingBox();
            for (int colNumber = 0; colNumber < tableBorder.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = tableBorder.getCell(rowNumber, colNumber);
                if (cell.getColNumber() == colNumber && cell.getRowNumber() == rowNumber) {
                    if (cell.getBoundingBox().isEmpty()) {
                        cell.setBoundingBox(boundingBox);
                    } else {
                        rowBoundingBox.union(tableBorder.getCell(rowNumber, colNumber).getBoundingBox());
                    }
                }
            }
            tableBorder.getRow(rowNumber).setBoundingBox(rowBoundingBox.isEmpty() ? boundingBox : rowBoundingBox);
        }
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
                TableBorderCell cell = tableBorder.getCell(rowNumber, columnNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == columnNumber && cell.getBoundingBox().isEmpty()) {
                    cell.setBoundingBox(boundingBox);
                }
            }
        }
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

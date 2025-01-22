/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;

public class TableBorderProcessor {
    
    private static final double LINE_ART_PERCENT = 0.9;

    public static List<IObject> processTableBorders(List<IObject> contents, int pageNumber) {
        List<IObject> newContents = new ArrayList<>();
        Set<TableBorder> processedTableBorders = new HashSet<>(); 
        for (IObject content : contents) {
            TableBorder tableBorder = addContentToTableBorder(content);
            if (tableBorder != null) {
                if (!processedTableBorders.contains(tableBorder)) {
                    processedTableBorders.add(tableBorder);
                    newContents.add(tableBorder);
                }
            } else {
                newContents.add(content);
            }
        }
        for (TableBorder border : processedTableBorders) {
            StaticContainers.getTableBordersCollection().removeTableBorder(border, pageNumber);
            processTableBorder(border, pageNumber);
        }
        return newContents;
    }

    private static TableBorder addContentToTableBorder(IObject content) {
        TableBorder tableBorder = StaticContainers.getTableBordersCollection().getTableBorder(content.getBoundingBox());
        if (tableBorder != null) {
            if (content instanceof LineChunk) {
                return tableBorder.isOneCellTable() ? null : tableBorder;
            }
            if (content instanceof LineArtChunk && BoundingBox.areSameBoundingBoxes(tableBorder.getBoundingBox(), content.getBoundingBox())) {
                return tableBorder;
            }
            TableBorderCell tableBorderCell = tableBorder.getTableBorderCell(content);
            if (tableBorderCell != null) {
                if (content instanceof LineArtChunk && 
                        tableBorderCell.getBoundingBox().getIntersectionPercent(content.getBoundingBox()) > LINE_ART_PERCENT) {
                    return tableBorder;
                }
                tableBorderCell.addContentObject(content);
                return tableBorder;
            }
            if (content instanceof LineArtChunk) {
                return tableBorder;
            }
        }
        return null;
    }

    public static void processTableBorder(TableBorder tableBorder, int pageNumber) {
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = tableBorder.getRow(rowNumber);
            for (int colNumber = 0; colNumber < tableBorder.getNumberOfColumns(); colNumber++) {
                TableBorderCell tableBorderCell = row.getCell(colNumber);
                if (tableBorderCell.getRowNumber() == rowNumber && tableBorderCell.getColNumber() == colNumber) {
                    tableBorderCell.setContents(processTableCellContent(tableBorderCell.getContents(), pageNumber));
                }
            }
        }
    }

    public static List<IObject> processTableCellContent(List<IObject> contents, int pageNumber) {
        List<IObject> newContents = TableBorderProcessor.processTableBorders(contents, pageNumber);
        newContents = TextLineProcessor.processTextLines(newContents);
        List<List<IObject>> contentsList = new ArrayList<>(1);
        contentsList.add(newContents);
        ListProcessor.processLists(contentsList, true);
        newContents = contentsList.get(0);
        newContents = ParagraphProcessor.processParagraphs(newContents);
        newContents = ListProcessor.processListsFromTextNodes(newContents);
        HeadingProcessor.processHeadings(newContents);
        DocumentProcessor.setIDs(newContents);
        CaptionProcessor.processCaptions(newContents);
        contentsList.set(0, newContents);
        ListProcessor.checkNeighborLists(contentsList);
        newContents = contentsList.get(0);
        return newContents;
    }

    public static void checkNeighborTables(List<List<IObject>> contents) {
        TableBorder previousTable = null;
        for (List<IObject> iObjects : contents) {
            for (IObject content : iObjects) {
                if (content instanceof TableBorder) {
                    TableBorder currentTable = (TableBorder) content;
                    if (previousTable != null) {
                        checkNeighborTables(previousTable, currentTable);
                    }
                    previousTable = currentTable;
                } else {
                    if (!HeaderFooterProcessor.isHeaderOrFooter(content) &&
                            !(content instanceof LineChunk) && !(content instanceof LineArtChunk)) {
                        previousTable = null;
                    }
                }
            }
        }
    }

    public static void checkNeighborTables(TableBorder previousTable, TableBorder currentTable) {
        if (Objects.equals(previousTable.getPageNumber(), currentTable.getPageNumber())) {
            return;
        }
        if (currentTable.getNumberOfColumns() != previousTable.getNumberOfColumns()) {
            return;
        }
        if (!NodeUtils.areCloseNumbers(currentTable.getWidth(), previousTable.getWidth())) {
            return;
        }
        for (int columnNumber = 0; columnNumber < previousTable.getNumberOfColumns(); columnNumber++) {
            TableBorderCell cell1 = previousTable.getCell(0, columnNumber);
            TableBorderCell cell2 = currentTable.getCell(0, columnNumber);
            if (!NodeUtils.areCloseNumbers(cell1.getWidth(), cell2.getWidth())) {
                return;
            }
        }
        previousTable.setNextTable(currentTable);
        currentTable.setPreviousTable(previousTable);
    }
}

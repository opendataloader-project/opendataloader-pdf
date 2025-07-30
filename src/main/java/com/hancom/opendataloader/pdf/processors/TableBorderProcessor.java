/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TableBorderProcessor {

    private static final double LINE_ART_PERCENT = 0.9;
    private static final double NEIGHBOUR_TABLE_EPSILON = 0.2;

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
            Set<TableBorderCell> tableBorderCells = tableBorder.getTableBorderCells(content);
            if (!tableBorderCells.isEmpty()) {
                if (tableBorderCells.size() > 1 && content instanceof TextChunk) {
                    TextChunk textChunk = (TextChunk) content;
                    for (TableBorderCell tableBorderCell : tableBorderCells) {
                        TextChunk currentTextChunk = getTextChunkPartForTableCell(textChunk, tableBorderCell);
                        if (currentTextChunk != null && !currentTextChunk.isEmpty()) {
                            tableBorderCell.addContentObject(currentTextChunk);
                        }
                    }
                } else {
                    for (TableBorderCell tableBorderCell : tableBorderCells) {
                        if (content instanceof LineArtChunk &&
                                tableBorderCell.getBoundingBox().getIntersectionPercent(content.getBoundingBox()) > LINE_ART_PERCENT) {
                            return tableBorder;
                        }
                        tableBorderCell.addContentObject(content);
                        break;
                    }
                }
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
                if (content instanceof TableBorder && !((TableBorder) content).isTextBlock()) {
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
        if (currentTable.getNumberOfColumns() != previousTable.getNumberOfColumns()) {
            return;
        }
        if (!NodeUtils.areCloseNumbers(currentTable.getWidth(), previousTable.getWidth(), NEIGHBOUR_TABLE_EPSILON)) {
            return;
        }
        for (int columnNumber = 0; columnNumber < previousTable.getNumberOfColumns(); columnNumber++) {
            TableBorderCell cell1 = previousTable.getCell(0, columnNumber);
            TableBorderCell cell2 = currentTable.getCell(0, columnNumber);
            if (!NodeUtils.areCloseNumbers(cell1.getWidth(), cell2.getWidth(), NEIGHBOUR_TABLE_EPSILON)) {
                return;
            }
        }
        previousTable.setNextTable(currentTable);
        currentTable.setPreviousTable(previousTable);
    }

    public static TextChunk getTextChunkPartForTableCell(TextChunk textChunk, TableBorderCell cell) {
        Integer start = textChunk.getSymbolStartIndexByCoordinate(cell.getLeftX());
        if (start == null) {
            return null;
        }
        Integer end = textChunk.getSymbolEndIndexByCoordinate(cell.getRightX());
        if (end == null) {
            return null;
        }
        TextChunk result = TextChunk.getTextChunk(textChunk, start, end);
        return ChunksMergeUtils.getTrimTextChunk(result);
    }
}

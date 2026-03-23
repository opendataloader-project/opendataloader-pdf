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

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

class TableStructureNormalizer {

    private static final int MAX_UNDERSEGMENTED_ROWS = 2;
    private static final int MIN_UNDERSEGMENTED_COLUMNS = 3;
    private static final int MIN_UNDERSEGMENTED_TEXT_LINES = 8;
    private static final int MIN_ROW_BAND_MISMATCH = 2;
    private static final int OVERSIZED_CELL_LINE_COUNT = 4;
    private static final double MIN_ROW_BAND_EPSILON = 3.0;
    private static final double ROW_BAND_EPSILON_RATIO = 0.6;
    private static final double ROW_BAND_ASSIGNMENT_EPSILON = 6.0;
    private static final double ROW_ORDER_EPSILON = 1.5;
    private static final Comparator<IObject> CONTENT_COMPARATOR =
        Comparator.comparingDouble(IObject::getCenterY).reversed()
            .thenComparingDouble(IObject::getLeftX);
    private static final Comparator<TextLine> TEXT_LINE_COMPARATOR =
        Comparator.comparingDouble(TextLine::getCenterY).reversed()
            .thenComparingDouble(TextLine::getLeftX);

    static TableBorder normalize(List<IObject> rawPageContents, TableBorder tableBorder) {
        if (rawPageContents == null || rawPageContents.isEmpty()) {
            return tableBorder;
        }
        if (tableBorder.isTextBlock()) {
            return tableBorder;
        }
        if (tableBorder.getNumberOfRows() > MAX_UNDERSEGMENTED_ROWS ||
                tableBorder.getNumberOfColumns() < MIN_UNDERSEGMENTED_COLUMNS) {
            return tableBorder;
        }

        List<ColumnSnapshot> columnSnapshots = collectColumnSnapshots(rawPageContents, tableBorder);
        int denseColumns = countDenseColumns(columnSnapshots);
        if (denseColumns < 2) {
            return tableBorder;
        }

        List<RowBand> rowBands = collectRowBands(tableBorder, columnSnapshots);
        if (rowBands.size() < tableBorder.getNumberOfRows() + MIN_ROW_BAND_MISMATCH) {
            return tableBorder;
        }

        TableBorder rebuiltTable = rebuildTable(tableBorder, rowBands);
        if (!isReplacementQualityBetter(tableBorder, rebuiltTable)) {
            return tableBorder;
        }

        return rebuiltTable;
    }

    private static List<ColumnSnapshot> collectColumnSnapshots(List<IObject> rawPageContents, TableBorder tableBorder) {
        List<ColumnSnapshot> columnSnapshots = new ArrayList<>(tableBorder.getNumberOfColumns());
        for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
            columnSnapshots.add(new ColumnSnapshot());
        }

        for (IObject content : rawPageContents) {
            if (content == null || !isInsideTableBounds(content, tableBorder)) {
                continue;
            }

            if (content instanceof TextChunk) {
                addTextChunkToColumns((TextChunk) content, tableBorder, columnSnapshots);
            } else if (!(content instanceof LineChunk) && !(content instanceof LineArtChunk)) {
                int columnNumber = findBestColumn(content, tableBorder);
                if (columnNumber >= 0) {
                    columnSnapshots.get(columnNumber).addContent(content);
                }
            }
        }

        for (ColumnSnapshot columnSnapshot : columnSnapshots) {
            columnSnapshot.finalizeSnapshot();
        }
        return columnSnapshots;
    }

    private static void addTextChunkToColumns(TextChunk textChunk, TableBorder tableBorder,
                                              List<ColumnSnapshot> columnSnapshots) {
        for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
            TextChunk columnTextChunk = TableBorderProcessor.getTextChunkPartForRange(textChunk,
                tableBorder.getLeftX(columnNumber), tableBorder.getRightX(columnNumber));
            if (columnTextChunk != null && !columnTextChunk.isEmpty() && !columnTextChunk.isWhiteSpaceChunk()) {
                columnSnapshots.get(columnNumber).addContent(columnTextChunk);
            }
        }
    }

    private static int findBestColumn(IObject content, TableBorder tableBorder) {
        double centerX = content.getCenterX();
        for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
            if (centerX >= tableBorder.getLeftX(columnNumber) && centerX <= tableBorder.getRightX(columnNumber)) {
                return columnNumber;
            }
        }

        int closestColumn = -1;
        double closestDistance = Double.MAX_VALUE;
        for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
            double columnCenter = (tableBorder.getLeftX(columnNumber) + tableBorder.getRightX(columnNumber)) / 2;
            double distance = Math.abs(centerX - columnCenter);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestColumn = columnNumber;
            }
        }
        return closestColumn;
    }

    private static boolean isInsideTableBounds(IObject content, TableBorder tableBorder) {
        return content.getCenterX() >= tableBorder.getLeftX() && content.getCenterX() <= tableBorder.getRightX() &&
            content.getCenterY() >= tableBorder.getBottomY() && content.getCenterY() <= tableBorder.getTopY();
    }

    private static int countDenseColumns(List<ColumnSnapshot> columnSnapshots) {
        int denseColumns = 0;
        for (ColumnSnapshot columnSnapshot : columnSnapshots) {
            if (columnSnapshot.meaningfulLineCount >= MIN_UNDERSEGMENTED_TEXT_LINES) {
                denseColumns++;
            }
        }
        return denseColumns;
    }

    private static List<RowBand> collectRowBands(TableBorder tableBorder, List<ColumnSnapshot> columnSnapshots) {
        List<TextLine> textLines = new ArrayList<>();
        for (ColumnSnapshot columnSnapshot : columnSnapshots) {
            textLines.addAll(columnSnapshot.textLines);
        }
        textLines.sort(TEXT_LINE_COMPARATOR);

        List<RowBand> rowBands = new ArrayList<>();
        for (TextLine textLine : textLines) {
            RowBand matchingBand = findMatchingRowBand(rowBands, textLine);
            if (matchingBand == null) {
                matchingBand = new RowBand(tableBorder.getNumberOfColumns());
                rowBands.add(matchingBand);
            }
            matchingBand.addLine(textLine);
        }

        for (int columnNumber = 0; columnNumber < columnSnapshots.size(); columnNumber++) {
            for (IObject content : columnSnapshots.get(columnNumber).contents) {
                RowBand matchingBand = findBestRowBand(rowBands, content);
                if (matchingBand != null) {
                    matchingBand.addContent(columnNumber, content);
                }
            }
        }

        rowBands.removeIf(rowBand -> rowBand.isEmpty());
        rowBands.sort(Comparator.comparingDouble(RowBand::getCenterY).reversed());
        rowBands.forEach(RowBand::sortContents);
        return rowBands;
    }

    private static RowBand findMatchingRowBand(List<RowBand> rowBands, TextLine textLine) {
        for (RowBand rowBand : rowBands) {
            double epsilon = Math.max(MIN_ROW_BAND_EPSILON,
                Math.min(rowBand.getAverageHeight(), textLine.getHeight()) * ROW_BAND_EPSILON_RATIO);
            if (Math.abs(rowBand.getCenterY() - textLine.getCenterY()) <= epsilon ||
                    rowBand.hasVerticalOverlap(textLine.getTopY(), textLine.getBottomY())) {
                return rowBand;
            }
        }
        return null;
    }

    private static RowBand findBestRowBand(List<RowBand> rowBands, IObject content) {
        RowBand bestBand = null;
        double bestDistance = Double.MAX_VALUE;
        for (RowBand rowBand : rowBands) {
            if (rowBand.hasVerticalOverlap(content.getTopY(), content.getBottomY())) {
                double distance = Math.abs(rowBand.getCenterY() - content.getCenterY());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestBand = rowBand;
                }
            }
        }
        if (bestBand != null) {
            return bestBand;
        }

        for (RowBand rowBand : rowBands) {
            double distance = Math.abs(rowBand.getCenterY() - content.getCenterY());
            if (distance < bestDistance && distance <= ROW_BAND_ASSIGNMENT_EPSILON + rowBand.getAverageHeight()) {
                bestDistance = distance;
                bestBand = rowBand;
            }
        }
        return bestBand;
    }

    private static TableBorder rebuildTable(TableBorder originalTable, List<RowBand> rowBands) {
        TableBorder rebuiltTable = new TableBorder(rowBands.size(), originalTable.getNumberOfColumns());
        rebuiltTable.setRecognizedStructureId(originalTable.getRecognizedStructureId());
        rebuiltTable.setBoundingBox(new BoundingBox(originalTable.getBoundingBox()));
        rebuiltTable.setNode(originalTable.getNode());
        rebuiltTable.setIndex(originalTable.getIndex());
        rebuiltTable.setLevel(originalTable.getLevel());
        rebuiltTable.setPreviousTable(originalTable.getPreviousTable());
        rebuiltTable.setNextTable(originalTable.getNextTable());

        for (int rowNumber = 0; rowNumber < rowBands.size(); rowNumber++) {
            RowBand rowBand = rowBands.get(rowNumber);
            TableBorderRow rebuiltRow = new TableBorderRow(rowNumber, originalTable.getNumberOfColumns(),
                originalTable.getRecognizedStructureId());
            rebuiltRow.setBoundingBox(rowBand.createRowBoundingBox(originalTable));
            rebuiltTable.getRows()[rowNumber] = rebuiltRow;

            for (int columnNumber = 0; columnNumber < originalTable.getNumberOfColumns(); columnNumber++) {
                TableBorderCell rebuiltCell = new TableBorderCell(rowNumber, columnNumber, 1, 1,
                    originalTable.getRecognizedStructureId());
                rebuiltCell.setContents(rowBand.getContents(columnNumber));
                rebuiltCell.setBoundingBox(rowBand.createCellBoundingBox(originalTable, columnNumber));
                rebuiltRow.getCells()[columnNumber] = rebuiltCell;
            }
        }

        rebuiltTable.calculateCoordinatesUsingBoundingBoxesOfRowsAndColumns();
        return rebuiltTable;
    }

    private static boolean isReplacementQualityBetter(TableBorder originalTable, TableBorder rebuiltTable) {
        int originalNonEmptyRows = countNonEmptyRows(originalTable);
        int rebuiltNonEmptyRows = countNonEmptyRows(rebuiltTable);
        if (rebuiltNonEmptyRows <= originalNonEmptyRows) {
            return false;
        }

        int originalNonEmptyColumns = countNonEmptyColumns(originalTable);
        int rebuiltNonEmptyColumns = countNonEmptyColumns(rebuiltTable);
        if (rebuiltNonEmptyColumns < originalNonEmptyColumns) {
            return false;
        }

        if (!hasMonotonicRowOrder(rebuiltTable)) {
            return false;
        }

        TableLineStats originalLineStats = collectTableLineStats(originalTable);
        TableLineStats rebuiltLineStats = collectTableLineStats(rebuiltTable);

        return rebuiltLineStats.oversizedCellCount < originalLineStats.oversizedCellCount ||
            rebuiltLineStats.maxMeaningfulTextLines < originalLineStats.maxMeaningfulTextLines;
    }

    private static int countNonEmptyRows(TableBorder tableBorder) {
        int count = 0;
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            boolean hasContent = false;
            for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
                TableBorderCell cell = tableBorder.getRow(rowNumber).getCell(columnNumber);
                if (cell != null && cell.getRowNumber() == rowNumber && cell.getColNumber() == columnNumber &&
                        hasMeaningfulContent(cell.getContents())) {
                    hasContent = true;
                    break;
                }
            }
            if (hasContent) {
                count++;
            }
        }
        return count;
    }

    private static int countNonEmptyColumns(TableBorder tableBorder) {
        int count = 0;
        for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
            boolean hasContent = false;
            for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
                TableBorderCell cell = tableBorder.getRow(rowNumber).getCell(columnNumber);
                if (cell != null && cell.getRowNumber() == rowNumber && cell.getColNumber() == columnNumber &&
                        hasMeaningfulContent(cell.getContents())) {
                    hasContent = true;
                    break;
                }
            }
            if (hasContent) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasMeaningfulContent(List<IObject> contents) {
        if (contents == null) {
            return false;
        }
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                if (!((TextChunk) content).isWhiteSpaceChunk() && !((TextChunk) content).isEmpty()) {
                    return true;
                }
            } else if (content instanceof TextLine) {
                if (!((TextLine) content).isSpaceLine() && !((TextLine) content).isEmpty()) {
                    return true;
                }
            } else if (!(content instanceof LineChunk) && !(content instanceof LineArtChunk)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasMonotonicRowOrder(TableBorder tableBorder) {
        double previousCenterY = Double.POSITIVE_INFINITY;
        double previousBottomY = Double.POSITIVE_INFINITY;
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = tableBorder.getRow(rowNumber);
            double currentCenterY = row.getBoundingBox().getCenterY();
            if (currentCenterY >= previousCenterY) {
                return false;
            }
            if (row.getTopY() > previousBottomY + ROW_ORDER_EPSILON) {
                return false;
            }
            previousCenterY = currentCenterY;
            previousBottomY = row.getBottomY();
        }
        return true;
    }

    private static TableLineStats collectTableLineStats(TableBorder tableBorder) {
        int oversizedCellCount = 0;
        int maxMeaningfulTextLines = 0;
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            for (int columnNumber = 0; columnNumber < tableBorder.getNumberOfColumns(); columnNumber++) {
                TableBorderCell cell = tableBorder.getRow(rowNumber).getCell(columnNumber);
                if (cell != null && cell.getRowNumber() == rowNumber && cell.getColNumber() == columnNumber) {
                    int meaningfulTextLines = countMeaningfulTextLines(cell.getContents());
                    if (meaningfulTextLines >= OVERSIZED_CELL_LINE_COUNT) {
                        oversizedCellCount++;
                    }
                    maxMeaningfulTextLines = Math.max(maxMeaningfulTextLines, meaningfulTextLines);
                }
            }
        }
        return new TableLineStats(oversizedCellCount, maxMeaningfulTextLines);
    }

    private static int countMeaningfulTextLines(List<IObject> contents) {
        if (contents == null || contents.isEmpty()) {
            return 0;
        }

        List<IObject> orderedContents = new ArrayList<>(contents);
        orderedContents.sort(CONTENT_COMPARATOR);
        int count = 0;
        for (IObject content : TextLineProcessor.processTextLines(orderedContents)) {
            if (content instanceof TextLine) {
                TextLine textLine = (TextLine) content;
                if (!textLine.isEmpty() && !textLine.isSpaceLine()) {
                    count++;
                }
            }
        }
        return count;
    }

    private static final class ColumnSnapshot {

        private final List<IObject> contents = new ArrayList<>();
        private final List<TextLine> textLines = new ArrayList<>();
        private int meaningfulLineCount;

        private void addContent(IObject content) {
            contents.add(content);
        }

        private void finalizeSnapshot() {
            contents.sort(CONTENT_COMPARATOR);
            List<IObject> textCandidates = new ArrayList<>();
            for (IObject content : contents) {
                if (content instanceof TextChunk || content instanceof TextLine) {
                    textCandidates.add(content);
                }
            }
            for (IObject content : TextLineProcessor.processTextLines(textCandidates)) {
                if (content instanceof TextLine) {
                    TextLine textLine = (TextLine) content;
                    if (!textLine.isEmpty() && !textLine.isSpaceLine()) {
                        textLines.add(textLine);
                        meaningfulLineCount++;
                    }
                }
            }
        }
    }

    private static final class TableLineStats {

        private final int oversizedCellCount;
        private final int maxMeaningfulTextLines;

        private TableLineStats(int oversizedCellCount, int maxMeaningfulTextLines) {
            this.oversizedCellCount = oversizedCellCount;
            this.maxMeaningfulTextLines = maxMeaningfulTextLines;
        }
    }

    private static final class RowBand {

        private final List<List<IObject>> contentsByColumn;
        private double topY = Double.NEGATIVE_INFINITY;
        private double bottomY = Double.POSITIVE_INFINITY;
        private double centerY;
        private double averageHeight;
        private int lineCount;

        private RowBand(int columnCount) {
            this.contentsByColumn = new ArrayList<>(columnCount);
            for (int columnNumber = 0; columnNumber < columnCount; columnNumber++) {
                this.contentsByColumn.add(new ArrayList<>());
            }
        }

        private void addLine(TextLine textLine) {
            updateBounds(textLine.getTopY(), textLine.getBottomY(), textLine.getCenterY(), textLine.getHeight());
        }

        private void addContent(int columnNumber, IObject content) {
            contentsByColumn.get(columnNumber).add(content);
            updateBounds(content.getTopY(), content.getBottomY(), content.getCenterY(), content.getHeight());
        }

        private void updateBounds(double contentTopY, double contentBottomY, double contentCenterY, double height) {
            topY = Math.max(topY, contentTopY);
            bottomY = Math.min(bottomY, contentBottomY);
            centerY = ((centerY * lineCount) + contentCenterY) / (lineCount + 1);
            averageHeight = ((averageHeight * lineCount) + height) / (lineCount + 1);
            lineCount++;
        }

        private boolean hasVerticalOverlap(double contentTopY, double contentBottomY) {
            return contentBottomY <= topY + ROW_ORDER_EPSILON && contentTopY >= bottomY - ROW_ORDER_EPSILON;
        }

        private boolean isEmpty() {
            for (List<IObject> contents : contentsByColumn) {
                if (!contents.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private void sortContents() {
            for (List<IObject> contents : contentsByColumn) {
                contents.sort(CONTENT_COMPARATOR);
            }
        }

        private List<IObject> getContents(int columnNumber) {
            return new ArrayList<>(contentsByColumn.get(columnNumber));
        }

        private BoundingBox createRowBoundingBox(TableBorder tableBorder) {
            return new BoundingBox(tableBorder.getPageNumber(), tableBorder.getLeftX(), bottomY,
                tableBorder.getRightX(), topY);
        }

        private BoundingBox createCellBoundingBox(TableBorder tableBorder, int columnNumber) {
            return new BoundingBox(tableBorder.getPageNumber(), tableBorder.getLeftX(columnNumber), bottomY,
                tableBorder.getRightX(columnNumber), topY);
        }

        private double getCenterY() {
            return centerY;
        }

        private double getAverageHeight() {
            return averageHeight;
        }
    }
}

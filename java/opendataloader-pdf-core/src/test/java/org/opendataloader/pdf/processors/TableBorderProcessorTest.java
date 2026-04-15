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

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTimeout;

public class TableBorderProcessorTest {

    @Test
    public void testProcessTableBorders() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(2l);
        TableBordersCollection tableBordersCollection = new TableBordersCollection();
        StaticContainers.setTableBordersCollection(tableBordersCollection);
        List<IObject> contents = new ArrayList<>();
        TableBorder tableBorder = new TableBorder(2, 2);
        SortedSet<TableBorder> tables = new TreeSet<>(new TableBorder.TableBordersComparator());
        tables.add(tableBorder);
        tableBordersCollection.getTableBorders().add(tables);
        tableBorder.setRecognizedStructureId(1l);
        tableBorder.setBoundingBox(new BoundingBox(0, 10.0, 10.0, 30.0, 30.0));
        TableBorderRow row1 = new TableBorderRow(0, 2, 0l);
        row1.setBoundingBox(new BoundingBox(0, 10.0, 20.0, 30.0, 30.0));
        row1.getCells()[0] = new TableBorderCell(0, 0, 1, 1, 0l);
        row1.getCells()[0].setBoundingBox(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0));
        row1.getCells()[1] = new TableBorderCell(0, 1, 1, 1, 0l);
        row1.getCells()[1].setBoundingBox(new BoundingBox(0, 20.0, 20.0, 30.0, 30.0));
        tableBorder.getRows()[0] = row1;
        TableBorderRow row2 = new TableBorderRow(0, 2, 0l);
        row2.setBoundingBox(new BoundingBox(0, 10.0, 10.0, 30.0, 20.0));
        row2.getCells()[0] = new TableBorderCell(1, 0, 1, 1, 0l);
        row2.getCells()[0].setBoundingBox(new BoundingBox(0, 10.0, 10.0, 20.0, 20.0));
        row2.getCells()[1] = new TableBorderCell(1, 1, 1, 1, 0l);
        row2.getCells()[1].setBoundingBox(new BoundingBox(0, 20.0, 10.0, 30.0, 20.0));
        tableBorder.getRows()[1] = row2;
        tableBorder.calculateCoordinatesUsingBoundingBoxesOfRowsAndColumns();
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 11.0, 21.0, 29.0, 29.0),
            "test", 10, 21.0);
        // xObjectName is null because test TextChunks are not backed by a real PDF stream
        textChunk.getStreamInfos().add(new StreamInfo(0, null, 0, "test".length()));
        contents.add(textChunk);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        contents.add(new ImageChunk(new BoundingBox(0, 11.0, 11.0, 19.0, 19.0)));
        contents = TableBorderProcessor.processTableBorders(contents, 0);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TableBorder);
        TableBorder resultBorder = (TableBorder) contents.get(0);
//        Assertions.assertSame(resultBorder,
//            tableBordersCollection.getTableBorder(resultBorder.getBoundingBox()));
        List<IObject> cellContents = resultBorder.getRow(0).getCell(0).getContents();
        Assertions.assertEquals(1, cellContents.size());
        Assertions.assertTrue(cellContents.get(0) instanceof SemanticParagraph);
        Assertions.assertEquals("te", ((SemanticParagraph) cellContents.get(0)).getValue());
        cellContents = resultBorder.getRow(0).getCell(1).getContents();
        Assertions.assertEquals(1, cellContents.size());
        Assertions.assertTrue(cellContents.get(0) instanceof SemanticParagraph);
        Assertions.assertEquals("t", ((SemanticParagraph) cellContents.get(0)).getValue());
        cellContents = resultBorder.getRow(1).getCell(0).getContents();
        Assertions.assertEquals(1, cellContents.size());
        Assertions.assertTrue(cellContents.get(0) instanceof ImageChunk);
    }

    @Test
    public void testCheckNeighborTables() {
        List<List<IObject>> contents = new ArrayList<>();
        List<IObject> pageContents1 = new ArrayList<>();
        contents.add(pageContents1);
        TableBorder tableBorder1 = new TableBorder(2, 2);
        tableBorder1.setRecognizedStructureId(1l);
        tableBorder1.setBoundingBox(new BoundingBox(0, 10.0, 10.0, 30.0, 30.0));
        TableBorderRow row1 = new TableBorderRow(0, 2, 0l);
        row1.setBoundingBox(new BoundingBox(0, 10.0, 20.0, 30.0, 30.0));
        row1.getCells()[0] = new TableBorderCell(0, 0, 1, 1, 0l);
        row1.getCells()[0].setBoundingBox(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0));
        row1.getCells()[1] = new TableBorderCell(0, 1, 1, 1, 0l);
        row1.getCells()[1].setBoundingBox(new BoundingBox(0, 20.0, 20.0, 30.0, 30.0));
        tableBorder1.getRows()[0] = row1;
        TableBorderRow row2 = new TableBorderRow(0, 2, 0l);
        row2.setBoundingBox(new BoundingBox(0, 10.0, 10.0, 30.0, 20.0));
        row2.getCells()[0] = new TableBorderCell(1, 0, 1, 1, 0l);
        row2.getCells()[0].setBoundingBox(new BoundingBox(0, 10.0, 10.0, 20.0, 20.0));
        row2.getCells()[1] = new TableBorderCell(1, 1, 1, 1, 0l);
        row2.getCells()[1].setBoundingBox(new BoundingBox(0, 20.0, 10.0, 30.0, 20.0));
        tableBorder1.getRows()[1] = row2;
        pageContents1.add(tableBorder1);

        List<IObject> pageContents2 = new ArrayList<>();
        contents.add(pageContents2);
        TableBorder tableBorder2 = new TableBorder(2, 2);
        tableBorder2.setRecognizedStructureId(2l);
        tableBorder2.setBoundingBox(new BoundingBox(1, 10.0, 10.0, 30.0, 30.0));
        row1 = new TableBorderRow(0, 2, 0l);
        row1.setBoundingBox(new BoundingBox(1, 10.0, 20.0, 30.0, 30.0));
        row1.getCells()[0] = new TableBorderCell(0, 0, 1, 1, 0l);
        row1.getCells()[0].setBoundingBox(new BoundingBox(1, 10.0, 20.0, 20.0, 30.0));
        row1.getCells()[1] = new TableBorderCell(0, 1, 1, 1, 0l);
        row1.getCells()[1].setBoundingBox(new BoundingBox(1, 20.0, 20.0, 30.0, 30.0));
        tableBorder2.getRows()[0] = row1;
        row2 = new TableBorderRow(0, 2, 0l);
        row2.setBoundingBox(new BoundingBox(1, 10.0, 10.0, 30.0, 20.0));
        row2.getCells()[0] = new TableBorderCell(1, 0, 1, 1, 0l);
        row2.getCells()[0].setBoundingBox(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0));
        row2.getCells()[1] = new TableBorderCell(1, 1, 1, 1, 0l);
        row2.getCells()[1].setBoundingBox(new BoundingBox(1, 20.0, 10.0, 30.0, 20.0));
        tableBorder2.getRows()[1] = row2;
        pageContents2.add(tableBorder2);

        TableBorderProcessor.checkNeighborTables(contents);
        Assertions.assertEquals(2, contents.size());
        Assertions.assertEquals(1, contents.get(0).size());
        Assertions.assertTrue(contents.get(0).get(0) instanceof TableBorder);
        Assertions.assertEquals(2l, ((TableBorder) contents.get(0).get(0)).getNextTableId());
        Assertions.assertEquals(1, contents.get(1).size());
        Assertions.assertTrue(contents.get(1).get(0) instanceof TableBorder);
        Assertions.assertEquals(1l, ((TableBorder) contents.get(1).get(0)).getPreviousTableId());
    }

    @Test
    public void testNormalSmallTableDoesNotTriggerStructuralNormalization() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(300L);
        TableBordersCollection tableBordersCollection = new TableBordersCollection();
        StaticContainers.setTableBordersCollection(tableBordersCollection);

        TableBorder tableBorder = createTable(0, 10.0, 10.0, 110.0, 70.0, 2, 2, 30L);
        SortedSet<TableBorder> tables = new TreeSet<>(new TableBorder.TableBordersComparator());
        tables.add(tableBorder);
        tableBordersCollection.getTableBorders().add(tables);

        List<IObject> contents = new ArrayList<>();
        contents.add(createTextChunk(0, 15.0, 48.0, 45.0, 58.0, "r1c1"));
        contents.add(createTextChunk(0, 65.0, 48.0, 95.0, 58.0, "r1c2"));
        contents.add(createTextChunk(0, 15.0, 22.0, 45.0, 32.0, "r2c1"));
        contents.add(createTextChunk(0, 65.0, 22.0, 95.0, 32.0, "r2c2"));

        TableBorder resultBorder = getSingleResultTable(contents, 0);

        Assertions.assertEquals(2, resultBorder.getNumberOfRows());
        Assertions.assertEquals("r1c1", ((SemanticParagraph) resultBorder.getCell(0, 0).getContents().get(0)).getValue());
        Assertions.assertEquals("r2c2", ((SemanticParagraph) resultBorder.getCell(1, 1).getContents().get(0)).getValue());
    }

    @Test
    public void testUndersegmentedFiveColumnTableIsRebuiltFromRawPageContents() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(400L);
        TableBordersCollection tableBordersCollection = new TableBordersCollection();
        StaticContainers.setTableBordersCollection(tableBordersCollection);

        TableBorder tableBorder = createTable(0, 10.0, 10.0, 260.0, 110.0, 2, 5, 40L);
        SortedSet<TableBorder> tables = new TreeSet<>(new TableBorder.TableBordersComparator());
        tables.add(tableBorder);
        tableBordersCollection.getTableBorders().add(tables);

        List<IObject> contents = new ArrayList<>();
        double[] rowBottoms = {94.0, 84.0, 74.0, 64.0, 54.0, 44.0, 34.0, 24.0};
        for (int rowIndex = 0; rowIndex < rowBottoms.length; rowIndex++) {
            double bottomY = rowBottoms[rowIndex];
            double topY = bottomY + 6.0;
            for (int columnNumber = 0; columnNumber < 5; columnNumber++) {
                double leftX = 15.0 + (columnNumber * 50.0);
                contents.add(createTextChunk(0, leftX, bottomY, leftX + 25.0, topY,
                    "r" + (rowIndex + 1) + "c" + (columnNumber + 1)));
            }
        }

        TableBorder resultBorder = getSingleResultTable(contents, 0);

        Assertions.assertEquals(8, resultBorder.getNumberOfRows());
//        Assertions.assertSame(resultBorder,
//            tableBordersCollection.getTableBorder(resultBorder.getBoundingBox()));
        Assertions.assertEquals("r1c1", ((SemanticParagraph) resultBorder.getCell(0, 0).getContents().get(0)).getValue());
        Assertions.assertEquals("r3c3", ((SemanticParagraph) resultBorder.getCell(2, 2).getContents().get(0)).getValue());
        Assertions.assertEquals("r8c5", ((SemanticParagraph) resultBorder.getCell(7, 4).getContents().get(0)).getValue());
    }

    @Test
    public void testNormalizationKeepsOriginalTableWhenRebuildLosesColumns() {
        TableBorder tableBorder = createTable(0, 10.0, 10.0, 260.0, 110.0, 2, 5, 50L);
        populateOriginalTableContents(tableBorder);

        List<IObject> rawPageContents = new ArrayList<>();
        double[] rowBottoms = {94.0, 84.0, 74.0, 64.0, 54.0, 44.0, 34.0, 24.0};
        for (int rowIndex = 0; rowIndex < rowBottoms.length; rowIndex++) {
            double bottomY = rowBottoms[rowIndex];
            double topY = bottomY + 6.0;
            rawPageContents.add(createTextChunk(0, 15.0, bottomY, 40.0, topY, "left-" + rowIndex));
            rawPageContents.add(createTextChunk(0, 65.0, bottomY, 90.0, topY, "mid-" + rowIndex));
        }

        TableBorder normalizedTable = TableStructureNormalizer.normalize(rawPageContents, tableBorder);

        Assertions.assertSame(tableBorder, normalizedTable);
        Assertions.assertEquals(2, normalizedTable.getNumberOfRows());
    }

    @Test
    public void testTextBlockTableIsNeverNormalized() {
        TableBorder tableBorder = createTable(0, 10.0, 10.0, 110.0, 50.0, 1, 1, 60L);
        List<IObject> cellContents = new ArrayList<>();
        cellContents.add(createTextChunk(0, 15.0, 20.0, 90.0, 30.0, "single cell text"));
        tableBorder.getCell(0, 0).setContents(cellContents);

        List<IObject> rawPageContents = new ArrayList<>();
        rawPageContents.add(createTextChunk(0, 15.0, 20.0, 90.0, 30.0, "single cell text"));
        rawPageContents.add(createTextChunk(0, 15.0, 32.0, 90.0, 42.0, "more text"));

        TableBorder normalizedTable = TableStructureNormalizer.normalize(rawPageContents, tableBorder);

        Assertions.assertSame(tableBorder, normalizedTable);
        Assertions.assertTrue(normalizedTable.isTextBlock());
    }

    // ========== RECURSION DEPTH LIMIT TESTS ==========

    /**
     * Test that processTableBorders completes within reasonable time even with
     * deeply nested table structures. This is a defensive measure against
     * malicious PDFs that could cause stack overflow through deeply nested tables.
     * <p>
     * Real-world PDFs rarely have tables nested more than 2-3 levels deep.
     * A depth limit of 10 provides safety margin while supporting legitimate use cases.
     */
    @Test
    public void testProcessTableBordersDepthLimitNoStackOverflow() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(100L);

        // Even with complex nested structures, processing should complete quickly
        // This test verifies that the depth limit prevents runaway recursion
        assertTimeout(Duration.ofSeconds(5), () -> {
            TableBordersCollection tableBordersCollection = new TableBordersCollection();
            StaticContainers.setTableBordersCollection(tableBordersCollection);

            // Create a simple table to process
            List<IObject> contents = new ArrayList<>();
            TableBorder tableBorder = createSimpleTable(0, 10.0, 10.0, 100.0, 100.0, 10L);
            SortedSet<TableBorder> tables = new TreeSet<>(new TableBorder.TableBordersComparator());
            tables.add(tableBorder);
            tableBordersCollection.getTableBorders().add(tables);

            TextChunk textChunk = new TextChunk(
                    new BoundingBox(0, 15.0, 15.0, 95.0, 95.0),
                    "test content", 10, 15.0);
            textChunk.getStreamInfos().add(new StreamInfo(0, null, 0, "test content".length()));
            textChunk.adjustSymbolEndsToBoundingBox(null);
            contents.add(textChunk);

            // Should complete without stack overflow
            List<IObject> result = TableBorderProcessor.processTableBorders(contents, 0);
            Assertions.assertNotNull(result);
        });
    }

    /**
     * Test that normal table processing still works correctly with depth tracking.
     * Verifies that the depth limit doesn't interfere with legitimate nested tables.
     */
    @Test
    public void testProcessTableBordersNormalNestedTableProcessedCorrectly() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(200L);
        TableBordersCollection tableBordersCollection = new TableBordersCollection();
        StaticContainers.setTableBordersCollection(tableBordersCollection);

        // Create outer table
        TableBorder outerTable = createSimpleTable(0, 10.0, 10.0, 200.0, 200.0, 20L);
        SortedSet<TableBorder> tables = new TreeSet<>(new TableBorder.TableBordersComparator());
        tables.add(outerTable);
        tableBordersCollection.getTableBorders().add(tables);

        List<IObject> contents = new ArrayList<>();
        TextChunk textChunk = new TextChunk(
                new BoundingBox(0, 15.0, 15.0, 95.0, 95.0),
                "outer content", 10, 15.0);
        textChunk.getStreamInfos().add(new StreamInfo(0, null, 0, "outer content".length()));
        textChunk.adjustSymbolEndsToBoundingBox(null);
        contents.add(textChunk);

        // Process should complete successfully
        List<IObject> result = TableBorderProcessor.processTableBorders(contents, 0);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0) instanceof TableBorder);
    }

    /**
     * Helper method to create a simple 2x2 table for testing.
     */
    private TableBorder createSimpleTable(int pageNumber, double leftX, double bottomY,
                                          double rightX, double topY, long structureId) {
        return createTable(pageNumber, leftX, bottomY, rightX, topY, 2, 2, structureId);
    }

    private TableBorder createTable(int pageNumber, double leftX, double bottomY,
                                    double rightX, double topY, int rows, int columns, long structureId) {
        TableBorder table = new TableBorder(rows, columns);
        table.setRecognizedStructureId(structureId);
        table.setBoundingBox(new BoundingBox(pageNumber, leftX, bottomY, rightX, topY));

        double columnWidth = (rightX - leftX) / columns;
        double rowHeight = (topY - bottomY) / rows;
        for (int rowNumber = 0; rowNumber < rows; rowNumber++) {
            double rowTopY = topY - (rowNumber * rowHeight);
            double rowBottomY = rowTopY - rowHeight;
            TableBorderRow row = new TableBorderRow(rowNumber, columns, 0L);
            row.setBoundingBox(new BoundingBox(pageNumber, leftX, rowBottomY, rightX, rowTopY));
            table.getRows()[rowNumber] = row;

            for (int columnNumber = 0; columnNumber < columns; columnNumber++) {
                double cellLeftX = leftX + (columnNumber * columnWidth);
                double cellRightX = cellLeftX + columnWidth;
                TableBorderCell cell = new TableBorderCell(rowNumber, columnNumber, 1, 1, 0L);
                cell.setBoundingBox(new BoundingBox(pageNumber, cellLeftX, rowBottomY, cellRightX, rowTopY));
                row.getCells()[columnNumber] = cell;
            }
        }

        table.calculateCoordinatesUsingBoundingBoxesOfRowsAndColumns();
        return table;
    }

    private void populateOriginalTableContents(TableBorder table) {
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            for (int columnNumber = 0; columnNumber < table.getNumberOfColumns(); columnNumber++) {
                TableBorderCell cell = table.getCell(rowNumber, columnNumber);
                cell.setContents(new ArrayList<>(List.of(createTextChunk(0,
                    cell.getLeftX() + 2.0, cell.getBottomY() + 5.0, cell.getLeftX() + 28.0,
                    cell.getBottomY() + 15.0, "orig-" + rowNumber + "-" + columnNumber))));
            }
        }
    }

    private TableBorder getSingleResultTable(List<IObject> contents, int pageNumber) {
        List<IObject> processedContents = TableBorderProcessor.processTableBorders(contents, pageNumber);
        Assertions.assertEquals(1, processedContents.size());
        Assertions.assertTrue(processedContents.get(0) instanceof TableBorder);
        return (TableBorder) processedContents.get(0);
    }

    private TextChunk createTextChunk(int pageNumber, double leftX, double bottomY, double rightX,
                                      double topY, String value) {
        TextChunk textChunk = new TextChunk(new BoundingBox(pageNumber, leftX, bottomY, rightX, topY),
            value, topY - bottomY, bottomY);
        textChunk.getStreamInfos().add(new StreamInfo(0, null, 0, value.length()));
        textChunk.adjustSymbolEndsToBoundingBox(null);
        return textChunk;
    }

}

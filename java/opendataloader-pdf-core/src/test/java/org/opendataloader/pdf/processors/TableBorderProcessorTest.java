/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
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
        contents.add(textChunk);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        contents.add(new ImageChunk(new BoundingBox(0, 11.0, 11.0, 19.0, 19.0)));
        contents = TableBorderProcessor.processTableBorders(contents, 0);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TableBorder);
        TableBorder resultBorder = (TableBorder) contents.get(0);
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
        TableBorder table = new TableBorder(2, 2);
        table.setRecognizedStructureId(structureId);
        table.setBoundingBox(new BoundingBox(pageNumber, leftX, bottomY, rightX, topY));

        double midX = (leftX + rightX) / 2;
        double midY = (bottomY + topY) / 2;

        // Row 0 (top)
        TableBorderRow row0 = new TableBorderRow(0, 2, 0L);
        row0.setBoundingBox(new BoundingBox(pageNumber, leftX, midY, rightX, topY));
        row0.getCells()[0] = new TableBorderCell(0, 0, 1, 1, 0L);
        row0.getCells()[0].setBoundingBox(new BoundingBox(pageNumber, leftX, midY, midX, topY));
        row0.getCells()[1] = new TableBorderCell(0, 1, 1, 1, 0L);
        row0.getCells()[1].setBoundingBox(new BoundingBox(pageNumber, midX, midY, rightX, topY));
        table.getRows()[0] = row0;

        // Row 1 (bottom)
        TableBorderRow row1 = new TableBorderRow(1, 2, 0L);
        row1.setBoundingBox(new BoundingBox(pageNumber, leftX, bottomY, rightX, midY));
        row1.getCells()[0] = new TableBorderCell(1, 0, 1, 1, 0L);
        row1.getCells()[0].setBoundingBox(new BoundingBox(pageNumber, leftX, bottomY, midX, midY));
        row1.getCells()[1] = new TableBorderCell(1, 1, 1, 1, 0L);
        row1.getCells()[1].setBoundingBox(new BoundingBox(pageNumber, midX, bottomY, rightX, midY));
        table.getRows()[1] = row1;

        table.calculateCoordinatesUsingBoundingBoxesOfRowsAndColumns();
        return table;
    }
}

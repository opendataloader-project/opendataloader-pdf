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

import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

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
}

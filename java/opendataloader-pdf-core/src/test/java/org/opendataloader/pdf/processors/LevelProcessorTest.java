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
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames;

import java.util.ArrayList;
import java.util.List;

public class LevelProcessorTest {

    @Test
    public void testDetectLevelsForParagraphs() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<List<IObject>> contents = new ArrayList<>();
        List<IObject> pageContents = new ArrayList<>();
        contents.add(pageContents);
        SemanticParagraph paragraph1 = new SemanticParagraph();
        pageContents.add(paragraph1);
        paragraph1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "- test", "Font1", 20, 700, 0, 30.0, new double[]{0.0},
            null, 0)));
        SemanticParagraph paragraph2 = new SemanticParagraph();
        pageContents.add(paragraph2);
        paragraph2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "+ test", "Font1", 10, 700, 0, 20.0, new double[]{0.5},
            null, 0)));
        LevelProcessor.detectLevels(contents);
        Assertions.assertEquals(2, contents.get(0).size());
        Assertions.assertEquals("1", contents.get(0).get(0).getLevel());
        Assertions.assertEquals("2", contents.get(0).get(1).getLevel());
    }

    @Test
    public void testDetectLevelsForLists() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<List<IObject>> contents = new ArrayList<>();
        List<IObject> pageContents = new ArrayList<>();
        contents.add(pageContents);
        PDFList list1 = new PDFList();
        list1.setNumberingStyle(NumberingStyleNames.ARABIC_NUMBERS);
        PDFList list2 = new PDFList();
        list2.setNumberingStyle(NumberingStyleNames.ARABIC_NUMBERS);
        PDFList list3 = new PDFList();
        list3.setNumberingStyle(NumberingStyleNames.UNORDERED);
        pageContents.add(list1);
        pageContents.add(list2);
        pageContents.add(list3);
        ListItem listItem1 = new ListItem(new BoundingBox(), 1l);
        listItem1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 70.0, 20.0, 80.0),
            "1. test", 10, 70.0)));
        list1.add(listItem1);
        ListItem listItem2 = new ListItem(new BoundingBox(), 2l);
        listItem2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 60.0, 20.0, 70.0),
            "2. test", 10, 60.0)));
        list1.add(listItem2);
        ListItem listItem3 = new ListItem(new BoundingBox(), 3l);
        listItem3.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 20.0, 60.0),
            "3. test", 10, 50.0)));
        list2.add(listItem3);
        ListItem listItem4 = new ListItem(new BoundingBox(), 4l);
        listItem4.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 20.0, 50.0),
            "4. test", 10, 40.0)));
        list1.add(listItem2);
        ListItem listItem5 = new ListItem(new BoundingBox(), 3l);
        listItem5.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "- test", 10, 30.0)));
        list3.add(listItem5);
        ListItem listItem6 = new ListItem(new BoundingBox(), 4l);
        listItem6.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "- test", 10, 20.0)));
        list3.add(listItem6);
        LevelProcessor.detectLevels(contents);
        Assertions.assertEquals(3, contents.get(0).size());
        Assertions.assertEquals("1", contents.get(0).get(0).getLevel());
        Assertions.assertEquals("1", contents.get(0).get(1).getLevel());
        Assertions.assertEquals("2", contents.get(0).get(2).getLevel());
    }

    @Test
    public void testDetectLevelsForTables() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
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
        row2.getCells()[0] = new TableBorderCell(0, 0, 1, 1, 0l);
        row2.getCells()[0].setBoundingBox(new BoundingBox(0, 10.0, 10.0, 20.0, 20.0));
        row2.getCells()[1] = new TableBorderCell(0, 1, 1, 1, 0l);
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
        LevelProcessor.detectLevels(contents);
        Assertions.assertEquals("1", contents.get(0).get(0).getLevel());
        Assertions.assertEquals("2", contents.get(1).get(0).getLevel());
    }
}

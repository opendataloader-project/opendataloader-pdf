/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.maps.AccumulatedNodeMapper;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class ListProcessorTest {

    @Test
    public void testProcessLists() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "1. test", 10, 30.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "2. test", 10, 20.0)));
        ListProcessor.processLists(contents, false);
        Assertions.assertEquals(1, contents.get(0).size());
        Assertions.assertTrue(contents.get(0).get(0) instanceof PDFList);
    }

    @Test
    public void testProcessListsFromTextNodes() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setAccumulatedNodeMapper(new AccumulatedNodeMapper());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph paragraph1 = new SemanticParagraph();
        contents.add(paragraph1);
        paragraph1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "1. test", 10, 30.0)));
        SemanticParagraph paragraph2 = new SemanticParagraph();
        contents.add(paragraph2);
        paragraph2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "2. test", 10, 20.0)));
        contents = ListProcessor.processListsFromTextNodes(contents);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof PDFList);
    }

    @Test
    public void testCheckNeighborLists() {
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        PDFList list1 = new PDFList();
        PDFList list2 = new PDFList();
        pageContents.add(list1);
        pageContents.add(list2);
        ListItem listItem1 = new ListItem(new BoundingBox(), 1l);
        listItem1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 20.0, 60.0),
            "1. test", 10, 50.0)));
        list1.add(listItem1);
        ListItem listItem2 = new ListItem(new BoundingBox(), 2l);
        listItem2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 20.0, 50.0),
            "2. test", 10, 40.0)));
        list1.add(listItem2);
        ListItem listItem3 = new ListItem(new BoundingBox(), 3l);
        listItem3.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "3. test", 10, 30.0)));
        list2.add(listItem3);
        ListItem listItem4 = new ListItem(new BoundingBox(), 4l);
        listItem4.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "4. test", 10, 20.0)));
        list2.add(listItem4);
        ListProcessor.checkNeighborLists(contents);
        contents.set(0, DocumentProcessor.removeNullObjectsFromList(contents.get(0)));
        Assertions.assertEquals(1, contents.size());
        Assertions.assertEquals(1, contents.get(0).size());
        Assertions.assertTrue(contents.get(0).get(0) instanceof PDFList);
        Assertions.assertEquals(4, ((PDFList) contents.get(0).get(0)).getNumberOfListItems());
    }

    @Test
    public void testProcessListsWithSingleCharacterLabels() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 20.0, 60.0),
            "1", 10, 50.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 20.0, 50.0),
            "가. 첫 번째 항목", 10, 40.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "나. 두 번째 항목", 10, 30.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            ")", 10, 20.0)));
        Assertions.assertDoesNotThrow(() -> ListProcessor.processLists(contents, false),
            "processLists should handle single character labels without throwing StringIndexOutOfBoundsException");
    }

    @Test
    public void testProcessListsWithEdgeCaseLabels() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> pageContents = new ArrayList<>();
        List<List<IObject>> contents = new ArrayList<>();
        contents.add(pageContents);
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 20.0, 60.0),
            "a", 10, 50.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 20.0, 50.0),
            "b", 10, 40.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "1)", 10, 30.0)));
        pageContents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "2)", 10, 20.0)));
        Assertions.assertDoesNotThrow(() -> ListProcessor.processLists(contents, false),
            "processLists should handle edge case labels without throwing StringIndexOutOfBoundsException");
    }
}

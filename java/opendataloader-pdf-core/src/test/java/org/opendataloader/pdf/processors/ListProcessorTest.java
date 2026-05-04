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
    public void testProcessListsFromSingleMultilineTextNode() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setAccumulatedNodeMapper(new AccumulatedNodeMapper());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph paragraph = new SemanticParagraph();
        contents.add(paragraph);
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 50.0, 120.0, 60.0),
            "1. Revenue from Operations", 10, 50.0)));
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 120.0, 50.0),
            "2. Other Income", 10, 40.0)));
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 120.0, 40.0),
            "3. Total Income", 10, 30.0)));
        contents = ListProcessor.processListsFromTextNodes(contents);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof PDFList);
        Assertions.assertEquals(3, ((PDFList) contents.get(0)).getNumberOfListItems());
    }

    @Test
    public void testProcessListsFromSingleMultilineTextNodeKeepsContinuationLines() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setAccumulatedNodeMapper(new AccumulatedNodeMapper());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph paragraph = new SemanticParagraph();
        contents.add(paragraph);
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 60.0, 160.0, 70.0),
            "1. Changes in Inventories", 10, 60.0)));
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, 24.0, 50.0, 160.0, 60.0),
            "Work In Progress", 10, 50.0)));
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 40.0, 160.0, 50.0),
            "2. Employee Benefits Expense", 10, 40.0)));
        contents = ListProcessor.processListsFromTextNodes(contents);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof PDFList);
        PDFList list = (PDFList) contents.get(0);
        Assertions.assertEquals(2, list.getNumberOfListItems());
        Assertions.assertEquals(2, list.getListItems().get(0).getLinesNumber());
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
        int originalSize = pageContents.size();
        ListProcessor.processLists(contents, false);
        Assertions.assertFalse(contents.get(0).isEmpty(),
            "Content should not be empty after processing");
        Assertions.assertTrue(contents.get(0).size() <= originalSize,
            "Content size should not exceed original size");
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
        int originalSize = pageContents.size();
        ListProcessor.processLists(contents, false);
        Assertions.assertFalse(contents.get(0).isEmpty(),
            "Content should not be empty after processing");
        Assertions.assertTrue(contents.get(0).size() <= originalSize,
            "Content size should not exceed original size");
    }
}

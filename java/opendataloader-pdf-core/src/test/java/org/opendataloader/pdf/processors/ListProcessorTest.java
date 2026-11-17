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
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.lists.TextListInterval;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemTextInfo;
import org.verapdf.wcag.algorithms.entities.maps.AccumulatedNodeMapper;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ListLabelsUtils;

import java.util.ArrayList;
import java.util.IdentityHashMap;
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

    /**
     * The list detection skips the comparison of two lines that cannot be items of one list. A skipped pair must be a
     * pair that the comparison does not join, for each value of its flags.
     */
    @Test
    public void testCanBeItemsOfOneListSkipsOnlyPairsThatDoNotJoin() {
        StaticContainers.setIsDataLoader(true);
        String[] lines = {"1. First", "2. Second", "3) Third", "10. Tenth", "11. Eleventh", "a) alpha", "b) beta",
            "A. Upper", "B. Upper", "aa. double", "bb. double", "i. roman", "ii. roman", "iv. roman", "I. INTRODUCTION",
            "II. METHOD", "V. RESULTS", "VI. CONCLUSION", "• bullet", "• next", "- dash", "* star",
            "o option", "가. 항목", "나. 항목", "① circled", "② circled",
            "(1) paren", "(2) paren", "[1] ref", "[2] ref", "Chapter 1", "Chapter 2", "The results show", "In this paper",
            "Data were", "model accuracy", "vector x", "x = 1", "1", "2", "a", "b", "I", "V", ")"};
        int skipped = 0;
        for (String previous : lines) {
            for (String current : lines) {
                TextListInterval interval = new TextListInterval();
                interval.getListItemsInfos().add(createInfo(0, previous));
                if (ListProcessor.canBeItemsOfOneList(interval, createInfo(1, current), new IdentityHashMap<>())) {
                    continue;
                }
                skipped++;
                for (boolean isSequential : new boolean[]{false, true}) {
                    for (boolean isUnordered : new boolean[]{false, true}) {
                        TextListInterval freshInterval = new TextListInterval();
                        freshInterval.getListItemsInfos().add(createInfo(0, previous));
                        Assertions.assertFalse(ListLabelsUtils.isTwoListItemsOfOneList(freshInterval,
                            createInfo(1, current), isSequential, isUnordered), previous + " -> " + current);
                    }
                }
            }
        }
        Assertions.assertTrue(skipped > 0);
        TextListInterval interval = new TextListInterval();
        interval.getListItemsInfos().add(createInfo(0, "1. First"));
        Assertions.assertTrue(ListProcessor.canBeItemsOfOneList(interval, createInfo(1, "2. Second"),
            new IdentityHashMap<>()));
    }

    private static ListItemTextInfo createInfo(int index, String value) {
        TextLine line = new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0 - 10 * index, 20.0,
            40.0 - 10 * index), value, 10, 30.0 - 10 * index));
        return new ListItemTextInfo(index, SemanticType.PARAGRAPH, line, value, true);
    }
}

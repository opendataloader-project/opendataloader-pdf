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
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class HeadingProcessorTest {

    @Test
    public void testProcessHeadings() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph paragraph1 = new SemanticParagraph();
        contents.add(paragraph1);
        paragraph1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "HEADING", "Font1", 20, 700, 0, 30.0, new double[]{0.0},
            null, 0)));
        SemanticParagraph paragraph2 = new SemanticParagraph();
        contents.add(paragraph2);
        paragraph2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "Paragraph", "Font1", 10, 700, 0, 20.0, new double[]{0.5},
            null, 0)));
        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertEquals(2, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof SemanticHeading);
    }

    @Test
    public void testDetectHeadingsLevels() {
        StaticContainers.setIsDataLoader(true);
        List<SemanticHeading> headings = new ArrayList<>();
        StaticLayoutContainers.setHeadings(headings);
        SemanticHeading heading1 = new SemanticHeading();
        headings.add(heading1);
        heading1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "HEADING", "Font1", 20, 700, 0, 30.0, new double[]{0.0},
            null, 0)));
        SemanticHeading heading2 = new SemanticHeading();
        headings.add(heading2);
        heading2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "Paragraph", "Font1", 10, 700, 0, 20.0, new double[]{0.5},
            null, 0)));
        HeadingProcessor.detectHeadingsLevels();
        Assertions.assertEquals(2, headings.size());
        Assertions.assertEquals(1, headings.get(0).getHeadingLevel());
        Assertions.assertEquals(2, headings.get(1).getHeadingLevel());
    }

    @Test
    public void testIeeeRomanSectionIsHeading() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();

        SemanticParagraph section = new SemanticParagraph();
        section.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "I. INTRODUCTION", "Font1", 10, 700, 0, 20.0, new double[]{0.0}, null, 0)));
        contents.add(section);

        SemanticParagraph body = new SemanticParagraph();
        body.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 10.0, 20.0, 20.0),
            "This paper introduces...", "Font1", 10, 400, 0, 12.0, new double[]{0.5}, null, 0)));
        contents.add(body);

        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertInstanceOf(SemanticHeading.class, contents.get(0),
            "I. INTRODUCTION should be classified as a heading");
    }

    @Test
    public void testConsecutiveRomanNumeralsAreList() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();

        SemanticParagraph item1 = new SemanticParagraph();
        item1.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "I. First item", "Font1", 10, 400, 0, 12.0, new double[]{0.0}, null, 0)));
        contents.add(item1);

        SemanticParagraph item2 = new SemanticParagraph();
        item2.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "II. Second item", "Font1", 10, 400, 0, 12.0, new double[]{0.0}, null, 0)));
        contents.add(item2);

        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertFalse(contents.get(0) instanceof SemanticHeading,
            "Consecutive roman numerals should stay as a list, not become a heading");
    }

    @Test
    public void testDropCapIsNotHeading() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();

        SemanticParagraph dropCap = new SemanticParagraph();
        dropCap.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "V", "Font1", 48, 400, 0, 50.0, new double[]{0.0}, null, 0)));
        contents.add(dropCap);

        SemanticParagraph body = new SemanticParagraph();
        body.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 10.0, 20.0, 20.0),
            "ISUAL Place Recognition serves as...", "Font1", 10, 400, 0, 12.0,
            new double[]{0.5}, null, 0)));
        contents.add(body);

        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertFalse(contents.get(0) instanceof SemanticHeading,
            "A single-letter drop cap should not be classified as a heading");
    }

    @Test
    public void testTwoCharacterHeadingIsPreserved() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();

        SemanticParagraph shortHeading = new SemanticParagraph();
        shortHeading.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "AI", "Font1", 16, 700, 0, 50.0, new double[]{0.0}, null, 0)));
        contents.add(shortHeading);

        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertTrue(contents.get(0) instanceof SemanticHeading,
            "A two-character standalone uppercase title like AI should be classified as a heading");
    }

    @Test
    public void testEquationIsNotHeading() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();

        SemanticParagraph eq = new SemanticParagraph();
        eq.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "Uj[t] = βUj[t − 1] + Ij[t] (2)", "Font1", 12, 700, 0, 30.0, new double[]{0.0}, null, 0)));
        contents.add(eq);

        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertFalse(contents.get(0) instanceof SemanticHeading,
            "An equation line should not be classified as a heading");
    }

    @Test
    public void testTableHeaderIsNotHeading() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();

        SemanticParagraph th = new SemanticParagraph();
        th.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "Strategy Acc. P@100R R@100P", "Font1", 10, 700, 0, 20.0, new double[]{0.0}, null, 0)));
        contents.add(th);

        HeadingProcessor.processHeadings(contents, false);
        Assertions.assertFalse(contents.get(0) instanceof SemanticHeading,
            "A table header row should not be classified as a heading");
    }
}

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
}

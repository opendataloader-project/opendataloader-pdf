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

    /**
     * Regression test for Issue #353: NullPointerException when hybrid backend
     * nodes lack color information.
     * 
     * Simulates a SemanticTextNode with null color fields (as returned by
     * Docling/Hancom hybrid backends) and ensures processHeadings() does not throw.
     */
    @Test
    public void testProcessHeadingsWithNullColorDoesNotThrow() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();
        
        // Create a paragraph with null color (simulates hybrid backend node)
        SemanticParagraph paragraph = new SemanticParagraph();
        contents.add(paragraph);
        
        // TextChunk with null color array - this triggers the NPE in
        // NodeUtils.headingProbability -> getTextColor -> calculateTextColor
        TextChunk textChunk = new TextChunk(
            new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "Test Heading",
            "Font1",
            20,      // fontSize
            700,     // fontWeight
            0,       // angle
            30.0,    // baseLine
            null,    // color - NULL to simulate hybrid backend
            null,    // fontWeightName
            0        // textType
        );
        paragraph.add(new TextLine(textChunk));
        
        // This should NOT throw NullPointerException
        // The fix catches NPE and uses a fallback probability
        Assertions.assertDoesNotThrow(() -> {
            HeadingProcessor.processHeadings(contents, false);
        }, "processHeadings should handle null color without throwing NPE");
        
        // Verify content is still processable
        Assertions.assertEquals(1, contents.size());
    }

    /**
     * Tests that multiple nodes with null color can be processed together.
     * This covers the case where prev/next nodes in headingProbability also have null color.
     */
    @Test
    public void testProcessHeadingsWithMultipleNullColorNodesDoesNotThrow() {
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setHeadings(new ArrayList<>());
        List<IObject> contents = new ArrayList<>();
        
        // Create multiple paragraphs with null color
        for (int i = 0; i < 3; i++) {
            SemanticParagraph paragraph = new SemanticParagraph();
            TextChunk textChunk = new TextChunk(
                new BoundingBox(0, 10.0 + i * 20, 30.0, 20.0, 40.0 + i * 10),
                "Paragraph " + i,
                "Font1",
                i == 0 ? 20 : 10,  // First one has larger font (potential heading)
                700,
                0,
                30.0,
                null,    // null color
                null,
                0
            );
            paragraph.add(new TextLine(textChunk));
            contents.add(paragraph);
        }
        
        // Should not throw when prev/next nodes also have null color
        Assertions.assertDoesNotThrow(() -> {
            HeadingProcessor.processHeadings(contents, false);
        }, "processHeadings should handle multiple nodes with null color");
        
        Assertions.assertEquals(3, contents.size());
    }
}

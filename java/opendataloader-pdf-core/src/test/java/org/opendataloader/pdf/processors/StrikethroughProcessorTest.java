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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class StrikethroughProcessorTest {

    @BeforeEach
    public void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setTableBordersCollection(null);
    }

    @Test
    public void testStrikethroughDetected() {
        List<IObject> contents = new ArrayList<>();

        // Text chunk: "apple" at y=[100, 120], x=[10, 60]
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        contents.add(textChunk);

        // Horizontal line through the center (y=110), matching the text width
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("~~apple~~", textChunk.getValue(),
            "Text chunk should be wrapped with strikethrough markers");
    }

    @Test
    public void testUnderlineNotDetectedAsStrikethrough() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        contents.add(textChunk);

        // Horizontal line near the bottom (y=101 — underline position)
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 101.0, 60.0, 101.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("apple", textChunk.getValue(),
            "Underline should not be detected as strikethrough");
    }

    @Test
    public void testLineAboveTextNotDetected() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        contents.add(textChunk);

        // Line above the text (y=130)
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 130.0, 60.0, 130.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("apple", textChunk.getValue(),
            "Line above text should not be detected as strikethrough");
    }

    @Test
    public void testPartialHorizontalOverlapNotDetected() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        contents.add(textChunk);

        // Line only covers half the text width: x=[10, 30]
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 30.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("apple", textChunk.getValue(),
            "Partial horizontal overlap should not be detected as strikethrough");
    }

    @Test
    public void testNoLinesNoChange() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "hello", 12, 100.0);
        contents.add(textChunk);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("hello", textChunk.getValue(),
            "Text should remain unchanged when no lines exist");
    }

    @Test
    public void testVerticalLineIgnored() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "hello", 12, 100.0);
        contents.add(textChunk);

        // Vertical line — should be ignored
        LineChunk line = LineChunk.createLineChunk(0, 35.0, 100.0, 35.0, 120.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("hello", textChunk.getValue(),
            "Vertical line should not trigger strikethrough");
    }

    @Test
    public void testDoubleWrappingPrevented() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "~~already~~", 12, 100.0);
        contents.add(textChunk);

        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("~~already~~", textChunk.getValue(),
            "Already wrapped text should not be double-wrapped");
    }

    @Test
    public void testWideLineSpanningMultipleChunksRejected() {
        List<IObject> contents = new ArrayList<>();

        // Two text chunks at different horizontal positions
        TextChunk chunk1 = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        TextChunk chunk2 = new TextChunk(new BoundingBox(0, 70.0, 100.0, 130.0, 120.0),
            "orange", 12, 100.0);
        contents.add(chunk1);
        contents.add(chunk2);

        // A wide line spanning both chunks — likely a table border or separator
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 130.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("apple", chunk1.getValue(),
            "Wide line matching multiple chunks should be rejected as structural separator");
        Assertions.assertEquals("orange", chunk2.getValue(),
            "Wide line matching multiple chunks should be rejected as structural separator");
    }

    @Test
    public void testLineMuchWiderThanTextRejected() {
        List<IObject> contents = new ArrayList<>();

        // Text chunk: x=[50, 80] (width=30)
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 50.0, 100.0, 80.0, 120.0),
            "hi", 12, 100.0);
        contents.add(textChunk);

        // Line: x=[10, 200] (width=190, much wider than text) — structural separator
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 200.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("hi", textChunk.getValue(),
            "Line much wider than text should be rejected as structural separator");
    }

    @Test
    public void testThickLineRejectedAsBackgroundFill() {
        List<IObject> contents = new ArrayList<>();

        // Text chunk: height = 120-100 = 20
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "hello", 12, 100.0);
        contents.add(textChunk);

        // Line with stroke=30.0 — thicker than text height (30/20 = 1.5 > 1.3)
        // This is a background fill or table cell shading, not a strikethrough
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 30.0,
            LineChunk.BUTT_CAP_STYLE);
        contents.add(line);

        StrikethroughProcessor.processStrikethroughs(contents);

        Assertions.assertEquals("hello", textChunk.getValue(),
            "Thick line (stroke > 1.3x text height) should be rejected");
    }

    @Test
    public void testThinLineAcceptedAsStrikethrough() {
        // Thin line (stroke=0.6, textHeight=20 → ratio=0.03) — typical strikethrough
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "test", 12, 100.0);
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 0.6,
            LineChunk.BUTT_CAP_STYLE);

        Assertions.assertTrue(StrikethroughProcessor.isStrikethroughLine(line, textChunk),
            "Thin line at center should be detected as strikethrough");
    }

    @Test
    public void testIsStrikethroughLineAtExactCenter() {
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "test", 12, 100.0);
        // Line exactly at center y=110, matching text width
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);

        Assertions.assertTrue(StrikethroughProcessor.isStrikethroughLine(line, textChunk),
            "Line at exact center should be detected as strikethrough");
    }
}

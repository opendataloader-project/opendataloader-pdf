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
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.LinesCollection;
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
        StaticContainers.setLinesCollection(new LinesCollection());
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
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertTrue(textChunk.getIsStrikethroughText(), "Text chunk should have isStrikethroughText set to true");
    }

    @Test
    public void testStrikethroughDetectedForSecondPage() {
        List<IObject> contents = new ArrayList<>();

        // Text chunk: "apple" at y=[100, 120], x=[10, 60]
        TextChunk textChunk = new TextChunk(new BoundingBox(1, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        contents.add(textChunk);

        // Horizontal line through the center (y=110), matching the text width
        LineChunk line = LineChunk.createLineChunk(1, 10.0, 110.0, 60.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        StaticContainers.getLinesCollection().getHorizontalLines(1).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 1);

        Assertions.assertTrue(textChunk.getIsStrikethroughText(), "Text chunk should have isStrikethroughText set to true");
    }

    @Test
    public void testLineFromDifferentPageIgnored() {
        List<IObject> contents = new ArrayList<>();
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        contents.add(textChunk);

        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        StaticContainers.getLinesCollection().getHorizontalLines(1).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(),
            "Line from another page must not affect current page processing");
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
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Underline should not be detected as strikethrough");
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
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Line above text should not be detected as strikethrough");
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
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Partial horizontal overlap should not be detected as strikethrough");
    }

    @Test
    public void testNoLinesNoChange() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "hello", 12, 100.0);
        contents.add(textChunk);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Text should remain unchanged when no lines exist");
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
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Vertical line should not trigger strikethrough");
    }

    @Test
    public void testWideLineSpanningMultipleChunksDetected() {
        List<IObject> contents = new ArrayList<>();

        // Two text chunks at different horizontal positions
        TextChunk chunk1 = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "apple", 12, 100.0);
        TextChunk chunk2 = new TextChunk(new BoundingBox(0, 70.0, 100.0, 130.0, 120.0),
            "orange", 12, 100.0);
        contents.add(chunk1);
        contents.add(chunk2);

        // A thin line spanning multiple chunks on one visual text line.
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 130.0, 110.0, 1.0,
            LineChunk.BUTT_CAP_STYLE);
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertTrue(chunk1.getIsStrikethroughText(),
            "Thin line matching multiple chunks should be detected");
        Assertions.assertTrue(chunk2.getIsStrikethroughText(),
            "Thin line matching multiple chunks should be detected");
    }

    @Test
    public void testWideLineArtSpanningMultipleChunksDetected() {
        List<IObject> contents = new ArrayList<>();

        TextChunk chunk1 = new TextChunk(new BoundingBox(0, 10.0, 100.0, 40.0, 120.0),
            "hello", 12, 100.0);
        TextChunk chunk2 = new TextChunk(new BoundingBox(0, 45.0, 100.0, 90.0, 120.0),
            "world", 12, 100.0);
        contents.add(chunk1);
        contents.add(chunk2);

        LineArtChunk lineArt = new LineArtChunk(new BoundingBox(0, 10.0, 109.5, 90.0, 110.5));
        contents.add(lineArt);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertTrue(chunk1.getIsStrikethroughText(),
            "Line-art strikethrough should support multiple chunks on one visual line");
        Assertions.assertTrue(chunk2.getIsStrikethroughText(),
            "Line-art strikethrough should support multiple chunks on one visual line");
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
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Line much wider than text should be rejected as structural separator");
    }

    @Test
    public void testThickLineRejectedAsBackgroundFill() {
        List<IObject> contents = new ArrayList<>();

        // Text chunk: height = 120-100 = 20
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "hello", 12, 100.0);
        contents.add(textChunk);

        // Line with stroke=30.0. This is a background fill or table cell shading,
        // not a strikethrough.
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 30.0,
            LineChunk.BUTT_CAP_STYLE);
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Thick line should be rejected");
    }

    @Test
    public void testTextHeightLineRejectedAsGlyphArtifact() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "hello", 12, 100.0);
        contents.add(textChunk);

        // Centered but text-height-ish rules are often extracted glyph/vector
        // artifacts, not strikethrough marks.
        LineChunk line = LineChunk.createLineChunk(0, 10.0, 110.0, 60.0, 110.0, 10.0,
            LineChunk.BUTT_CAP_STYLE);
        StaticContainers.getLinesCollection().getHorizontalLines(0).add(line);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Text-height line should be rejected");
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
    public void testNullLineInputsAreIgnored() {
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "test", 12, 100.0);

        Assertions.assertFalse(StrikethroughProcessor.isStrikethroughLine(null, textChunk),
            "Null LineChunk should not throw or match");
        Assertions.assertFalse(StrikethroughProcessor.isStrikethroughLineArt(null, textChunk),
            "Null LineArtChunk should not throw or match");
    }

    @Test
    public void testThinLineArtDetectedAsStrikethrough() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "test", 12, 100.0);
        contents.add(textChunk);

        // Thin horizontal rectangle through the text center.
        LineArtChunk lineArt = new LineArtChunk(new BoundingBox(0, 10.0, 109.5, 60.0, 110.5));
        contents.add(lineArt);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertTrue(textChunk.getIsStrikethroughText(), "Thin centered line art should trigger strikethrough");
    }

    @Test
    public void testWideLineArtHelperRejected() {
        TextChunk textChunk = new TextChunk(new BoundingBox(0, 50.0, 100.0, 80.0, 120.0),
            "hi", 12, 100.0);
        LineArtChunk lineArt = new LineArtChunk(new BoundingBox(0, 10.0, 109.5, 200.0, 110.5));

        Assertions.assertFalse(StrikethroughProcessor.isStrikethroughLineArt(lineArt, textChunk),
            "Direct line-art helper should reject rules much wider than the text");
    }

    @Test
    public void testUnderlineLineArtNotDetectedAsStrikethrough() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "test", 12, 100.0);
        contents.add(textChunk);

        // Thin horizontal rectangle near the text bottom.
        LineArtChunk lineArt = new LineArtChunk(new BoundingBox(0, 10.0, 100.5, 60.0, 101.5));
        contents.add(lineArt);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Underline-position line art should be ignored");
    }

    @Test
    public void testTallLineArtRejectedAsBackgroundFill() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 10.0, 100.0, 60.0, 120.0),
            "test", 12, 100.0);
        contents.add(textChunk);

        // Height=30, far thicker than a strikethrough rule.
        LineArtChunk lineArt = new LineArtChunk(new BoundingBox(0, 10.0, 95.0, 60.0, 125.0));
        contents.add(lineArt);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Tall line art should be rejected");
    }

    @Test
    public void testLargeBackgroundLineArtRejected() {
        List<IObject> contents = new ArrayList<>();

        TextChunk textChunk = new TextChunk(new BoundingBox(0, 50.0, 100.0, 80.0, 120.0),
            "hi", 12, 100.0);
        contents.add(textChunk);

        // Thin but much wider than the text, so it is likely a separator or background shape.
        LineArtChunk lineArt = new LineArtChunk(new BoundingBox(0, 10.0, 109.5, 200.0, 110.5));
        contents.add(lineArt);

        StrikethroughProcessor.processStrikethroughs(contents, 0);

        Assertions.assertFalse(textChunk.getIsStrikethroughText(), "Large background-like line art should be rejected");
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

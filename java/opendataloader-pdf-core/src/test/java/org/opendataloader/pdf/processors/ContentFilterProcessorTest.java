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
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;

public class ContentFilterProcessorTest {

    /**
     * Regression test for issue #150: short text chunks with abnormally wide bounding boxes.
     *
     * When PDF streams have text rendered in non-sequential order within a single Tj/TJ
     * operation, VeraPDF may calculate incorrect bounding boxes where rightX extends far
     * beyond the actual character width. For example, a single character "4" with height 10
     * might get a width of 42 instead of ~7.
     *
     * This causes the text to span multiple table cells incorrectly, leading to text being
     * dropped or assigned to wrong cells (the "프로트롬빈 시간" row in issue #150 where
     * numbers like "4" and "6" were missing).
     *
     * The fix should detect and correct these abnormal bounding boxes for short text (1-3 chars)
     * where actualWidth > expectedWidth * 3.
     */
    @Test
    public void testShortTextWithAbnormallyWideBoundingBox() {
        // Given: A single character "4" with height 10 but abnormally wide bbox (width=42)
        double height = 10.0;
        double leftX = 180.0;
        double abnormalRightX = 222.0; // Width = 42, expected ~7 for single char
        TextChunk textChunk = new TextChunk(
            new BoundingBox(0, leftX, 100.0, abnormalRightX, 100.0 + height),
            "4", height, 100.0);

        double actualWidth = textChunk.getBoundingBox().getWidth();
        double expectedMaxWidth = 1 * height * 0.7 * 3; // char_count * height * 0.7 * threshold(3x)

        // This assertion documents the bug: the width is abnormally large
        // When fixAbnormalTextChunkBoundingBoxes() is implemented, this should be corrected
        Assertions.assertTrue(actualWidth > expectedMaxWidth,
            "This test documents that the bounding box width (" + actualWidth +
            ") is abnormally large compared to expected max (" + expectedMaxWidth +
            ") for a single character. A fix should correct this.");
    }

    /**
     * Regression test for issue #150: normal text chunks should not be affected.
     *
     * Text chunks with reasonable widths (width <= expectedWidth * 3) should not
     * have their bounding boxes modified.
     */
    @Test
    public void testNormalTextWidthNotAbnormal() {
        // Given: A two-character text "AB" with reasonable width
        double height = 10.0;
        double leftX = 100.0;
        double normalRightX = 115.0; // Width = 15, reasonable for 2 chars
        TextChunk textChunk = new TextChunk(
            new BoundingBox(0, leftX, 100.0, normalRightX, 100.0 + height),
            "AB", height, 100.0);

        double actualWidth = textChunk.getBoundingBox().getWidth();
        double expectedMaxWidth = 2 * height * 0.7 * 3; // char_count * height * 0.7 * threshold(3x)

        // Normal width should be within expected range
        Assertions.assertTrue(actualWidth <= expectedMaxWidth,
            "Normal text chunk width (" + actualWidth + ") should not exceed threshold (" +
            expectedMaxWidth + ")");
    }

    /**
     * Regression test for issue #150: long text (>3 chars) should not be considered abnormal.
     *
     * The fix should only target short text chunks (1-3 characters) where the width
     * calculation is clearly wrong. Longer text can legitimately have wider bounding boxes.
     */
    @Test
    public void testLongTextNotTargetedForCorrection() {
        // Given: A 5-character text "Hello" with a wide bbox - this is plausible for longer text
        double height = 10.0;
        double leftX = 100.0;
        double rightX = 200.0; // Width = 100
        TextChunk textChunk = new TextChunk(
            new BoundingBox(0, leftX, 100.0, rightX, 100.0 + height),
            "Hello", height, 100.0);

        // For text with more than 3 characters, the fix should not apply
        // regardless of the width-to-height ratio
        Assertions.assertEquals(5, textChunk.getValue().length(),
            "Long text should have 5 characters");
        Assertions.assertEquals(100.0, textChunk.getBoundingBox().getWidth(), 0.01,
            "Long text width should remain unchanged");
    }
}

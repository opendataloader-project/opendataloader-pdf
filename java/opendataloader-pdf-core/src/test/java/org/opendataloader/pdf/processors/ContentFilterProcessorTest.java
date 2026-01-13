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
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class ContentFilterProcessorTest {

    @Test
    public void testFixAbnormalTextChunkBoundingBoxes_shortTextWithAbnormalWidth() throws Exception {
        // Given: A short text chunk (1 char) with abnormally wide bounding box
        // This simulates the bug where '4' has width ~38 instead of expected ~7
        List<IObject> contents = new ArrayList<>();
        double height = 10.0;
        double leftX = 180.0;
        double abnormalRightX = 222.0; // Much wider than expected for single char
        contents.add(new TextChunk(
            new BoundingBox(0, leftX, 100.0, abnormalRightX, 100.0 + height),
            "4", height, height));

        // When: Fix abnormal bounding boxes
        Method method = ContentFilterProcessor.class.getDeclaredMethod(
            "fixAbnormalTextChunkBoundingBoxes", List.class);
        method.setAccessible(true);
        method.invoke(null, contents);

        // Then: Width should be corrected
        TextChunk fixed = (TextChunk) contents.get(0);
        double fixedWidth = fixed.getBoundingBox().getWidth();
        double expectedWidth = 1 * height * 0.7; // char_count * height * 0.7

        // Fixed width should be close to expected (much smaller than original)
        Assertions.assertTrue(fixedWidth < 15,
            "Width should be corrected to reasonable size, got: " + fixedWidth);
        Assertions.assertEquals(leftX, fixed.getBoundingBox().getLeftX(),
            "LeftX should remain unchanged");
    }

    @Test
    public void testFixAbnormalTextChunkBoundingBoxes_normalWidthNotChanged() throws Exception {
        // Given: A normal text chunk with reasonable width
        List<IObject> contents = new ArrayList<>();
        double height = 10.0;
        double leftX = 100.0;
        double normalRightX = 115.0; // Reasonable width for 2 chars
        contents.add(new TextChunk(
            new BoundingBox(0, leftX, 100.0, normalRightX, 100.0 + height),
            "AB", height, height));

        double originalWidth = normalRightX - leftX;

        // When: Fix abnormal bounding boxes
        Method method = ContentFilterProcessor.class.getDeclaredMethod(
            "fixAbnormalTextChunkBoundingBoxes", List.class);
        method.setAccessible(true);
        method.invoke(null, contents);

        // Then: Width should remain unchanged
        TextChunk chunk = (TextChunk) contents.get(0);
        Assertions.assertEquals(originalWidth, chunk.getBoundingBox().getWidth(), 0.01,
            "Normal width should not be changed");
    }

    @Test
    public void testFixAbnormalTextChunkBoundingBoxes_longTextNotChanged() throws Exception {
        // Given: A long text chunk (more than 3 chars) - should not be fixed even if wide
        List<IObject> contents = new ArrayList<>();
        double height = 10.0;
        double leftX = 100.0;
        double rightX = 200.0; // Wide but acceptable for longer text
        contents.add(new TextChunk(
            new BoundingBox(0, leftX, 100.0, rightX, 100.0 + height),
            "Hello", height, height));

        double originalWidth = rightX - leftX;

        // When: Fix abnormal bounding boxes
        Method method = ContentFilterProcessor.class.getDeclaredMethod(
            "fixAbnormalTextChunkBoundingBoxes", List.class);
        method.setAccessible(true);
        method.invoke(null, contents);

        // Then: Width should remain unchanged (long text is not processed)
        TextChunk chunk = (TextChunk) contents.get(0);
        Assertions.assertEquals(originalWidth, chunk.getBoundingBox().getWidth(), 0.01,
            "Long text width should not be changed");
    }
}

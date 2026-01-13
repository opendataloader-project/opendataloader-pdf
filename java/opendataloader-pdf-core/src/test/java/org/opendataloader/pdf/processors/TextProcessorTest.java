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
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class TextProcessorTest {

    @Test
    public void testRemoveSameTextChunks() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "test", 10, 10.0));
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "test", 10, 10.0));
        TextProcessor.removeSameTextChunks(contents);
        contents = DocumentProcessor.removeNullObjectsFromList(contents);
        Assertions.assertEquals(1, contents.size());
    }

    @Test
    public void testRemoveTextDecorationImages() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "test", 10, 10.0));
        contents.add(new ImageChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0)));
        TextProcessor.removeTextDecorationImages(contents);
        contents = DocumentProcessor.removeNullObjectsFromList(contents);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TextChunk);
    }

    /**
     * Tests that areNeighborsTextChunks returns false when physical leftX gap is too large,
     * even if textEnd/textStart positions are close (simulating non-sequential PDF stream order).
     *
     * Note: TextChunk's setTextStart() also modifies the bounding box leftX, so we need to
     * set textStart/End first, then manually update the bounding box to simulate the bug scenario.
     */
    @Test
    public void testAreNeighborsTextChunksRejectsDistantChunks() throws Exception {
        double height = 10.0;
        double fontSize = 10.0;

        // Chunk 1: single char "4" - textEnd=107 means PDF stream position 107
        TextChunk first = new TextChunk(
            new BoundingBox(0, 183.0, 100.0, 190.0, 100.0 + height),
            "4", fontSize, height);
        // setTextStart/End will change bbox, so set them to values close to each other
        // then we manually fix the bounding box
        first.setTextStart(100.0);
        first.setTextEnd(107.0);
        // Override bounding box to physical position x=183
        first.setBoundingBox(new BoundingBox(0, 183.0, 100.0, 190.0, 100.0 + height));

        // Chunk 2: single char "4" at physical position x=222 (different table cell)
        // textStart=107.5 is close to first's textEnd=107 (PDF stream proximity)
        TextChunk second = new TextChunk(
            new BoundingBox(0, 222.0, 100.0, 229.0, 100.0 + height),
            "4", fontSize, height);
        second.setTextStart(107.5);
        second.setTextEnd(114.0);
        // Override bounding box to physical position x=222
        second.setBoundingBox(new BoundingBox(0, 222.0, 100.0, 229.0, 100.0 + height));

        // Get private method via reflection
        Method method = TextProcessor.class.getDeclaredMethod(
            "areNeighborsTextChunks", TextChunk.class, TextChunk.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(null, first, second);

        // Physical gap = 222 - 183 = 39
        // Expected max gap for single char = 1 * 10 * 0.6 + 10 * 2 = 26
        // Since 39 > 26, should return false
        Assertions.assertFalse(result,
            "Should reject chunks with leftX gap (39) exceeding max allowed (26)");
    }

    /**
     * Tests that areNeighborsTextChunks returns true for physically adjacent chunks.
     * Note: setBoundingBox() also updates textStart/textEnd, so we use bbox values
     * that result in textEnd and textStart being close.
     */
    @Test
    public void testAreNeighborsTextChunksAcceptsAdjacentChunks() throws Exception {
        double height = 10.0;
        double fontSize = 10.0;

        // Chunk 1: "Hello" at x=100, rightX=130, so textEnd will be 130
        TextChunk first = new TextChunk(
            new BoundingBox(0, 100.0, 100.0, 130.0, 100.0 + height),
            "Hello", fontSize, height);

        // Chunk 2: "World" at x=130.5 (physically close, 0.5px gap from first's rightX)
        // This makes textStart=130.5, which is close to first's textEnd=130
        TextChunk second = new TextChunk(
            new BoundingBox(0, 130.5, 100.0, 160.5, 100.0 + height),
            "World", fontSize, height);

        Method method = TextProcessor.class.getDeclaredMethod(
            "areNeighborsTextChunks", TextChunk.class, TextChunk.class);
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(null, first, second);

        // textEnd=130, textStart=130.5, diff=0.5 < epsilon(1.0) => textPositionsClose=true
        // Physical gap = 130.5 - 100 = 30.5
        // Expected max gap for "Hello" = 5 * 10 * 0.6 + 10 * 2 = 50
        // Since 30.5 < 50, should return true
        Assertions.assertTrue(result,
            "Should accept chunks with leftX gap (30.5) within max allowed (50)");
    }
}

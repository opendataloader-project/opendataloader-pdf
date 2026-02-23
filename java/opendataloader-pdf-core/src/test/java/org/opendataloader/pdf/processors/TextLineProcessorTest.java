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
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class TextLineProcessorTest {

    @Test
    public void testProcessTextLines() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "test", 10, 30.0));
        contents.add(new TextChunk(new BoundingBox(0, 20.0, 30.0, 30.0, 40.0),
            "test", 10, 30.0));
        contents.add(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "test", 10, 20.0));
        contents = TextLineProcessor.processTextLines(contents);
        Assertions.assertEquals(2, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TextLine);
        Assertions.assertEquals("testtest", ((TextLine) contents.get(0)).getValue());
        Assertions.assertTrue(contents.get(1) instanceof TextLine);
        Assertions.assertEquals("test", ((TextLine) contents.get(1)).getValue());
    }

    /**
     * Regression test for issue #150: text chunks on the same line should be sorted by leftX.
     *
     * When PDF streams render text in non-sequential order (e.g., "A:" content appears
     * after "Q:" content in the stream but should appear before it visually),
     * TextLineProcessor should sort chunks by leftX to produce correct reading order.
     */
    @Test
    public void testProcessTextLinesSortsChunksByLeftX() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> contents = new ArrayList<>();

        // Simulate chunks arriving in wrong stream order but on the same line.
        // In the PDF stream, "content" appears first, then "Q:" appears second,
        // but "Q:" is physically to the left of "content".
        TextChunk contentChunk = new TextChunk(new BoundingBox(0, 100.0, 300.0, 200.0, 310.0),
            "content", 10, 300.0);
        TextChunk labelChunk = new TextChunk(new BoundingBox(0, 10.0, 300.0, 40.0, 310.0),
            "Q:", 10, 300.0);

        // Add in wrong order (as they might appear in PDF stream)
        contents.add(contentChunk);
        contents.add(labelChunk);

        contents = TextLineProcessor.processTextLines(contents);

        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TextLine);

        TextLine textLine = (TextLine) contents.get(0);
        // After sorting by leftX, "Q:" (at x=10) should come before "content" (at x=100)
        Assertions.assertTrue(textLine.getValue().startsWith("Q:"),
            "Text line should start with 'Q:' (leftmost chunk), but got: " + textLine.getValue());
    }

    /**
     * Regression test for issue #150: spaces should be inserted between sorted chunks
     * when there is a physical gap between them.
     */
    @Test
    public void testProcessTextLinesAddsSpacesBetweenDistantChunks() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> contents = new ArrayList<>();

        // Two chunks on the same line with a significant gap between them
        TextChunk chunk1 = new TextChunk(new BoundingBox(0, 10.0, 300.0, 30.0, 310.0),
            "A:", 10, 300.0);
        TextChunk chunk2 = new TextChunk(new BoundingBox(0, 50.0, 300.0, 150.0, 310.0),
            "answer text", 10, 300.0);

        contents.add(chunk1);
        contents.add(chunk2);

        contents = TextLineProcessor.processTextLines(contents);

        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TextLine);

        TextLine textLine = (TextLine) contents.get(0);
        // There should be a space between "A:" and "answer text" due to the gap
        Assertions.assertTrue(textLine.getValue().contains("A:") && textLine.getValue().contains("answer text"),
            "Both chunks should be present in the text line: " + textLine.getValue());
        Assertions.assertNotEquals("A:answer text", textLine.getValue(),
            "There should be a space between chunks when there is a physical gap");
    }

}

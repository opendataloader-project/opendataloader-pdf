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

import java.util.ArrayList;
import java.util.List;

public class TextProcessorTest {

    @Test
    public void testReplaceUndefinedCharacters() {
        // Simulate backend results containing U+FFFD (replacement character)
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
            "Hello \uFFFD World", 10, 10.0));
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 30.0, 100.0, 40.0),
            "No issues here", 10, 10.0));

        TextProcessor.replaceUndefinedCharacters(contents, "?");

        Assertions.assertEquals("Hello ? World", ((TextChunk) contents.get(0)).getValue());
        Assertions.assertEquals("No issues here", ((TextChunk) contents.get(1)).getValue());
    }

    @Test
    public void testReplaceUndefinedCharactersSkipsWhenDefault() {
        // When replacement string equals REPLACEMENT_CHARACTER_STRING, should be a no-op
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
            "Hello \uFFFD World", 10, 10.0));

        TextProcessor.replaceUndefinedCharacters(contents, "\uFFFD");

        // Should remain unchanged
        Assertions.assertEquals("Hello \uFFFD World", ((TextChunk) contents.get(0)).getValue());
    }

    @Test
    public void testReplaceUndefinedCharactersMultipleOccurrences() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
            "\uFFFD first \uFFFD second \uFFFD", 10, 10.0));

        TextProcessor.replaceUndefinedCharacters(contents, "*");

        Assertions.assertEquals("* first * second *", ((TextChunk) contents.get(0)).getValue());
    }

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
}

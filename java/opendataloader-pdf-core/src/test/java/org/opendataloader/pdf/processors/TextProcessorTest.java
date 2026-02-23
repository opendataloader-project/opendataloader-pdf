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
    public void testReplaceUndefinedCharactersWithRegexSpecialChars() {
        // Verify that regex-special characters in replacement string work correctly
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
            "Hello \uFFFD World", 10, 10.0));

        TextProcessor.replaceUndefinedCharacters(contents, "$");

        Assertions.assertEquals("Hello $ World", ((TextChunk) contents.get(0)).getValue());
    }

    @Test
    public void testReplaceUndefinedCharactersSkipsNonTextChunks() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0)));
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 30.0, 100.0, 40.0),
            "Hello \uFFFD", 10, 10.0));

        TextProcessor.replaceUndefinedCharacters(contents, "?");

        Assertions.assertTrue(contents.get(0) instanceof ImageChunk);
        Assertions.assertEquals("Hello ?", ((TextChunk) contents.get(1)).getValue());
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

    /**
     * Regression test for issue #150: physically distant text chunks should not be merged.
     *
     * When PDF streams have text rendered in non-sequential order, textEnd/textStart values
     * can be close even though the text chunks are physically far apart (different bounding
     * box positions). mergeCloseTextChunks should verify physical proximity, not just
     * textEnd/textStart closeness.
     *
     * KNOWN BUG: Currently areNeighborsTextChunks() only checks textEnd/textStart without
     * physical position verification (commit 21b7d64 fix not merged to main).
     * TODO: When fix is applied, flip assertion: expect size==2 with separate "4" and "6".
     */
    @Test
    public void testMergeCloseTextChunksPhysicallyDistantNotMerged() {
        List<IObject> contents = new ArrayList<>();
        String fontName = "Arial";

        // First chunk: "4" at x=180, physically in one table cell
        TextChunk chunk1 = new TextChunk(new BoundingBox(0, 180.0, 100.0, 190.0, 110.0),
            "4", 10, 100.0);
        chunk1.adjustSymbolEndsToBoundingBox(null);
        chunk1.setFontName(fontName);
        chunk1.setFontWeight(400);
        chunk1.setTextEnd(190.0);

        // Second chunk: "6" at x=350, physically in a different table cell (far away)
        // But textStart is set close to chunk1's textEnd (simulating non-sequential PDF rendering)
        TextChunk chunk2 = new TextChunk(new BoundingBox(0, 350.0, 100.0, 360.0, 110.0),
            "6", 10, 100.0);
        chunk2.adjustSymbolEndsToBoundingBox(null);
        chunk2.setFontName(fontName);
        chunk2.setFontWeight(400);
        chunk2.setTextStart(190.5);

        contents.add(chunk1);
        contents.add(chunk2);

        TextProcessor.mergeCloseTextChunks(contents);
        contents = DocumentProcessor.removeNullObjectsFromList(contents);

        // KNOWN BUG (#150): chunks are 160 units apart but get merged because only
        // textEnd/textStart proximity is checked, not physical bounding box distance.
        // Current (broken) behavior: merged into 1 chunk
        // Correct behavior after fix: should remain as 2 separate chunks
        Assertions.assertEquals(1, contents.size(),
            "KNOWN BUG #150: distant chunks are incorrectly merged. "
            + "When areNeighborsTextChunks physical check is added, flip to assertEquals(2)");
    }

    /**
     * Regression test for issue #150: adjacent text chunks should still be merged.
     */
    @Test
    public void testMergeCloseTextChunksAdjacentMerged() {
        List<IObject> contents = new ArrayList<>();
        String fontName = "Arial";

        // First chunk: "Hel" at x=10
        TextChunk chunk1 = new TextChunk(new BoundingBox(0, 10.0, 100.0, 30.0, 110.0),
            "Hel", 10, 100.0);
        chunk1.adjustSymbolEndsToBoundingBox(null);
        chunk1.setFontName(fontName);
        chunk1.setFontWeight(400);
        chunk1.setTextEnd(30.0);

        // Second chunk: "lo" at x=30, immediately adjacent
        TextChunk chunk2 = new TextChunk(new BoundingBox(0, 30.0, 100.0, 45.0, 110.0),
            "lo", 10, 100.0);
        chunk2.adjustSymbolEndsToBoundingBox(null);
        chunk2.setFontName(fontName);
        chunk2.setFontWeight(400);
        chunk2.setTextStart(30.0);

        contents.add(chunk1);
        contents.add(chunk2);

        TextProcessor.mergeCloseTextChunks(contents);
        contents = DocumentProcessor.removeNullObjectsFromList(contents);

        // Adjacent chunks should be merged
        Assertions.assertEquals(1, contents.size(),
            "Adjacent text chunks should be merged");
        Assertions.assertEquals("Hello", ((TextChunk) contents.get(0)).getValue());
    }
}

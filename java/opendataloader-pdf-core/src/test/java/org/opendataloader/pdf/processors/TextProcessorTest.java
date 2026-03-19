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
     * Regression test for issue #150: text chunks with a large horizontal gap
     * should remain separate.
     */
    @Test
    public void testMergeCloseTextChunksSeparatedByLargeGapNotMerged() {
        List<IObject> contents = new ArrayList<>();
        String fontName = "Arial";

        // First chunk: "4" at x=180, physically in one table cell
        TextChunk chunk1 = new TextChunk(new BoundingBox(0, 180.0, 100.0, 190.0, 110.0),
            "4", 10, 100.0);
        chunk1.adjustSymbolEndsToBoundingBox(null);
        chunk1.setFontName(fontName);
        chunk1.setFontWeight(400);

        // Second chunk: "6" at x=350, physically in a different table cell
        TextChunk chunk2 = new TextChunk(new BoundingBox(0, 350.0, 100.0, 360.0, 110.0),
            "6", 10, 100.0);
        chunk2.adjustSymbolEndsToBoundingBox(null);
        chunk2.setFontName(fontName);
        chunk2.setFontWeight(400);

        contents.add(chunk1);
        contents.add(chunk2);

        TextProcessor.mergeCloseTextChunks(contents);
        contents = DocumentProcessor.removeNullObjectsFromList(contents);

        Assertions.assertEquals(2, contents.size(),
            "Text chunks separated by a large gap should not be merged");
        Assertions.assertEquals("4", ((TextChunk) contents.get(0)).getValue());
        Assertions.assertEquals("6", ((TextChunk) contents.get(1)).getValue());
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

    @Test
    public void testMeasureReplacementCharRatioAllReplacement() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
            "\uFFFD\uFFFD\uFFFD", 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertEquals(1.0, ratio, 0.001);
    }

    @Test
    public void testMeasureReplacementCharRatioNoReplacement() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
            "Hello World", 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertEquals(0.0, ratio, 0.001);
    }

    @Test
    public void testMeasureReplacementCharRatioMixed() {
        List<IObject> contents = new ArrayList<>();
        // 3 replacement chars out of 10 total = 0.3
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0),
            "\uFFFD\uFFFD\uFFFDAbcdefg", 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertEquals(0.3, ratio, 0.001);
    }

    @Test
    public void testMeasureReplacementCharRatioEmptyContents() {
        List<IObject> contents = new ArrayList<>();

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertEquals(0.0, ratio, 0.001);
    }

    @Test
    public void testMeasureReplacementCharRatioNonTextChunksIgnored() {
        List<IObject> contents = new ArrayList<>();
        contents.add(new ImageChunk(new BoundingBox(1, 10.0, 10.0, 100.0, 20.0)));
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 30.0, 100.0, 40.0),
            "\uFFFD\uFFFD\uFFFD\uFFFD\uFFFD", 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        // Only TextChunks counted: 5/5 = 1.0
        Assertions.assertEquals(1.0, ratio, 0.001);
    }
}

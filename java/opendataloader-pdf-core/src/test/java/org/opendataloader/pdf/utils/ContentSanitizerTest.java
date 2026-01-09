package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.FilterConfig;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContentSanitizerTest {
    private ContentSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        FilterConfig filterConfig = new FilterConfig();
        sanitizer = new ContentSanitizer(filterConfig.getFilterRules());
    }

    TextChunk createTextChunk(String value, double left, double bottom, double right, double top) {
        TextChunk chunk = new TextChunk(new BoundingBox(left, bottom, right, top), value,10, 10);
        chunk.adjustSymbolEndsToBoundingBox(null);
        return chunk;
    }

    private void assertChunksContainValues(List<TextChunk> chunks, String... expectedValues) {
        assertEquals(expectedValues.length, chunks.size(),
            "Wrong number of chunks");

        for (int i = 0; i < expectedValues.length; i++) {
            assertEquals(expectedValues[i], chunks.get(i).getValue(),
                "Chunk " + i + " contains wrong value");
        }
    }

    @Test
    void testMultipleReplacementsInSingleChunk() {
        TextChunk chunk = createTextChunk(
            "Email: test@gmail.com, IP: 192.168.1.1", 0f, 40f, 100f, 20f);
        List<TextChunk> originalChunks = Collections.singletonList(chunk);

        List<ContentSanitizer.ReplacementInfo> replacements = sanitizer.findAllReplacements(chunk.getValue());
        List<TextChunk> result = sanitizer.applyReplacementsToChunks(
            originalChunks, replacements);

        assertChunksContainValues(result, "Email: ", "email@example.com", ", IP: ", "0.0.0.0");
    }

    @Test
    void testReplaceCoveringMultipleFullChunks() {
        List<TextChunk> originalChunks = new ArrayList<>();
        originalChunks.add(createTextChunk("User: ", 0f, 60f, 10f, 20f));
        originalChunks.add(createTextChunk("john", 60f, 60f, 100f, 20f));
        originalChunks.add(createTextChunk(".doe@", 100f, 60f, 140f, 20f));
        originalChunks.add(createTextChunk("example.com", 140f, 60f, 220f, 20f));
        TextLine line = new TextLine();
        for (TextChunk chunk : originalChunks) {
            line.add(chunk);
        }

        List<ContentSanitizer.ReplacementInfo> replacements = sanitizer.findAllReplacements(line.getValue());

        List<TextChunk> result = sanitizer.applyReplacementsToChunks(
            originalChunks, replacements);

        assertChunksContainValues(result, "User: ", "email@example.com");
    }

    @Test
    void testReplaceCoveringPartsOfChunks() {
        List<TextChunk> originalChunks = new ArrayList<>();
        originalChunks.add(createTextChunk("User: ", 0f, 60f, 10f, 20f));
        originalChunks.add(createTextChunk("john", 60f, 60f, 100f, 20f));
        originalChunks.add(createTextChunk(".doe@", 100f, 60f, 140f, 20f));
        originalChunks.add(createTextChunk("example.com. Hi!", 140f, 60f, 250f, 20f));
        TextLine line = new TextLine();
        for (TextChunk chunk : originalChunks) {
            line.add(chunk);
        }

        List<ContentSanitizer.ReplacementInfo> replacements = sanitizer.findAllReplacements(line.getValue());

        List<TextChunk> result = sanitizer.applyReplacementsToChunks(
            originalChunks, replacements);

        assertChunksContainValues(result, "User: ", "email@example.com", ". Hi!");
    }

    @Test
    void testReplaceCoveringOneFullChunkInArray() {
        List<TextChunk> originalChunks = new ArrayList<>();
        originalChunks.add(createTextChunk("User: ", 0f, 60f, 10f, 20f));
        originalChunks.add(createTextChunk("john.doe@example.com", 20f, 60f, 140f, 20f));
        originalChunks.add(createTextChunk(". Hi!", 150f, 60f, 180f, 20f));
        TextLine line = new TextLine();
        for (TextChunk chunk : originalChunks) {
            line.add(chunk);
        }

        List<ContentSanitizer.ReplacementInfo> replacements = sanitizer.findAllReplacements(line.getValue());

        List<TextChunk> result = sanitizer.applyReplacementsToChunks(
            originalChunks, replacements);

        assertChunksContainValues(result, "User: ", "email@example.com", ". Hi!");
    }
}

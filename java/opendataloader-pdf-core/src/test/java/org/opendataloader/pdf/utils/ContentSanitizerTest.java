package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opendataloader.pdf.api.FilterConfig;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentSanitizerTest {
    private ContentSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        FilterConfig filterConfig = new FilterConfig();
        sanitizer = new ContentSanitizer(filterConfig.getFilterRules());
    }

    @ParameterizedTest
    @CsvSource({
        "12:30:45, 12:30:45",
        "1:2:3, 1:2:3",
        "John 3:16:2, John 3:16:2",
        "12:30, 12:30",
        "Score 3:2, Score 3:2",
        "John 3:16, John 3:16",
        "a:b:c:d, a:b:c:d",
        "1:2:3:4:5:6:7, 1:2:3:4:5:6:7",
        "1:2:3:4:5:6:7:8:9, 1:2:3:4:5:6:7:8:9",
        "2001:::1, 2001:::1",
        "::1:, ::1:",
        "label: value, label: value",
        "2001::db8::1, 2001::db8::1",
        "12345::1, 12345::1",
        "1:2:3:4:5:6:7::8, 1:2:3:4:5:6:7::8",
        "::1:2:3:4:5:6:7:8, ::1:2:3:4:5:6:7:8",
        "2001:db8::1, 0.0.0.0::1",
        "2001::1, 0.0.0.0::1",
        "::1, 0.0.0.0::1",
        "::, 0.0.0.0::1",
        "2001:db8::, 0.0.0.0::1",
        "1:2:3:4:5:6:7:8, 0.0.0.0::1",
        "2001:DB8:1234:5678:9ABC:DEF0:1234:5678, 0.0.0.0::1",
        "1::2:3:4:5:6:7, 0.0.0.0::1",
        "1:2::3:4:5:6:7, 0.0.0.0::1",
        "1:2:3::4:5:6:7, 0.0.0.0::1",
        "1:2:3:4::5:6:7, 0.0.0.0::1",
        "1:2:3:4:5::6:7, 0.0.0.0::1",
        "1:2:3:4:5:6::7, 0.0.0.0::1",
        "1:2:3:4:5:6:7::, 0.0.0.0::1",
        "::1:2:3:4:5:6:7, 0.0.0.0::1",
        "[::1], [0.0.0.0::1]",
        "IP:2001:db8::1, IP:0.0.0.0::1",
        "IPv6:2001:db8::1, IPv6:0.0.0.0::1",
        "Address:2001:db8::1, Address:0.0.0.0::1",
        "host-name:2001:db8::1, host-name:0.0.0.0::1",
        "IP:::1, IP:0.0.0.0::1",
        "IP:::, IP:0.0.0.0::1",
        "IP:12:30:45, IP:12:30:45",
        "IP:1:2:3:4:5:6:7:8:9, IP:1:2:3:4:5:6:7:8:9",
        "::ffff:192.0.2.128, 0.0.0.0::1",
        "0:0:0:0:0:ffff:192.0.2.128, 0.0.0.0::1",
        "2001:db8::192.0.2.128, 0.0.0.0::1",
        "::192.0.2.128, 0.0.0.0::1",
        "IP:::ffff:192.0.2.128, IP:0.0.0.0::1",
        "[::ffff:192.0.2.128]:443, [0.0.0.0::1]:443",
        "IP:2001:db8::1., IP:0.0.0.0::1.",
        "::ffff:192.0.2.128., 0.0.0.0::1.",
        "IP:2001:db8::1..., IP:0.0.0.0::1...",
        "::ffff:192.0.2.128..., 0.0.0.0::1...",
        "::ffff:192..0.2.128, ::ffff:192..0.2.128",
        "::255.255.255.255, 0.0.0.0::1",
        "::0.0.0.0, 0.0.0.0::1",
        "2001:db8::1.example.com, 2001:db8::1.example.com",
        "aa:bb:cc:dd:ee:ff, 00:00:00:00:00:00"
    })
    void testSanitizeColonSeparatedText(String input, String expected) {
        TextLine line = new TextLine();
        line.add(createTextChunk("Value (" + input + ").", 0, 0, 400, 20));

        sanitizer.sanitizeContents(Collections.singletonList(Collections.singletonList(line)));

        assertEquals("Value (" + expected + ").", line.getValue());
    }

    @Test
    void testIpv6AcrossChunksAndMaskingDisabled() {
        TextLine line = new TextLine();
        String[] parts = {"Time 12:", "30:45; IP:", "::ffff:", "192.0.", "2.128", "."};
        for (int i = 0; i < parts.length; i++) {
            line.add(createTextChunk(parts[i], i * 100, 0, (i + 1) * 100, 20));
        }
        new ContentSanitizer(new FilterConfig().getFilterRules(), false)
            .sanitizeContents(Collections.singletonList(Collections.singletonList(line)));
        assertEquals(String.join("", parts), line.getValue());

        sanitizer.sanitizeContents(Collections.singletonList(Collections.singletonList(line)));
        assertEquals("Time 12:30:45; IP:0.0.0.0::1.", line.getValue());
    }

    @Test
    void testCustomRuleCapturingGroupsStillReplaceEntireMatch() {
        ContentSanitizer custom = new ContentSanitizer(Collections.singletonList(
            new SanitizationRule(Pattern.compile("prefix:(secret)"), "$1")));
        TextLine line = new TextLine();
        line.add(createTextChunk("prefix:secret", 0, 0, 200, 20));

        custom.sanitizeContents(Collections.singletonList(Collections.singletonList(line)));

        assertEquals("$1", line.getValue());
    }

    @Test
    void testUnmatchedReplacementGroupIsSkipped() {
        ContentSanitizer custom = new ContentSanitizer(Collections.singletonList(
            new SanitizationRule(Pattern.compile("prefix(?::(secret))?"), "hidden", 1)));
        TextLine line = new TextLine();
        line.add(createTextChunk("prefix", 0, 0, 200, 20));

        custom.sanitizeContents(Collections.singletonList(Collections.singletonList(line)));

        assertEquals("prefix", line.getValue());
    }

    @Test
    void testInvalidReplacementGroupIsRejected() {
        assertThrows(IllegalArgumentException.class,
            () -> new SanitizationRule(Pattern.compile("(secret)"), "hidden", -1));
        assertThrows(IllegalArgumentException.class,
            () -> new SanitizationRule(Pattern.compile("(secret)"), "hidden", 2));
    }

    TextChunk createTextChunk(String value, double left, double bottom, double right, double top) {
        TextChunk chunk = new TextChunk(new BoundingBox(left, bottom, right, top), value,10, 10);
        chunk.getStreamInfos().add(new StreamInfo(0, null, 0, value.length()));
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
        originalChunks.add(createTextChunk("User: john", 0f, 60f, 100, 20f));
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
        originalChunks.add(createTextChunk(" Hello!", 180f, 60f, 210f, 20f));
        TextLine line = new TextLine();
        for (TextChunk chunk : originalChunks) {
            line.add(chunk);
        }

        List<ContentSanitizer.ReplacementInfo> replacements = sanitizer.findAllReplacements(line.getValue());

        List<TextChunk> result = sanitizer.applyReplacementsToChunks(
            originalChunks, replacements);

        assertChunksContainValues(result, "User: ", "email@example.com", ". Hi!", " Hello!");
    }
}

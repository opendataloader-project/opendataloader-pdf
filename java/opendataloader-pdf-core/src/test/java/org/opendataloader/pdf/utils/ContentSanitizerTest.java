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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSanitizerTest {
    private ContentSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        FilterConfig filterConfig = new FilterConfig();
        sanitizer = new ContentSanitizer(filterConfig.getFilterRules());
    }

    @ParameterizedTest
    @CsvSource({
        // Timestamps and ratios are not IPv6 and must survive.
        "12:30:45, 12:30:45",
        "1:2:3, 1:2:3",
        "John 3:16:2, John 3:16:2",
        // A MAC address is not IPv6, so the MAC rule masks it.
        "MAC 00:1A:2B:3C:4D:5E end, MAC 00:00:00:00:00:00 end",
        "12:30, 12:30",
        "Score 3:2, Score 3:2",
        "John 3:16, John 3:16",
        "a:b:c:d, a:b:c:d",
        "1:2:3:4:5:6:7, 1:2:3:4:5:6:7",
        // A malformed colon run still contains a valid address, so the valid part is masked.
        "1:2:3:4:5:6:7:8:9, 0.0.0.0::1:9",
        "2001:::1, 0.0.0.0::1:1",
        "2001::db8::1, 0.0.0.0::1::1",
        "1:2:3:4:5:6:7::8, 1:0.0.0.0::1",
        "::1:2:3:4:5:6:7:8, 0.0.0.0::1:8",
        "IP:1:2:3:4:5:6:7:8:9, IP:0.0.0.0::1:9",
        "::ffff:192..0.2.128, 0.0.0.0::1..0.2.128",
        // A trailing colon, a port, a dotted suffix, or a label stays outside the replaced span.
        "::1:, 0.0.0.0::1:",
        "label: value, label: value",
        "12345::1, 12345::1",
        "dead-beef::1, dead-0.0.0.0::1",
        "token-beef::1, token-0.0.0.0::1",
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
        // Addresses followed by a colon, a port, a dotted suffix, or a hyphen are masked.
        "Addr 2001:db8::1: down, Addr 0.0.0.0::1: down",
        "'IP: 2001:db8::1:', 'IP: 0.0.0.0::1:'",
        "Server:2001:db8::1:port, Server:0.0.0.0::1:port",
        "2001:db8:85a3:0:0:8a2e:370:7334:, 0.0.0.0::1:",
        "2001:db8:0:0:0:0:0:1:8080, 0.0.0.0::1:8080",
        "2001:db8::1.Next, 0.0.0.0::1.Next",
        "abcde:2001:db8::1, abcde:0.0.0.0::1",
        "2001:db8::1-2001:db8::2, 0.0.0.0::1-0.0.0.0::1",
        "a-2001:db8::1, a-0.0.0.0::1",
        // IPv4-embedded forms.
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
        "::255.255.255.255, 0.0.0.0::1",
        "::0.0.0.0, 0.0.0.0::1",
        "2001:db8::1.example.com, 0.0.0.0::1.example.com",
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

    @ParameterizedTest
    @CsvSource({
        "prefix(), 1, false",
        "prefix(), 1, true",
        "(?<=prefix), 0, false",
        "(?<=prefix), 0, true"
    })
    void testEmptyReplacementSpanIsSkipped(String regex, int group, boolean trailingChunks) {
        ContentSanitizer custom = new ContentSanitizer(Collections.singletonList(
            new SanitizationRule(Pattern.compile(regex), "hidden", group)));
        TextLine line = new TextLine();
        line.add(createTextChunk("prefix", 0, 0, 100, 20));
        if (trailingChunks) {
            line.add(createTextChunk(" middle ", 100, 0, 200, 20));
            line.add(createTextChunk("tail", 200, 0, 300, 20));
        }

        custom.sanitizeContents(Collections.singletonList(Collections.singletonList(line)));

        assertEquals(trailingChunks ? "prefix middle tail" : "prefix", line.getValue());
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
    void testZeroLengthMatchesAreSkipped() {
        ContentSanitizer zeroLength = new ContentSanitizer(Arrays.asList(
            new SanitizationRule("line-end", Pattern.compile("$"), "X"),
            new SanitizationRule("line-start", Pattern.compile("^"), "X"),
            new SanitizationRule("word-boundary", Pattern.compile("\\b"), "X")));
        List<TextChunk> originalChunks = new ArrayList<>();
        originalChunks.add(createTextChunk("ab ", 0f, 40f, 30f, 20f));
        originalChunks.add(createTextChunk("cd", 30f, 40f, 50f, 20f));

        List<ContentSanitizer.ReplacementInfo> replacements = zeroLength.findAllReplacements("ab cd");
        List<TextChunk> result = zeroLength.applyReplacementsToChunks(originalChunks, replacements);

        assertTrue(replacements.isEmpty());
        assertChunksContainValues(result, "ab ", "cd");
    }

    @Test
    void testCustomRuleReplacementIsLiteral() {
        ContentSanitizer custom = new ContentSanitizer(Collections.singletonList(
            new SanitizationRule("group", Pattern.compile("(ab)c"), "$1\\")));
        TextChunk chunk = createTextChunk("x abc y", 0f, 40f, 100f, 20f);

        List<TextChunk> result = custom.applyReplacementsToChunks(
            Collections.singletonList(chunk), custom.findAllReplacements(chunk.getValue()));

        assertChunksContainValues(result, "x ", "$1\\", " y");
    }

    @Test
    void testStackOverflowInARuleNamesTheRule() {
        ContentSanitizer nested = new ContentSanitizer(Collections.singletonList(
            new SanitizationRule("nested-group", Pattern.compile("(a|b)*c"), "x")));
        char[] text = new char[1_000_000];
        Arrays.fill(text, 'a');

        SanitizationRuleException exception = assertThrows(SanitizationRuleException.class,
            () -> nested.findAllReplacements(new String(text)));

        assertTrue(exception.getMessage().contains("nested-group"), exception.getMessage());
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

    @Test
    void testReplacementGetsTheStreamInfosOfTheReplacedText() {
        // A tagged PDF marks the glyphs of each chunk with its stream infos. Thus the replacement
        // gets the stream infos of the replaced text in each chunk, and the other chunks keep the rest.
        List<TextChunk> originalChunks = new ArrayList<>();
        originalChunks.add(createTextChunk("Write to info", 1, 0f, 130f));
        originalChunks.add(createTextChunk("@test.org now", 2, 130f, 260f));

        List<TextChunk> result = sanitizer.applyReplacementsToChunks(
            originalChunks, sanitizer.findAllReplacements("Write to info@test.org now"));

        assertChunksContainValues(result, "Write to ", "email@example.com", " now");
        assertStreamInfos(result.get(0), "1:0-9");
        // The replacement is 4 characters longer than "info@test.org". No glyph is under these characters,
        // so their stream info has the operator index -2 (ContentSanitizer.EXTRA_CHARACTERS_OPERATOR_INDEX).
        assertStreamInfos(result.get(1), "1:9-13", "2:0-9", "-2:0-4");
        assertStreamInfos(result.get(2), "2:9-13");
    }

    @Test
    void testReplacementSplitsAtACharacter() {
        // The tagged PDF splits a chunk at the edge of a link annotation. The split counts the stream
        // info lengths as characters of the chunk value.
        TextChunk chunk = createTextChunk("info@test.org", 1, 0f, 130f);
        TextChunk replacement = sanitizer.applyReplacementsToChunks(
            Collections.singletonList(chunk), sanitizer.findAllReplacements(chunk.getValue())).get(0);

        TextChunk first = TextChunk.getTextChunk(replacement, 0, 1);
        TextChunk rest = TextChunk.getTextChunk(replacement, 1, replacement.getValue().length());

        assertStreamInfos(first, "1:0-1");
        assertStreamInfos(rest, "1:1-13", "-2:0-4");
    }

    @Test
    void testReplacementInAValueThatIsLongerThanItsStreamInfos() {
        // --replace-invalid-chars changes only the value. A replacement with two characters makes the
        // value longer than its symbol ends and its stream infos.
        TextChunk first = createTextChunk("Contact: #info@te", 1, 0f, 170f);
        first.setValue("Contact: ##info@te");
        List<TextChunk> originalChunks = new ArrayList<>();
        originalChunks.add(first);
        originalChunks.add(createTextChunk("st.org now.", 2, 170f, 280f));

        List<TextChunk> result = sanitizer.applyReplacementsToChunks(
            originalChunks, sanitizer.findAllReplacements("Contact: ##info@test.org now."));

        assertChunksContainValues(result, "Contact: ##", "email@example.com", " now.");
        int length = 0;
        for (StreamInfo streamInfo : result.get(1).getStreamInfos()) {
            length += streamInfo.getEndIndex() - streamInfo.getStartIndex();
        }
        assertEquals("email@example.com".length(), length, "sum of the stream info lengths of the replacement");
    }

    private TextChunk createTextChunk(String value, int operatorIndex, double left, double right) {
        TextChunk chunk = createTextChunk(value, left, 0, right, 20);
        chunk.getStreamInfos().set(0, new StreamInfo(operatorIndex, null, 0, value.length()));
        return chunk;
    }

    private static void assertStreamInfos(TextChunk chunk, String... expected) {
        List<String> actual = new ArrayList<>();
        for (StreamInfo streamInfo : chunk.getStreamInfos()) {
            actual.add(streamInfo.getOperatorIndex() + ":" + streamInfo.getStartIndex() + "-" + streamInfo.getEndIndex());
        }
        assertEquals(Arrays.asList(expected), actual, "stream infos of \"" + chunk.getValue() + "\"");
    }
}

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
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.FilterConfig;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SanitizationRulesLoaderTest {
    /** The template that users copy. It must give the same rules as the built-in rules. */
    private static final Path TEMPLATE = Paths.get("../../examples/sanitize-rules/default-rules.json");
    private static final String KR_MOBILE = "{\"id\": \"kr-mobile\","
            + " \"pattern\": \"(?<!\\\\d)01[016789]-\\\\d{3,4}-\\\\d{4}(?!\\\\d)\", \"replacement\": \"010-0000-0000\"}";

    @TempDir
    Path tempDir;

    private static List<SanitizationRule> parse(String json) {
        return SanitizationRulesLoader.parse(json.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> ids(List<SanitizationRule> rules) {
        return rules.stream().map(SanitizationRule::getId).collect(Collectors.toList());
    }

    private static void assertRejected(String json, String... expectedParts) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> parse(json));
        for (String part : expectedParts) {
            assertTrue(exception.getMessage().contains(part),
                    "Message should contain '" + part + "': " + exception.getMessage());
        }
    }

    private static String rule(String id, String pattern, String replacement) {
        return "{\"id\": \"" + id + "\", \"pattern\": \"" + pattern + "\", \"replacement\": \"" + replacement + "\"}";
    }

    private static String rules(int count) {
        StringBuilder json = new StringBuilder("{\"rules\": [");
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                json.append(", ");
            }
            json.append(rule("r" + i, "x" + i, "y"));
        }
        return json.append("]}").toString();
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String sanitize(List<SanitizationRule> rules, String text) {
        ContentSanitizer sanitizer = new ContentSanitizer(rules);
        TextChunk chunk = new TextChunk(new BoundingBox(0, 40, 100, 20), text, 10, 10);
        chunk.getStreamInfos().add(new StreamInfo(0, null, 0, text.length()));
        chunk.adjustSymbolEndsToBoundingBox(null);
        StringBuilder value = new StringBuilder();
        for (TextChunk result : sanitizer.applyReplacementsToChunks(Collections.singletonList(chunk),
                sanitizer.findAllReplacements(text))) {
            value.append(result.getValue());
        }
        return value.toString();
    }

    @Test
    void onlyTheRulesOfTheFileRunInTheirOrder() {
        List<SanitizationRule> result = parse("{\"rules\": [" + KR_MOBILE + ", " + rule("b", "x", "y") + "]}");
        assertEquals(Arrays.asList("kr-mobile", "b"), ids(result));
        SanitizationRule first = result.get(0);
        assertTrue(first.getPattern().matcher("Phone 010-1234-5678").find());
        assertEquals("010-0000-0000", first.getReplacement());
        assertEquals("Mail a@b.com, phone 010-0000-0000", sanitize(result, "Mail a@b.com, phone 010-1234-5678"));
    }

    @Test
    void templateHasTheBuiltInRules() throws Exception {
        List<SanitizationRule> template = SanitizationRulesLoader.load(TEMPLATE);
        List<SanitizationRule> builtIn = FilterConfig.createDefaultRules();
        assertEquals(ids(builtIn), ids(template));
        for (int i = 0; i < builtIn.size(); i++) {
            assertEquals(builtIn.get(i).getPattern().pattern(), template.get(i).getPattern().pattern(),
                    "pattern of " + builtIn.get(i).getId());
            assertEquals(builtIn.get(i).getReplacement(), template.get(i).getReplacement(),
                    "replacement of " + builtIn.get(i).getId());
        }
    }

    @Test
    void templateMasksTheSameTextAsTheBuiltInRules() throws Exception {
        // The built-in ipv6 rule replaces a group. A file rule replaces the full match. The result must be the same.
        List<SanitizationRule> template = SanitizationRulesLoader.load(TEMPLATE);
        List<SanitizationRule> builtIn = FilterConfig.createDefaultRules();
        String[] lines = {
            "Mail alice.smith@example.org and +82-10-1234-5678 now",
            "Passport AB1234567, card 1234-5678-9012-3456, id 1234567890",
            "Host 192.168.0.42 and Server:2001:db8::1:port and (::) and IP:::",
            "Meeting at 12:30:45, MAC 00:1A:2B:3C:4D:5E, see https://github.com/owner/repo",
        };
        for (String line : lines) {
            assertEquals(sanitize(builtIn, line), sanitize(template, line), line);
        }
    }

    @Test
    void replacementIsLiteralText() {
        List<SanitizationRule> result = parse("{\"rules\": [" + rule("group", "(ab)c", "$1\\\\") + "]}");
        assertEquals("$1\\", result.get(0).getReplacement());
    }

    @Test
    void utf8ByteOrderMarkIsAccepted() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] json = ("{\"rules\": [" + KR_MOBILE + "]}").getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[bom.length + json.length];
        System.arraycopy(bom, 0, content, 0, bom.length);
        System.arraycopy(json, 0, content, bom.length, json.length);
        assertEquals(1, SanitizationRulesLoader.parse(content).size());
    }

    @Test
    void rejectsContentThatIsNotOneJsonObject() {
        assertRejected("", "one JSON object");
        assertRejected("[]", "one JSON object");
        assertRejected("\"rules\"", "one JSON object");
        assertRejected("{\"rules\": [" + KR_MOBILE + "]", "not valid JSON");
        assertRejected("{\"rules\": [" + KR_MOBILE + "]} {}", "not valid JSON");
        assertRejected("{\"rules\": [], \"rules\": [" + KR_MOBILE + "]}", "not valid JSON");
    }

    @Test
    void rejectsUnknownAndMissingKeys() {
        assertRejected("{}", "no 'rules' key");
        assertRejected("{\"rule\": []}", "unknown key 'rule'", "Allowed keys: rules");
        assertRejected("{\"extends\": \"default\", \"rules\": [" + KR_MOBILE + "]}", "unknown key 'extends'");
        assertRejected("{\"rules\": [{\"id\": \"a\", \"pattern\": \"x\", \"replacement\": \"y\", \"flags\": \"i\"}]}",
                "rule 'a'", "unknown key 'flags'");
    }

    @Test
    void rejectsBadIds() {
        assertRejected("{\"rules\": [{\"pattern\": \"x\", \"replacement\": \"y\"}]}", "rule 1", "'id' is missing");
        assertRejected("{\"rules\": [{\"id\": 7, \"pattern\": \"x\", \"replacement\": \"y\"}]}", "rule 1",
                "'id' must be a string");
        assertRejected("{\"rules\": [" + rule("KR", "x", "y") + "]}", "rule 1", "the ID 'KR'");
        assertRejected("{\"rules\": [" + rule("", "x", "y") + "]}", "rule 1");
        assertRejected("{\"rules\": [" + rule(repeat('a', 65), "x", "y") + "]}", "rule 1", "1 to 64");
        assertRejected("{\"rules\": [" + KR_MOBILE + ", " + KR_MOBILE + "]}", "rule 'kr-mobile'", "more than once");
    }

    @Test
    void acceptsTheLongestIdAndBuiltInIds() {
        String id = repeat('a', 64);
        assertEquals(id, parse("{\"rules\": [" + rule(id, "x", "y") + "]}").get(0).getId());
        assertEquals("email", parse("{\"rules\": [" + rule("email", "x", "y") + "]}").get(0).getId());
    }

    @Test
    void messagesEscapeAndShortenValuesFromTheFile() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"rules\": [" + rule("\\u001b]0;title\\u0007", "x", "y") + "]}"));
        assertTrue(exception.getMessage().contains("'\\u001b]0;title\\u0007'"), exception.getMessage());
        assertTrue(exception.getMessage().chars().noneMatch(Character::isISOControl), exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"rules\": [" + rule(repeat('A', 100_000), "x", "y") + "]}"));
        assertTrue(exception.getMessage().contains("'" + repeat('A', 64) + "...'"), exception.getMessage());
        assertTrue(exception.getMessage().length() < 300, "The message must stay short");

        exception = assertThrows(IllegalArgumentException.class, () -> parse("{\"\\u001b[8mkey\": 1}"));
        assertTrue(exception.getMessage().contains("'\\u001b[8mkey'"), exception.getMessage());
    }

    @Test
    void parserMessagesAreEscapedAndShortened() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"\\u001b]0;x\\u0007\": 1, \"\\u001b]0;x\\u0007\": 2}"));
        assertTrue(exception.getMessage().contains("not valid JSON"), exception.getMessage());
        assertTrue(exception.getMessage().chars().noneMatch(Character::isISOControl), exception.getMessage());

        exception = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"rules\": [" + rule("prop", "\\\\p{\\u001b]0;x\\u0007}", "y") + "]}"));
        assertTrue(exception.getMessage().contains("does not compile"), exception.getMessage());
        assertTrue(exception.getMessage().chars().noneMatch(Character::isISOControl), exception.getMessage());

        String longKey = repeat('A', 40_000);
        exception = assertThrows(IllegalArgumentException.class,
                () -> parse("{\"" + longKey + "\": 1, \"" + longKey + "\": 2}"));
        assertTrue(exception.getMessage().length() < 400, "The message must stay short");
    }

    @Test
    void rejectsBadPatterns() {
        assertRejected("{\"rules\": [" + rule("bad-regex", "(unclosed", "x") + "]}", "rule 'bad-regex'",
                "does not compile");
        assertRejected("{\"rules\": [" + rule("a", "", "x") + "]}", "rule 'a'", "pattern is empty");
        assertRejected("{\"rules\": [{\"id\": \"a\", \"pattern\": 1, \"replacement\": \"x\"}]}", "rule 'a'",
                "'pattern' must be a string");
        assertRejected("{\"rules\": [{\"id\": \"a\", \"replacement\": \"x\"}]}", "rule 'a'", "'pattern' is missing");
        assertRejected("{\"rules\": [" + rule("a", repeat('x', SanitizationRulesLoader.MAX_PATTERN_LENGTH + 1), "x")
                + "]}", "rule 'a'", "the maximum is 5000");
    }

    @Test
    void acceptsTheLongestPatternAndReplacement() {
        List<SanitizationRule> result = parse("{\"rules\": [" + rule("a",
                repeat('x', SanitizationRulesLoader.MAX_PATTERN_LENGTH),
                repeat('y', SanitizationRulesLoader.MAX_REPLACEMENT_LENGTH)) + "]}");
        assertEquals(1, result.size());
    }

    @Test
    void rejectsBadReplacements() {
        assertRejected("{\"rules\": [" + rule("a", "x", "") + "]}", "rule 'a'", "replacement is empty");
        assertRejected("{\"rules\": [" + rule("a", "x", "two\\nlines") + "]}", "rule 'a'", "control character");
        assertRejected("{\"rules\": [" + rule("a", "x", repeat('y', SanitizationRulesLoader.MAX_REPLACEMENT_LENGTH + 1))
                + "]}", "rule 'a'", "the maximum is 200");
        assertRejected("{\"rules\": [{\"id\": \"a\", \"pattern\": \"x\"}]}", "rule 'a'", "'replacement' is missing");
    }

    @Test
    void rejectsBadRuleArrays() {
        assertRejected("{\"rules\": {}}", "must be an array");
        assertRejected("{\"rules\": [\"email\"]}", "rule 1 must be a JSON object");
        assertRejected("{\"rules\": []}", "no rules");
        assertEquals(SanitizationRulesLoader.MAX_RULES, parse(rules(SanitizationRulesLoader.MAX_RULES)).size());
        assertRejected(rules(SanitizationRulesLoader.MAX_RULES + 1), "201 rules", "the maximum is 200");
    }

    @Test
    void loadReadsAFile() throws Exception {
        Path file = tempDir.resolve("rules.json");
        Files.write(file, ("{\"rules\": [" + KR_MOBILE + "]}").getBytes(StandardCharsets.UTF_8));
        assertEquals(Arrays.asList("kr-mobile"), ids(SanitizationRulesLoader.load(file)));
    }

    @Test
    void loadNamesTheFileInTheError() throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.write(file, "{\"rules\": []}".getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SanitizationRulesLoader.load(file));
        assertTrue(exception.getMessage().contains("bad.json"), exception.getMessage());
        assertTrue(exception.getMessage().contains("no rules"), exception.getMessage());
    }

    @Test
    void loadRejectsAMissingFileAndADirectory() {
        assertThrows(IllegalArgumentException.class, () -> SanitizationRulesLoader.load(tempDir.resolve("missing.json")));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SanitizationRulesLoader.load(tempDir));
        assertTrue(exception.getMessage().contains("not a regular file"), exception.getMessage());
    }

    @Test
    void loadRejectsAFileOverTheSizeLimit() throws Exception {
        Path file = tempDir.resolve("large.json");
        String padding = repeat(' ', SanitizationRulesLoader.MAX_FILE_BYTES);
        Files.write(file, ("{\"rules\": [" + KR_MOBILE + "]}" + padding).getBytes(StandardCharsets.UTF_8));
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> SanitizationRulesLoader.load(file));
        assertTrue(exception.getMessage().contains("larger than 1048576 bytes"), exception.getMessage());
    }

    @Test
    void loadAcceptsAFileAtTheSizeLimit() throws Exception {
        Path file = tempDir.resolve("limit.json");
        String json = "{\"rules\": [" + KR_MOBILE + "]}";
        String padding = repeat(' ', SanitizationRulesLoader.MAX_FILE_BYTES - json.length());
        Files.write(file, (json + padding).getBytes(StandardCharsets.UTF_8));
        assertEquals(1, SanitizationRulesLoader.load(file).size());
    }

    @Test
    void filterConfigKeepsItsRulesWhenTheFileIsNotValid() throws Exception {
        Path file = tempDir.resolve("bad.json");
        Files.write(file, "{\"rules\": []}".getBytes(StandardCharsets.UTF_8));
        FilterConfig config = new FilterConfig();
        config.addFilterRule("secret-\\d+", "secret-0");
        List<String> before = ids(config.getFilterRules());
        assertThrows(IllegalArgumentException.class, () -> config.loadFilterRules(file));
        assertEquals(before, ids(config.getFilterRules()));
    }

    @Test
    void filterConfigReplacesAllRulesWithTheRulesOfTheFile() throws Exception {
        Path file = tempDir.resolve("rules.json");
        Files.write(file, ("{\"rules\": [" + KR_MOBILE + "]}").getBytes(StandardCharsets.UTF_8));
        FilterConfig config = new FilterConfig();
        config.addFilterRule("secret-\\d+", "secret-0");
        config.loadFilterRules(file);
        assertEquals(Arrays.asList("kr-mobile"), ids(config.getFilterRules()));
    }
}

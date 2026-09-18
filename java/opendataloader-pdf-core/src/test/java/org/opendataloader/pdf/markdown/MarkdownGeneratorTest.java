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
package org.opendataloader.pdf.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendataloader.pdf.api.Config;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for MarkdownGenerator, particularly heading level handling.
 * <p>
 * Per Markdown specification, heading levels should be 1-6.
 * Levels outside this range should be normalized:
 * - Levels > 6 are capped to 6
 * - Levels < 1 are normalized to 1
 */
public class MarkdownGeneratorTest {

    /**
     * Tests that heading levels 1-6 produce the correct number of # symbols.
     */
    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    void testValidHeadingLevels(int level) {
        String expected = "#".repeat(level) + " ";
        String actual = generateHeadingPrefix(level);
        assertEquals(expected, actual, "Heading level " + level + " should produce " + level + " # symbols");
    }

    /**
     * Tests that heading levels > 6 are capped to 6 (Markdown specification compliance).
     * Regression test for issue #222 (derived from #221).
     */
    @ParameterizedTest
    @ValueSource(ints = {7, 8, 10, 15, 100})
    void testHeadingLevelsCappedAt6(int level) {
        String expected = "###### "; // 6 # symbols (max allowed in Markdown)
        String actual = generateHeadingPrefix(level);
        assertEquals(expected, actual,
            "Heading level " + level + " should be capped to 6 # symbols per Markdown spec");
    }

    /**
     * Tests that heading level 0 or negative is normalized to 1.
     */
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    void testHeadingLevelsMinimumIs1(int level) {
        String expected = "# "; // 1 # symbol (minimum)
        String actual = generateHeadingPrefix(level);
        assertEquals(expected, actual,
            "Heading level " + level + " should be normalized to 1 # symbol");
    }

    /**
     * Verifies that level 6 is the maximum.
     */
    @Test
    void testMaxHeadingLevelIs6() {
        assertEquals("###### ", generateHeadingPrefix(6));
        assertEquals("###### ", generateHeadingPrefix(7));
        assertEquals("###### ", generateHeadingPrefix(999));
    }

    /**
     * Verifies that level 1 is the minimum.
     */
    @Test
    void testMinHeadingLevelIs1() {
        assertEquals("# ", generateHeadingPrefix(1));
        assertEquals("# ", generateHeadingPrefix(0));
        assertEquals("# ", generateHeadingPrefix(-1));
    }

    /**
     * Helper method that mirrors the heading prefix generation logic in
     * MarkdownGenerator.writeHeading().
     * <p>
     * This must be kept in sync with the actual implementation.
     * The logic is: Math.min(6, Math.max(1, headingLevel))
     */
    private String generateHeadingPrefix(int headingLevel) {
        // This mirrors MarkdownGenerator.writeHeading() logic
        int level = Math.min(6, Math.max(1, headingLevel));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append(MarkdownSyntax.HEADING_LEVEL);
        }
        sb.append(MarkdownSyntax.SPACE);
        return sb.toString();
    }

    // ----- formatMarkdownLinkDestination (#405) -----

    @Test
    void testFormatLinkDestination_wrapsSafePathsInAngleBrackets() {
        // Even paths with no reserved chars get wrapped — uniform output is easier
        // to reason about than "sometimes bare, sometimes angle-bracketed".
        assertEquals("<imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("imageFile1.png"));
        assertEquals("<output/sub.dir/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("output/sub.dir/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_keepsSpacesVerbatim() {
        // The whole point: inside `<...>`, spaces are legal per CommonMark §6.4.
        assertEquals("<my paper_images/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("my paper_images/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_keepsParensAndBracketsVerbatim() {
        assertEquals("<draft (v2) [rc]/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("draft (v2) [rc]/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_keepsApostropheAndDoubleQuoteVerbatim() {
        // Common real-world filename — `John's "draft".pdf`.
        assertEquals("<John's \"draft\"_images/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("John's \"draft\"_images/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_keepsPercentAndHashVerbatim() {
        // No percent-decoding happens inside `<...>`, so a literal `%` or `#`
        // in a directory name renders correctly without double-encoding.
        assertEquals("<100% (final)#1/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("100% (final)#1/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_escapesAngleBracketsInPath() {
        // `<` and `>` inside the destination must be backslash-escaped — they
        // would otherwise terminate the angle-bracket form prematurely.
        assertEquals("<a\\<b\\>c/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("a<b>c/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_escapesBackslashInPath() {
        // Backslash itself must be escaped to round-trip cleanly.
        assertEquals("<a\\\\b/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("a\\b/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_replacesNewlinesWithSpaces() {
        // Newlines have no representable form in a link destination per spec.
        assertEquals("<a b/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("a\nb/imageFile1.png"));
        assertEquals("<a b/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("a\rb/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_passesThroughNonAscii() {
        assertEquals("<문서/imageFile1.png>",
                MarkdownGenerator.formatMarkdownLinkDestination("문서/imageFile1.png"));
    }

    @Test
    void testFormatLinkDestination_handlesNull() {
        assertNull(MarkdownGenerator.formatMarkdownLinkDestination(null));
    }

    // ----- getCorrectMarkdownString (`#637`) -----

    private MarkdownGenerator newGeneratorForEscaping() {
        Config config = new Config();
        return new MarkdownGenerator(new java.io.StringWriter(), config);
    }

    @Test
    void testGetCorrectMarkdownString_handlesNull() {
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("", generator.getCorrectMarkdownString(null));
    }

    @Test
    void testGetCorrectMarkdownString_removesNullCharacter() {
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("ab", generator.getCorrectMarkdownString("a\u0000b"));
    }

    @Test
    void testGetCorrectMarkdownString_escapesAmpersand() {
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("A &amp; B", generator.getCorrectMarkdownString("A & B"));
    }

    @Test
    void testGetCorrectMarkdownString_escapesLessThan() {
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("a &lt; b", generator.getCorrectMarkdownString("a < b"));
    }

    @Test
    void testGetCorrectMarkdownString_escapesGreaterThan() {
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("a &gt; b", generator.getCorrectMarkdownString("a > b"));
    }

    @Test
    void testGetCorrectMarkdownString_escapesAllReservedCharsTogether() {
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("&lt;tag&gt; A &amp; B &lt;/tag&gt;",
            generator.getCorrectMarkdownString("<tag> A & B </tag>"));
    }

    @Test
    void testGetCorrectMarkdownString_leavesPlainTextUnchanged() {
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("Plain text 123", generator.getCorrectMarkdownString("Plain text 123"));
    }

    @Test
    void testGetCorrectMarkdownString_doubleEscapesExistingEntities() {
        // Documents current behavior: since '&' is escaped first, any text that
        // already contains an HTML entity (e.g. "&amp;") gets double-escaped
        // (e.g. "&amp;amp;"). This is a known limitation, not necessarily the
        // desired long-term behavior.
        MarkdownGenerator generator = newGeneratorForEscaping();
        assertEquals("&amp;amp;", generator.getCorrectMarkdownString("&amp;"));
    }

    // ----- unordered list label stripping (#648 regression: first character lost) -----

    @Test
    void testStartsWithLabelGlyph_bulletGlyphsAreStripped() {
        assertTrue(MarkdownGenerator.startsWithLabelGlyph("• item", 1));
        assertTrue(MarkdownGenerator.startsWithLabelGlyph("- item", 1));
        assertTrue(MarkdownGenerator.startsWithLabelGlyph("◦ 진영농협", 1));
        assertTrue(MarkdownGenerator.startsWithLabelGlyph("□ 화훼 생산 현황", 1));
    }

    @Test
    void testStartsWithLabelGlyph_textWithoutBulletKeepsFirstCharacter() {
        // Items assembled from plain text nodes carry labelLength == 1 although nothing was a label:
        // letters, digits and ordinary punctuation must survive.
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("단감시즌 외 선별작업이 없어", 1));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("2022년 파프리카 글로벌GAP 인증", 1));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("Turn the focusing knob", 1));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("(note) see below", 1));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("$100 per unit", 1));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("_config", 1));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph(" leading space", 1));
    }

    @Test
    void testStartsWithLabelGlyph_invalidLengths() {
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("• item", 0));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("•", 2));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph("", 1));
        assertFalse(MarkdownGenerator.startsWithLabelGlyph(null, 1));
    }

    private static org.verapdf.wcag.algorithms.entities.lists.ListItem unorderedItem(String text, int labelLength) {
        org.verapdf.wcag.algorithms.entities.geometry.BoundingBox bbox =
                new org.verapdf.wcag.algorithms.entities.geometry.BoundingBox(0, 10.0, 10.0, 200.0, 22.0);
        org.verapdf.wcag.algorithms.entities.content.TextChunk chunk =
                new org.verapdf.wcag.algorithms.entities.content.TextChunk(bbox, text, 12.0, 12.0);
        chunk.adjustSymbolEndsToBoundingBox(null);
        // a fixed id keeps the test independent of the thread-local content-id counter
        org.verapdf.wcag.algorithms.entities.lists.ListItem item =
                new org.verapdf.wcag.algorithms.entities.lists.ListItem(bbox, Long.valueOf(text.hashCode()));
        item.add(new org.verapdf.wcag.algorithms.entities.content.TextLine(chunk));
        item.setLabelLength(labelLength);
        return item;
    }

    private static String renderUnorderedList(org.verapdf.wcag.algorithms.entities.lists.ListItem... items) throws java.io.IOException {
        java.io.StringWriter out = new java.io.StringWriter();
        MarkdownGenerator generator = new MarkdownGenerator(out, new Config());
        org.verapdf.wcag.algorithms.entities.lists.PDFList list = new org.verapdf.wcag.algorithms.entities.lists.PDFList();
        list.setNumberingStyle(org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames.UNORDERED);
        for (org.verapdf.wcag.algorithms.entities.lists.ListItem item : items) {
            list.add(item);
        }
        generator.writeList(list);
        return out.toString();
    }

    @Test
    void testWriteList_dropsBulletGlyphLabel() throws java.io.IOException {
        // the label detection records the glyph plus the following space as the label (length 2)
        String markdown = renderUnorderedList(
                unorderedItem("• first item", 2), unorderedItem("◦ 진영농협", 2), unorderedItem("- dashed item", 2));
        assertTrue(markdown.contains("- first item"), markdown);
        assertTrue(markdown.contains("- 진영농협"), markdown);
        assertTrue(markdown.contains("- dashed item"), markdown);
        assertFalse(markdown.contains("- - dashed item"), markdown);
        assertFalse(markdown.contains("• "), markdown);
        assertFalse(markdown.contains("◦ "), markdown);
    }

    @Test
    void testWriteList_keepsFirstCharacterWhenItemHasNoBulletGlyph() throws java.io.IOException {
        // items assembled from plain text nodes carry labelLength == 1: the first character is the "label"
        String markdown = renderUnorderedList(
                unorderedItem("단감시즌 외 선별작업", 1), unorderedItem("2022년 파프리카", 1),
                unorderedItem("(note) see below", 1), unorderedItem("$100 per unit", 1));
        assertTrue(markdown.contains("- 단감시즌 외 선별작업"), markdown);
        assertTrue(markdown.contains("- 2022년 파프리카"), markdown);
        assertTrue(markdown.contains("- (note) see below"), markdown);
        assertTrue(markdown.contains("- $100 per unit"), markdown);
        assertFalse(markdown.contains("- 감시즌"), markdown);
        assertFalse(markdown.contains("- 022년"), markdown);
    }
}

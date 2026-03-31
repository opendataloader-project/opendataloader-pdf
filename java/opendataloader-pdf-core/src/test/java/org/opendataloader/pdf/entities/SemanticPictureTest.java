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
package org.opendataloader.pdf.entities;

import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import static org.junit.jupiter.api.Assertions.*;

class SemanticPictureTest {

    private static final BoundingBox BBOX = new BoundingBox(0, 0, 100, 100);

    // --- hasDescription ---

    @Test
    void hasDescription_nullDescription_returnsFalse() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1);
        assertFalse(picture.hasDescription());
    }

    @Test
    void hasDescription_emptyDescription_returnsFalse() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "");
        assertFalse(picture.hasDescription());
    }

    @Test
    void hasDescription_nonEmptyDescription_returnsTrue() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "A bar chart");
        assertTrue(picture.hasDescription());
    }

    // --- sanitizeDescription: no description ---

    @Test
    void sanitizeDescription_noDescription_returnsEmpty() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1);
        assertEquals("", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_emptyDescription_returnsEmpty() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "");
        assertEquals("", picture.sanitizeDescription());
    }

    // --- sanitizeDescription: clean input ---

    @Test
    void sanitizeDescription_cleanText_returnsUnchanged() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "A bar chart showing sales data");
        assertEquals("A bar chart showing sales data", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_textWithNumbers_returnsUnchanged() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "Figure 3: Q1 2025 results 42%");
        assertEquals("Figure 3: Q1 2025 results 42%", picture.sanitizeDescription());
    }

    // --- sanitizeDescription: HTML attribute delimiters ---

    @Test
    void sanitizeDescription_doubleQuotes_removed() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "hell \"world\" my friend");
        assertEquals("hell world my friend", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_htmlTags_removed() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "hell \"world\" my friend! <this is god@%>");
        assertEquals("hell world my friend! this is god@%", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_ampersand_removedAndWhitespaceCollapsed() {
        // & removed → "Sales  Marketing" → whitespace collapsed → "Sales Marketing"
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "Sales & Marketing");
        String result = picture.sanitizeDescription();
        assertFalse(result.contains("&"));
        assertFalse(result.contains("  ")); // no double space after collapse
        assertEquals("Sales Marketing", result);
    }

    // --- sanitizeDescription: Markdown alt delimiters ---

    @Test
    void sanitizeDescription_squareBrackets_removed() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "See [figure 1] for details");
        assertEquals("See figure 1 for details", picture.sanitizeDescription());
    }

    // --- sanitizeDescription: newlines ---

    @Test
    void sanitizeDescription_newline_replacedWithSpace() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "Line one\nLine two");
        assertEquals("Line one Line two", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_carriageReturn_replacedWithSpace() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "Line one\rLine two");
        assertEquals("Line one Line two", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_crLf_replacedWithSingleSpace() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "Line one\r\nLine two");
        assertEquals("Line one Line two", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_multipleNewlines_collapsedToSingleSpace() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "Line one\n\nLine two");
        assertEquals("Line one Line two", picture.sanitizeDescription());
    }

    // --- sanitizeDescription: null character ---

    @Test
    void sanitizeDescription_nullChar_removed() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "hello\u0000world");
        assertEquals("helloworld", picture.sanitizeDescription());
    }

    // --- sanitizeDescription: whitespace collapsing & trim ---

    @Test
    void sanitizeDescription_leadingTrailingWhitespace_trimmed() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "  hello world  ");
        assertEquals("hello world", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_consecutiveSpaces_collapsed() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "hello   world");
        assertEquals("hello world", picture.sanitizeDescription());
    }

    // --- sanitizeDescription: combined real-world cases ---

    @Test
    void sanitizeDescription_aiGeneratedWithSpecialChars() {
        // Typical AI model output with mixed special characters
        SemanticPicture picture = new SemanticPicture(BBOX, 1,
                "A bar chart titled \"Q4 Results\" showing <revenue> & <profit> trends.\nValues range from $10M to $50M.");
        String result = picture.sanitizeDescription();
        assertFalse(result.contains("\""));
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertFalse(result.contains("&"));
        assertFalse(result.contains("\n"));
        assertFalse(result.contains("  "));
        assertEquals("A bar chart titled Q4 Results showing revenue profit trends. Values range from $10M to $50M.", result);
    }

    @Test
    void sanitizeDescription_onlySpecialChars_returnsEmpty() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "\"<>&[]");
        assertEquals("", picture.sanitizeDescription());
    }

    @Test
    void sanitizeDescription_onlyWhitespace_returnsEmpty() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1, "   \n\r\t  ");
        // \t is not removed but trim handles edges; collapsed whitespace → trimmed to empty or near-empty
        assertTrue(picture.sanitizeDescription().isBlank());
    }

    // --- sanitizeDescription: idempotency ---

    @Test
    void sanitizeDescription_idempotent() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1,
                "hell \"world\" <test> & [link]");
        String once = picture.sanitizeDescription();
        SemanticPicture picture2 = new SemanticPicture(BBOX, 1, once);
        assertEquals(once, picture2.sanitizeDescription());
    }

    // --- sanitizeDescription: safe for Markdown alt ---

    @Test
    void sanitizeDescription_safeForMarkdownAlt() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1,
                "Chart [showing] data \"here\" <b>bold</b> & more");
        String result = picture.sanitizeDescription();
        // Must not contain Markdown alt-breaking chars
        assertFalse(result.contains("["));
        assertFalse(result.contains("]"));
        // Must be embeddable in ![...](path) without breaking
        String markdown = "![" + result + "](image.png)";
        assertTrue(markdown.startsWith("!["));
        assertTrue(markdown.endsWith("](image.png)"));
    }

    // --- sanitizeDescription: safe for HTML attribute ---

    @Test
    void sanitizeDescription_safeForHtmlAttribute() {
        SemanticPicture picture = new SemanticPicture(BBOX, 1,
                "Title: \"Hello\" <World> & Co.");
        String result = picture.sanitizeDescription();
        assertFalse(result.contains("\""));
        assertFalse(result.contains("<"));
        assertFalse(result.contains(">"));
        assertFalse(result.contains("&"));
        // Safe to embed in alt="..."
        String html = "<img alt=\"" + result + "\">";
        assertTrue(html.contains("alt=\""));
    }
}

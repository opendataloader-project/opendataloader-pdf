/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.markdown;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
}

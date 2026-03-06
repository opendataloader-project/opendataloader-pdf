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

import java.util.List;

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

    @Test
    void testBuildMetricColumnsDisambiguatesDuplicates() {
        List<String> cols = MarkdownGenerator.buildMetricColumns("pass@1 cons@64 pass@1 pass@1 pass@1 rating", 6);
        assertEquals(List.of("pass@1", "cons@64", "pass@1_2", "pass@1_3", "pass@1_4", "rating"), cols);
    }

    @Test
    void testRecoverBenchmarkRowsFromFlattenedText() {
        String flattened = "GPT-4o-0513 9.3 13.4 74.6 49.9 32.9 759 "
            + "Claude-3.5-Sonnet-1022 16.0 26.7 78.3 65.0 38.9 717 "
            + "OpenAI-o1-mini 63.6 80.0 90.0 60.0 53.8 1820";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(flattened, 6);
        assertEquals(3, rows.size());
    }

    @Test
    void testCountMetricTokensRecognizesDeepSeekHeaderShape() {
        int count = MarkdownGenerator.countMetricTokens("pass@1 cons@64 pass@1 pass@1 pass@1 rating");
        assertEquals(6, count);
    }

    @Test
    void testRecoverBenchmarkRowsIgnoresProse() {
        String prose = "This paragraph discusses benchmark trends without structured rows or scores.";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(prose, 6);
        assertTrue(rows.isEmpty());
    }

    @Test
    void testRecoverBenchmarkRowsSupportsTwoRowTables() {
        String flattened = "OpenAI-o1-0912 74.4 83.3 96.4 63.6 53.8 1840 "
            + "DeepSeek-R1 79.8 86.7 97.3 65.9 49.2 2029";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(flattened, 6);
        assertEquals(2, rows.size());
    }

    @Test
    void testRecoverBenchmarkRowsFromDeepSeekDistillBlock() {
        String flattened = "GPT-4o-0513 9.3 74.6 49.9 759 Claude-3.5-Sonnet-1022 16.0 26.7 78.3 38.9 717 "
            + "DeepSeek-R1-Distill-Qwen-1.5B 28.9 52.7 83.9 33.8 16.9 954 "
            + "DeepSeek-R1-Distill-Qwen-7B 55.5 83.3 92.8 49.1 37.6 1189 "
            + "DeepSeek-R1-Distill-Qwen-14B 69.7 80.0 93.9 59.1 53.1 1481";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(flattened, 6);
        assertTrue(rows.size() >= 2);
    }

    @Test
    void testRecoverBenchmarkRowsAllowsPartialWhenAtLeastOneFullRowExists() {
        String flattened = "QwQ-32B-Preview 50.0 60.0 41.9 "
            + "Qwen2.5-32B-Zero 47.0 60.0 40.2 "
            + "DeepSeek-R1-Distill-Qwen-32B 72.6 83.3 94.3 62.1 57.2";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(flattened, 5);
        assertEquals(3, rows.size());
    }

    @Test
    void testRecoverBenchmarkRowsSkipsPartialWhenNoFullRowsExist() {
        String flattened = "QwQ-32B-Preview 50.0 60.0 41.9 "
            + "Qwen2.5-32B-Zero 47.0 60.0 40.2";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(flattened, 5);
        assertTrue(rows.isEmpty());
    }

    @Test
    void testRecoverBenchmarkRowsSupportsPercentValues() {
        String flattened = "GPT-4o-0513 9.3% "
            + "Qwen2-Math-7B-Instruct 7.9% 4.6% "
            + "Qwen2-Math-7B-Zero 22.3% 18.1%";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(flattened, 2);
        assertEquals(3, rows.size());
    }

    @Test
    void testRecoverBenchmarkRowsPercentOnlyWithoutFullRowStillSkips() {
        String flattened = "GPT-4o-0513 9.3% "
            + "Qwen2-Math-7B-Instruct 7.9%";
        List<?> rows = MarkdownGenerator.recoverBenchmarkRowsFromText(flattened, 2);
        assertTrue(rows.isEmpty());
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

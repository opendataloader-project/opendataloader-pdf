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
package org.opendataloader.pdf;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for page separator options (--markdown-page-separator, --text-page-separator, --html-page-separator).
 * Tests the full pipeline from Config to output files.
 */
class PageSeparatorIntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/1901.03003.pdf";
    private static final String OUTPUT_BASENAME = "1901.03003";

    @TempDir
    Path tempDir;

    private File samplePdf;

    @BeforeEach
    void setUp() {
        samplePdf = new File(SAMPLE_PDF);
        assumeTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    // --- Markdown Page Separator Tests ---

    @Test
    void testMarkdownPageSeparatorSimple() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("---");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        assertTrue(mdContent.contains("---"), "Markdown should contain the page separator '---'");
    }

    @Test
    void testMarkdownPageSeparatorWithPageNumber() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("<!-- Page %page-number% -->");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        assertTrue(mdContent.contains("<!-- Page 1 -->"), "Markdown should contain page separator with page number 1");
    }

    @Test
    void testMarkdownPageSeparatorEmpty() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        // Default empty separator - no separator should be added

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        assertFalse(mdContent.contains("<!-- Page"), "Markdown should not contain page separators when empty");
    }

    // --- Text Page Separator Tests ---

    @Test
    void testTextPageSeparatorSimple() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setTextPageSeparator("=====");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        assertTrue(Files.exists(txtOutput), "Text output should exist");

        String txtContent = Files.readString(txtOutput);
        assertTrue(txtContent.contains("====="), "Text should contain the page separator '====='");
    }

    @Test
    void testTextPageSeparatorWithPageNumber() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setTextPageSeparator("[Page %page-number%]");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        assertTrue(Files.exists(txtOutput), "Text output should exist");

        String txtContent = Files.readString(txtOutput);
        assertTrue(txtContent.contains("[Page 1]"), "Text should contain page separator with page number 1");
    }

    @Test
    void testTextPageSeparatorEmpty() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        // Default empty separator

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        assertTrue(Files.exists(txtOutput), "Text output should exist");

        String txtContent = Files.readString(txtOutput);
        assertFalse(txtContent.contains("[Page"), "Text should not contain page separators when empty");
    }

    // --- HTML Page Separator Tests ---

    @Test
    void testHtmlPageSeparatorSimple() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setHtmlPageSeparator("<hr class=\"page-break\"/>");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        assertTrue(Files.exists(htmlOutput), "HTML output should exist");

        String htmlContent = Files.readString(htmlOutput);
        assertTrue(htmlContent.contains("<hr class=\"page-break\"/>"), "HTML should contain the page separator");
    }

    @Test
    void testHtmlPageSeparatorWithPageNumber() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setHtmlPageSeparator("<div class=\"page\" data-page=\"%page-number%\">");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        assertTrue(Files.exists(htmlOutput), "HTML output should exist");

        String htmlContent = Files.readString(htmlOutput);
        assertTrue(htmlContent.contains("<div class=\"page\" data-page=\"1\">"), "HTML should contain page separator with page number 1");
    }

    @Test
    void testHtmlPageSeparatorEmpty() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        // Default empty separator

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        assertTrue(Files.exists(htmlOutput), "HTML output should exist");

        String htmlContent = Files.readString(htmlOutput);
        assertFalse(htmlContent.contains("<hr class=\"page-break\"/>"), "HTML should not contain page separators when empty");
        assertFalse(htmlContent.contains("data-page="), "HTML should not contain page separators when empty");
    }

    // --- Config Unit Tests ---

    @Test
    void testConfigPageSeparatorDefaults() {
        Config config = new Config();

        assertEquals("", config.getMarkdownPageSeparator(), "Default markdown page separator should be empty");
        assertEquals("", config.getTextPageSeparator(), "Default text page separator should be empty");
        assertEquals("", config.getHtmlPageSeparator(), "Default html page separator should be empty");
    }

    @Test
    void testConfigPageSeparatorSetters() {
        Config config = new Config();

        config.setMarkdownPageSeparator("---");
        assertEquals("---", config.getMarkdownPageSeparator());

        config.setTextPageSeparator("=====");
        assertEquals("=====", config.getTextPageSeparator());

        config.setHtmlPageSeparator("<hr/>");
        assertEquals("<hr/>", config.getHtmlPageSeparator());
    }

    @Test
    void testConfigPageNumberConstant() {
        assertEquals("%page-number%", Config.PAGE_NUMBER_STRING, "PAGE_NUMBER_STRING constant should be correct");
    }

    // --- Page Separator x --pages Filter Combination Tests ---
    // Contract: when --pages selects a subset, separators must appear only for the selected pages
    // and exactly once each. Assertions use occurrence counts so a stray substring in the PDF body
    // text cannot mask a regression.

    private static final int SAMPLE_PAGE_COUNT = 15;

    /**
     * Counts non-overlapping occurrences of {@code needle} in {@code haystack}.
     * Used by separator assertions so a stray substring in extracted PDF body
     * text cannot mask a regression. Markers used here include a trailing
     * delimiter (e.g. {@code "<!-- Page 1 -->"}, {@code "data-page=\"1\""})
     * so page 1 does not falsely match inside page 10..15.
     */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String mdMarker(int page) {
        return "<!-- Page " + page + " -->";
    }

    private static String textMarker(int page) {
        return "[Page " + page + "]";
    }

    private static String htmlMarker(int page) {
        return "data-page=\"" + page + "\"";
    }

    @Test
    void testMarkdownPageSeparatorRespectsPagesFilter() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("<!-- Page %page-number% -->");
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        String mdContent = Files.readString(mdOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == 1 || page == 3) ? 1 : 0;
            assertEquals(expected, countOccurrences(mdContent, mdMarker(page)),
                "Markdown page " + page + " marker count mismatch");
        }
    }

    @Test
    void testTextPageSeparatorRespectsPagesFilter() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setTextPageSeparator("[Page %page-number%]");
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        String txtContent = Files.readString(txtOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == 1 || page == 3) ? 1 : 0;
            assertEquals(expected, countOccurrences(txtContent, textMarker(page)),
                "Text page " + page + " marker count mismatch");
        }
    }

    @Test
    void testHtmlPageSeparatorRespectsPagesFilter() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setHtmlPageSeparator("<div data-page=\"%page-number%\">");
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        String htmlContent = Files.readString(htmlOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == 1 || page == 3) ? 1 : 0;
            assertEquals(expected, countOccurrences(htmlContent, htmlMarker(page)),
                "HTML page " + page + " marker count mismatch");
        }
    }

    // No-filter regression guard: every page must emit its separator exactly once when --pages
    // is unset. Ensures the filter change does not regress to "select none" when the set is empty.

    @Test
    void testMarkdownPageSeparatorWithoutPagesFilterEmitsAllPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("<!-- Page %page-number% -->");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        String mdContent = Files.readString(mdOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(1, countOccurrences(mdContent, mdMarker(page)),
                "Markdown page " + page + " marker should appear exactly once without filter");
        }
    }

    @Test
    void testTextPageSeparatorWithoutPagesFilterEmitsAllPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setTextPageSeparator("[Page %page-number%]");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        String txtContent = Files.readString(txtOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(1, countOccurrences(txtContent, textMarker(page)),
                "Text page " + page + " marker should appear exactly once without filter");
        }
    }

    @Test
    void testHtmlPageSeparatorWithoutPagesFilterEmitsAllPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setHtmlPageSeparator("<div data-page=\"%page-number%\">");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        String htmlContent = Files.readString(htmlOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(1, countOccurrences(htmlContent, htmlMarker(page)),
                "HTML page " + page + " marker should appear exactly once without filter");
        }
    }

    // Boundary scenarios — lock in contract corners most likely to regress in future refactors.

    @Test
    void testMarkdownPageSeparatorLastPageOnly() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("<!-- Page %page-number% -->");
        config.setPages(String.valueOf(SAMPLE_PAGE_COUNT));

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        String mdContent = Files.readString(mdOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == SAMPLE_PAGE_COUNT) ? 1 : 0;
            assertEquals(expected, countOccurrences(mdContent, mdMarker(page)),
                "Markdown page " + page + " marker count mismatch for last-page-only selection");
        }
    }

    @Test
    void testMarkdownPageSeparatorWithOutOfRangePages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("<!-- Page %page-number% -->");
        // Mix valid + out-of-range: the in-range page must still emit; the out-of-range page must not.
        config.setPages("1,99");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        String mdContent = Files.readString(mdOutput);

        assertEquals(1, countOccurrences(mdContent, mdMarker(1)), "Page 1 marker should appear once");
        assertEquals(0, countOccurrences(mdContent, mdMarker(99)), "Out-of-range page 99 marker must not appear");
        for (int page = 2; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(0, countOccurrences(mdContent, mdMarker(page)),
                "Markdown page " + page + " marker must not appear");
        }
    }

    @Test
    void testMarkdownPageSeparatorWithNonContiguousPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("<!-- Page %page-number% -->");
        config.setPages("1,4,5");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        String mdContent = Files.readString(mdOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == 1 || page == 4 || page == 5) ? 1 : 0;
            assertEquals(expected, countOccurrences(mdContent, mdMarker(page)),
                "Markdown page " + page + " marker count mismatch for non-contiguous selection");
        }
    }

    @Test
    void testMarkdownEmptyPageSeparatorWithPagesFilter() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        // Empty separator combined with --pages: the filter must not introduce any marker.
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        String mdContent = Files.readString(mdOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(0, countOccurrences(mdContent, mdMarker(page)),
                "Markdown page " + page + " marker must not appear with empty separator");
        }
    }

    // Boundary scenarios — Text format

    @Test
    void testTextPageSeparatorLastPageOnly() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setTextPageSeparator("[Page %page-number%]");
        config.setPages(String.valueOf(SAMPLE_PAGE_COUNT));

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        String txtContent = Files.readString(txtOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == SAMPLE_PAGE_COUNT) ? 1 : 0;
            assertEquals(expected, countOccurrences(txtContent, textMarker(page)),
                "Text page " + page + " marker count mismatch for last-page-only selection");
        }
    }

    @Test
    void testTextPageSeparatorWithOutOfRangePages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setTextPageSeparator("[Page %page-number%]");
        config.setPages("1,99");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        String txtContent = Files.readString(txtOutput);

        assertEquals(1, countOccurrences(txtContent, textMarker(1)), "Page 1 marker should appear once");
        assertEquals(0, countOccurrences(txtContent, textMarker(99)), "Out-of-range page 99 marker must not appear");
        for (int page = 2; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(0, countOccurrences(txtContent, textMarker(page)),
                "Text page " + page + " marker must not appear");
        }
    }

    @Test
    void testTextPageSeparatorWithNonContiguousPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setTextPageSeparator("[Page %page-number%]");
        config.setPages("1,4,5");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        String txtContent = Files.readString(txtOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == 1 || page == 4 || page == 5) ? 1 : 0;
            assertEquals(expected, countOccurrences(txtContent, textMarker(page)),
                "Text page " + page + " marker count mismatch for non-contiguous selection");
        }
    }

    @Test
    void testTextEmptyPageSeparatorWithPagesFilter() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateText(true);
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path txtOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        String txtContent = Files.readString(txtOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(0, countOccurrences(txtContent, textMarker(page)),
                "Text page " + page + " marker must not appear with empty separator");
        }
    }

    // Boundary scenarios — HTML format

    @Test
    void testHtmlPageSeparatorLastPageOnly() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setHtmlPageSeparator("<div data-page=\"%page-number%\">");
        config.setPages(String.valueOf(SAMPLE_PAGE_COUNT));

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        String htmlContent = Files.readString(htmlOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == SAMPLE_PAGE_COUNT) ? 1 : 0;
            assertEquals(expected, countOccurrences(htmlContent, htmlMarker(page)),
                "HTML page " + page + " marker count mismatch for last-page-only selection");
        }
    }

    @Test
    void testHtmlPageSeparatorWithOutOfRangePages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setHtmlPageSeparator("<div data-page=\"%page-number%\">");
        config.setPages("1,99");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        String htmlContent = Files.readString(htmlOutput);

        assertEquals(1, countOccurrences(htmlContent, htmlMarker(1)), "Page 1 marker should appear once");
        assertEquals(0, countOccurrences(htmlContent, htmlMarker(99)), "Out-of-range page 99 marker must not appear");
        for (int page = 2; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(0, countOccurrences(htmlContent, htmlMarker(page)),
                "HTML page " + page + " marker must not appear");
        }
    }

    @Test
    void testHtmlPageSeparatorWithNonContiguousPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setHtmlPageSeparator("<div data-page=\"%page-number%\">");
        config.setPages("1,4,5");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        String htmlContent = Files.readString(htmlOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            int expected = (page == 1 || page == 4 || page == 5) ? 1 : 0;
            assertEquals(expected, countOccurrences(htmlContent, htmlMarker(page)),
                "HTML page " + page + " marker count mismatch for non-contiguous selection");
        }
    }

    @Test
    void testHtmlEmptyPageSeparatorWithPagesFilter() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        String htmlContent = Files.readString(htmlOutput);

        for (int page = 1; page <= SAMPLE_PAGE_COUNT; page++) {
            assertEquals(0, countOccurrences(htmlContent, htmlMarker(page)),
                "HTML page " + page + " marker must not appear with empty separator");
        }
    }
}

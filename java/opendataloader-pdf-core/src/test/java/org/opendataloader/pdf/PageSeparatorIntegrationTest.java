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
}

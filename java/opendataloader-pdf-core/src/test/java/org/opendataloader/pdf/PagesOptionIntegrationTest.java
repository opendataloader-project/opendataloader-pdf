/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for the --pages option.
 * Tests the full pipeline from Config to output files with page filtering.
 */
class PagesOptionIntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/1901.03003.pdf";
    private static final String OUTPUT_BASENAME = "1901.03003";
    private static final String JSON_EXT = ".json";
    private static final String JSON_OUTPUT_EXISTS_MSG = "JSON output should exist";
    private static final String PAGE_1_CONTENT_MSG = "Page 1 should have content";
    private static final String PAGE_3_CONTENT_MSG = "Page 3 should have content";
    private static final String TAGGED_PDF_NOT_FOUND_MSG = "Tagged PDF not found at ";

    @TempDir
    Path tempDir;

    private File samplePdf;

    @BeforeEach
    void setUp() {
        samplePdf = new File(SAMPLE_PDF);
        assumeTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    @Test
    void testPagesOptionSinglePage() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setPages("1");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertEquals(Set.of(1), pagesInOutput, "Only page 1 should have content when --pages=1");
    }

    @Test
    void testPagesOptionMultiplePages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setPages("1,3,5");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertTrue(pagesInOutput.contains(1), PAGE_1_CONTENT_MSG);
        assertTrue(pagesInOutput.contains(3), PAGE_3_CONTENT_MSG);
        assertTrue(pagesInOutput.contains(5), "Page 5 should have content");
        assertFalse(pagesInOutput.contains(2), "Page 2 should NOT have content");
        assertFalse(pagesInOutput.contains(4), "Page 4 should NOT have content");
    }

    @Test
    void testPagesOptionPageRange() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setPages("1-3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertTrue(pagesInOutput.contains(1), PAGE_1_CONTENT_MSG);
        assertTrue(pagesInOutput.contains(2), "Page 2 should have content");
        assertTrue(pagesInOutput.contains(3), PAGE_3_CONTENT_MSG);
        assertFalse(pagesInOutput.contains(4), "Page 4 should NOT have content");
    }

    @Test
    void testPagesOptionMixedRangeAndSingle() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setPages("1,3-5");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertTrue(pagesInOutput.contains(1), PAGE_1_CONTENT_MSG);
        assertFalse(pagesInOutput.contains(2), "Page 2 should NOT have content");
        assertTrue(pagesInOutput.contains(3), PAGE_3_CONTENT_MSG);
        assertTrue(pagesInOutput.contains(4), "Page 4 should have content");
        assertTrue(pagesInOutput.contains(5), "Page 5 should have content");
    }

    @Test
    void testPagesOptionAllPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        // No pages option - all pages should be processed

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        // 15-page document should have content from many pages
        assertTrue(pagesInOutput.size() > 5, "All pages should have content when no --pages option");
    }

    @Test
    void testPagesOptionMarkdown() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setMarkdownPageSeparator("<!-- Page %page-number% -->");
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        assertTrue(mdContent.contains("<!-- Page 1 -->"), "Should contain page 1 separator");
        assertTrue(mdContent.contains("<!-- Page 3 -->"), "Should contain page 3 separator");
        // Page 2 is skipped, so its separator shouldn't appear
        // Note: Page separators are added between pages, so we verify page 1 and 3 content exists
    }

    @Test
    void testPagesOptionExceedsDocumentPages() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setPages("1,100,200"); // 100, 200 don't exist in 15-page document

        // Should not throw - just warn and process existing pages
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertTrue(pagesInOutput.contains(1), "Only page 1 should have content (100, 200 don't exist)");
        assertFalse(pagesInOutput.contains(100), "Page 100 should NOT exist");
        assertFalse(pagesInOutput.contains(200), "Page 200 should NOT exist");
    }

    @Test
    void testPagesOptionAllPagesExceedDocument() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setPages("100,200"); // All pages don't exist

        // Should not throw - just warn and produce empty result
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertTrue(pagesInOutput.isEmpty(), "No pages should have content when all requested pages don't exist");
    }

    // ===== Tagged PDF Tests (using struct-tree) =====

    private static final String TAGGED_PDF = "../../samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-04_Presentation.pdf";
    private static final String TAGGED_OUTPUT_BASENAME = "PDFUA-Ref-2-04_Presentation";

    @Test
    void testPagesOptionTaggedPdfSinglePage() throws IOException {
        File taggedPdf = new File(TAGGED_PDF);
        assumeTrue(taggedPdf.exists(), TAGGED_PDF_NOT_FOUND_MSG + taggedPdf.getAbsolutePath());

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setUseStructTree(true);
        config.setPages("1");

        DocumentProcessor.processFile(taggedPdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(TAGGED_OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertEquals(Set.of(1), pagesInOutput, "Only page 1 should have content when --pages=1");
    }

    @Test
    void testPagesOptionTaggedPdfMultiplePages() throws IOException {
        File taggedPdf = new File(TAGGED_PDF);
        assumeTrue(taggedPdf.exists(), TAGGED_PDF_NOT_FOUND_MSG + taggedPdf.getAbsolutePath());

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setUseStructTree(true);
        config.setPages("1,2");

        DocumentProcessor.processFile(taggedPdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(TAGGED_OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertTrue(pagesInOutput.contains(1), PAGE_1_CONTENT_MSG);
        assertTrue(pagesInOutput.contains(2), "Page 2 should have content");
    }

    @Test
    void testPagesOptionTaggedPdfAllPages() throws IOException {
        File taggedPdf = new File(TAGGED_PDF);
        assumeTrue(taggedPdf.exists(), TAGGED_PDF_NOT_FOUND_MSG + taggedPdf.getAbsolutePath());

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setUseStructTree(true);
        // No pages option - all pages should be processed

        DocumentProcessor.processFile(taggedPdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(TAGGED_OUTPUT_BASENAME + JSON_EXT);
        assertTrue(Files.exists(jsonOutput), JSON_OUTPUT_EXISTS_MSG);

        JsonNode root = parseJson(jsonOutput);
        Set<Integer> pagesInOutput = getPageNumbersFromKids(root);

        assertFalse(pagesInOutput.isEmpty(), "All pages should have content when no --pages option");
    }

    private JsonNode parseJson(Path jsonPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readTree(Files.newInputStream(jsonPath));
    }

    /**
     * Extracts all unique page numbers from the 'kids' array in the JSON output.
     * Each kid element has a 'page number' field.
     */
    private Set<Integer> getPageNumbersFromKids(JsonNode root) {
        Set<Integer> pageNumbers = new HashSet<>();
        JsonNode kids = root.get("kids");
        if (kids != null && kids.isArray()) {
            for (JsonNode kid : kids) {
                JsonNode pageNumber = kid.get("page number");
                if (pageNumber != null && pageNumber.isInt()) {
                    pageNumbers.add(pageNumber.asInt());
                }
            }
        }
        return pageNumbers;
    }
}

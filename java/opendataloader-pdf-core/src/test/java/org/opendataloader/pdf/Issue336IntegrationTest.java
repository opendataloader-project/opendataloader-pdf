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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Issue336IntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/issue-336-conto-economico-bialetti.pdf";
    private static final String OUTPUT_BASENAME = "issue-336-conto-economico-bialetti";

    @TempDir
    Path tempDir;

    private File samplePdf;

    @BeforeEach
    void setUp() {
        samplePdf = new File(SAMPLE_PDF);
        assertTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    @Test
    void testSpreadsheetExportedTableKeepsFinancialRowsSeparatedAcrossStandardOutputs() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        config.setGenerateHtml(true);
        config.setGenerateText(true);
        config.setGeneratePDF(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + ".json");
        Path markdownOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        Path htmlOutput = tempDir.resolve(OUTPUT_BASENAME + ".html");
        Path textOutput = tempDir.resolve(OUTPUT_BASENAME + ".txt");
        Path annotatedPdfOutput = tempDir.resolve(OUTPUT_BASENAME + "_annotated.pdf");

        assertTrue(Files.exists(jsonOutput), "JSON output should exist");
        assertTrue(Files.exists(markdownOutput), "Markdown output should exist");
        assertTrue(Files.exists(htmlOutput), "HTML output should exist");
        assertTrue(Files.exists(textOutput), "Text output should exist");
        assertTrue(Files.exists(annotatedPdfOutput), "Annotated PDF output should exist");
        assertTrue(Files.size(annotatedPdfOutput) > 0, "Annotated PDF output should not be empty");

        assertJsonContainsExpectedRow(jsonOutput);
        assertMarkdownTableContainsExpectedRow(markdownOutput);
        assertHtmlTableContainsExpectedRow(htmlOutput);
        assertTextContainsExpectedRow(textOutput);
    }

    @Test
    void testSpreadsheetExportedTableKeepsFinancialRowsSeparatedInMarkdownHtmlOutput() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setUseHTMLInMarkdown(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path markdownOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        assertTrue(Files.exists(markdownOutput), "Markdown-with-HTML output should exist");
        assertHtmlTableContainsExpectedRow(markdownOutput);
    }

    @Test
    void testSpreadsheetExportedTableKeepsFinancialRowsSeparatedInMarkdownImageMode() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(false);
        config.setAddImageToMarkdown(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path markdownOutput = tempDir.resolve(OUTPUT_BASENAME + ".md");
        assertTrue(Files.exists(markdownOutput), "Markdown-with-images output should exist");
        assertMarkdownTableContainsExpectedRow(markdownOutput);
    }

    private static void assertJsonContainsExpectedRow(Path jsonOutput) throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(jsonOutput));
        List<List<String>> rows = extractTableRows(root);

        assertTrue(rows.contains(expectedFinancialRow()),
            "Expected the financial statement row to be extracted as its own table row");
    }

    private static void assertMarkdownTableContainsExpectedRow(Path markdownOutput) throws IOException {
        String markdown = Files.readString(markdownOutput);
        String escapedLabel = Pattern.quote(expectedFinancialRow().get(0));
        String rowPattern = "(?m)^\\|\\s*" + escapedLabel
            + "\\s*\\|\\s*1\\.942\\.000\\s*\\|\\s*117\\.000\\s*\\|\\s*2\\.538\\.000\\s*\\|\\s*-3\\.970\\.000\\s*\\|\\s*$";

        assertTrue(Pattern.compile(rowPattern).matcher(markdown).find(),
            "Expected the financial statement row to remain a single Markdown table row");
    }

    private static void assertHtmlTableContainsExpectedRow(Path htmlOutput) throws IOException {
        String html = Files.readString(htmlOutput);
        Matcher rowMatcher = Pattern.compile("(?is)<tr\\b[^>]*>.*?</tr>").matcher(html);
        List<String> expectedRow = expectedFinancialRow();
        boolean found = false;
        while (rowMatcher.find()) {
            String rowText = normalizeText(rowMatcher.group().replaceAll("(?is)<[^>]+>", " "));
            if (containsExpectedValues(rowText, expectedRow)) {
                found = true;
                break;
            }
        }

        assertTrue(found,
            "Expected the financial statement row to remain a single HTML table row");
    }

    private static void assertTextContainsExpectedRow(Path textOutput) throws IOException {
        String text = Files.readString(textOutput);
        String expectedLine = String.join("\t", expectedFinancialRow());

        assertTrue(text.contains(expectedLine),
            "Expected the financial statement row to remain a single plain-text table row");
    }

    private static List<String> expectedFinancialRow() {
        return List.of(
            "2) Variazione rimanenze prodotti in corso di lavor., semilavorati e finiti",
            "1.942.000",
            "117.000",
            "2.538.000",
            "-3.970.000"
        );
    }

    private static boolean containsExpectedValues(String value, List<String> expectedValues) {
        for (String expectedValue : expectedValues) {
            if (!value.contains(expectedValue)) {
                return false;
            }
        }
        return true;
    }

    private static List<List<String>> extractTableRows(JsonNode root) {
        List<JsonNode> tables = new ArrayList<>();
        collectTables(root, tables);

        List<List<String>> rows = new ArrayList<>();
        for (JsonNode table : tables) {
            JsonNode tableRows = table.get("rows");
            if (tableRows == null || !tableRows.isArray()) {
                continue;
            }
            for (JsonNode row : tableRows) {
                JsonNode cells = row.get("cells");
                if (cells == null || !cells.isArray()) {
                    continue;
                }
                List<String> rowTexts = new ArrayList<>();
                for (JsonNode cell : cells) {
                    rowTexts.add(normalizeText(collectContent(cell)));
                }
                rows.add(rowTexts);
            }
        }

        return rows;
    }

    private static void collectTables(JsonNode node, List<JsonNode> tables) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (type != null && "table".equals(type.asText())) {
                tables.add(node);
            }
            node.fields().forEachRemaining(entry -> collectTables(entry.getValue(), tables));
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTables(child, tables);
            }
        }
    }

    private static String collectContent(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        appendContent(node, builder);
        return builder.toString();
    }

    private static void appendContent(JsonNode node, StringBuilder builder) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode content = node.get("content");
            if (content != null && !content.asText().isBlank()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(content.asText());
            }

            JsonNode listItems = node.get("list items");
            if (listItems != null && listItems.isArray()) {
                for (JsonNode listItem : listItems) {
                    appendContent(listItem, builder);
                }
            }

            JsonNode kids = node.get("kids");
            if (kids != null && kids.isArray()) {
                for (JsonNode kid : kids) {
                    appendContent(kid, builder);
                }
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                appendContent(child, builder);
            }
        }
    }

    private static String normalizeText(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}

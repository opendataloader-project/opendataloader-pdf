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
package org.opendataloader.pdf.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #336: the border lines form fewer rows than the text.
 *
 * <p>The test builds a synthetic PDF: a grid of 2 rows and 5 columns, with 6 rows of
 * text in each grid row (12 text rows). Before the fix, each cell held 6 text rows.
 * The expected values come from this design, not from the current output.
 *
 * <p>The rows are as dense as in the reporter's spreadsheet export (a row pitch of about
 * 12 pt, glyph boxes of about 8.6 pt, gaps of about 3 pt). This density stresses the
 * row-band clustering in {@code TableStructureNormalizer}. The 12 rows per column are
 * also well above its minimum of 8 text lines.
 *
 * <p>The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names
 * start with {@code issue-336}.
 */
class Issue336UndersegmentedTableTest {

    private static final String NAME = "issue-336-undersegmented-table";
    private static final Pattern MARKDOWN_TABLE_ROW = Pattern.compile("^\\|\\s*R\\d+ C1\\s*\\|");
    private static final int BORDER_ROWS = 2;
    private static final int COLUMNS = 5;
    private static final int TEXT_ROWS = 12;
    private static final float X0 = 72;
    private static final float Y_TOP = 700;
    private static final float COLUMN_WIDTH = 90;
    private static final float BORDER_ROW_HEIGHT = 72;
    private static final float TEXT_ROW_HEIGHT = BORDER_ROW_HEIGHT * BORDER_ROWS / TEXT_ROWS;   // 12 pt
    private static final float LINE_WIDTH = 0.5f;
    private static final float FONT_SIZE = 8;
    private static final float TEXT_INSET = 8;   // from the left border of the cell
    private static final float BASELINE_OFFSET = 2;

    @Test
    void tableHasOneRowForEachTextRowInEachOutput() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-336");
        Path pdf = directory.resolve(NAME + ".pdf");
        writeSample(pdf);

        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        config.setGenerateHtml(true);
        config.setGenerateText(true);
        config.setGeneratePDF(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        assertJson(directory.resolve(NAME + ".json"));
        assertMarkdown(directory.resolve(NAME + ".md"));
        assertHtml(directory.resolve(NAME + ".html"));
        assertText(directory.resolve(NAME + ".txt"));
        Path annotated = directory.resolve(NAME + "_annotated.pdf");
        assertThat(annotated).exists();
        assertThat(Files.size(annotated)).isPositive();
    }

    @Test
    void markdownWithHtmlHasOneTableRowForEachTextRow() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-336-markdown-html");
        Path pdf = directory.resolve(NAME + ".pdf");
        writeSample(pdf);

        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(false);
        config.setUseHTMLInMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        assertHtml(directory.resolve(NAME + ".md"));
    }

    @Test
    void markdownImageModeHasOneTableRowForEachTextRow() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-336-markdown-image");
        Path pdf = directory.resolve(NAME + ".pdf");
        writeSample(pdf);

        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(false);
        config.setAddImageToMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        assertMarkdown(directory.resolve(NAME + ".md"));
    }

    private static void assertJson(Path json) throws IOException {
        JsonNode root = new ObjectMapper().readTree(json.toFile());
        List<JsonNode> tables = new ArrayList<>();
        collectTables(root, tables);

        assertThat(tables).hasSize(1);
        JsonNode table = tables.get(0);
        assertThat(table.path("number of rows").asInt()).isEqualTo(TEXT_ROWS);
        assertThat(table.path("number of columns").asInt()).isEqualTo(COLUMNS);
        assertThat(cellText(table, 1, 1)).isEqualTo(text(1, 1));
        assertThat(cellText(table, 5, 3)).isEqualTo(text(5, 3));
        assertThat(cellText(table, 8, 5)).isEqualTo(text(8, 5));
    }

    private static void assertMarkdown(Path markdown) throws IOException {
        List<List<String>> tableRows = Files.readAllLines(markdown).stream()
            .filter(line -> MARKDOWN_TABLE_ROW.matcher(line).find())
            .map(Issue336UndersegmentedTableTest::markdownCells)
            .collect(Collectors.toList());
        assertThat(tableRows).hasSize(TEXT_ROWS);
        assertThat(tableRows.get(4)).containsExactlyElementsOf(rowCells(5));
    }

    /** Splits a Markdown table row into trimmed cells, so cell padding does not matter. */
    private static List<String> markdownCells(String line) {
        String inner = line.trim();
        inner = inner.substring(1, inner.length() - 1);   // drop the outer pipes
        return Arrays.stream(inner.split("\\|", -1)).map(String::trim).collect(Collectors.toList());
    }

    private static List<String> rowCells(int row) {
        return IntStream.rangeClosed(1, COLUMNS).mapToObj(column -> text(row, column)).collect(Collectors.toList());
    }

    private static void assertHtml(Path html) throws IOException {
        Matcher row = Pattern.compile("(?is)<tr\\b.*?</tr>").matcher(Files.readString(html));
        List<String> rows = new ArrayList<>();
        while (row.find()) {
            rows.add(row.group());
        }
        assertThat(rows).hasSize(TEXT_ROWS);
        assertThat(rows.get(4)).contains(text(5, 1), text(5, 5)).doesNotContain(text(6, 1));
    }

    private static void assertText(Path text) throws IOException {
        List<String> lines = Files.readAllLines(text);
        for (int row = 1; row <= TEXT_ROWS; row++) {
            assertThat(lines).contains(rowText(row, "\t"));
        }
    }

    private static String text(int row, int column) {
        return "R" + row + " C" + column;
    }

    private static String rowText(int row, String separator) {
        return IntStream.rangeClosed(1, COLUMNS)
            .mapToObj(column -> text(row, column))
            .collect(Collectors.joining(separator));
    }

    private static void writeSample(Path pdf) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                drawBorders(content);
                drawText(content);
            }
            document.save(pdf.toFile());
        }
    }

    /** Draws a grid of 2 rows: 3 horizontal lines and a vertical line at each column edge. */
    private static void drawBorders(PDPageContentStream content) throws IOException {
        float right = X0 + COLUMNS * COLUMN_WIDTH;
        float bottom = Y_TOP - BORDER_ROWS * BORDER_ROW_HEIGHT;
        content.setLineWidth(LINE_WIDTH);
        for (int row = 0; row <= BORDER_ROWS; row++) {
            float y = Y_TOP - row * BORDER_ROW_HEIGHT;
            content.moveTo(X0, y);
            content.lineTo(right, y);
        }
        for (int column = 0; column <= COLUMNS; column++) {
            float x = X0 + column * COLUMN_WIDTH;
            content.moveTo(x, Y_TOP);
            content.lineTo(x, bottom);
        }
        content.stroke();
    }

    /** Draws 12 text rows inside the 2 grid rows. */
    private static void drawText(PDPageContentStream content) throws IOException {
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
        for (int row = 1; row <= TEXT_ROWS; row++) {
            for (int column = 1; column <= COLUMNS; column++) {
                content.beginText();
                content.newLineAtOffset(X0 + (column - 1) * COLUMN_WIDTH + TEXT_INSET,
                    Y_TOP - row * TEXT_ROW_HEIGHT + BASELINE_OFFSET);
                content.showText(text(row, column));
                content.endText();
            }
        }
    }

    private static void collectTables(JsonNode node, List<JsonNode> tables) {
        if ("table".equals(node.path("type").asText())) {
            tables.add(node);
        }
        node.elements().forEachRemaining(child -> collectTables(child, tables));
    }

    private static String cellText(JsonNode table, int row, int column) {
        for (JsonNode tableRow : table.path("rows")) {
            for (JsonNode cell : tableRow.path("cells")) {
                if (cell.path("row number").asInt() == row && cell.path("column number").asInt() == column) {
                    StringBuilder content = new StringBuilder();
                    cell.path("kids").forEach(kid -> content.append(kid.path("content").asText()));
                    return content.toString();
                }
            }
        }
        return null;
    }
}

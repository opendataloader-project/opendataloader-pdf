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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Markdown table generation, specifically verifying correct handling
 * of merged cells (colspan/rowspan).
 *
 * <p>Merged cells occur in practice via:
 * <ul>
 *   <li>SpecialTableProcessor: Korean document tables (수신/경유/제목) always create colspan</li>
 *   <li>DoclingSchemaTransformer: Hybrid mode with Docling backend</li>
 *   <li>HancomSchemaTransformer: Hybrid mode with Hancom backend</li>
 *   <li>TaggedDocumentProcessor: Tagged PDFs with explicit merge attributes</li>
 * </ul>
 */
public class MarkdownTableTest {

    @TempDir
    Path tempDir;

    @BeforeAll
    static void initStaticContainers() {
        StaticContainers.updateContainers(null);
    }

    /**
     * Simulates the exact table structure created by SpecialTableProcessor
     * for Korean documents. When a row has no ':' separator (e.g., "수신"),
     * the processor creates a single cell with the same object assigned to
     * both column positions — producing a colspan-like merged cell.
     *
     * <p>Before fix: content was written twice (e.g., "|수신|수신|").
     * After fix: content written once, spanned column gets empty space (e.g., "|수신| |").
     *
     * @see org.opendataloader.pdf.processors.SpecialTableProcessor
     */
    @Test
    void testKoreanSpecialTableMergedRow() throws IOException {
        // Reproduce SpecialTableProcessor: 3 rows, 2 columns
        // "수신" (no colon → one cell spanning 2 columns)
        // "경유" (no colon → one cell spanning 2 columns)
        // "제목: 테스트" (has colon → two separate cells)
        TableBorderRow row0 = new TableBorderRow(0, 2, null);
        TableBorderCell cell00 = new TableBorderCell(0, 0, 1, 2, null);
        addTextContent(cell00, "수신");
        row0.getCells()[0] = cell00;
        row0.getCells()[1] = cell00; // same object, like SpecialTableProcessor

        TableBorderRow row1 = new TableBorderRow(1, 2, null);
        TableBorderCell cell10 = new TableBorderCell(1, 0, 1, 2, null);
        addTextContent(cell10, "경유");
        row1.getCells()[0] = cell10;
        row1.getCells()[1] = cell10;

        TableBorderRow row2 = new TableBorderRow(2, 2, null);
        TableBorderCell cell20 = new TableBorderCell(2, 0, 1, 1, null);
        addTextContent(cell20, "제목");
        TableBorderCell cell21 = new TableBorderCell(2, 1, 1, 1, null);
        addTextContent(cell21, "테스트");
        row2.getCells()[0] = cell20;
        row2.getCells()[1] = cell21;

        TableBorder table = new TableBorder(null, new TableBorderRow[]{row0, row1, row2}, 3, 2);
        String markdown = generateMarkdownTable(table);
        String[] lines = markdown.split("\n");

        // Row 0 (header): "수신" must appear exactly once
        assertEquals(1, countOccurrences(lines[0], "수신"),
            "Merged cell '수신' should appear once. Got: " + lines[0]);

        // Row 1 (after header + separator): "경유" must appear exactly once
        assertEquals(1, countOccurrences(lines[2], "경유"),
            "Merged cell '경유' should appear once. Got: " + lines[2]);

        // Row 2: "제목" and "테스트" in separate cells
        String row2Line = lines[3];
        assertTrue(row2Line.contains("제목") && row2Line.contains("테스트"),
            "Split row should contain both cells. Got: " + row2Line);
    }

    /**
     * A 3-column table where cell (0,0) has colspan=2 should produce
     * 3 column separators per row in the header separator line,
     * and the content row should not duplicate the merged cell's content.
     *
     * Before fix: the merged cell content was written twice because
     * getCells() returns duplicated references for spanned columns.
     */
    @Test
    void testColspanCellsAreNotDuplicated() throws IOException {
        // Row 0: [A (colspan=2)] [B]   — 3 columns
        // Row 1: [C] [D] [E]
        TableBorderCell cell00 = new TableBorderCell(0, 0, 2, 1, null);
        addTextContent(cell00, "A");
        TableBorderCell cell02 = new TableBorderCell(0, 2, 1, 1, null);
        addTextContent(cell02, "B");

        TableBorderRow row0 = new TableBorderRow(0, 3, null);
        row0.getCells()[0] = cell00;
        row0.getCells()[1] = cell00; // colspan duplicate
        row0.getCells()[2] = cell02;

        TableBorderCell cell10 = new TableBorderCell(1, 0, 1, 1, null);
        addTextContent(cell10, "C");
        TableBorderCell cell11 = new TableBorderCell(1, 1, 1, 1, null);
        addTextContent(cell11, "D");
        TableBorderCell cell12 = new TableBorderCell(1, 2, 1, 1, null);
        addTextContent(cell12, "E");

        TableBorderRow row1 = new TableBorderRow(1, 3, null);
        row1.getCells()[0] = cell10;
        row1.getCells()[1] = cell11;
        row1.getCells()[2] = cell12;

        TableBorder table = new TableBorder(null, new TableBorderRow[]{row0, row1}, 2, 3);
        String markdown = generateMarkdownTable(table);
        String[] lines = markdown.split("\n");

        assertTrue(lines.length >= 3, "Expected at least 3 lines, got: " + lines.length);

        // Header row: content "A" should appear once
        String headerRow = lines[0];
        assertEquals(1, countOccurrences(headerRow, "A"),
            "Merged cell content 'A' should appear exactly once in header row. Got: " + headerRow);

        // Header separator: |---|---|---|
        assertEquals(3, countOccurrences(lines[1], "---"),
            "Header separator should have 3 columns. Got: " + lines[1]);

        // Data row: |C|D|E|
        assertTrue(lines[2].contains("C") && lines[2].contains("D") && lines[2].contains("E"),
            "Data row should contain C, D, E. Got: " + lines[2]);
    }

    /**
     * A simple 2x2 table without any merged cells should work correctly.
     */
    @Test
    void testSimpleTableWithoutMergedCells() throws IOException {
        TableBorderCell cell00 = new TableBorderCell(0, 0, 1, 1, null);
        addTextContent(cell00, "H1");
        TableBorderCell cell01 = new TableBorderCell(0, 1, 1, 1, null);
        addTextContent(cell01, "H2");
        TableBorderRow row0 = new TableBorderRow(0, 2, null);
        row0.getCells()[0] = cell00;
        row0.getCells()[1] = cell01;

        TableBorderCell cell10 = new TableBorderCell(1, 0, 1, 1, null);
        addTextContent(cell10, "V1");
        TableBorderCell cell11 = new TableBorderCell(1, 1, 1, 1, null);
        addTextContent(cell11, "V2");
        TableBorderRow row1 = new TableBorderRow(1, 2, null);
        row1.getCells()[0] = cell10;
        row1.getCells()[1] = cell11;

        TableBorder table = new TableBorder(null, new TableBorderRow[]{row0, row1}, 2, 2);
        String markdown = generateMarkdownTable(table);
        String[] lines = markdown.split("\n");

        assertEquals(3, lines.length, "Simple 2x2 table should produce 3 lines");
        assertTrue(lines[0].contains("H1") && lines[0].contains("H2"), "Header row: " + lines[0]);
        assertEquals(2, countOccurrences(lines[1], "---"), "Separator columns: " + lines[1]);
        assertTrue(lines[2].contains("V1") && lines[2].contains("V2"), "Data row: " + lines[2]);
    }

    /**
     * A table with rowspan should not duplicate the cell content in subsequent rows.
     */
    @Test
    void testRowspanCellsAreNotDuplicated() throws IOException {
        // Row 0: [A (rowspan=2)] [B]
        // Row 1: [A (span)]      [C]
        // Row 2: [D]             [E]
        TableBorderCell cell00 = new TableBorderCell(0, 0, 1, 2, null);
        addTextContent(cell00, "A");
        TableBorderCell cell01 = new TableBorderCell(0, 1, 1, 1, null);
        addTextContent(cell01, "B");
        TableBorderRow row0 = new TableBorderRow(0, 2, null);
        row0.getCells()[0] = cell00;
        row0.getCells()[1] = cell01;

        TableBorderCell cell11 = new TableBorderCell(1, 1, 1, 1, null);
        addTextContent(cell11, "C");
        TableBorderRow row1 = new TableBorderRow(1, 2, null);
        row1.getCells()[0] = cell00; // rowspan duplicate
        row1.getCells()[1] = cell11;

        TableBorderCell cell20 = new TableBorderCell(2, 0, 1, 1, null);
        addTextContent(cell20, "D");
        TableBorderCell cell21 = new TableBorderCell(2, 1, 1, 1, null);
        addTextContent(cell21, "E");
        TableBorderRow row2 = new TableBorderRow(2, 2, null);
        row2.getCells()[0] = cell20;
        row2.getCells()[1] = cell21;

        TableBorder table = new TableBorder(null, new TableBorderRow[]{row0, row1, row2}, 3, 2);
        String markdown = generateMarkdownTable(table);
        String[] lines = markdown.split("\n");

        assertTrue(lines.length >= 4, "Should have 4+ lines for 3-row table");
        // Row 1 (index 2 after header+separator) should NOT contain 'A'
        String row1Line = lines[2];
        assertEquals(0, countOccurrences(row1Line, "A"),
            "Rowspan cell 'A' should not appear in row 1. Got: " + row1Line);
        assertTrue(row1Line.contains("C"), "Row 1 should contain 'C'. Got: " + row1Line);
    }

    private void addTextContent(TableBorderCell cell, String text) {
        TextChunk chunk = new TextChunk(text);
        TextLine line = new TextLine(chunk);
        TextColumn column = new TextColumn(line);
        BoundingBox bbox = new BoundingBox(null, 0, 0, 100, 10);
        SemanticParagraph paragraph = new SemanticParagraph(bbox, List.of(column));
        cell.addContentObject(paragraph);
    }

    private String generateMarkdownTable(TableBorder table) throws IOException {
        File dummyPdf = tempDir.resolve("test.pdf").toFile();
        Files.createFile(dummyPdf.toPath());
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateMarkdown(true);

        try (MarkdownGenerator generator = new MarkdownGenerator(dummyPdf, config)) {
            generator.writeTable(table);
        }

        File mdFile = tempDir.resolve("test.md").toFile();
        return Files.readString(mdFile.toPath()).trim();
    }

    private long countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}

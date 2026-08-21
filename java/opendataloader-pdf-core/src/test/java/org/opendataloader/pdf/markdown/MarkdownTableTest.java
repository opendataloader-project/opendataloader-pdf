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

        // Row 0 (header): "수신" repeated across colspan (2 columns)
        assertEquals(2, countOccurrences(lines[0], "수신"),
            "Merged cell '수신' should appear twice (colspan=2). Got: " + lines[0]);

        // Row 1 (after header + separator): "경유" repeated across colspan (2 columns)
        assertEquals(2, countOccurrences(lines[2], "경유"),
            "Merged cell '경유' should appear twice (colspan=2). Got: " + lines[2]);

        // Row 2: "제목" and "테스트" in separate cells
        String row2Line = lines[3];
        assertTrue(row2Line.contains("제목") && row2Line.contains("테스트"),
            "Split row should contain both cells. Got: " + row2Line);
    }

    /**
     * A 3-column table where cell (0,0) has colspan=2 should repeat
     * the merged cell content across the spanned columns for better
     * Markdown readability and proper table alignment.
     *
     * Expected behavior: merged cell content is repeated for each
     * spanned column to ensure consistent table column count and
     * readable Markdown output when viewing raw text.
     */
    @Test
    void testColspanCellsAreRepeatedAcrossColumns() throws IOException {
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

        // Header row: content "A" repeated across colspan (2 columns)
        String headerRow = lines[0];
        assertEquals(2, countOccurrences(headerRow, "A"),
            "Merged cell content 'A' should appear twice (colspan=2) in header row. Got: " + headerRow);

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
     * A table with rowspan should repeat the cell content in subsequent rows
     * for better Markdown readability and proper table alignment.
     */
    @Test
    void testRowspanCellsAreRepeatedDownRows() throws IOException {
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
        // Row 1 (index 2 after header+separator) should contain 'A' (filled down from rowspan)
        String row1Line = lines[2];
        assertEquals(1, countOccurrences(row1Line, "A"),
            "Rowspan cell 'A' should appear in row 1 (fill-down). Got: " + row1Line);
        assertTrue(row1Line.contains("C"), "Row 1 should contain 'C'. Got: " + row1Line);
    }
    
    /**
     * A mixed-merge table (colspan + rowspan on the same parent cell) should
     * preserve repeated content across all covered coordinates.
     */
    @Test
    void testMixedColspanAndRowspanAreRepeated() throws IOException {
        // Row 0: [A (colspan=2,rowspan=2)] [B] [C]
        // Row 1: [A (span)] [A (span)]      [D] [E]
        // Row 2: [F] [G] [H] [I]
        TableBorderCell cellA = new TableBorderCell(0, 0, 2, 2, null);
        addTextContent(cellA, "A");
        TableBorderCell cellB = new TableBorderCell(0, 2, 1, 1, null);
        addTextContent(cellB, "B");
        TableBorderCell cellC = new TableBorderCell(0, 3, 1, 1, null);
        addTextContent(cellC, "C");
        TableBorderRow row0 = new TableBorderRow(0, 4, null);
        row0.getCells()[0] = cellA;
        row0.getCells()[1] = cellA;
        row0.getCells()[2] = cellB;
        row0.getCells()[3] = cellC;

        TableBorderCell cellD = new TableBorderCell(1, 2, 1, 1, null);
        addTextContent(cellD, "D");
        TableBorderCell cellE = new TableBorderCell(1, 3, 1, 1, null);
        addTextContent(cellE, "E");
        TableBorderRow row1 = new TableBorderRow(1, 4, null);
        row1.getCells()[0] = cellA;
        row1.getCells()[1] = cellA;
        row1.getCells()[2] = cellD;
        row1.getCells()[3] = cellE;

        TableBorderCell cellF = new TableBorderCell(2, 0, 1, 1, null);
        addTextContent(cellF, "F");
        TableBorderCell cellG = new TableBorderCell(2, 1, 1, 1, null);
        addTextContent(cellG, "G");
        TableBorderCell cellH = new TableBorderCell(2, 2, 1, 1, null);
        addTextContent(cellH, "H");
        TableBorderCell cellI = new TableBorderCell(2, 3, 1, 1, null);
        addTextContent(cellI, "I");
        TableBorderRow row2 = new TableBorderRow(2, 4, null);
        row2.getCells()[0] = cellF;
        row2.getCells()[1] = cellG;
        row2.getCells()[2] = cellH;
        row2.getCells()[3] = cellI;

        TableBorder table = new TableBorder(null, new TableBorderRow[]{row0, row1, row2}, 3, 4);
        String markdown = generateMarkdownTable(table);
        String[] lines = markdown.split("\\n");

        assertTrue(lines.length >= 4, "Expected at least 4 lines, got: " + lines.length);
        assertEquals(2, countOccurrences(lines[0], "A"),
            "Row 0 should contain two 'A' cells from colspan=2. Got: " + lines[0]);
        assertEquals(2, countOccurrences(lines[2], "A"),
            "Row 1 should contain two 'A' cells from rowspan+colspan coverage. Got: " + lines[2]);
        assertTrue(lines[2].contains("D") && lines[2].contains("E"),
            "Row 1 should include D and E. Got: " + lines[2]);
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

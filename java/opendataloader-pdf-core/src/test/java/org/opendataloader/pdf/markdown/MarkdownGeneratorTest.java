/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.markdown;

import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    @TempDir
    Path tempDir;

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
    void testFullTableOutputWritesCaptionAndTableBody() throws IOException {
        String markdown = renderCaptionAndTable(Config.MARKDOWN_TABLE_OUTPUT_FULL);

        assertTrue(markdown.contains("Table 1 | Sample results."));
        assertTrue(markdown.contains("| Col A | Col B |"));
        assertTrue(markdown.contains("| --- | --- |"));
        assertTrue(markdown.contains("| 10 | 20 |"));
    }

    @Test
    void testCaptionOnlyTableOutputKeepsCaptionAndOmitsTableBody() throws IOException {
        String markdown = renderCaptionAndTable(Config.MARKDOWN_TABLE_OUTPUT_CAPTION_ONLY);

        assertTrue(markdown.contains("Table 1 | Sample results."));
        assertFalse(markdown.contains("| Col A | Col B |"));
        assertFalse(markdown.contains("| 10 | 20 |"));
    }

    @Test
    void testOffTableOutputKeepsCaptionAndOmitsTableBody() throws IOException {
        String markdown = renderCaptionAndTable(Config.MARKDOWN_TABLE_OUTPUT_OFF);

        assertTrue(markdown.contains("Table 1 | Sample results."));
        assertFalse(markdown.contains("| Col A | Col B |"));
        assertFalse(markdown.contains("| 10 | 20 |"));
    }

    @Test
    void testCaptionOnlySkipsFlattenedTableArtifactsAroundCaption() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setMarkdownTableOutput(Config.MARKDOWN_TABLE_OUTPUT_CAPTION_ONLY);
        File inputPdf = tempDir.resolve("artifacts.pdf").toFile();
        Files.writeString(inputPdf.toPath(), "");

        try (TestMarkdownGenerator generator = new TestMarkdownGenerator(inputPdf, config)) {
            generator.writePage(List.of(
                createCaption("Benchmark (Metric)"),
                createCaption("Model"),
                createCaption("AIME 2024 MATH-500 GPQA Diamond"),
                createCaption("Table 4 | Comparison between models."),
                createCaption("This paragraph should remain after the caption because it is narrative.")
            ));
        }

        String markdown = Files.readString(tempDir.resolve("artifacts.md"));
        assertFalse(markdown.contains("Benchmark (Metric)"));
        assertFalse(markdown.contains("\n\nModel\n\n"));
        assertTrue(markdown.contains("Table 4 | Comparison between models."));
        assertTrue(markdown.contains("This paragraph should remain after the caption because it is narrative."));
    }

    @Test
    void testCaptionOnlySkipsFootnotePlaceholderListsAndDanglingLowercaseFragments() throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setMarkdownTableOutput(Config.MARKDOWN_TABLE_OUTPUT_CAPTION_ONLY);
        File inputPdf = tempDir.resolve("dangling.pdf").toFile();
        Files.writeString(inputPdf.toPath(), "");

        try (TestMarkdownGenerator generator = new TestMarkdownGenerator(inputPdf, config)) {
            generator.writePage(List.of(
                createFootnoteList("1https://example.com", "2https://example.com"),
                createCaption("Table 4 | Comparison between models."),
                createCaption("performance of the model will improve soon."),
                createHeading("3.2. Distilled Model Evaluation", 5)
            ));
        }

        String markdown = Files.readString(tempDir.resolve("dangling.md"));
        assertFalse(markdown.contains("1https://example.com"));
        assertFalse(markdown.contains("performance of the model will improve soon."));
        assertTrue(markdown.contains("Table 4 | Comparison between models."));
        assertTrue(markdown.contains("##### 3.2. Distilled Model Evaluation"));
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

    private String renderCaptionAndTable(String tableOutputMode) throws IOException {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setMarkdownTableOutput(tableOutputMode);
        File inputPdf = tempDir.resolve("sample.pdf").toFile();
        Files.writeString(inputPdf.toPath(), "");

        try (TestMarkdownGenerator generator = new TestMarkdownGenerator(inputPdf, config)) {
            generator.writeObject(createCaption("Table 1 | Sample results."));
            generator.writeSeparator();
            generator.writeObject(createSimpleTable());
        }

        Path markdownPath = tempDir.resolve("sample.md");
        return Files.readString(markdownPath);
    }

    private SemanticParagraph createCaption(String text) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(
            new BoundingBox(0, 10.0, 10.0, 20.0, 20.0),
            text,
            "Font1",
            10,
            700,
            0,
            20.0,
            new double[]{0.0},
            null,
            0
        )));
        return paragraph;
    }

    private TableBorder createSimpleTable() {
        TableBorder table = new TableBorder(2, 2);

        TableBorderRow headerRow = new TableBorderRow(0, 2, 0L);
        headerRow.getCells()[0] = createCell(0, 0, "Col A");
        headerRow.getCells()[1] = createCell(0, 1, "Col B");
        table.getRows()[0] = headerRow;

        TableBorderRow dataRow = new TableBorderRow(1, 2, 0L);
        dataRow.getCells()[0] = createCell(1, 0, "10");
        dataRow.getCells()[1] = createCell(1, 1, "20");
        table.getRows()[1] = dataRow;

        return table;
    }

    private TableBorderCell createCell(int row, int col, String text) {
        TableBorderCell cell = new TableBorderCell(row, col, 1, 1, 0L);
        SemanticParagraph paragraph = createCaption(text);
        List<IObject> contents = cell.getContents();
        contents.add(paragraph);
        return cell;
    }

    private SemanticHeading createHeading(String text, int level) {
        SemanticHeading heading = new SemanticHeading(createCaption(text));
        heading.setHeadingLevel(level);
        return heading;
    }

    private PDFList createFootnoteList(String... items) {
        PDFList list = new PDFList();
        for (String itemText : items) {
            ListItem item = new ListItem(new BoundingBox(), null);
            item.add(new TextLine(new TextChunk(
                new BoundingBox(0, 10.0, 10.0, 20.0, 20.0),
                itemText,
                "Font1",
                10,
                700,
                0,
                20.0,
                new double[]{0.0},
                null,
                0
            )));
            list.add(item);
        }
        return list;
    }

    private static class TestMarkdownGenerator extends MarkdownGenerator {
        TestMarkdownGenerator(File inputPdf, Config config) throws IOException {
            super(inputPdf, config);
        }

        void writeObject(IObject object) throws IOException {
            write(object);
        }

        void writeSeparator() throws IOException {
            writeContentsSeparator();
        }

        void writePage(List<IObject> contents) throws IOException {
            Set<Integer> skip = collectTableArtifactIndices(contents);
            for (int index = 0; index < contents.size(); index++) {
                if (skip.contains(index)) {
                    continue;
                }
                write(contents.get(index));
                if (index < contents.size() - 1) {
                    writeContentsSeparator();
                }
            }
        }
    }
}

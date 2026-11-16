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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #936: in default mode, a word of the next column goes between the lines of a wrapped cell.
 *
 * <p>A table without lines has three columns: a number, a text, and a result. The text of a row can wrap to more
 * lines. Default mode does not detect the table, so each row becomes text. The first line of the text and the result
 * are on one baseline, so they become one line, and the other lines of the text come after it: the result is then
 * between the lines of the text. The expected output keeps the lines of the text together, before the result.
 *
 * <p>The samples copy the measurements of the reported layout: text in 6.65 pt; the numbers at x = 20.5, the texts at
 * x = 92, the results at x = 462; the lines of a text 9.6 pt apart, and the first line of a text 0.1 pt above the
 * baseline of its row; the next row 18 pt below a row of one line, and 15.6 pt below the last line of a wrapped text.
 * The page content draws each row in one of three orders. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-936}.
 */
class Issue936WrappedCellColumnWordTest {

    private static final float SIZE = 6.65f;
    private static final float NUMBER_X = 20.5f;
    private static final float TEXT_X = 92;
    private static final float RESULT_X = 462;
    private static final float ROW_DISTANCE = 18;
    private static final float LAST_LINE_TO_NEXT_ROW = 15.6f;
    private static final float LINE_DISTANCE = 9.6f;
    private static final float FIRST_LINE_RISE = 0.1f;
    private static final float TOP = 700;
    private static final String SYMPTOM = "a word of the next column is between the lines of a wrapped cell";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    /** The order in which the page content draws the parts of a row. */
    private enum Order {
        /** The result, the lines of the text, then the number: the order of the reported pages. */
        RIGHT_TO_LEFT,
        /** The number, the first line of the text, the result, then the other lines of the text. */
        RESULT_AFTER_FIRST_LINE,
        /** The number, the lines of the text, then the result. */
        LEFT_TO_RIGHT
    }

    private static final Row[] ROWS = {
        new Row("1", "Accepted", "Approve the plan of", "Pat Doe"),
        new Row("2", "Rejected", "Approve the budget"),
        new Row("3", "Deferred", "Ratify the auditor for", "the next year"),
        new Row("4", "Withdrawn", "Elect the members"),
        new Row("5", "Accepted in full", "Amend the rules of", "the committee")};

    private static final Row[] ROWS_OF_THREE_LINES = {
        new Row("1", "Accepted", "Approve the plan of", "Pat Doe and of", "Sam Roe"),
        new Row("2", "Rejected", "Approve the budget"),
        new Row("3", "Deferred", "Elect the members")};

    private static final Row[] ROWS_WITHOUT_WRAPPED_CELLS = {
        new Row("1", "Accepted", "Approve the plan"),
        new Row("2", "Rejected", "Approve the budget"),
        new Row("3", "Deferred", "Elect the members")};

    /** The page draws each row from the right, as the reported pages do: the lines of each text stay together. */
    @Test
    void wrappedCellsOfRowsDrawnFromTheRightKeepTheirLines() throws IOException {
        JsonNode document = convertJson("right-to-left", rowLines(ROWS, Order.RIGHT_TO_LEFT));

        assertCellsKeepTheirLines(document, ROWS);
    }

    /** The page draws the result after the first line of the text: the lines of each text stay together. */
    @Test
    void wrappedCellsWithTheResultBeforeTheirLastLinesKeepTheirLines() throws IOException {
        JsonNode document = convertJson("result-after-first-line", rowLines(ROWS, Order.RESULT_AFTER_FIRST_LINE));

        assertCellsKeepTheirLines(document, ROWS);
    }

    /** A text of three lines, in a row drawn from the right: its three lines stay together. */
    @Test
    void cellOfThreeLinesKeepsItsLines() throws IOException {
        JsonNode document = convertJson("three-lines", rowLines(ROWS_OF_THREE_LINES, Order.RIGHT_TO_LEFT));

        assertCellsKeepTheirLines(document, ROWS_OF_THREE_LINES);
    }

    /**
     * A row with two wrapped cells, a name of three lines in the first column and a text of two lines in the second.
     * The page draws the first lines of the row, then the other lines of the name, then the other line of the text:
     * the lines of each cell stay together, and the result comes after them.
     */
    @Test
    void twoWrappedCellsOfARowKeepTheirLines() throws IOException {
        List<Line> lines = new ArrayList<>();
        float row = TOP;
        lines.add(new Line(NUMBER_X, row, "Item"));
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE, "Approve the plan of"));
        lines.add(new Line(RESULT_X, row, "Accepted"));
        lines.add(new Line(NUMBER_X, row - LINE_DISTANCE, "number"));
        lines.add(new Line(NUMBER_X, row - 2 * LINE_DISTANCE, "one"));
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE - LINE_DISTANCE, "Pat Doe"));
        row -= 2 * LINE_DISTANCE + LAST_LINE_TO_NEXT_ROW;
        lines.add(new Line(NUMBER_X, row, "Next"));
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE, "Approve the budget"));
        lines.add(new Line(RESULT_X, row, "Rejected"));
        JsonNode document = convertJson("two-wrapped-cells", lines);

        String text = documentText(document);
        List<String> contents = contents(document);
        assertTrue(contents.stream().anyMatch(content -> content.contains("Item number one"))
                && contents.stream().anyMatch(content -> content.contains("Approve the plan of Pat Doe")),
            SYMPTOM + " (the lines of a cell are not together): " + text);
        int result = text.indexOf("Accepted");
        assertTrue(result > text.indexOf("Pat Doe") && result < text.indexOf("Approve the budget"),
            SYMPTOM + " (the result is not between the cells and the next row): " + text);
    }

    /**
     * The first row of a table, with a name of four lines and a text of two lines. The page draws the first lines of
     * the row, then the other lines of the name, then the other line of the text. The next row is below the name, more
     * than four font sizes below the second line of the text: the lines of the text stay together too.
     */
    @Test
    void wrappedCellBesideALongerWrappedCellKeepsItsLines() throws IOException {
        List<Line> lines = new ArrayList<>();
        float row = TOP;
        String[] name = {"Item", "one", "of", "four"};
        lines.add(new Line(NUMBER_X, row, name[0]));
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE, "Approve the plan of"));
        lines.add(new Line(RESULT_X, row, "Accepted"));
        for (int i = 1; i < name.length; i++) {
            lines.add(new Line(NUMBER_X, row - i * LINE_DISTANCE, name[i]));
        }
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE - LINE_DISTANCE, "Pat Doe"));
        row -= (name.length - 1) * LINE_DISTANCE + LAST_LINE_TO_NEXT_ROW;
        lines.add(new Line(NUMBER_X, row, "Next"));
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE, "Approve the budget"));
        lines.add(new Line(RESULT_X, row, "Rejected"));
        JsonNode document = convertJson("longer-wrapped-cell", lines);

        String text = documentText(document);
        assertTrue(contents(document).stream().anyMatch(content -> content.contains("Approve the plan of Pat Doe")),
            SYMPTOM + " (the lines of the text are not together): " + text);
        int result = text.indexOf("Accepted");
        assertTrue(result > text.indexOf("Pat Doe") && result < text.indexOf("Approve the budget"),
            SYMPTOM + " (the result is not between the cells and the next row): " + text);
    }

    /** Guard: the page draws the result after all the lines of the text. The base keeps this order. */
    @Test
    void wrappedCellsDrawnFromTheLeftStayInOrder() throws IOException {
        JsonNode document = convertJson("left-to-right", rowLines(ROWS, Order.LEFT_TO_RIGHT));

        assertCellsKeepTheirLines(document, ROWS);
    }

    /** Guard: the result wraps, in the last column. Its second line comes after its first line. */
    @Test
    void wrappedResultInTheLastColumnStaysInOrder() throws IOException {
        List<Line> lines = new ArrayList<>();
        float row = TOP;
        lines.add(new Line(NUMBER_X, row, "1"));
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE, "Approve the plan"));
        lines.add(new Line(RESULT_X, row, "Accepted in"));
        lines.add(new Line(RESULT_X, row - LINE_DISTANCE, "full"));
        row -= LINE_DISTANCE + LAST_LINE_TO_NEXT_ROW;
        lines.add(new Line(NUMBER_X, row, "2"));
        lines.add(new Line(TEXT_X, row + FIRST_LINE_RISE, "Approve the budget"));
        lines.add(new Line(RESULT_X, row, "Rejected"));
        JsonNode document = convertJson("wrapped-result", lines);

        String text = documentText(document);
        int first = text.indexOf("Accepted in");
        int second = text.indexOf("full", first);
        assertTrue(first >= 0 && second > first && text.indexOf("Approve the budget") > second,
            "the lines of the result are not in order: " + text);
    }

    /** Guard: rows without wrapped cells stay one element each, with the number, the text, and the result. */
    @Test
    void rowsWithoutWrappedCellsStayOneElementEach() throws IOException {
        for (Order order : Order.values()) {
            JsonNode document = convertJson("no-wrap-" + order.name().toLowerCase().replace('_', '-'),
                rowLines(ROWS_WITHOUT_WRAPPED_CELLS, order));

            List<String> contents = contents(document);
            for (Row row : ROWS_WITHOUT_WRAPPED_CELLS) {
                String expected = row.number + " " + row.lines[0] + " " + row.result;
                assertTrue(contents.contains(expected),
                    "row " + row.number + " is not one element (" + order + "): " + contents);
            }
        }
    }

    /**
     * Guard: one row of a form, with a gap between its two parts, and a line below its left part. No other row has
     * these columns, so this is not a table: the line below stays after the row.
     */
    @Test
    void lineBelowTheLeftPartOfOneFormRowStaysAfterTheRow() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(TEXT_X, TOP, "Name: Pat Doe"));
        lines.add(new Line(RESULT_X, TOP, "Date: 01/02/2025"));
        lines.add(new Line(TEXT_X, TOP - LINE_DISTANCE, "Address: 1 Main Street"));
        JsonNode document = convertJson("form-row", lines);

        String text = documentText(document);
        int date = text.indexOf("Date: 01/02/2025");
        int address = text.indexOf("Address: 1 Main Street");
        assertTrue(date >= 0 && address > date, "the line below the form row moved: " + text);
    }

    /**
     * Each text is one run of its lines in one element, with one space between them. Its result comes after its
     * last line, and before the text of the next row.
     */
    private static void assertCellsKeepTheirLines(JsonNode document, Row[] rows) {
        String text = documentText(document);
        int[] starts = new int[rows.length + 1];
        int previous = -1;
        for (int i = 0; i < rows.length; i++) {
            String cell = String.join(" ", rows[i].lines);
            boolean inOneElement = false;
            for (String content : contents(document)) {
                inOneElement |= content.contains(cell);
            }
            assertTrue(inOneElement, SYMPTOM + " (the text of row " + rows[i].number + " is not \"" + cell + "\"): "
                + text);
            starts[i] = text.indexOf(cell, previous + 1);
            assertTrue(starts[i] > previous, SYMPTOM + " (row " + rows[i].number + " is out of order): " + text);
            previous = starts[i];
        }
        starts[rows.length] = text.length();
        for (int i = 0; i < rows.length; i++) {
            int result = text.indexOf(rows[i].result, starts[i] + String.join(" ", rows[i].lines).length());
            assertTrue(result >= 0 && result < starts[i + 1], SYMPTOM + " (the result of row " + rows[i].number
                + " is not between its text and the next row): " + text);
        }
    }

    /** The lines of the rows, in the order of the page content. */
    private static List<Line> rowLines(Row[] rows, Order order) {
        List<Line> lines = new ArrayList<>();
        float row = TOP;
        for (Row r : rows) {
            Line number = new Line(NUMBER_X, row, r.number);
            Line result = new Line(RESULT_X, row, r.result);
            List<Line> text = new ArrayList<>();
            for (int i = 0; i < r.lines.length; i++) {
                text.add(new Line(TEXT_X, row + FIRST_LINE_RISE - i * LINE_DISTANCE, r.lines[i]));
            }
            switch (order) {
                case RIGHT_TO_LEFT:
                    lines.add(result);
                    lines.addAll(text);
                    lines.add(number);
                    break;
                case RESULT_AFTER_FIRST_LINE:
                    lines.add(number);
                    lines.add(text.get(0));
                    lines.add(result);
                    lines.addAll(text.subList(1, text.size()));
                    break;
                default:
                    lines.add(number);
                    lines.addAll(text);
                    lines.add(result);
                    break;
            }
            row -= r.lines.length == 1 ? ROW_DISTANCE : (r.lines.length - 1) * LINE_DISTANCE + LAST_LINE_TO_NEXT_ROW;
        }
        return lines;
    }

    /** The own content of each element, in the order of the document. */
    private static List<String> contents(JsonNode document) {
        List<String> contents = new ArrayList<>();
        addContents(document.path("kids"), contents);
        return contents;
    }

    private static void addContents(JsonNode nodes, List<String> contents) {
        for (JsonNode node : nodes) {
            if (node.has("content")) {
                contents.add(node.path("content").asText());
            }
            addContents(node.path("kids"), contents);
            addContents(node.path("list items"), contents);
        }
    }

    /** The contents of the elements, in the order of the document, one in each line. */
    private static String documentText(JsonNode document) {
        return String.join("\n", contents(document));
    }

    private static JsonNode convertJson(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-936-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    content.beginText();
                    content.setFont(REGULAR, SIZE);
                    content.newLineAtOffset(line.x, line.baseline);
                    content.showText(line.text);
                    content.endText();
                }
            }
            document.save(pdf.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    /** A row: its number, its result, and the lines of its text. */
    private static final class Row {
        final String number;
        final String result;
        final String[] lines;

        Row(String number, String result, String... lines) {
            this.number = number;
            this.result = result;
            this.lines = lines;
        }
    }

    /** A line of text at its place. */
    private static final class Line {
        final float x;
        final float baseline;
        final String text;

        Line(float x, float baseline, String text) {
            this.x = x;
            this.baseline = baseline;
            this.text = text;
        }
    }
}

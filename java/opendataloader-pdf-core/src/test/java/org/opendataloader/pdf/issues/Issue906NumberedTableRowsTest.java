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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #906: a legend table drawn by columns is read column by column.
 *
 * <p>A legend below a figure is a table without lines: in each row a number, a name, and a description. The page
 * content draws it column by column: all the names, then all the descriptions, then all the numbers. Each cell is
 * then a line of its own, and the lines of each column become one paragraph or one list: the output has all the
 * descriptions, then all the numbers, then all the names. The expected output has one element for each row, with
 * its number, its name, and its description, in the order of the numbers.
 *
 * <p>The samples have this layout: text in 8 pt; the numbers centered at x = 76, the names at x = 88, the
 * descriptions at x = 200, and the rows 14 pt apart. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-906}.
 */
class Issue906NumberedTableRowsTest {

    private static final float SIZE = 8;
    private static final float NUMBER_CENTER = 76;
    private static final float NAME_X = 88;
    private static final float DESCRIPTION_X = 200;
    private static final float ROW_DISTANCE = 14;
    private static final float LINE_DISTANCE = 11;
    private static final float TOP = 700;
    private static final String SYMPTOM = "the table is read column by column";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final String[] NAMES = {"Clock", "Mode button", "More button", "Rinse water", "Pure water",
        "Cold water", "Hot water", "Alkaline water", "Cold alkaline water", "Amount button"};
    private static final String[] DESCRIPTIONS = {"Touch to set the clock.", "Touch to change the mode.",
        "Touch to set the functions.", "Touch to let rinse water out.", "Touch, then touch the out button.",
        "Touch, then touch the out button again.", "Touch again to change the heat.", "Touch again to change the pH.",
        "Touch again to change the pH level.", "Touch for two seconds to set the amount."};

    /** A legend of ten rows drawn by columns, with centered numbers: one element for each row, in order. */
    @Test
    void rowsOfALegendDrawnByColumnsAreOneElementEach() throws IOException {
        List<Line> lines = new ArrayList<>();
        for (int i = 0; i < NAMES.length; i++) {
            lines.add(new Line(NAME_X, TOP - i * ROW_DISTANCE, true, NAMES[i]));
        }
        for (int i = 0; i < DESCRIPTIONS.length; i++) {
            lines.add(new Line(DESCRIPTION_X, TOP - i * ROW_DISTANCE, false, DESCRIPTIONS[i]));
        }
        for (int i = 0; i < NAMES.length; i++) {
            lines.add(Line.centered(NUMBER_CENTER, TOP - i * ROW_DISTANCE, true, String.valueOf(i + 1)));
        }
        JsonNode document = convertJson("legend", lines);

        int previous = -1;
        for (int i = 0; i < NAMES.length; i++) {
            JsonNode element = elementThatContains(document, NAMES[i]);
            assertNotNull(element, "the name of row " + (i + 1) + " is lost: " + document);
            String content = element.path("content").asText();
            assertTrue(content.startsWith((i + 1) + " ") && content.contains(DESCRIPTIONS[i]),
                SYMPTOM + " (row " + (i + 1) + " is not one element): " + document);
            int index = document.toString().indexOf(NAMES[i]);
            assertTrue(index > previous, SYMPTOM + " (row " + (i + 1) + " is out of order): " + document);
            previous = index;
        }
    }

    /**
     * A legend drawn by columns whose description of row 2 has two lines: one element for each row, and the second line
     * of the description between row 2 and row 3.
     */
    @Test
    void rowWithADescriptionOfTwoLinesStaysBeforeTheNextRow() throws IOException {
        List<Line> lines = new ArrayList<>();
        float[] rows = {TOP, TOP - ROW_DISTANCE, TOP - 2 * ROW_DISTANCE - LINE_DISTANCE};
        for (int i = 0; i < rows.length; i++) {
            lines.add(new Line(NAME_X, rows[i], true, NAMES[i]));
        }
        for (int i = 0; i < rows.length; i++) {
            lines.add(new Line(DESCRIPTION_X, rows[i], false, DESCRIPTIONS[i]));
        }
        lines.add(new Line(DESCRIPTION_X, rows[1] - LINE_DISTANCE, false, "The mode stays until you change it."));
        for (int i = 0; i < rows.length; i++) {
            lines.add(Line.centered(NUMBER_CENTER, rows[i], true, String.valueOf(i + 1)));
        }
        JsonNode document = convertJson("two-line-cell", lines);

        for (int i = 0; i < rows.length; i++) {
            JsonNode element = elementThatContains(document, NAMES[i]);
            assertNotNull(element, "the name of row " + (i + 1) + " is lost: " + document);
            String content = element.path("content").asText();
            assertTrue(content.startsWith((i + 1) + " ") && content.contains(DESCRIPTIONS[i]),
                SYMPTOM + " (row " + (i + 1) + " is not one element): " + document);
        }
        String text = document.toString();
        assertTrue(text.indexOf(NAMES[1]) < text.indexOf("The mode stays")
                && text.indexOf("The mode stays") < text.indexOf(NAMES[2]),
            SYMPTOM + " (the second line of row 2 is not between row 2 and row 3): " + document);
    }

    /**
     * Guard: on a page with two columns, the text of the right column, which goes on above and below the rows of a
     * numbered list of the left column, is not in the rows of that list.
     */
    @Test
    void textOfAnotherColumnIsNotInTheRows() throws IOException {
        List<Line> lines = new ArrayList<>();
        String[] items = {"Read all the steps first.", "Put the parts on a table.", "Keep the parts dry."};
        for (int i = 0; i < items.length; i++) {
            lines.add(new Line(NAME_X, TOP - i * ROW_DISTANCE, false, items[i]));
        }
        for (int i = -2; i < items.length + 2; i++) {
            lines.add(new Line(310, TOP - i * ROW_DISTANCE, false, "The right column has a text in this line."));
        }
        for (int i = 0; i < items.length; i++) {
            lines.add(Line.centered(NUMBER_CENTER, TOP - i * ROW_DISTANCE, false, String.valueOf(i + 1)));
        }
        JsonNode document = convertJson("two-columns", lines);

        for (String item : items) {
            JsonNode element = elementThatContains(document, item);
            assertNotNull(element, "the item is lost (" + item + "): " + document);
            assertTrue(!element.path("content").asText().contains("The right column"),
                "the text of the right column is in a row (" + item + "): " + document);
        }
    }

    /** Guard: a numbered list whose lines start with their numbers stays one list. */
    @Test
    void numberedListOfLinesStaysOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        String[] items = {"1. Read all the steps first.", "2. Put the parts on a table.", "3. Keep the parts dry."};
        for (int i = 0; i < items.length; i++) {
            lines.add(new Line(NAME_X, TOP - i * ROW_DISTANCE, false, items[i]));
        }
        JsonNode document = convertJson("numbered-list", lines);

        JsonNode list = listWithItem(document, items[0]);
        assertNotNull(list, "the list is lost: " + document);
        for (String item : items) {
            assertSame(list, listWithItem(document, item), "the list is split at " + item + ": " + document);
        }
    }

    /** The deepest element whose own content contains the text, or null. */
    private static JsonNode elementThatContains(JsonNode document, String text) {
        List<JsonNode> stack = new ArrayList<>();
        document.path("kids").forEach(stack::add);
        JsonNode found = null;
        while (!stack.isEmpty()) {
            JsonNode node = stack.remove(stack.size() - 1);
            if (node.path("content").asText().contains(text)) {
                found = node;
            }
            node.path("kids").forEach(stack::add);
            node.path("list items").forEach(stack::add);
        }
        return found;
    }

    /** The innermost list with an item whose own content contains the text, or null. */
    private static JsonNode listWithItem(JsonNode document, String text) {
        List<JsonNode> stack = new ArrayList<>();
        document.path("kids").forEach(stack::add);
        JsonNode found = null;
        while (!stack.isEmpty()) {
            JsonNode node = stack.remove(stack.size() - 1);
            for (JsonNode item : node.path("list items")) {
                if (item.path("content").asText().contains(text)) {
                    found = node;
                }
                stack.add(item);
            }
            node.path("kids").forEach(stack::add);
        }
        return found;
    }

    private static JsonNode convertJson(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-906-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    PDType1Font font = line.bold ? BOLD : REGULAR;
                    float x = line.x;
                    if (line.centered) {
                        x -= font.getStringWidth(line.text) / 1000 * SIZE / 2;
                    }
                    content.beginText();
                    content.setFont(font, SIZE);
                    content.newLineAtOffset(x, line.baseline);
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

    /** A line at its place, in the bold or the regular font; a centered line has its middle at x. */
    private static final class Line {
        final float x;
        final float baseline;
        final boolean bold;
        final String text;
        final boolean centered;

        Line(float x, float baseline, boolean bold, String text) {
            this(x, baseline, bold, text, false);
        }

        private Line(float x, float baseline, boolean bold, String text, boolean centered) {
            this.x = x;
            this.baseline = baseline;
            this.bold = bold;
            this.text = text;
            this.centered = centered;
        }

        static Line centered(float x, float baseline, boolean bold, String text) {
            return new Line(x, baseline, bold, text, true);
        }
    }
}

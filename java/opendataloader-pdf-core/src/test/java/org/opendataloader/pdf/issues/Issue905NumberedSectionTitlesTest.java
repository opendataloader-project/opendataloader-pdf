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
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #905: numbered section titles become one list and move before the text of the first section.
 *
 * <p>A page has two section titles numbered "1)" and "2)", each with text below it, at the left edge of the text.
 * The content stream draws the two titles one after the other, and the text of the sections after them. The list
 * detection makes one list of the two titles: the first title cannot take the text below it, because that text is
 * not between the two titles in the content order. The list then comes before the text of the first section. The
 * expected output has each title before the text of its section, and the titles are not one list.
 *
 * <p>The samples have this layout: text in 9 pt, titles in bold 10 pt, at x = 72 (a second column at x = 310), and
 * the lines 14 pt apart. The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names
 * start with {@code issue-905}.
 */
class Issue905NumberedSectionTitlesTest {

    private static final float SIZE = 9;
    private static final float TITLE_SIZE = 10;
    private static final float LEFT = 72;
    private static final float RIGHT = 310;
    private static final float LINE_DISTANCE = 14;
    private static final String SYMPTOM = "the numbered section titles are one list";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * Two numbered titles drawn first, and the text of each section after them: each title comes before the text of
     * its section, and the titles are not one list.
     */
    @Test
    void numberedTitlesDrawnFirstComeBeforeTheirText() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LEFT, 760, TITLE_SIZE, true, "1) Daily checks"));
        lines.add(new Line(LEFT, 620, TITLE_SIZE, true, "2) Cleaning"));
        lines.add(new Line(LEFT, 730, SIZE, true, "Q1. Did you use the product in the last week?"));
        lines.add(new Line(LEFT, 716, SIZE, false, "If not, let the water run for one minute first."));
        lines.add(new Line(LEFT, 690, SIZE, true, "Q2. Is the tray of the product empty?"));
        lines.add(new Line(LEFT, 676, SIZE, false, "Empty the tray each day, so that it does not run over."));
        lines.add(new Line(LEFT, 590, SIZE, false, "Clean the body of the product with a soft and dry cloth."));
        lines.add(new Line(LEFT, 576, SIZE, false, "Do not use water or soap on the body of the product."));
        JsonNode document = convertJson("titles-first", Arrays.asList(lines));

        assertBefore(document, "1) Daily checks", "Q1. Did you use");
        assertBefore(document, "Empty the tray", "2) Cleaning");
        assertBefore(document, "2) Cleaning", "Clean the body");
        JsonNode list = listWithItem(document, "1) Daily checks");
        assertTrue(list == null || !list.toString().contains("2) Cleaning"), SYMPTOM + ": " + document);
    }

    /**
     * Four numbered titles drawn first, and text only between the titles (2) and (3): the title (3) comes after that
     * text, and the lists of the titles before and after it are not one list again.
     */
    @Test
    void numberedTitlesWithTextBetweenTwoOfThemComeBeforeAndAfterIt() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LEFT, 760, TITLE_SIZE, true, "1) Daily checks"));
        lines.add(new Line(LEFT, 740, TITLE_SIZE, true, "2) Weekly checks"));
        lines.add(new Line(LEFT, 640, TITLE_SIZE, true, "3) Cleaning"));
        lines.add(new Line(LEFT, 620, TITLE_SIZE, true, "4) Storage"));
        lines.add(new Line(LEFT, 710, SIZE, false, "Look at the filter and at the tray of the product each week."));
        lines.add(new Line(LEFT, 696, SIZE, false, "Change the filter when the display shows the filter icon."));
        lines.add(new Line(LEFT, 682, SIZE, false, "Empty the tray, so that it does not run over."));
        JsonNode document = convertJson("four-titles", Arrays.asList(lines));

        assertBefore(document, "2) Weekly checks", "Look at the filter");
        assertBefore(document, "so that it does not run over", "3) Cleaning");
    }

    /** Guard: a numbered list with a caption that the content stream draws between two items stays one list. */
    @Test
    void numberedListWithACaptionDrawnBetweenItemsStaysOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LEFT, 760, TITLE_SIZE, true, "Exercises"));
        lines.add(new Line(LEFT, 742, SIZE, false, "1. Find the length of the side of the box."));
        lines.add(new Line(LEFT, 728, SIZE, false, "2. Find the area of the top of the box."));
        lines.add(new Line(LEFT, 714, SIZE, false, "3. Find the volume of the box in the figure."));
        lines.add(new Line(LEFT, 688, SIZE, false, "Figure 2 for exercise 3."));
        lines.add(new Line(LEFT, 662, SIZE, false, "4. Find the mass of the box when it is full."));
        JsonNode document = convertJson("caption", Arrays.asList(lines));

        assertOneList(document, "1. Find the length", "2. Find the area", "3. Find the volume", "4. Find the mass");
    }

    /** Guard: a numbered list whose items go on in lines at the numbers, drawn in their order, stays one list. */
    @Test
    void numberedListWithLinesAtTheNumbersStaysOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LEFT, 760, TITLE_SIZE, true, "Before you start"));
        lines.add(new Line(LEFT, 742, SIZE, false, "1. Read all the steps before you start the work, and keep"));
        lines.add(new Line(LEFT, 728, SIZE, false, "this paper near you."));
        lines.add(new Line(LEFT, 714, SIZE, false, "2. Put the parts on a clean table, in the order of the list"));
        lines.add(new Line(LEFT, 700, SIZE, false, "on the box."));
        lines.add(new Line(LEFT, 686, SIZE, false, "3. Keep the small parts away from children."));
        JsonNode document = convertJson("lines-at-numbers", Arrays.asList(lines));

        assertOneList(document, "1. Read all the steps", "2. Put the parts", "3. Keep the small parts");
    }

    /**
     * Guard: a numbered list in the left column of a page with two columns, where the content stream draws the text of
     * the right column after the list, stays one list.
     */
    @Test
    void numberedListBesideTheTextOfAnotherColumnStaysOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(LEFT, 742, SIZE, false, "1. Read all the steps first."));
        lines.add(new Line(LEFT, 728, SIZE, false, "2. Put the parts on a table."));
        lines.add(new Line(LEFT, 714, SIZE, false, "3. Keep the parts dry."));
        for (int i = 0; i < 6; i++) {
            lines.add(new Line(RIGHT, 742 - i * LINE_DISTANCE / 2, SIZE, false, "The right column has a text in it."));
        }
        JsonNode document = convertJson("two-columns", Arrays.asList(lines));

        assertOneList(document, "1. Read all the steps", "2. Put the parts", "3. Keep the parts dry");
    }

    /** Asserts that one list has an item for each text, in its own content. */
    private static void assertOneList(JsonNode document, String... items) {
        JsonNode list = listWithItem(document, items[0]);
        assertNotNull(list, "the list is lost: " + document);
        for (String item : items) {
            assertSame(list, listWithItem(document, item), "the list is split at " + item + ": " + document);
        }
    }

    /** Asserts that the first text comes before the second text in the output. */
    private static void assertBefore(JsonNode document, String first, String second) {
        String text = document.toString();
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue(firstIndex >= 0 && secondIndex >= 0, "a text is lost (" + first + ", " + second + "): " + document);
        assertTrue(firstIndex < secondIndex, SYMPTOM + " (" + first + " after " + second + "): " + document);
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

    private static JsonNode convertJson(String name, List<List<Line>> pages) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-905-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (List<Line> lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    for (Line line : lines) {
                        content.beginText();
                        content.setFont(line.bold ? BOLD : REGULAR, line.size);
                        content.newLineAtOffset(line.x, line.baseline);
                        content.showText(line.text);
                        content.endText();
                    }
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

    /** A line at its place, in the bold or the regular font. */
    private static final class Line {
        final float x;
        final float baseline;
        final float size;
        final boolean bold;
        final String text;

        Line(float x, float baseline, float size, boolean bold, String text) {
            this.x = x;
            this.baseline = baseline;
            this.size = size;
            this.bold = bold;
            this.text = text;
        }
    }
}

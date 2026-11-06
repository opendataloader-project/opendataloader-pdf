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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #354: the bullets of two sections are one list, and the title of the second section is outside its place.
 *
 * <p>A page has sections, each a title and bullets below it. The list detection joins a bullet to the list of the
 * bullet above it in the same column. It does not look at the lines between them, so the bullets below the title of
 * the next section go on in the list of the section above. The title stays out of the list, so it comes after the
 * list, or inside an item of it. The expected output has one list for the bullets of each section and column, and
 * each title before the list of its section.
 *
 * <p>The samples have this layout: text in 9 pt, titles in bold; bullets at x = 72 (a second column at x = 310),
 * their text at x = 80, and the lines 14 pt apart; a title 32 pt below the bullets above it and 18 pt above the
 * bullets below it. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-354}.
 */
class Issue354BulletsAcrossSectionTitlesTest {

    private static final float SIZE = 9;
    private static final float TITLE_SIZE = 10;
    private static final float LEFT = 72;
    private static final float RIGHT = 310;
    private static final float TEXT_INDENT = 8;
    private static final float LINE_DISTANCE = 14;
    private static final float TITLE_ABOVE = 32;
    private static final float TITLE_BELOW = 18;
    private static final String SYMPTOM = "the bullets of two sections are one list";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** One column, two sections: one list for the bullets of each section, and the second title between them. */
    @Test
    void bulletsOfTwoSectionsAreTwoLists() throws IOException {
        List<Line> lines = new ArrayList<>();
        float y = 760;
        lines.add(title(LEFT, y, "Before you start"));
        y = bullets(lines, LEFT, y - 18, "Read all the steps before you start the work.",
            "Put the parts on a clean table.", "Keep the small parts away from children.");
        lines.add(title(LEFT, y - TITLE_ABOVE, "While you work"));
        bullets(lines, LEFT, y - TITLE_ABOVE - TITLE_BELOW, "Turn off the power before you open the cover.",
            "Use only the tools that come with the parts.", "Do not use force to put a part in its place.");
        JsonNode document = convertJson("one-column", Arrays.asList(lines));

        assertSectionLists(document, "Read all the steps", "Keep the small parts", "Turn off the power",
            "Do not use force");
        assertFalse(isInList(document, "While you work"), "the title of the second section is in a list: " + document);
        assertBefore(document, "Keep the small parts", "While you work");
        assertBefore(document, "While you work", "Turn off the power");
    }

    /**
     * Two columns, two sections, with the titles drawn first in the page content: one list for each section and
     * column, and the second title between the lists of its column.
     */
    @Test
    void bulletsOfTwoSectionsInTwoColumnsAreFourLists() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        lines.add(title(LEFT, 660, "While you work"));
        bullets(lines, LEFT, 742, "Read all the steps first.", "Put the parts on a table.", "Keep the parts dry.");
        bullets(lines, RIGHT, 742, "Check that no part is lost.", "Clean the table.", "Find the tools.");
        bullets(lines, LEFT, 642, "Turn off the power.", "Use the tools of the box.", "Do not use force.");
        bullets(lines, RIGHT, 642, "Work in a dry room.", "Keep the cover closed.", "Stop if you see smoke.");
        JsonNode document = convertJson("two-columns", Arrays.asList(lines));

        assertSectionLists(document, "Read all the steps", "Keep the parts dry", "Turn off the power",
            "Do not use force");
        assertSectionLists(document, "Check that no part", "Find the tools", "Work in a dry room",
            "Stop if you see smoke");
        assertFalse(isInList(document, "While you work"), "the title of the second section is in a list: " + document);
        assertBefore(document, "Keep the parts dry", "While you work");
        assertBefore(document, "While you work", "Turn off the power");
    }

    /** A subtitle at the bullets between two groups of bullets is a paragraph between two lists, not in an item. */
    @Test
    void subtitleBetweenBulletsIsNotInTheList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Main rules:"));
        float y = bullets(lines, LEFT, 742, "Tag each element.", "Define the reading order.",
            "Give each figure a text.");
        lines.add(title(LEFT, y - TITLE_ABOVE, "More rules:"));
        bullets(lines, LEFT, y - TITLE_ABOVE - TITLE_BELOW, "Use Unicode for each glyph.", "Show the document title.");
        JsonNode document = convertJson("subtitle", Arrays.asList(lines));

        assertFalse(isInList(document, "More rules:"), SYMPTOM + " (the subtitle is in a list): " + document);
        assertSectionLists(document, "Tag each element", "Give each figure", "Use Unicode", "Show the document");
    }

    /** A section with one bullet: the bullet does not go on in the list of the section above. */
    @Test
    void bulletOfASectionWithOneBulletIsNotInTheListAbove() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        float y = bullets(lines, LEFT, 742, "Read all the steps first.", "Put the parts on a table.",
            "Keep the parts dry.");
        lines.add(title(LEFT, y - TITLE_ABOVE, "After the work"));
        bullets(lines, LEFT, y - TITLE_ABOVE - TITLE_BELOW, "Take the old parts to a collection point.");
        JsonNode document = convertJson("one-bullet", Arrays.asList(lines));

        JsonNode list = listWithItem(document, "Read all the steps");
        assertNotNull(list, "the list of the first section is lost: " + document);
        assertFalse(list.toString().contains("Take the old parts"),
            SYMPTOM + " (the bullet of the next section): " + document);
        assertBefore(document, "After the work", "Take the old parts");
    }

    /**
     * One column, two sections, with the titles drawn first in the page content: one list for the bullets of each
     * section, and the second title between them.
     */
    @Test
    void bulletsOfTwoSectionsInOneColumnWithTitlesDrawnFirstAreTwoLists() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        lines.add(title(LEFT, 660, "While you work"));
        bullets(lines, LEFT, 742, "Read all the steps first.", "Put the parts on a table.", "Keep the parts dry.");
        bullets(lines, LEFT, 642, "Turn off the power.", "Use the tools of the box.", "Do not use force.");
        JsonNode document = convertJson("one-column-titles-first", Arrays.asList(lines));

        assertSectionLists(document, "Read all the steps", "Keep the parts dry", "Turn off the power",
            "Do not use force");
        assertBefore(document, "Keep the parts dry", "While you work");
        assertBefore(document, "While you work", "Turn off the power");
    }

    /** Titles in the font size of the bullets, only in bold: one list for the bullets of each section. */
    @Test
    void bulletsOfTwoSectionsWithBoldTitlesInTheirSizeAreTwoLists() throws IOException {
        List<Line> lines = new ArrayList<>();
        float y = 760;
        lines.add(new Line(LEFT, y, new Run("Before you start", true)));
        y = bullets(lines, LEFT, y - TITLE_BELOW, "Read all the steps first.", "Put the parts on a table.",
            "Keep the parts dry.");
        lines.add(new Line(LEFT, y - TITLE_ABOVE, new Run("While you work", true)));
        bullets(lines, LEFT, y - TITLE_ABOVE - TITLE_BELOW, "Turn off the power.", "Use the tools of the box.",
            "Do not use force.");
        JsonNode document = convertJson("bold-titles", Arrays.asList(lines));

        assertSectionLists(document, "Read all the steps", "Keep the parts dry", "Turn off the power",
            "Do not use force");
        assertBefore(document, "Keep the parts dry", "While you work");
        assertBefore(document, "While you work", "Turn off the power");
    }

    /**
     * Two sections with one bullet each, with the titles drawn first in the page content: the two bullets are not one
     * list, and the second title is between them.
     */
    @Test
    void bulletsOfTwoSectionsWithOneBulletEachAreNotOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        lines.add(title(LEFT, 760 - TITLE_BELOW - TITLE_ABOVE, "After the work"));
        lines.add(bullet(LEFT, 760 - TITLE_BELOW, "Read all the steps first."));
        lines.add(bullet(LEFT, 760 - 2 * TITLE_BELOW - TITLE_ABOVE, "Take the old parts to a collection point."));
        JsonNode document = convertJson("one-bullet-each", Arrays.asList(lines));

        JsonNode list = listWithItem(document, "Read all the steps");
        assertTrue(list == null || !list.toString().contains("Take the old parts"),
            SYMPTOM + " (one bullet in each section): " + document);
        assertBefore(document, "Read all the steps", "After the work");
        assertBefore(document, "After the work", "Take the old parts");
    }

    /** Guard: one list without titles, whose items have lines that start at the text of the item, stays one list. */
    @Test
    void bulletsWithWrappedLinesStayOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        lines.add(bullet(LEFT, 742, "Read all the steps before you start the work, and keep"));
        lines.add(new Line(LEFT + TEXT_INDENT, 728, new Run("this paper near you.", false)));
        lines.add(bullet(LEFT, 714, "Put the parts on a clean table, in the order of the list"));
        lines.add(new Line(LEFT + TEXT_INDENT, 700, new Run("on the box.", false)));
        lines.add(bullet(LEFT, 686, "Keep the small parts away from children."));
        JsonNode document = convertJson("wrapped-lines", Arrays.asList(lines));

        assertSame(listWithItem(document, "Read all the steps"), listWithItem(document, "Keep the small parts"),
            "the list is split: " + document);
        assertNotNull(listWithItem(document, "Read all the steps"), "the list is lost: " + document);
    }

    /** Guard: a list whose items go on in lines that start at the bullets stays one list. */
    @Test
    void bulletsWithLinesAtTheBulletStayOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        lines.add(bullet(LEFT, 742, "Read all the steps before you start the work, and keep"));
        lines.add(new Line(LEFT, 728, new Run("this paper near you.", false)));
        lines.add(bullet(LEFT, 714, "Put the parts on a clean table, in the order of the list"));
        lines.add(new Line(LEFT, 700, new Run("on the box.", false)));
        lines.add(bullet(LEFT, 686, "Keep the small parts away from children."));
        JsonNode document = convertJson("lines-at-bullet", Arrays.asList(lines));

        assertNotNull(listWithItem(document, "Read all the steps"), "the list is lost: " + document);
        assertSame(listWithItem(document, "Read all the steps"), listWithItem(document, "Keep the small parts"),
            "the list is split: " + document);
    }

    /** Guard: a numbered list with a caption between two items, at the numbers, stays one list. */
    @Test
    void numberedListWithACaptionBetweenItemsStaysOneList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Exercises"));
        lines.add(new Line(LEFT, 742, new Run("1. Find the length of the side of the box.", false)));
        lines.add(new Line(LEFT, 728, new Run("2. Find the area of the top of the box.", false)));
        lines.add(new Line(LEFT, 714, new Run("3. Find the volume of the box in the figure.", false)));
        lines.add(new Line(LEFT, 688, new Run("Figure 2 for exercise 3.", false)));
        lines.add(new Line(LEFT, 662, new Run("4. Find the mass of the box when it is full.", false)));
        JsonNode document = convertJson("numbered-caption", Arrays.asList(lines));

        assertNotNull(listWithItem(document, "1. Find the length"), "the list is lost: " + document);
        assertSame(listWithItem(document, "1. Find the length"), listWithItem(document, "4. Find the mass"),
            "the numbered list is split: " + document);
    }

    /** Guard: an item with a second paragraph at the text of the item stays in one list with the items after it. */
    @Test
    void itemWithASecondParagraphStaysInTheList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        lines.add(bullet(LEFT, 742, "Read all the steps before you start the work."));
        lines.add(new Line(LEFT + TEXT_INDENT, 716, new Run("Keep this paper near you while you work.", false)));
        lines.add(bullet(LEFT, 690, "Put the parts on a clean table."));
        lines.add(bullet(LEFT, 676, "Keep the small parts away from children."));
        JsonNode document = convertJson("second-paragraph", Arrays.asList(lines));

        assertNotNull(listWithItem(document, "Read all the steps"), "the list is lost: " + document);
        assertSame(listWithItem(document, "Read all the steps"), listWithItem(document, "Keep the small parts"),
            "the list is split: " + document);
    }

    /**
     * Guard: a paragraph of an item in the font of the items, at the bullets and after an empty line, stays in one
     * list with the items after it. Only a title, in a larger or a heavier font, ends the list.
     */
    @Test
    void itemParagraphAtTheBulletsStaysInTheList() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(title(LEFT, 760, "Before you start"));
        lines.add(bullet(LEFT, 742, "Read all the steps before you start the work."));
        lines.add(new Line(LEFT, 742 - TITLE_ABOVE, new Run("Keep this paper near you while you work.", false)));
        bullets(lines, LEFT, 742 - TITLE_ABOVE - TITLE_BELOW, "Put the parts on a clean table.",
            "Keep the small parts away from children.");
        JsonNode document = convertJson("item-paragraph", Arrays.asList(lines));

        assertNotNull(listWithItem(document, "Read all the steps"), "the list is lost: " + document);
        assertSame(listWithItem(document, "Read all the steps"), listWithItem(document, "Keep the small parts"),
            "the list is split at a paragraph of an item: " + document);
    }

    /** Guard: a list at the end of a page that goes on at the start of the next page stays connected. */
    @Test
    void listThatGoesOnInTheNextPageStaysConnected() throws IOException {
        List<Line> first = new ArrayList<>();
        first.add(title(LEFT, 760, "Before you start"));
        bullets(first, LEFT, 104, "Read all the steps first.", "Put the parts on a table.", "Keep the parts dry.");
        List<Line> second = new ArrayList<>();
        bullets(second, LEFT, 760, "Check that no part is lost.", "Clean the table.", "Find the tools.");
        JsonNode document = convertJson("next-page", Arrays.asList(first, second));

        JsonNode list = listWithItem(document, "Read all the steps");
        JsonNode next = listWithItem(document, "Find the tools");
        assertNotNull(list, "the list of the first page is lost: " + document);
        assertNotNull(next, "the list of the second page is lost: " + document);
        assertEquals(next.path("id").asInt(), list.path("next list id").asInt(-1),
            "the list of the first page is not connected to the list of the next page: " + document);
    }

    /** Asserts one list for the first two texts and another list for the last two texts. */
    private static void assertSectionLists(JsonNode document, String first, String last, String nextFirst,
                                           String nextLast) {
        JsonNode list = listWithItem(document, first);
        JsonNode next = listWithItem(document, nextFirst);
        assertNotNull(list, "the list of the first section is lost (" + first + "): " + document);
        assertNotNull(next, "the list of the second section is lost (" + nextFirst + "): " + document);
        assertSame(list, listWithItem(document, last), "the list of the first section is split: " + document);
        assertSame(next, listWithItem(document, nextLast), "the list of the second section is split: " + document);
        assertTrue(list != next, SYMPTOM + " (" + first + ", " + nextFirst + "): " + document);
    }

    /** Asserts that the first text comes before the second text in the output. */
    private static void assertBefore(JsonNode document, String first, String second) {
        String text = document.toString();
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue(firstIndex >= 0 && secondIndex >= 0, "a text is lost (" + first + ", " + second + "): " + document);
        assertTrue(firstIndex < secondIndex, "the order is wrong: " + first + " after " + second + ": " + document);
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

    /** Whether the deepest element that contains the text is in a list. */
    private static boolean isInList(JsonNode document, String text) {
        return isInList(document.path("kids"), text, false);
    }

    private static boolean isInList(JsonNode nodes, String text, boolean inList) {
        for (JsonNode node : nodes) {
            boolean nodeInList = inList || "list".equals(node.path("type").asText());
            if (isInList(node.path("kids"), text, nodeInList) || isInList(node.path("list items"), text, nodeInList)) {
                return true;
            }
            if (node.path("content").asText().contains(text)) {
                return nodeInList;
            }
        }
        return false;
    }

    /** Adds one bullet line for each text, from the baseline down; returns the baseline of the last bullet. */
    private static float bullets(List<Line> lines, float x, float baseline, String... texts) {
        float y = baseline;
        for (int i = 0; i < texts.length; i++) {
            y = baseline - i * LINE_DISTANCE;
            lines.add(bullet(x, y, texts[i]));
        }
        return y;
    }

    private static Line bullet(float x, float baseline, String text) {
        return new Line(x, baseline, new Run("\u2022 " + text, false));
    }

    private static Line title(float x, float baseline, String text) {
        return new Line(x, baseline, TITLE_SIZE, new Run(text, true));
    }

    private static JsonNode convertJson(String name, List<List<Line>> pages) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-354-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (List<Line> lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    for (Line line : lines) {
                        content.beginText();
                        content.newLineAtOffset(line.x, line.baseline);
                        for (Run run : line.runs) {
                            content.setFont(run.bold ? BOLD : REGULAR, line.size);
                            content.showText(run.text);
                        }
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

    private static final class Run {
        final String text;
        final boolean bold;

        Run(String text, boolean bold) {
            this.text = text;
            this.bold = bold;
        }
    }

    /** A line at its place: runs of text, each in the bold or the regular font, one after the other. */
    private static final class Line {
        final float x;
        final float baseline;
        final float size;
        final List<Run> runs;

        Line(float x, float baseline, Run... runs) {
            this(x, baseline, SIZE, runs);
        }

        Line(float x, float baseline, float size, Run... runs) {
            this.x = x;
            this.baseline = baseline;
            this.size = size;
            this.runs = Arrays.asList(runs);
        }
    }
}

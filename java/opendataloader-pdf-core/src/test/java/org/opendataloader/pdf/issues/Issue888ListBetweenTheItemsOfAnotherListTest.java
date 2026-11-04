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
 * #888: a list that starts between two items of another list, in another numbering style, is in the first item.
 *
 * <p>Page 7 of the document of #876 has a bulleted list with an item above section title F and an item between
 * titles F and G. The labels of the titles ("2026-04-F-", "2026-04-G-") make a list in letters. The list of the
 * titles is made first, and item F takes the content up to title G, with the second bulleted item. The first
 * bulleted item then takes the content up to its next item, and the list of the titles with it. The heading
 * detection does not examine a list in a list item, so the titles stay list items. The expected output keeps the
 * list of another style out of the item above it: the item ends before that list.
 *
 * <p>The samples copy the measurements of the document of #876: text in 10.73 pt, at x = 69.1, the lines of a
 * paragraph 12.3 pt apart, the lines around an empty line 24.6 pt apart, and the titles in bold and underlined.
 * The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start with
 * {@code issue-888}.
 */
class Issue888ListBetweenTheItemsOfAnotherListTest {

    private static final float LEFT = 69.1f;
    private static final float SIZE = 10.73f;
    private static final float LINE = 12.3f;
    private static final float EMPTY_LINE = 24.6f;
    private static final float INDENT = 18;

    private static final String ITEM_1 = "- To elect one member and one deputy member of the board";
    private static final String ITEM_2 = "- To elect one other member and one other deputy member";
    private static final String TITLE_F = "2026-04-F-Members of the water union";
    private static final String TITLE_G = "2026-04-G-Members of the energy union";

    /** The layout of page 7: titles F and G, with a bulleted item above F and a bulleted item between them. */
    @Test
    void titlesBetweenTheItemsOfABulletedListAreHeadings() throws IOException {
        Page page = new Page()
            .line("After this report, the council decides by all the votes or by the")
            .line("majority of the votes:")
            .afterEmptyLine(ITEM_1)
            .afterEmptyLine("This decision replaces the decision of the last meeting of the")
            .line("council, which elected two members.")
            .afterEmptyLine("Vote :")
            .afterEmptyLine("Adopted by all the votes.")
            .title(TITLE_F)
            .afterEmptyLine("The mayor recalls that the town is a member of the water union, and")
            .line("that the union asks each town for one member and one deputy member.")
            .afterEmptyLine(ITEM_2)
            .afterEmptyLine("Vote :")
            .afterEmptyLine("Adopted by all the votes.")
            .title(TITLE_G)
            .afterEmptyLine("Considering the elections of the town council of this year,")
            .afterEmptyLine("Considering that the town is a member of the energy union, and that")
            .line("the union asks each town for one member.");
        JsonNode document = convert("titles", page);

        List<JsonNode> elements = elements(document);
        for (String title : new String[] {TITLE_F, TITLE_G}) {
            JsonNode element = elementThatContains(elements, title);
            assertTrue(element != null && "heading".equals(element.path("type").asText()) && isOnThePage(document, element),
                "the section title is in a list in an item of another list: " + document);
        }
        assertInOrder(document, elements, ITEM_1, TITLE_F, ITEM_2, TITLE_G);
    }

    /** A table of contents: the list of the parts starts between two chapters, and it is not in a chapter. */
    @Test
    void partsBetweenTheChaptersOfATableOfContentsAreNotInAChapter() throws IOException {
        Page page = new Page()
            .line("Introduction 7")
            .line("1. Changing practices of the town 7")
            .line("2. Core and periphery of the town 12")
            .line("Part I: New streets, different houses 21")
            .line("3. The street as a market 26")
            .line("4. The houses of the street 30")
            .line("Part II: From one town to a network of towns 45")
            .line("5. The bridges of the town 50")
            .line("6. The roads between the towns 58")
            .line("Notes 137");
        JsonNode document = convert("table-of-contents", page);

        List<JsonNode> elements = elements(document);
        JsonNode chapter = elementThatContains(elements, "2. Core and periphery of the town");
        JsonNode part = elementThatContains(elements, "Part I: New streets");
        assertTrue(chapter != null && part != null, "a line of the table of contents is missing: " + document);
        assertTrue(!descendants(chapter).contains(part),
            "the list of the parts is in the item of a chapter above it: " + document);
        assertInOrder(document, elements, "2. Core and periphery", "Part I: New streets", "3. The street as a market",
            "Part II: From one town", "5. The bridges of the town");
    }

    /** Guard: a numbered list below a bulleted item, with its numbers after the bullet, stays in the item. */
    @Test
    void indentedListBelowABulletedItemStaysInTheItem() throws IOException {
        Page page = new Page()
            .line("The members of the board meet in the town hall on the first day")
            .line("of each month, and the agenda of the day has two points:")
            .afterEmptyLine("- The first point of the agenda, which has two parts:")
            .line("1. The first part of the first point", INDENT)
            .line("2. The second part of the first point", INDENT)
            .line("- The second point of the agenda")
            .afterEmptyLine("The mayor opens the meeting and gives the agenda of the day to")
            .line("the members of the board of the town.");
        JsonNode document = convert("nested-list", page);

        List<JsonNode> elements = elements(document);
        JsonNode item = elementThatContains(elements, "The first point of the agenda");
        JsonNode part = elementThatContains(elements, "The second part of the first point");
        assertTrue(item != null && part != null && descendants(item).contains(part),
            "the numbered list below the bulleted item is not in the item: " + document);
        assertInOrder(document, elements, "The first point of the agenda", "The first part of the first point",
            "The second point of the agenda");
    }

    /**
     * Guard: a numbered list below a bulleted item, with its numbers after the bullet, stays in the item, also when
     * its numbers go on below the next bulleted item (review of #890).
     */
    @Test
    void indentedListThatGoesOnBelowTheNextItemStaysInTheItem() throws IOException {
        Page page = new Page()
            .line("The members of the board meet in the town hall on the first day")
            .line("of each month, and the agenda of the day has two points:")
            .afterEmptyLine("- The first point of the agenda, which has parts:")
            .line("1. The first part of the agenda", INDENT)
            .line("2. The second part of the agenda", INDENT)
            .line("- The second point of the agenda, which has a part:")
            .line("3. The third part of the agenda", INDENT)
            .afterEmptyLine("The mayor opens the meeting and gives the agenda of the day to")
            .line("the members of the board of the town.");
        JsonNode document = convert("nested-list-goes-on", page);

        List<JsonNode> elements = elements(document);
        JsonNode item = elementThatContains(elements, "The first point of the agenda");
        JsonNode part = elementThatContains(elements, "The first part of the agenda");
        assertTrue(item != null && part != null && descendants(item).contains(part),
            "the numbered list below the bulleted item is not in the item: " + document);
        assertInOrder(document, elements, "The first point of the agenda", "The first part of the agenda",
            "The second point of the agenda", "The third part of the agenda");
    }

    /** Guard: a paragraph between two bulleted items, with no other list, stays in the first item. */
    @Test
    void paragraphBetweenTwoBulletedItemsStaysInTheFirstItem() throws IOException {
        Page page = new Page()
            .line("The members of the board meet in the town hall on the first day")
            .line("of each month, and the agenda of the day has two points:")
            .afterEmptyLine("- The first point of the agenda of the day")
            .afterEmptyLine("This point goes on in a paragraph of two lines below the item, and")
            .line("the secretary writes it in the minutes of the day.")
            .afterEmptyLine("- The second point of the agenda of the day")
            .afterEmptyLine("The mayor opens the meeting and gives the agenda of the day to")
            .line("the members of the board of the town.");
        JsonNode document = convert("paragraph-in-item", page);

        List<JsonNode> elements = elements(document);
        JsonNode item = elementThatContains(elements, "The first point of the agenda");
        JsonNode paragraph = elementThatContains(elements, "This point goes on in a paragraph");
        assertTrue(item != null && paragraph != null && descendants(item).contains(paragraph),
            "the paragraph between the two items is not in the first item: " + document);
    }

    private static void assertInOrder(JsonNode document, List<JsonNode> elements, String... texts) {
        int previous = -1;
        for (String text : texts) {
            JsonNode element = elementThatContains(elements, text);
            int index = element != null ? elements.indexOf(element) : -1;
            assertTrue(index > previous, "the text is missing or out of the reading order (" + text + "): " + document);
            previous = index;
        }
    }

    /** Whether the element is an element of the page, not a part of a list item or of another element. */
    private static boolean isOnThePage(JsonNode document, JsonNode element) {
        for (JsonNode kid : document.path("kids")) {
            if (kid == element) {
                return true;
            }
        }
        return false;
    }

    /** The element and the elements in it: its kids and its list items, at each depth. */
    private static List<JsonNode> descendants(JsonNode element) {
        List<JsonNode> elements = new ArrayList<>();
        addElements(element, elements);
        return elements;
    }

    /** The elements of the document in reading order: each element before its kids and its list items. */
    private static List<JsonNode> elements(JsonNode document) {
        List<JsonNode> elements = new ArrayList<>();
        document.path("kids").forEach(kid -> addElements(kid, elements));
        return elements;
    }

    private static void addElements(JsonNode node, List<JsonNode> elements) {
        elements.add(node);
        node.path("list items").forEach(item -> addElements(item, elements));
        node.path("kids").forEach(kid -> addElements(kid, elements));
    }

    /** The innermost element whose text contains the text, or null. */
    private static JsonNode elementThatContains(List<JsonNode> elements, String text) {
        JsonNode found = null;
        for (JsonNode element : elements) {
            if (element.path("content").asText().contains(text)) {
                found = element;
            }
        }
        return found;
    }

    private static JsonNode convert(String name, Page page) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-888-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage pdfPage = new PDPage(PDRectangle.A4);
            document.addPage(pdfPage);
            try (PDPageContentStream content = new PDPageContentStream(document, pdfPage)) {
                for (Line line : page.lines) {
                    PDType1Font font = line.title ? bold : regular;
                    content.beginText();
                    content.setFont(font, SIZE);
                    content.newLineAtOffset(LEFT + line.indent, line.baseline);
                    content.showText(line.text);
                    content.endText();
                    if (line.title) {
                        // The underline of the issue: 0.2 pt below the baseline, 1.2 pt thick.
                        float width = font.getStringWidth(line.text) / 1000 * SIZE;
                        content.addRect(LEFT + line.indent, line.baseline - 1.4f, width, 1.2f);
                        content.fill();
                    }
                }
            }
            document.save(pdf.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    /** The lines of a page, from the top: each line 12.3 pt or 24.6 pt (an empty line) below the line above. */
    private static final class Page {
        final List<Line> lines = new ArrayList<>();
        float baseline = 700 + EMPTY_LINE;

        Page line(String text) {
            return line(text, 0);
        }

        Page line(String text, float indent) {
            return add(text, lines.isEmpty() ? EMPTY_LINE : LINE, indent, false);
        }

        Page afterEmptyLine(String text) {
            return add(text, EMPTY_LINE, 0, false);
        }

        Page title(String text) {
            return add(text, EMPTY_LINE, 0, true);
        }

        private Page add(String text, float distance, float indent, boolean title) {
            baseline -= distance;
            lines.add(new Line(text, baseline, indent, title));
            return this;
        }
    }

    private static final class Line {
        final String text;
        final float baseline;
        final float indent;
        final boolean title;

        Line(String text, float baseline, float indent, boolean title) {
            this.text = text;
            this.baseline = baseline;
            this.indent = indent;
            this.title = title;
        }
    }
}

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
 * #876: the paragraphs in the content of a list item join the lines around an empty line.
 *
 * <p>The section titles of the document of the issue ("2026-04-F-", "2026-04-G-") are a list before the heading
 * detection changes them into headings. The text of a section is the content of the item of its title. The
 * paragraph detection of the body text of a page starts a paragraph at a line after an empty line (#884), but the
 * content of a list item keeps the rules without empty lines. Two short paragraphs with an empty line between
 * them, in the text of a section, become one paragraph. The expected output starts a paragraph there, with the
 * lines of the page, as in the body text of the page.
 *
 * <p>The samples copy the measurements of pages 7 and 8 of the issue: text in 10.73 pt, at x = 69.1, the lines of
 * a paragraph 12.3 pt apart, the lines around an empty line 24.6 pt apart, and the titles in bold and underlined.
 * The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start with
 * {@code issue-876-content}.
 */
class Issue876ListItemContentAfterAnEmptyLineTest {

    private static final float LEFT = 69.1f;
    private static final float SIZE = 10.73f;
    private static final float LINE = 12.3f;
    private static final float EMPTY_LINE = 24.6f;

    private static final String TITLE_F = "2026-04-F-Members of the water union";
    private static final String TITLE_G = "2026-04-G-Members of the energy union";
    private static final String REPORT = "After this report, the council decides";
    private static final String DECISION = "It decides by all the votes or by the majority";
    private static final String SECOND = "The second paragraph of the first point.";
    private static final String THIRD = "The third paragraph of the first point.";

    /** The layout of page 7: two short paragraphs, with an empty line between them, in the text of section F. */
    @Test
    void paragraphsOfASectionWithAnEmptyLineBetweenThemAreSeparate() throws IOException {
        Page page = new Page()
            .line("The members of the board meet in the town hall on the first day")
            .line("of each month, and the agenda of the day has two points.")
            .title(TITLE_F)
            .afterEmptyLine("The mayor recalls that the town is a member of the water union, and")
            .line("that the union asks each town for one member and one deputy member.")
            .afterEmptyLine(REPORT)
            .afterEmptyLine(DECISION)
            .afterEmptyLine("Adopted by all the votes.")
            .title(TITLE_G)
            .afterEmptyLine("Considering the elections of the town council of this year,")
            .afterEmptyLine("Considering that the town is a member of the energy union, and that")
            .line("the union asks each town for one member.");
        JsonNode document = convert("section", page);

        List<JsonNode> elements = elements(document);
        JsonNode report = elementThatContains(elements, REPORT);
        JsonNode decision = elementThatContains(elements, DECISION);
        assertTrue(report != null && decision != null, "a line of the section is missing: " + document);
        assertTrue(report != decision,
            "the paragraphs with an empty line between them in the section are one paragraph: " + document);
        assertTrue(elements.indexOf(report) < elements.indexOf(decision),
            "the paragraphs of the section are out of the reading order: " + document);
    }

    /** A bulleted item with paragraphs below it: two paragraphs of one line, with an empty line between them. */
    @Test
    void paragraphsOfAListItemWithAnEmptyLineBetweenThemAreSeparate() throws IOException {
        JsonNode document = convert("item", itemPage());

        List<JsonNode> elements = elements(document);
        JsonNode second = elementThatContains(elements, SECOND);
        JsonNode third = elementThatContains(elements, THIRD);
        assertTrue(second != null && third != null, "a paragraph of the item is missing: " + document);
        assertTrue(second != third,
            "the paragraphs with an empty line between them in the list item are one paragraph: " + document);
        JsonNode item = elementThatContains(elements, "The first point of the agenda");
        assertTrue(item != null && descendants(item).contains(second) && descendants(item).contains(third),
            "the paragraphs are not in the list item: " + document);
        assertTrue(elements.indexOf(second) < elements.indexOf(third),
            "the paragraphs of the list item are out of the reading order: " + document);
    }

    /** Guard: the lines of a paragraph in a list item, 12.3 pt apart, stay one paragraph. */
    @Test
    void linesOfAParagraphInAListItemStayOneParagraph() throws IOException {
        JsonNode document = convert("item-paragraph", itemPage());

        JsonNode paragraph = elementThatContains(elements(document), "The first paragraph of the first point");
        assertTrue(paragraph != null && paragraph.path("content").asText().contains("goes on in a second line"),
            "the lines of a paragraph in the list item are not one paragraph: " + document);
    }

    /**
     * Guard: on a page with single spacing, a full line in a list item continues on the line 24.6 pt below it. The
     * lines of the paragraph are filled with words to a width of 300 pt.
     */
    @Test
    void fullLineInAListItemContinuesBelow() throws IOException {
        List<String> lines = wrap("The first paragraph of the first point, which the secretary of the board writes "
            + "in two lines");
        Page page = new Page()
            .line("The members of the board meet in the town hall on the first day")
            .line("of each month, and the agenda of the day has two points:")
            .afterEmptyLine("- The first point of the agenda of the day")
            .below(lines.get(0), 1.5f * EMPTY_LINE)
            .afterEmptyLine(lines.get(1))
            .afterEmptyLine("- The second point of the agenda of the day")
            .afterEmptyLine("The mayor opens the meeting and gives the agenda of the day to")
            .line("the members of the board of the town.");
        JsonNode document = convert("item-full-line", page);

        JsonNode paragraph = elementThatContains(elements(document), lines.get(0));
        assertTrue(lines.size() == 2 && paragraph != null && paragraph.path("content").asText().contains(lines.get(1)),
            "the full line in the list item does not continue below: " + document);
    }

    /**
     * A page with a bulleted list of two items. Below the first item, each after an empty line: a paragraph of two
     * lines and two paragraphs of one line.
     */
    private static Page itemPage() {
        return new Page()
            .line("The members of the board meet in the town hall on the first day")
            .line("of each month, and the agenda of the day has two points:")
            .afterEmptyLine("- The first point of the agenda of the day")
            .afterEmptyLine("The first paragraph of the first point, which")
            .line("goes on in a second line of the paragraph.")
            .afterEmptyLine(SECOND)
            .afterEmptyLine(THIRD)
            .afterEmptyLine("- The second point of the agenda of the day")
            .afterEmptyLine("The mayor opens the meeting and gives the agenda of the day to")
            .line("the members of the board of the town.");
    }

    /** The lines of the text in Helvetica, each with as many words as fit in 300 pt. */
    private static List<String> wrap(String text) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String longer = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringWidth(longer) / 1000 * SIZE > 300) {
                lines.add(line);
                longer = word;
            }
            line = longer;
        }
        lines.add(line);
        return lines;
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
        Path directory = TestSamples.emptyDirectory("issue-876-content-" + name);
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
                    content.newLineAtOffset(LEFT, line.baseline);
                    content.showText(line.text);
                    content.endText();
                    if (line.title) {
                        // The underline of the issue: 0.2 pt below the baseline, 1.2 pt thick.
                        float width = font.getStringWidth(line.text) / 1000 * SIZE;
                        content.addRect(LEFT, line.baseline - 1.4f, width, 1.2f);
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

    /** The lines of a page, from the top: each line 12.3 pt, 24.6 pt (an empty line), or a given distance below. */
    private static final class Page {
        final List<Line> lines = new ArrayList<>();
        float baseline = 700 + EMPTY_LINE;

        Page line(String text) {
            return add(text, lines.isEmpty() ? EMPTY_LINE : LINE, false);
        }

        Page afterEmptyLine(String text) {
            return add(text, EMPTY_LINE, false);
        }

        Page below(String text, float distance) {
            return add(text, distance, false);
        }

        Page title(String text) {
            return add(text, EMPTY_LINE, true);
        }

        private Page add(String text, float distance, boolean title) {
            baseline -= distance;
            lines.add(new Line(text, baseline, title));
            return this;
        }
    }

    private static final class Line {
        final String text;
        final float baseline;
        final boolean title;

        Line(String text, float baseline, boolean title) {
            this.text = text;
            this.baseline = baseline;
            this.title = title;
        }
    }
}

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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #876: a section title that is the last item of a list on its page takes the line after the empty line below it.
 *
 * <p>The section titles of the PDF of the issue start with labels as "2026-04-A-" and "2026-04-B-". The list
 * detection makes one list of these lines, with an item on each page. The last item of a list on a page takes the
 * lines below it that continue its text. After title A, an empty line and a paragraph of one line follow, and the
 * item takes that line too. The item then has two lines, so the heading detection does not change it into a
 * heading, as it does for the other titles. The expected output keeps a line after an empty line out of the last
 * item of the page, as the paragraph detection keeps it out of the paragraph above it (#884).
 *
 * <p>The samples copy the measurements of pages 3, 4, and 8 of the issue: text in 10.73 pt, at x = 69.1, the lines
 * of a paragraph 12.3 pt apart, the lines around an empty line 24.6 pt apart, and the titles in bold and
 * underlined. The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start
 * with {@code issue-876-list}.
 */
class Issue876LastListItemBeforeAnEmptyLineTest {

    private static final float LEFT = 69.1f;
    private static final float SIZE = 10.73f;
    private static final float LINE = 12.3f;
    private static final float EMPTY_LINE = 24.6f;

    private static final String TITLE_A = "2026-04-A-Allowances of the mayor";
    private static final String LINE_A = "The town council of the town,";
    private static final String TITLE_B = "2026-04-B-Vote of the tax rates";
    private static final String PARAGRAPH_B = "The mayor recalls that the council votes the rates of the local";
    private static final String VOTE = "Vote :";

    /** The layout of page 3: title A, an empty line, and a paragraph of one line. Title A is a heading. */
    @Test
    void titleAboveALineAfterAnEmptyLineIsAHeading() throws IOException {
        JsonNode document = convert("titles", titlePages());

        List<JsonNode> elements = elements(document);
        JsonNode title = elementThatContains(elements, TITLE_A);
        JsonNode line = elementThatContains(elements, LINE_A);
        assertTrue(line != null, "the line after the empty line is missing: " + document);
        assertTrue(title != null && "heading".equals(title.path("type").asText())
                && !title.path("content").asText().contains(LINE_A),
            "the title and the line after the empty line below it are one list item: " + document);
        assertTrue(elements.indexOf(title) < elements.indexOf(line),
            "the line after the empty line is before the title: " + document);
    }

    /** The layout of page 8: the line "Vote :" after an empty line below the last item of a list. */
    @Test
    void lineAfterAnEmptyLineIsNotInTheLastItemOfThePage() throws IOException {
        Page page = new Page()
            .line("The council elects one member and one deputy member of the")
            .line("board of the energy union of the area, as its rules set it.")
            .afterEmptyLine("After the vote, the council takes note of the candidates:")
            .afterEmptyLine("- One member of the council")
            .line("- One deputy member of the council")
            .afterEmptyLine(VOTE)
            .afterEmptyLine("Adopted by all the votes.")
            .afterEmptyLine("The mayor closes this point of the agenda, and the council")
            .line("goes on with the next point of the day.");
        JsonNode document = convert("vote", Collections.singletonList(page));

        List<JsonNode> elements = elements(document);
        JsonNode item = elementThatContains(elements, "One deputy member of the council");
        JsonNode vote = elementThatContains(elements, VOTE);
        assertTrue(item != null && vote != null, "a line of the page is missing: " + document);
        assertTrue(!item.path("content").asText().contains(VOTE) && vote != item,
            "the line after the empty line is in the text of the last item of the page: " + document);
    }

    /** Guard: title B, above a paragraph of two lines after an empty line, is a heading on main too. */
    @Test
    void titleAboveAParagraphOfTwoLinesIsAHeading() throws IOException {
        JsonNode document = convert("titles", titlePages());

        List<JsonNode> elements = elements(document);
        JsonNode title = elementThatContains(elements, TITLE_B);
        assertTrue(elementThatContains(elements, PARAGRAPH_B) != null, "the paragraph is missing: " + document);
        assertTrue(title != null && "heading".equals(title.path("type").asText())
                && !title.path("content").asText().contains(PARAGRAPH_B),
            "the title above a paragraph of two lines is not a heading of its own: " + document);
    }

    /** Guard: the second line of the last item of the page, 12.3 pt below the first, stays in the item. */
    @Test
    void wrappedLastItemKeepsItsSecondLine() throws IOException {
        Page page = new Page()
            .line("The members of the board meet in the town hall on the first")
            .line("day of each month, and the agenda has two points:")
            .afterEmptyLine("- The first item of the list of points")
            .line("- The second item of the list of points, which")
            .line("goes on in a second line of the item.")
            .afterEmptyLine("The mayor opens the meeting and gives the agenda of the day")
            .line("to the members of the board of the town.");
        JsonNode document = convert("wrapped-item", Collections.singletonList(page));

        JsonNode item = elementThatContains(elements(document), "The second item of the list");
        assertTrue(item != null && item.path("content").asText().contains("goes on in a second line"),
            "the second line of the last item is not in the item: " + document);
    }

    /** Guard: on a page with all its lines 24.6 pt apart, the second line of the last item stays in the item. */
    @Test
    void doubleSpacedLastItemKeepsItsSecondLine() throws IOException {
        Page page = new Page();
        for (String text : Arrays.asList("The members of the board meet in the town hall", "on the first day of each "
                + "month, and the agenda has two points:", "- The first item of the list of points", "- The second "
                + "item of the list of points, which", "goes on in a second line of the item.", "The mayor opens the "
                + "meeting and gives the agenda", "of the day to the members of the board.")) {
            page.afterEmptyLine(text);
        }
        JsonNode document = convert("double-spaced-item", Collections.singletonList(page));

        JsonNode item = elementThatContains(elements(document), "The second item of the list");
        assertTrue(item != null && item.path("content").asText().contains("goes on in a second line"),
            "the second line of the last item is not in the item on a double-spaced page: " + document);
    }

    /**
     * Guard: on a page with single spacing, a full line of the last item continues on the line 24.6 pt below it.
     * The lines of the paragraph are filled with words to a width of 250 pt, and the lines of the item to 300 pt,
     * so that the first line of the item is the longest line of the page.
     */
    @Test
    void fullLineOfTheLastItemContinuesBelow() throws IOException {
        Page page = new Page();
        for (String text : wrap("The members of the board meet in the town hall on the first day of each month. The "
                + "secretary of the board writes the minutes of the meeting, and the mayor signs them.", 250)) {
            page.line(text);
        }
        page.afterEmptyLine("- The first item of the agenda of the day");
        List<String> item = wrap("- The second item of the agenda of the day, which the secretary writes in two lines",
            300);
        page.line(item.get(0));
        for (String text : item.subList(1, item.size())) {
            page.afterEmptyLine(text);
        }
        page.afterEmptyLine("The mayor opens the meeting.");
        JsonNode document = convert("full-item-line", Collections.singletonList(page));

        JsonNode element = elementThatContains(elements(document), "The second item of the agenda");
        assertTrue(item.size() == 2 && element != null && element.path("content").asText().contains(item.get(1)),
            "the full line of the last item does not continue below: " + document);
    }

    /**
     * Two pages as pages 3 and 4 of the issue. Page 1: a paragraph of two lines, a line, title A, the line after the
     * empty line below it, and paragraphs. Page 2: title B, a paragraph of two lines, and two lines.
     */
    private static List<Page> titlePages() {
        Page first = new Page()
            .line("The town council meets on the first day of each month, and the")
            .line("secretary of the board writes the minutes of the meeting.")
            .afterEmptyLine("Adopted by all the votes.")
            .title(TITLE_A)
            .afterEmptyLine(LINE_A)
            .afterEmptyLine("Considering the general code of the local authorities, and in")
            .line("particular its articles on the allowances of the members,")
            .afterEmptyLine("Considering the minutes of the first meeting of the council,")
            .afterEmptyLine("Considering that the maximum of the allowances is set by the")
            .line("index of the public service, and the board votes the rates.");
        Page second = new Page()
            .title(TITLE_B)
            .afterEmptyLine(PARAGRAPH_B)
            .line("taxes each year, as the law of the year 1980 sets them.")
            .afterEmptyLine("Considering the general code of the taxes,")
            .afterEmptyLine("Adopted by all the votes.");
        return Arrays.asList(first, second);
    }

    /** The lines of the text in Helvetica, each with as many words as fit in the width. */
    private static List<String> wrap(String text, float width) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        List<String> lines = new ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String longer = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringWidth(longer) / 1000 * SIZE > width) {
                lines.add(line);
                longer = word;
            }
            line = longer;
        }
        lines.add(line);
        return lines;
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

    private static JsonNode convert(String name, List<Page> pages) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-876-list-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            for (Page lines : pages) {
                PDPage page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    for (Line line : lines.lines) {
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
            return add(text, lines.isEmpty() ? EMPTY_LINE : LINE, false);
        }

        Page afterEmptyLine(String text) {
            return add(text, EMPTY_LINE, false);
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

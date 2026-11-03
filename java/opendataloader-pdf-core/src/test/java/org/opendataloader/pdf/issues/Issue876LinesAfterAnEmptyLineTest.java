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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #876: a line that stands alone, after an empty line, is merged with the line below it.
 *
 * <p>The PDF of the issue writes the lines of a paragraph 12 pt apart and puts an empty line between two
 * paragraphs, so that the lines of two paragraphs are 24 to 25 pt apart. A bold underlined line, as a section
 * title, stands alone between empty lines. The paragraph detection merges two lines that are 25 pt apart, at
 * 10.73 pt, so the title and the line below it become one paragraph. The expected output keeps a line after an
 * empty line out of the paragraph above it, when the other lines of the page are 12 pt apart.
 *
 * <p>The samples copy the measurements of pages 2 and 3 of the issue: text in 10.73 pt, at x = 69.1, the lines of
 * a paragraph 12 pt apart, the lines around an empty line 24 or 25 pt apart, and the titles in bold and
 * underlined. The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start
 * with {@code issue-876}.
 */
class Issue876LinesAfterAnEmptyLineTest {

    private static final float LEFT = 69.1f;
    private static final float SIZE = 10.73f;

    private static final String TITLE = "Additional item of the agenda in a closed session";
    private static final String VOTE = "Vote :";
    private static final String PUBLIC = "Public part :";
    private static final String APPROVAL = "Approval of the minutes of the last meeting";

    /** The layout of page 2: a bold title, an empty line, and a line of body text are not one paragraph. */
    @Test
    void lineAfterAnEmptyLineBelowATitleIsNotInTheTitle() throws IOException {
        JsonNode document = convert("title-vote", singleSpacedPage());

        assertTrue(elementThatContains(document, VOTE) != null, "the line after the empty line is missing: " + document);
        JsonNode title = elementThatContains(document, TITLE);
        assertTrue(title != null && !title.path("content").asText().contains(VOTE),
            "the line after the empty line is merged with the title: " + document);
    }

    /** The layout of page 3: two bold titles with an empty line between them are not one paragraph. */
    @Test
    void twoTitlesWithAnEmptyLineBetweenThemAreNotOneParagraph() throws IOException {
        JsonNode document = convert("two-titles", singleSpacedPage());

        assertTrue(elementThatContains(document, APPROVAL) != null, "the second title is missing: " + document);
        JsonNode title = elementThatContains(document, PUBLIC);
        assertTrue(title != null && !title.path("content").asText().contains(APPROVAL),
            "the two titles with an empty line between them are merged: " + document);
    }

    /**
     * The composition of page 2 of the issue: most paragraphs have one line, so the lines 24 or 25 pt apart are
     * more than the lines 12 pt apart. The title and the line "Vote :" are still not one paragraph.
     */
    @Test
    void lineAfterAnEmptyLineIsNotInTheTitleOnAPageOfShortParagraphs() throws IOException {
        List<Line> lines = new ArrayList<>(Arrays.asList(
            new Line("Agenda of the meeting:", 750, false),
            new Line("Secretary of the meeting: one member of the board", 726, false),
            new Line("The quorum is reached (14 members of 15)", 702, false),
            new Line("The board can meet.", 690, false),
            new Line("The sheet of the members is signed by each member of the", 666, false),
            new Line("board who is present.", 654, false),
            new Line(TITLE, 630, true),
            new Line(VOTE, 605, false),
            new Line("Adopted by all the votes.", 581, false),
            new Line("The plot of land of the town, along the road of the old", 557, false),
            new Line("mill, is for sale.", 545, false),
            new Line("The mayor proposes not to buy the plot.", 521, false),
            new Line("Adopted by all the votes of the board.", 497, false)));
        JsonNode document = convert("short-paragraphs", lines);

        assertTrue(elementThatContains(document, VOTE) != null, "the line after the empty line is missing: " + document);
        JsonNode title = elementThatContains(document, TITLE);
        assertTrue(title != null && !title.path("content").asText().contains(VOTE),
            "the line after the empty line is merged with the title on a page of short paragraphs: " + document);
    }

    /** Guard: the lines of a paragraph, 12 pt apart, stay one paragraph. */
    @Test
    void linesOfAParagraphStayOneParagraph() throws IOException {
        JsonNode document = convert("paragraph", singleSpacedPage());

        JsonNode paragraph = elementThatContains(document, "The members of the board meet in the town hall");
        assertTrue(paragraph != null && paragraph.path("content").asText().contains("signs the minutes of the day"),
            "the lines of a paragraph are not one paragraph: " + document);
    }

    /** Guard: in a page with all its lines 24 pt apart, the lines of a paragraph stay one paragraph. */
    @Test
    void linesOfADoubleSpacedParagraphStayOneParagraph() throws IOException {
        List<Line> lines = new ArrayList<>(Arrays.asList(
            new Line("The members of the board meet in the town hall", 700, false),
            new Line("on the first day of each month, and the secretary", 676, false),
            new Line("of the board writes the minutes of the meeting and", 652, false),
            new Line("signs the minutes of the day before the next meeting.", 628, false),
            new Line("The mayor opens the meeting and gives the agenda", 580, false),
            new Line("of the day to the members of the board of the town.", 556, false)));
        JsonNode document = convert("double-spaced", lines);

        JsonNode paragraph = elementThatContains(document, "The members of the board meet in the town hall");
        assertTrue(paragraph != null && paragraph.path("content").asText().contains("signs the minutes of the day"),
            "the lines of a double-spaced paragraph are not one paragraph: " + document);
    }

    /**
     * Guard: a double-spaced paragraph stays one paragraph on a page that also has a single-spaced paragraph in the
     * same font size, at the same x. Each line of the two paragraphs is filled with words to a width of 300 pt.
     */
    @Test
    void linesOfADoubleSpacedParagraphStayOneParagraphOnAPageWithSingleSpacing() throws IOException {
        List<Line> lines = new ArrayList<>();
        float baseline = 700;
        for (String text : wrap("The members of the board meet in the town hall on the first day of each month. The "
                + "secretary of the board writes the minutes of the meeting, and the mayor signs them before the next "
                + "meeting of the board.")) {
            lines.add(new Line(text, baseline, false));
            baseline -= 12;
        }
        baseline -= 12;
        List<String> doubleSpaced = wrap("The mayor opens the meeting and gives the agenda of the day to the members "
            + "of the board. Each member can ask for one more point of the agenda, and the board votes on it before "
            + "the other points.");
        for (String text : doubleSpaced) {
            lines.add(new Line(text, baseline, false));
            baseline -= 24;
        }
        JsonNode document = convert("double-spaced-below-single", lines);

        String last = doubleSpaced.get(doubleSpaced.size() - 1);
        JsonNode paragraph = elementThatContains(document, doubleSpaced.get(0));
        assertTrue(doubleSpaced.size() >= 3 && paragraph != null && paragraph.path("content").asText().contains(last),
            "the lines of a double-spaced paragraph are not one paragraph: " + document);
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

    /**
     * A page as pages 2 and 3 of the issue: a paragraph of three lines 12 pt apart, a bold title, the line
     * "Vote :" 25 pt below it, a paragraph of two lines, two bold titles 25 pt apart, and a paragraph of two lines.
     */
    private static List<Line> singleSpacedPage() {
        return new ArrayList<>(Arrays.asList(
            new Line("The members of the board meet in the town hall on the first", 700, false),
            new Line("day of each month, and the secretary of the board writes and", 688, false),
            new Line("signs the minutes of the day before the next meeting.", 676, false),
            new Line(TITLE, 652, true),
            new Line(VOTE, 627, false),
            new Line("The members vote with their hands, and the secretary counts", 603, false),
            new Line("the votes of the members of the board.", 591, false),
            new Line(PUBLIC, 567, true),
            new Line(APPROVAL, 542, true),
            new Line("The mayor proposes to approve the minutes of the last meeting", 518, false),
            new Line("of the board, as the secretary wrote them.", 506, false)));
    }

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

    private static JsonNode convert(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-876-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
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

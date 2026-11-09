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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #913: on a page with two columns of text, the label of a list item in the left column ({@code 4.}) is on the row
 * of a heading in the right column. The page paints the label, then the heading, and the text of the list item
 * after the right column. The label and the heading become one text line across the gutter, and the reading order
 * reads the two columns in turn.
 *
 * <p>The samples have this layout: an A4 page; the left column at x = 51 and the right column at x = 303.3, each
 * 241 pt wide; body text in 9.96 pt Helvetica, the lines 12 pt apart; the text of a list item in 9.96 pt
 * Helvetica Oblique at x = 70.2, on the baseline of its label; headings in 10.96 pt Helvetica Bold. The heading on
 * the row of the label has its baseline 2.03 pt above the baseline of the label, and its second line, 12.41 pt
 * lower, starts at x = 337.8. Each paragraph starts with a word that names it. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-913}.
 */
class Issue913ListLabelAndHeadingTest {

    private static final float BODY = 9.96f;
    private static final float HEADING = 10.96f;
    private static final float LINE_DISTANCE = 12;
    private static final float PARAGRAPH_DISTANCE = 16;
    private static final float LEFT_X = 51;
    private static final float ITEM_TEXT_X = 70.2f;
    private static final float RIGHT_X = 303.3f;
    private static final float WIDTH = 241;
    private static final float NARROW_WIDTH = 150;
    private static final float TOP = 722;
    private static final float LABEL_ROW = 266;
    private static final float HEADING_ABOVE_LABEL = 2.03f;
    private static final float HEADING_LINE_DISTANCE = 12.41f;
    private static final float HEADING_INDENT_X = 337.8f;
    private static final String COLUMNS = "the paragraphs of the two columns are not read column by column";
    private static final String ITEM = "the label is not in one line with the text of its list item";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** The paragraph names in the expected order: the left column, then the right column. */
    private static final String[] EXPECTED = {"Apple", "Alder", "Birch", "Beech", "Cedar", "Cherry", "Willow",
        "Daisy", "Upper Plain", "Maple", "Survey Methods", "Nettle"};

    private static final String[] WORDS = ("the survey of the coast shows layers of sand and clay that formed in "
        + "shallow water near the shore over a long time while the sea went up and down and the wind moved the "
        + "dunes along the beach so that each layer keeps a record of the place where it formed").split(" ");

    /** Where the page paints the label and the text of the last list item. */
    private enum Paint {
        /** The label before the heading on its row, and the text of the item after the right column. */
        LABEL_BEFORE_THE_HEADING,
        /** The label and the text of the item one after the other, after the right column. */
        LABEL_WITH_ITS_TEXT_AFTER_THE_RIGHT_COLUMN
    }

    @Test
    void labelPaintedBeforeAHeadingOfTheNextColumnStaysWithItsItem() throws IOException {
        String markdown = convert("label-before-heading", Paint.LABEL_BEFORE_THE_HEADING);

        assertTrue(markdown.contains("4. Willow notes:"), ITEM + ":\n" + markdown);
    }

    @Test
    void itemOfALabelPaintedBeforeAHeadingOfTheNextColumnStaysInTheList() throws IOException {
        String markdown = convert("label-in-list", Paint.LABEL_BEFORE_THE_HEADING);

        assertTrue(markdown.contains("- 3. Cedar notes:") && markdown.contains("- 4. Willow notes:"),
            "the item of the label is not in the list of the items before it:\n" + markdown);
    }

    @Test
    void headingOnTheRowOfALabelOfTheOtherColumnStartsItsColumn() throws IOException {
        String markdown = convert("heading-on-label-row", Paint.LABEL_BEFORE_THE_HEADING);

        assertOrder(COLUMNS, markdown, EXPECTED);
    }

    /**
     * Variant: three columns, each 150 pt wide. The page paints the labels of the first two columns and a heading of
     * the third column on one row, one after the other, and later the text of each item with the paragraph below it.
     */
    @Test
    void labelsOfTwoColumnsPaintedBeforeAHeadingOfTheThirdStayWithTheirItems() throws IOException {
        Page page = new Page();
        float[] columns = {LEFT_X, 222, 393};
        String[] above = {"Alder", "Beech", "Maple"};
        String[] below = {"Daisy", "Elder", "Nettle"};
        for (int i = 0; i < columns.length; i++) {
            page.paragraph(REGULAR, BODY, columns[i], NARROW_WIDTH, TOP, 30, above[i]);
        }
        page.text(REGULAR, BODY, columns[0], LABEL_ROW, "4.");
        page.text(REGULAR, BODY, columns[1], LABEL_ROW, "7.");
        page.text(BOLD, HEADING, columns[2], LABEL_ROW + HEADING_ABOVE_LABEL, "3.2 Lower Plain");
        page.paragraph(REGULAR, BODY, columns[2], NARROW_WIDTH, LABEL_ROW - PARAGRAPH_DISTANCE, 10, below[2]);
        String[] texts = {"Willow notes:", "Rowan notes:"};
        for (int i = 0; i < texts.length; i++) {
            page.text(ITALIC, BODY, columns[i] + ITEM_TEXT_X - LEFT_X, LABEL_ROW, texts[i]);
            page.paragraph(REGULAR, BODY, columns[i], NARROW_WIDTH, LABEL_ROW - PARAGRAPH_DISTANCE, 10, below[i]);
        }

        String markdown = write("labels-of-two-columns", page);

        assertTrue(hasLine(markdown, "4. Willow notes:") && hasLine(markdown, "7. Rowan notes:"),
            ITEM + ":\n" + markdown);
    }

    /** Variant that main gets right: the page paints the label just before the text of its item. */
    @Test
    void labelPaintedWithItsTextKeepsTheColumnsApart() throws IOException {
        String markdown = convert("label-with-text", Paint.LABEL_WITH_ITS_TEXT_AFTER_THE_RIGHT_COLUMN);

        assertTrue(markdown.contains("4. Willow notes:"), ITEM + ":\n" + markdown);
        assertOrder(COLUMNS, markdown, EXPECTED);
    }

    /**
     * Output that must stay: on a page of one column, the text of a list item starts more than 6 font sizes after
     * its label, and no other text is on the row. The label and its text stay one line.
     */
    @Test
    void labelFarFromItsTextStaysWithItsText() throws IOException {
        Page page = new Page();
        float baseline = TOP;
        for (String name : new String[] {"Apple", "Birch", "Cedar"}) {
            page.text(REGULAR, BODY, LEFT_X, baseline, (page.labels + 1) + ".");
            page.labels++;
            page.text(REGULAR, BODY, LEFT_X + 70, baseline, name + " shows the layers of the coast");
            baseline -= 2 * LINE_DISTANCE;
        }

        String markdown = write("label-far-from-text", page);

        for (String item : new String[] {"1. Apple", "2. Birch", "3. Cedar"}) {
            assertTrue(markdown.contains(item), ITEM + ":\n" + markdown);
        }
    }

    /**
     * The page of the issue. The left column has four list items, each with its paragraph; the right column has a
     * heading, a paragraph, the heading on the row of the label of the last item, and a paragraph.
     */
    private static String convert(String name, Paint paint) throws IOException {
        Page page = new Page();
        float baseline = TOP;
        String[][] items = {{"Apple", "Alder"}, {"Birch", "Beech"}, {"Cedar", "Cherry"}};
        int[] lineCounts = {8, 8, 16};
        for (int i = 0; i < items.length; i++) {
            page.item(baseline, items[i][0] + " notes:");
            baseline = page.paragraph(REGULAR, BODY, LEFT_X, baseline - PARAGRAPH_DISTANCE, lineCounts[i], items[i][1]);
            baseline -= PARAGRAPH_DISTANCE + 4;
        }
        page.text(BOLD, HEADING, RIGHT_X, TOP + 2, "2.1 Upper Plain");
        page.paragraph(REGULAR, BODY, RIGHT_X, TOP - 18, 35, "Maple");
        if (paint == Paint.LABEL_BEFORE_THE_HEADING) {
            page.text(REGULAR, BODY, LEFT_X, LABEL_ROW, "4.");
        }
        float headingBaseline = LABEL_ROW + HEADING_ABOVE_LABEL;
        page.text(BOLD, HEADING, RIGHT_X, headingBaseline, "2.1.1 Survey Methods and Their");
        page.text(BOLD, HEADING, HEADING_INDENT_X, headingBaseline - HEADING_LINE_DISTANCE, "Results");
        page.paragraph(REGULAR, BODY, RIGHT_X, headingBaseline - HEADING_LINE_DISTANCE - 18, 10, "Nettle");
        if (paint == Paint.LABEL_WITH_ITS_TEXT_AFTER_THE_RIGHT_COLUMN) {
            page.text(REGULAR, BODY, LEFT_X, LABEL_ROW, "4.");
        }
        page.text(ITALIC, BODY, ITEM_TEXT_X, LABEL_ROW, "Willow notes:");
        page.paragraph(REGULAR, BODY, LEFT_X, LABEL_ROW - PARAGRAPH_DISTANCE, 12, "Daisy");
        return write(name, page);
    }

    /** Whether a line of the Markdown output, without its list or heading marker, is the text. */
    private static boolean hasLine(String markdown, String text) {
        for (String line : markdown.split("\n")) {
            if (line.replaceFirst("^(- |#+ )", "").trim().equals(text)) {
                return true;
            }
        }
        return false;
    }

    /** Asserts that the words are in the Markdown output, in their order; if not, fails with the symptom. */
    private static void assertOrder(String symptom, String markdown, String... words) {
        List<String> found = new ArrayList<>();
        for (String word : words) {
            if (markdown.contains(word)) {
                found.add(word);
            }
        }
        found.sort((a, b) -> Integer.compare(markdown.indexOf(a), markdown.indexOf(b)));
        assertEquals(Arrays.asList(words), found, symptom + ":\n" + markdown);
    }

    private static String write(String name, Page sample) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-913-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : sample.lines) {
                    content.beginText();
                    content.setFont(line.font, line.size);
                    content.setWordSpacing(line.wordSpacing);
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
        return new String(Files.readAllBytes(directory.resolve(name + ".md")), StandardCharsets.UTF_8);
    }

    private static float width(PDType1Font font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000 * size;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The lines of one page, in the order that the page paints them. */
    private static final class Page {
        final List<Line> lines = new ArrayList<>();
        int labels;
        int nextWord;

        void text(PDType1Font font, float size, float x, float baseline, String text) {
            lines.add(new Line(font, size, x, baseline, text, 0));
        }

        /** Paints the label of the next list item and, just after it, the text of the item. */
        void item(float baseline, String text) {
            labels++;
            text(REGULAR, BODY, LEFT_X, baseline, labels + ".");
            text(ITALIC, BODY, ITEM_TEXT_X, baseline, text);
        }

        /**
         * Paints a paragraph of the given number of justified lines, from the baseline at the top; the paragraph
         * starts with its name, and its last line has half of the width. Returns the baseline of the last line.
         */
        float paragraph(PDType1Font font, float size, float x, float top, int lineCount, String name) {
            return paragraph(font, size, x, WIDTH, top, lineCount, name);
        }

        /** Paints a paragraph as above, in a column of the given width. */
        float paragraph(PDType1Font font, float size, float x, float width, float top, int lineCount, String name) {
            List<String> text = new ArrayList<>();
            StringBuilder line = new StringBuilder(name);
            while (text.size() < lineCount - 1) {
                String word = WORDS[nextWord++ % WORDS.length];
                if (width(font, size, line + " " + word) > width) {
                    text.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line.append(' ').append(word);
                }
            }
            while (width(font, size, line + " " + WORDS[nextWord % WORDS.length]) < width / 2) {
                line.append(' ').append(WORDS[nextWord++ % WORDS.length]);
            }
            text.add(line.toString());
            float baseline = top;
            for (int i = 0; i < text.size(); i++) {
                String value = text.get(i);
                float spacing = 0;
                if (i < text.size() - 1) {
                    spacing = (width - width(font, size, value)) / (value.split(" ").length - 1);
                }
                lines.add(new Line(font, size, x, baseline, value, spacing));
                if (i < text.size() - 1) {
                    baseline -= LINE_DISTANCE;
                }
            }
            return baseline;
        }
    }

    private static final class Line {
        final PDType1Font font;
        final float size;
        final float x;
        final float baseline;
        final String text;
        final float wordSpacing;

        Line(PDType1Font font, float size, float x, float baseline, String text, float wordSpacing) {
            this.font = font;
            this.size = size;
            this.x = x;
            this.baseline = baseline;
            this.text = text;
            this.wordSpacing = wordSpacing;
        }
    }
}

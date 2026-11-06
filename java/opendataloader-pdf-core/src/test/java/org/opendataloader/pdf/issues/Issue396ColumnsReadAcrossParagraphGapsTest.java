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
 * #396: on a page with two columns of text, the paragraphs of the two columns are read in turn, row by row.
 *
 * <p>The gutter between the columns is narrow (11 pt). Where the gap between two paragraphs of the left column is at
 * the same height as a gap between two paragraphs of the right column, a horizontal gap goes across the page. This
 * gap is wider than the gutter, so the page is cut at the gap first, and the paragraphs above it in both columns are
 * read before the paragraphs below it. A page header that goes across the gutter hides the gutter, and has the same
 * effect. The expected order is the left column, then the right column.
 *
 * <p>The samples have this layout: an A4 page, text in 10 pt Helvetica, the lines 12 pt apart, and 25 pt between the
 * last line of a paragraph and the first line of the next paragraph; the left column at x = 51 and the right column at
 * x = 303, each 241 pt wide, the lines justified. Each paragraph starts with a word that names it. The samples and the
 * outputs stay in {@code target/test-samples/}, in the folders whose names start with {@code issue-396}.
 */
class Issue396ColumnsReadAcrossParagraphGapsTest {

    private static final float SIZE = 10;
    private static final float LINE_DISTANCE = 12;
    private static final float PARAGRAPH_DISTANCE = 25;
    private static final float LEFT_X = 51;
    private static final float RIGHT_X = 303;
    private static final float WIDTH = 241;
    private static final float TOP = 733;
    private static final String SYMPTOM = "the paragraphs of the two columns are not read column by column";
    private static final String FOOTER_SYMPTOM = "the page footer is not after both columns";
    private static final String HEADER = "Notes on the Survey of a Small Bay and its Coast";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final String[] WORDS = ("the survey of the coast shows layers of sand and clay that formed in "
        + "shallow water near the shore over a long time while the sea went up and down and the wind moved the "
        + "dunes along the beach so that each layer keeps a record of the place where it formed").split(" ");

    /** Each column has three paragraphs; only the first gaps of the two columns are at the same height. */
    @Test
    void columnsWithParagraphGapsAtTheSameHeightAreReadColumnByColumn() throws IOException {
        Page page = new Page();
        page.column(LEFT_X, TOP, "Amber", 4, "Basil", 5, "Coral", 3);
        page.column(RIGHT_X, TOP, "Delta", 4, "Ember", 3, "Flint", 5);

        String markdown = convert("level-gaps", page);

        assertOrder(SYMPTOM, markdown, "Amber", "Basil", "Coral", "Delta", "Ember", "Flint");
    }

    /**
     * Each page has a page header across the gutter, 11 pt above the columns. The paragraph gaps of the two columns
     * are at the same height, and the gap is wider than the gap below the header.
     */
    @Test
    void pageHeaderAcrossTheGutterDoesNotHideTheColumns() throws IOException {
        String markdown = convert("header", pagesWithHeader());

        assertOrder(SYMPTOM, markdown, "Amber", "Basil", "Delta", "Ember", "Grove", "Haven", "Ivory", "Jewel");
    }

    /** Guard: without {@code --include-header-footer}, the page header stays out of the output. */
    @Test
    void pageHeaderStaysOutOfTheOutputWithoutIncludeHeaderFooter() throws IOException {
        String markdown = convert("header-out", pagesWithHeader());

        assertTrue(markdown.contains("Amber") && !markdown.contains(HEADER),
            "the page header is in the output without --include-header-footer:\n" + markdown);
    }

    /** Two pages, each with the page header across the gutter, 11 pt above the columns. */
    private static Page[] pagesWithHeader() {
        Page first = new Page();
        first.header(HEADER);
        first.column(LEFT_X, TOP, "Amber", 4, "Basil", 4);
        first.column(RIGHT_X, TOP, "Delta", 4, "Ember", 4);
        Page second = new Page();
        second.header(HEADER);
        second.column(LEFT_X, TOP, "Grove", 5, "Haven", 3);
        second.column(RIGHT_X, TOP, "Ivory", 5, "Jewel", 3);
        return new Page[] {first, second};
    }

    /**
     * With {@code --include-header-footer}, a page footer below the left column, 7 pt below the end of the columns,
     * comes after both columns. The footer is narrower than the left column, so it does not go across the gutter.
     */
    @Test
    void footerBelowTheLeftColumnComesAfterBothColumns() throws IOException {
        Page first = new Page();
        first.column(LEFT_X, TOP, "Amber", 30, "Basil", 26);
        first.column(RIGHT_X, TOP, "Delta", 26, "Ember", 30);
        first.line(REGULAR, 9, LEFT_X, 42, "1 Survey Notes");
        Page second = new Page();
        second.column(LEFT_X, TOP, "Grove", 26, "Haven", 30);
        second.column(RIGHT_X, TOP, "Ivory", 30, "Jewel", 26);
        second.line(REGULAR, 9, LEFT_X, 42, "2 Survey Notes");

        String markdown = convert("footer", true, first, second);

        assertOrder(FOOTER_SYMPTOM, markdown, "Amber", "Basil", "Delta", "Ember", "1 Survey Notes",
            "Grove", "Haven", "Ivory", "Jewel", "2 Survey Notes");
    }

    /**
     * Guard: a heading at the left below two columns that end at the same height starts a new section. The columns
     * of the new section start below the heading. The gap above the heading is less than two font sizes, but the right
     * column has no text below the gap until the new section, so the sections are read one after the other.
     */
    @Test
    void headingBelowColumnsThatEndAtTheSameHeightStartsANewSection() throws IOException {
        Page page = new Page();
        float end = page.column(LEFT_X, TOP, "Amber", 3, "Basil", 4);
        page.column(RIGHT_X, TOP, "Delta", 4, "Ember", 3);
        float heading = end - 30;
        page.line(BOLD, 14, LEFT_X, heading, "Kelp Beds");
        float below = heading - 26;
        page.column(LEFT_X, below, "Grove", 3, "Haven", 4);
        page.column(RIGHT_X, below, "Ivory", 4, "Jewel", 3);

        String markdown = convert("heading", page);

        assertOrder(SYMPTOM, markdown, "Amber", "Basil", "Delta", "Ember", "Kelp Beds", "Grove", "Haven", "Ivory", "Jewel");
    }

    /**
     * Guard: two sections of two columns, with 2.6 font sizes between them in both columns, are read one after the
     * other. A gap between paragraphs is less wide.
     */
    @Test
    void sectionsWithAWideGapBetweenThemAreReadOneAfterTheOther() throws IOException {
        Page page = new Page();
        float end = page.column(LEFT_X, TOP, "Amber", 3, "Basil", 4);
        page.column(RIGHT_X, TOP, "Delta", 4, "Ember", 3);
        float below = end - LINE_DISTANCE - 26;
        page.column(LEFT_X, below, "Grove", 3, "Haven", 4);
        page.column(RIGHT_X, below, "Ivory", 4, "Jewel", 3);

        String markdown = convert("sections", page);

        assertOrder(SYMPTOM, markdown, "Amber", "Basil", "Delta", "Ember", "Grove", "Haven", "Ivory", "Jewel");
    }

    /**
     * Guard: a section of two columns of 16 pt text above a section of two columns of 9 pt text, 24 pt apart, are read
     * one after the other. The gap is more than two font sizes of the 9 pt text at the gap. It is less than two font
     * sizes of the 16 pt text, which has as many paragraphs as the 9 pt text.
     */
    @Test
    void smallTextBelowAWideGapStartsANewSectionAlsoBelowLargerText() throws IOException {
        Page page = new Page();
        page.text(16, 19, 38);
        float end = page.column(LEFT_X, TOP, "Amber", 2, "Basil", 4);
        page.column(RIGHT_X, TOP, "Delta", 3, "Ember", 3);
        page.text(9, 11, 22);
        float below = end - 36;
        page.column(LEFT_X, below, "Grove", 3, "Haven", 2);
        page.column(RIGHT_X, below, "Ivory", 2, "Jewel", 3);

        String markdown = convert("text-sizes", page);

        assertOrder(SYMPTOM, markdown, "Amber", "Basil", "Delta", "Ember", "Grove", "Haven", "Ivory", "Jewel");
    }

    /**
     * Guard: a form with a field of one line in each column of each row is read row by row, as the rows of a table.
     * The fields fill their columns with a line to write on, the gap between the columns is 15 pt, and the rows are
     * 30 pt apart.
     */
    @Test
    void formWithFieldsOfOneLineIsReadRowByRow() throws IOException {
        Page page = new Page();
        String[][] rows = {{"Station", "Weather"}, {"Leader", "Vessel"}, {"Sample", "Remark"}};
        float fieldWidth = WIDTH - 2;
        for (int row = 0; row < rows.length; row++) {
            float baseline = TOP - row * 30;
            page.line(REGULAR, SIZE, LEFT_X, baseline, field(rows[row][0], fieldWidth));
            page.line(REGULAR, SIZE, LEFT_X + fieldWidth + 15, baseline, field(rows[row][1], fieldWidth));
        }

        String markdown = convert("form", page);

        assertOrder(SYMPTOM, markdown, "Station", "Weather", "Leader", "Vessel", "Sample", "Remark");
    }

    /** The name of a field, then a line to write on, to the given width. */
    private static String field(String name, float fieldWidth) {
        StringBuilder field = new StringBuilder(name).append(": ");
        while (width(REGULAR, SIZE, field + "_") <= fieldWidth) {
            field.append('_');
        }
        return field.toString();
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

    private static String convert(String name, Page... pages) throws IOException {
        return convert(name, false, pages);
    }

    private static String convert(String name, boolean includeHeaderFooter, Page... pages) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-396-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (Page sample : pages) {
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
            }
            document.save(pdf.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        config.setIncludeHeaderFooter(includeHeaderFooter);
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

    /** The lines of one page. */
    private static final class Page {
        final List<Line> lines = new ArrayList<>();
        int nextWord;
        float size = SIZE;
        float lineDistance = LINE_DISTANCE;
        float paragraphDistance = PARAGRAPH_DISTANCE;

        /** Sets the font size and the distances of the lines of the next columns. */
        void text(float fontSize, float lines, float paragraphs) {
            size = fontSize;
            lineDistance = lines;
            paragraphDistance = paragraphs;
        }

        void header(String text) {
            float size = 9;
            float x = (PDRectangle.A4.getWidth() - width(REGULAR, size, text)) / 2;
            line(REGULAR, size, x, TOP + 22, text);
        }

        void line(PDType1Font font, float size, float x, float baseline, String text) {
            lines.add(new Line(font, size, x, baseline, text, 0));
        }

        /**
         * Adds the paragraphs to a column: each paragraph as a name and its number of lines. Returns the baseline of
         * the last line.
         */
        float column(float x, float top, Object... paragraphs) {
            float baseline = top;
            for (int index = 0; index < paragraphs.length; index += 2) {
                List<String> text = paragraph((String) paragraphs[index], (Integer) paragraphs[index + 1]);
                for (int i = 0; i < text.size(); i++) {
                    if (index > 0 || i > 0) {
                        baseline -= i == 0 ? paragraphDistance : lineDistance;
                    }
                    String line = text.get(i);
                    float spacing = 0;
                    if (i < text.size() - 1) {
                        spacing = (WIDTH - width(REGULAR, size, line)) / (line.split(" ").length - 1);
                    }
                    lines.add(new Line(REGULAR, size, x, baseline, line, spacing));
                }
            }
            return baseline;
        }

        /** The lines of a paragraph that starts with the name: full lines, then a last line of half the width. */
        private List<String> paragraph(String name, int lineCount) {
            List<String> result = new ArrayList<>();
            StringBuilder line = new StringBuilder(name);
            while (result.size() < lineCount - 1) {
                String word = WORDS[nextWord++ % WORDS.length];
                if (width(REGULAR, size, line + " " + word) > WIDTH) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line.append(' ').append(word);
                }
            }
            while (width(REGULAR, size, line + " " + WORDS[nextWord % WORDS.length]) < WIDTH / 2) {
                line.append(' ').append(WORDS[nextWord++ % WORDS.length]);
            }
            result.add(line.toString());
            return result;
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

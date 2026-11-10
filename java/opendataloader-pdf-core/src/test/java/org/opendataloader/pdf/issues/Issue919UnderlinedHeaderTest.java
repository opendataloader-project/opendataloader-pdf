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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #919: an underline below a column header is not a leader of a table of contents.
 *
 * <p>A table has column headers of two lines: a word, and below it a fiscal year, underlined by a thin filled
 * rectangle. The page draws the headers column by column. The underline of one year is thin and wide, and the years
 * of the columns on its left and right end and start near it, on the same baseline. Thus the underline looked like a
 * leader between two parts of one row, and the years of two columns became one line. The text that an underline
 * underlines lies right above it, on the same baseline; no text lies over a leader.
 *
 * <p>The sample has the measurements of the page in the issue: a letter page, the headers in Times bold of 9 pt,
 * the years 11.7 pt below the words, each underline 32.3 pt wide and 0.8 pt high, 1.3 pt below the baseline of its
 * year, and four rows of numbers below. A guard has a table of contents whose leaders are thin filled rectangles
 * between the titles and the page numbers. The samples and the outputs stay in {@code target/test-samples/}, in the
 * folders whose names start with {@code issue-919}.
 */
class Issue919UnderlinedHeaderTest {

    private static final float TOP = PDRectangle.LETTER.getHeight();
    private static final float SIZE = 9;
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.TIMES_BOLD);
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);

    /** The x of the word and the x of the year of each column, and the word and the year. */
    private static final Object[][] HEADERS = {
        {231.2f, 229.0f, "Plan", "FY 2027"}, {280.9f, 281.3f, "Outlook", "FY 2028"},
        {339.4f, 339.7f, "Outlook", "FY 2029"}, {402.2f, 402.6f, "Outlook", "FY 2030"},
        {458.5f, 458.9f, "Outlook", "FY 2031"}};
    private static final float[] NUMBER_ENDS = {269.5f, 314.5f, 386.4f, 440.4f, 498.8f};

    /** Each header keeps its own year, and no line has the years of two columns. */
    @Test
    void underlinedYearOfAHeaderIsNotALeader() throws IOException {
        List<String> lines = convertText("headers", true);

        for (Object[] header : HEADERS) {
            String expected = header[2] + " " + header[3];
            assertTrue(lines.stream().anyMatch(line -> line.contains(expected)),
                "the underline is read as a leader (no line has \"" + expected + "\"): " + lines);
        }
        for (String line : lines) {
            assertTrue(line.indexOf("FY ") == line.lastIndexOf("FY "),
                "the underline is read as a leader (one line has two years): " + lines);
        }
    }

    /** Guard: a thin filled rectangle between a title and its page number is a leader, also in rows drawn by columns. */
    @Test
    void thinRectangleBetweenATitleAndItsPageNumberIsALeader() throws IOException {
        String text = String.join("\n", convertText("leaders", false));

        String[][] rows = {{"Introduction", "p. 3"}, {"Methods of the survey", "p. 14"}, {"Results", "p. 22"}};
        int previous = -1;
        for (String[] row : rows) {
            String expected = row[0] + " " + row[1];
            int index = text.indexOf(expected);
            assertTrue(index > previous, "a row of the table of contents is not one row in order (\"" + expected
                + "\"): " + text);
            previous = index;
        }
    }

    private static List<String> convertText(String name, boolean headers) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-919-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                if (headers) {
                    drawHeadersAndRows(content);
                } else {
                    drawTableOfContents(content);
                }
            }
            document.save(pdf.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateText(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Files.readAllLines(directory.resolve(name + ".txt")).stream()
            .map(String::trim).filter(line -> !line.isEmpty()).collect(Collectors.toList());
    }

    /** The headers column by column (the word, the year, and its underline), then the rows of numbers. */
    private static void drawHeadersAndRows(PDPageContentStream content) throws IOException {
        for (Object[] header : HEADERS) {
            float yearX = (Float) header[1];
            show(content, BOLD, (Float) header[0], TOP - 114.5f, (String) header[2]);
            show(content, BOLD, yearX, TOP - 126.2f, (String) header[3]);
            content.addRect(yearX, TOP - 127.5f, 32.3f, 0.8f);
            content.fill();
        }
        float top = 129.0f;
        for (int i = 0; i < 4; i++) {
            show(content, REGULAR, 41.4f, TOP - top - 9.2f, "Item " + (i + 1));
            String number = String.format("%,d", (i + 1) * 1111);
            for (float end : NUMBER_ENDS) {
                show(content, REGULAR, end - REGULAR.getStringWidth(number) / 1000 * SIZE, TOP - top - 9.2f, number);
            }
            top += 11.8f;
        }
    }

    /**
     * A table of contents drawn by columns: the titles, then each page number and its leader. Each leader is a
     * filled rectangle 0.8 pt high, from 6 pt after its title to 6 pt before its page number, 1 pt above the baseline.
     * A text between two rectangles keeps them apart, so that each leader is a line art of its own.
     */
    private static void drawTableOfContents(PDPageContentStream content) throws IOException {
        String[][] rows = {{"Introduction", "p. 3"}, {"Methods of the survey", "p. 14"}, {"Results", "p. 22"}};
        float titleX = 72;
        float pageX = 300;
        float[] baselines = {TOP - 200, TOP - 218, TOP - 236};
        for (int i = 0; i < rows.length; i++) {
            show(content, REGULAR, titleX, baselines[i], rows[i][0]);
        }
        for (int i = 0; i < rows.length; i++) {
            show(content, REGULAR, pageX, baselines[i], rows[i][1]);
            float start = titleX + REGULAR.getStringWidth(rows[i][0]) / 1000 * SIZE + 6;
            content.addRect(start, baselines[i] + 1, pageX - 6 - start, 0.8f);
            content.fill();
        }
    }

    private static void show(PDPageContentStream content, PDType1Font font, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, SIZE);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }
}

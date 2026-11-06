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

import org.apache.pdfbox.cos.COSName;
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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #389: a table of contents is read by columns: all the numbers, then all the titles, then all the page numbers.
 *
 * <p>Each row of the table of contents has a number, a title, a dotted leader, and a page number. The page content
 * draws the columns in turn: the page numbers of all the rows, then the titles, then the numbers, and then the
 * leaders. The line detection makes lines only of text that comes one after the other in the page content, so each
 * part of a row is a line of its own, and the paragraphs and lists are made of the columns. The expected output has
 * one line for each row: the number, the title, and the page number, in this order.
 *
 * <p>The samples have this layout: text in 8 pt; each number about 2.4 font sizes before its title and 0.5 pt
 * above the baseline of the title; each leader from 1.5 font sizes after its title to about 1.4 font sizes before
 * its page number, 3 pt above the baseline; and the rows 18 pt apart. Each leader is a dashed line of round dots,
 * 0.5 pt wide, in a marked-content sequence of an artifact, so that each leader is a line-art chunk of its own. The
 * samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start with
 * {@code issue-389}.
 */
class Issue389TocRowsWithLeadersTest {

    private static final float SIZE = 8;
    private static final float ROW_DISTANCE = 18;
    private static final float NUMBER_X = 100;
    private static final float NUMBER_RISE = 0.5f;
    private static final float TITLE_X = 128;
    private static final float LEADER_GAP = 12;
    private static final float LEADER_END_X = 320;
    private static final float LEADER_RISE = 3;
    private static final float PAGE_END_X = 340;
    private static final float TOP = 700;
    private static final String SYMPTOM = "a row of the table of contents is not one line";
    private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final Entry[] ENTRIES = {
        new Entry("01", "Introduction", "03"), new Entry("02", "Background of the plan", "09"),
        new Entry("03", "Methods", "14"), new Entry("04", "Results of the survey", "22"),
        new Entry("05", "Discussion", "27"), new Entry("06", "Appendix tables", "41")};

    /** The page content draws the columns of the rows in turn. */
    @Test
    void rowsDrawnByColumnsAreOneLineEach() throws IOException {
        String markdown = convertMarkdown("columns", tableOfContents(ENTRIES, 0, true));

        assertRowsInOrder(markdown, ENTRIES);
    }

    /**
     * Two tables of contents side by side, 10 pt apart, each drawn by columns: each row has only its own texts,
     * and the rows of the left table come first.
     */
    @Test
    void twoTablesOfContentsSideBySideKeepTheirRows() throws IOException {
        Entry[] left = Arrays.copyOfRange(ENTRIES, 0, 3);
        Entry[] right = Arrays.copyOfRange(ENTRIES, 3, 6);
        List<Item> items = tableOfContents(left, 0, true);
        items.addAll(tableOfContents(right, 250, true));
        String markdown = convertMarkdown("two-tables", items);

        assertRowsInOrder(markdown, ENTRIES);
    }

    /**
     * A column of text to the left of the table of contents, on the same baselines, 20 pt to 38 pt from the numbers:
     * the text stays one paragraph, and no word of it goes into a row.
     */
    @Test
    void textOfAnotherColumnStaysOutOfTheRows() throws IOException {
        String[] text = {"The board meets in the town hall on the", "first day of each month, and all the",
            "points of the agenda are read at the", "start. The vote comes after the talks,",
            "and the secretary writes the notes of", "the meeting for the members in 2026."};
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < text.length; i++) {
            items.add(Item.text(text[i], 140, TOP - i * ROW_DISTANCE));
        }
        items.addAll(tableOfContents(ENTRIES, 200, true));
        String markdown = convertMarkdown("text-and-table", items);

        assertRowsInOrder(markdown, ENTRIES);
        assertTrue(markdown.contains(String.join(" ", text)), "the text of the other column is not one paragraph, "
            + "in its order: " + markdown);
    }

    /** Guard: the rows of a table of contents that the page content draws one after the other stay one line each. */
    @Test
    void rowsDrawnOneAfterTheOtherStayOneLineEach() throws IOException {
        String markdown = convertMarkdown("rows", tableOfContents(ENTRIES, 0, false));

        assertRowsInOrder(markdown, ENTRIES);
    }

    /** Guard: a table of contents of one row, drawn by columns, is one line. */
    @Test
    void oneRowIsOneLine() throws IOException {
        Entry[] entries = {ENTRIES[0]};
        String markdown = convertMarkdown("one-row", tableOfContents(entries, 0, true));

        assertRowsInOrder(markdown, entries);
    }

    /**
     * Guard: the rows of a form, each with two labels and a dotted line after each label, 26 pt apart, drawn by
     * columns, keep the order of the rows: the labels of a row come before the labels of the next row.
     */
    @Test
    void rowsOfAFormWithDottedLinesKeepTheirOrder() throws IOException {
        String[][] rows = {{"Name:", "Date:"}, {"Town:", "Phone:"}, {"Street:", "Code:"}};
        List<Item> items = new ArrayList<>();
        for (int column = 0; column < 2; column++) {
            for (int i = 0; i < rows.length; i++) {
                items.add(Item.text(rows[i][column], 72 + column * 228, TOP - i * 26));
            }
        }
        for (int i = 0; i < rows.length; i++) {
            float baseline = TOP - i * 26;
            items.add(Item.leader(72 + width(rows[i][0]) + 4, 290, baseline));
            items.add(Item.leader(300 + width(rows[i][1]) + 4, 520, baseline));
        }
        String markdown = convertMarkdown("form", items);

        int previous = -1;
        for (String[] row : rows) {
            for (String label : row) {
                int index = markdown.indexOf(label);
                assertTrue(index > previous, "the labels of the form are not in the order of the rows: " + markdown);
                previous = index;
            }
        }
    }

    /**
     * Asserts that each entry is a line of its own, with the number, the title, and the page number in this order
     * and no other text, and that the entries come in their order.
     */
    private static void assertRowsInOrder(String markdown, Entry[] entries) {
        List<String> lines = Arrays.asList(markdown.split("\n"));
        int previous = -1;
        for (Entry entry : entries) {
            Pattern row = Pattern.compile("[^\\p{Alnum}]*" + Pattern.quote(entry.number + " " + entry.title + " "
                + entry.page));
            int index = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (row.matcher(lines.get(i).trim()).matches()) {
                    index = i;
                    break;
                }
            }
            assertTrue(index >= 0, SYMPTOM + " (" + entry.number + " " + entry.title + " " + entry.page + "): "
                + markdown);
            assertTrue(index > previous, "the rows of the table of contents are not in their order: " + markdown);
            previous = index;
        }
    }

    /**
     * The items of a table of contents, moved right by the shift. By columns: the page numbers of all the rows, then
     * the titles, then the numbers, then the leaders. Else each row in turn.
     */
    private static List<Item> tableOfContents(Entry[] entries, float shift, boolean byColumns) {
        List<Item> pages = new ArrayList<>();
        List<Item> titles = new ArrayList<>();
        List<Item> numbers = new ArrayList<>();
        List<Item> leaders = new ArrayList<>();
        for (int i = 0; i < entries.length; i++) {
            Entry entry = entries[i];
            float baseline = TOP - i * ROW_DISTANCE;
            pages.add(Item.text(entry.page, PAGE_END_X + shift - width(entry.page), baseline));
            titles.add(Item.text(entry.title, TITLE_X + shift, baseline));
            numbers.add(Item.text(entry.number, NUMBER_X + shift, baseline + NUMBER_RISE));
            leaders.add(Item.leader(TITLE_X + shift + width(entry.title) + LEADER_GAP, LEADER_END_X + shift,
                baseline + LEADER_RISE));
        }
        List<Item> items = new ArrayList<>();
        if (byColumns) {
            items.addAll(pages);
            items.addAll(titles);
            items.addAll(numbers);
            items.addAll(leaders);
        } else {
            for (int i = 0; i < entries.length; i++) {
                items.addAll(Arrays.asList(numbers.get(i), titles.get(i), pages.get(i), leaders.get(i)));
            }
        }
        return items;
    }

    private static float width(String text) {
        try {
            return FONT.getStringWidth(text) / 1000 * SIZE;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String convertMarkdown(String name, List<Item> items) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-389-" + name);
        Path pdf = writeSample(directory, name, items);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Files.readString(directory.resolve(name + ".md"));
    }

    /** Writes one A4 page with the items in their order: texts in Helvetica, and leaders. */
    private static Path writeSample(Path directory, String name, List<Item> items) throws IOException {
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Item item : items) {
                    if (item.text != null) {
                        content.beginText();
                        content.setFont(FONT, SIZE);
                        content.newLineAtOffset(item.x, item.y);
                        content.showText(item.text);
                        content.endText();
                    } else {
                        content.beginMarkedContent(COSName.ARTIFACT);
                        content.saveGraphicsState();
                        content.setLineWidth(0.5f);
                        content.setLineCapStyle(1);
                        content.setLineDashPattern(new float[] {0, 1.5f}, 0);
                        content.moveTo(item.x, item.y);
                        content.lineTo(item.endX, item.y);
                        content.stroke();
                        content.restoreGraphicsState();
                        content.endMarkedContent();
                    }
                }
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private static final class Entry {
        final String number;
        final String title;
        final String page;

        Entry(String number, String title, String page) {
            this.number = number;
            this.title = title;
            this.page = page;
        }
    }

    /** A text at its baseline, or a leader from x to the end x. */
    private static final class Item {
        final String text;
        final float x;
        final float y;
        final float endX;

        private Item(String text, float x, float y, float endX) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.endX = endX;
        }

        static Item text(String text, float x, float baseline) {
            return new Item(text, x, baseline, x);
        }

        static Item leader(float x, float endX, float y) {
            return new Item(null, x, y, endX);
        }
    }
}

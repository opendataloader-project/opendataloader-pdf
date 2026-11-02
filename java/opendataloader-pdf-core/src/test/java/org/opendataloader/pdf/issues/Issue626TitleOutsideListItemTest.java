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
import com.fasterxml.jackson.databind.node.MissingNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #626: a section title above a list goes into a list item of that list.
 *
 * <p>The PDF of the issue draws a "banner" title at the top of each column, above the first item of a
 * numbered list. In the content stream, the title comes in the middle of the list: after the line of
 * one item and before the next item. The list collects the lines between two items as the content of
 * the first item, so the title becomes the content of an item, far below its place on the page. The
 * expected output keeps the title out of the list: it is an element of the page, before the list.
 *
 * <p>The samples copy the measurements of the issue: a title in 10 pt, 7.5 pt above the first item;
 * items in 9 pt at x = 45.4. The samples and the outputs stay in {@code target/test-samples/}, in the
 * folders whose names start with {@code issue-626}.
 */
class Issue626TitleOutsideListItemTest {

    private static final String TITLE = "Special terms for dental care";
    private static final String RIGHT_TITLE = "Special terms for family accidents";
    private static final float LEFT = 45.4f;
    private static final float RIGHT = 459.2f;

    /** One column: the title is drawn after the line of item 2, but it is above item 1 on the page. */
    @Test
    void titleDrawnInTheListIsAPageElementBeforeTheList() throws IOException {
        JsonNode document = convert("one-column", Config.READING_ORDER_XYCUT, oneColumnLines(LEFT, TITLE));

        assertTitleOutsideTheList(document, TITLE);
        assertTitleBeforeItsList(document, TITLE, "1. Scope of the special terms");
    }

    /** The layout of the issue: two columns, each with a title above its list. */
    @Test
    void titleOfEachColumnIsAPageElementBeforeItsList() throws IOException {
        List<Line> lines = new ArrayList<>(oneColumnLines(LEFT, TITLE));
        lines.addAll(oneColumnLines(RIGHT, RIGHT_TITLE));
        JsonNode document = convert("two-columns", Config.READING_ORDER_XYCUT, lines);

        assertTitleOutsideTheList(document, TITLE);
        assertTitleOutsideTheList(document, RIGHT_TITLE);
        assertTitleBeforeItsList(document, TITLE, "1. Scope of the special terms");
        assertTitleBeforeItsList(document, RIGHT_TITLE, "1. Scope of the special terms");
    }

    /**
     * The right column of the issue: the line of item 2 is only its label, and the title is to the right of
     * that line. The title is above the list and in its column, so it is not in an item.
     */
    @Test
    void titleBesideAShortItemLineIsAPageElementBeforeTheList() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("1. Scope of the special terms for the family of the insured", LEFT, 527, 9),
            new Line("2. Benefits", LEFT, 490, 9),
            new Line(TITLE, LEFT + 103.7f, 545, 10),
            new Line("3. Insured persons of the special terms for the family", LEFT, 453, 9));
        JsonNode document = convert("short-item-line", Config.READING_ORDER_XYCUT, lines);

        assertTitleOutsideTheList(document, TITLE);
        assertTitleBeforeItsList(document, TITLE, "1. Scope of the special terms");
    }

    /**
     * A quote of a magazine (PDFUA-Ref-2-01 of the PDF/UA reference suite): six lines in 13 pt, drawn after
     * item 1. Five lines are above item 1; the last line ends beside its first line. The quote is one paragraph
     * outside the list, also its last line.
     */
    @Test
    void quoteThatEndsBesideAnItemIsOneParagraphOutsideTheList() throws IOException {
        float quote = 140.4f;
        List<Line> lines = Arrays.asList(
            new Line("1. Members meet to share", 28.3f, 420, 10.5f),
            new Line("To meet others who are in", quote, 507, 13),
            new Line("the same situation as the", quote, 491, 13),
            new Line("one that you are in has an", quote, 475, 13),
            new Line("effect on you that is truly", quote, 459, 13),
            new Line("great and of an incredible", quote, 443, 13),
            new Line("value.", quote, 427, 13),
            new Line("2. Members build a network", 28.3f, 390, 10.5f),
            new Line("3. Members join activities", 28.3f, 360, 10.5f));
        JsonNode document = convert("quote", Config.READING_ORDER_XYCUT, lines);

        List<String> path = pathTo(document.path("kids"), "To meet others", new ArrayList<>());
        assertTrue(path != null && path.stream().noneMatch(type -> type.equals("list") || type.equals("list item")),
            "the quote is in a list item: " + path);
        List<String> end = pathTo(document.path("kids"), "of an incredible value.", new ArrayList<>());
        assertTrue(end != null && path.equals(end), "the quote is split: its last line is in another element: "
            + document);
    }

    /** Without a reading order, the title is still not in a list item. */
    @Test
    void titleDrawnInTheListIsNotInAListItemWithoutReadingOrder() throws IOException {
        JsonNode document = convert("reading-order-off", Config.READING_ORDER_OFF, oneColumnLines(LEFT, TITLE));

        assertTitleOutsideTheList(document, TITLE);
    }

    /** The Markdown output shows the title before the first item, as the page does. */
    @Test
    void markdownShowsTheTitleBeforeTheFirstItem() throws IOException {
        List<String> markdown = markdownLines("markdown", oneColumnLines(LEFT, TITLE));

        int title = indexOfLineThatContains(markdown, TITLE);
        int firstItem = indexOfLineThatContains(markdown, "1. Scope of the special terms");
        assertTrue(title >= 0 && firstItem >= 0 && title < firstItem,
            "the title is after the first item in the Markdown: " + markdown);
    }

    /** Guard: a note below an item, which is not a line of the item, stays the content of that item. */
    @Test
    void noteBelowAnItemStaysTheContentOfThatItem() throws IOException {
        List<Line> lines = new ArrayList<>(Arrays.asList(
            new Line("1. Scope of the special terms", LEFT, 527, 9),
            new Line("2. Benefits of the special terms", LEFT, 490, 9),
            new Line("Note on the benefits of the special terms", 62.4f, 466, 8),
            new Line("3. Insured persons of the special terms", LEFT, 430, 9)));
        JsonNode document = convert("note-below-item", Config.READING_ORDER_XYCUT, lines);

        JsonNode item = listItemThatContains(document, "2. Benefits of the special terms");
        assertTrue(item != null && containsText(item.path("kids"), "Note on the benefits"),
            "the note is not the content of item 2: " + document);
    }

    /** Guard: an image below an item, drawn before the next item, stays the content of that item. */
    @Test
    void imageBelowAnItemStaysTheContentOfThatItem() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-626-image-below-item");
        Path pdf = directory.resolve("image-below-item.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(841.9f, 595.3f));
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            BufferedImage image = new BufferedImage(60, 40, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = image.createGraphics();
            graphics.setColor(Color.DARK_GRAY);
            graphics.fillRect(0, 0, 60, 40);
            graphics.dispose();
            PDImageXObject xObject = LosslessFactory.createFromImage(document, image);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                showLine(content, font, new Line("1. Scope of the special terms", LEFT, 527, 9));
                showLine(content, font, new Line("2. Benefits of the special terms", LEFT, 490, 9));
                content.drawImage(xObject, 62.4f, 430, 60, 40);
                showLine(content, font, new Line("3. Insured persons of the special terms", LEFT, 400, 9));
            }
            document.save(pdf.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        JsonNode document = new ObjectMapper().readTree(directory.resolve("image-below-item.json").toFile());

        JsonNode item = listItemThatContains(document, "2. Benefits of the special terms");
        assertTrue(item != null && containsType(item.path("kids"), "image"),
            "the image is not the content of item 2: " + document);
    }

    /**
     * Guard: a list that goes on in the next column. Items 1 and 2 are at the bottom of the left column, the
     * text of item 2 goes on at the top of the right column, and items 3 and 4 follow there. The items of each
     * column stay one list, and no text is lost.
     */
    @Test
    void listThatGoesOnInTheNextColumnKeepsItsItems() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("1. Scope of the special terms for the insured", LEFT, 160, 9),
            new Line("2. Benefits of the special terms for the insured", LEFT, 120, 9),
            new Line("the benefits go on in the next column of the page", RIGHT, 527, 9),
            new Line("3. Insured persons of the special terms for the plan", RIGHT, 490, 9),
            new Line("4. Rules of the special terms for the plan", RIGHT, 453, 9));
        JsonNode document = convert("next-column", Config.READING_ORDER_XYCUT, lines);

        JsonNode first = listItemThatContains(document, "1. Scope of the special terms");
        JsonNode third = listItemThatContains(document, "3. Insured persons of the special terms");
        assertTrue(first != null && third != null, "an item is not in a list: " + document);
        assertTrue(listThatContains(document, "1. Scope").toString().contains("2. Benefits of the special terms"),
            "items 1 and 2 are not one list: " + document);
        assertTrue(listThatContains(document, "3. Insured").toString().contains("4. Rules of the special terms"),
            "items 3 and 4 are not one list: " + document);
        assertTrue(document.toString().contains("the benefits go on in the next column"),
            "the text of the next column is lost: " + document);
    }

    /** Guard: a title drawn before the list in the content stream is a page element before the list. */
    @Test
    void titleDrawnBeforeTheListIsAPageElementBeforeTheList() throws IOException {
        List<Line> lines = new ArrayList<>(oneColumnLines(LEFT, TITLE));
        Line title = lines.remove(2);
        lines.add(0, title);
        JsonNode document = convert("title-first", Config.READING_ORDER_XYCUT, lines);

        assertTitleOutsideTheList(document, TITLE);
        assertTitleBeforeItsList(document, TITLE, "1. Scope of the special terms");
    }

    /**
     * The lines of one column, in the order of the content stream: item 1, item 2, the title, item 3.
     * The title is above item 1 on the page.
     */
    private static List<Line> oneColumnLines(float left, String title) {
        return Arrays.asList(
            new Line("1. Scope of the special terms", left, 527, 9),
            new Line("2. Benefits of the special terms", left, 490, 9),
            new Line(title, left + 103.7f, 545, 10),
            new Line("3. Insured persons of the special terms", left, 453, 9));
    }

    private static void assertTitleOutsideTheList(JsonNode document, String title) {
        List<String> path = pathTo(document.path("kids"), title, new ArrayList<>());
        assertTrue(path != null, "the title is missing: " + document);
        assertTrue(path.stream().noneMatch(type -> type.equals("list") || type.equals("list item")),
            "the title is in a list item: " + String.join(" > ", path));
    }

    /** The title is an element of the page, and a list with the first item comes after it. */
    private static void assertTitleBeforeItsList(JsonNode document, String title, String firstItem) {
        JsonNode kids = document.path("kids");
        int titleIndex = -1;
        for (int i = 0; i < kids.size() && titleIndex < 0; i++) {
            if (kids.get(i).path("content").asText().contains(title)) {
                titleIndex = i;
            }
        }
        boolean listAfterTitle = false;
        for (int i = titleIndex + 1; titleIndex >= 0 && i < kids.size() && !listAfterTitle; i++) {
            JsonNode kid = kids.get(i);
            listAfterTitle = "list".equals(kid.path("type").asText()) && kid.toString().contains(firstItem);
        }
        assertTrue(listAfterTitle, "the title is not a page element before its list: " + document);
    }

    /** The types of the elements from the page to the element whose content contains the text, or null. */
    private static List<String> pathTo(JsonNode nodes, String text, List<String> path) {
        for (JsonNode node : nodes) {
            List<String> here = new ArrayList<>(path);
            here.add(node.path("type").asText());
            if (node.path("content").asText().contains(text)) {
                return here;
            }
            for (String key : new String[] {"kids", "list items"}) {
                List<String> found = pathTo(node.path(key), text, here);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static JsonNode listItemThatContains(JsonNode document, String text) {
        List<JsonNode> stack = new ArrayList<>();
        document.path("kids").forEach(stack::add);
        while (!stack.isEmpty()) {
            JsonNode node = stack.remove(stack.size() - 1);
            if ("list item".equals(node.path("type").asText()) && node.path("content").asText().contains(text)) {
                return node;
            }
            node.path("kids").forEach(stack::add);
            node.path("list items").forEach(stack::add);
        }
        return null;
    }

    private static JsonNode listThatContains(JsonNode document, String text) {
        for (JsonNode kid : document.path("kids")) {
            if ("list".equals(kid.path("type").asText()) && kid.toString().contains(text)) {
                return kid;
            }
        }
        return MissingNode.getInstance();
    }

    private static boolean containsText(JsonNode nodes, String text) {
        return nodes.toString().contains(text);
    }

    private static boolean containsType(JsonNode nodes, String type) {
        for (JsonNode node : nodes) {
            if (type.equals(node.path("type").asText()) || containsType(node.path("kids"), type)) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfLineThatContains(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(text)) {
                return i;
            }
        }
        return -1;
    }

    private static JsonNode convert(String name, String readingOrder, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-626-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        writeSample(pdf, lines);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setReadingOrder(readingOrder);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static List<String> markdownLines(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-626-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        writeSample(pdf, lines);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Arrays.stream(Files.readString(directory.resolve(name + ".md")).split("\n"))
            .filter(line -> !line.isBlank())
            .collect(Collectors.toList());
    }

    /** Writes one page with the lines in the given order, in Helvetica. */
    private static void writeSample(Path pdf, List<Line> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(841.9f, 595.3f));
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    showLine(content, font, line);
                }
            }
            document.save(pdf.toFile());
        }
    }

    private static void showLine(PDPageContentStream content, PDType1Font font, Line line) throws IOException {
        content.beginText();
        content.setFont(font, line.size);
        content.newLineAtOffset(line.x, line.baseline);
        content.showText(line.text);
        content.endText();
    }

    private static final class Line {
        final String text;
        final float x;
        final float baseline;
        final float size;

        Line(String text, float x, float baseline, float size) {
            this.text = text;
            this.x = x;
            this.baseline = baseline;
            this.size = size;
        }
    }
}

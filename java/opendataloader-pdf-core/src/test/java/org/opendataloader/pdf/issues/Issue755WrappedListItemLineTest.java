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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #755: the wrapped line of a list item becomes a new item when it starts with a dash.
 *
 * <p>An agenda item of the issue is too long for one line. Its last word, a dash, goes to the next line, at the
 * start of the item text. A line that starts with a dash is read as the start of a new item, so the line is not
 * added to the text of the item. It becomes the content of the item, and the Markdown writes it as an item of
 * its own. The expected output keeps the line in the text of its item.
 *
 * <p>The samples copy the measurements of the issue: markers "o" at x = 121.5, item text at x = 139, text in
 * 10.73 pt, 12.3 pt between baselines. The text is in Courier, so that the width of a line is its number of
 * characters: a line of 60 characters ends at x = 525.3, as the long lines of the issue do (525.8 and 526.4),
 * and the next word does not fit on it. The samples and the outputs stay in {@code target/test-samples/}, in
 * the folders whose names start with {@code issue-755}.
 */
class Issue755WrappedListItemLineTest {

    private static final float MARKER = 121.5f;
    private static final float TEXT = 139.0f;
    private static final float SIZE = 10.73f;
    private static final float PITCH = 12.3f;
    private static final float TOP = 702.9f;

    /** A first line of 60 characters: the line is full. */
    private static final String WRAPPED = "Choice of the members who speak for the town at the regional";
    private static final String DASH_LINE = "- (one member and one deputy)";
    private static final String FULL = "Sale of a part of the public land of the town to a neighbour";

    /** The layout of the issue: the third item wraps, and its second line starts with a dash. */
    @Test
    void wrappedLineThatStartsWithADashIsInTheMarkdownLineOfItsItem() throws IOException {
        List<String> markdown = markdownLines("markdown", agenda(Arrays.asList(
            new Line(DASH_LINE, TEXT))));

        List<String> withLine = markdown.stream().filter(line -> line.contains("(one member and one deputy)"))
            .collect(Collectors.toList());
        assertEquals(1, withLine.size(), "the wrapped line is a separate Markdown line: " + markdown);
        assertTrue(withLine.get(0).contains("at the regional"),
            "the wrapped line is a separate Markdown line: " + markdown);
    }

    /** The JSON of the issue layout: five items, and the wrapped line is a line of the third item. */
    @Test
    void wrappedLineThatStartsWithADashIsATextLineOfItsItem() throws IOException {
        JsonNode document = convert("json", agenda(Arrays.asList(new Line(DASH_LINE, TEXT))));

        JsonNode item = itemThatContains(document, WRAPPED);
        assertTrue(item != null && item.path("content").asText().contains("(one member and one deputy)")
                && item.path("kids").isEmpty(),
            "the wrapped line is not in the text of its item: " + document);
        assertEquals(5, onlyList(document).path("list items").size(), "the list does not have five items: "
            + document);
    }

    /** Variant: the wrapped item is the last item of the list. */
    @Test
    void wrappedLineOfTheLastItemThatStartsWithADashIsATextLineOfItsItem() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("o", MARKER), new Line("Approval of the minutes of the last meeting", TEXT),
            new Line("o", MARKER), new Line("Allowance of the mayor and of the deputies", TEXT),
            new Line("o", MARKER), new Line(WRAPPED, TEXT),
            new Line(DASH_LINE, TEXT),
            new Line("The minutes are signed by the members.", 69.1f, 2));
        JsonNode document = convert("last-item", lines);

        JsonNode item = itemThatContains(document, WRAPPED);
        assertTrue(item != null && item.path("content").asText().contains("(one member and one deputy)"),
            "the wrapped line is not in the text of its item: " + document);
    }

    /**
     * Guard: lines with a dash at the start of the item text, below a short line, are not a wrap of that line.
     * They can be the items of a nested list, so they do not go into the text of the item.
     */
    @Test
    void dashLinesBelowAShortItemLineStayOutOfItsText() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("o", MARKER), new Line("Approval of the minutes of the last meeting", TEXT),
            new Line("o", MARKER), new Line("Allowance of the mayor", TEXT),
            new Line("- first part of the allowance", TEXT),
            new Line("- second part of the allowance", TEXT),
            new Line("o", MARKER), new Line("Budget of the town for the year", TEXT),
            new Line("o", MARKER), new Line(FULL, TEXT));
        JsonNode document = convert("short-line", lines);

        JsonNode item = itemThatContains(document, "Allowance of the mayor");
        assertTrue(item != null && !item.path("content").asText().contains("part of the allowance"),
            "a dash line below a short item line is in the text of the item: " + document);
        assertTrue(document.toString().contains("first part of the allowance")
                && document.toString().contains("second part of the allowance"),
            "a dash line is lost: " + document);
    }

    /**
     * Guard: in a list of short items, the widest item is not a full line. A body line above the list ends at
     * the right margin, so one dash line below the widest item is not a wrap of its line.
     */
    @Test
    void dashLineBelowTheWidestShortItemStaysOutOfItsText() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("The members of the board meet in the town hall on the first day of each", 69.1f),
            new Line("o", MARKER, 2), new Line("Approval of the minutes", TEXT),
            new Line("o", MARKER), new Line("Allowance of the mayor and of the deputies", TEXT),
            new Line("- part of the allowance", TEXT),
            new Line("o", MARKER), new Line("Budget of the town for the year", TEXT));
        JsonNode document = convert("widest-short-item", lines);

        JsonNode item = itemThatContains(document, "Allowance of the mayor");
        assertTrue(item != null && !item.path("content").asText().contains("part of the allowance"),
            "a dash line below the widest short item is in the text of the item: " + document);
        assertTrue(document.toString().contains("part of the allowance"), "the dash line is lost: " + document);
    }

    /** Guard: a wrapped line without a label stays a line of its item. */
    @Test
    void wrappedLineWithoutALabelIsATextLineOfItsItem() throws IOException {
        JsonNode document = convert("no-label", agenda(Arrays.asList(new Line("energy board of the county", TEXT))));

        JsonNode item = itemThatContains(document, WRAPPED);
        assertTrue(item != null && item.path("content").asText().contains("energy board of the county"),
            "the wrapped line is not in the text of its item: " + document);
    }

    /** Guard: the next item after a full line is a new item. */
    @Test
    void itemAfterAFullLineIsANewItem() throws IOException {
        JsonNode document = convert("full-line", agenda(new ArrayList<>()));

        JsonNode item = itemThatContains(document, WRAPPED);
        assertTrue(item != null && !item.path("content").asText().contains("Choice of one member"),
            "the next item is in the text of the full line: " + document);
        assertEquals(5, onlyList(document).path("list items").size(), "the list does not have five items: "
            + document);
    }

    /** Guard: a dash line at the place of the markers, after a full line, is not a wrap of that line. */
    @Test
    void dashLineAtTheMarkersAfterAFullLineStaysOutOfTheItemText() throws IOException {
        JsonNode document = convert("dash-at-markers", agenda(Arrays.asList(new Line(DASH_LINE, MARKER))));

        JsonNode item = itemThatContains(document, WRAPPED);
        assertTrue(item != null && !item.path("content").asText().contains("(one member and one deputy)"),
            "a dash line at the markers is in the text of the full line: " + document);
        assertTrue(document.toString().contains("(one member and one deputy)"), "the dash line is lost: "
            + document);
    }

    /**
     * Five items, as the agenda of the issue: the third item has a full first line, followed by the given
     * lines; the fifth item has a full line too.
     */
    private static List<Line> agenda(List<Line> afterWrapped) {
        List<Line> lines = new ArrayList<>(Arrays.asList(
            new Line("o", MARKER), new Line("Approval of the minutes of the last meeting", TEXT),
            new Line("o", MARKER), new Line("Allowance of the mayor and of the deputies", TEXT),
            new Line("o", MARKER), new Line(WRAPPED, TEXT)));
        lines.addAll(afterWrapped);
        lines.addAll(Arrays.asList(
            new Line("o", MARKER), new Line("Choice of one member for the land board", TEXT),
            new Line("o", MARKER), new Line(FULL, TEXT)));
        return lines;
    }

    private static JsonNode itemThatContains(JsonNode document, String text) {
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

    private static JsonNode onlyList(JsonNode document) {
        for (JsonNode kid : document.path("kids")) {
            if ("list".equals(kid.path("type").asText())) {
                return kid;
            }
        }
        return document.path("no list");
    }

    private static JsonNode convert(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-755-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        writeSample(pdf, lines);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static List<String> markdownLines(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-755-" + name);
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

    /**
     * Writes one A4 page in Courier. A marker and the text of its item share a baseline; each other line has a
     * baseline of its own, 12.3 pt below the line above it. A line can skip lines below it.
     */
    private static void writeSample(Path pdf, List<Line> lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);
            float baseline = TOP + PITCH;
            boolean afterMarker = false;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    if (!afterMarker) {
                        baseline -= PITCH * line.skip;
                    }
                    content.beginText();
                    content.setFont(font, SIZE);
                    content.newLineAtOffset(line.x, baseline);
                    content.showText(line.text);
                    content.endText();
                    afterMarker = "o".equals(line.text);
                }
            }
            document.save(pdf.toFile());
        }
    }

    private static final class Line {
        final String text;
        final float x;
        final int skip;

        Line(String text, float x) {
            this(text, x, 1);
        }

        Line(String text, float x, int skip) {
            this.text = text;
            this.x = x;
            this.skip = skip;
        }
    }
}

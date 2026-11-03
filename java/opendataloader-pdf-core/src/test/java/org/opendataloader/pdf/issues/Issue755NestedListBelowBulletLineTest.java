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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #755: a list below a line that starts with a bullet is not nested in that line.
 *
 * <p>The agenda of the issue starts with one line "- En public" and, below it, a list with the markers "o". The
 * markers of the list are to the right of the text of the line. The line is the parent item of the list, but
 * the list detection needs two items, so the line stays a paragraph, and the list is a sibling of it. The
 * expected output is one list with one item, the line, and the list in that item.
 *
 * <p>The samples copy the measurements of page 2 of the issue: the dash at x = 86.5 and its text at x = 104, the
 * markers "o" at x = 121.5 and their text at x = 139, text in 10.73 pt, 12.3 pt between baselines. The text is
 * in Courier. The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start
 * with {@code issue-755-nest}.
 */
class Issue755NestedListBelowBulletLineTest {

    private static final float DASH = 86.5f;
    private static final float DASH_TEXT = 104.0f;
    private static final float MARKER = 121.5f;
    private static final float TEXT = 139.0f;
    private static final float BODY = 69.1f;
    private static final float SIZE = 10.73f;
    private static final float PITCH = 12.3f;

    private static final String PARENT = "Public part";
    /** The markers that share the baseline of the text after them. */
    private static final List<String> MARKERS = Arrays.asList("-", "o", "1.", "2.");

    /** The layout of the issue: the list is in the item of the dash line. */
    @Test
    void listBelowADashLineIsInTheItemOfTheLine() throws IOException {
        JsonNode document = convert("json", agenda(new Line("-", DASH), new Line(PARENT, DASH_TEXT)));

        JsonNode item = itemThatContains(document, PARENT);
        assertTrue(item != null && item.path("kids").toString().contains("Approval of the minutes")
                && item.path("kids").toString().contains("Budget of the town"),
            "the list below the dash line is not in its item: " + document);
    }

    /** The Markdown of the issue layout: the items of the list are indented below the dash line. */
    @Test
    void listBelowADashLineIsIndentedInTheMarkdown() throws IOException {
        List<String> markdown = markdownLines("markdown", agenda(new Line("-", DASH), new Line(PARENT, DASH_TEXT)));

        assertTrue(lineThatContains(markdown, PARENT).startsWith("- ")
                && lineThatContains(markdown, "Approval of the minutes").startsWith("  - ")
                && lineThatContains(markdown, "Budget of the town").startsWith("  - "),
            "the list below the dash line is not indented in its item: " + markdown);
    }

    /** Variant: the line starts with a bullet "•" in the same chunk as its text. */
    @Test
    void listBelowABulletLineIsInTheItemOfTheLine() throws IOException {
        JsonNode document = convert("bullet", agenda(new Line("\u2022 " + PARENT, DASH)));

        JsonNode item = itemThatContains(document, PARENT);
        assertTrue(item != null && item.path("kids").toString().contains("Approval of the minutes"),
            "the list below the bullet line is not in its item: " + document);
    }

    /** Variant of the issue: the text of the dash line is underlined, and the underline is drawn before the list. */
    @Test
    void listBelowAnUnderlinedDashLineIsInTheItemOfTheLine() throws IOException {
        JsonNode document = convert("underlined", agenda(new Line("-", DASH), new Line(PARENT, DASH_TEXT, true)));

        JsonNode item = itemThatContains(document, PARENT);
        assertTrue(item != null && item.path("kids").toString().contains("Approval of the minutes"),
            "the list below the underlined dash line is not in its item: " + document);
    }

    /** Variant: two lists follow the dash line, one after the other; both are in its item. */
    @Test
    void twoListsBelowADashLineAreInTheItemOfTheLine() throws IOException {
        List<Line> lines = agenda(new Line("-", DASH), new Line(PARENT, DASH_TEXT));
        lines.addAll(Arrays.asList(
            new Line("o", MARKER), new Line("Approval of the minutes of the last meeting", TEXT),
            new Line("o", MARKER), new Line("Allowance of the mayor and of the deputies", TEXT),
            new Line("1.", MARKER), new Line("Budget of the town for the year", TEXT),
            new Line("2.", MARKER), new Line("Sale of a part of the public land", TEXT),
            new Line("The secretary of the meeting signs the minutes.", BODY)));
        JsonNode document = convertPages("two-lists", Arrays.asList(lines));

        JsonNode item = itemThatContains(document, PARENT);
        assertTrue(item != null && item.path("kids").toString().contains("Approval of the minutes")
                && item.path("kids").toString().contains("Sale of a part of the public land"),
            "the second list below the dash line is not in its item: " + document);
    }

    /**
     * Guard: a list that goes on in the next page stays linked to its next part. The links between pages are made
     * before the list goes into the item of the dash line.
     */
    @Test
    void listThatGoesOnInTheNextPageKeepsItsLink() throws IOException {
        List<Line> first = agenda(new Line("-", DASH), new Line(PARENT, DASH_TEXT));
        first.addAll(Arrays.asList(
            new Line("o", MARKER), new Line("Approval of the minutes of the last meeting", TEXT),
            new Line("o", MARKER), new Line("Allowance of the mayor and of the deputies", TEXT)));
        List<Line> second = new ArrayList<>(Arrays.asList(
            new Line("o", MARKER), new Line("Budget of the town for the year", TEXT),
            new Line("o", MARKER), new Line("Sale of a part of the public land", TEXT),
            new Line("The secretary of the meeting signs the minutes.", BODY)));
        JsonNode document = convertPages("next-page", Arrays.asList(first, second));

        String json = document.toString();
        assertTrue(json.contains("\"next list id\"") && json.contains("\"previous list id\""),
            "the list on the first page is not linked to its next part: " + document);
    }

    /** Guard: a line without a bullet, above an indented list, stays a paragraph. */
    @Test
    void lineWithoutABulletStaysAParagraph() throws IOException {
        JsonNode document = convert("no-bullet", agenda(new Line(PARENT, DASH_TEXT)));

        assertTrue(itemThatContains(document, PARENT) == null, "a line without a bullet is a list item: "
            + document);
    }

    /** Guard: a numbered line, as a section title, above an indented list, is not a parent item. */
    @Test
    void numberedLineIsNotAParentItem() throws IOException {
        JsonNode document = convert("numbered", agenda(new Line("1. " + PARENT, DASH)));

        JsonNode item = itemThatContains(document, PARENT);
        assertTrue(item == null || !item.path("kids").toString().contains("Approval of the minutes"),
            "the list below a numbered line is in its item: " + document);
    }

    /** Guard: a list whose markers are left of the text of the dash line is not nested in it. */
    @Test
    void listThatIsNotIndentedIsNotNested() throws IOException {
        JsonNode document = convert("not-indented", agenda(new Line("-", DASH), new Line(PARENT, DASH_TEXT)),
            DASH, DASH_TEXT);

        JsonNode item = itemThatContains(document, PARENT);
        assertTrue(item == null || !item.path("kids").toString().contains("Approval of the minutes"),
            "a list that is not indented is in the item of the dash line: " + document);
    }

    /** Guard: a list after an empty line below the dash line is not nested in it. */
    @Test
    void listAfterAGapIsNotNested() throws IOException {
        List<Line> lines = new ArrayList<>(Arrays.asList(new Line("-", DASH), new Line(PARENT, DASH_TEXT)));
        lines.add(new Line("", MARKER));
        lines.add(new Line("", MARKER));
        JsonNode document = convert("gap", agenda(lines.toArray(new Line[0])));

        JsonNode item = itemThatContains(document, PARENT);
        assertTrue(item == null || !item.path("kids").toString().contains("Approval of the minutes"),
            "a list after an empty line is in the item of the dash line: " + document);
    }

    /**
     * The agenda of the issue: a title, the given lines, a list of three items, and a line of body text. A marker
     * "o" and the text of its item share a baseline.
     */
    private static List<Line> agenda(Line... parent) {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line("Agenda of the meeting:", BODY));
        lines.addAll(Arrays.asList(parent));
        return lines;
    }

    private static void addList(List<Line> lines, float marker, float text) {
        lines.addAll(Arrays.asList(
            new Line("o", marker), new Line("Approval of the minutes of the last meeting", text),
            new Line("o", marker), new Line("Allowance of the mayor and of the deputies", text),
            new Line("o", marker), new Line("Budget of the town for the year", text),
            new Line("The secretary of the meeting signs the minutes.", BODY)));
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

    private static String lineThatContains(List<String> lines, String text) {
        return lines.stream().filter(line -> line.contains(text)).findFirst().orElse("<missing: " + text + ">");
    }

    private static JsonNode convert(String name, List<Line> lines) throws IOException {
        return convert(name, lines, MARKER, TEXT);
    }

    private static JsonNode convert(String name, List<Line> lines, float marker, float text) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-755-nest-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        List<Line> all = new ArrayList<>(lines);
        addList(all, marker, text);
        writeSample(pdf, all);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static JsonNode convertPages(String name, List<List<Line>> pages) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-755-nest-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        writePages(pdf, pages);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static List<String> markdownLines(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-755-nest-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        List<Line> all = new ArrayList<>(lines);
        addList(all, MARKER, TEXT);
        writeSample(pdf, all);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Arrays.stream(Files.readString(directory.resolve(name + ".md")).split("\n"))
            .filter(line -> !line.isBlank())
            .collect(Collectors.toList());
    }

    /**
     * Writes one A4 page in Courier, from the baseline of the title of the issue (750.3) down. A marker ("-" or
     * "o") and the text after it share a baseline; each other line is 12.3 pt below the line above it. An empty
     * line only moves the baseline.
     */
    private static void writeSample(Path pdf, List<Line> lines) throws IOException {
        writePages(pdf, Arrays.asList(lines));
    }

    /** Writes one A4 page for each list of lines, as {@link #writeSample} does. */
    private static void writePages(Path pdf, List<List<Line>> pages) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (List<Line> lines : pages) {
                writePage(document, lines);
            }
            document.save(pdf.toFile());
        }
    }

    private static void writePage(PDDocument document, List<Line> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.COURIER);
        float baseline = 750.3f + PITCH;
        boolean afterMarker = false;
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            for (Line line : lines) {
                if (!afterMarker) {
                    baseline -= PITCH;
                }
                afterMarker = MARKERS.contains(line.text);
                if (line.text.isEmpty()) {
                    continue;
                }
                content.beginText();
                content.setFont(font, SIZE);
                content.newLineAtOffset(line.x, baseline);
                content.showText(line.text);
                content.endText();
                if (line.underlined) {
                    // The underline of the issue: 0.2 pt below the baseline, 1.2 pt thick.
                    content.addRect(line.x, baseline - 1.4f, line.text.length() * SIZE * 0.6f, 1.2f);
                    content.fill();
                }
            }
        }
    }

    private static final class Line {
        final String text;
        final float x;
        final boolean underlined;

        Line(String text, float x) {
            this(text, x, false);
        }

        Line(String text, float x, boolean underlined) {
            this.text = text;
            this.x = x;
            this.underlined = underlined;
        }
    }
}

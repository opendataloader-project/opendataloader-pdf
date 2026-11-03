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
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #878: the two columns of a signature block are merged into two lines.
 *
 * <p>At the end of the PDF of #755, two persons sign in two columns: a role and, below it, a name in each column.
 * The roles are on one baseline, and the names are on another baseline, about 165 pt to the right. The line
 * detection makes one line of each baseline, across the gap between the columns, so the output puts the two roles
 * on one line and the two names on the next line. The expected output keeps each role with its name.
 *
 * <p>The samples copy the measurements of page 11 of the issue: the last line of the paragraph at x = 69.1, the
 * roles at x = 103.4 and 310.2, the names at x = 95.9 and 310.2, the baselines 26 pt and 38 pt apart, and text in
 * 10.73 pt. The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start
 * with {@code issue-878}.
 */
class Issue878SignatureBlockColumnsTest {

    private static final float SIZE = 10.73f;
    private static final String LEFT_ROLE = "The Mayor";
    private static final String RIGHT_ROLE = "The Secretary of the meeting";
    private static final String LEFT_NAME = "First Member";
    private static final String RIGHT_NAME = "Second Member";

    /** The layout of the issue: the two roles are not one element. */
    @Test
    void rolesOfTheTwoColumnsAreNotOneElement() throws IOException {
        JsonNode document = convertJson("json", signatureBlock());

        JsonNode role = elementThatContains(document, LEFT_ROLE);
        assertTrue(role != null && !role.path("content").asText().contains(RIGHT_ROLE),
            "the roles of the two columns are one element: " + document);
        assertTrue(role == elementThatContains(document, LEFT_NAME)
                && elementThatContains(document, RIGHT_ROLE) == elementThatContains(document, RIGHT_NAME),
            "a role and its name are not one element: " + document);
    }

    /** The Markdown of the issue layout: each role comes with its name, the left column first. */
    @Test
    void eachRoleComesWithItsName() throws IOException {
        String markdown = convertMarkdown("markdown", signatureBlock());

        int leftRole = markdown.indexOf(LEFT_ROLE);
        int leftName = markdown.indexOf(LEFT_NAME);
        int rightRole = markdown.indexOf(RIGHT_ROLE);
        int rightName = markdown.indexOf(RIGHT_NAME);
        assertTrue(leftRole >= 0 && leftRole < leftName && leftName < rightRole && rightRole < rightName,
            "a role does not come with its name: " + markdown);
        assertTrue(paragraphThatContains(markdown, LEFT_ROLE).contains(LEFT_NAME)
                && paragraphThatContains(markdown, RIGHT_ROLE).contains(RIGHT_NAME),
            "a role and its name are not one paragraph: " + markdown);
    }

    /**
     * Guard (review of #885): a small mark drawn over a word of the left role, as a footnote mark, keeps the
     * word with its role: the word comes before the name below it, not after it in the right column.
     */
    @Test
    void aMarkOverTheLeftRoleKeepsItInTheLeftColumn() throws IOException {
        String markdown = convertMarkdown("mark-over-role", signatureBlock(), (pdf, content) -> {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 6);
            content.newLineAtOffset(135, 610);
            content.showText("1");
            content.endText();
        });

        int word = markdown.indexOf("Mayor");
        assertTrue(word >= 0 && word < markdown.indexOf(LEFT_NAME),
            "a word of the left role is not in the left column: " + markdown);
    }

    /** Guard: the lines of a table of contents, a title and its page number far apart, stay one line each. */
    @Test
    void linesOfATableOfContentsStayOneLine() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("Introduction to the plan of the town", 72, 700), new Line("3", 500, 700),
            new Line("Methods of the board", 72, 672), new Line("5", 500, 672),
            new Line("Results of the vote", 72, 644), new Line("8", 500, 644));
        String markdown = convertMarkdown("toc", lines);

        assertTrue(lineThatContains(markdown, "Methods of the board").contains("5"),
            "a line of the table of contents is split: " + markdown);
    }

    /** Guard: the rows of a form, a label and its value far apart, one row below the other, stay one line each. */
    @Test
    void rowsOfAFormStayOneLine() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("Name of the town", 72, 700), new Line("Old Mill", 300, 700),
            new Line("Number of members", 72, 687), new Line("Fifteen", 300, 687),
            new Line("Day of the meeting", 72, 674), new Line("First Monday", 300, 674));
        String markdown = convertMarkdown("form", lines);

        assertTrue(lineThatContains(markdown, "Number of members").contains("Fifteen"),
            "a row of the form is split: " + markdown);
    }

    /**
     * Guard (review of #885): the lines of a table of contents with page labels that have letters, as {@code ix}
     * and {@code A-12}, far from their titles, 26 pt apart, stay one line each.
     */
    @Test
    void linesOfATableOfContentsWithPageLabelsStayOneLine() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("Preface of the book", 72, 700), new Line("iv", 500, 700),
            new Line("Introduction to the plan", 72, 674), new Line("ix", 500, 674),
            new Line("Appendix of the tables", 72, 648), new Line("A-12", 500, 648));
        String markdown = convertMarkdown("toc-labels", lines, null);

        assertTrue(lineThatContains(markdown, "Introduction to the plan").matches(".*the plan\\s+ix\\b.*"),
            "a line of the table of contents with page labels is split: " + markdown);
    }

    /**
     * Guard (review of #885): each page label of a table of contents stays with its title, also when the next row
     * has a word far from its title. The labels are a word with a digit ({@code A-12}), a letter ({@code A}), two
     * words of a page reference ({@code p. 12} and {@code B 7}), labels with a period ({@code iv.} and
     * {@code A.}), and a letter with a roman numeral ({@code A-IV}). A line without a gap separates the groups,
     * so that each label is the only one that can make its row a row of two columns.
     */
    @Test
    void pageLabelsNextToOtherRowsStayWithTheirTitles() throws IOException {
        String[][] entries = {
            {"Appendix of the tables", "A-12"}, {"Annex of the budget", "A"},
            {"Notes of the secretary", "p. 12"}, {"List of the members", "B 7"},
            {"Preface of the book", "iv."}, {"Foreword of the mayor", "A."}, {"Annex of the laws", "A-IV"}};
        List<Line> lines = new ArrayList<>();
        float baseline = 740;
        for (String[] entry : entries) {
            lines.add(new Line(entry[0], 72, baseline));
            lines.add(new Line(entry[1], 500, baseline));
            lines.add(new Line("Other notes of the board", 72, baseline - 26));
            lines.add(new Line("Notes", 500, baseline - 26));
            lines.add(new Line("The board reads the notes before each meeting.", 72, baseline - 52));
            baseline -= 78;
        }
        String markdown = convertMarkdown("toc-label-forms", lines, null);

        for (String[] entry : entries) {
            String pattern = ".*" + Pattern.quote(entry[0]) + "\\s+" + Pattern.quote(entry[1]) + "(\\s.*)?";
            assertTrue(lineThatContains(markdown, entry[0]).matches(pattern),
                "the page label " + entry[1] + " is not with its title: " + markdown);
        }
    }

    /**
     * Guard (review of #885): an image between the two rows of the signature block, in the order of the page
     * content, keeps its place in the output: after the roles and before the names.
     */
    @Test
    void anImageBetweenTheRowsKeepsItsPlace() throws IOException {
        JsonNode document = convertJson("image-between", signatureBlock(), (pdf, content) -> {
            BufferedImage image = new BufferedImage(100, 20, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 100; x++) {
                image.setRGB(x, x / 5, 0xFFFFFF);
            }
            PDImageXObject xObject = LosslessFactory.createFromImage(pdf, image);
            content.drawImage(xObject, 103.4f, 582, 100, 20);
        });

        List<JsonNode> kids = new ArrayList<>();
        document.path("kids").forEach(kids::add);
        int role = indexOfElementThatContains(kids, LEFT_ROLE);
        int name = indexOfElementThatContains(kids, LEFT_NAME);
        int image = -1;
        for (int i = 0; i < kids.size(); i++) {
            if ("image".equals(kids.get(i).path("type").asText())) {
                image = i;
            }
        }
        assertTrue(role >= 0 && role < image && image < name,
            "the image between the rows is not after the roles and before the names: " + document);
    }

    /**
     * Guard (review of #885): two lines with a bullet drawn as a square before each, a text, and a far value,
     * 26 pt apart, keep their bullets: each line stays one element with its value, and it has a list level.
     */
    @Test
    void linesWithDrawnBulletsKeepTheirBullets() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line("First point of the vote", 84, 606, true), new Line("Adopted by the board", 310.2f, 606),
            new Line("Second point of the vote", 84, 580, true), new Line("Rejected by the board", 310.2f, 580));
        JsonNode document = convertJson("drawn-bullets", lines);

        JsonNode first = elementThatContains(document, "First point of the vote");
        assertTrue(first != null && first.path("content").asText().contains("Adopted by the board")
                && first.has("level"), "the first line with a drawn bullet lost its bullet: " + document);
        JsonNode line = elementThatContains(document, "Second point of the vote");
        assertTrue(line != null && line.path("content").asText().contains("Rejected by the board")
                && line.has("level"), "a line with a drawn bullet lost its bullet: " + document);
    }

    /** The last line of the paragraph of the issue, and the signature block below it. */
    private static List<Line> signatureBlock() {
        return new ArrayList<>(Arrays.asList(
            new Line("The members of the board meet in the town hall on the first day of each", 69.1f, 658),
            new Line("month. All the points of the agenda are done, and the meeting closes.", 69.1f, 632),
            new Line(LEFT_ROLE, 103.4f, 606), new Line(RIGHT_ROLE, 310.2f, 606),
            new Line(LEFT_NAME, 95.9f, 568), new Line(RIGHT_NAME, 310.2f, 568)));
    }

    private static String paragraphThatContains(String markdown, String text) {
        return Arrays.stream(markdown.split("\n\n")).filter(paragraph -> paragraph.contains(text)).findFirst()
            .orElse("<missing: " + text + ">");
    }

    private static String lineThatContains(String markdown, String text) {
        return Arrays.stream(markdown.split("\n")).filter(line -> line.contains(text)).findFirst()
            .orElse("<missing: " + text + ">");
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

    private static int indexOfElementThatContains(List<JsonNode> kids, String text) {
        for (int i = 0; i < kids.size(); i++) {
            if (kids.get(i).toString().contains(text)) {
                return i;
            }
        }
        return -1;
    }

    private static JsonNode convertJson(String name, List<Line> lines) throws IOException {
        return convertJson(name, lines, null);
    }

    private static JsonNode convertJson(String name, List<Line> lines, Drawing drawing) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-878-" + name);
        Path pdf = writeSample(directory, name, lines, drawing);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static String convertMarkdown(String name, List<Line> lines) throws IOException {
        return convertMarkdown(name, lines, null);
    }

    private static String convertMarkdown(String name, List<Line> lines, Drawing drawing) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-878-" + name);
        Path pdf = writeSample(directory, name, lines, drawing);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Files.readString(directory.resolve(name + ".md"));
    }

    /**
     * Writes one A4 page in Helvetica, each text at its place. The drawing, if any, comes after the texts above
     * the middle of the page and before the others.
     */
    private static Path writeSample(Path directory, String name, List<Line> lines, Drawing drawing)
            throws IOException {
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                boolean drawn = drawing == null;
                for (Line line : lines) {
                    if (!drawn && line.baseline < 590) {
                        drawing.draw(document, content);
                        drawn = true;
                    }
                    if (line.bullet) {
                        content.addRect(line.x - 12, line.baseline + 2, 4, 4);
                        content.fill();
                    }
                    content.beginText();
                    content.setFont(font, SIZE);
                    content.newLineAtOffset(line.x, line.baseline);
                    content.showText(line.text);
                    content.endText();
                }
                if (!drawn) {
                    drawing.draw(document, content);
                }
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }

    /** Draws on the page of a sample. */
    private interface Drawing {
        void draw(PDDocument document, PDPageContentStream content) throws IOException;
    }

    private static final class Line {
        final String text;
        final float x;
        final float baseline;
        /** Whether a square is drawn before the text, as a bullet. */
        final boolean bullet;

        Line(String text, float x, float baseline) {
            this(text, x, baseline, false);
        }

        Line(String text, float x, float baseline, boolean bullet) {
            this.text = text;
            this.x = x;
            this.baseline = baseline;
            this.bullet = bullet;
        }
    }
}

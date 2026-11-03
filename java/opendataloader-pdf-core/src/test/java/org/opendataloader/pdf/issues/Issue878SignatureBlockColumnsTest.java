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

    /** The last line of the paragraph of the issue, and the signature block below it. */
    private static List<Line> signatureBlock() {
        return new ArrayList<>(Arrays.asList(
            new Line("The members of the board meet in the town hall on the first day of each", 69.1f, 658),
            new Line("month. All the points of the agenda are done, and the meeting closes.", 69.1f, 632),
            new Line(LEFT_ROLE, 103.4f, 606), new Line(RIGHT_ROLE, 310.2f, 606),
            new Line(LEFT_NAME, 95.9f, 568), new Line(RIGHT_NAME, 310.2f, 568)));
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

    private static JsonNode convertJson(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-878-" + name);
        Path pdf = writeSample(directory, name, lines);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static String convertMarkdown(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-878-" + name);
        Path pdf = writeSample(directory, name, lines);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Files.readString(directory.resolve(name + ".md"));
    }

    /** Writes one A4 page in Helvetica, each text at its place. */
    private static Path writeSample(Path directory, String name, List<Line> lines) throws IOException {
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    content.beginText();
                    content.setFont(font, SIZE);
                    content.newLineAtOffset(line.x, line.baseline);
                    content.showText(line.text);
                    content.endText();
                }
            }
            document.save(pdf.toFile());
        }
        return pdf;
    }

    private static final class Line {
        final String text;
        final float x;
        final float baseline;

        Line(String text, float x, float baseline) {
            this.text = text;
            this.x = x;
            this.baseline = baseline;
        }
    }
}

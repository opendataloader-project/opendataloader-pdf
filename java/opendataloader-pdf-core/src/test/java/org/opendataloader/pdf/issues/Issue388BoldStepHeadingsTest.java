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
 * #388: the heading of a step and the text of the step are one element.
 *
 * <p>Each step has a heading of one line in a bold font and, below it, its text in a regular font, indented. When
 * the text has two lines or more, the paragraph detection takes the heading as the first line of the paragraph of
 * the text, so the heading and the text are one paragraph. The expected output has the heading as an element of
 * its own, and the text as another element. When the headings of steps that follow each other make a list, the
 * paragraph detection gets the text of each step without its heading; then the heading stays an element of its
 * own already.
 *
 * <p>The samples have this layout: text in 8 pt; each heading at x = 72; its text at x = 84, the first baseline
 * 15.5 pt below the baseline of the heading, and the lines 12.5 pt apart; and 30 pt between the steps. The samples
 * and the outputs stay in {@code target/test-samples/}, in the folders whose names start with {@code issue-388}.
 */
class Issue388BoldStepHeadingsTest {

    private static final float SIZE = 8;
    private static final float HEADING_X = 72;
    private static final float TEXT_X = 84;
    private static final float HEADING_TO_TEXT = 15.5f;
    private static final float LINE_DISTANCE = 12.5f;
    private static final float STEP_DISTANCE = 30;
    private static final String SYMPTOM = "the heading of a step and its text are one element";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /** A step with a text of two lines: the heading is an element of its own, without the text of its step. */
    @Test
    void headingOfAStepWithTwoLinesOfTextIsAnElementOfItsOwn() throws IOException {
        String[][] steps = {{"(1) Fill the tank", "Open the cover of the tank and pour clean water",
            "up to the line on the side."}};
        JsonNode document = convertJson("two-lines", steps(steps));

        assertHeadingsAreAlone(document, steps);
    }

    /** A step with a text of three lines: the heading is an element of its own. */
    @Test
    void headingOfAStepWithThreeLinesOfTextIsAnElementOfItsOwn() throws IOException {
        String[][] steps = {{"(1) Clean the filter", "Take the filter out of the machine, and rinse it",
            "under cold water for one minute. Then put the", "filter back, and push it until it stops."}};
        JsonNode document = convertJson("three-lines", steps(steps));

        assertHeadingsAreAlone(document, steps);
    }

    /**
     * Guard: three steps one after the other, each with a text of two lines, keep each heading as an element of its
     * own.
     */
    @Test
    void headingsOfStepsOneAfterTheOtherStayElementsOfTheirOwn() throws IOException {
        String[][] steps = {
            {"(1) Fill the tank", "Open the cover of the tank and pour clean water",
                "up to the line on the side."},
            {"(2) Close the cover", "Push the cover down until you hear a click, and",
                "then turn the knob to the right."},
            {"(3) Read the display", "The display shows the level of the water and the",
                "time until the water is ready."}};
        JsonNode document = convertJson("steps", steps(steps));

        assertHeadingsAreAlone(document, steps);
    }

    /** Guard: a step with a text of one line keeps its heading as an element of its own. */
    @Test
    void headingOfAStepWithOneLineOfTextStaysAnElementOfItsOwn() throws IOException {
        String[][] steps = {{"(1) Turn on the machine", "Push the button on the front of the machine."}};
        JsonNode document = convertJson("one-line", steps(steps));

        assertHeadingsAreAlone(document, steps);
    }

    /**
     * Guard: a paragraph whose first line is indented and starts with a bold word, and then has regular words, stays
     * one paragraph.
     */
    @Test
    void firstLineThatStartsWithABoldWordStaysInItsParagraph() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line(TEXT_X, 700, new Run("Note:", true),
                new Run(" use only clean water in the tank of the", false)),
            new Line(HEADING_X, 700 - LINE_DISTANCE,
                new Run("machine, and change the water each day. Water that", false)),
            new Line(HEADING_X, 700 - 2 * LINE_DISTANCE,
                new Run("stays in the tank for a long time is not safe.", false)));
        JsonNode document = convertJson("bold-word", lines);

        assertTrue(elementThatContains(document, "Note:") == elementThatContains(document, "is not safe."),
            "the paragraph that starts with a bold word is split: " + document);
    }

    /** Guard: a paragraph in one font whose first line is indented stays one paragraph. */
    @Test
    void indentedFirstLineStaysInItsParagraph() throws IOException {
        List<Line> lines = Arrays.asList(
            new Line(TEXT_X, 700, new Run("The machine makes cold water and hot water. It", false)),
            new Line(HEADING_X, 700 - LINE_DISTANCE,
                new Run("keeps the water at the temperature that you set, and", false)),
            new Line(HEADING_X, 700 - 2 * LINE_DISTANCE, new Run("it shows the temperature on the display.", false)));
        JsonNode document = convertJson("indented-first-line", lines);

        assertTrue(elementThatContains(document, "The machine makes")
                == elementThatContains(document, "on the display."),
            "the paragraph with an indented first line is split: " + document);
    }

    /** Asserts that the heading of each step is an element without the text of the step. */
    private static void assertHeadingsAreAlone(JsonNode document, String[][] steps) {
        for (String[] step : steps) {
            JsonNode heading = elementThatContains(document, step[0]);
            assertTrue(heading != null && !heading.path("content").asText().contains(step[1]),
                SYMPTOM + " (" + step[0] + "): " + document);
            assertTrue(elementThatContains(document, step[1]) != null, "the text of the step is lost (" + step[0]
                + "): " + document);
        }
    }

    /** The lines of the steps: each heading in bold, and its text below it, indented. */
    private static List<Line> steps(String[][] steps) {
        List<Line> lines = new ArrayList<>();
        float baseline = 700;
        for (String[] step : steps) {
            lines.add(new Line(HEADING_X, baseline, new Run(step[0], true)));
            baseline -= HEADING_TO_TEXT;
            for (int i = 1; i < step.length; i++) {
                lines.add(new Line(TEXT_X, baseline, new Run(step[i], false)));
                baseline -= LINE_DISTANCE;
            }
            baseline -= STEP_DISTANCE - LINE_DISTANCE;
        }
        return lines;
    }

    /** The deepest element whose content contains the text, or null. */
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
        Path directory = TestSamples.emptyDirectory("issue-388-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    content.beginText();
                    content.newLineAtOffset(line.x, line.baseline);
                    for (Run run : line.runs) {
                        content.setFont(run.bold ? BOLD : REGULAR, line.size);
                        content.showText(run.text);
                    }
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
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static final class Run {
        final String text;
        final boolean bold;

        Run(String text, boolean bold) {
            this.text = text;
            this.bold = bold;
        }
    }

    /** A line at its place: runs of text, each in the bold or the regular font, one after the other. */
    private static final class Line {
        final float x;
        final float baseline;
        final float size;
        final List<Run> runs;

        Line(float x, float baseline, Run... runs) {
            this(x, baseline, SIZE, runs);
        }

        Line(float x, float baseline, float size, Run... runs) {
            this.x = x;
            this.baseline = baseline;
            this.size = size;
            this.runs = Arrays.asList(runs);
        }
    }
}

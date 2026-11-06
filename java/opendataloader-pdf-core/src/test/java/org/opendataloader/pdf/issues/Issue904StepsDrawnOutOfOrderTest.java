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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #904: numbered steps that the page content draws out of their order are nested in the wrong order.
 *
 * <p>A column has the numbered steps (1) to (4), and the page content draws them in the order (1), (3), (2), (4).
 * The list detection makes a list of (3) and (4) first. Then it makes a list of (1) and (2), and the step (1) takes
 * the list of (3) and (4), because that list is between (1) and (2) in the content order. The output has (3) and
 * (4) in the step (1), and (2) after them; when the steps look like headings, the list of (1) and (2) becomes two
 * headings, with the list of (3) and (4) between them. The expected output has one list of the four steps, in their
 * order.
 *
 * <p>The samples have this layout: text in 9 pt; each step number at x = 300, the lines after the first line of a
 * step at x = 314, and the lines 12 pt apart; 40 pt between the first lines of two steps. The samples and the
 * outputs stay in {@code target/test-samples/}, in the folders whose names start with {@code issue-904}.
 */
class Issue904StepsDrawnOutOfOrderTest {

    private static final float SIZE = 9;
    private static final float STEP_X = 300;
    private static final float TEXT_X = 314;
    private static final float LINE_DISTANCE = 12;
    private static final float STEP_DISTANCE = 40;
    private static final float TOP = 700;
    private static final String SYMPTOM = "the steps are not one list in their order";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final String[][] STEPS = {
        {"(1) Connect the power cable when", "the setup of the product is done."},
        {"(2) Read the message on the screen,", "and touch the button below it."},
        {"(3) Touch the start button to fill", "the tank with water.", "Note: do not stop the setup."},
        {"(4) The main screen comes up."}};

    /** Steps of two lines drawn in the order (1), (3), (2), (4): one list of the four steps, in their order. */
    @Test
    void stepsDrawnOutOfOrderAreOneListInOrder() throws IOException {
        JsonNode document = convertJson("out-of-order", steps(STEPS, 0, 2, 1, 3));

        assertStepsInOrder(document, "(1) Connect", "(2) Read", "(3) Touch", "(4) The main");
    }

    /** Steps of one line drawn in the order (1), (3), (2), (4): one list of the four steps, in their order. */
    @Test
    void stepsOfOneLineDrawnOutOfOrderAreOneListInOrder() throws IOException {
        String[][] steps = {{"(1) Connect the power cable."}, {"(2) Read the message."}, {"(3) Touch the start button."},
            {"(4) The main screen comes up."}};
        JsonNode document = convertJson("one-line-out-of-order", steps(steps, 0, 2, 1, 3));

        assertStepsInOrder(document, "(1) Connect", "(2) Read", "(3) Touch", "(4) The main");
    }

    /**
     * A note below the step (2), at the text of the steps, drawn after (2) in the order (1), (3), (2), (4): one list of
     * the four steps, and the note between (2) and (3).
     */
    @Test
    void noteBelowAStepDrawnOutOfOrderStaysBelowItsStep() throws IOException {
        String[][] steps = {STEPS[0], {STEPS[1][0], STEPS[1][1], "- Keep 10 cm free at each side of the product."},
            STEPS[2], STEPS[3]};
        JsonNode document = convertJson("note-out-of-order", steps(steps, 0, 2, 1, 3));

        assertStepsInOrder(document, "(1) Connect", "(2) Read", "(3) Touch", "(4) The main");
        String text = document.toString();
        assertTrue(text.indexOf("(2) Read") < text.indexOf("Keep 10 cm free")
                && text.indexOf("Keep 10 cm free") < text.indexOf("(3) Touch"),
            SYMPTOM + " (the note of (2) is not between (2) and (3)): " + document);
    }

    /** Guard: steps drawn in their order stay one list of the four steps, in their order. */
    @Test
    void stepsDrawnInOrderStayOneListInOrder() throws IOException {
        JsonNode document = convertJson("in-order", steps(STEPS, 0, 1, 2, 3));

        assertStepsInOrder(document, "(1) Connect", "(2) Read", "(3) Touch", "(4) The main");
    }

    /** Guard: a list below a step, indented and numbered from 1 again, stays a list of two items after that step. */
    @Test
    void indentedListBelowAStepStaysAfterTheStep() throws IOException {
        List<Line> lines = new ArrayList<>();
        lines.add(new Line(STEP_X, TOP, "(1) Open the box of the product."));
        lines.add(new Line(TEXT_X, TOP - LINE_DISTANCE, "(1) Cut the tape on the top."));
        lines.add(new Line(TEXT_X, TOP - 2 * LINE_DISTANCE, "(2) Lift the cover of the box."));
        lines.add(new Line(STEP_X, TOP - STEP_DISTANCE, "(2) Take out the product."));
        JsonNode document = convertJson("indented-list", lines);

        JsonNode list = listWithItem(document, "(1) Cut the tape");
        assertNotNull(list, "the indented list is lost: " + document);
        assertEquals(2, list.path("list items").size(), "the indented list is not two items: " + document);
        String text = document.toString();
        assertTrue(text.indexOf("(1) Open the box") < text.indexOf("(1) Cut the tape")
                && text.indexOf("(2) Lift the cover") < text.indexOf("(2) Take out"),
            "the indented list is not between its step and the next step: " + document);
    }

    /** Asserts one list with the four steps, in their order. */
    private static void assertStepsInOrder(JsonNode document, String... steps) {
        JsonNode list = listWithItem(document, steps[0]);
        assertNotNull(list, SYMPTOM + " (no list has the step " + steps[0] + "): " + document);
        JsonNode items = list.path("list items");
        assertEquals(steps.length, items.size(), SYMPTOM + " (the list has " + items.size() + " items): " + document);
        for (int i = 0; i < steps.length; i++) {
            assertTrue(items.get(i).path("content").asText().startsWith(steps[i]),
                SYMPTOM + " (item " + (i + 1) + " is not " + steps[i] + "): " + document);
        }
    }

    /** The outermost list with an item whose own content contains the text, or null. */
    private static JsonNode listWithItem(JsonNode document, String text) {
        List<JsonNode> queue = new ArrayList<>();
        document.path("kids").forEach(queue::add);
        for (int index = 0; index < queue.size(); index++) {
            JsonNode node = queue.get(index);
            for (JsonNode item : node.path("list items")) {
                if (item.path("content").asText().contains(text)) {
                    return node;
                }
            }
            node.path("kids").forEach(queue::add);
            for (JsonNode item : node.path("list items")) {
                item.path("kids").forEach(queue::add);
            }
        }
        return null;
    }

    /**
     * The lines of the steps, each step at its place in the column, in the given order of the page content: the
     * first line of a step at the number, its other lines at the text.
     */
    private static List<Line> steps(String[][] steps, int... order) {
        List<Line> lines = new ArrayList<>();
        for (int step : order) {
            float baseline = TOP - step * STEP_DISTANCE;
            for (int i = 0; i < steps[step].length; i++) {
                lines.add(new Line(i == 0 ? STEP_X : TEXT_X, baseline - i * LINE_DISTANCE, steps[step][i]));
            }
        }
        return lines;
    }

    private static JsonNode convertJson(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-904-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    content.beginText();
                    content.setFont(REGULAR, SIZE);
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
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
    }

    private static final class Line {
        final float x;
        final float baseline;
        final String text;

        Line(float x, float baseline, String text) {
            this.x = x;
            this.baseline = baseline;
            this.text = text;
        }
    }
}

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
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
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

/**
 * #396: on a page with two columns of text, thin rules that the page paints one after the other hide the gutter
 * between the columns, and the paragraphs of the two columns are read in turn, row by row.
 *
 * <p>The page has a rule across both columns above them, and a rule in the left column above a heading. Without the
 * IDs of marked content, veraPDF makes one line art of the paths that a page paints one after the other, with one
 * box around them. This box goes across the gutter, and it covers the first lines of both columns. Thus the page has
 * no vertical gap, and no horizontal gap of 5 pt or more. The expected order is the left column, then the right
 * column. A box that is wider than half of the page and taller than a tenth of it is a background, and it is not in
 * the contents of the page: thus only rules near to each other hide the gutter.
 *
 * <p>The samples have this layout: an A4 page, text in 10 pt Helvetica, the lines 12 pt apart, and 18 pt between the
 * last line of a paragraph and the first line of the next paragraph; the left column at x = 51 and the right column
 * at x = 303, each 241 pt wide, the lines justified; rules 1.02 pt thick. Each paragraph starts with a word that
 * names it. The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start with
 * {@code issue-396-rules}.
 */
class Issue396RulesPaintedTogetherTest {

    private static final float SIZE = 10;
    private static final float LINE_DISTANCE = 12;
    private static final float PARAGRAPH_DISTANCE = 18;
    private static final float LEFT_X = 51;
    private static final float RIGHT_X = 303;
    private static final float WIDTH = 241;
    private static final float PAGE_WIDTH = RIGHT_X + WIDTH - LEFT_X;
    private static final float TOP = 726;
    private static final float RULE_ABOVE_COLUMNS = 741.7f;
    private static final float RULE = 1.02f;
    private static final String SYMPTOM = "the paragraphs of the two columns are not read column by column";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    private static final String[] LEFT = {"Apple", "Birch", "Cedar", "Daisy", "Elder", "Fennel", "Grape", "Hazel",
        "Iris", "Juniper"};
    private static final String[] RIGHT = {"Maple", "Nettle", "Olive", "Poppy", "Quince", "Rowan", "Sage", "Thyme",
        "Umber"};

    private static final String[] WORDS = ("the survey of the coast shows layers of sand and clay that formed in "
        + "shallow water near the shore over a long time while the sea went up and down and the wind moved the "
        + "dunes along the beach so that each layer keeps a record of the place where it formed").split(" ");

    /** How the page paints its rules. */
    private enum Paint {
        /** All rules one after the other in one artifact, before the text, as the document of the issue does. */
        TOGETHER_IN_ONE_ARTIFACT,
        /** All rules one after the other without marked content, before the text. */
        TOGETHER_WITHOUT_MARKED_CONTENT,
        /** Each rule in an artifact of its own. */
        EACH_IN_ITS_OWN_ARTIFACT,
        /**
         * All rules in one form that the page draws two times, each time in an artifact: before the text, and after
         * the text, moved down below the columns. The paints of the two draws have the same operators of the form.
         */
        IN_A_FORM_DRAWN_TWO_TIMES
    }

    /** The distance that the second draw of the form moves the rules down, to below the columns. */
    private static final float SECOND_DRAW_DOWN = 650;

    @Test
    void rulesPaintedTogetherInOneArtifactDoNotHideTheGutter() throws IOException {
        Page page = twoColumns();
        page.rule(LEFT_X, 675.6f, WIDTH, RULE);

        String markdown = convert("rules-artifact", Paint.TOGETHER_IN_ONE_ARTIFACT, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    @Test
    void rulesPaintedTogetherWithoutMarkedContentDoNotHideTheGutter() throws IOException {
        Page page = twoColumns();
        page.rule(LEFT_X, 675.6f, WIDTH, RULE);

        String markdown = convert("rules-unmarked", Paint.TOGETHER_WITHOUT_MARKED_CONTENT, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    /** Variant: the second rule is in the right column, between its first two paragraphs. */
    @Test
    void ruleInTheRightColumnPaintedWithTheRuleAboveTheColumnsDoesNotHideTheGutter() throws IOException {
        Page page = twoColumns();
        page.rule(RIGHT_X, 670.5f, WIDTH, RULE);

        String markdown = convert("rules-right", Paint.TOGETHER_IN_ONE_ARTIFACT, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    /**
     * Variant that main gets right: a rule across both columns below them. The box around the two rules is taller
     * than a tenth of the page, so it is a background, and the background is not in the contents of the page.
     */
    @Test
    void rulesAboveAndBelowTheColumnsPaintedTogetherKeepTheColumnsApart() throws IOException {
        Page page = twoColumns();
        page.rule(LEFT_X, 135, PAGE_WIDTH, RULE);

        String markdown = convert("rules-around", Paint.TOGETHER_IN_ONE_ARTIFACT, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    /** Variant: three rules, one in each column at a different height, below the rule above the columns. */
    @Test
    void threeRulesPaintedTogetherDoNotHideTheGutter() throws IOException {
        Page page = twoColumns();
        page.rule(LEFT_X, 675.6f, WIDTH, RULE);
        page.rule(RIGHT_X, 670.5f, WIDTH, RULE);

        String markdown = convert("rules-three", Paint.TOGETHER_IN_ONE_ARTIFACT, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    /**
     * Variant that main gets right: a rule in the gutter, from the first lines of the columns to below them. The box
     * around the two rules is a background, as above.
     */
    @Test
    void ruleInTheGutterPaintedWithTheRuleAboveTheColumnsKeepsTheColumnsApart() throws IOException {
        Page page = twoColumns();
        page.rule((LEFT_X + WIDTH + RIGHT_X) / 2 - RULE / 2, 100, RULE, 635);

        String markdown = convert("rules-gutter", Paint.TOGETHER_IN_ONE_ARTIFACT, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    /**
     * Variant: the page draws the form of the two rules two times, the second time below the columns. The lines of
     * the second draw have the same paint operators as the lines of the first draw.
     */
    @Test
    void rulesOfAFormDrawnTwoTimesDoNotHideTheGutter() throws IOException {
        Page page = twoColumns();
        page.rule(LEFT_X, 675.6f, WIDTH, RULE);

        String markdown = convert("rules-form", Paint.IN_A_FORM_DRAWN_TWO_TIMES, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    /** Variant that main gets right: each rule is a line art of its own. */
    @Test
    void rulesPaintedInArtifactsOfTheirOwnKeepTheColumnsApart() throws IOException {
        Page page = twoColumns();
        page.rule(LEFT_X, 675.6f, WIDTH, RULE);

        String markdown = convert("rules-apart", Paint.EACH_IN_ITS_OWN_ARTIFACT, page);

        assertOrder(SYMPTOM, markdown, page.expected());
    }

    /** Output that must stay: on a page of one column, the paragraphs between rules keep their order. */
    @Test
    void rulesPaintedTogetherOnAPageOfOneColumnKeepTheParagraphOrder() throws IOException {
        Page page = new Page();
        page.rule(LEFT_X, RULE_ABOVE_COLUMNS, PAGE_WIDTH, RULE);
        float baseline = page.column(LEFT_X, PAGE_WIDTH, TOP, 4, "Apple", "Birch");
        page.rule(LEFT_X, baseline - 20, PAGE_WIDTH / 2, RULE);
        page.heading(LEFT_X, baseline - 36, "Section Two");
        page.column(LEFT_X, PAGE_WIDTH, baseline - 58, 4, "Cedar", "Daisy", "Elder");

        String markdown = convert("rules-one-column", Paint.TOGETHER_IN_ONE_ARTIFACT, page);

        assertOrder("the paragraphs of the column are not read in their order", markdown,
            "Apple", "Birch", "Section Two", "Cedar", "Daisy", "Elder");
    }

    /**
     * The page of the issue: the rule across both columns above them; the left column has a paragraph, then (after
     * the rule that the test adds) a heading and paragraphs of 4 lines; the right column has paragraphs of 5 lines.
     */
    private static Page twoColumns() {
        Page page = new Page();
        page.rule(LEFT_X, RULE_ABOVE_COLUMNS, PAGE_WIDTH, RULE);
        page.column(LEFT_X, WIDTH, TOP, 4, LEFT[0]);
        page.heading(LEFT_X, 660, "Section Two");
        page.column(LEFT_X, WIDTH, 638, 4, Arrays.copyOfRange(LEFT, 1, LEFT.length));
        page.column(RIGHT_X, WIDTH, TOP, 5, RIGHT);
        page.expected.add(LEFT[0]);
        page.expected.add("Section Two");
        page.expected.addAll(Arrays.asList(LEFT).subList(1, LEFT.length));
        page.expected.addAll(Arrays.asList(RIGHT));
        return page;
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

    private static String convert(String name, Paint paint, Page sample) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-396-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFormXObject form = null;
            if (paint == Paint.IN_A_FORM_DRAWN_TWO_TIMES) {
                form = new PDFormXObject(document);
                form.setBBox(PDRectangle.A4);
                form.setResources(new PDResources());
                try (PDFormContentStream formContent = new PDFormContentStream(form)) {
                    for (float[] rule : sample.rules) {
                        formContent.addRect(rule[0], rule[1], rule[2], rule[3]);
                        formContent.fill();
                    }
                }
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                if (form != null) {
                    content.beginMarkedContent(COSName.ARTIFACT);
                    content.drawForm(form);
                    content.endMarkedContent();
                } else {
                    paintRules(content, paint, sample.rules);
                }
                for (Line line : sample.lines) {
                    content.beginText();
                    content.setFont(line.font, line.size);
                    content.setWordSpacing(line.wordSpacing);
                    content.newLineAtOffset(line.x, line.baseline);
                    content.showText(line.text);
                    content.endText();
                }
                if (form != null) {
                    content.saveGraphicsState();
                    content.transform(Matrix.getTranslateInstance(0, -SECOND_DRAW_DOWN));
                    content.beginMarkedContent(COSName.ARTIFACT);
                    content.drawForm(form);
                    content.endMarkedContent();
                    content.restoreGraphicsState();
                }
            }
            document.save(pdf.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new String(Files.readAllBytes(directory.resolve(name + ".md")), StandardCharsets.UTF_8);
    }

    /** Paints the rules in the page contents, in marked content as the paint says. */
    private static void paintRules(PDPageContentStream content, Paint paint, List<float[]> rules) throws IOException {
        if (paint == Paint.TOGETHER_IN_ONE_ARTIFACT) {
            content.beginMarkedContent(COSName.ARTIFACT);
        }
        for (float[] rule : rules) {
            if (paint == Paint.EACH_IN_ITS_OWN_ARTIFACT) {
                content.beginMarkedContent(COSName.ARTIFACT);
            }
            content.addRect(rule[0], rule[1], rule[2], rule[3]);
            content.fill();
            if (paint == Paint.EACH_IN_ITS_OWN_ARTIFACT) {
                content.endMarkedContent();
            }
        }
        if (paint == Paint.TOGETHER_IN_ONE_ARTIFACT) {
            content.endMarkedContent();
        }
    }

    private static float width(PDType1Font font, float size, String text) {
        try {
            return font.getStringWidth(text) / 1000 * size;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The rules and the lines of one page, and the expected order of the paragraph names. */
    private static final class Page {
        final List<float[]> rules = new ArrayList<>();
        final List<Line> lines = new ArrayList<>();
        final List<String> expected = new ArrayList<>();
        int nextWord;

        void rule(float x, float y, float width, float height) {
            rules.add(new float[] {x, y, width, height});
        }

        void heading(float x, float baseline, String text) {
            lines.add(new Line(BOLD, 12, x, baseline, text, 0));
        }

        /**
         * Adds paragraphs of the given number of lines to a column, from the baseline at the top. Each paragraph
         * starts with its name. Returns the baseline of the last line.
         */
        float column(float x, float width, float top, int lineCount, String... names) {
            float baseline = top + PARAGRAPH_DISTANCE;
            for (String name : names) {
                List<String> text = paragraph(name, lineCount, width);
                for (int i = 0; i < text.size(); i++) {
                    baseline -= i == 0 ? PARAGRAPH_DISTANCE : LINE_DISTANCE;
                    String line = text.get(i);
                    float spacing = 0;
                    if (i < text.size() - 1) {
                        spacing = (width - width(REGULAR, SIZE, line)) / (line.split(" ").length - 1);
                    }
                    lines.add(new Line(REGULAR, SIZE, x, baseline, line, spacing));
                }
            }
            return baseline;
        }

        String[] expected() {
            return expected.toArray(new String[0]);
        }

        /** The lines of a paragraph that starts with the name: full lines, then a last line of half the width. */
        private List<String> paragraph(String name, int lineCount, float width) {
            List<String> result = new ArrayList<>();
            StringBuilder line = new StringBuilder(name);
            while (result.size() < lineCount - 1) {
                String word = WORDS[nextWord++ % WORDS.length];
                if (width(REGULAR, SIZE, line + " " + word) > width) {
                    result.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line.append(' ').append(word);
                }
            }
            while (width(REGULAR, SIZE, line + " " + WORDS[nextWord % WORDS.length]) < width / 2) {
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

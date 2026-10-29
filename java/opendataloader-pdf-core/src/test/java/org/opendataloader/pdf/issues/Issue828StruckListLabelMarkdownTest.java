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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #828: with {@code --detect-strikethrough}, a struck label of an unordered list stayed in
 * the Markdown item, and the strikethrough markup was not balanced ({@code - •~~ ~~Item~~ ...}).
 *
 * <p>{@code MarkdownGenerator.writeList} added the {@code ~~} markup to each struck text chunk
 * first. Then it cut the label length (2 for {@code "• "}) from the start of that text. The cut
 * removed the two characters {@code ~~} of the label markup, not the label.
 *
 * <p>The sample is synthetic: one page with three items in Helvetica 11, 18 pt apart. For each
 * line, a 0.75 pt rule goes through the middle of the lowercase letters (3.3 pt above the
 * baseline), as a strikethrough does. The variants draw the rule through the label and the body,
 * the body only, the label only, or not at all. A two-line item has its second line 13 pt below
 * the first, at the start of the body. The output stays in
 * {@code target/test-samples/issue-828-<variant>/}.
 */
class Issue828StruckListLabelMarkdownTest {

    private static final float FONT_SIZE = 11;
    private static final float LEADING = 18;
    private static final float CONTINUATION_LEADING = 13;
    private static final float LEFT = 72;
    private static final float TOP = 700;
    private static final float RULE_RISE = 0.3f * FONT_SIZE;
    private static final float RULE_WIDTH = 0.75f;
    private static final String[] BULLETS = {"•", "•", "•"};
    private static final String[] DASHES = {"-", "-", "-"};
    private static final String[] NUMBERS = {"1.", "2.", "3."};
    private static final String BODY = "Item one of the plan";
    private static final String STRUCK_BODY = "~~Item~~ ~~one~~ ~~of~~ ~~the~~ ~~plan~~";
    private static final String CONTINUATION = "and the next step";
    private static final String STRUCK_CONTINUATION = "~~and~~ ~~the~~ ~~next~~ ~~step~~";

    /** The part of each line that the rule goes through. */
    private enum Rule { NONE, LABEL_AND_BODY, BODY, LABEL }

    private static Path writeList(Path dir, String[] labels, Rule rule) throws IOException {
        return writeList(dir, labels, rule, null);
    }

    /**
     * Writes one item for each label: the label, a space, and {@link #BODY}. If
     * {@code continuation} is not null, each item has it as a second line.
     */
    private static Path writeList(Path dir, String[] labels, Rule rule, String continuation) throws IOException {
        Path file = dir.resolve("list.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float itemPitch = continuation == null ? LEADING : LEADING + CONTINUATION_LEADING;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < labels.length; i++) {
                    float baseline = TOP - i * itemPitch;
                    String label = labels[i] + " ";
                    float bodyStart = LEFT + width(font, label);
                    float bodyEnd = bodyStart + width(font, BODY);
                    showText(cs, font, LEFT, baseline, label + BODY);
                    switch (rule) {
                        case LABEL_AND_BODY:
                            strike(cs, LEFT, bodyEnd, baseline);
                            break;
                        case BODY:
                            strike(cs, bodyStart, bodyEnd, baseline);
                            break;
                        case LABEL:
                            strike(cs, LEFT, LEFT + width(font, labels[i]), baseline);
                            break;
                        default:
                            break;
                    }
                    if (continuation != null) {
                        float secondBaseline = baseline - CONTINUATION_LEADING;
                        showText(cs, font, bodyStart, secondBaseline, continuation);
                        if (rule != Rule.NONE) {
                            strike(cs, bodyStart, bodyStart + width(font, continuation), secondBaseline);
                        }
                    }
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static void showText(PDPageContentStream cs, PDType1Font font, float x, float baseline, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(font, FONT_SIZE);
        cs.newLineAtOffset(x, baseline);
        cs.showText(text);
        cs.endText();
    }

    private static void strike(PDPageContentStream cs, float from, float to, float baseline) throws IOException {
        cs.setLineWidth(RULE_WIDTH);
        cs.moveTo(from, baseline + RULE_RISE);
        cs.lineTo(to, baseline + RULE_RISE);
        cs.stroke();
    }

    private static float width(PDType1Font font, String text) throws IOException {
        return font.getStringWidth(text) / 1000 * FONT_SIZE;
    }

    /** Converts the sample to Markdown and returns its lines that are not empty. */
    private static List<String> markdownLines(Path pdf, boolean detectStrikethrough) throws IOException {
        Config config = new Config();
        config.setOutputFolder(pdf.getParent().toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setImageOutput("off");
        config.setDetectStrikethrough(detectStrikethrough);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Files.readAllLines(pdf.resolveSibling("list.md"), StandardCharsets.UTF_8).stream()
            .filter(line -> !line.isEmpty())
            .collect(Collectors.toList());
    }

    @Test
    void struckBulletLabelIsDroppedAndTheMarkupIsBalanced() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-bullet"), BULLETS, Rule.LABEL_AND_BODY);

        assertThat(markdownLines(pdf, true)).as("Markdown items of a struck list label")
            .containsExactly("- " + STRUCK_BODY, "- " + STRUCK_BODY, "- " + STRUCK_BODY);
    }

    @Test
    void struckDashLabelIsDroppedAndTheMarkupIsBalanced() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-dash"), DASHES, Rule.LABEL_AND_BODY);

        assertThat(markdownLines(pdf, true)).as("Markdown items of a struck list label")
            .containsExactly("- " + STRUCK_BODY, "- " + STRUCK_BODY, "- " + STRUCK_BODY);
    }

    @Test
    void labelStruckAloneIsDroppedWithItsMarkup() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-label-only"), BULLETS, Rule.LABEL);

        assertThat(markdownLines(pdf, true)).as("Markdown items of a struck list label")
            .containsExactly("- " + BODY, "- " + BODY, "- " + BODY);
    }

    @Test
    void struckTwoLineItemKeepsTheMarkupOfBothLines() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-two-lines"), BULLETS, Rule.LABEL_AND_BODY,
            CONTINUATION);

        String item = "- " + STRUCK_BODY + " " + STRUCK_CONTINUATION;
        assertThat(markdownLines(pdf, true)).as("Markdown items of a struck list label")
            .containsExactly(item, item, item);
    }

    @Test
    void bodyStruckAloneKeepsBalancedMarkup() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-body-only"), BULLETS, Rule.BODY);

        assertThat(markdownLines(pdf, true)).as("Markdown items with a struck body")
            .containsExactly("- " + STRUCK_BODY, "- " + STRUCK_BODY, "- " + STRUCK_BODY);
    }

    @Test
    void labelWithoutRuleIsDropped() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-no-rule"), BULLETS, Rule.NONE);

        assertThat(markdownLines(pdf, true)).as("Markdown items without a rule")
            .containsExactly("- " + BODY, "- " + BODY, "- " + BODY);
    }

    @Test
    void twoLineItemWithoutRuleKeepsTheSecondLine() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-two-lines-no-rule"), BULLETS, Rule.NONE,
            CONTINUATION);

        String item = "- " + BODY + " " + CONTINUATION;
        assertThat(markdownLines(pdf, true)).as("Markdown items of two lines without a rule")
            .containsExactly(item, item, item);
    }

    @Test
    void ruleWithoutDetectStrikethroughAddsNoMarkup() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-detection-off"), BULLETS, Rule.LABEL_AND_BODY);

        assertThat(markdownLines(pdf, false)).as("Markdown items without --detect-strikethrough")
            .containsExactly("- " + BODY, "- " + BODY, "- " + BODY);
    }

    @Test
    void struckOrderedLabelStaysWithBalancedMarkup() throws IOException {
        Path pdf = writeList(TestSamples.emptyDirectory("issue-828-ordered"), NUMBERS, Rule.LABEL_AND_BODY);

        assertThat(markdownLines(pdf, true)).as("Markdown items of a struck ordered list")
            .containsExactly("- ~~1.~~ " + STRUCK_BODY, "- ~~2.~~ " + STRUCK_BODY, "- ~~3.~~ " + STRUCK_BODY);
    }
}

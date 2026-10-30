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
import org.apache.pdfbox.util.Matrix;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #642: a small image in a line of text, such as the icon of a button, is written after the paragraph
 * or the list that holds the line. The line has a gap where the image is.
 *
 * <p>The expected Markdown puts the image in the line, at its place between the words: an image is an
 * inline in CommonMark (0.31.2, 6.4 Images). A figure between paragraphs stays a block of its own.
 *
 * <p>Each sample is a Letter page in Helvetica 11, one line each 16 pt, and the paragraph {@link #LAST}
 * below the lines: a page with one paragraph makes it a heading. In a line with {@link #ICON}, the
 * words before and the words after the icon are two text objects, and an 11 pt image is in the gap,
 * on the baseline, as the icons of the issue. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-642}.
 */
class Issue642InlineImageTest {

    /** The place of an 11 pt image in a line, 3 pt after the words before it. */
    private static final String ICON = "{icon}";
    /** The place of an 11 pt image 100 pt after the words of the line: an icon in the margin. */
    private static final String FAR_ICON = "{far}";
    private static final float FONT_SIZE = 11;
    private static final float LEFT = 72;
    private static final float ICON_SIZE = 11;
    private static final String LAST = "The other parts of the plan are on the next pages.";

    @Test
    void iconInALineOfAParagraphIsInTheLine() throws IOException {
        assertEquals(List.of("The status bar shows the state of the upload. If the upload fails for a file, a Retry button"
                + " ![](<paragraph_images/imageFile1.png>) will appear in the window, and it starts the upload again.",
                LAST),
            markdownLines("paragraph",
                "The status bar shows the state of the upload. If the upload",
                "fails for a file, a Retry button " + ICON + "will appear in the window, and",
                "it starts the upload again."));
    }

    /** The case of the issue: a list item with the icon of a button. */
    @Test
    void iconInAListItemIsInTheItem() throws IOException {
        assertEquals(List.of(
                "- Files: the names and the sizes of the files.",
                "- Upload: if the upload fails, a Retry button ![](<list_images/imageFile1.png>) will appear.",
                "- Space: the use of the disk of the computer.",
                LAST),
            markdownLines("list",
                "• Files: the names and the sizes of the files.",
                "• Upload: if the upload fails, a Retry button " + ICON + "will appear.",
                "• Space: the use of the disk of the computer."));
    }

    /** An icon after the last word of a line goes before the first word of the next line. */
    @Test
    void iconAtTheEndOfALineIsBeforeTheNextWord() throws IOException {
        assertEquals(List.of("To keep the changes of the plan, click the Save icon ![](<line-end_images/imageFile1.png>)"
                + " in the tool bar at the top of the page.", LAST),
            markdownLines("line-end",
                "To keep the changes of the plan, click the Save icon " + ICON,
                "in the tool bar at the top of the page."));
    }

    /** A figure between two paragraphs is not in a line, and stays a block. */
    @Test
    void figureBetweenParagraphsStaysABlock() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-642-figure");
        Path pdf = directory.resolve("figure.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, pattern(60));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                text(content, "The screen below shows the home page of the plan.", LEFT, 700);
                content.drawImage(image, LEFT, 600, 150, 80);
                text(content, "The home page has a menu bar and a status bar.", LEFT, 570);
            }
            document.save(pdf.toFile());
        }
        assertEquals(List.of(
                "The screen below shows the home page of the plan.",
                "![](<figure_images/imageFile1.png>)",
                "The home page has a menu bar and a status bar."),
            convert(directory, pdf));
    }

    /**
     * An icon 100 pt after the last word of its line is not between words, and stays a block. The
     * reading order puts it after the column of text on its left.
     */
    @Test
    void iconFarAfterTheWordsOfALineStaysABlock() throws IOException {
        assertEquals(List.of(
                "The status bar shows the state of the upload.",
                LAST,
                "![](<far_images/imageFile1.png>)"),
            markdownLines("far", "The status bar shows the state of the upload. " + FAR_ICON));
    }

    /**
     * A line written up the page, as the stamp in the margin of a paper, is not a line that an image can be in: a figure
     * at its height, far to its right, stays a block. Its box is as high as the line is long.
     */
    @Test
    void figureBesideALineWrittenUpThePageStaysABlock() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-642-vertical");
        Path pdf = directory.resolve("vertical.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, pattern(40));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 20);
                content.setTextMatrix(Matrix.getRotateInstance(Math.PI / 2, 36, 230));
                content.showText("Draft 3 of the plan for the site office, 10 January 2026");
                content.endText();
                text(content, "The figure shows the steps of the plan.", LEFT + 100, 560);
                content.drawImage(image, LEFT + 228, 480, 70, 43);
                text(content, LAST, LEFT + 100, 440);
            }
            document.save(pdf.toFile());
        }
        assertImageIsABlock("![](<vertical_images/imageFile1.png>)", convert(directory, pdf));
    }

    /**
     * A logo at the start of the next column, 18 pt after the last word of a line, is not in the line: a column
     * gap is wider than a space.
     */
    @Test
    void logoInTheNextColumnStaysABlock() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-642-column");
        Path pdf = directory.resolve("column.pdf");
        String first = "Open the menu to see the list of all the";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, pattern(30));
            float logoLeft = LEFT + new PDType1Font(Standard14Fonts.FontName.HELVETICA).getStringWidth(first) / 1000 * FONT_SIZE + 18;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                text(content, first, LEFT, 700);
                text(content, "files, and the parts of them that you can change.", LEFT, 684);
                content.drawImage(image, logoLeft, 699, 60, 13);
                text(content, "The menu is a tool for your work.", logoLeft, 684);
                text(content, LAST, LEFT, 640);
            }
            document.save(pdf.toFile());
        }
        assertImageIsABlock("![](<column_images/imageFile1.png>)", convert(directory, pdf));
    }

    /** The image is a line of the Markdown of its own, and no other line has an image. */
    private static void assertImageIsABlock(String image, List<String> lines) {
        String markdown = String.join("\n", lines);
        assertTrue(lines.contains(image), markdown);
        assertTrue(lines.stream().filter(line -> line.contains("![](")).count() == 1, markdown);
    }

    /** With embedded images, the icon is a data URI in the line. */
    @Test
    void embeddedIconInALineIsInTheLine() throws IOException {
        List<String> lines = markdownLinesWithImages("embedded", Config.IMAGE_OUTPUT_EMBEDDED,
            "The status bar shows the state of the upload. If the upload",
            "fails for a file, a Retry button " + ICON + "will appear in the window, and",
            "it starts the upload again.");
        String markdown = String.join("\n", lines);
        assertEquals(2, lines.size(), markdown);
        assertTrue(lines.get(0).matches(".* a Retry button !\\[\\]\\(data:image/png;base64,[A-Za-z0-9+/=]+\\)"
            + " will appear in the window, and it starts the upload again\\."), markdown);
    }

    /** The JSON output does not change: the icon stays an image element, and the paragraph keeps its text. */
    @Test
    void jsonKeepsTheIconAsAnImageElement() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-642-json");
        Path pdf = directory.resolve("json.pdf");
        writeSample(pdf,
            "The status bar shows the state of the upload. If the upload",
            "fails for a file, a Retry button " + ICON + "will appear in the window, and",
            "it starts the upload again.");
        convert(directory, pdf);
        JsonNode kids = new ObjectMapper().readTree(directory.resolve("json.json").toFile()).get("kids");
        List<String> elements = new ArrayList<>();
        for (JsonNode kid : kids) {
            elements.add(kid.get("type").asText() + ": " + (kid.has("content") ? kid.get("content").asText() : kid.get("source").asText()));
        }
        assertEquals(List.of(
                "paragraph: The status bar shows the state of the upload. If the upload fails for a file, a Retry button"
                    + " will appear in the window, and it starts the upload again.",
                "image: json_images/imageFile1.png",
                "paragraph: " + LAST),
            elements);
    }

    /** Converts a one-page sample with the given lines, and returns the lines of the Markdown that are not empty. */
    private static List<String> markdownLines(String name, String... lines) throws IOException {
        return markdownLinesWithImages(name, Config.IMAGE_OUTPUT_EXTERNAL, lines);
    }

    private static List<String> markdownLinesWithImages(String name, String imageOutput, String... lines)
            throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-642-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        writeSample(pdf, lines);
        return convert(directory, pdf, imageOutput);
    }

    private static List<String> convert(Path directory, Path pdf) throws IOException {
        return convert(directory, pdf, Config.IMAGE_OUTPUT_EXTERNAL);
    }

    private static List<String> convert(Path directory, Path pdf, String imageOutput) throws IOException {
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setImageOutput(imageOutput);
        config.setGenerateMarkdown(true);
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        String name = pdf.getFileName().toString().replaceFirst("\\.pdf$", "");
        return Arrays.stream(Files.readString(directory.resolve(name + ".md")).split("\n"))
            .filter(line -> !line.isBlank())
            .collect(Collectors.toList());
    }

    private static void writeSample(Path pdf, String... lines) throws IOException {
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(document, pattern(22));
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float baseline = 700;
                for (String line : lines) {
                    String marker = line.contains(ICON) ? ICON : line.contains(FAR_ICON) ? FAR_ICON : null;
                    if (marker == null) {
                        text(content, line, LEFT, baseline);
                    } else {
                        String before = line.substring(0, line.indexOf(marker)).trim();
                        String after = line.substring(line.indexOf(marker) + marker.length());
                        text(content, before, LEFT, baseline);
                        float gap = ICON.equals(marker) ? 3 : 100;
                        float iconLeft = LEFT + font.getStringWidth(before) / 1000 * FONT_SIZE + gap;
                        content.drawImage(image, iconLeft, baseline, ICON_SIZE, ICON_SIZE);
                        if (!after.isEmpty()) {
                            text(content, after, iconLeft + ICON_SIZE + 3, baseline);
                        }
                    }
                    baseline -= 16;
                }
                text(content, LAST, LEFT, baseline - 30);
            }
            document.save(pdf.toFile());
        }
    }

    private static void text(PDPageContentStream content, String text, float x, float y) throws IOException {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    /** A square image of two colors, so that no step takes it for a plain background. */
    private static BufferedImage pattern(int pixels) {
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < pixels; x++) {
            for (int y = 0; y < pixels; y++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? 0x2E7D32 : 0xFFFFFF);
            }
        }
        return image;
    }
}

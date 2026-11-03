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
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #755: the Markdown output does not indent a nested list, so a viewer shows it as items of the outer list.
 *
 * <p>The JSON and the HTML output keep a nested list in its item. The Markdown output writes the nested list at
 * the start of the line. CommonMark keeps a block in a list item only when it is indented to the start of the
 * item text. The expected output indents the nested lists of an item by the width of its marker and the space
 * after it. Other blocks of an item, as a paragraph below it, stay at the start of the line: the list processor
 * also gives an item text that is not its own (#876), and an indent would put that text in the item.
 *
 * <p>The first samples copy the measurements of the agenda of the issue: markers "o" at x = 121.5, item text at
 * x = 139, text in 10.73 pt Courier, 12.3 pt between baselines. The note sample copies the layout of the #626
 * sample: items in 9 pt at x = 45.4 and a note in 8 pt at x = 62.4. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-755-md}.
 */
class Issue755MarkdownListItemContentTest {

    private static final float MARKER = 121.5f;
    private static final float TEXT = 139.0f;
    private static final float SIZE = 10.73f;
    private static final float PITCH = 12.3f;
    private static final float TOP = 702.9f;

    /** Two dash lines below an item are a nested list in the JSON; the Markdown indents them in the item. */
    @Test
    void nestedListIsIndentedInItsItem() throws IOException {
        List<String> markdown = markdownLines("nested", Arrays.asList(
            new Line("o", MARKER, SIZE), new Line("Approval of the minutes", TEXT, SIZE),
            new Line("o", MARKER, SIZE), new Line("Allowance of the mayor and of the deputies", TEXT, SIZE),
            new Line("- first part of the allowance", TEXT, SIZE),
            new Line("- second part of the allowance", TEXT, SIZE),
            new Line("o", MARKER, SIZE), new Line("Budget of the town for the year", TEXT, SIZE)), false);

        assertTrue(lineThatContains(markdown, "first part of the allowance").startsWith("  - ")
                && lineThatContains(markdown, "second part of the allowance").startsWith("  - "),
            "the nested list is not indented in its item: " + markdown);
        assertTrue(lineThatContains(markdown, "Budget of the town").startsWith("- "),
            "the next item of the outer list is indented: " + markdown);
    }

    /** Three levels: a list nested in a nested list is indented by both markers. */
    @Test
    void listInANestedListIsIndentedByBothMarkers() throws IOException {
        PDFList innermost = list(NumberingStyleNames.UNORDERED, item("-", "innermost item"));
        PDFList inner = list(NumberingStyleNames.UNORDERED, item("-", "inner item"));
        inner.getListItems().get(0).getContents().add(innermost);
        PDFList outer = list(NumberingStyleNames.UNORDERED, item("-", "outer item"));
        outer.getListItems().get(0).getContents().add(inner);

        List<String> lines = render(false, outer, paragraph("text after the list"));

        assertEquals("  - inner item", lineThatContains(lines, "inner item"),
            "the nested list is not indented in its item: " + lines);
        assertEquals("    - innermost item", lineThatContains(lines, "innermost item"),
            "the list in the nested list is not indented by both markers: " + lines);
    }

    /** Guard: a note below an item, the content of the item in the JSON, stays at the start of the line. */
    @Test
    void paragraphBelowAnItemIsNotIndented() throws IOException {
        List<String> markdown = markdownLines("note", Arrays.asList(
            new Line("1. Scope of the special terms", 45.4f, 9, 527),
            new Line("2. Benefits of the special terms", 45.4f, 9, 490),
            new Line("Note on the benefits of the special terms", 62.4f, 8, 466),
            new Line("3. Insured persons of the special terms", 45.4f, 9, 430)), true);

        assertTrue(lineThatContains(markdown, "Note on the benefits").startsWith("Note"),
            "the paragraph below an item is indented: " + markdown);
    }

    /** Guard: a list after a paragraph of the item stays at the start of the line, as the paragraph does. */
    @Test
    void listAfterAParagraphOfTheItemIsNotIndented() throws IOException {
        PDFList inner = list(NumberingStyleNames.UNORDERED, item("-", "inner item"));
        PDFList outer = list(NumberingStyleNames.UNORDERED, item("-", "outer item"));
        outer.getListItems().get(0).getContents().add(paragraph("text below the outer item"));
        outer.getListItems().get(0).getContents().add(inner);

        List<String> lines = render(false, outer);

        assertEquals("text below the outer item", lineThatContains(lines, "text below the outer item"),
            "the paragraph of the item is indented: " + lines);
        assertEquals("- inner item", lineThatContains(lines, "inner item"),
            "the list after a paragraph of the item is indented: " + lines);
    }

    /** Guard: a list without blocks in its items is written as before, at the start of the line. */
    @Test
    void listWithoutBlocksIsNotIndented() throws IOException {
        List<String> markdown = markdownLines("no-blocks", Arrays.asList(
            new Line("o", MARKER, SIZE), new Line("Approval of the minutes", TEXT, SIZE),
            new Line("o", MARKER, SIZE), new Line("Allowance of the mayor and of the deputies", TEXT, SIZE),
            new Line("o", MARKER, SIZE), new Line("Budget of the town for the year", TEXT, SIZE)), false);

        assertTrue(markdown.stream().allMatch(line -> line.startsWith("- ")),
            "a list without blocks is not at the start of the line: " + markdown);
    }

    /** Guard: a paragraph after a list with a nested list is at the start of the line. */
    @Test
    void paragraphAfterTheListIsNotIndented() throws IOException {
        PDFList list = list(NumberingStyleNames.UNORDERED, item("-", "item"));
        list.getListItems().get(0).getContents().add(list(NumberingStyleNames.UNORDERED, item("-", "inner item")));

        List<String> lines = render(false, list, paragraph("text after the list"));

        assertEquals("text after the list", lineThatContains(lines, "text after the list"),
            "the paragraph after the list is indented: " + lines);
    }

    /** Guard: a tagged numbered list without blocks keeps its numbers as Markdown markers. */
    @Test
    void numberedListWithoutBlocksKeepsItsNumbers() throws IOException {
        PDFList list = list(NumberingStyleNames.ARABIC_NUMBERS, item("1.", " first"), item("2.", " second"));

        List<String> lines = render(true, list);

        assertEquals("1. first", lineThatContains(lines, "first"), "the number of the item changed: " + lines);
        assertEquals("2. second", lineThatContains(lines, "second"), "the number of the item changed: " + lines);
    }

    /** A picture without an image file writes nothing, so the nested list after it is still the first block. */
    @Test
    void nestedListAfterAPictureWithoutAFileIsIndented() throws IOException {
        PDFList outer = list(NumberingStyleNames.UNORDERED, item("-", "outer item"));
        outer.getListItems().get(0).getContents().add(new SemanticPicture(new BoundingBox(), 9999));
        outer.getListItems().get(0).getContents().add(list(NumberingStyleNames.UNORDERED, item("-", "inner item")));

        List<String> lines = render(false, outer);

        assertEquals("  - inner item", lineThatContains(lines, "inner item"),
            "the nested list after a picture that writes nothing is not indented: " + lines);
    }

    /** With image output on, an image without an image file writes nothing too. */
    @Test
    void nestedListAfterAnImageWithoutAFileIsIndented() throws IOException {
        PDFList outer = list(NumberingStyleNames.UNORDERED, item("-", "outer item"));
        outer.getListItems().get(0).getContents().add(new ImageChunk(new BoundingBox(0, 0, 0, 10, 10)));
        outer.getListItems().get(0).getContents().add(list(NumberingStyleNames.UNORDERED, item("-", "inner item")));

        StaticContainers.setKeepLineBreaks(false);
        Generator generator = new Generator(new StringWriter(), new Config(), true);
        List<String> lines = Arrays.stream(generator.render(Arrays.asList(outer)).split("\n"))
            .filter(line -> !line.isBlank()).collect(Collectors.toList());

        assertEquals("  - inner item", lineThatContains(lines, "inner item"),
            "the nested list after an image that writes nothing is not indented: " + lines);
    }

    private static String lineThatContains(List<String> lines, String text) {
        Optional<String> line = lines.stream().filter(value -> value.contains(text)).findFirst();
        return line.orElse("<missing: " + text + ">");
    }

    private static final class Generator extends MarkdownGenerator {
        private final StringWriter out;

        Generator(StringWriter out, Config config) {
            super(out, config);
            this.out = out;
        }

        Generator(StringWriter out, Config config, boolean imageSupported) {
            this(out, config);
            this.isImageSupported = imageSupported;
        }

        String render(List<IObject> contents) throws IOException {
            for (IObject content : contents) {
                if (isSupportedContent(content)) {
                    write(content);
                    writeContentsSeparator();
                }
            }
            markdownWriter.flush();
            return out.toString();
        }
    }

    private static List<String> render(boolean useStructTree, IObject... contents) throws IOException {
        // A paragraph reads this state of the thread; a conversion sets it, these objects have none.
        StaticContainers.setKeepLineBreaks(false);
        Config config = new Config();
        config.setUseStructTree(useStructTree);
        String markdown = new Generator(new StringWriter(), config).render(Arrays.asList(contents));
        return Arrays.stream(markdown.split("\n")).filter(line -> !line.isBlank()).collect(Collectors.toList());
    }

    private static ListItem item(String label, String body) {
        ListItem item = new ListItem(new MultiBoundingBox(), null);
        TextLine line = new TextLine();
        line.add(new TextChunk(label));
        line.add(new TextChunk(body));
        item.add(line);
        item.setLabelLength(label.length());
        return item;
    }

    private static PDFList list(String style, ListItem... items) {
        PDFList list = new PDFList();
        list.setNumberingStyle(style);
        for (ListItem item : items) {
            list.add(item);
        }
        return list;
    }

    private static SemanticParagraph paragraph(String text) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(new TextChunk(text)));
        return paragraph;
    }

    private static List<String> markdownLines(String name, List<Line> lines, boolean helvetica) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-755-md-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        writeSample(pdf, lines, helvetica);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Arrays.stream(Files.readString(directory.resolve(name + ".md")).split("\n"))
            .filter(line -> !line.isBlank())
            .collect(Collectors.toList());
    }

    /**
     * Writes one A4 page. A line with a baseline is at that baseline. Otherwise, a marker "o" and the text of its
     * item share a baseline, and each other line is 12.3 pt below the line above it.
     */
    private static void writeSample(Path pdf, List<Line> lines, boolean helvetica) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(helvetica ? Standard14Fonts.FontName.HELVETICA
                : Standard14Fonts.FontName.COURIER);
            float baseline = TOP + PITCH;
            boolean afterMarker = false;
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    if (line.baseline != null) {
                        baseline = line.baseline;
                    } else if (!afterMarker) {
                        baseline -= PITCH;
                    }
                    content.beginText();
                    content.setFont(font, line.size);
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
        final float size;
        final Float baseline;

        Line(String text, float x, float size) {
            this.text = text;
            this.x = x;
            this.size = size;
            this.baseline = null;
        }

        Line(String text, float x, float size, float baseline) {
            this.text = text;
            this.x = x;
            this.size = size;
            this.baseline = baseline;
        }
    }
}

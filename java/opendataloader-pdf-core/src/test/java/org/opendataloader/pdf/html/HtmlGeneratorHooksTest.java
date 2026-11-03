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
package org.opendataloader.pdf.html;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.utils.TextLinks;
import org.verapdf.wcag.algorithms.entities.BaseObject;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The extension points of {@link HtmlGenerator}: a subclass adds attributes and head content where the
 * hooks are called, and the rest of the HTML stays what the generator writes on its own.
 */
class HtmlGeneratorHooksTest {

    /** A sample with lists, list items, tables, cells, headings, paragraphs and captions. */
    private static final Path SAMPLE = Path.of("../../samples/pdf/1901.03003.pdf");
    private static final String HEAD_CONTENT = "<meta name=\"hook\">\n";
    private static final Pattern MARK = Pattern.compile(" data-hook=\"[^\"]*\"");

    @TempDir
    Path tempDir;

    @AfterEach
    void clearImageContainers() {
        StaticLayoutContainers.clearContainers();
    }

    /** Puts an attribute named after each hook where it is called, and follows the containers. */
    static class MarkingHtmlGenerator extends HtmlGenerator {
        int depth;
        int maxDepth;
        final List<Integer> startedPages = new ArrayList<>();
        final List<String> itemIndexes = new ArrayList<>();

        MarkingHtmlGenerator(File inputPdf, Config config) throws IOException {
            super(inputPdf, config);
        }

        static String mark(String name) {
            return " data-hook=\"" + name + "\"";
        }

        String output() throws IOException {
            return Files.readString(htmlFilePath, StandardCharsets.UTF_8);
        }

        @Override
        protected void writeHeadContent() throws IOException {
            htmlWriter.write(HEAD_CONTENT);
        }

        @Override
        protected void startPage(int pageNumber) {
            startedPages.add(pageNumber);
        }

        @Override
        protected void enterContainer(IObject container) {
            depth++;
            maxDepth = Math.max(maxDepth, depth);
        }

        @Override
        protected void leaveContainer(IObject container) {
            depth--;
        }

        @Override
        protected String getListAttributes(BaseObject list) {
            return super.getListAttributes(list) + mark("list");
        }

        @Override
        protected String getListItemAttributes(List<? extends IObject> items, int index) {
            itemIndexes.add(index + "/" + items.size());
            return mark("item");
        }

        @Override
        protected String getListItemTextAttributes(TextBlock item) {
            return mark("item-text");
        }

        @Override
        protected String getTextNodeAttributes(SemanticTextNode textNode) {
            return mark("text");
        }

        @Override
        protected String getImageAttributes(BaseObject image) {
            return mark("image");
        }

        @Override
        protected String getFormulaAttributes(SemanticFormula formula) {
            return mark("formula");
        }

        @Override
        protected String getTableAttributes(TableBorder table) {
            return mark("table");
        }

        @Override
        protected String getCellAttributes(TableBorderCell cell) {
            return mark("cell");
        }
    }

    private Config config(Path outputFolder) throws IOException {
        Files.createDirectories(outputFolder);
        Config config = new Config();
        config.setOutputFolder(outputFolder.toString());
        return config;
    }

    private static int count(String text, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(text);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    void hooksAddOnlyTheirAttributesAndHeadContent() throws IOException {
        assumeTrue(Files.exists(SAMPLE), "Sample not found: " + SAMPLE.toAbsolutePath());
        File input = SAMPLE.toFile();

        String plain;
        String marked;
        int pages;
        MarkingHtmlGenerator marking;
        try (ExtractionResult extraction = DocumentProcessor.extractContents(input.getPath(), new Config())) {
            pages = extraction.getContents().size();
            try (HtmlGenerator generator = new HtmlGenerator(input, config(tempDir.resolve("plain")))) {
                generator.writeToHtml(extraction.getContents());
            }
            plain = Files.readString(tempDir.resolve("plain/1901.03003.html"), StandardCharsets.UTF_8);

            marking = new MarkingHtmlGenerator(input, config(tempDir.resolve("marked")));
            try (MarkingHtmlGenerator generator = marking) {
                generator.writeToHtml(extraction.getContents());
            }
            marked = marking.output();
        }

        // The hooks change nothing else: without their attributes and head content, the HTML is the same.
        String unmarked = MARK.matcher(marked).replaceAll("").replace(HEAD_CONTENT, "");
        assertThat(unmarked).isEqualTo(plain);

        // Each hook is called for every start tag it is for.
        assertThat(marked).contains("<title>1901.03003.pdf</title>\n" + HEAD_CONTENT + "</head>");
        assertThat(count(plain, "<ul ")).isPositive()
            .isEqualTo(count(marked, "<ul style=\"list-style-type: none;\" data-hook=\"list\">"));
        assertThat(count(plain, "<li>")).isPositive()
            .isEqualTo(count(marked, "<li data-hook=\"item\">"));
        assertThat(count(plain, "<li><p>")).isPositive()
            .isEqualTo(count(marked, "<li data-hook=\"item\"><p data-hook=\"item-text\">"));
        assertThat(count(plain, "<table border=\"1\">")).isPositive()
            .isEqualTo(count(marked, "<table border=\"1\" data-hook=\"table\">"));
        assertThat(count(plain, "<t[dh][ >]")).isPositive()
            .isEqualTo(count(marked, "<t[dh][^>]* data-hook=\"cell\">"));
        assertThat(count(plain, "<h[1-6]>")).isPositive()
            .isEqualTo(count(marked, "<h[1-6] data-hook=\"text\">"));
        assertThat(count(plain, "<figcaption>")).isPositive()
            .isEqualTo(count(marked, "<figcaption data-hook=\"text\">"));
        assertThat(count(marked, "<p data-hook=\"text\">")).isEqualTo(count(plain, "<p>") - count(plain, "<li><p>"));

        // Every page is started once, in order, and every container that is entered is left.
        List<Integer> allPages = new ArrayList<>();
        for (int page = 0; page < pages; page++) {
            allPages.add(page);
        }
        assertThat(pages).isPositive();
        assertThat(marking.startedPages).isEqualTo(allPages);
        assertThat(marking.depth).isZero();
        assertThat(marking.maxDepth).isGreaterThan(1);
        assertThat(marking.itemIndexes).contains("0/1").allMatch(i -> {
            String[] parts = i.split("/");
            return Integer.parseInt(parts[0]) < Integer.parseInt(parts[1]);
        });
    }

    @Test
    void imageFigureAndFormulaCarryTheirHookAttributes() throws IOException {
        Path images = Files.createDirectories(tempDir.resolve("images"));
        Files.write(images.resolve("imageFile1.png"), new byte[]{1});
        Files.write(images.resolve("imageFile2.png"), new byte[]{1});
        StaticLayoutContainers.setImagesDirectory(images.toString());
        BoundingBox box = new BoundingBox(0, 0, 0, 10, 10);
        ImageChunk image = new ImageChunk(box);
        image.setIndex(1);

        MarkingHtmlGenerator marking = new MarkingHtmlGenerator(new File("hooks.pdf"), config(tempDir.resolve("out")));
        try (MarkingHtmlGenerator generator = marking) {
            generator.writeImage(image);
            generator.writePicture(new SemanticPicture(box, 2));
            generator.writeFormula(new SemanticFormula(box, "x^2"));
        }
        String marked = marking.output();

        assertThat(marked).contains("<img src=\"images/imageFile1.png\" alt=\"\" data-hook=\"image\">\n");
        assertThat(marked).contains("<figure data-hook=\"image\">\n<img src=\"images/imageFile2.png\" alt=\"\">\n");
        assertThat(marked).contains("<div class=\"math-display\" data-hook=\"formula\">\\[x^2\\]</div>");
    }

    @Test
    void lineTextTakesTheStyleOfEachChunk() {
        TextLine line = new TextLine(new TextChunk(new BoundingBox(0, 0, 0, 10, 10), "A", 12, 0.0));
        line.add(new TextChunk(new BoundingBox(0, 10, 0, 20, 10), "B", 12, 0.0));
        StringBuilder text = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, text, new TextLinks.OpenLink(),
            chunk -> "A".equals(chunk.getValue()) ? "color: red;" : "");

        assertThat(text.toString()).isEqualTo("<span style=\"color: red;\">A</span>B");
    }

    @Test
    void startTagPutsTheAttributesBeforeItsEnd() {
        assertThat(HtmlGenerator.startTag("<p>", "")).isEqualTo("<p>");
        assertThat(HtmlGenerator.startTag("<p>", null)).isEqualTo("<p>");
        assertThat(HtmlGenerator.startTag("<table border=\"1\">", " id=\"t\"")).isEqualTo("<table border=\"1\" id=\"t\">");
    }
}

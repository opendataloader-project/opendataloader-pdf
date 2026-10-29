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

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.opendataloader.pdf.utils.SanitizationRule;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #814: with {@code --use-struct-tree --sanitize}, the Markdown output of a bullet list stopped or lost text.
 *
 * <p>The Markdown writer removes the {@code Lbl} text of a bullet list ({@code /ListNumbering /Disc}). It
 * removed as many characters as the label had before the sanitizer ran. If a sanitizer match starts in the
 * label, the sanitized item text has another length. A shorter text made the writer fail and stop the file. A
 * longer text lost the start of the replacement.
 *
 * <p>The samples are synthetic: a tagged list of two items. Each item is one {@code LI} with an {@code Lbl} and
 * an {@code LBody}, shown in one text object with no gap between them. The label of the reporter has 30
 * hyphens, and its body is {@code @x.com}: the line is one match of the built-in e-mail rule.
 */
class Issue814SanitizedBulletListMarkdownTest {

    private static final String LONG_LABEL = "------------------------------";
    private static final String SHORT_LABEL = "-----";
    private static final String BULLET = "•";
    private static final String MASKED_ITEM = "- email@example.com";
    private static final String SYMPTOM = "Markdown list items are not the sanitized items";
    private static final Consumer<Config> PLAIN = config -> { };
    private static final Consumer<Config> SANITIZE = config -> config.getFilterConfig().setFilterSensitiveData(true);

    /** The replacement (17 characters) is shorter than the label (30 characters). */
    @Test
    void replacementShorterThanTheLabelKeepsEachItem() throws IOException {
        Output out = convert("long-label", "Disc", labels(LONG_LABEL), "@x.com", SANITIZE);
        assertItems(out.markdown, MASKED_ITEM, MASKED_ITEM);
    }

    /** The replacement (17 characters) is longer than the label (5 characters). */
    @Test
    void replacementLongerThanTheLabelKeepsTheWholeReplacement() throws IOException {
        Output out = convert("short-label", "Disc", labels(SHORT_LABEL), "@x.com", SANITIZE);
        assertItems(out.markdown, MASKED_ITEM, MASKED_ITEM);
    }

    /** A sanitize rule of the user replaces the bullet with a longer text. The writer removes all of that text. */
    @Test
    void ruleThatReplacesTheLabelRemovesTheReplacement() throws IOException {
        List<SanitizationRule> rules = Collections.singletonList(
            new SanitizationRule(Pattern.compile(BULLET), "[bullet]"));
        Output out = convert("replaced-bullet", "Disc", labels(BULLET), "Item",
            SANITIZE.andThen(config -> config.getFilterConfig().setFilterRules(rules)));
        assertItems(out.markdown, "- Item", "- Item");
    }

    @Test
    void labelIsRemovedWithoutSanitize() throws IOException {
        Output out = convert("long-label-plain", "Disc", labels(LONG_LABEL), "@x.com", PLAIN);
        assertItems(out.markdown, "- @x.com", "- @x.com");
    }

    /** The bullet is not part of the match, so the match starts at the end of the label. */
    @Test
    void matchAfterTheLabelKeepsTheLabelOut() throws IOException {
        Output out = convert("bullet-label", "Disc", labels(BULLET), "a@x.com", SANITIZE);
        assertItems(out.markdown, MASKED_ITEM, MASKED_ITEM);
    }

    /** A match on the second line of an item does not change the label of the first line. */
    @Test
    void matchOnALaterLineKeepsTheLabelOut() throws IOException {
        Output out = convert("second-line", "Disc", labels(BULLET), "First line\na@x.com", SANITIZE);
        assertItems(out.markdown, "- First line email@example.com", "- First line email@example.com");
    }

    /** The writer keeps the label of an ordered list. The match "1.a@x.com" starts in the label. */
    @Test
    void orderedListKeepsTheReplacementOfItsLabel() throws IOException {
        Output out = convert("ordered", "Decimal", new String[]{"1.", "2."}, "a@x.com", SANITIZE);
        assertItems(out.markdown, MASKED_ITEM, MASKED_ITEM);
    }

    @Test
    void jsonKeepsEachSanitizedItem() throws IOException {
        Output out = convert("long-label-json", "Disc", labels(LONG_LABEL), "@x.com", SANITIZE);
        assertEquals(2, out.json.split("\"content\" : \"email@example.com\"", -1).length - 1,
            "JSON list items are not the sanitized items: " + out.json);
    }

    /**
     * The tagged PDF marks the glyphs of the source, so a replacement does not move the label glyphs out of
     * the {@code Lbl}. The match "-----@x.com" starts in the label.
     */
    @Test
    void taggedPdfKeepsTheGlyphsOfASanitizedLabelInTheLabel() throws IOException {
        Output out = convert("short-label-tagged", "Disc", labels(SHORT_LABEL), "@x.com",
            SANITIZE.andThen(config -> config.setGenerateTaggedPDF(true)));
        List<String> expected = Arrays.asList("Lbl " + SHORT_LABEL, "LBody @x.com", "Lbl " + SHORT_LABEL, "LBody @x.com");
        assertEquals(expected, getMarkedText(out.dir.resolve("short-label-tagged_tagged.pdf")),
            "the tagged PDF moved the glyphs of a sanitized label");
    }

    private static String[] labels(String label) {
        return new String[]{label, label};
    }

    private static void assertItems(String markdown, String... expected) {
        List<String> items = Arrays.stream(markdown.split("\n"))
            .filter(line -> line.startsWith("-"))
            .collect(Collectors.toList());
        assertEquals(Arrays.asList(expected), items, SYMPTOM + ": " + markdown);
    }

    private static Output convert(String name, String listNumbering, String[] labels, String body,
                                  Consumer<Config> options) throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-814-" + name);
        Path pdf = writeTaggedList(dir.resolve(name + ".pdf"), listNumbering, labels, body);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setUseStructTree(true);
        config.setGenerateMarkdown(true);
        config.setGenerateJSON(true);
        options.accept(config);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new Output(dir, Files.readString(dir.resolve(name + ".md")), Files.readString(dir.resolve(name + ".json")));
    }

    /** The text of each text-show operator on page 1, after the structure type of its marked-content sequence. */
    private static List<String> getMarkedText(Path pdf) throws IOException {
        List<String> result = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            Deque<String> types = new ArrayDeque<>();
            List<Object> operands = new ArrayList<>();
            for (Object token : new PDFStreamParser(doc.getPage(0)).parse()) {
                if (!(token instanceof Operator)) {
                    operands.add(token);
                    continue;
                }
                String operator = ((Operator) token).getName();
                if (("BDC".equals(operator) || "BMC".equals(operator)) && operands.get(0) instanceof COSName) {
                    types.push(((COSName) operands.get(0)).getName());
                } else if ("EMC".equals(operator) && !types.isEmpty()) {
                    types.pop();
                } else if ("Tj".equals(operator) && operands.get(0) instanceof COSString && !types.isEmpty()) {
                    result.add(types.peek() + " " + ((COSString) operands.get(0)).getString());
                }
                operands.clear();
            }
        }
        return result;
    }

    /**
     * Document > L (with {@code listNumbering}) > LI > (Lbl, LBody), one LI for each label. Each line of
     * {@code body} ("\n" separates them) is a line of the LBody. The first line of the LBody follows the label in
     * the same text object, with no gap.
     */
    private static Path writeTaggedList(Path pdf, String listNumbering, String[] labels, String body)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(400, 300));
            doc.addPage(page);
            PDResources resources = new PDResources();
            String font = resources.add(new PDType1Font(Standard14Fonts.FontName.HELVETICA)).getName();
            page.setResources(resources);
            page.setStructParents(0);

            PDStructureTreeRoot root = new PDStructureTreeRoot();
            PDStructureElement document = new PDStructureElement("Document", root);
            root.appendKid(document);
            PDStructureElement list = new PDStructureElement("L", document);
            COSDictionary attributes = new COSDictionary();
            attributes.setName(COSName.O, "List");
            attributes.setName(COSName.getPDFName("ListNumbering"), listNumbering);
            list.getCOSObject().setItem(COSName.A, attributes);
            document.appendKid(list);

            COSArray owners = new COSArray();
            StringBuilder content = new StringBuilder();
            int y = 260;
            for (String label : labels) {
                PDStructureElement item = new PDStructureElement("LI", list);
                list.appendKid(item);
                content.append("BT /").append(font).append(" 12 Tf 40 ").append(y).append(" Td\n");
                content.append(marked(item, "Lbl", "(" + label + ") Tj", page, owners));
                String lines = Arrays.stream(body.split("\n"))
                    .map(line -> "(" + line + ") Tj")
                    .collect(Collectors.joining(" 0 -14 Td "));
                content.append(marked(item, "LBody", lines, page, owners));
                content.append("ET\n");
                y -= 40;
            }
            PDStream stream = new PDStream(doc);
            try (OutputStream os = stream.createOutputStream()) {
                os.write(content.toString().getBytes(StandardCharsets.ISO_8859_1));
            }
            page.setContents(stream);

            COSArray nums = new COSArray();
            nums.add(COSInteger.get(0));
            nums.add(owners);
            COSDictionary parentTree = new COSDictionary();
            parentTree.setItem(COSName.NUMS, nums);
            root.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
            root.getCOSObject().setInt(COSName.PARENT_TREE_NEXT_KEY, 1);
            doc.getDocumentCatalog().setStructureTreeRoot(root);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            doc.getDocumentCatalog().setMarkInfo(markInfo);
            doc.getDocumentCatalog().setLanguage("en");
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** A child of {@code parent} of the type {@code type}, with one marked-content sequence around {@code text}. */
    private static String marked(PDStructureElement parent, String type, String text, PDPage page, COSArray owners) {
        PDStructureElement element = new PDStructureElement(type, parent);
        parent.appendKid(element);
        int mcid = owners.size();
        element.setPage(page);
        element.getCOSObject().setInt(COSName.K, mcid);
        owners.add(element);
        // WinAnsiEncoding has the bullet at 0x95, the same code as in ISO 8859-1 bytes of U+0095.
        return "/" + type + " <</MCID " + mcid + ">> BDC " + text.replace(BULLET, "\u0095") + " EMC\n";
    }

    private static final class Output {
        final Path dir;
        final String markdown;
        final String json;

        Output(Path dir, String markdown, String json) {
            this.dir = dir;
            this.markdown = markdown;
            this.json = json;
        }
    }
}

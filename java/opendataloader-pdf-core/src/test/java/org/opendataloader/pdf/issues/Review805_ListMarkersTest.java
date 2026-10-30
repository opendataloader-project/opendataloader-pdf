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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.Revisions;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDListAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review of PR #805 (list markers in the Markdown output with {@code --use-struct-tree}).
 *
 * <p>The samples are synthetic tagged PDFs: {@code Document > L > LI > (Lbl, LBody)}, one item
 * per line, the label at x, the body 18 pt to the right. The expected values come from the
 * sample design and from the format specifications that each test names. The samples and the
 * outputs stay in {@code target/test-samples/review-805-*}.
 */
class Review805_ListMarkersTest {

    // ---------------------------------------------------------------------------------------
    // 1. A Lbl with a number that is not an ASCII digit (circled digits, Roman numeral signs,
    //    circled Hangul, parenthesized digits) in a list without /ListNumbering.
    //
    //    ISO 32000-1, Table 347: when /ListNumbering is absent (default None), "Lbl elements
    //    (if present) contain arbitrary text not subject to any numbering scheme". The label is
    //    content. Only a bullet glyph can be replaced by the Markdown "-" marker. These labels
    //    are numbers (Unicode general category No or Nl, or an enclosed Hangul letter), and
    //    veraPDF has its own style for them (NumberingStyleNames.CIRCLED_ARABIC_NUMBERS).
    //    The reference marks *, **, and a dagger are different on each item, so they are not
    //    bullets either: they tie each item to text in another place.
    // ---------------------------------------------------------------------------------------

    static Stream<Arguments> numberLabels() {
        return Stream.of(
                Arguments.of("circled-digits", new String[]{"\u2460", "\u2461", "\u2462"}),
                Arguments.of("roman-numeral-signs", new String[]{"\u2160.", "\u2161.", "\u2162."}),
                Arguments.of("circled-hangul", new String[]{"\u3260", "\u3261", "\u3262"}),
                Arguments.of("parenthesized-digits", new String[]{"\u2474", "\u2475", "\u2476"}),
                Arguments.of("reference-marks", new String[]{"*", "**", "\u2020"}),
                Arguments.of("plus-minus-signs", new String[]{"+", "+", "-"}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("numberLabels")
    void numberLabelsWithoutListNumberingStayInTheMarkdown(String name, String[] labels) throws IOException {
        String[] bodies = {"First item", "Second item", "Third item"};
        Path pdf = numberLabelSample("review-805-" + name, labels, bodies);

        List<String> lines = markdownLines(pdf, true);

        for (int i = 0; i < bodies.length; i++) {
            String body = bodies[i];
            assertThat(lines).as("Markdown line of '%s'", body)
                    .anySatisfy(line -> assertThat(line).contains(body));
            String line = lines.stream().filter(l -> l.contains(body)).findFirst().get();
            assertThat(line).as("the number of item %d stays in its line", i + 1).contains(labels[i]);
        }
    }

    @Test
    void circledNumberListIsNotReportedAsUnordered() throws IOException {
        Path pdf = numberLabelSample("review-805-circled-digits-json",
                new String[]{"\u2460", "\u2461", "\u2462"}, new String[]{"First item", "Second item", "Third item"});

        JsonNode list = jsonLists(pdf, true).get(0);

        // ISO 32000-2, Table 383: "Unordered" is a list with unspecified bullets. A list numbered
        // 1, 2, 3 with circled digits is an ordered list.
        assertThat(list.path("numbering style").asText()).isNotEqualTo("unordered");
    }

    // ---------------------------------------------------------------------------------------
    // 2. Ordered lists that follow each other or that are nested.
    //
    //    CommonMark 0.31.2, 5.3: "The start number of an ordered list is determined by the list
    //    number of its initial list item. The numbers of subsequent list items are disregarded."
    //    Blank lines between items do not end a list. So the Markdown must not put items with
    //    labels 1., 1., 2., 2. into one ordered list: a renderer shows them as 1, 2, 3, 4.
    //    The expected number of each item is its Lbl in the PDF.
    // ---------------------------------------------------------------------------------------

    @Test
    void nestedOrderedListKeepsTheNumberOfEachItemWhenRendered() throws IOException {
        Path directory = TestSamples.emptyDirectory("review-805-nested-ordered");
        Path pdf = directory.resolve("review-805-nested-ordered.pdf");
        try (TaggedListPdf sample = new TaggedListPdf()) {
            PDStructureElement outer = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            PDStructureElement parentBody = sample.item(outer, 72, "1.", "Parent item");
            PDStructureElement inner = sample.list(parentBody, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(inner, 90, "1.", "Child one");
            sample.item(inner, 90, "2.", "Child two");
            sample.item(outer, 72, "2.", "Next item");
            sample.save(pdf);
        }

        Map<String, Integer> rendered = renderedNumbers(markdownLines(pdf, true));

        assertThat(rendered).containsEntry("Parent item", 1).containsEntry("Child one", 1)
                .containsEntry("Child two", 2).containsEntry("Next item", 2);
    }

    @Test
    void adjacentOrderedListsKeepTheNumberOfEachItemWhenRendered() throws IOException {
        Path directory = TestSamples.emptyDirectory("review-805-adjacent-ordered");
        Path pdf = directory.resolve("review-805-adjacent-ordered.pdf");
        try (TaggedListPdf sample = new TaggedListPdf()) {
            PDStructureElement first = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(first, 72, "1.", "Alpha item");
            sample.item(first, 72, "2.", "Beta item");
            sample.y -= 20;
            PDStructureElement second = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(second, 72, "1.", "Gamma item");
            sample.item(second, 72, "2.", "Delta item");
            sample.save(pdf);
        }

        Map<String, Integer> rendered = renderedNumbers(markdownLines(pdf, true));

        assertThat(rendered).containsEntry("Alpha item", 1).containsEntry("Beta item", 2)
                .containsEntry("Gamma item", 1).containsEntry("Delta item", 2);
    }

    @Test
    void adjacentOrderedListsKeepTheNumberOfEachItemWhenRenderedWithoutStructTree() throws IOException {
        Path directory = TestSamples.emptyDirectory("review-805-adjacent-ordered-default");
        Path pdf = directory.resolve("review-805-adjacent-ordered-default.pdf");
        try (TaggedListPdf sample = new TaggedListPdf()) {
            PDStructureElement first = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(first, 72, "1.", "Alpha item");
            sample.item(first, 72, "2.", "Beta item");
            sample.item(first, 72, "3.", "Gamma item");
            sample.y -= 30;
            PDStructureElement second = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(second, 72, "1.", "Delta item");
            sample.item(second, 72, "2.", "Epsilon item");
            sample.item(second, 72, "3.", "Zeta item");
            sample.save(pdf);
        }

        Map<String, Integer> rendered = renderedNumbers(markdownLines(pdf, false));

        assertThat(rendered).containsEntry("Alpha item", 1).containsEntry("Gamma item", 3)
                .containsEntry("Delta item", 1).containsEntry("Zeta item", 3);
    }

    // ---------------------------------------------------------------------------------------
    // 3. A Lbl that is on its own text line (its baseline 9 pt above the body baseline, 12 pt
    //    text), in a list without /ListNumbering. The body text must stay complete and inside
    //    its list item.
    // ---------------------------------------------------------------------------------------

    @Test
    void dashLabelOnItsOwnLineKeepsTheWholeBody() throws IOException {
        Path pdf = ownLineLabelSample("review-805-dash-own-line", "-");

        List<String> lines = markdownLines(pdf, true);

        assertThat(lines).anySatisfy(line -> assertThat(line).contains("Body one"));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("Body two"));
    }

    @Test
    void bulletLabelOnItsOwnLineKeepsTheBodyInTheItemWithKeepLineBreaks() throws IOException {
        Path pdf = ownLineLabelSample("review-805-bullet-own-line-keep", "\u2022");

        Config config = new Config();
        config.setOutputFolder(pdf.getParent().toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setUseStructTree(true);
        config.setKeepLineBreaks(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        List<String> lines = Files.readAllLines(pdf.resolveSibling("review-805-bullet-own-line-keep.md"),
                StandardCharsets.UTF_8);

        // CommonMark 0.31.2, 5.2: after a marker with nothing behind it, a line at column 0 is
        // not part of the item. 4.3: a "-" line under a paragraph line is a setext heading.
        assertThat(lines).noneMatch(line -> line.matches(" {0,3}[-+*][ \\t]*"));
    }

    // ---------------------------------------------------------------------------------------
    // 1b. The tagged PDF output of the circled-number list.
    //     ISO 32000-1, Table 347: Disc, Circle, Square are bullets. A list numbered with
    //     circled digits is not a bullet list.
    // ---------------------------------------------------------------------------------------

    // ---------------------------------------------------------------------------------------
    // 4. --sanitize. The label and the body touch, so the sanitizer replaces text that starts in
    //    the label. The Markdown file must still contain each item.
    // ---------------------------------------------------------------------------------------

    @ParameterizedTest(name = "ListNumbering {0}")
    @NullSource
    @ValueSource(strings = {PDListAttributeObject.LIST_NUMBERING_DISC})
    void sanitizedListKeepsEachItemInTheMarkdown(String listNumbering) throws IOException {
        String name = "review-805-sanitize-joined-label" + (listNumbering == null ? "" : "-" + listNumbering.toLowerCase(Locale.ROOT));
        Path directory = TestSamples.emptyDirectory(name);
        Path pdf = directory.resolve(name + ".pdf");
        try (TaggedListPdf sample = new TaggedListPdf()) {
            PDStructureElement list = sample.list(sample.documentElement, listNumbering);
            sample.joinedItem(list, 72, "------------------------------", "@x.com");
            sample.joinedItem(list, 72, "------------------------------", "@x.com");
            sample.save(pdf);
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setUseStructTree(true);
        config.getFilterConfig().setFilterSensitiveData(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        String markdown = new String(Files.readAllBytes(directory.resolve(name + ".md")),
                StandardCharsets.UTF_8);
        // The built-in e-mail rule replaces the address; each of the two items keeps its text.
        assertThat(markdown.split("email@example.com", -1)).as("items with text in: %s", markdown).hasSize(3);
    }

    @Test
    void circledNumberListIsNotTaggedAsABulletList() throws IOException {
        Path pdf = numberLabelSample("review-805-circled-digits-tagged",
                new String[]{"\u2460", "\u2461", "\u2462"}, new String[]{"First item", "Second item", "Third item"});
        Config config = new Config();
        config.setOutputFolder(pdf.getParent().toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(false);
        config.setGenerateTaggedPDF(true);
        config.setUseStructTree(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        List<String> numberings = new ArrayList<>();
        try (PDDocument tagged = Loader.loadPDF(pdf.resolveSibling("review-805-circled-digits-tagged_tagged.pdf").toFile())) {
            collectListNumberings(tagged.getDocumentCatalog().getStructureTreeRoot().getKids(), numberings);
        }

        assertThat(numberings).as("ListNumbering of each L").isNotEmpty()
                .noneMatch(n -> n.equals("Disc") || n.equals("Circle") || n.equals("Square") || n.equals("Unordered"));
    }

    // ---------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------

    private static Path ownLineLabelSample(String name, String label) throws IOException {
        Path directory = TestSamples.emptyDirectory(name);
        Path pdf = directory.resolve(name + ".pdf");
        try (TaggedListPdf sample = new TaggedListPdf()) {
            sample.labelRise = 9;
            PDStructureElement list = sample.list(sample.documentElement, null);
            sample.item(list, 72, label, "Body one");
            sample.item(list, 72, label, "Body two");
            sample.save(pdf);
        }
        return pdf;
    }

    private static void collectListNumberings(List<Object> kids, List<String> numberings) {
        for (Object kid : kids) {
            if (!(kid instanceof PDStructureElement)) {
                continue;
            }
            PDStructureElement element = (PDStructureElement) kid;
            if (StandardStructureTypes.L.equals(element.getStructureType())) {
                String numbering = "None";
                Revisions<PDAttributeObject> attributes = element.getAttributes();
                for (int i = 0; i < attributes.size(); i++) {
                    if (attributes.getObject(i) instanceof PDListAttributeObject) {
                        numbering = ((PDListAttributeObject) attributes.getObject(i)).getListNumbering();
                    }
                }
                numberings.add(numbering);
            }
            collectListNumberings(element.getKids(), numberings);
        }
    }

    private static Path numberLabelSample(String name, String[] labels, String[] bodies) throws IOException {
        Path directory = TestSamples.emptyDirectory(name);
        Path pdf = directory.resolve(name + ".pdf");
        try (TaggedListPdf sample = new TaggedListPdf()) {
            Map<Character, String> mapping = new LinkedHashMap<>();
            char placeholder = 'a';
            for (String label : labels) {
                mapping.put(placeholder++, label);
            }
            sample.labelFont = sample.mappedFont(mapping);
            PDStructureElement list = sample.list(sample.documentElement, null);
            placeholder = 'a';
            for (String body : bodies) {
                sample.item(list, 72, String.valueOf(placeholder++), body);
            }
            sample.save(pdf);
        }
        return pdf;
    }

    private static List<String> markdownLines(Path pdf, boolean useStructTree) throws IOException {
        Config config = new Config();
        config.setOutputFolder(pdf.getParent().toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setUseStructTree(useStructTree);
        DocumentProcessor.processFile(pdf.toString(), config);
        Path markdown = pdf.resolveSibling(pdf.getFileName().toString().replace(".pdf", ".md"));
        assertThat(markdown).exists();
        return Files.readAllLines(markdown, StandardCharsets.UTF_8);
    }

    private static List<JsonNode> jsonLists(Path pdf, boolean useStructTree) throws IOException {
        Config config = new Config();
        config.setOutputFolder(pdf.getParent().toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(false);
        config.setUseStructTree(useStructTree);
        DocumentProcessor.processFile(pdf.toString(), config);
        Path json = pdf.resolveSibling(pdf.getFileName().toString().replace(".pdf", ".json"));
        assertThat(json).exists();
        List<JsonNode> lists = new ArrayList<>();
        collectLists(new ObjectMapper().readTree(json.toFile()), lists);
        assertThat(lists).as("lists in the JSON output").isNotEmpty();
        return lists;
    }

    private static void collectLists(JsonNode node, List<JsonNode> lists) {
        if (node.isObject() && "list".equals(node.path("type").asText())) {
            lists.add(node);
        }
        for (JsonNode child : node) {
            collectLists(child, lists);
        }
    }

    private static final Pattern LIST_ITEM =
            Pattern.compile("^( *)(?:(\\d{1,9})([.)])|([-+*]))(?:( +)(.*))?$");

    /**
     * A small model of CommonMark 0.31.2, sections 5.2 and 5.3, for the numbers that a renderer
     * shows: it returns the shown number of each ordered item, by the item text.
     *
     * <ul>
     *   <li>An item continues the current list if it has the same type and delimiter, and if it
     *       is not indented to the content column of the previous item. Blank lines between items
     *       do not end a list.</li>
     *   <li>An item indented to the content column of the previous item starts a nested list.</li>
     *   <li>After a blank line, a line that is not a list item and that is not indented to the
     *       content column ends the list (for example a paragraph or an HTML comment).</li>
     *   <li>The first item of a list sets its start number. The numbers of later items are
     *       ignored.</li>
     * </ul>
     * Tabs, block quotes, and code blocks are not modeled. The test
     * {@code commonMarkModelAgreesWithTheSpecification} checks the model.
     */
    static Map<String, Integer> renderedNumbers(List<String> lines) {
        Map<String, Integer> result = new LinkedHashMap<>();
        // Each entry: {marker indent, content column of the last item, next number, kind}.
        // Kind: 0 = ordered with '.', 1 = ordered with ')', 2 = bullet.
        Deque<int[]> lists = new ArrayDeque<>();
        boolean afterBlank = false;
        for (String line : lines) {
            if (line.trim().isEmpty()) {
                afterBlank = true;
                continue;
            }
            int indent = line.length() - line.replaceFirst("^ +", "").length();
            String rest = line;
            Matcher item = LIST_ITEM.matcher(rest);
            boolean listLine = item.matches();
            while (item.matches()) {
                // An item whose content is again a list item ("- 1. text") starts a nested list
                // at its content column, on the same line.
                while (!lists.isEmpty() && indent < lists.peek()[0]) {
                    lists.pop();
                }
                boolean ordered = item.group(2) != null;
                int kind = ordered ? (".".equals(item.group(3)) ? 0 : 1) : 2;
                int markerWidth = ordered ? item.group(2).length() + 1 : 1;
                int spaces = item.group(5) == null || item.group(5).length() > 4 ? 1 : item.group(5).length();
                int content = indent + markerWidth + spaces;
                int start = ordered ? Integer.parseInt(item.group(2)) : 0;
                int[] top = lists.peek();
                if (top != null && indent >= top[1]) {
                    lists.push(new int[]{indent, content, start, kind});
                } else if (top != null && top[3] == kind) {
                    top[1] = content;
                } else {
                    if (top != null) {
                        lists.pop();
                    }
                    lists.push(new int[]{indent, content, start, kind});
                }
                int[] current = lists.peek();
                String inner = item.group(6) == null ? "" : item.group(6);
                Matcher next = LIST_ITEM.matcher(inner);
                if (ordered && !next.matches()) {
                    result.put(text(inner), current[2]);
                }
                current[2]++;
                indent = content;
                rest = inner;
                item = next;
            }
            if (listLine) {
                afterBlank = false;
                continue;
            } else if (afterBlank) {
                while (!lists.isEmpty() && indent < lists.peek()[1]) {
                    lists.pop();
                }
            }
            afterBlank = false;
        }
        return result;
    }

    @Test
    void commonMarkModelAgreesWithTheSpecification() {
        // Each expected map was also checked with markdown-it-py 4.0.0 in CommonMark mode.
        assertThat(renderedNumbers(List.of("- 1. A", "- 2. B", "- 1. C")))
                .containsExactly(Map.entry("A", 1), Map.entry("B", 2), Map.entry("C", 1));
        assertThat(renderedNumbers(List.of("1. A", "2. B", "", "1. C", "2. D")))
                .containsExactly(Map.entry("A", 1), Map.entry("B", 2), Map.entry("C", 3), Map.entry("D", 4));
        assertThat(renderedNumbers(List.of("1. A", "2. B", "", "<!-- -->", "", "1. C", "2. D")))
                .containsExactly(Map.entry("A", 1), Map.entry("B", 2), Map.entry("C", 1), Map.entry("D", 2));
        assertThat(renderedNumbers(List.of("1. A", "2. B", "", "Text", "", "1. C")))
                .containsExactly(Map.entry("A", 1), Map.entry("B", 2), Map.entry("C", 1));
        assertThat(renderedNumbers(List.of("1. A", "", "1) C")))
                .containsExactly(Map.entry("A", 1), Map.entry("C", 1));
        assertThat(renderedNumbers(List.of("1. Parent", "", "   1. Child one", "   2. Child two", "", "2. Next")))
                .containsExactly(Map.entry("Parent", 1), Map.entry("Child one", 1), Map.entry("Child two", 2),
                        Map.entry("Next", 2));
        assertThat(renderedNumbers(List.of("1. Parent", "", "    1. Child one", "    2. Child two", "", "2. Next")))
                .containsExactly(Map.entry("Parent", 1), Map.entry("Child one", 1), Map.entry("Child two", 2),
                        Map.entry("Next", 2));
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    /** A one-page tagged PDF with lists. Each item is one line: the label at x, the body at x + 18. */
    static final class TaggedListPdf implements AutoCloseable {
        final PDDocument document = new PDDocument();
        final PDPage page = new PDPage(PDRectangle.LETTER);
        final PDStructureTreeRoot root = new PDStructureTreeRoot();
        final PDStructureElement documentElement;
        final COSArray parents = new COSArray();
        final PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        final PDPageContentStream content;
        PDFont labelFont = helvetica;
        float labelSize = 12;
        float labelRise = 0;
        float y = 700;

        TaggedListPdf() throws IOException {
            document.addPage(page);
            page.setStructParents(0);
            document.getDocumentCatalog().setStructureTreeRoot(root);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            document.getDocumentCatalog().setMarkInfo(markInfo);
            documentElement = new PDStructureElement(StandardStructureTypes.DOCUMENT, root);
            root.appendKid(documentElement);
            content = new PDPageContentStream(document, page);
        }

        PDStructureElement list(PDStructureNode parent, String listNumbering) {
            PDStructureElement list = new PDStructureElement(StandardStructureTypes.L, parent);
            parent.appendKid(list);
            if (listNumbering != null) {
                PDListAttributeObject attribute = new PDListAttributeObject();
                attribute.setListNumbering(listNumbering);
                list.addAttribute(attribute);
            }
            return list;
        }

        /** Writes one item and returns its LBody, so that a nested list can go into it. */
        PDStructureElement item(PDStructureElement list, float x, String label, String body) throws IOException {
            PDStructureElement item = new PDStructureElement(StandardStructureTypes.LI, list);
            list.appendKid(item);
            PDStructureElement lbl = new PDStructureElement(StandardStructureTypes.LBL, item);
            item.appendKid(lbl);
            PDStructureElement lBody = new PDStructureElement(StandardStructureTypes.L_BODY, item);
            item.appendKid(lBody);
            writeMarkedText(labelFont, labelSize, x, y + labelRise, label, lbl);
            writeMarkedText(helvetica, 12, x + 18, y, body, lBody);
            y -= 20;
            return lBody;
        }

        /** Writes one item with the body directly after the label, in one text object (no gap). */
        void joinedItem(PDStructureElement list, float x, String label, String body) throws IOException {
            PDStructureElement item = new PDStructureElement(StandardStructureTypes.LI, list);
            list.appendKid(item);
            PDStructureElement lbl = new PDStructureElement(StandardStructureTypes.LBL, item);
            item.appendKid(lbl);
            PDStructureElement lBody = new PDStructureElement(StandardStructureTypes.L_BODY, item);
            item.appendKid(lBody);
            content.beginText();
            content.setFont(helvetica, 12);
            content.newLineAtOffset(x, y);
            for (Object[] part : new Object[][]{{label, lbl}, {body, lBody}}) {
                PDStructureElement element = (PDStructureElement) part[1];
                int mcid = parents.size();
                COSDictionary properties = new COSDictionary();
                properties.setInt(COSName.MCID, mcid);
                content.beginMarkedContent(COSName.getPDFName(element.getStructureType()),
                        PDPropertyList.create(properties));
                content.showText((String) part[0]);
                content.endMarkedContent();
                element.setPage(page);
                element.getCOSObject().setInt(COSName.K, mcid);
                parents.add(element);
            }
            content.endText();
            y -= 20;
        }

        /**
         * Helvetica with a ToUnicode CMap that maps each placeholder byte to a label text, so
         * that the extracted text of the label is, for example, a circled digit.
         */
        PDFont mappedFont(Map<Character, String> mapping) throws IOException {
            StringBuilder cmap = new StringBuilder();
            cmap.append("/CIDInit /ProcSet findresource begin\n12 dict begin\nbegincmap\n")
                    .append("/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n")
                    .append("/CMapName /Review805-UCS def\n/CMapType 2 def\n")
                    .append("1 begincodespacerange\n<00> <FF>\nendcodespacerange\n")
                    .append(mapping.size()).append(" beginbfchar\n");
            for (Map.Entry<Character, String> entry : mapping.entrySet()) {
                cmap.append(String.format("<%02X> <", (int) entry.getKey()));
                for (byte b : entry.getValue().getBytes(StandardCharsets.UTF_16BE)) {
                    cmap.append(String.format("%02X", b & 0xFF));
                }
                cmap.append(">\n");
            }
            cmap.append("endbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
            PDStream toUnicode = new PDStream(document);
            try (OutputStream out = toUnicode.createOutputStream()) {
                out.write(cmap.toString().getBytes(StandardCharsets.US_ASCII));
            }
            COSDictionary font = new COSDictionary();
            font.setItem(COSName.TYPE, COSName.FONT);
            font.setItem(COSName.SUBTYPE, COSName.TYPE1);
            font.setName(COSName.BASE_FONT, "Helvetica");
            font.setItem(COSName.ENCODING, COSName.WIN_ANSI_ENCODING);
            font.setItem(COSName.TO_UNICODE, toUnicode);
            return new PDType1Font(font);
        }

        void save(Path pdf) throws IOException {
            content.close();
            COSArray nums = new COSArray();
            nums.add(COSInteger.get(0));
            nums.add(parents);
            COSDictionary parentTree = new COSDictionary();
            parentTree.setItem(COSName.NUMS, nums);
            root.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
            root.getCOSObject().setInt(COSName.PARENT_TREE_NEXT_KEY, 1);
            document.save(pdf.toFile());
        }

        private void writeMarkedText(PDFont font, float size, float x, float baseline, String text,
                                     PDStructureElement element) throws IOException {
            int mcid = parents.size();
            COSDictionary properties = new COSDictionary();
            properties.setInt(COSName.MCID, mcid);
            content.beginMarkedContent(COSName.getPDFName(element.getStructureType()),
                    PDPropertyList.create(properties));
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, baseline);
            content.showText(text);
            content.endText();
            content.endMarkedContent();
            element.setPage(page);
            element.getCOSObject().setInt(COSName.K, mcid);
            parents.add(element);
        }

        @Override
        public void close() throws IOException {
            document.close();
        }
    }
}

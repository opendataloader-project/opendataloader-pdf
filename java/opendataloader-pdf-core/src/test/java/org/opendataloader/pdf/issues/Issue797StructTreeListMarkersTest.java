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

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDListAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
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
 * Issue #797: with {@code --use-struct-tree}, the Markdown output wrote a {@code -} marker before
 * the text of the {@code Lbl} of each list item.
 *
 * <ul>
 *   <li>An ordered list ({@code /ListNumbering /Decimal}, {@code Lbl "1."}) gave {@code - 1. First step}
 *       instead of {@code 1. First step}.</li>
 *   <li>A list without {@code /ListNumbering} and with {@code Lbl "•"} gave {@code - • First item}
 *       instead of {@code - First item}.</li>
 * </ul>
 *
 * <p>The test builds a synthetic tagged PDF: {@code L > LI > (Lbl, LBody)} for each item, with
 * the label and the body on the same line. The expected lines come from this design. The sample
 * and the output stay in {@code target/test-samples/issue-797/}.
 */
class Issue797StructTreeListMarkersTest {

    private static final String[] ITEMS = {"First item", "Second item", "Third item"};

    @Test
    void orderedListLabelIsTheMarker() throws IOException {
        List<String> lines = convert("issue-797-ordered", PDListAttributeObject.LIST_NUMBERING_DECIMAL,
                new String[]{"1.", "2.", "3."});

        assertThat(lines).containsSubsequence("1. First item", "2. Second item", "3. Third item");
        assertThat(lines).noneMatch(line -> line.startsWith("- "));
    }

    @Test
    void bulletLabelWithoutListNumberingIsStripped() throws IOException {
        List<String> lines = convert("issue-797-bullet-no-attribute", null,
                new String[]{"•", "•", "•"});

        assertThat(lines).containsSubsequence("- First item", "- Second item", "- Third item");
        assertThat(lines).noneMatch(line -> line.contains("•"));
    }

    @Test
    void numberedLabelWithoutListNumberingIsTheMarker() throws IOException {
        List<String> lines = convert("issue-797-numbers-no-attribute", null,
                new String[]{"1.", "2.", "3."});

        assertThat(lines).containsSubsequence("1. First item", "2. Second item", "3. Third item");
        assertThat(lines).noneMatch(line -> line.startsWith("- "));
    }

    private static List<String> convert(String name, String listNumbering, String[] labels) throws IOException {
        Path directory = TestSamples.emptyDirectory(name);
        Path pdf = directory.resolve(name + ".pdf");
        writeSample(pdf, listNumbering, labels);

        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setUseStructTree(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        Path markdown = directory.resolve(name + ".md");
        assertThat(markdown).exists();
        return Files.readAllLines(markdown, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .collect(Collectors.toList());
    }

    /** Writes a tagged PDF with one list: {@code Document > L > LI > (Lbl, LBody)}. */
    private static void writeSample(Path pdf, String listNumbering, String[] labels) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            page.setStructParents(0);

            PDStructureTreeRoot root = new PDStructureTreeRoot();
            document.getDocumentCatalog().setStructureTreeRoot(root);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            document.getDocumentCatalog().setMarkInfo(markInfo);

            PDStructureElement documentElement = new PDStructureElement(StandardStructureTypes.DOCUMENT, root);
            root.appendKid(documentElement);
            PDStructureElement list = new PDStructureElement(StandardStructureTypes.L, documentElement);
            documentElement.appendKid(list);
            if (listNumbering != null) {
                PDListAttributeObject attribute = new PDListAttributeObject();
                attribute.setListNumbering(listNumbering);
                list.addAttribute(attribute);
            }

            COSArray parents = new COSArray();
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float y = 700;
                for (int i = 0; i < ITEMS.length; i++) {
                    PDStructureElement item = new PDStructureElement(StandardStructureTypes.LI, list);
                    list.appendKid(item);
                    PDStructureElement label = new PDStructureElement(StandardStructureTypes.LBL, item);
                    item.appendKid(label);
                    PDStructureElement body = new PDStructureElement(StandardStructureTypes.L_BODY, item);
                    item.appendKid(body);

                    writeMarkedText(content, font, 72, y, labels[i], label, page, parents);
                    writeMarkedText(content, font, 90, y, ITEMS[i], body, page, parents);
                    y -= 20;
                }
            }

            COSArray nums = new COSArray();
            nums.add(COSInteger.get(0));
            nums.add(parents);
            COSDictionary parentTree = new COSDictionary();
            parentTree.setItem(COSName.NUMS, nums);
            root.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
            root.getCOSObject().setInt(COSName.PARENT_TREE_NEXT_KEY, 1);

            document.save(pdf.toFile());
        }
    }

    private static void writeMarkedText(PDPageContentStream content, PDType1Font font, float x, float y,
                                        String text, PDStructureElement element, PDPage page,
                                        COSArray parents) throws IOException {
        int mcid = parents.size();
        COSDictionary properties = new COSDictionary();
        properties.setInt(COSName.MCID, mcid);
        content.beginMarkedContent(COSName.getPDFName(element.getStructureType()),
                PDPropertyList.create(properties));
        content.beginText();
        content.setFont(font, 12);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        content.endMarkedContent();

        element.setPage(page);
        element.getCOSObject().setInt(COSName.K, mcid);
        parents.add(element);
    }
}

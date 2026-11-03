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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #877: with {@code --use-struct-tree}, a PDF tagged by macOS Preview has no text after page 2.
 *
 * <p>The PDF of the issue marks the text of each page with an MCID, but its structure tree has one paragraph,
 * on page 1. The tree has no {@code /ParentTree}, and the pages have no {@code /StructParents}, so the tree
 * cannot map the marked content of the other pages to a structure element (ISO 32000-1, 14.7.4.4: the parent
 * tree is required when structure elements contain content items). The text of these pages is neither in the
 * tree nor an artifact, and the output leaves it out. The expected output has the text of each page: the
 * structure tree is not used, as for a PDF without a tree.
 *
 * <p>The samples copy the structure of the issue: each page has its body text in a {@code /P} marked-content
 * sequence with an MCID, and the tree has one {@code P} for page 1. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-877}.
 */
class Issue877StructTreeWithoutParentTreeTest {

    private static final String BODY = "The members of the board meet in the town hall";

    /** The structure of the issue: the text of pages 2 and 3 is in the output. */
    @Test
    void textOfEachPageIsInTheOutputWithoutAParentTree() throws IOException {
        String markdown = convert("no-parent-tree", false);

        assertTrue(markdown.contains(BODY + " 1") && markdown.contains(BODY + " 2") && markdown.contains(BODY + " 3"),
            "the text of a page is not in the output: " + markdown);
    }

    /** Guard: a tree with a parent tree is used, so a line tagged as a heading is a heading. */
    @Test
    void treeWithAParentTreeIsUsed() throws IOException {
        String markdown = convert("parent-tree", true);

        assertTrue(markdown.contains("# " + BODY + " 1") && markdown.contains(BODY + " 3"),
            "the structure tree with a parent tree is not used: " + markdown);
    }

    private static String convert(String name, boolean withParentTree) throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-877-" + name);
        Path pdf = writeTaggedPdf(dir.resolve(name + ".pdf"), 3, withParentTree);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setUseStructTree(true);
        config.setGenerateMarkdown(true);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Files.readString(dir.resolve(name + ".md"));
    }

    /**
     * Each page has its body text, with the page number at the end, in a {@code /P} marked-content sequence with
     * MCID 0. With a parent tree, each page has a structure element: an {@code H1} for page 1 and a {@code P} for
     * the other pages. Without it, as in the issue, the tree has one {@code P}, for page 1, and the pages have no
     * {@code /StructParents}.
     */
    private static Path writeTaggedPdf(Path pdf, int pages, boolean withParentTree) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDStructureTreeRoot root = new PDStructureTreeRoot();
            COSArray nums = new COSArray();
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(new PDRectangle(400, 300));
                doc.addPage(page);
                PDResources resources = new PDResources();
                String font = resources.add(new PDType1Font(Standard14Fonts.FontName.HELVETICA)).getName();
                page.setResources(resources);
                String content = "/P <</MCID 0>> BDC\nBT /" + font + " 12 Tf 40 200 Td (" + BODY + " " + (i + 1)
                    + ") Tj ET\nEMC\n";
                PDStream stream = new PDStream(doc);
                try (OutputStream os = stream.createOutputStream()) {
                    os.write(content.getBytes(StandardCharsets.US_ASCII));
                }
                page.setContents(stream);
                if (!withParentTree && i > 0) {
                    continue;
                }
                PDStructureElement element = new PDStructureElement(withParentTree && i == 0 ? "H1" : "P", root);
                element.setPage(page);
                element.getCOSObject().setInt(COSName.K, 0);
                root.appendKid(element);
                if (withParentTree) {
                    page.setStructParents(i);
                    COSArray owners = new COSArray();
                    owners.add(element);
                    nums.add(COSInteger.get(i));
                    nums.add(owners);
                }
            }
            if (withParentTree) {
                COSDictionary parentTree = new COSDictionary();
                parentTree.setItem(COSName.NUMS, nums);
                root.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
                root.getCOSObject().setInt(COSName.PARENT_TREE_NEXT_KEY, pages);
            }
            doc.getDocumentCatalog().setStructureTreeRoot(root);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            doc.getDocumentCatalog().setMarkInfo(markInfo);
            doc.getDocumentCatalog().setLanguage("en");
            doc.save(pdf.toFile());
        }
        return pdf;
    }
}

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
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #801: in structure-tree mode, text in an {@code /Artifact} marked-content sequence is not content.
 *
 * <p>ISO 32000-1 14.8.2.2: an artifact is a graphics object that is not part of the authored content.
 * The structure tree does not reference it. Structure-tree mode still added the artifact text to the
 * output as a paragraph, both for a bare {@code /Artifact BMC} and for a pagination header artifact.
 *
 * <p>The sample has the measurements of the reporter's fixture: a 400 x 300 page, Helvetica 12, the
 * artifact text at (40, 260), and one {@code /P} with MCID 0 at (40, 200). Text outside the tree that
 * is not marked as an artifact is untagged content, not an artifact, so it stays in the output.
 */
class Issue801StructTreeArtifactTextTest {

    private static final String ARTIFACT = "ARTIFACT_SENTINEL";
    private static final String BODY = "BODY_SENTINEL";
    private static final String UNTAGGED = "UNTAGGED_SENTINEL";
    private static final String PAGINATION_HEADER = "/Artifact <</Type /Pagination /Subtype /Header>> BDC";

    @Test
    void bareArtifactTextIsNotOutput() throws IOException {
        Output out = convert("bare-artifact", 1, marked("/Artifact BMC"), false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertFalse(out.markdown.contains(ARTIFACT), "Markdown shows the artifact text as content: " + out.markdown);
        assertFalse(out.json.contains(ARTIFACT), "JSON shows the artifact text as content");
    }

    @Test
    void paginationHeaderArtifactTextIsNotOutput() throws IOException {
        Output out = convert("pagination-artifact", 1, marked(PAGINATION_HEADER), false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertFalse(out.markdown.contains(ARTIFACT), "Markdown shows the header artifact text as content: " + out.markdown);
        assertFalse(out.json.contains(ARTIFACT), "JSON shows the header artifact text as content");
    }

    /** Two words in two text operators on one line: the line gets an inserted space between them. */
    @Test
    void artifactLineOfTwoWordsIsNotOutput() throws IOException {
        Output out = convert("two-word-artifact", 1, (doc, resources, font) ->
            "/Artifact BMC\n" + text(font, 40, 260, ARTIFACT) + text(font, 200, 260, "Continued") + "EMC\n", false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertFalse(out.markdown.contains("Continued") || out.markdown.contains(ARTIFACT),
            "Markdown shows the artifact line as content: " + out.markdown);
    }

    /** A word gap as a kern in a TJ array, as pdfTeX writes it: veraPDF adds a space to the chunk. */
    @Test
    void artifactLineWithAKernedWordGapIsNotOutput() throws IOException {
        Output out = convert("kerned-artifact", 1, (doc, resources, font) ->
            "/Artifact BMC\nBT /" + font + " 12 Tf 40 260 Td [(" + ARTIFACT + ") -333 (Continued)] TJ ET\nEMC\n", false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertFalse(out.markdown.contains("Continued") || out.markdown.contains(ARTIFACT),
            "Markdown shows the artifact line as content: " + out.markdown);
    }

    @Test
    void artifactTextInAFormIsNotOutput() throws IOException {
        Output out = convert("form-artifact", 1, (doc, resources, font) ->
            "/Artifact BMC\n/" + resources.add(form(doc, resources, text(font, 40, 260, ARTIFACT))).getName()
                + " Do\nEMC\n", false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertFalse(out.markdown.contains(ARTIFACT), "Markdown shows the text of a form drawn in an artifact: " + out.markdown);
    }

    /** The page draws one form under two names. A stream info names the form by the name of its {@code Do}. */
    @Test
    void artifactTextInAFormWithTwoNamesIsNotOutput() throws IOException {
        Output out = convert("two-name-form-artifact", 1, (doc, resources, font) -> {
            PDFormXObject form = form(doc, resources, "/Artifact BMC\n" + text(font, 40, 260, ARTIFACT) + "EMC\n");
            resources.put(COSName.getPDFName("A"), form);
            resources.put(COSName.getPDFName("B"), form);
            return "/A Do\n/B Do\n";
        }, false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertFalse(out.markdown.contains(ARTIFACT), "Markdown shows the text of a form drawn under a second name: " + out.markdown);
    }

    @Test
    void untaggedTextOutsideTheTreeStays() throws IOException {
        Output out = convert("untagged-text", 1, (doc, resources, font) -> text(font, 40, 260, ARTIFACT), false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertTrue(out.markdown.contains(ARTIFACT), "untagged text outside the tree is lost: " + out.markdown);
    }

    /**
     * The page draws the form X0, and the form X1 draws a different form that it also names X0. A stream
     * info names a form only by that name. The untagged text in the second X0 has the operator index of
     * the artifact text in the first X0.
     */
    @Test
    void untaggedTextInAFormWithTheNameOfAnArtifactFormStays() throws IOException {
        Output out = convert("shared-form-name", 1, (doc, resources, font) -> {
            COSName x0 = COSName.getPDFName("X0");
            resources.put(x0, form(doc, resources, "/Artifact BMC\n" + text(font, 40, 260, ARTIFACT) + "EMC\n"));
            PDResources inner = new PDResources();
            String innerFont = inner.add(new PDType1Font(Standard14Fonts.FontName.HELVETICA)).getName();
            inner.put(x0, form(doc, inner, "/Span BMC\n" + text(innerFont, 40, 230, UNTAGGED) + "EMC\n"));
            resources.put(COSName.getPDFName("X1"), form(doc, inner, "/X0 Do\n"));
            return "/X0 Do\n/X1 Do\n";
        }, false);
        assertTrue(out.markdown.contains(BODY), "the paragraph of the tree is missing: " + out.markdown);
        assertTrue(out.markdown.contains(UNTAGGED), "untagged text in a form is lost: " + out.markdown);
    }

    /** The JSON is checked: in structure-tree mode, the Markdown of a header has no text (a different problem). */
    @Test
    void repeatedHeaderArtifactStaysAvailableAsAHeader() throws IOException {
        Output plain = convert("repeated-header", 3, marked(PAGINATION_HEADER), false);
        assertTrue(plain.markdown.contains(BODY + " 3"), "a paragraph of the tree is missing: " + plain.markdown);
        assertFalse(plain.json.contains(ARTIFACT), "the header shows without --include-header-footer");

        Output headers = convert("repeated-header-included", 3, marked(PAGINATION_HEADER), true);
        assertTrue(headers.json.contains("\"type\" : \"header\"") && headers.json.contains(ARTIFACT),
            "--include-header-footer lost the header artifact");
    }

    /** The content of a page before its paragraph. */
    private interface PageStart {
        String write(PDDocument doc, PDResources resources, String font) throws IOException;
    }

    /** The artifact text at (40, 260) in a marked-content sequence that {@code begin} starts. */
    private static PageStart marked(String begin) {
        return (doc, resources, font) -> begin + "\n" + text(font, 40, 260, ARTIFACT) + "EMC\n";
    }

    private static String text(String font, int x, int y, String text) {
        return "BT /" + font + " 12 Tf " + x + " " + y + " Td (" + text + ") Tj ET\n";
    }

    private static PDFormXObject form(PDDocument doc, PDResources resources, String content) throws IOException {
        PDFormXObject form = new PDFormXObject(doc);
        form.setBBox(new PDRectangle(400, 300));
        form.setResources(resources);
        write(form.getContentStream(), content);
        return form;
    }

    private static Output convert(String name, int pages, PageStart start, boolean includeHeaderFooter)
            throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-801-" + name);
        Path pdf = writeTaggedPdf(dir.resolve(name + ".pdf"), pages, start);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setUseStructTree(true);
        config.setGenerateMarkdown(true);
        config.setGenerateJSON(true);
        config.setIncludeHeaderFooter(includeHeaderFooter);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new Output(Files.readString(dir.resolve(name + ".md")), Files.readString(dir.resolve(name + ".json")));
    }

    /**
     * Each page: the content of {@code start}, then a {@code /P} with MCID 0. The body text differs on each
     * page of a document with more than one page, so it does not become a header.
     */
    private static Path writeTaggedPdf(Path pdf, int pages, PageStart start) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDStructureTreeRoot root = new PDStructureTreeRoot();
            COSArray nums = new COSArray();
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage(new PDRectangle(400, 300));
                doc.addPage(page);
                PDResources resources = new PDResources();
                String font = resources.add(new PDType1Font(Standard14Fonts.FontName.HELVETICA)).getName();
                page.setResources(resources);
                String body = pages == 1 ? BODY : BODY + " " + (i + 1);
                String content = start.write(doc, resources, font)
                    + "/P <</MCID 0>> BDC\n" + text(font, 40, 200, body) + "EMC\n";
                PDStream stream = new PDStream(doc);
                write(stream, content);
                page.setContents(stream);
                page.setStructParents(i);

                PDStructureElement paragraph = new PDStructureElement("P", root);
                paragraph.setPage(page);
                paragraph.getCOSObject().setInt(COSName.K, 0);
                root.appendKid(paragraph);
                COSArray owners = new COSArray();
                owners.add(paragraph);
                nums.add(COSInteger.get(i));
                nums.add(owners);
            }
            COSDictionary parentTree = new COSDictionary();
            parentTree.setItem(COSName.NUMS, nums);
            root.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
            root.getCOSObject().setInt(COSName.PARENT_TREE_NEXT_KEY, pages);
            doc.getDocumentCatalog().setStructureTreeRoot(root);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            doc.getDocumentCatalog().setMarkInfo(markInfo);
            doc.getDocumentCatalog().setLanguage("en");
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static void write(PDStream stream, String content) throws IOException {
        try (OutputStream os = stream.createOutputStream()) {
            os.write(content.getBytes(StandardCharsets.US_ASCII));
        }
    }

    private static final class Output {
        final String markdown;
        final String json;

        Output(String markdown, String json) {
            this.markdown = markdown;
            this.json = json;
        }
    }
}

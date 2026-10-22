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
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #820: with {@code --sanitize}, the tagged PDF of a document failed with
 * {@code IndexOutOfBoundsException: Index -1 out of bounds for length 1}, and no tagged PDF was written.
 *
 * <p>The trigger is an e-mail address under a link annotation. The address is shorter than its
 * replacement ({@code info@pdfa.org}, 13 characters, against {@code email@example.com}, 17
 * characters), and one text-show operator holds only the address. The left edge of the link is
 * 0.0024 pt to the right of the first glyph. Thus the tagged PDF splits one character from the
 * replacement chunk, and the split counts its stream infos as characters of the replacement.
 * The measurements come from {@code PDFUA-Ref-2-06_Brochure.pdf} in {@code samples/pdf}: body
 * text of 9.5 pt, a left link edge 0.0024 pt inside the first glyph, and a right link edge
 * 0.0029 pt outside the last glyph.
 *
 * <p>A replacement that is longer than the replaced text gets a stream info without a glyph for its
 * extra characters. This stream info must not stop the merge of the glyphs before and after it.
 * Otherwise the tagged PDF splits the text-show operator of the line and puts the space after the
 * replacement in an artifact.
 *
 * <p>The samples are synthetic: one page with body text, and one line with an address under a
 * link or in the text. The output stays in {@code target/test-samples/issue-820/} and
 * {@code target/test-samples/issue-820-text/}.
 */
class Issue820SanitizedLinkTaggedPdfTest {

    private static final float FONT_SIZE = 9.5f;
    private static final float LEADING = 11.6f;
    private static final float LEFT = 72;
    private static final float TOP = 600;
    // The link edges of the reporter's document, relative to the glyph edges.
    private static final float LINK_LEFT_OFFSET = 0.0024f;
    private static final float LINK_RIGHT_OFFSET = 0.0029f;
    private static final String PREFIX = "Send your questions to ";
    private static final String ADDRESS = "info@test.org";
    // One text-show operator holds the line, with the address between two words.
    private static final String TEXT_LINE = PREFIX + ADDRESS + " for a copy.";
    private static final String[] LINES = {
        "The working group meets four times each year to review the drafts of the",
        "guide. Each meeting has a short agenda, and the minutes record each decision.",
        "Members can comment on a draft until the end of the month after the meeting.",
        null,
        "The next edition of the guide will include examples for forms and tables.",
        "It will also give a list of the tools that the members used for the tests.",
    };

    private static Path writeAddressUnderLink(Path dir) throws IOException {
        Path file = dir.resolve("address-link.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float addressLeft = LEFT + font.getStringWidth(PREFIX) / 1000 * FONT_SIZE;
            float addressRight = addressLeft + font.getStringWidth(ADDRESS) / 1000 * FONT_SIZE;
            float addressY = 0;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < LINES.length; i++) {
                    float y = TOP - i * LEADING;
                    if (LINES[i] != null) {
                        showText(cs, font, LEFT, y, LINES[i]);
                        continue;
                    }
                    // One text-show operator holds only the address, as in the reporter's document.
                    showText(cs, font, LEFT, y, PREFIX);
                    showText(cs, font, addressLeft, y, ADDRESS);
                    addressY = y;
                }
            }
            PDAnnotationLink link = new PDAnnotationLink();
            float left = addressLeft + LINK_LEFT_OFFSET;
            float right = addressRight + LINK_RIGHT_OFFSET;
            link.setRectangle(new PDRectangle(left, addressY - 2.3f, right - left, LEADING));
            PDActionURI action = new PDActionURI();
            action.setURI("mailto:" + ADDRESS);
            link.setAction(action);
            page.getAnnotations().add(link);
            doc.save(file.toFile());
        }
        return file;
    }

    private static Path writeAddressInText(Path dir) throws IOException {
        Path file = dir.resolve("address-text.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < LINES.length; i++) {
                    showText(cs, font, LEFT, TOP - i * LEADING, LINES[i] != null ? LINES[i] : TEXT_LINE);
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static void showText(PDPageContentStream cs, PDType1Font font, float x, float y, String text)
            throws IOException {
        cs.beginText();
        cs.setFont(font, FONT_SIZE);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    @Test
    void sanitizedAddressUnderLinkDoesNotStopTheTaggedPdf() throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-820");
        Path source = writeAddressUnderLink(dir);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        config.getFilterConfig().setFilterSensitiveData(true);

        DocumentProcessor.processFile(source.toString(), config);

        File tagged = dir.resolve("address-link_tagged.pdf").toFile();
        assertThat(tagged).as("tagged PDF").isFile();
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject()
                .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
            List<Integer> treeMcids = new ArrayList<>();
            addMcidKids(root, treeMcids);
            List<Integer> linkMcids = new ArrayList<>();
            addMcidKids(findLink(root), linkMcids);
            List<Integer> contentMcids = contentMcids(doc);
            Collections.sort(treeMcids);
            Collections.sort(contentMcids);
            assertThat(contentMcids).as("marked content on the text").isNotEmpty();
            assertThat(treeMcids).as("MCIDs in the structure tree and in the content stream").isEqualTo(contentMcids);
            assertThat(linkMcids).as("MCIDs of the address under the link").isNotEmpty();
        }
    }

    @Test
    void sanitizedAddressInTextKeepsItsLineInOneSequence() throws IOException {
        // Without a link, the replacement must not split the text-show operator of its line, and no
        // glyph of the line can become an artifact.
        Path dir = TestSamples.emptyDirectory("issue-820-text");
        Path source = writeAddressInText(dir);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        config.getFilterConfig().setFilterSensitiveData(true);

        DocumentProcessor.processFile(source.toString(), config);

        File tagged = dir.resolve("address-text_tagged.pdf").toFile();
        assertThat(tagged).as("tagged PDF").isFile();
        List<String> shownText = new ArrayList<>();
        List<String> artifactText = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            addShownText(doc, shownText, artifactText);
        }
        assertThat(shownText).as("text-show operators").contains(TEXT_LINE);
        assertThat(artifactText).as("text in artifacts").isEmpty();
    }

    /** Adds the string of each Tj operator on the first page, and of each Tj operator in an artifact. */
    private static void addShownText(PDDocument doc, List<String> shownText, List<String> artifactText)
            throws IOException {
        List<Object> tokens = new PDFStreamParser(doc.getPage(0)).parse();
        Deque<String> tags = new ArrayDeque<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof Operator)) {
                continue;
            }
            String name = ((Operator) tokens.get(i)).getName();
            if ("BDC".equals(name)) {
                tags.push(((COSName) tokens.get(i - 2)).getName());
            } else if ("BMC".equals(name)) {
                tags.push(((COSName) tokens.get(i - 1)).getName());
            } else if ("EMC".equals(name) && !tags.isEmpty()) {
                tags.pop();
            } else if ("Tj".equals(name) && tokens.get(i - 1) instanceof COSString) {
                String text = ((COSString) tokens.get(i - 1)).getString();
                shownText.add(text);
                if (tags.contains("Artifact")) {
                    artifactText.add(text);
                }
            }
        }
    }

    /** The MCIDs of the marked-content sequences on the first page. */
    private static List<Integer> contentMcids(PDDocument doc) throws IOException {
        List<Integer> mcids = new ArrayList<>();
        List<Object> tokens = new PDFStreamParser(doc.getPage(0)).parse();
        for (int i = 1; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (token instanceof Operator && "BDC".equals(((Operator) token).getName())
                    && tokens.get(i - 1) instanceof COSDictionary) {
                COSBase mcid = ((COSDictionary) tokens.get(i - 1)).getDictionaryObject(COSName.MCID);
                if (mcid instanceof COSInteger) {
                    mcids.add(((COSInteger) mcid).intValue());
                }
            }
        }
        return mcids;
    }

    /** Adds the MCIDs referenced from the structure tree below {@code node}, as integers or MCRs. */
    private static void addMcidKids(COSBase node, List<Integer> mcids) {
        COSBase base = node instanceof COSObject ? ((COSObject) node).getObject() : node;
        if (base instanceof COSInteger) {
            mcids.add(((COSInteger) base).intValue());
        } else if (base instanceof COSArray) {
            for (COSBase kid : (COSArray) base) {
                addMcidKids(kid, mcids);
            }
        } else if (base instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) base;
            COSBase mcid = dictionary.getDictionaryObject(COSName.MCID);
            if (mcid instanceof COSInteger) {
                mcids.add(((COSInteger) mcid).intValue());
            } else {
                addMcidKids(dictionary.getDictionaryObject(COSName.K), mcids);
            }
        }
    }

    /** The first structure element of type Link below {@code node}, or null. */
    private static COSDictionary findLink(COSBase node) {
        COSBase base = node instanceof COSObject ? ((COSObject) node).getObject() : node;
        if (base instanceof COSArray) {
            for (COSBase kid : (COSArray) base) {
                COSDictionary link = findLink(kid);
                if (link != null) {
                    return link;
                }
            }
        } else if (base instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) base;
            if (COSName.getPDFName("Link").equals(dictionary.getDictionaryObject(COSName.S))) {
                return dictionary;
            }
            return findLink(dictionary.getDictionaryObject(COSName.K));
        }
        return null;
    }
}

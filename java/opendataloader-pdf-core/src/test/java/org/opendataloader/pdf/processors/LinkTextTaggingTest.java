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
package org.opendataloader.pdf.processors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.apache.pdfbox.text.PDFMarkedContentExtractor;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.awt.geom.Point2D;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The text a link is drawn over goes to its Link element whole.
 *
 * <p>The shape of a table of contents: each entry is a link whose box starts where the entry's
 * first glyph starts, and a producer can write the same link twice.
 */
class LinkTextTaggingTest {

    private static final String NUMBER = "3.   ";
    private static final String ENTRY = "Our Mental Shortcuts";
    private static final float X = 72;
    private static final float Y = 700;
    private static final float SIZE = 12;
    private static final String ALPHA = "Alpha first entry here";
    private static final String BRAVO = "Bravo second entry here";

    /**
     * A page with one line, the entry's number and then the entry, drawn by one operator. One link
     * covers the number up to the entry's first glyph; {@code titleLinks} links start on that glyph
     * and cover the entry, each a point higher than the one before, as a producer writes them.
     */
    private static Path write(Path dir, String name, int titleLinks) throws IOException {
        Path file = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, SIZE);
                cs.newLineAtOffset(X, Y);
                cs.showText(NUMBER + ENTRY);
                cs.endText();
            }
            float edge = X + font.getStringWidth(NUMBER) / 1000 * SIZE;
            float end = X + font.getStringWidth(NUMBER + ENTRY) / 1000 * SIZE;
            page.getAnnotations().add(link(page, X - 10, Y - 3, edge));
            for (int i = 0; i < titleLinks; i++) {
                page.getAnnotations().add(link(page, edge, Y - 3 + i, end));
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static PDAnnotationLink link(PDPage page, float left, float bottom, float right) {
        return link(page, left, bottom, right, bottom + SIZE + 3);
    }

    private static PDAnnotationLink link(PDPage page, float left, float bottom, float right, float top) {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(new PDRectangle(left, bottom, right - left, top - bottom));
        PDActionGoTo goTo = new PDActionGoTo();
        PDPageFitDestination destination = new PDPageFitDestination();
        destination.setPage(page);
        goTo.setDestination(destination);
        link.setAction(goTo);
        return link;
    }

    /**
     * Two lines, each drawn by its own operator, and two links: a tall link over the first line and
     * the start of the second, and on the second line a link that starts three points left of it
     * and ends where {@code secondEnd}, the second line's first words, ends.
     */
    private static Path writeTwoLines(Path dir, String name, String second, String secondEnd) throws IOException {
        Path file = dir.resolve(name + ".pdf");
        String first = "Please read the full guide";
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (String[] line : new String[][]{{first, "700"}, {second, "686"}}) {
                    cs.beginText();
                    cs.setFont(font, SIZE);
                    cs.newLineAtOffset(X, Float.parseFloat(line[1]));
                    cs.showText(line[0]);
                    cs.endText();
                }
            }
            float right = X + font.getStringWidth(first) / 1000 * SIZE;
            page.getAnnotations().add(uriLink("https://tall", new PDRectangle(X, Y - 17, right - X, 28)));
            float secondRight = secondEnd == null ? right + 3 : X + font.getStringWidth(secondEnd) / 1000 * SIZE;
            page.getAnnotations().add(uriLink("https://line2", new PDRectangle(X - 3, Y - 17, secondRight - X + 3, 14)));
            doc.save(file.toFile());
        }
        return file;
    }

    /**
     * A table of contents heading, "Part II.  Chapter Two - Test Statistics", drawn as one text
     * object for each word and space, and its link written twice, as a producer wrote it: a box over
     * the line and a narrower, taller box over "Part II.", whose left edge is {@code narrowLeft}.
     */
    private static Path writeHeading(Path dir, String name, float narrowLeft) throws IOException {
        Path file = dir.resolve(name + ".pdf");
        String[] pieces = {"Part", " ", "II.", " ", " ", "Chapter Two - Test Statistics"};
        float[] xs = {85.04f, 102.69f, 105.22f, 114.51f, 117.03f, 119.56f};
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < pieces.length; i++) {
                    cs.beginText();
                    cs.setFont(font, 10.8f);
                    cs.newLineAtOffset(xs[i], 420.55f);
                    cs.showText(pieces[i]);
                    cs.endText();
                }
            }
            page.getAnnotations().add(link(page, 85.04f, 418.39f, 443.79f, 429.19f));
            page.getAnnotations().add(link(page, narrowLeft, 415.42f, 117.03f, 432.16f));
            doc.save(file.toFile());
        }
        return file;
    }

    /**
     * One line under {@code count} links, each a little wider than the one before on both sides, as
     * a crafted document can have them.
     */
    private static Path writeManyLinks(Path dir, String name, String line, int count) throws IOException {
        Path file = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, SIZE);
                cs.newLineAtOffset(X, Y);
                cs.showText(line);
                cs.endText();
            }
            float right = X + font.getStringWidth(line) / 1000 * SIZE;
            for (int i = 0; i < count; i++) {
                float step = 0.9f * i / count;
                page.getAnnotations().add(link(page, X - step, Y - 3, right + step));
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /**
     * One line, "ABC read the guide here", whose "B" has no Unicode: the font's ToUnicode map gives
     * it U+FFFD. A link starts on the "B" and covers the rest of the line.
     */
    private static Path writeUnmappedGlyph(Path dir, String name) throws IOException {
        Path file = dir.resolve(name + ".pdf");
        String line = "ABC read the guide here";
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            StringBuilder cmap = new StringBuilder("/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
                + "/CMapName /Unmapped def /CMapType 2 def\n1 begincodespacerange <00> <FF> endcodespacerange\n");
            Set<Character> chars = new TreeSet<>();
            for (char c : line.toCharArray()) {
                chars.add(c);
            }
            cmap.append(chars.size()).append(" beginbfchar\n");
            for (char c : chars) {
                cmap.append(String.format("<%02X> <%04X>%n", (int) c, c == 'B' ? 0xFFFD : (int) c));
            }
            cmap.append("endbfchar\nendcmap\nCMapName currentdict /CMap defineresource pop\nend\nend\n");
            PDStream toUnicode = new PDStream(doc,
                new ByteArrayInputStream(cmap.toString().getBytes(StandardCharsets.US_ASCII)));
            font.getCOSObject().setItem(COSName.TO_UNICODE, toUnicode);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, SIZE);
                cs.newLineAtOffset(X, Y);
                cs.showText(line);
                cs.endText();
            }
            float left = X + font.getStringWidth("A") / 1000 * SIZE - 0.3f;
            float right = X + font.getStringWidth(line) / 1000 * SIZE;
            page.getAnnotations().add(uriLink("https://guide", new PDRectangle(left, Y - 3, right - left, SIZE + 3)));
            doc.save(file.toFile());
        }
        return file;
    }

    /**
     * Two entries, each on its own line under its own link, on a Letter page that {@code boxes}
     * changes: a page box or the rotation. {@code toPage} places the lines and the links, which
     * are laid out as the page is seen, on the page.
     */
    private static Path writeEntries(Path dir, String name, Consumer<PDPage> boxes, Matrix toPage)
            throws IOException {
        Path file = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            boxes.accept(page);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            String[][] entries = {{ALPHA, "533", "https://alpha"}, {BRAVO, "503", "https://bravo"}};
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (String[] entry : entries) {
                    cs.beginText();
                    cs.setFont(font, SIZE);
                    cs.setTextMatrix(Matrix.getTranslateInstance(105, Float.parseFloat(entry[1])).multiply(toPage));
                    cs.showText(entry[0]);
                    cs.endText();
                }
            }
            for (String[] entry : entries) {
                float right = 105 + font.getStringWidth(entry[0]) / 1000 * SIZE + 2;
                float baseline = Float.parseFloat(entry[1]);
                page.getAnnotations().add(uriLink(entry[2], onPage(toPage, 105, baseline - 3, right, baseline + SIZE)));
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** The box from (left, bottom) to (right, top), placed on the page by {@code toPage}. */
    private static PDRectangle onPage(Matrix toPage, float left, float bottom, float right, float top) {
        Point2D.Float a = toPage.transformPoint(left, bottom);
        Point2D.Float b = toPage.transformPoint(right, top);
        return new PDRectangle(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    private static PDAnnotationLink uriLink(String uri, PDRectangle rect) {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(rect);
        PDActionURI action = new PDActionURI();
        action.setURI(uri);
        link.setAction(action);
        return link;
    }

    private static File tag(String name, Path source) throws IOException {
        return tag(name, source, new Config());
    }

    private static File tag(String name, Path source, Config config) throws IOException {
        config.setOutputFolder(source.getParent().toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);
        return source.getParent().resolve(name + "_tagged.pdf").toFile();
    }

    private static File tag(String name, int links) throws IOException {
        return tag(name, write(TestSamples.emptyDirectory(name), name, links));
    }

    /** Text by MCID, from the page's marked content. */
    private static Map<Integer, String> textByMcid(PDPage page) throws IOException {
        PDFMarkedContentExtractor extractor = new PDFMarkedContentExtractor();
        extractor.processPage(page);
        Map<Integer, String> out = new HashMap<>();
        for (PDMarkedContent content : extractor.getMarkedContents()) {
            collect(content, out);
        }
        return out;
    }

    private static void collect(PDMarkedContent content, Map<Integer, String> out) {
        StringBuilder text = new StringBuilder();
        for (Object child : content.getContents()) {
            if (child instanceof TextPosition) {
                text.append(((TextPosition) child).getUnicode());
            } else if (child instanceof PDMarkedContent) {
                collect((PDMarkedContent) child, out);
            }
        }
        if (content.getMCID() >= 0) {
            out.merge(content.getMCID(), text.toString(), String::concat);
        }
    }

    /** Each element that holds MCIDs: its type and its MCIDs, in tree order. */
    private static void elements(COSBase node, List<Map.Entry<String, List<Integer>>> out) {
        if (node instanceof COSObject) {
            node = ((COSObject) node).getObject();
        }
        if (node instanceof COSArray) {
            for (COSBase kid : (COSArray) node) {
                elements(kid, out);
            }
            return;
        }
        if (!(node instanceof COSDictionary)) {
            return;
        }
        COSDictionary dict = (COSDictionary) node;
        COSName type = dict.getCOSName(COSName.TYPE);
        if (COSName.getPDFName("MCR").equals(type) || COSName.getPDFName("OBJR").equals(type)) {
            return;
        }
        COSBase k = dict.getDictionaryObject(COSName.K);
        List<COSBase> kids = new ArrayList<>();
        if (k instanceof COSArray) {
            ((COSArray) k).forEach(kids::add);
        } else if (k != null) {
            kids.add(k);
        }
        List<Integer> own = new ArrayList<>();
        for (COSBase kid : kids) {
            COSBase direct = kid instanceof COSObject ? ((COSObject) kid).getObject() : kid;
            if (direct instanceof COSInteger) {
                own.add(((COSInteger) direct).intValue());
            } else if (direct instanceof COSDictionary
                    && COSName.getPDFName("MCR").equals(((COSDictionary) direct).getCOSName(COSName.TYPE))) {
                own.add(((COSDictionary) direct).getInt(COSName.MCID));
            }
        }
        COSName s = dict.getCOSName(COSName.S);
        out.add(Map.entry(s != null ? s.getName() : "?", own));
        for (COSBase kid : kids) {
            elements(kid, out);
        }
    }

    private static List<Map.Entry<String, List<Integer>>> elements(PDDocument doc) {
        COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject()
            .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
        List<Map.Entry<String, List<Integer>>> out = new ArrayList<>();
        elements(root.getDictionaryObject(COSName.K), out);
        return out;
    }

    private static String textOf(List<Integer> mcids, Map<Integer, String> text) {
        StringBuilder out = new StringBuilder();
        for (Integer mcid : mcids) {
            out.append(text.getOrDefault(mcid, ""));
        }
        return out.toString().replaceAll("\\s+", "");
    }

    /** The text of each Link that holds any, without spaces: they lie between the marks. */
    private static List<String> linkTexts(List<Map.Entry<String, List<Integer>>> elements,
                                          Map<Integer, String> text) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> element : elements) {
            String own = textOf(element.getValue(), text);
            if ("Link".equals(element.getKey()) && !own.isEmpty()) {
                out.add(own);
            }
        }
        return out;
    }

    /** The text of the elements that are not links: what fell out of them. */
    private static String outsideLinks(List<Map.Entry<String, List<Integer>>> elements, Map<Integer, String> text) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, List<Integer>> element : elements) {
            if (!"Link".equals(element.getKey())) {
                out.append(textOf(element.getValue(), text));
            }
        }
        return out.toString();
    }

    /**
     * "O" fell out of the entry's link and read on its own, before "ur": the link starts on the
     * glyph, where the number's link ends.
     */
    @Test
    void theGlyphOnTwoLinksEdgeIsInTheLinkItStarts() throws IOException {
        try (PDDocument doc = Loader.loadPDF(tag("link-edge", 1))) {
            Map<Integer, String> text = textByMcid(doc.getPage(0));
            List<Map.Entry<String, List<Integer>>> elements = elements(doc);

            assertThat(linkTexts(elements, text)).containsExactly("3.", "OurMentalShortcuts");
            assertThat(outsideLinks(elements, text)).isEmpty();
        }
    }

    /**
     * Two links over the entry: the first holds it whole, and no element holds an MCID the page
     * does not mark. "M" was lost, and the elements held MCIDs that marked nothing.
     */
    @Test
    void aSecondLinkOverTheSameTextTakesNoneOfIt() throws IOException {
        try (PDDocument doc = Loader.loadPDF(tag("link-twice", 2))) {
            Map<Integer, String> text = textByMcid(doc.getPage(0));
            List<Map.Entry<String, List<Integer>>> elements = elements(doc);

            assertThat(linkTexts(elements, text)).containsExactly("3.", "OurMentalShortcuts");
            assertThat(outsideLinks(elements, text)).isEmpty();
            Set<Integer> referenced = new TreeSet<>();
            elements.forEach(element -> referenced.addAll(element.getValue()));
            assertThat(text.keySet()).containsAll(referenced);
        }
    }

    private static String sorted(String text) {
        char[] chars = text.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    /** OBJR references by the annotation they reference, over the whole tree. */
    private static void objrs(COSBase node, Map<COSBase, Integer> out, Set<COSBase> seen) {
        if (node instanceof COSObject) {
            node = ((COSObject) node).getObject();
        }
        if (node instanceof COSArray) {
            for (COSBase kid : (COSArray) node) {
                objrs(kid, out, seen);
            }
        } else if (node instanceof COSDictionary && seen.add(node)) {
            COSDictionary dict = (COSDictionary) node;
            if (COSName.getPDFName("OBJR").equals(dict.getCOSName(COSName.TYPE))) {
                COSBase annotation = dict.getDictionaryObject(COSName.getPDFName("Obj"));
                out.merge(annotation, 1, Integer::sum);
                return;
            }
            objrs(dict.getDictionaryObject(COSName.K), out, seen);
        }
    }

    private static void assertEachGlyphAndLinkOnce(File tagged, String text) throws IOException {
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            PDPage page = doc.getPage(0);
            Map<Integer, String> byMcid = textByMcid(page);
            List<Map.Entry<String, List<Integer>>> elements = elements(doc);

            StringBuilder taggedText = new StringBuilder();
            Set<Integer> referenced = new TreeSet<>();
            elements.forEach(element -> {
                referenced.addAll(element.getValue());
                taggedText.append(textOf(element.getValue(), byMcid));
            });
            // Each glyph once; the order is the tree's, which these tests do not judge.
            assertThat(sorted(taggedText.toString())).isEqualTo(sorted(text.replaceAll("\\s+", "")));
            assertThat(byMcid.keySet()).containsAll(referenced);

            COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject()
                .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
            Map<COSBase, Integer> objrs = new HashMap<>();
            objrs(root.getDictionaryObject(COSName.K), objrs, new HashSet<>());
            assertThat(objrs.values()).hasSize(page.getAnnotations().size()).containsOnly(1);
        }
    }

    /**
     * A link carried over from the line above ended where a link that starts left of it began:
     * the new link was started at the old one's right edge, and "on", between the two, was marked
     * as an artifact. Each glyph is tagged once, and each link is in the tree once.
     */
    @Test
    void aLinkCarriedFromTheLineAboveGivesNoGlyphAway() throws IOException {
        String second = "on the site now and then some more words here";
        Path dir = TestSamples.emptyDirectory("link-carried");
        assertEachGlyphAndLinkOnce(tag("link-carried", writeTwoLines(dir, "link-carried", second, null)),
            "Please read the full guide" + second);
    }

    /**
     * The same, with a short link on the second line: the link carried over from the line above
     * was taken up again after the short one ended, and its annotation had two Link elements.
     */
    @Test
    void aLinkThatEndedIsNotTakenUpAgain() throws IOException {
        String second = "on the site now";
        Path dir = TestSamples.emptyDirectory("link-ended");
        assertEachGlyphAndLinkOnce(tag("link-ended", writeTwoLines(dir, "link-ended", second, "on the")),
            "Please read the full guide" + second);
    }

    private static void assertHeadingInOneLink(String name, float narrowLeft) throws IOException {
        String heading = "Part II.  Chapter Two - Test Statistics";
        File tagged = tag(name, writeHeading(TestSamples.emptyDirectory(name), name, narrowLeft));
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            Map<Integer, String> text = textByMcid(doc.getPage(0));
            List<Map.Entry<String, List<Integer>>> elements = elements(doc);

            assertThat(linkTexts(elements, text)).containsExactly(heading.replaceAll("\\s+", ""));
            assertThat(outsideLinks(elements, text)).isEmpty();
        }
        assertEachGlyphAndLinkOnce(tagged, heading);
    }

    /**
     * A heading's link written twice, a box over the line and a narrower one over "Part II." with
     * the same left edge: the heading is in one link. The links were taken in an order of their
     * own, and when the narrower one came first, the heading read "Part" in one link and
     * "II. Chapter Two - Test Statistics" in the other.
     */
    @Test
    void aLinkWithinAWiderOneTakesNoneOfItsText() throws IOException {
        assertHeadingInOneLink("link-wider-same-left", 85.04f);
    }

    /** The same, with the narrower box half a point to the left, so that it comes first. */
    @Test
    void aLinkWithinAWiderOneThatStartsLeftOfItTakesNoneOfItsText() throws IOException {
        assertHeadingInOneLink("link-wider-left-of", 84.5f);
    }

    /**
     * More links over one line than are compared each with each: each glyph is tagged once, and each
     * link is in the tree once.
     */
    @Test
    void manyLinksOverOneLineTagEachGlyphOnce() throws IOException {
        String line = "Please read the full guide";
        String name = "link-many";
        assertEachGlyphAndLinkOnce(tag(name, writeManyLinks(TestSamples.emptyDirectory(name), name, line, 40)), line);
    }

    /**
     * A link over part of a word with a glyph without Unicode, and a replacement of three
     * characters for that glyph: the word has more characters than glyphs. Cut at the link's edge,
     * it stopped the tagged PDF with an IndexOutOfBoundsException. The word goes to one side whole.
     */
    @Test
    void aLinkOverAWordWithALongerReplacementGivesATaggedPdf() throws IOException {
        String name = "link-replaced-glyph";
        Config config = new Config();
        config.setReplaceInvalidChars("[?]");
        File tagged = tag(name, writeUnmappedGlyph(TestSamples.emptyDirectory(name), name), config);

        assertThat(tagged).isFile();
        assertEachGlyphAndLinkOnce(tagged, "A\uFFFDC read the guide here");
    }

    private static void assertEachEntryInItsLink(String name, Consumer<PDPage> boxes, Matrix toPage)
            throws IOException {
        File tagged = tag(name, writeEntries(TestSamples.emptyDirectory(name), name, boxes, toPage));
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            Map<Integer, String> text = textByMcid(doc.getPage(0));
            List<Map.Entry<String, List<Integer>>> elements = elements(doc);

            assertThat(linkTexts(elements, text))
                .containsExactly(ALPHA.replaceAll("\\s+", ""), BRAVO.replaceAll("\\s+", ""));
            assertThat(outsideLinks(elements, text)).isEmpty();
        }
        assertEachGlyphAndLinkOnce(tagged, ALPHA + BRAVO);
    }

    /** Page boxes at the origin, and no rotation: each entry is in its own link. */
    @Test
    void eachEntryIsInItsLinkOnAPageAtTheOrigin() throws IOException {
        assertEachEntryInItsLink("link-page-origin", page -> { }, new Matrix());
    }

    /**
     * A MediaBox that starts at (33, 33): each entry is in its own link, as at the origin. The text
     * is placed from the CropBox's lower-left corner, and the links were not: each link was
     * compared with the text 33 points away from it.
     */
    @Test
    void eachEntryIsInItsLinkOnAPageWhoseMediaBoxIsOffTheOrigin() throws IOException {
        assertEachEntryInItsLink("link-mediabox-offset",
            page -> page.setMediaBox(new PDRectangle(33, 33, 612, 792)), new Matrix());
    }

    /** The same, with only the CropBox off the origin. */
    @Test
    void eachEntryIsInItsLinkOnAPageWhoseCropBoxIsOffTheOrigin() throws IOException {
        assertEachEntryInItsLink("link-cropbox-offset",
            page -> page.setCropBox(new PDRectangle(33, 33, 540, 740)), new Matrix());
    }

    /**
     * The same, on a page turned a quarter, whose lines are drawn turned back so that they read
     * across the page as it is seen: the text is turned with the page, and the links were not.
     */
    @Test
    void eachEntryIsInItsLinkOnARotatedPage() throws IOException {
        assertEachEntryInItsLink("link-page-rotated", page -> page.setRotation(90), new Matrix(0, 1, -1, 0, 612, 0));
    }

    /**
     * The contents on page 1 of a PDF/UA reference document, whose MediaBox starts at (33, 33): each
     * entry is in its own link. "of Maturation" was in the link of another entry, and the links of
     * "An Overview of Maturation and Growth" and "Development of the Brain" were empty.
     */
    @Test
    void eachContentsEntryOfTheBookChapterIsInItsLink() throws IOException {
        String name = "link-book-chapter";
        Path source = Files.copy(Paths.get("../../samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-08_BookChapter.pdf"),
            TestSamples.emptyDirectory(name).resolve(name + ".pdf"));
        Config config = new Config();
        config.setPages("1");
        try (PDDocument doc = Loader.loadPDF(tag(name, source, config))) {
            Map<Integer, String> text = textByMcid(doc.getPage(0));

            assertThat(linkTexts(elements(doc), text)).containsExactly(
                "AnOverviewofMaturationandGrowth",
                "DevelopmentoftheBrain",
                "MotorDevelopment",
                "FocusonResearch:SportsParticipationandSelf-EsteemAmongAdolescentFemales",
                "Puberty:ThePhysicalTransitionfromChildtoAdult",
                "CausesandCorrelatesofPhysicalDevelopment",
                "ApplyingDevelopmentalThemestoPhysicalDevelopment");
        }
    }
}

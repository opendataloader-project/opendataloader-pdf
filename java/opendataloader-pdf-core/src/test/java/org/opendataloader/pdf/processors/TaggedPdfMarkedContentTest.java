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
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.text.PDFMarkedContentExtractor;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.AutoTagger;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.TaggingResult;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Producers such as Skia (Google Docs, Chrome) draw one glyph per {@code Tj}. The tagged
 * output must not turn each of those into its own marked-content sequence: a paragraph
 * would then hold hundreds of one-character MCIDs.
 */
class TaggedPdfMarkedContentTest {

    private static final String TEXT = "Statement of account for the quarter";

    private static Path writeOneGlyphPerTj(Path dir) throws IOException {
        Path file = dir.resolve("glyphs.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 720);
                for (char c : TEXT.toCharArray()) {
                    cs.showText(String.valueOf(c));
                    cs.newLineAtOffset(font.getStringWidth(String.valueOf(c)) / 1000 * 12, 0);
                }
                cs.endText();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static int countMarkedContentSequences(File pdf) throws IOException {
        int count = 0;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (Object token : new PDFStreamParser(doc.getPage(0)).parse()) {
                if (token instanceof Operator && "BDC".equals(((Operator) token).getName())) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Counts the integer MCIDs referenced from the structure tree below {@code node}. */
    private static int countMcidKids(COSBase node) {
        COSBase base = node instanceof COSObject ? ((COSObject) node).getObject() : node;
        if (base instanceof COSInteger) {
            return 1;
        }
        int count = 0;
        if (base instanceof COSArray) {
            for (COSBase kid : (COSArray) base) {
                count += countMcidKids(kid);
            }
        } else if (base instanceof COSDictionary) {
            count += countMcidKids(((COSDictionary) base).getDictionaryObject(COSName.K));
        }
        return count;
    }

    @Test
    void oneGlyphPerTjDoesNotProduceOneSequencePerGlyph(@TempDir Path dir) throws IOException {
        Path source = writeOneGlyphPerTj(dir);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);

        File tagged = dir.resolve("glyphs_tagged.pdf").toFile();
        assertTrue(tagged.isFile(), "expected a tagged PDF at " + tagged);

        int sequences = countMarkedContentSequences(tagged);
        // Spaces are tagged as artifacts, so a word is the largest run that can share a sequence.
        int words = TEXT.split(" ").length;
        assertTrue(sequences >= 1 && sequences <= words,
                "expected at most one marked-content sequence per word (" + words + "), got "
                        + sequences + " for " + TEXT.length() + " glyphs");

        // The structure tree must reference exactly the MCIDs the content stream carries.
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
            assertTrue(countMcidKids(root) == sequences,
                    "structure tree references " + countMcidKids(root) + " MCIDs, content has " + sequences);
        }
    }

    // Body text of an arXiv paper made by pdfTeX: 10.9091 pt, with TJ word gaps of -198
    // (0.198 em). veraPDF inserts a space into a TJ above 0.17 em, and the text chunk keeps it up
    // to 0.21 em. The inserted space is not in the content stream, so its stream info has the
    // operator index -1.
    private static final float BODY_SIZE = 10.9091f;
    private static final float WORD_GAP = -198;
    private static final String[] BODY_LINES = {
        "Autoregressive models dominate the current landscape of large language",
        "models, but they generate one token at a time and cannot revise",
        "earlier output. Diffusion models refine all positions in parallel and",
        "can correct a mistake in a later step. This report describes a",
        "diffusion variant of an open model and the data that trained it.",
        "We compare it with the base model on common tasks.",
    };

    private static Path writeKernedLines(Path dir) throws IOException {
        Path file = dir.resolve("kerned.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, BODY_SIZE);
                cs.newLineAtOffset(72, 500);
                for (String line : BODY_LINES) {
                    String[] words = line.split(" ");
                    Object[] tj = new Object[words.length * 2 - 1];
                    for (int i = 0; i < words.length; i++) {
                        tj[i * 2] = words[i];
                        if (i > 0) {
                            tj[i * 2 - 1] = WORD_GAP;
                        }
                    }
                    cs.showTextWithPositioning(tj);
                    cs.newLineAtOffset(0, -13);
                }
                cs.endText();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    @Test
    void insertedSpacesGetNoMcid(@TempDir Path dir) throws IOException {
        Path source = writeKernedLines(dir);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);

        File tagged = dir.resolve("kerned_tagged.pdf").toFile();
        assertTrue(tagged.isFile(), "expected a tagged PDF at " + tagged);

        // An inserted space is in no operator, so no marked content can carry its MCID. The
        // structure tree must reference exactly the MCIDs the content stream carries: the same
        // values, each once.
        List<Integer> contentMcids = contentMcids(tagged);
        assertTrue(!contentMcids.isEmpty(), "expected marked content on the text, got none");
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
            List<Integer> treeMcids = new ArrayList<>();
            addMcidKids(root, treeMcids);
            Collections.sort(contentMcids);
            Collections.sort(treeMcids);
            assertEquals(contentMcids, treeMcids, "MCIDs in the structure tree and in the content stream");
        }
    }

    // A paragraph as a scan's OCR text layer draws it: each word its own Tj with its own Tm,
    // Times-Roman 8.1 pt on a 9 pt leading. The layer draws the left part of every line first and
    // then the right part, so the last word of one line's left part comes right before the first word
    // of the next line's left part in the stream.
    private static final String[][] SCAN_LINES = {
        {"The first line starts", "and then it ends here;"},
        {"the second line starts", "and it ends here too;"},
        {"the third line starts", "and the paragraph ends."},
    };

    private static Path writeLinesDrawnInTwoParts(Path dir) throws IOException {
        Path file = dir.resolve("scan-layer.pdf");
        float size = 8.1f;
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, size);
                for (int part = 0; part < 2; part++) {
                    for (int line = 0; line < SCAN_LINES.length; line++) {
                        float x = part == 0 ? 300 : 406;
                        float y = 452 - 9 * line;
                        for (String word : SCAN_LINES[line][part].split(" ")) {
                            cs.setTextMatrix(Matrix.getTranslateInstance(x, y));
                            cs.showText(word + " ");
                            x += font.getStringWidth(word + " ") / 1000 * size;
                        }
                    }
                }
                cs.endText();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /**
     * Text-show operators of one element that are adjacent in the stream share one MCID only when
     * they also follow each other in the element. A caller that builds the paragraph from the visual
     * lines, as the Hancom AI route of odl-pdfua does, hands the tagger the words in line order. The
     * left parts of the three lines are adjacent in the stream, and they became one span: the
     * paragraph read "The first line starts the second line starts the third line starts and then it
     * ends here; …", the left parts and then the right parts.
     */
    @Test
    void textShowsAdjacentInTheStreamOnlyShareAnMcidInReadingOrder(@TempDir Path dir) throws IOException {
        Path source = writeLinesDrawnInTwoParts(dir);
        Path tagged = dir.resolve("scan-layer_tagged.pdf");
        try (ExtractionResult extraction = DocumentProcessor.extractContents(source.toString(), new Config())) {
            List<IObject> page = extraction.getContents().get(0);
            List<TextChunk> chunks = new ArrayList<>();
            collectChunks(page, chunks);
            // The visual lines, top to bottom, each left to right.
            chunks.sort(Comparator.comparingLong((TextChunk c) -> -Math.round(c.getBoundingBox().getCenterY()))
                    .thenComparingDouble(c -> c.getBoundingBox().getLeftX()));
            TextLine line = new TextLine(chunks.get(0));
            for (TextChunk chunk : chunks.subList(1, chunks.size())) {
                line.add(chunk);
            }
            SemanticParagraph paragraph = new SemanticParagraph();
            paragraph.add(line);
            paragraph.setBoundingBox(new BoundingBox(line.getBoundingBox()));
            page.clear();
            page.add(paragraph);
            DocumentProcessor.setIndexesForDocumentContents(extraction.getContents());
            try (TaggingResult result = AutoTagger.tag(extraction, null)) {
                result.saveTo(tagged.toString());
            }
        }

        StringBuilder expected = new StringBuilder();
        for (String[] visual : SCAN_LINES) {
            expected.append(visual[0]).append(' ').append(visual[1]).append(' ');
        }
        try (PDDocument doc = Loader.loadPDF(tagged.toFile())) {
            Map<Integer, String> text = textByMcid(doc.getPage(0));
            COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
            List<Integer> treeMcids = new ArrayList<>();
            addMcidKids(root, treeMcids);
            StringBuilder read = new StringBuilder();
            for (int mcid : treeMcids) {
                read.append(text.getOrDefault(mcid, "")).append(' ');
            }
            assertEquals(normalized(expected.toString()), normalized(read.toString()),
                    "the text of the tags, in the order of the structure tree");

            // The words of one part of a line follow each other in the stream and in the
            // paragraph, so they still share one MCID.
            List<String> parts = new ArrayList<>();
            for (String[] visual : SCAN_LINES) {
                parts.add(visual[0]);
                parts.add(visual[1]);
            }
            List<String> spans = new ArrayList<>();
            for (int mcid : treeMcids) {
                spans.add(normalized(text.getOrDefault(mcid, "")));
            }
            assertEquals(parts, spans, "the text of each MCID, in the order of the structure tree");
        }
    }

    private static void collectChunks(List<IObject> objects, List<TextChunk> out) {
        for (IObject object : objects) {
            if (object instanceof SemanticTextNode) {
                for (TextColumn column : ((SemanticTextNode) object).getColumns()) {
                    for (TextLine textLine : column.getLines()) {
                        for (TextChunk chunk : textLine.getTextChunks()) {
                            if (!chunk.isWhiteSpaceChunk()) {
                                out.add(chunk);
                            }
                        }
                    }
                }
            }
        }
    }

    private static String normalized(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }

    /** Text by MCID, from the page's marked content. */
    private static Map<Integer, String> textByMcid(PDPage page) throws IOException {
        PDFMarkedContentExtractor extractor = new PDFMarkedContentExtractor();
        extractor.processPage(page);
        Map<Integer, String> out = new HashMap<>();
        for (PDMarkedContent content : extractor.getMarkedContents()) {
            StringBuilder text = new StringBuilder();
            for (Object child : content.getContents()) {
                if (child instanceof TextPosition) {
                    text.append(((TextPosition) child).getUnicode());
                }
            }
            if (content.getMCID() >= 0) {
                out.merge(content.getMCID(), text.toString(), String::concat);
            }
        }
        return out;
    }

    /** The MCIDs of the marked-content sequences on the first page, in stream order. */
    private static List<Integer> contentMcids(File pdf) throws IOException {
        List<Integer> mcids = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
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
}

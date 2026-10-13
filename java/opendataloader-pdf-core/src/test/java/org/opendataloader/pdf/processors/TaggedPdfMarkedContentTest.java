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
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

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
}

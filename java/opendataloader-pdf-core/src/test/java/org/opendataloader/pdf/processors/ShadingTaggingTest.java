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
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.function.PDFunctionType2;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.shading.PDShadingType2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shading ({@code sh}) is a paint operator like a fill, so it must sit in a marked-content
 * sequence of the output. The rewrite drops the producer's own marked content and wraps again
 * only the operators it knows; {@code sh} was not one, so a budget's gradient bands stayed in
 * none (7 of them in 71 pages), which fails PDF/UA-1 7.1 test 3 and PDF/UA-2 8.2.2.1.
 */
class ShadingTaggingTest {

    private static PDShadingType2 axialShading() {
        COSDictionary function = new COSDictionary();
        function.setInt(COSName.FUNCTION_TYPE, 2);
        COSArray domain = new COSArray();
        domain.add(COSInteger.ZERO);
        domain.add(COSInteger.ONE);
        function.setItem(COSName.DOMAIN, domain);
        COSArray c0 = new COSArray();
        c0.add(COSInteger.ONE);
        c0.add(COSInteger.ZERO);
        c0.add(COSInteger.ZERO);
        COSArray c1 = new COSArray();
        c1.add(COSInteger.ZERO);
        c1.add(COSInteger.ZERO);
        c1.add(COSInteger.ONE);
        function.setItem(COSName.C0, c0);
        function.setItem(COSName.C1, c1);
        function.setItem(COSName.N, new COSFloat(1));
        COSDictionary dictionary = new COSDictionary();
        dictionary.setInt(COSName.SHADING_TYPE, 2);
        dictionary.setItem(COSName.COLORSPACE, COSName.DEVICERGB);
        COSArray coords = new COSArray();
        coords.add(COSInteger.get(100));
        coords.add(COSInteger.get(400));
        coords.add(COSInteger.get(300));
        coords.add(COSInteger.get(400));
        dictionary.setItem(COSName.COORDS, coords);
        dictionary.setItem(COSName.FUNCTION, new PDFunctionType2(function).getCOSObject());
        return new PDShadingType2(dictionary);
    }

    /** Text and two gradient bands, each painted by {@code sh} inside its own clip. */
    private static Path writeShadings(Path dir) throws IOException {
        Path file = dir.resolve("shadings.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(72, 720);
                cs.showText("Budget summary for the year");
                cs.endText();
                cs.saveGraphicsState();
                cs.addRect(100, 380, 200, 40);
                cs.clip();
                cs.shadingFill(axialShading());
                cs.restoreGraphicsState();
                // A second band, so the operator after a Q is covered as well.
                cs.saveGraphicsState();
                cs.addRect(100, 300, 200, 40);
                cs.clip();
                cs.shadingFill(axialShading());
                cs.restoreGraphicsState();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** The marked-content nesting depth at each {@code sh} of the page, in order. */
    private static List<Integer> depthsAtShadings(Path pdf) throws IOException {
        List<Integer> depths = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            int depth = 0;
            for (Object token : new PDFStreamParser(doc.getPage(0)).parse()) {
                if (!(token instanceof Operator)) {
                    continue;
                }
                String name = ((Operator) token).getName();
                if ("BMC".equals(name) || "BDC".equals(name)) {
                    depth++;
                } else if ("EMC".equals(name)) {
                    depth--;
                } else if ("sh".equals(name)) {
                    depths.add(depth);
                }
            }
        }
        return depths;
    }

    @Test
    void everyShadingSitsInAMarkedContentSequence(@TempDir Path dir) throws IOException {
        Path source = writeShadings(dir);
        assertEquals(List.of(0, 0), depthsAtShadings(source), "the source has its shadings in no sequence");

        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);

        List<Integer> depths = depthsAtShadings(dir.resolve("shadings_tagged.pdf"));
        assertEquals(2, depths.size(), "no shading may be lost or doubled");
        for (int depth : depths) {
            assertTrue(depth == 1, "each shading is inside one marked-content sequence, got depth " + depth);
        }
    }

    @Test
    void aShadingIsAnArtifactWhenNoElementOwnsIt(@TempDir Path dir) throws IOException {
        Path source = writeShadings(dir);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);

        try (PDDocument doc = Loader.loadPDF(dir.resolve("shadings_tagged.pdf").toFile())) {
            List<Object> tokens = new PDFStreamParser(doc.getPage(0)).parse();
            int artifacts = 0;
            for (int i = 0; i + 3 < tokens.size(); i++) {
                if (tokens.get(i) instanceof COSName && "Artifact".equals(((COSName) tokens.get(i)).getName())
                    && tokens.get(i + 1) instanceof Operator && "BMC".equals(((Operator) tokens.get(i + 1)).getName())
                    && tokens.get(i + 3) instanceof Operator && "sh".equals(((Operator) tokens.get(i + 3)).getName())) {
                    artifacts++;
                }
            }
            assertEquals(2, artifacts, "each shading is wrapped as /Artifact BMC <name> sh EMC");
        }
    }

    /**
     * A path that is built and not painted before an {@code sh} is not content a conforming file
     * has, but it must not end up inside the shading's span: {@code sh} does not paint it, and the
     * span would carry construction operators that nothing in it paints. It stays where it was.
     */
    @Test
    void aPathLeftOpenBeforeAShadingStaysOutsideItsSpan(@TempDir Path dir) throws IOException {
        Path source = dir.resolve("open-path.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.addRect(100, 380, 200, 40);
                cs.shadingFill(axialShading());
            }
            doc.save(source.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);

        try (PDDocument doc = Loader.loadPDF(dir.resolve("open-path_tagged.pdf").toFile())) {
            List<Object> tokens = new PDFStreamParser(doc.getPage(0)).parse();
            int rect = -1;
            int span = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i) instanceof Operator && "re".equals(((Operator) tokens.get(i)).getName())) {
                    rect = i;
                }
                if (tokens.get(i) instanceof Operator && "BMC".equals(((Operator) tokens.get(i)).getName())) {
                    span = i;
                }
            }
            assertTrue(rect >= 0 && span >= 0, "the path and the span are in the output");
            assertTrue(rect < span, "the path comes before the span");
        }
    }
}

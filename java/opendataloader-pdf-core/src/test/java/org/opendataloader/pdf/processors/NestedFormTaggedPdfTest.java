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
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tagging content in a form that a page form draws.
 *
 * <p>The shape of a signed resolution: the page draws a form, and that form draws the form that
 * holds the scanned signature. The page's resources list only the outer form.
 */
class NestedFormTaggedPdfTest {

    private static final COSName OUTER = COSName.getPDFName("FFT0");
    private static final COSName INNER = COSName.getPDFName("F0");

    private static Path write(Path dir, String name) throws IOException {
        Path file = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            BufferedImage signature = new BufferedImage(150, 60, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = signature.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 150, 60);
            g.setColor(Color.BLACK);
            g.drawLine(5, 50, 60, 10);
            g.drawLine(60, 10, 140, 45);
            g.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(doc, signature);

            PDFormXObject inner = new PDFormXObject(doc);
            inner.setBBox(PDRectangle.LETTER);
            inner.setResources(new PDResources());
            try (PDFormContentStream cs = new PDFormContentStream(inner)) {
                cs.drawImage(image, 72, 500, 150, 60);
            }

            PDFormXObject outer = new PDFormXObject(doc);
            outer.setBBox(PDRectangle.LETTER);
            PDResources outerResources = new PDResources();
            outerResources.put(INNER, inner);
            outer.setResources(outerResources);
            try (PDFormContentStream cs = new PDFormContentStream(outer)) {
                cs.drawForm(inner);
            }

            page.setResources(new PDResources());
            page.getResources().put(OUTER, outer);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(72, 700);
                cs.showText("The board adopted the resolution by the vote shown below.");
                cs.endText();
                cs.drawForm(outer);
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static File tag(String name) throws IOException {
        Path dir = TestSamples.emptyDirectory(name);
        Path source = write(dir, name);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);
        File tagged = dir.resolve(name + "_tagged.pdf").toFile();
        assertThat(tagged).as("tagged PDF").isFile();
        return tagged;
    }

    @Test
    void anImageInAFormInsideAFormIsMarkedInItsOwnForm() throws IOException {
        File tagged = tag("nested-form");
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            PDPage page = doc.getPage(0);
            PDFormXObject outer = (PDFormXObject) page.getResources().getXObject(OUTER);
            PDFormXObject inner = (PDFormXObject) outer.getResources().getXObject(INNER);
            COSDictionary root = (COSDictionary) doc.getDocumentCatalog().getCOSObject()
                .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));

            List<COSDictionary> refs = new ArrayList<>();
            addMcrs(root, refs);
            List<Integer> innerMcids = new ArrayList<>();
            for (COSDictionary mcr : refs) {
                if (mcr.getDictionaryObject(COSName.getPDFName("Stm")) == inner.getCOSObject()) {
                    innerMcids.add(mcr.getInt(COSName.MCID));
                }
            }
            assertThat(innerMcids).as("MCRs into the form that holds the image").isNotEmpty();
            assertThat(contentMcids(inner)).as("marked content in that form").containsAll(innerMcids);

            List<Integer> pageMcids = new ArrayList<>();
            addIntegerKids(root, pageMcids);
            assertThat(contentMcids(page)).as("the page's own MCIDs, each with marked content")
                .containsAll(pageMcids);
        }
    }

    private static List<Integer> contentMcids(PDContentStream stream) throws IOException {
        List<Integer> mcids = new ArrayList<>();
        List<Object> tokens = new PDFStreamParser(stream).parse();
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator && "BDC".equals(((Operator) tokens.get(i)).getName())
                    && tokens.get(i - 1) instanceof COSDictionary) {
                COSBase mcid = ((COSDictionary) tokens.get(i - 1)).getDictionaryObject(COSName.MCID);
                if (mcid instanceof COSInteger) {
                    mcids.add(((COSInteger) mcid).intValue());
                }
            }
        }
        return mcids;
    }

    /** Adds the MCR dictionaries below {@code node}. */
    private static void addMcrs(COSBase node, List<COSDictionary> mcrs) {
        COSBase base = node instanceof COSObject ? ((COSObject) node).getObject() : node;
        if (base instanceof COSArray) {
            for (COSBase kid : (COSArray) base) {
                addMcrs(kid, mcrs);
            }
        } else if (base instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) base;
            if (COSName.getPDFName("MCR").equals(dictionary.getDictionaryObject(COSName.TYPE))) {
                mcrs.add(dictionary);
            } else {
                addMcrs(dictionary.getDictionaryObject(COSName.K), mcrs);
            }
        }
    }

    /** Adds the integer MCIDs below {@code node}: the page's own marked content. */
    private static void addIntegerKids(COSBase node, List<Integer> mcids) {
        COSBase base = node instanceof COSObject ? ((COSObject) node).getObject() : node;
        if (base instanceof COSInteger) {
            mcids.add(((COSInteger) base).intValue());
        } else if (base instanceof COSArray) {
            for (COSBase kid : (COSArray) base) {
                addIntegerKids(kid, mcids);
            }
        } else if (base instanceof COSDictionary
                && !COSName.getPDFName("MCR").equals(((COSDictionary) base).getDictionaryObject(COSName.TYPE))) {
            addIntegerKids(((COSDictionary) base).getDictionaryObject(COSName.K), mcids);
        }
    }
}

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

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDFormContentStream;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFMarkedContentExtractor;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review of #669: in hybrid mode, the tagged PDF of a page that gets OCR text keeps its native text
 * extractable and inside the structure tree (a marked-content sequence with an MCID, not an
 * artifact), as main does, for page set-ups that the PR's tests do not build: resources inherited
 * from the page tree and shared by two pages, native text drawn in a Form XObject, a /Contents
 * array, and --sanitize. The OCR text of the image is extractable. No backend and no network: a
 * local mock server answers with the backend's nodes.
 */
class Review669_ContentStreamCasesTest {

    private static final String OCR = "Recovered from the image";
    private static final String TOP = "Native line at the top";
    private static final String BOTTOM = "Native line at the bottom";
    private static final String SECOND_PAGE = "Native line of the second page";

    @TempDir
    Path tempDir;

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        HybridClientFactory.shutdown();
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            server.close();
        } finally {
            OpenDataLoaderPDF.shutdown();
        }
    }

    @Test
    void twoPagesThatShareInheritedResourcesKeepTheirNativeText() throws IOException {
        Path pdf = tempDir.resolve("inherited.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage first = new PDPage(PDRectangle.A4);
            PDPage second = new PDPage(PDRectangle.A4);
            document.addPage(first);
            document.addPage(second);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, first)) {
                show(content, font, TOP, 72, 770);
                content.drawImage(grayImage(document), 60, 300, 450, 300);
            }
            try (PDPageContentStream content = new PDPageContentStream(document, second)) {
                show(content, font, SECOND_PAGE, 72, 770);
            }
            // One /Resources on the page tree, inherited by both pages: the font and the image.
            PDResources shared = first.getResources();
            for (COSName name : second.getResources().getFontNames()) {
                shared.put(name, second.getResources().getFont(name));
            }
            document.getPages().getCOSObject().setItem(COSName.RESOURCES, shared.getCOSObject());
            first.getCOSObject().removeItem(COSName.RESOURCES);
            second.getCOSObject().removeItem(COSName.RESOURCES);
            document.save(pdf.toFile());
        }
        Path tagged = convert(pdf, node(TOP, 1, 72, 782, 300, 766) + "," + node(OCR, 1, 72, 500, 400, 482) + ","
            + node(SECOND_PAGE, 2, 72, 782, 300, 766), "\"1\": {}, \"2\": {}", false);
        // main writes both native lines of this set-up as artifacts; the bar is main: both stay extractable.
        String text = rawText(tagged);
        for (String line : List.of(TOP, SECOND_PAGE)) {
            assertTrue(text.contains(line), "\"" + line + "\" is not extractable from the tagged PDF:\n" + text);
        }
        assertOcrExtractable(tagged);
    }

    @Test
    void nativeTextDrawnInAFormXObjectStaysTagged() throws IOException {
        Path pdf = tempDir.resolve("form.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFormXObject form = new PDFormXObject(document);
            form.setBBox(new PDRectangle(0, 0, PDRectangle.A4.getWidth(), PDRectangle.A4.getHeight()));
            form.setResources(new PDResources());
            try (PDFormContentStream content = new PDFormContentStream(form)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(72, 770);
                content.showText(TOP);
                content.endText();
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawForm(form);
                content.drawImage(grayImage(document), 60, 300, 450, 300);
                show(content, font, BOTTOM, 72, 100);
            }
            document.save(pdf.toFile());
        }
        Path tagged = convert(pdf, node(TOP, 1, 72, 782, 300, 766) + "," + node(OCR, 1, 72, 500, 400, 482) + ","
            + node(BOTTOM, 1, 72, 112, 300, 96), "\"1\": {}", false);
        assertNativeTagged(tagged, TOP, BOTTOM);
        assertOcrExtractable(tagged);
    }

    @Test
    void aContentsArrayKeepsTheNativeTextOfEachStreamTagged() throws IOException {
        Path pdf = tempDir.resolve("array.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                show(content, font, TOP, 72, 770);
                content.drawImage(grayImage(document), 60, 300, 450, 300);
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                show(content, font, BOTTOM, 72, 100);
            }
            document.save(pdf.toFile());
        }
        Path tagged = convert(pdf, node(TOP, 1, 72, 782, 300, 766) + "," + node(OCR, 1, 72, 500, 400, 482) + ","
            + node(BOTTOM, 1, 72, 112, 300, 96), "\"1\": {}", false);
        assertNativeTagged(tagged, TOP, BOTTOM);
        assertOcrExtractable(tagged);
    }

    @Test
    void sanitizeWithAnEmailInTheOcrTextStillWritesTheTaggedPdf() throws IOException {
        Path pdf = tempDir.resolve("sanitize.pdf");
        String nativeEmail = "Native contact native.desk@example.com";
        String ocrEmail = "Write to pat.doe@example.com for help";
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                show(content, font, nativeEmail, 72, 770);
                content.drawImage(grayImage(document), 60, 300, 450, 300);
            }
            document.save(pdf.toFile());
        }
        Path tagged = convert(pdf, node(nativeEmail, 1, 72, 782, 320, 766) + "," + node(ocrEmail, 1, 72, 500, 400, 482),
            "\"1\": {}", true);
        String text = rawText(tagged);
        assertTrue(text.contains("Native contact"), "the native line is gone from the tagged PDF:\n" + text);
        assertTrue(text.contains("Write to") && text.contains("for help"),
            "the OCR text is not extractable from the tagged PDF:\n" + text);
    }

    private Path convert(Path pdf, String texts, String pages, boolean sanitize) throws IOException {
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder()
            .body("{\"status\": \"success\", \"document\": {\"json_content\": {\"texts\": [" + texts + "],"
                + " \"pages\": {" + pages + "}}}, \"processing_time\": 0.5, \"errors\": [], \"failed_pages\": []}")
            .addHeader("Content-Type", "application/json")
            .build());
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateTaggedPDF(true);
        config.setGenerateJSON(true);
        config.getFilterConfig().setFilterSensitiveData(sanitize);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        DocumentProcessor.processFile(pdf.toString(), config);
        String name = pdf.getFileName().toString().replace(".pdf", "");
        Path tagged = tempDir.resolve(name + "_tagged.pdf");
        assertTrue(Files.exists(tagged), "no tagged PDF was written");
        return tagged;
    }

    private static void assertNativeTagged(Path tagged, String... lines) throws IOException {
        String text = rawText(tagged);
        List<String> inTags = new ArrayList<>();
        List<String> inArtifacts = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(tagged.toFile())) {
            for (PDPage page : document.getPages()) {
                PDFMarkedContentExtractor extractor = new PDFMarkedContentExtractor();
                extractor.processPage(page);
                for (PDMarkedContent content : extractor.getMarkedContents()) {
                    collect(content, false, inTags, inArtifacts);
                }
            }
        }
        for (String line : lines) {
            assertTrue(text.contains(line), "\"" + line + "\" is not extractable from the tagged PDF:\n" + text);
            assertTrue(String.join(" ", inTags).contains(line),
                "\"" + line + "\" is not in a tagged sequence; tagged: " + inTags + "; artifacts: " + inArtifacts);
        }
    }

    private static void assertOcrExtractable(Path tagged) throws IOException {
        String text = rawText(tagged);
        assertTrue(text.contains(OCR), "the OCR text is not extractable from the tagged PDF:\n" + text);
    }

    private static void collect(PDMarkedContent content, boolean inArtifact, List<String> inTags,
                                List<String> inArtifacts) {
        boolean artifact = inArtifact || "Artifact".equals(content.getTag());
        StringBuilder text = new StringBuilder();
        for (Object item : content.getContents()) {
            if (item instanceof TextPosition) {
                text.append(((TextPosition) item).getUnicode());
            } else if (item instanceof PDMarkedContent) {
                collect((PDMarkedContent) item, artifact, inTags, inArtifacts);
            }
        }
        if (text.length() == 0) {
            return;
        }
        if (artifact) {
            inArtifacts.add(text.toString());
        } else if (content.getMCID() >= 0) {
            inTags.add(text.toString());
        }
    }

    private static String rawText(Path tagged) throws IOException {
        try (PDDocument document = Loader.loadPDF(tagged.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            return stripper.getText(document);
        }
    }

    private static void show(PDPageContentStream content, PDType1Font font, String text, float x, float y)
            throws IOException {
        content.beginText();
        content.setFont(font, 12);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
    }

    private static PDImageXObject grayImage(PDDocument document) throws IOException {
        BufferedImage raster = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = raster.createGraphics();
        graphics.setColor(new Color(200, 200, 200));
        graphics.fillRect(0, 0, 600, 400);
        graphics.dispose();
        return LosslessFactory.createFromImage(document, raster);
    }

    private static String node(String text, int page, float left, float top, float right, float bottom) {
        return "{\"label\": \"text\", \"text\": \"" + text + "\", \"prov\": [{\"page_no\": " + page + ", \"bbox\": {\"l\": "
            + left + ", \"t\": " + top + ", \"r\": " + right + ", \"b\": " + bottom
            + ", \"coord_origin\": \"BOTTOMLEFT\"}}]}";
    }
}

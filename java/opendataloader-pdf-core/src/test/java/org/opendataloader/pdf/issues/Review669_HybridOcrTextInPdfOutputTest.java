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
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.hybrid.ScannedPagePdf;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review of #669: in hybrid mode, text that the backend recovered from an image (no Java text,
 * no paths under it) is in the tagged PDF as extractable text, and native text stays.
 */
class Review669_HybridOcrTextInPdfOutputTest {

    /** OCR text with the three characters that a PDF literal string must escape. */
    private static final String OCR_TEXT = "Scanned words (one) and back\\slash";
    private static final String NATIVE_TEXT = "Native line of text";

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
    void ocrTextOfAScannedPageIsExtractableFromTheTaggedPdf() throws IOException {
        PDRectangle a4 = PDRectangle.A4;
        Path pdf = ScannedPagePdf.of(a4).writeTo(tempDir.resolve("scan.pdf"));
        String text = convertTagged(pdf, "scan", textNode(OCR_TEXT, 72, a4.getHeight() - 72, 400, a4.getHeight() - 90));

        assertTrue(text.contains(OCR_TEXT), "the OCR text is not extractable from the tagged PDF:\n" + text);
    }

    @Test
    void nativeTextStaysWhenOcrTextIsAddedOnThePage() throws IOException {
        PDRectangle a4 = PDRectangle.A4;
        Path pdf = nativeTextAboveAnImage(tempDir.resolve("mixed.pdf"), a4);
        String text = convertTagged(pdf, "mixed",
            textNode(NATIVE_TEXT, 72, a4.getHeight() - 60, 300, a4.getHeight() - 76) + ","
                + textNode(OCR_TEXT, 72, 500, 400, 482));

        assertTrue(text.contains(NATIVE_TEXT), "the native text is gone from the tagged PDF:\n" + text);
    }

    /** Converts with a mocked docling-fast backend in full mode and returns the text of the tagged PDF. */
    private String convertTagged(Path pdf, String name, String texts) throws IOException {
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder()
            .body("{\"status\": \"success\", \"document\": {\"json_content\": {\"texts\": [" + texts + "],"
                + " \"pages\": {\"1\": {}}}}, \"processing_time\": 0.5, \"errors\": [], \"failed_pages\": []}")
            .addHeader("Content-Type", "application/json")
            .build());
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateTaggedPDF(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));

        DocumentProcessor.processFile(pdf.toString(), config);

        try (PDDocument tagged = Loader.loadPDF(tempDir.resolve(name + "_tagged.pdf").toFile())) {
            return new PDFTextStripper().getText(tagged);
        }
    }

    private static String textNode(String text, float left, float top, float right, float bottom) {
        String escaped = text.replace("\\", "\\\\");
        return "{\"label\": \"text\", \"text\": \"" + escaped + "\", \"prov\": [{\"page_no\": 1, \"bbox\": {\"l\": " + left
            + ", \"t\": " + top + ", \"r\": " + right + ", \"b\": " + bottom + ", \"coord_origin\": \"BOTTOMLEFT\"}}]}";
    }

    /** A page with one line of native text at the top and a gray image (no text in it) below. */
    private static Path nativeTextAboveAnImage(Path file, PDRectangle pageSize) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            BufferedImage raster = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = raster.createGraphics();
            graphics.setColor(new Color(200, 200, 200));
            graphics.fillRect(0, 0, 600, 400);
            graphics.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(document, raster);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, pageSize.getHeight() - 72);
                content.showText(NATIVE_TEXT);
                content.endText();
                content.drawImage(image, 60, 300, 450, 300);
            }
            document.save(file.toFile());
        }
        return file;
    }
}

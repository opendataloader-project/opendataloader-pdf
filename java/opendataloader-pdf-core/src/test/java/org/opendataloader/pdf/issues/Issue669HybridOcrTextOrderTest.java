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
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
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
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.parser.Operators;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * In hybrid mode, text the backend recovered from an image goes into the page's content stream
 * next to the native text before it, not at the end of the stream. A page has a native line, an
 * image with OCR text, and a second native line; read in raw content-stream order (the order a
 * plain text extractor uses), the OCR text comes between the two native lines.
 *
 * <p>Adding the OCR text shifts the position of every operator after it. The second test checks
 * that native text the backend returned nothing for is still extractable and tagged afterwards: a
 * stale position would leave it outside the structure tree, as an artifact.
 */
class Issue669HybridOcrTextOrderTest {

    private static final String FIRST = "First native line";
    private static final String OCR = "Recovered from the image";
    private static final String LAST = "Last native line";
    private static final String ARTIFACT_TAG = "Artifact";

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
    void ocrTextOfAnImageStaysBetweenTheNativeLinesAroundIt() throws IOException {
        PDRectangle a4 = PDRectangle.A4;
        String texts = node(FIRST, 72, a4.getHeight() - 60, 300, a4.getHeight() - 76) + ","
            + node(OCR, 72, 500, 400, 482) + ","
            + node(LAST, 72, 116, 300, 98);

        String text = rawOrderText(convertTagged(texts));

        assertInOrder(text);
    }

    @Test
    void nativeTextTheBackendReturnedNothingForStaysTaggedAfterOcrTextIsInserted() throws IOException {
        // The backend returns only the OCR text: both native lines are text it did not map. No
        // order is asserted: the OCR text is anchored in reading-order tree order, and text the
        // backend missed comes after the backend's own nodes, so it is not placed between them.
        String texts = node(OCR, 72, 500, 400, 482);

        Path tagged = convertTagged(texts);

        String text = rawOrderText(tagged);
        List<String> artifacts = textInArtifacts(tagged);
        for (String line : List.of(FIRST, OCR, LAST)) {
            assertTrue(text.contains(line), "\"" + line + "\" is not extractable from the tagged PDF:\n" + text);
            assertTrue(artifacts.stream().noneMatch(shown -> shown.contains(line)),
                "\"" + line + "\" is outside the structure tree (an artifact); artifacts: " + artifacts);
        }
    }

    private static void assertInOrder(String text) {
        int first = text.indexOf(FIRST);
        int ocr = text.indexOf(OCR);
        int last = text.indexOf(LAST);
        assertTrue(first >= 0 && last >= 0, "a native line is gone from the tagged PDF:\n" + text);
        assertTrue(ocr >= 0, "the OCR text is not extractable from the tagged PDF:\n" + text);
        assertTrue(first < ocr && ocr < last,
            "the OCR text is not between the native lines around it (first=" + first + ", ocr=" + ocr
                + ", last=" + last + "):\n" + text);
    }

    /** Converts a generated page with a mocked docling-fast backend in full mode; returns the tagged PDF. */
    private Path convertTagged(String backendTexts) throws IOException {
        Path pdf = nativeLineImageNativeLine(tempDir.resolve("order.pdf"), PDRectangle.A4);
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder()
            .body("{\"status\": \"success\", \"document\": {\"json_content\": {\"texts\": [" + backendTexts + "],"
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

        return tempDir.resolve("order_tagged.pdf");
    }

    /** The page text in raw content-stream order, which is what a plain text extractor reads. */
    private static String rawOrderText(Path tagged) throws IOException {
        try (PDDocument document = Loader.loadPDF(tagged.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            return stripper.getText(document);
        }
    }

    /** Every string shown inside an /Artifact marked-content sequence on the first page. */
    private static List<String> textInArtifacts(Path tagged) throws IOException {
        List<String> shown = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(tagged.toFile())) {
            List<Object> tokens = new PDFStreamParser(document.getPage(0)).parse();
            Deque<String> tags = new ArrayDeque<>();
            for (int i = 0; i < tokens.size(); i++) {
                if (!(tokens.get(i) instanceof Operator)) {
                    continue;
                }
                String name = ((Operator) tokens.get(i)).getName();
                if (Operators.BMC.equals(name) && i >= 1) {
                    tags.push(tagName(tokens.get(i - 1)));
                } else if (Operators.BDC.equals(name) && i >= 2) {
                    tags.push(tagName(tokens.get(i - 2)));
                } else if (Operators.EMC.equals(name) && !tags.isEmpty()) {
                    tags.pop();
                } else if ((Operators.TJ_SHOW.equals(name) || Operators.TJ_SHOW_POS.equals(name)) && i >= 1
                        && tags.contains(ARTIFACT_TAG)) {
                    shown.add(shownText(tokens.get(i - 1)));
                }
            }
        }
        return shown;
    }

    private static String tagName(Object token) {
        return token instanceof COSName ? ((COSName) token).getName() : "";
    }

    private static String shownText(Object operand) {
        if (operand instanceof COSString) {
            return ((COSString) operand).getString();
        }
        StringBuilder text = new StringBuilder();
        if (operand instanceof COSArray) {
            for (COSBase element : (COSArray) operand) {
                if (element instanceof COSString) {
                    text.append(((COSString) element).getString());
                }
            }
        }
        return text.toString();
    }

    private static String node(String text, float left, float top, float right, float bottom) {
        return "{\"label\": \"text\", \"text\": \"" + text + "\", \"prov\": [{\"page_no\": 1, \"bbox\": {\"l\": " + left
            + ", \"t\": " + top + ", \"r\": " + right + ", \"b\": " + bottom + ", \"coord_origin\": \"BOTTOMLEFT\"}}]}";
    }

    /** A native line at the top, a gray image (no text in it) in the middle, and a native line at the bottom. */
    private static Path nativeLineImageNativeLine(Path file, PDRectangle pageSize) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(pageSize);
            document.addPage(page);
            BufferedImage raster = new BufferedImage(600, 400, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = raster.createGraphics();
            graphics.setColor(new Color(200, 200, 200));
            graphics.fillRect(0, 0, 600, 400);
            graphics.dispose();
            PDImageXObject image = LosslessFactory.createFromImage(document, raster);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(72, pageSize.getHeight() - 72);
                content.showText(FIRST);
                content.endText();
                content.drawImage(image, 60, 300, 450, 300);
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(72, 100);
                content.showText(LAST);
                content.endText();
            }
            document.save(file.toFile());
        }
        return file;
    }
}

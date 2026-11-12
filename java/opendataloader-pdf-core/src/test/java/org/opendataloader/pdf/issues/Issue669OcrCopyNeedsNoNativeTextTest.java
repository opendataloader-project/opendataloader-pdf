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
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review of #669, the edge of the rule "add the invisible text only for a node under which the page
 * shows no text". "Under" is the node's box widened by 5 points, the same slack that matches a node
 * to the text chunks it holds. The page draws one native line. The backend returns two nodes for
 * the same words: one over the line, which takes the native text, and a second whose left edge lies
 * a given distance to the right of the middle of the last word. Inside the slack, the page shows the
 * second node's text (the first node took it), so no copy is added. Outside it, nothing on the
 * page is under the second node, so its words are OCR text and keep their invisible copy.
 */
class Issue669OcrCopyNeedsNoNativeTextTest {

    private static final String LAST_WORD = "totals";
    private static final String LINE = "Quarterly " + LAST_WORD;
    private static final float LEFT = 72;
    private static final float BASELINE = 500;
    private static final float FONT_SIZE = 10;

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

    @ParameterizedTest(name = "node starts {0} pt right of the middle of the last word: read {1} time(s)")
    @CsvSource({
        "1, 1",
        "3, 1",
        "12, 2",
        "30, 2",
    })
    void copyIsAddedOnlyWhenTheNodeIsOutsideTheMatchingSlack(float gap, int expectedReads) throws IOException {
        Path pdf = writeLine(tempDir.resolve("line.pdf"));
        PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float lastWordMiddle = LEFT + (font.getStringWidth(LINE) - font.getStringWidth(LAST_WORD) / 2) / 1000 * FONT_SIZE;
        float left = lastWordMiddle + gap;
        String texts = node(LEFT - 2, 200) + "," + node(left, 120);
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

        String text;
        try (PDDocument tagged = Loader.loadPDF(tempDir.resolve("line_tagged.pdf").toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            text = stripper.getText(tagged);
        }
        int reads = 0;
        for (int i = text.indexOf(LINE); i >= 0; i = text.indexOf(LINE, i + LINE.length())) {
            reads++;
        }
        assertEquals(expectedReads, reads, "reads of \"" + LINE + "\" in:\n" + text);
    }

    private static String node(float left, float width) {
        return "{\"label\": \"text\", \"text\": \"" + LINE + "\", \"prov\": [{\"page_no\": 1, \"bbox\": {\"l\": "
            + left + ", \"t\": " + (BASELINE + 12) + ", \"r\": " + (left + width) + ", \"b\": " + (BASELINE - 4)
            + ", \"coord_origin\": \"BOTTOMLEFT\"}}]}";
    }

    private static Path writeLine(Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, FONT_SIZE);
                content.newLineAtOffset(LEFT, BASELINE);
                content.showText(LINE);
                content.endText();
            }
            document.save(file.toFile());
        }
        return file;
    }
}

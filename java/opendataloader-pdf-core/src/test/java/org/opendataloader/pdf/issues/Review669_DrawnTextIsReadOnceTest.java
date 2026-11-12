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
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDMarkedContent;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review of #669: text that the page draws is read once from the tagged PDF, as on main. The page
 * is a bordered 2 x 3 table drawn as text; the backend's answer is the table that docling-fast
 * returned for this page (each cell with its text and the box of that text). Each cell's text is
 * once in the structure tree (a marked-content sequence with an MCID) and once in the text that a
 * plain extractor reads. No backend and no network: a local mock server answers.
 */
class Review669_DrawnTextIsReadOnceTest {

    private static final float[] COLS = {72, 152, 212, 372};
    private static final float TOP = 600;
    private static final float ROW_H = 20;
    private static final float FONT_SIZE = 10;
    private static final String[][] CELLS = {
        {"Item", "Value", "Note"},
        {"Total area", "1,649", "see note"},
    };
    /** The box docling-fast gave each cell's text: left, top, right, bottom, from the top of the page. */
    private static final double[][][] CELL_BOXES = {
        {{76.0, 198.82, 95.45, 208.07}, {156.0, 198.82, 181.57, 208.07}, {281.44, 198.82, 302.56, 208.07}},
        {{76.0, 218.82, 121.02, 228.07}, {156.0, 218.82, 181.02, 228.07}, {272.82, 218.82, 311.18, 228.07}},
    };

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
    void eachCellOfADrawnTableIsReadOnceFromTheTaggedPdf() throws IOException {
        Path pdf = writeTable(tempDir.resolve("table.pdf"));
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder().body(tableResponse())
            .addHeader("Content-Type", "application/json").build());
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateTaggedPDF(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        DocumentProcessor.processFile(pdf.toString(), config);

        Path tagged = tempDir.resolve("table_tagged.pdf");
        String tree = String.join(" | ", taggedSequences(tagged));
        String text = rawText(tagged);
        for (String[] row : CELLS) {
            for (String cell : row) {
                assertEquals(1, count(tree, cell),
                    "\"" + cell + "\" is not read once from the structure tree; tagged sequences: " + tree);
                assertEquals(1, count(text, cell),
                    "\"" + cell + "\" is not read once by a plain text extractor:\n" + text);
            }
        }
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            n++;
        }
        return n;
    }

    private static Path writeTable(Path file) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float bottom = TOP - ROW_H * CELLS.length;
                for (int r = 0; r <= CELLS.length; r++) {
                    content.moveTo(COLS[0], TOP - r * ROW_H);
                    content.lineTo(COLS[COLS.length - 1], TOP - r * ROW_H);
                }
                for (float x : COLS) {
                    content.moveTo(x, TOP);
                    content.lineTo(x, bottom);
                }
                content.stroke();
                for (int r = 0; r < CELLS.length; r++) {
                    float baseline = TOP - r * ROW_H - 14;
                    for (int c = 0; c < CELLS[r].length; c++) {
                        String s = CELLS[r][c];
                        float width = font.getStringWidth(s) / 1000 * FONT_SIZE;
                        float x = c < 2 ? COLS[c] + 4 : (COLS[c] + COLS[c + 1]) / 2 - width / 2;
                        content.beginText();
                        content.setFont(font, FONT_SIZE);
                        content.newLineAtOffset(x, baseline);
                        content.showText(s);
                        content.endText();
                    }
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    private static String tableResponse() {
        StringBuilder cells = new StringBuilder();
        StringBuilder grid = new StringBuilder();
        for (int r = 0; r < CELLS.length; r++) {
            grid.append(r == 0 ? "[" : ",[");
            for (int c = 0; c < CELLS[r].length; c++) {
                double[] box = CELL_BOXES[r][c];
                String cell = "{\"bbox\": {\"l\": " + box[0] + ", \"t\": " + box[1] + ", \"r\": " + box[2] + ", \"b\": "
                    + box[3] + ", \"coord_origin\": \"TOPLEFT\"}, \"row_span\": 1, \"col_span\": 1,"
                    + " \"start_row_offset_idx\": " + r + ", \"end_row_offset_idx\": " + (r + 1) + ","
                    + " \"start_col_offset_idx\": " + c + ", \"end_col_offset_idx\": " + (c + 1) + ","
                    + " \"text\": \"" + CELLS[r][c] + "\", \"column_header\": " + (r == 0) + ", \"row_header\": false}";
                cells.append(cells.length() == 0 ? "" : ",").append(cell);
                grid.append(c == 0 ? "" : ",").append(cell);
            }
            grid.append("]");
        }
        return "{\"status\": \"success\", \"document\": {\"json_content\": {\"texts\": [], \"tables\": [{\"label\": \"table\","
            + " \"prov\": [{\"page_no\": 1, \"bbox\": {\"l\": 70.85, \"t\": 600.81, \"r\": 372.69, \"b\": 559.18,"
            + " \"coord_origin\": \"BOTTOMLEFT\"}}], \"data\": {\"num_rows\": 2, \"num_cols\": 3, \"table_cells\": ["
            + cells + "], \"grid\": [" + grid + "]}}],"
            + " \"pages\": {\"1\": {\"size\": {\"width\": 612.0, \"height\": 792.0}, \"page_no\": 1}}}},"
            + " \"processing_time\": 0.5, \"errors\": [], \"failed_pages\": []}";
    }

    private static List<String> taggedSequences(Path tagged) throws IOException {
        List<String> sequences = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(tagged.toFile())) {
            for (PDPage page : document.getPages()) {
                PDFMarkedContentExtractor extractor = new PDFMarkedContentExtractor();
                extractor.processPage(page);
                for (PDMarkedContent content : extractor.getMarkedContents()) {
                    collect(content, false, sequences);
                }
            }
        }
        return sequences;
    }

    private static void collect(PDMarkedContent content, boolean inArtifact, List<String> sequences) {
        boolean artifact = inArtifact || "Artifact".equals(content.getTag());
        StringBuilder text = new StringBuilder();
        for (Object item : content.getContents()) {
            if (item instanceof TextPosition) {
                text.append(((TextPosition) item).getUnicode());
            } else if (item instanceof PDMarkedContent) {
                collect((PDMarkedContent) item, artifact, sequences);
            }
        }
        if (!artifact && content.getMCID() >= 0 && text.length() > 0) {
            sequences.add(text.toString());
        }
    }

    private static String rawText(Path tagged) throws IOException {
        try (PDDocument document = Loader.loadPDF(tagged.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(false);
            return stripper.getText(document);
        }
    }
}

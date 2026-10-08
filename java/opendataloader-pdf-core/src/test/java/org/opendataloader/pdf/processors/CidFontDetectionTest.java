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
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Integration test for CID font extraction failure detection.
 *
 * Tests the full pipeline: PDF parsing -> ContentFilterProcessor ->
 * measurement -> StaticLayoutContainers storage -> warning log.
 *
 * <p>The test builds a synthetic PDF: a Type0 (CID) font with the Identity-H encoding
 * and no ToUnicode map, so the text has no Unicode mapping. The font is LiberationSans
 * (SIL OFL), which PDFBox ships. The sample stays in
 * {@code target/test-samples/cid-font-no-tounicode/}. Only the two integration
 * tests build it, so a problem with the sample does not stop the unit tests.
 */
public class CidFontDetectionTest {

    private static final String LIBERATION_SANS = "/org/apache/pdfbox/resources/ttf/LiberationSans-Regular.ttf";

    private static Path cidPdfPath;

    /** Builds the sample on the first call and returns its path. */
    private static synchronized Path cidSample() throws IOException {
        if (cidPdfPath != null) {
            return cidPdfPath;
        }
        Path pdf = TestSamples.emptyDirectory("cid-font-no-tounicode").resolve("cid-font-no-tounicode.pdf");
        writeCidSample(pdf);
        assertSampleHasTheTrigger(pdf);
        cidPdfPath = pdf;
        return cidPdfPath;
    }

    private static void writeCidSample(Path pdf) throws IOException {
        try (PDDocument document = new PDDocument();
             InputStream ttf = PDDocument.class.getResourceAsStream(LIBERATION_SANS)) {
            Assertions.assertNotNull(ttf, "PDFBox does not ship " + LIBERATION_SANS);
            PDType0Font font = PDType0Font.load(document, ttf, false);   // Identity-H, full font
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(72, 700);
                for (int line = 1; line <= 5; line++) {
                    content.showText("Line " + line + " of a synthetic sample with a CID font and no ToUnicode map.");
                    content.newLineAtOffset(0, -18);
                }
                content.endText();
            }
            font.getCOSObject().removeItem(COSName.TO_UNICODE);   // the trigger
            document.save(pdf.toFile());
        }
    }

    /** Checks the saved file: one Type0 (CID) font without a ToUnicode map. */
    private static void assertSampleHasTheTrigger(Path pdf) throws IOException {
        try (PDDocument saved = Loader.loadPDF(pdf.toFile())) {
            PDPage page = saved.getPage(0);
            List<PDFont> fonts = new ArrayList<>();
            for (COSName name : page.getResources().getFontNames()) {
                fonts.add(page.getResources().getFont(name));
            }
            Assertions.assertEquals(1, fonts.size(), "The sample must have exactly one font");
            Assertions.assertTrue(fonts.get(0) instanceof PDType0Font, "The sample font must be a Type0 (CID) font");
            Assertions.assertFalse(fonts.get(0).getCOSObject().containsKey(COSName.TO_UNICODE),
                "The sample font must not have a ToUnicode map");
        }
    }

    /**
     * Closes the document that {@code preprocessing} opened. processFile does this in
     * closePdfResources; without it, veraPDF keeps the temporary file of the decoded
     * object stream ({@code tmp_pdf_file*.pdf}) after the JVM exits.
     */
    @AfterEach
    public void closeDocument() {
        if (StaticResources.getDocument() != null) {
            StaticResources.getDocument().close();
            StaticResources.setDocument(null);
        }
    }

    @Test
    public void testCidPdfHighReplacementRatioDetected() throws IOException {
        String pdfPath = cidSample().toString();
        Config config = new Config();

        DocumentProcessor.preprocessing(pdfPath, config);
        StaticLayoutContainers.clearContainers();

        int numPages = StaticContainers.getDocument().getNumberOfPages();
        Assertions.assertTrue(numPages > 0, "PDF should have at least 1 page");

        // Process page 0 through ContentFilterProcessor
        List<IObject> filteredContents = ContentFilterProcessor.getFilteredContents(
            pdfPath,
            StaticContainers.getDocument().getArtifacts(0),
            0,
            config
        );

        // Verify ratio was stored
        double ratio = StaticLayoutContainers.getReplacementCharRatio(0);
        Assertions.assertTrue(ratio >= 0.3,
            "CID font PDF should have >= 30% replacement characters, got "
            + String.format("%.1f%%", ratio * 100));
    }

    @Test
    public void testCidPdfWarningLogEmitted() throws IOException {
        // Capture warning logs
        Logger logger = Logger.getLogger(ContentFilterProcessor.class.getCanonicalName());
        List<String> warnings = new ArrayList<>();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel() == Level.WARNING) {
                    warnings.add(r.getMessage());
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);

        try {
            String pdfPath = cidSample().toString();
            Config config = new Config();

            DocumentProcessor.preprocessing(pdfPath, config);
            StaticLayoutContainers.clearContainers();

            ContentFilterProcessor.getFilteredContents(
                pdfPath,
                StaticContainers.getDocument().getArtifacts(0),
                0,
                config
            );

            boolean hasReplacementWarning = warnings.stream()
                .anyMatch(w -> w.contains("replacement characters")
                    && w.contains("--hybrid docling-fast")
                    && !w.contains("--hybrid-mode for OCR fallback"));
            Assertions.assertTrue(hasReplacementWarning,
                "Expected WARNING log about replacement characters with actionable hybrid guidance");
        } finally {
            logger.removeHandler(handler);
        }
    }

    /**
     * Unit-level boundary tests (no PDF fixture needed).
     */
    @Test
    public void testBoundaryBelowThreshold29percent() {
        // 29 replacement chars out of 100 = 0.29 (below threshold)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 29; i++) sb.append('\uFFFD');
        for (int i = 0; i < 71; i++) sb.append('A');

        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 500.0, 20.0),
            sb.toString(), 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertTrue(ratio < 0.3,
            "29% should be below threshold, got " + ratio);
    }

    @Test
    public void testBoundaryAtThreshold30percent() {
        // 30 replacement chars out of 100 = 0.30 (at threshold)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append('\uFFFD');
        for (int i = 0; i < 70; i++) sb.append('A');

        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(1, 10.0, 10.0, 500.0, 20.0),
            sb.toString(), 10, 10.0));

        double ratio = TextProcessor.measureReplacementCharRatio(contents);
        Assertions.assertTrue(ratio >= 0.3,
            "30% should be at threshold, got " + ratio);
    }
}

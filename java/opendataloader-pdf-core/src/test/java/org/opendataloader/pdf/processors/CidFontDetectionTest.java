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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
 */
public class CidFontDetectionTest {

    private static final Path CID_PDF_PATH = Paths.get(
        "src/test/resources/cid-font-no-tounicode.pdf");

    private static boolean pdfAvailable = false;

    @BeforeAll
    static void checkFixture() {
        pdfAvailable = Files.exists(CID_PDF_PATH) && Files.isRegularFile(CID_PDF_PATH);
        if (!pdfAvailable) {
            System.out.println("CID font test PDF not found: " + CID_PDF_PATH.toAbsolutePath());
            System.out.println("Skipping integration tests. Generate fixture first.");
        }
    }

    @Test
    public void testCidPdfHighReplacementRatioDetected() throws IOException {
        Assumptions.assumeTrue(pdfAvailable, "CID font test PDF not available");

        String pdfPath = CID_PDF_PATH.toAbsolutePath().toString();
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
        Assumptions.assumeTrue(pdfAvailable, "CID font test PDF not available");

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
            String pdfPath = CID_PDF_PATH.toAbsolutePath().toString();
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

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
package org.opendataloader.pdf.hybrid;

import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageSignals;
import org.opendataloader.pdf.processors.ContentFilterProcessor;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Triage of image-only scanned pages in hybrid auto mode (#619).
 *
 * <p>Each page goes through the same preprocessing, content filtering and
 * {@link TriageProcessor#classifyPage} calls as {@code HybridDocumentProcessor}.
 * A scan has no text layer for the Java path to extract, so it must reach the
 * backend whatever its orientation, even though its image is not wide enough
 * for the table-image signal.
 */
class ScannedPageTriageIntegrationTest {

    private static final String CHINESE_SCAN_PDF = "../../samples/pdf/chinese_scan.pdf";

    @TempDir
    Path tempDir;

    @AfterEach
    void closeDocument() throws IOException {
        if (StaticResources.getDocument() != null) {
            StaticResources.getDocument().close();
            StaticResources.setDocument(null);
        }
    }

    @Test
    void portraitA4ScanRoutesToBackend() throws IOException {
        assertRoutedAsScan(triage(ScannedPagePdf.of(PDRectangle.A4)
                .writeTo(tempDir.resolve("portrait-a4.pdf"))));
    }

    @Test
    void landscapeA4ScanRoutesToBackend() throws IOException {
        assertRoutedAsScan(triage(ScannedPagePdf.of(ScannedPagePdf.landscape(PDRectangle.A4))
                .writeTo(tempDir.resolve("landscape-a4.pdf"))));
    }

    @Test
    void landscapeLetterJpegScanRoutesToBackend() throws IOException {
        assertRoutedAsScan(triage(ScannedPagePdf.of(ScannedPagePdf.landscape(PDRectangle.LETTER))
                .encodedAsJpeg()
                .writeTo(tempDir.resolve("landscape-letter.pdf"))));
    }

    @Test
    void rotatedPortraitScanRoutesToBackend() throws IOException {
        assertRoutedAsScan(triage(ScannedPagePdf.of(PDRectangle.A4)
                .rotatedBy(90)
                .writeTo(tempDir.resolve("rotated-portrait.pdf"))));
    }

    @Test
    void scanWithScannerAppStampRoutesToBackend() throws IOException {
        TriageResult result = triage(ScannedPagePdf.of(PDRectangle.A4)
                .stampedWith("Scanned with CamScanner")
                .writeTo(tempDir.resolve("stamped.pdf")));

        assertRoutedAsScan(result);
        assertEquals(3, result.getSignals().getNonWhitespaceTextCount());
    }

    @Test
    void chineseScanSampleRoutesToBackend() throws IOException {
        File sample = new File(CHINESE_SCAN_PDF);
        assumeTrue(sample.exists(), "Sample PDF not found at " + sample.getAbsolutePath());

        assertRoutedAsScan(triage(sample.toPath()));
    }

    @Test
    void textlessImageCoveringLessThanScanThresholdStaysOnJava() throws IOException {
        TriageResult result = triage(ScannedPagePdf.of(PDRectangle.A4)
                .imageCovering(0.3)
                .writeTo(tempDir.resolve("small-image.pdf")));

        assertEquals(0.3, result.getSignals().getLargeImageRatio(), 0.01);
        assertEquals(TriageDecision.JAVA, result.getDecision());
        assertFalse(result.getSignals().isLikelyScannedPage());
    }

    @Test
    void fullPageImageWithSixWordsStaysOnJava() throws IOException {
        TriageResult result = triage(ScannedPagePdf.of(PDRectangle.A4)
                .stampedWith("one two three four five six")
                .writeTo(tempDir.resolve("six-words.pdf")));

        assertEquals(6, result.getSignals().getNonWhitespaceTextCount());
        assertEquals(TriageDecision.JAVA, result.getDecision());
        assertFalse(result.getSignals().isLikelyScannedPage());
    }

    private static TriageResult triage(Path pdf) throws IOException {
        Config config = new Config();
        config.setHybrid("docling-fast");
        DocumentProcessor.preprocessing(pdf.toString(), config);
        List<IObject> filteredContents = ContentFilterProcessor.getFilteredContents(
                pdf.toString(), StaticContainers.getDocument().getArtifacts(0), 0, config);
        return TriageProcessor.classifyPage(filteredContents, 0, config.getHybridConfig());
    }

    private static void assertRoutedAsScan(TriageResult result) {
        TriageSignals signals = result.getSignals();
        assertEquals(TriageDecision.BACKEND, result.getDecision(), signals.toString());
        assertTrue(signals.getLargeImageRatio() >= 0.99, signals.toString());
        assertFalse(signals.hasLargeImage(), "page-shaped images are not wide enough for the table-image signal");
        assertTrue(signals.isLikelyScannedPage(), signals.toString());
    }
}

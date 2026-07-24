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
package org.opendataloader.pdf;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for full-page image-only scan routing under {@code --hybrid-mode auto}.
 *
 * <p>Wires a real PDF through {@link DocumentProcessor#processFile} with a mocked hybrid
 * backend and observes whether the page reaches the backend (a {@code /v1/convert/file}
 * request arrives) or stays on the Java path. A scanned, text-layer-free page must route to
 * the backend regardless of aspect ratio; a text-only page must stay on Java.
 *
 * <p>The backend answers {@code 500} on convert so the {@code --hybrid-fallback} path
 * completes the run — the test only cares about the <em>routing decision</em>, not the
 * backend output, so a valid DoclingDocument response is unnecessary.
 */
class TriageScanRoutingIntegrationTest {

    private static final String PORTRAIT_SCAN_PDF = "../../samples/pdf/chinese_scan.pdf";
    private static final String LANDSCAPE_SCAN_PDF = "../../samples/pdf/landscape_scan.pdf";
    private static final String TEXT_PDF = "../../samples/pdf/lorem.pdf";

    @TempDir
    Path tempDir;

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        // HybridClientFactory keeps a process-wide cache keyed by backend name; clear it so
        // an earlier test's docling-fast client doesn't shadow our MockWebServer URL.
        HybridClientFactory.shutdown();
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            if (server != null) {
                server.close();
            }
        } finally {
            OpenDataLoaderPDF.shutdown();
        }
    }

    private Config hybridAutoConfig() {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        // With fallback on, a 500 on convert is recovered by the Java path so the run
        // completes; we still observe that the page was routed to the backend.
        config.getHybridConfig().setFallbackToJava(true);
        return config;
    }

    /**
     * Runs a PDF through the auto-triage hybrid path and returns whether any page reached the
     * backend (a convert request was received).
     */
    private boolean routedToBackend(String pdfPath) throws InterruptedException {
        // One health probe (Phase 0) + one convert per backend chunk. A 1-page scan needs at
        // most these two; enqueue them in order (FIFO).
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder().code(500).body("boom").build());

        File pdf = new File(pdfPath);
        assertTrue(pdf.exists(), "fixture missing: " + pdf.getAbsolutePath());

        Config config = hybridAutoConfig();
        assertEquals(HybridConfig.MODE_AUTO, config.getHybridConfig().getMode(),
            "auto is the default triage mode for this scenario");

        assertDoesNotThrow(() -> DocumentProcessor.processFile(pdf.getAbsolutePath(), config),
            "run must complete (fallback recovers the mocked backend 500)");

        int requestCount = server.getRequestCount();
        boolean sawConvert = false;
        for (int i = 0; i < requestCount; i++) {
            RecordedRequest req = server.takeRequest(2, TimeUnit.SECONDS);
            if (req != null && req.getTarget() != null
                    && req.getTarget().contains("/v1/convert/file")) {
                sawConvert = true;
            }
        }
        return sawConvert;
    }

    @Test
    void portraitImageOnlyScan_autoMode_routesToBackend() throws Exception {
        assertTrue(routedToBackend(PORTRAIT_SCAN_PDF),
            "portrait full-page scan must triage to BACKEND (convert request expected)");
    }

    @Test
    void landscapeImageOnlyScan_autoMode_routesToBackend() throws Exception {
        assertTrue(routedToBackend(LANDSCAPE_SCAN_PDF),
            "landscape full-page scan must triage to BACKEND (aspect gate must not exclude it)");
    }

    @Test
    void textOnlyPage_autoMode_staysOnJava() throws Exception {
        assertFalse(routedToBackend(TEXT_PDF),
            "text-only page must stay on the Java path (no backend convert request)");
    }
}

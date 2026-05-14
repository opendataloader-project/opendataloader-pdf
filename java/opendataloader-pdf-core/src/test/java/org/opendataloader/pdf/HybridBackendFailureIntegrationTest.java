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

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end test for the fail-fast contract on hybrid backend failures.
 *
 * <p>Wires a real PDF through {@link DocumentProcessor#processFile} with a mocked
 * hybrid backend, verifying that the failure surfaces as an {@link IOException}
 * all the way to the caller — guarding the call site at which
 * {@code HybridDocumentProcessor.processDocument} invokes the fail-fast helper.
 * The {@code HybridDocumentProcessorTest} suite covers the helper in isolation;
 * this test covers the wiring.
 */
class HybridBackendFailureIntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/lorem.pdf";

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private File samplePdf;

    @BeforeEach
    void setUp() throws IOException {
        // HybridClientFactory keeps a process-wide cache keyed by backend name,
        // so any earlier test that touched docling-fast leaves a client wired
        // to its own URL. Clear it before standing up our MockWebServer URL.
        HybridClientFactory.shutdown();

        server = new MockWebServer();
        server.start();
        samplePdf = new File(SAMPLE_PDF);
        assertTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            // Guard against partial @BeforeEach failure leaving server unassigned
            // (HybridClientFactory.shutdown() or `new MockWebServer()` could throw
            // before the assignment). JUnit 5 still calls tearDown, so the guard
            // prevents an NPE from masking the real setup failure.
            if (server != null) {
                server.shutdown();
            }
        } finally {
            // Drop the cached HybridClient holding the mock server's URL so other
            // tests don't accidentally reuse it. Runs even if server.shutdown()
            // throws, so the cache never leaks the mock URL into other tests.
            OpenDataLoaderPDF.shutdown();
        }
    }

    @Test
    void backendPartialSuccessFailsFastWhenFallbackDisabled() {
        // /health probe (Phase 0 checkAvailability)
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        // /v1/convert/file response — the single page of lorem.pdf is marked
        // failed. Listing just the requested pages keeps the test's intent
        // explicit instead of relying on the processor's intersection logic.
        String partialSuccess = "{"
            + "\"status\": \"partial_success\","
            + "\"document\": {\"json_content\": {\"pages\": {}}},"
            + "\"processing_time\": 0.5,"
            + "\"errors\": [\"Unknown page: pipeline terminated early\"],"
            + "\"failed_pages\": [1]"
            + "}";
        server.enqueue(new MockResponse()
            .setBody(partialSuccess)
            .addHeader("Content-Type", "application/json"));

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        // Full mode routes every page to the backend, so the mocked response
        // covers all of them without depending on triage decisions.
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        // setFallbackToJava is false by default; assert to document the precondition.
        assertFalse(config.getHybridConfig().isFallbackToJava(),
            "fallback should be disabled by default for this scenario");

        IOException ex = assertThrows(IOException.class,
            () -> DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config));

        assertTrue(ex.getMessage().contains("page(s) with fallback disabled"),
            "exception should mention fallback context: " + ex.getMessage());
    }
}

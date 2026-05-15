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
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    /**
     * PDFDLOSP-21 case 1 (P0): backend is entirely unreachable and
     * {@code --hybrid-fallback} is ON. {@code processDocument} must catch the
     * health-check IOException and route every page through the Java path so the
     * run completes normally and writes a valid JSON output.
     */
    @Test
    void serverAbsent_withFallback_processesWithJava() throws IOException {
        // Reserve a port and immediately close the socket so connection attempts
        // hit a closed port (most reliable connection-refused simulation that
        // does not depend on platform DNS quirks).
        int closedPort = reserveClosedPort();
        String unreachableUrl = "http://127.0.0.1:" + closedPort;

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(unreachableUrl);
        config.getHybridConfig().setFallbackToJava(true);

        assertDoesNotThrow(
            () -> DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config),
            "with --hybrid-fallback the run must succeed when the backend is absent");

        Path jsonOutput = tempDir.resolve("lorem.json");
        assertTrue(Files.exists(jsonOutput),
            "Java-fallback path should still produce JSON output at " + jsonOutput);
        assertTrue(Files.size(jsonOutput) > 0L,
            "JSON output must not be empty when fallback ran successfully");
    }

    /**
     * PDFDLOSP-21 case 2 (P0): backend is entirely unreachable and
     * {@code --hybrid-fallback} is OFF. The run must fail fast with an
     * {@link IOException} whose message points the user at the new
     * {@code --hybrid-fallback} escape hatch (DoclingFastServerClient patch).
     */
    @Test
    void serverAbsent_withoutFallback_failsFastWithHelpfulMessage() throws IOException {
        int closedPort = reserveClosedPort();
        String unreachableUrl = "http://127.0.0.1:" + closedPort;

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(unreachableUrl);
        // Document the precondition: fallback stays off, so health-check failure
        // must propagate.
        assertFalse(config.getHybridConfig().isFallbackToJava(),
            "fallback must be disabled for the fail-fast scenario");

        IOException ex = assertThrows(IOException.class,
            () -> DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config));

        String msg = ex.getMessage() == null ? "" : ex.getMessage();
        assertTrue(msg.contains("Hybrid server is not available"),
            "exception should identify the health-check failure: " + msg);
        assertTrue(msg.contains("--hybrid-fallback"),
            "exception should point user to --hybrid-fallback escape hatch: " + msg);
    }

    /**
     * PDFDLOSP-21 case 3 (P0): same as case 1 but with an aggressive 1 ms
     * timeout. The fallback path must be triggered by any IOException raised
     * from {@code checkAvailability()} — connection refused, connect timeout,
     * read timeout — so this guards H-6/H-7 equivalence: timeout-driven failures
     * are recovered just like connection-refused failures.
     */
    @Test
    void serverAbsent_withFallbackAndTinyTimeout_processesWithJava() throws IOException {
        int closedPort = reserveClosedPort();
        String unreachableUrl = "http://127.0.0.1:" + closedPort;

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(unreachableUrl);
        config.getHybridConfig().setFallbackToJava(true);
        config.getHybridConfig().setTimeoutMs(1);

        assertDoesNotThrow(
            () -> DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config),
            "fallback must handle timeout-driven health-check failures too");

        Path jsonOutput = tempDir.resolve("lorem.json");
        assertTrue(Files.exists(jsonOutput),
            "Java-fallback path should still produce JSON output at " + jsonOutput);
        assertTrue(Files.size(jsonOutput) > 0L,
            "JSON output must not be empty when fallback ran with tiny timeout");
    }

    /**
     * Binds an ephemeral port and releases it. Subsequent connect() attempts to
     * the returned port number are extremely likely to be refused, giving a
     * deterministic "backend unreachable" condition without relying on
     * unrouteable IPs (which can hang on some hosts).
     */
    private static int reserveClosedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}

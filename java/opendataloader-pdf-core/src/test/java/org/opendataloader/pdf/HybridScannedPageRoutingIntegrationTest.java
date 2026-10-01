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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.ScannedPagePdf;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that hybrid auto mode, the default, sends an image-only
 * scanned page to the backend (#619).
 *
 * <p>The Java path has no text to extract from a scan, so the output is empty
 * unless the backend receives the page.
 */
class HybridScannedPageRoutingIntegrationTest {

    private static final String BACKEND_TEXT = "Text recognised by the backend";

    @TempDir
    Path tempDir;

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        // HybridClientFactory caches clients per backend name, so a client wired
        // to another test's URL would otherwise receive our requests.
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

    @Test
    void autoModeSendsPortraitScanToBackend() throws Exception {
        assertScanIsConvertedByBackend(PDRectangle.A4);
    }

    @Test
    void autoModeSendsLandscapeScanToBackend() throws Exception {
        assertScanIsConvertedByBackend(ScannedPagePdf.landscape(PDRectangle.A4));
    }

    private void assertScanIsConvertedByBackend(PDRectangle pageSize) throws Exception {
        Path pdf = ScannedPagePdf.of(pageSize).writeTo(tempDir.resolve("scan.pdf"));
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder()
            .body(convertResponse(pageSize))
            .addHeader("Content-Type", "application/json")
            .build());

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        assertFalse(config.getHybridConfig().isFullMode(), "auto mode is the default under test");

        DocumentProcessor.processFile(pdf.toString(), config);

        assertEquals(2, server.getRequestCount(), "expected the health check and one convert request");
        assertEquals("/health", server.takeRequest().getTarget());
        RecordedRequest convert = server.takeRequest();
        assertEquals("/v1/convert/file", convert.getTarget());
        assertEquals("1-1", formField(convert.getBody().utf8(), "page_ranges"));
        assertTrue(Files.readString(tempDir.resolve("scan.json")).contains(BACKEND_TEXT),
            "the backend's text should reach the output");
    }

    private static String convertResponse(PDRectangle pageSize) {
        return "{"
            + "\"status\": \"success\","
            + "\"document\": {\"json_content\": {"
            + "\"texts\": [{\"label\": \"text\", \"text\": \"" + BACKEND_TEXT + "\","
            + "\"prov\": [{\"page_no\": 1, \"bbox\": {\"l\": 72, \"t\": " + (pageSize.getHeight() - 72)
            + ", \"r\": 400, \"b\": " + (pageSize.getHeight() - 90) + ", \"coord_origin\": \"BOTTOMLEFT\"}}]}],"
            + "\"pages\": {\"1\": {}}"
            + "}},"
            + "\"processing_time\": 0.5,"
            + "\"errors\": [],"
            + "\"failed_pages\": []"
            + "}";
    }

    private static String formField(String multipartBody, String name) {
        String header = "name=\"" + name + "\"";
        int headerAt = multipartBody.indexOf(header);
        assertTrue(headerAt >= 0, "multipart body has no " + name + " field");
        int separatorAt = multipartBody.indexOf("\r\n\r\n", headerAt);
        assertTrue(separatorAt >= 0, "multipart " + name + " field has no header/value separator");
        int valueStart = separatorAt + 4;
        return multipartBody.substring(valueStart, multipartBody.indexOf("\r\n", valueStart));
    }
}

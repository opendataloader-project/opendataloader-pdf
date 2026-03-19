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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for hybrid page filtering with docling backends.
 */
class HybridPageRangeIntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/1901.03003.pdf";
    private static final String OUTPUT_BASENAME = "1901.03003";

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private File samplePdf;

    @BeforeEach
    void setUp() throws IOException {
        HybridClientFactory.shutdown();
        server = new MockWebServer();
        server.start();
        samplePdf = new File(SAMPLE_PDF);
        assumeTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    @AfterEach
    void tearDown() throws IOException {
        HybridClientFactory.shutdown();
        server.shutdown();
    }

    @Test
    void testHybridFullModeSplitsSparsePageSelectionIntoContiguousBackendRanges() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        server.enqueue(new MockResponse()
            .setBody(buildTextResponse(1, "page one from backend"))
            .addHeader("Content-Type", "application/json"));
        server.enqueue(new MockResponse()
            .setBody(buildTextResponse(3, "page three from backend"))
            .addHeader("Content-Type", "application/json"));

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setImageOutput(Config.IMAGE_OUTPUT_OFF);
        config.setHybrid(Config.HYBRID_DOCLING_FAST);
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(getBaseUrl());
        config.setPages("1,3");

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        JsonNode root = new ObjectMapper().readTree(Files.newInputStream(jsonOutput));
        assertEquals(Set.of(1, 3), getPageNumbersFromKids(root),
            "Only selected pages should be populated from backend responses");

        assertEquals(3, server.getRequestCount(), "Expected one health check and one convert call per contiguous range");

        RecordedRequest healthRequest = server.takeRequest();
        RecordedRequest firstConvertRequest = server.takeRequest();
        RecordedRequest secondConvertRequest = server.takeRequest();

        assertNotNull(healthRequest);
        assertNotNull(firstConvertRequest);
        assertNotNull(secondConvertRequest);
        assertEquals("/health", healthRequest.getPath());
        assertTrue(firstConvertRequest.getBody().readUtf8().contains("1-1"));
        assertTrue(secondConvertRequest.getBody().readUtf8().contains("3-3"));
    }

    private String getBaseUrl() {
        String baseUrl = server.url("").toString();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String buildTextResponse(int pageNo, String text) {
        return "{"
            + "\"status\":\"success\","
            + "\"document\":{"
            + "\"json_content\":{"
            + "\"pages\":{\"" + pageNo + "\":{}},"
            + "\"texts\":[{"
            + "\"label\":\"text\","
            + "\"text\":\"" + text + "\","
            + "\"prov\":[{"
            + "\"page_no\":" + pageNo + ","
            + "\"bbox\":{"
            + "\"l\":72.0,"
            + "\"t\":720.0,"
            + "\"r\":300.0,"
            + "\"b\":740.0,"
            + "\"coord_origin\":\"BOTTOMLEFT\""
            + "}"
            + "}]"
            + "}]"
            + "}"
            + "}"
            + "}";
    }

    private Set<Integer> getPageNumbersFromKids(JsonNode root) {
        Set<Integer> pageNumbers = new HashSet<>();
        JsonNode kids = root.get("kids");
        if (kids != null && kids.isArray()) {
            for (JsonNode kid : kids) {
                JsonNode pageNumber = kid.get("page number");
                if (pageNumber != null && pageNumber.isInt()) {
                    pageNumbers.add(pageNumber.asInt());
                }
            }
        }
        return pageNumbers;
    }
}

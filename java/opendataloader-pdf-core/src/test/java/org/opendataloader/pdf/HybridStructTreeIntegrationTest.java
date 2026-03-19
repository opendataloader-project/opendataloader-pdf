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
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration tests for hybrid full mode with tagged PDFs.
 */
class HybridStructTreeIntegrationTest {

    private static final String TAGGED_PDF = "../../samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-04_Presentation.pdf";
    private static final String OUTPUT_BASENAME = "PDFUA-Ref-2-04_Presentation";
    private static final String FORMULA_LATEX = "\\frac{a}{b}";

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private File taggedPdf;

    @BeforeEach
    void setUp() throws IOException {
        HybridClientFactory.shutdown();
        server = new MockWebServer();
        server.start();
        taggedPdf = new File(TAGGED_PDF);
        assumeTrue(taggedPdf.exists(), "Tagged PDF not found at " + taggedPdf.getAbsolutePath());
    }

    @AfterEach
    void tearDown() throws IOException {
        HybridClientFactory.shutdown();
        server.shutdown();
    }

    @Test
    void testHybridFullModeUsesBackendWhenStructTreeIsEnabled() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));
        server.enqueue(new MockResponse()
            .setBody(buildConvertResponse())
            .addHeader("Content-Type", "application/json"));

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.setImageOutput(Config.IMAGE_OUTPUT_OFF);
        config.setUseStructTree(true);
        config.setHybrid(Config.HYBRID_DOCLING_FAST);
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(getBaseUrl());

        DocumentProcessor.processFile(taggedPdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(OUTPUT_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        JsonNode root = new ObjectMapper().readTree(Files.newInputStream(jsonOutput));
        assertTrue(containsFormula(root, FORMULA_LATEX),
            "Hybrid full mode should preserve backend formula output even when struct-tree is enabled");
        assertEquals(2, server.getRequestCount(), "Expected a health check and one backend conversion request");

        RecordedRequest healthRequest = server.takeRequest();
        RecordedRequest convertRequest = server.takeRequest();

        assertNotNull(healthRequest);
        assertNotNull(convertRequest);
        assertEquals("/health", healthRequest.getPath());
        assertTrue(convertRequest.getPath().startsWith("/v1/convert/file"));
    }

    private String getBaseUrl() {
        String baseUrl = server.url("").toString();
        if (baseUrl.endsWith("/")) {
            return baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }

    private String buildConvertResponse() {
        return "{"
            + "\"status\":\"success\","
            + "\"document\":{"
            + "\"json_content\":{"
            + "\"pages\":{\"1\":{}},"
            + "\"texts\":[{"
            + "\"label\":\"formula\","
            + "\"text\":\"\\\\frac{a}{b}\","
            + "\"prov\":[{"
            + "\"page_no\":1,"
            + "\"bbox\":{"
            + "\"l\":226.2,"
            + "\"t\":144.7,"
            + "\"r\":377.1,"
            + "\"b\":168.7,"
            + "\"coord_origin\":\"BOTTOMLEFT\""
            + "}"
            + "}]"
            + "}]"
            + "}"
            + "}"
            + "}";
    }

    private boolean containsFormula(JsonNode node, String latex) {
        JsonNode kids = node.get("kids");
        if (kids == null || !kids.isArray()) {
            return false;
        }
        for (Iterator<JsonNode> iterator = kids.elements(); iterator.hasNext();) {
            JsonNode kid = iterator.next();
            JsonNode type = kid.get("type");
            JsonNode content = kid.get("content");
            if (type != null && content != null
                && "formula".equals(type.asText())
                && latex.equals(content.asText())) {
                return true;
            }
        }
        return false;
    }
}

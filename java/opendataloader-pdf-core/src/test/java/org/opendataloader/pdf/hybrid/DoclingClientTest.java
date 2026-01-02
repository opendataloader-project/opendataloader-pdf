/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DoclingClient.
 *
 * <p>These tests use MockWebServer to verify the client behavior
 * without requiring a running docling-serve instance.
 */
class DoclingClientTest {

    private static final String SAMPLE_RESPONSE = "{\n" +
        "    \"document\": {\n" +
        "        \"filename\": \"test.pdf\",\n" +
        "        \"md_content\": \"# Test Document\\n\\nThis is a test.\",\n" +
        "        \"json_content\": {\n" +
        "            \"schema_name\": \"DoclingDocument\",\n" +
        "            \"version\": \"1.8.0\",\n" +
        "            \"name\": \"test\",\n" +
        "            \"pages\": {\n" +
        "                \"1\": {\n" +
        "                    \"size\": {\"width\": 612.0, \"height\": 792.0}\n" +
        "                }\n" +
        "            },\n" +
        "            \"texts\": [\n" +
        "                {\"self_ref\": \"#/texts/0\", \"label\": \"text\", \"text\": \"This is a test.\"}\n" +
        "            ]\n" +
        "        }\n" +
        "    },\n" +
        "    \"status\": \"success\",\n" +
        "    \"errors\": [],\n" +
        "    \"processing_time\": 1.5\n" +
        "}";

    private static final String ERROR_RESPONSE = "{\n" +
        "    \"status\": \"failure\",\n" +
        "    \"errors\": [{\"error_type\": \"ProcessingError\", \"message\": \"Failed to process PDF\"}],\n" +
        "    \"processing_time\": 0.5\n" +
        "}";

    private MockWebServer mockServer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockServer.shutdown();
    }

    @Test
    void testHybridRequestFactoryMethods() {
        byte[] pdfBytes = new byte[]{1, 2, 3};

        // Test allPages factory method
        HybridClient.HybridRequest allPagesRequest = HybridClient.HybridRequest.allPages(pdfBytes);
        assertArrayEquals(pdfBytes, allPagesRequest.getPdfBytes());
        assertTrue(allPagesRequest.getPageNumbers().isEmpty());
        assertTrue(allPagesRequest.isDoTableStructure());
        assertFalse(allPagesRequest.isDoOcr());

        // Test forPages factory method
        Set<Integer> pages = Set.of(1, 2, 3);
        HybridClient.HybridRequest forPagesRequest = HybridClient.HybridRequest.forPages(pdfBytes, pages);
        assertArrayEquals(pdfBytes, forPagesRequest.getPdfBytes());
        assertEquals(pages, forPagesRequest.getPageNumbers());
        assertTrue(forPagesRequest.isDoTableStructure());
        assertFalse(forPagesRequest.isDoOcr());
    }

    @Test
    void testHybridRequestClass() {
        byte[] pdfBytes = new byte[]{1, 2, 3, 4, 5};
        Set<Integer> pageNumbers = Set.of(1, 3, 5);

        HybridClient.HybridRequest request = new HybridClient.HybridRequest(
            pdfBytes, pageNumbers, true, false
        );

        assertArrayEquals(pdfBytes, request.getPdfBytes());
        assertEquals(pageNumbers, request.getPageNumbers());
        assertTrue(request.isDoTableStructure());
        assertFalse(request.isDoOcr());
    }

    @Test
    void testHybridResponseClass() {
        String markdown = "# Test";
        JsonNode json = objectMapper.createObjectNode().put("test", "value");

        HybridClient.HybridResponse response = new HybridClient.HybridResponse(
            markdown, json, Collections.emptyMap()
        );

        assertEquals(markdown, response.getMarkdown());
        assertEquals(json, response.getJson());
        assertTrue(response.getPageContents().isEmpty());
    }

    @Test
    void testHybridResponseEmpty() {
        HybridClient.HybridResponse empty = HybridClient.HybridResponse.empty();

        assertEquals("", empty.getMarkdown());
        assertNull(empty.getJson());
        assertTrue(empty.getPageContents().isEmpty());
    }

    @Test
    void testConvertSuccessfulResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(SAMPLE_RESPONSE)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        DoclingClient client = createClient();

        HybridClient.HybridRequest request = HybridClient.HybridRequest.allPages(
            new byte[]{0x25, 0x50, 0x44, 0x46} // %PDF
        );

        HybridClient.HybridResponse response = client.convert(request);

        assertNotNull(response);
        assertEquals("# Test Document\n\nThis is a test.", response.getMarkdown());
        assertNotNull(response.getJson());
        assertEquals("DoclingDocument", response.getJson().get("schema_name").asText());
        assertFalse(response.getPageContents().isEmpty());
        assertTrue(response.getPageContents().containsKey(1));

        client.shutdown();
    }

    @Test
    void testConvertErrorResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(ERROR_RESPONSE)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        DoclingClient client = createClient();

        HybridClient.HybridRequest request = HybridClient.HybridRequest.allPages(
            new byte[]{0x25, 0x50, 0x44, 0x46}
        );

        IOException exception = assertThrows(IOException.class, () -> client.convert(request));
        assertTrue(exception.getMessage().contains("Docling processing failed"));

        client.shutdown();
    }

    @Test
    void testConvertHttpError() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody("Internal Server Error")
            .setResponseCode(500));

        DoclingClient client = createClient();

        HybridClient.HybridRequest request = HybridClient.HybridRequest.allPages(
            new byte[]{0x25, 0x50, 0x44, 0x46}
        );

        IOException exception = assertThrows(IOException.class, () -> client.convert(request));
        assertTrue(exception.getMessage().contains("status 500"));

        client.shutdown();
    }

    @Test
    void testConvertAsyncSuccessfulResponse() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(SAMPLE_RESPONSE)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        DoclingClient client = createClient();

        HybridClient.HybridRequest request = HybridClient.HybridRequest.allPages(
            new byte[]{0x25, 0x50, 0x44, 0x46}
        );

        CompletableFuture<HybridClient.HybridResponse> future = client.convertAsync(request);
        HybridClient.HybridResponse response = future.join();

        assertNotNull(response);
        assertEquals("# Test Document\n\nThis is a test.", response.getMarkdown());

        client.shutdown();
    }

    @Test
    void testGetBaseUrl() {
        HybridConfig config = new HybridConfig();
        config.setUrl("http://custom:8080");

        DoclingClient client = new DoclingClient(config);

        assertEquals("http://custom:8080", client.getBaseUrl());
        client.shutdown();
    }

    @Test
    void testDefaultBaseUrl() {
        HybridConfig config = new HybridConfig();
        // Don't set URL, should use default

        DoclingClient client = new DoclingClient(config);

        assertEquals(HybridConfig.DOCLING_DEFAULT_URL, client.getBaseUrl());
        client.shutdown();
    }

    @Test
    void testConvertWithPageNumbers() throws Exception {
        mockServer.enqueue(new MockResponse()
            .setBody(SAMPLE_RESPONSE)
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json"));

        DoclingClient client = createClient();

        HybridClient.HybridRequest request = new HybridClient.HybridRequest(
            new byte[]{0x25, 0x50, 0x44, 0x46},
            Set.of(1, 3, 5),
            true,
            true
        );

        HybridClient.HybridResponse response = client.convert(request);

        assertNotNull(response);
        assertNotNull(response.getMarkdown());

        client.shutdown();
    }

    @Test
    void testShutdownReleasesResources() {
        HybridConfig config = new HybridConfig();
        DoclingClient client = new DoclingClient(config);

        // Should not throw
        client.shutdown();
    }

    /**
     * Creates a DoclingClient connected to the mock server.
     */
    private DoclingClient createClient() {
        String baseUrl = mockServer.url("/").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

        return new DoclingClient(baseUrl, httpClient, objectMapper);
    }
}

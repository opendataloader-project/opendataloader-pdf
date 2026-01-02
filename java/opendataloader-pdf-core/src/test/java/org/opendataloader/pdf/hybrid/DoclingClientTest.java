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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DoclingClient.
 *
 * <p>These tests use a mock HTTP client to verify the client behavior
 * without requiring a running docling-serve instance.
 */
class DoclingClientTest {

    private static final String TEST_BASE_URL = "http://localhost:5001";
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

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
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
        // Create a mock HTTP client that returns a successful response
        HttpClient mockClient = createMockHttpClient(200, SAMPLE_RESPONSE);

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

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
    }

    @Test
    void testConvertErrorResponse() throws Exception {
        // Create a mock HTTP client that returns an error response
        HttpClient mockClient = createMockHttpClient(200, ERROR_RESPONSE);

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

        HybridClient.HybridRequest request = HybridClient.HybridRequest.allPages(
            new byte[]{0x25, 0x50, 0x44, 0x46}
        );

        IOException exception = assertThrows(IOException.class, () -> client.convert(request));
        assertTrue(exception.getMessage().contains("Docling processing failed"));
    }

    @Test
    void testConvertHttpError() throws Exception {
        // Create a mock HTTP client that returns a 500 error
        HttpClient mockClient = createMockHttpClient(500, "Internal Server Error");

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

        HybridClient.HybridRequest request = HybridClient.HybridRequest.allPages(
            new byte[]{0x25, 0x50, 0x44, 0x46}
        );

        IOException exception = assertThrows(IOException.class, () -> client.convert(request));
        assertTrue(exception.getMessage().contains("status 500"));
    }

    @Test
    void testConvertAsyncSuccessfulResponse() throws Exception {
        HttpClient mockClient = createMockAsyncHttpClient(200, SAMPLE_RESPONSE);

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

        HybridClient.HybridRequest request = HybridClient.HybridRequest.allPages(
            new byte[]{0x25, 0x50, 0x44, 0x46}
        );

        CompletableFuture<HybridClient.HybridResponse> future = client.convertAsync(request);
        HybridClient.HybridResponse response = future.join();

        assertNotNull(response);
        assertEquals("# Test Document\n\nThis is a test.", response.getMarkdown());
    }

    @Test
    void testIsAvailableReturnsTrue() throws Exception {
        HttpClient mockClient = createMockHttpClient(200, "OK");

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

        assertTrue(client.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalseOnError() throws Exception {
        HttpClient mockClient = createMockHttpClient(503, "Service Unavailable");

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

        assertFalse(client.isAvailable());
    }

    @Test
    void testIsAvailableReturnsFalseOnException() {
        HttpClient mockClient = createExceptionThrowingHttpClient();

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

        assertFalse(client.isAvailable());
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
        HttpClient mockClient = createMockHttpClient(200, SAMPLE_RESPONSE);

        DoclingClient client = new DoclingClient(
            TEST_BASE_URL, mockClient, objectMapper, 30000
        );

        HybridClient.HybridRequest request = new HybridClient.HybridRequest(
            new byte[]{0x25, 0x50, 0x44, 0x46},
            Set.of(1, 3, 5),
            true,
            true
        );

        HybridClient.HybridResponse response = client.convert(request);

        assertNotNull(response);
        assertNotNull(response.getMarkdown());
    }

    // ===== Helper Methods for Creating Mock HTTP Clients =====

    /**
     * Creates a mock HttpClient that returns a synchronous response.
     */
    private HttpClient createMockHttpClient(int statusCode, String body) {
        return new MockHttpClient(statusCode, body, false);
    }

    /**
     * Creates a mock HttpClient for async operations.
     */
    private HttpClient createMockAsyncHttpClient(int statusCode, String body) {
        return new MockHttpClient(statusCode, body, false);
    }

    /**
     * Creates a mock HttpClient that throws an exception.
     */
    private HttpClient createExceptionThrowingHttpClient() {
        return new MockHttpClient(0, null, true);
    }

    /**
     * Mock HttpClient implementation for testing.
     */
    private static class MockHttpClient extends HttpClient {
        private final int statusCode;
        private final String body;
        private final boolean throwException;

        MockHttpClient(int statusCode, String body, boolean throwException) {
            this.statusCode = statusCode;
            this.body = body;
            this.throwException = throwException;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NORMAL;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(30));
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
            throws IOException {
            if (throwException) {
                throw new IOException("Connection refused");
            }
            return (HttpResponse<T>) createMockResponse(statusCode, body);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler) {
            if (throwException) {
                return CompletableFuture.failedFuture(new IOException("Connection refused"));
            }
            return CompletableFuture.completedFuture(
                (HttpResponse<T>) createMockResponse(statusCode, body)
            );
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler,
            HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            return sendAsync(request, responseBodyHandler);
        }

        private HttpResponse<String> createMockResponse(int statusCode, String body) {
            return new MockHttpResponse(statusCode, body);
        }
    }

    /**
     * Mock HttpResponse implementation for testing.
     */
    private static class MockHttpResponse implements HttpResponse<String> {
        private final int statusCode;
        private final String body;

        MockHttpResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(Collections.emptyMap(), (a, b) -> true);
        }

        @Override
        public String body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create(TEST_BASE_URL);
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}

/*
 * Copyright 2025 Hancom Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for hybrid server health check (issue #225).
 *
 * <p>Verifies that the client fails fast with a clear error message
 * when the hybrid server is not available, instead of hanging for 30 seconds.
 */
class HealthCheckTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void testDoclingHealthCheckSucceeds() throws IOException {
        server.start();
        server.enqueue(new MockResponse()
            .setBody("{\"status\": \"ok\"}")
            .addHeader("Content-Type", "application/json"));

        String baseUrl = stripTrailingSlash(server.url("").toString());
        DoclingFastServerClient client = new DoclingFastServerClient(
            baseUrl, new OkHttpClient(), new ObjectMapper());

        try {
            assertDoesNotThrow(() -> client.checkAvailability());
        } finally {
            client.shutdown();
        }
    }

    @Test
    void testDoclingHealthCheckFailsWhenServerDown() throws IOException {
        // Find an unused port, then don't start any server on it
        int unusedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            unusedPort = s.getLocalPort();
        }

        String baseUrl = "http://localhost:" + unusedPort;
        DoclingFastServerClient client = new DoclingFastServerClient(
            baseUrl, new OkHttpClient(), new ObjectMapper());

        try {
            IOException exception = assertThrows(IOException.class, client::checkAvailability);
            assertTrue(exception.getMessage().contains("not available"),
                "Error message should indicate server is not available");
            assertTrue(exception.getMessage().contains(String.valueOf(unusedPort)),
                "Error message should include the server URL");
            assertTrue(exception.getMessage().contains("opendataloader-pdf-hybrid"),
                "Error message should suggest how to start the server");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void testDoclingHealthCheckFailsOnServerError() throws IOException {
        server.start();
        server.enqueue(new MockResponse().setResponseCode(503));

        String baseUrl = stripTrailingSlash(server.url("").toString());
        DoclingFastServerClient client = new DoclingFastServerClient(
            baseUrl, new OkHttpClient(), new ObjectMapper());

        try {
            IOException exception = assertThrows(IOException.class, client::checkAvailability);
            assertTrue(exception.getMessage().contains("returned HTTP 503"),
                "Error message should include the HTTP status code");
            assertTrue(exception.getMessage().contains("reachable but"),
                "Error message should indicate server is reachable but unhealthy");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void testHancomHealthCheckSucceeds() throws IOException {
        server.start();
        server.enqueue(new MockResponse().setResponseCode(200));

        String baseUrl = stripTrailingSlash(server.url("").toString());
        HancomClient client = new HancomClient(
            baseUrl, new OkHttpClient(), new ObjectMapper());

        try {
            assertDoesNotThrow(() -> client.checkAvailability());
        } finally {
            client.shutdown();
        }
    }

    @Test
    void testHancomHealthCheckFailsWhenServerDown() throws IOException {
        int unusedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            unusedPort = s.getLocalPort();
        }

        String baseUrl = "http://localhost:" + unusedPort;
        HancomClient client = new HancomClient(
            baseUrl, new OkHttpClient(), new ObjectMapper());

        try {
            IOException exception = assertThrows(IOException.class, client::checkAvailability);
            assertTrue(exception.getMessage().contains("not available"),
                "Error message should indicate server is not available");
        } finally {
            client.shutdown();
        }
    }

    @Test
    void testHealthCheckTimesOutQuickly() throws IOException {
        // Uses TEST-NET IP (RFC 5737) to trigger a connect timeout.
        // Some CI environments may reject packets instantly instead of timing out,
        // but the upper-bound assertion (< 10s) still holds in either case.
        String baseUrl = "http://192.0.2.1:9999";
        DoclingFastServerClient client = new DoclingFastServerClient(
            baseUrl, new OkHttpClient(), new ObjectMapper());

        try {
            long start = System.currentTimeMillis();
            assertThrows(IOException.class, client::checkAvailability);
            long elapsed = System.currentTimeMillis() - start;

            // Should fail within ~5 seconds (3s timeout + overhead), not 30 seconds
            assertTrue(elapsed < 10_000,
                "Health check should timeout quickly, took " + elapsed + "ms");
        } finally {
            client.shutdown();
        }
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}

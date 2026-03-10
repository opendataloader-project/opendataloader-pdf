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
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DoclingFastServerClient partial_success handling.
 */
class DoclingFastServerClientTest {

    private MockWebServer server;
    private DoclingFastServerClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("").toString();
        // Remove trailing slash
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        client = new DoclingFastServerClient(baseUrl, new OkHttpClient(), new ObjectMapper());
    }

    @AfterEach
    void tearDown() throws IOException {
        client.shutdown();
        server.shutdown();
    }

    @Test
    void testSuccessResponseHasNoFailedPages() throws IOException {
        String responseJson = "{"
            + "\"status\": \"success\","
            + "\"document\": {\"json_content\": {\"pages\": {\"1\": {}, \"2\": {}, \"3\": {}}}},"
            + "\"processing_time\": 1.5,"
            + "\"errors\": [],"
            + "\"failed_pages\": []"
            + "}";

        server.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        HybridRequest request = HybridRequest.allPages(new byte[]{0x25, 0x50, 0x44, 0x46});
        HybridResponse response = client.convert(request);

        assertFalse(response.hasFailedPages());
        assertEquals(Collections.emptyList(), response.getFailedPages());
    }

    @Test
    void testPartialSuccessResponseWithFailedPages() throws IOException {
        String responseJson = "{"
            + "\"status\": \"partial_success\","
            + "\"document\": {\"json_content\": {\"pages\": {\"1\": {}, \"2\": {}, \"4\": {}, \"5\": {}}}},"
            + "\"processing_time\": 2.0,"
            + "\"errors\": [\"Unknown page: pipeline terminated early\"],"
            + "\"failed_pages\": [3]"
            + "}";

        server.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        HybridRequest request = HybridRequest.allPages(new byte[]{0x25, 0x50, 0x44, 0x46});
        HybridResponse response = client.convert(request);

        assertTrue(response.hasFailedPages());
        assertEquals(Collections.singletonList(3), response.getFailedPages());
    }

    @Test
    void testPartialSuccessMultipleFailedPages() throws IOException {
        String responseJson = "{"
            + "\"status\": \"partial_success\","
            + "\"document\": {\"json_content\": {\"pages\": {\"1\": {}, \"3\": {}, \"5\": {}}}},"
            + "\"processing_time\": 3.0,"
            + "\"errors\": [\"Unknown page: pipeline terminated early\", \"Unknown page: pipeline terminated early\"],"
            + "\"failed_pages\": [2, 4]"
            + "}";

        server.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        HybridRequest request = HybridRequest.allPages(new byte[]{0x25, 0x50, 0x44, 0x46});
        HybridResponse response = client.convert(request);

        assertTrue(response.hasFailedPages());
        assertEquals(Arrays.asList(2, 4), response.getFailedPages());
    }

    @Test
    void testFailureResponseThrowsIOException() {
        String responseJson = "{"
            + "\"status\": \"failure\","
            + "\"errors\": [\"PDF conversion failed: ValueError: corrupted file\"]"
            + "}";

        server.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        HybridRequest request = HybridRequest.allPages(new byte[]{0x25, 0x50, 0x44, 0x46});

        IOException exception = assertThrows(IOException.class, () -> client.convert(request));
        assertTrue(exception.getMessage().contains("processing failed"));
    }

    @Test
    void testLegacyResponseWithoutFailedPagesField() throws IOException {
        // Older server versions may not include failed_pages field
        String responseJson = "{"
            + "\"status\": \"success\","
            + "\"document\": {\"json_content\": {\"pages\": {\"1\": {}, \"2\": {}}}},"
            + "\"processing_time\": 1.0"
            + "}";

        server.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        HybridRequest request = HybridRequest.allPages(new byte[]{0x25, 0x50, 0x44, 0x46});
        HybridResponse response = client.convert(request);

        assertFalse(response.hasFailedPages());
        assertEquals(Collections.emptyList(), response.getFailedPages());
    }

    @Test
    void testMalformedFailedPagesValues() throws IOException {
        // Server returns mixed valid/invalid values in failed_pages array
        String responseJson = "{"
            + "\"status\": \"partial_success\","
            + "\"document\": {\"json_content\": {\"pages\": {\"1\": {}, \"2\": {}}}},"
            + "\"processing_time\": 1.0,"
            + "\"errors\": [\"error\"],"
            + "\"failed_pages\": [3, \"bad\", null, 5]"
            + "}";

        server.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        HybridRequest request = HybridRequest.allPages(new byte[]{0x25, 0x50, 0x44, 0x46});
        HybridResponse response = client.convert(request);

        assertTrue(response.hasFailedPages());
        // Only valid integer values should be extracted
        assertEquals(Arrays.asList(3, 5), response.getFailedPages());
    }

    @Test
    void testPartialSuccessAllPagesFailed() throws IOException {
        String responseJson = "{"
            + "\"status\": \"partial_success\","
            + "\"document\": {\"json_content\": {\"pages\": {}}},"
            + "\"processing_time\": 2.0,"
            + "\"errors\": [\"error1\", \"error2\", \"error3\"],"
            + "\"failed_pages\": [1, 2, 3]"
            + "}";

        server.enqueue(new MockResponse()
            .setBody(responseJson)
            .addHeader("Content-Type", "application/json"));

        HybridRequest request = HybridRequest.allPages(new byte[]{0x25, 0x50, 0x44, 0x46});
        HybridResponse response = client.convert(request);

        assertTrue(response.hasFailedPages());
        assertEquals(Arrays.asList(1, 2, 3), response.getFailedPages());
    }
}

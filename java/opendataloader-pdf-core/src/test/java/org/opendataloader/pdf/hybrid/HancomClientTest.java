/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for HancomClient.
 *
 * <p>Uses MockWebServer to simulate Hancom API responses.
 */
public class HancomClientTest {

    private MockWebServer mockServer;
    private HancomClient client;
    private ObjectMapper objectMapper;

    private static final byte[] SAMPLE_PDF_BYTES = "%PDF-1.4 sample".getBytes();

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start();

        HybridConfig config = new HybridConfig();
        config.setUrl(mockServer.url("/").toString());
        config.setTimeoutMs(5000);

        client = new HancomClient(config);
        objectMapper = new ObjectMapper();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.shutdown();
        mockServer.shutdown();
    }

    @Test
    void testDefaultUrlConfiguration() {
        Assertions.assertEquals(
            "https://dataloader.cloud.hancom.com/studio-lite/api",
            HancomClient.DEFAULT_URL
        );
    }

    @Test
    void testConvertFullWorkflow() throws Exception {
        // Mock upload response (Hancom API format: data.fileId)
        String uploadResponse = "{\"codeNum\":0,\"code\":\"file.upload.success\",\"data\":{\"fileId\":\"test-file-123\",\"fileName\":\"test.pdf\"}}";
        mockServer.enqueue(new MockResponse()
            .setBody(uploadResponse)
            .setHeader("Content-Type", "application/json"));

        // Mock visualinfo response
        String visualInfoResponse = createVisualInfoResponse();
        mockServer.enqueue(new MockResponse()
            .setBody(visualInfoResponse)
            .setHeader("Content-Type", "application/json"));

        // Mock delete response
        mockServer.enqueue(new MockResponse().setResponseCode(200));

        HybridRequest request = HybridRequest.allPages(SAMPLE_PDF_BYTES);
        HybridResponse response = client.convert(request);

        Assertions.assertNotNull(response);
        Assertions.assertNotNull(response.getJson());

        // Verify 3 requests: upload, visualinfo, delete
        Assertions.assertEquals(3, mockServer.getRequestCount());

        // Verify upload request
        RecordedRequest uploadReq = mockServer.takeRequest(1, TimeUnit.SECONDS);
        Assertions.assertTrue(uploadReq.getPath().contains("/v1/dl/files/upload"));
        Assertions.assertTrue(uploadReq.getHeader("Content-Type").contains("multipart/form-data"));

        // Verify visualinfo request
        RecordedRequest visualInfoReq = mockServer.takeRequest(1, TimeUnit.SECONDS);
        Assertions.assertTrue(visualInfoReq.getPath().contains("/v1/dl/files/test-file-123/visualinfo"));
        Assertions.assertTrue(visualInfoReq.getPath().contains("engine=pdf_ai_dl"));
        Assertions.assertTrue(visualInfoReq.getPath().contains("dlaMode=ENABLED"));
        Assertions.assertTrue(visualInfoReq.getPath().contains("ocrMode=FORCE"));

        // Verify delete request
        RecordedRequest deleteReq = mockServer.takeRequest(1, TimeUnit.SECONDS);
        Assertions.assertTrue(deleteReq.getPath().contains("/v1/dl/files/test-file-123"));
        Assertions.assertEquals("DELETE", deleteReq.getMethod());
    }

    @Test
    void testConvertWithCleanupOnProcessingError() throws Exception {
        // Mock upload response (Hancom API format: data.fileId)
        String uploadResponse = "{\"codeNum\":0,\"code\":\"file.upload.success\",\"data\":{\"fileId\":\"test-file-456\",\"fileName\":\"test.pdf\"}}";
        mockServer.enqueue(new MockResponse()
            .setBody(uploadResponse)
            .setHeader("Content-Type", "application/json"));

        // Mock visualinfo error response
        mockServer.enqueue(new MockResponse()
            .setResponseCode(500)
            .setBody("{\"error\": \"Internal server error\"}"));

        // Mock delete response - should still be called
        mockServer.enqueue(new MockResponse().setResponseCode(200));

        HybridRequest request = HybridRequest.allPages(SAMPLE_PDF_BYTES);

        Assertions.assertThrows(IOException.class, () -> {
            client.convert(request);
        });

        // Verify delete was still called (cleanup)
        Assertions.assertEquals(3, mockServer.getRequestCount());
    }

    @Test
    void testConvertWithSpecificPages() throws Exception {
        // Mock upload response (Hancom API format: data.fileId)
        String uploadResponse = "{\"codeNum\":0,\"code\":\"file.upload.success\",\"data\":{\"fileId\":\"test-file-pages\",\"fileName\":\"test.pdf\"}}";
        mockServer.enqueue(new MockResponse()
            .setBody(uploadResponse)
            .setHeader("Content-Type", "application/json"));

        // Mock visualinfo response
        String visualInfoResponse = createVisualInfoResponse();
        mockServer.enqueue(new MockResponse()
            .setBody(visualInfoResponse)
            .setHeader("Content-Type", "application/json"));

        // Mock delete response
        mockServer.enqueue(new MockResponse().setResponseCode(200));

        Set<Integer> pages = new HashSet<>();
        pages.add(1);
        pages.add(3);
        HybridRequest request = HybridRequest.forPages(SAMPLE_PDF_BYTES, pages);

        HybridResponse response = client.convert(request);

        Assertions.assertNotNull(response);
    }

    @Test
    void testUploadFailure() throws Exception {
        // Mock upload error
        mockServer.enqueue(new MockResponse()
            .setResponseCode(400)
            .setBody("{\"error\": \"Invalid file format\"}"));

        HybridRequest request = HybridRequest.allPages(SAMPLE_PDF_BYTES);

        Assertions.assertThrows(IOException.class, () -> {
            client.convert(request);
        });

        // Only upload was called (no visualinfo or delete)
        Assertions.assertEquals(1, mockServer.getRequestCount());
    }

    @Test
    void testDeleteFailureIsIgnored() throws Exception {
        // Mock upload response (Hancom API format: data.fileId)
        String uploadResponse = "{\"codeNum\":0,\"code\":\"file.upload.success\",\"data\":{\"fileId\":\"test-file-del\",\"fileName\":\"test.pdf\"}}";
        mockServer.enqueue(new MockResponse()
            .setBody(uploadResponse)
            .setHeader("Content-Type", "application/json"));

        // Mock visualinfo response
        String visualInfoResponse = createVisualInfoResponse();
        mockServer.enqueue(new MockResponse()
            .setBody(visualInfoResponse)
            .setHeader("Content-Type", "application/json"));

        // Mock delete failure - should be ignored
        mockServer.enqueue(new MockResponse().setResponseCode(404));

        HybridRequest request = HybridRequest.allPages(SAMPLE_PDF_BYTES);
        HybridResponse response = client.convert(request);

        // Should succeed despite delete failure
        Assertions.assertNotNull(response);
        Assertions.assertEquals(3, mockServer.getRequestCount());
    }

    @Test
    void testConvertAsync() throws Exception {
        // Mock responses (Hancom API format: data.fileId)
        String uploadResponse = "{\"codeNum\":0,\"code\":\"file.upload.success\",\"data\":{\"fileId\":\"async-file\",\"fileName\":\"test.pdf\"}}";
        mockServer.enqueue(new MockResponse()
            .setBody(uploadResponse)
            .setHeader("Content-Type", "application/json"));

        String visualInfoResponse = createVisualInfoResponse();
        mockServer.enqueue(new MockResponse()
            .setBody(visualInfoResponse)
            .setHeader("Content-Type", "application/json"));

        mockServer.enqueue(new MockResponse().setResponseCode(200));

        HybridRequest request = HybridRequest.allPages(SAMPLE_PDF_BYTES);
        HybridResponse response = client.convertAsync(request).get(10, TimeUnit.SECONDS);

        Assertions.assertNotNull(response);
    }

    private String createVisualInfoResponse() {
        return "{\n" +
            "  \"runtime\": 1234,\n" +
            "  \"version\": \"1.0\",\n" +
            "  \"metadata\": {\n" +
            "    \"fileId\": \"test-file\",\n" +
            "    \"fileName\": \"test.pdf\",\n" +
            "    \"numOfPages\": 1\n" +
            "  },\n" +
            "  \"elements\": [\n" +
            "    {\n" +
            "      \"id\": \"1\",\n" +
            "      \"category\": {\"type\": \"PARAGRAPH\", \"label\": \"text\"},\n" +
            "      \"content\": {\"text\": \"Hello World\"},\n" +
            "      \"bbox\": {\"left\": 100, \"top\": 100, \"width\": 200, \"height\": 50},\n" +
            "      \"pageIndex\": 0\n" +
            "    }\n" +
            "  ],\n" +
            "  \"pageSizes\": [{\"width\": 612, \"height\": 792}]\n" +
            "}";
    }
}

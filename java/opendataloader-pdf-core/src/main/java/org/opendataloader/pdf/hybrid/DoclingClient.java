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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * HTTP client for docling-serve API.
 *
 * <p>Implements the HybridClient interface for communicating with docling-serve,
 * the HTTP API for the Docling PDF/document parsing library.
 *
 * <p>API documentation: https://github.com/DS4SD/docling-serve
 *
 * @see HybridClient
 * @see HybridConfig
 */
public class DoclingClient implements HybridClient {

    private static final String CONVERT_ENDPOINT = "/v1/convert/file";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final String DEFAULT_FILENAME = "document.pdf";

    private final String baseUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int timeoutMs;
    private final ExecutorService executor;

    /**
     * Creates a new DoclingClient with the specified configuration.
     *
     * @param config The hybrid configuration containing URL and timeout settings.
     */
    public DoclingClient(HybridConfig config) {
        this.baseUrl = config.getEffectiveUrl("docling");
        this.timeoutMs = config.getTimeoutMs();
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newFixedThreadPool(config.getMaxConcurrentRequests());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(timeoutMs))
            .executor(executor)
            .build();
    }

    /**
     * Creates a new DoclingClient with a custom HttpClient (for testing).
     *
     * @param baseUrl      The base URL of the docling-serve instance.
     * @param httpClient   The HTTP client to use for requests.
     * @param objectMapper The Jackson ObjectMapper for JSON parsing.
     * @param timeoutMs    Request timeout in milliseconds.
     */
    DoclingClient(String baseUrl, HttpClient httpClient, ObjectMapper objectMapper, int timeoutMs) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.timeoutMs = timeoutMs;
        this.executor = null; // Not needed when HttpClient is provided
    }

    @Override
    public HybridResponse convert(HybridRequest request) throws IOException {
        try {
            HttpRequest httpRequest = buildConvertRequest(request);
            HttpResponse<String> response = httpClient.send(
                httpRequest,
                HttpResponse.BodyHandlers.ofString()
            );
            return parseResponse(response);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Request interrupted", e);
        }
    }

    @Override
    public CompletableFuture<HybridResponse> convertAsync(HybridRequest request) {
        try {
            HttpRequest httpRequest = buildConvertRequest(request);
            return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        return parseResponse(response);
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to parse response", e);
                    }
                });
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + HEALTH_ENDPOINT))
                .timeout(Duration.ofMillis(Math.min(timeoutMs, 5000))) // Short timeout for health check
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );

            return response.statusCode() == 200;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Gets the base URL of this client.
     *
     * @return The base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Builds a multipart/form-data HTTP request for the convert endpoint.
     */
    private HttpRequest buildConvertRequest(HybridRequest request) throws IOException {
        String boundary = UUID.randomUUID().toString();
        byte[] body = buildMultipartBody(request, boundary);

        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + CONVERT_ENDPOINT))
            .timeout(Duration.ofMillis(timeoutMs))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body));

        return builder.build();
    }

    /**
     * Builds the multipart/form-data request body.
     */
    private byte[] buildMultipartBody(HybridRequest request, String boundary) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Add file part
        writeFilePart(baos, boundary, "files", DEFAULT_FILENAME, request.getPdfBytes());

        // Add to_formats - request both JSON and Markdown
        writeFormField(baos, boundary, "to_formats", "json");
        writeFormField(baos, boundary, "to_formats", "md");

        // Add processing options
        writeFormField(baos, boundary, "do_table_structure", String.valueOf(request.isDoTableStructure()));
        writeFormField(baos, boundary, "do_ocr", String.valueOf(request.isDoOcr()));

        // Add page range if specified
        if (request.getPageNumbers() != null && !request.getPageNumbers().isEmpty()) {
            int minPage = request.getPageNumbers().stream().min(Integer::compareTo).orElse(1);
            int maxPage = request.getPageNumbers().stream().max(Integer::compareTo).orElse(Integer.MAX_VALUE);
            // Note: docling-serve uses page_range as [start, end] inclusive
            writeFormField(baos, boundary, "page_range", "[" + minPage + "," + maxPage + "]");
        }

        // Write closing boundary
        baos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        return baos.toByteArray();
    }

    /**
     * Writes a file part to the multipart body.
     */
    private void writeFilePart(ByteArrayOutputStream baos, String boundary,
                               String fieldName, String filename, byte[] content) throws IOException {
        String header = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + filename + "\"\r\n" +
            "Content-Type: application/pdf\r\n\r\n";
        baos.write(header.getBytes(StandardCharsets.UTF_8));
        baos.write(content);
        baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Writes a form field to the multipart body.
     */
    private void writeFormField(ByteArrayOutputStream baos, String boundary,
                                String fieldName, String value) throws IOException {
        String part = "--" + boundary + "\r\n" +
            "Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n" +
            value + "\r\n";
        baos.write(part.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Parses the HTTP response into a HybridResponse.
     */
    private HybridResponse parseResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("Docling API request failed with status " + response.statusCode() +
                ": " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());

        // Check for API error status
        JsonNode statusNode = root.get("status");
        if (statusNode != null && "failure".equals(statusNode.asText())) {
            JsonNode errorsNode = root.get("errors");
            String errorMessage = errorsNode != null ? errorsNode.toString() : "Unknown error";
            throw new IOException("Docling processing failed: " + errorMessage);
        }

        // Extract document content
        JsonNode documentNode = root.get("document");
        if (documentNode == null) {
            throw new IOException("Invalid response: missing 'document' field");
        }

        String markdown = extractString(documentNode, "md_content");
        JsonNode jsonContent = documentNode.get("json_content");

        // Extract per-page content from json_content if available
        Map<Integer, JsonNode> pageContents = extractPageContents(jsonContent);

        return new HybridResponse(markdown, jsonContent, pageContents);
    }

    /**
     * Extracts a string value from a JSON node.
     */
    private String extractString(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && !field.isNull() ? field.asText() : "";
    }

    /**
     * Extracts per-page content from the DoclingDocument JSON structure.
     *
     * <p>The DoclingDocument stores page information in the "pages" object,
     * keyed by page number (as string). This method extracts the content
     * elements for each page based on the "prov" (provenance) information.
     */
    private Map<Integer, JsonNode> extractPageContents(JsonNode jsonContent) {
        Map<Integer, JsonNode> pageContents = new HashMap<>();

        if (jsonContent == null) {
            return pageContents;
        }

        // The pages node contains page metadata keyed by page number
        JsonNode pagesNode = jsonContent.get("pages");
        if (pagesNode != null && pagesNode.isObject()) {
            Iterator<String> fieldNames = pagesNode.fieldNames();
            while (fieldNames.hasNext()) {
                String pageNumStr = fieldNames.next();
                try {
                    int pageNum = Integer.parseInt(pageNumStr);
                    pageContents.put(pageNum, pagesNode.get(pageNumStr));
                } catch (NumberFormatException ignored) {
                    // Skip non-numeric page keys
                }
            }
        }

        return pageContents;
    }

    /**
     * Shuts down the executor service used by this client.
     *
     * <p>This should be called when the client is no longer needed
     * to release resources.
     */
    public void shutdown() {
        if (executor != null) {
            executor.shutdown();
        }
    }
}

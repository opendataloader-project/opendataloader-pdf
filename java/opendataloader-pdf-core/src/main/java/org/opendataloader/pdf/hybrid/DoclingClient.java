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
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger(DoclingClient.class.getCanonicalName());

    private static final String CONVERT_ENDPOINT = "/v1/convert/file";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final String DEFAULT_FILENAME = "document.pdf";
    private static final MediaType MEDIA_TYPE_PDF = MediaType.parse("application/pdf");

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new DoclingClient with the specified configuration.
     *
     * @param config The hybrid configuration containing URL and timeout settings.
     */
    public DoclingClient(HybridConfig config) {
        this.baseUrl = config.getEffectiveUrl("docling");
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
    }

    /**
     * Creates a new DoclingClient with a custom OkHttpClient (for testing).
     *
     * @param baseUrl      The base URL of the docling-serve instance.
     * @param httpClient   The OkHttp client to use for requests.
     * @param objectMapper The Jackson ObjectMapper for JSON parsing.
     */
    DoclingClient(String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public HybridResponse convert(HybridRequest request) throws IOException {
        Request httpRequest = buildConvertRequest(request);
        LOGGER.log(Level.FINE, "Sending request to {0}", baseUrl + CONVERT_ENDPOINT);

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            return parseResponse(response);
        }
    }

    @Override
    public CompletableFuture<HybridResponse> convertAsync(HybridRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convert(request);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to convert", e);
            }
        });
    }

    @Override
    public boolean isAvailable() {
        Request request = new Request.Builder()
            .url(baseUrl + HEALTH_ENDPOINT)
            .get()
            .build();

        // Use a short timeout for health check
        OkHttpClient healthClient = httpClient.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build();

        try (Response response = healthClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
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
    private Request buildConvertRequest(HybridRequest request) {
        MultipartBody.Builder bodyBuilder = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("files", DEFAULT_FILENAME,
                RequestBody.create(request.getPdfBytes(), MEDIA_TYPE_PDF))
            .addFormDataPart("to_formats", "json")
            .addFormDataPart("to_formats", "md")
            .addFormDataPart("do_table_structure", String.valueOf(request.isDoTableStructure()))
            .addFormDataPart("do_ocr", String.valueOf(request.isDoOcr()));

        // Add page range if specified
        if (request.getPageNumbers() != null && !request.getPageNumbers().isEmpty()) {
            int minPage = request.getPageNumbers().stream().min(Integer::compareTo).orElse(1);
            int maxPage = request.getPageNumbers().stream().max(Integer::compareTo).orElse(Integer.MAX_VALUE);
            bodyBuilder.addFormDataPart("page_range", String.valueOf(minPage));
            bodyBuilder.addFormDataPart("page_range", String.valueOf(maxPage));
        }

        return new Request.Builder()
            .url(baseUrl + CONVERT_ENDPOINT)
            .post(bodyBuilder.build())
            .build();
    }

    /**
     * Parses the HTTP response into a HybridResponse.
     */
    private HybridResponse parseResponse(Response response) throws IOException {
        if (!response.isSuccessful()) {
            ResponseBody body = response.body();
            String bodyStr = body != null ? body.string() : "";
            throw new IOException("Docling API request failed with status " + response.code() +
                ": " + bodyStr);
        }

        ResponseBody body = response.body();
        if (body == null) {
            throw new IOException("Empty response body");
        }

        String responseStr = body.string();
        JsonNode root = objectMapper.readTree(responseStr);

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
     * Shuts down the HTTP client and releases all resources.
     *
     * <p>This gracefully shuts down the dispatcher's executor service,
     * allowing the JVM to exit cleanly. Idle connections are evicted
     * from the connection pool.
     */
    public void shutdown() {
        // Gracefully shutdown the dispatcher - allows pending requests to complete
        httpClient.dispatcher().executorService().shutdown();
        // Evict idle connections from pool (does not affect the server)
        httpClient.connectionPool().evictAll();
        // Close the cache if present
        if (httpClient.cache() != null) {
            try {
                httpClient.cache().close();
            } catch (Exception ignored) {
                // Ignore cache close errors
            }
        }
    }
}

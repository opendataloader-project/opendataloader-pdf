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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for Hancom Document AI API.
 *
 * <p>This client communicates with the Hancom Document AI backend service
 * for PDF processing. The workflow is:
 * <ol>
 *   <li>Upload PDF file to /v1/dl/files/upload</li>
 *   <li>Request visual info extraction from /v1/dl/files/{fileId}/visualinfo</li>
 *   <li>Delete the file from server after processing</li>
 * </ol>
 *
 * <p>The client ensures cleanup (file deletion) even when processing fails.
 *
 * @see HybridClient
 * @see HybridConfig
 */
public class HancomClient implements HybridClient {

    private static final Logger LOGGER = Logger.getLogger(HancomClient.class.getCanonicalName());

    /** Default URL for Hancom Document AI API. */
    public static final String DEFAULT_URL = "https://dataloader.cloud.hancom.com/studio-lite/api";

    private static final String UPLOAD_ENDPOINT = "/v1/dl/files/upload";
    private static final String VISUALINFO_ENDPOINT = "/v1/dl/files/%s/visualinfo";
    private static final String DELETE_ENDPOINT = "/v1/dl/files/%s";

    private static final String DEFAULT_FILENAME = "document.pdf";
    private static final MediaType MEDIA_TYPE_PDF = MediaType.parse("application/pdf");

    // Query parameters for visualinfo
    private static final String ENGINE = "pdf_ai_dl";
    private static final String DLA_MODE = "ENABLED";
    private static final String OCR_MODE = "FORCE";

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new HancomClient with the specified configuration.
     *
     * @param config The hybrid configuration containing URL and timeout settings.
     */
    public HancomClient(HybridConfig config) {
        String effectiveUrl = config.getEffectiveUrl("hancom");
        this.baseUrl = effectiveUrl != null ? normalizeUrl(effectiveUrl) : DEFAULT_URL;
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
    }

    /**
     * Creates a new HancomClient with a custom OkHttpClient (for testing).
     *
     * @param baseUrl      The base URL of the Hancom API.
     * @param httpClient   The OkHttp client to use for requests.
     * @param objectMapper The Jackson ObjectMapper for JSON parsing.
     */
    HancomClient(String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this.baseUrl = normalizeUrl(baseUrl);
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public HybridResponse convert(HybridRequest request) throws IOException {
        String fileId = null;
        try {
            // Step 1: Upload PDF
            fileId = uploadFile(request.getPdfBytes());
            LOGGER.log(Level.FINE, "Uploaded file with ID: {0}", fileId);

            // Step 2: Get visual info
            JsonNode visualInfo = getVisualInfo(fileId);
            LOGGER.log(Level.FINE, "Retrieved visual info for file: {0}", fileId);

            return new HybridResponse(null, visualInfo, null);
        } finally {
            // Step 3: Always cleanup
            if (fileId != null) {
                deleteFile(fileId);
            }
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

    /**
     * Gets the base URL of this client.
     *
     * @return The base URL.
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Uploads a PDF file to the Hancom API.
     *
     * @param pdfBytes The PDF file bytes.
     * @return The file ID assigned by the server.
     * @throws IOException If the upload fails.
     */
    private String uploadFile(byte[] pdfBytes) throws IOException {
        MultipartBody requestBody = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", DEFAULT_FILENAME,
                RequestBody.create(pdfBytes, MEDIA_TYPE_PDF))
            .build();

        Request request = new Request.Builder()
            .url(baseUrl + UPLOAD_ENDPOINT)
            .post(requestBody)
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody body = response.body();
                String bodyStr = body != null ? body.string() : "";
                throw new IOException("Hancom upload failed with status " + response.code() + ": " + bodyStr);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body from upload");
            }

            JsonNode root = objectMapper.readTree(body.string());
            // Response format: {"codeNum":0,"code":"file.upload.success","data":{"fileId":"...",...}}
            JsonNode dataNode = root.get("data");
            if (dataNode == null) {
                throw new IOException("Invalid upload response: missing data field");
            }
            JsonNode fileIdNode = dataNode.get("fileId");
            if (fileIdNode == null || !fileIdNode.isTextual()) {
                throw new IOException("Invalid upload response: missing fileId in data");
            }

            return fileIdNode.asText();
        }
    }

    /**
     * Retrieves visual info for an uploaded file.
     *
     * @param fileId The file ID from upload.
     * @return The visual info JSON response.
     * @throws IOException If the request fails.
     */
    private JsonNode getVisualInfo(String fileId) throws IOException {
        String url = baseUrl + String.format(VISUALINFO_ENDPOINT, fileId) +
            "?engine=" + ENGINE +
            "&dlaMode=" + DLA_MODE +
            "&ocrMode=" + OCR_MODE;

        Request request = new Request.Builder()
            .url(url)
            .get()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody body = response.body();
                String bodyStr = body != null ? body.string() : "";
                throw new IOException("Hancom visualinfo failed with status " + response.code() + ": " + bodyStr);
            }

            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("Empty response body from visualinfo");
            }

            return objectMapper.readTree(body.string());
        }
    }

    /**
     * Deletes an uploaded file from the server.
     *
     * <p>This method silently ignores any errors to ensure cleanup
     * doesn't interfere with the main processing result.
     *
     * @param fileId The file ID to delete.
     */
    private void deleteFile(String fileId) {
        String url = baseUrl + String.format(DELETE_ENDPOINT, fileId);

        Request request = new Request.Builder()
            .url(url)
            .delete()
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
                LOGGER.log(Level.FINE, "Deleted file: {0}", fileId);
            } else {
                LOGGER.log(Level.WARNING, "Failed to delete file {0}: {1}",
                    new Object[]{fileId, response.code()});
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error deleting file " + fileId, e);
        }
    }

    /**
     * Normalizes a URL by removing trailing slashes.
     */
    private static String normalizeUrl(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Shuts down the HTTP client and releases all resources.
     *
     * <p>This gracefully shuts down the dispatcher's executor service,
     * allowing the JVM to exit cleanly. Idle connections are evicted
     * from the connection pool.
     */
    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        if (httpClient.cache() != null) {
            try {
                httpClient.cache().close();
            } catch (Exception ignored) {
                // Ignore cache close errors
            }
        }
    }
}

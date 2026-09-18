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
package org.opendataloader.pdf.hybrid;

/**
 * Configuration class for hybrid PDF processing with external AI backends.
 *
 * <p>Hybrid processing routes pages to either Java-based processing or external
 * AI backend (docling-fast) based on page triage decisions.
 */
public class HybridConfig {

    /** Default timeout for backend requests in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MS = 0;

    /** Default maximum concurrent requests to the backend. */
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 4;

    /** Default number of pages sent to the backend per request.
     * Large scanned PDFs (100+ pages) cause the backend to hang when sent all at once
     * due to non-linear memory/processing scaling in the AI pipeline.
     * Chunking into smaller batches avoids this; note that each batch re-sends the
     * full PDF to the backend, so smaller batches mean more transfers and re-parses.
     *
     * @see <a href="https://github.com/opendataloader-project/opendataloader-pdf/issues/352">#352</a> */
    public static final int DEFAULT_CHUNK_SIZE = 50;

    /** Default URL for docling-fast-server. */
    public static final String DOCLING_FAST_DEFAULT_URL = "http://localhost:5002";

    private String url;
    private int timeoutMs = DEFAULT_TIMEOUT_MS;
    private boolean fallbackToJava = false;
    private int maxConcurrentRequests = DEFAULT_MAX_CONCURRENT_REQUESTS;
    private int chunkSize = DEFAULT_CHUNK_SIZE;

    /** Hybrid triage mode: auto (dynamic triage based on page content). */
    public static final String MODE_AUTO = "auto";
    /** Hybrid triage mode: full (skip triage, send all pages to backend). */
    public static final String MODE_FULL = "full";

    private String mode = MODE_AUTO;

    /**
     * Default constructor initializing the configuration with default values.
     */
    public HybridConfig() {
    }

    /**
     * Gets the backend server URL.
     *
     * @return The backend URL, or null if using default for the backend type.
     */
    public String getUrl() {
        return url;
    }

    /**
     * Sets the backend server URL.
     *
     * @param url The backend URL to use.
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * Gets the request timeout in milliseconds.
     *
     * @return The timeout in milliseconds.
     */
    public int getTimeoutMs() {
        return timeoutMs;
    }

    /**
     * Sets the request timeout in milliseconds. Use 0 for no timeout.
     *
     * @param timeoutMs The timeout in milliseconds (0 = no timeout).
     * @throws IllegalArgumentException if timeout is negative.
     */
    public void setTimeoutMs(int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout must be non-negative: " + timeoutMs);
        }
        this.timeoutMs = timeoutMs;
    }

    /**
     * Checks if fallback to Java processing is enabled when backend fails.
     *
     * @return true if fallback is enabled, false otherwise.
     */
    public boolean isFallbackToJava() {
        return fallbackToJava;
    }

    /**
     * Sets whether to fallback to Java processing when backend fails.
     *
     * @param fallbackToJava true to enable fallback, false to fail on backend error.
     */
    public void setFallbackToJava(boolean fallbackToJava) {
        this.fallbackToJava = fallbackToJava;
    }

    /**
     * Gets the maximum number of concurrent requests to the backend.
     *
     * @return The maximum concurrent requests.
     */
    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    /**
     * Sets the maximum number of concurrent requests to the backend.
     *
     * @param maxConcurrentRequests The maximum concurrent requests.
     * @throws IllegalArgumentException if the value is not positive.
     */
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        if (maxConcurrentRequests <= 0) {
            throw new IllegalArgumentException("Max concurrent requests must be positive: " + maxConcurrentRequests);
        }
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    /**
     * Gets the number of pages sent to the backend per request.
     *
     * @return The pages per backend request.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Sets the number of pages sent to the backend per request.
     *
     * @param chunkSize The pages per backend request.
     * @throws IllegalArgumentException if the value is not positive.
     */
    public void setChunkSize(int chunkSize) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("Chunk size must be positive: " + chunkSize);
        }
        this.chunkSize = chunkSize;
    }

    /**
     * Gets the default URL for a given hybrid backend.
     *
     * @param hybrid The hybrid backend name (docling-fast).
     * @return The default URL, or null if the backend requires explicit URL.
     */
    public static String getDefaultUrl(String hybrid) {
        if (hybrid == null) {
            return null;
        }
        String lowerHybrid = hybrid.toLowerCase();
        if ("docling-fast".equals(lowerHybrid)) {
            return DOCLING_FAST_DEFAULT_URL;
        }
        return null;
    }

    /**
     * Gets the effective URL for a given hybrid backend.
     * Returns the configured URL if set, otherwise returns the default URL for the backend.
     *
     * @param hybrid The hybrid backend name.
     * @return The effective URL to use for the backend.
     */
    public String getEffectiveUrl(String hybrid) {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        return getDefaultUrl(hybrid);
    }

    /**
     * Gets the hybrid triage mode.
     *
     * @return The mode (auto or full).
     */
    public String getMode() {
        return mode;
    }

    /**
     * Sets the hybrid triage mode.
     *
     * @param mode The mode (auto or full).
     */
    public void setMode(String mode) {
        this.mode = mode;
    }

    /**
     * Checks if full mode is enabled (skip triage, send all pages to backend).
     *
     * @return true if mode is full, false otherwise.
     */
    public boolean isFullMode() {
        return MODE_FULL.equals(mode);
    }

}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

/**
 * Configuration class for hybrid PDF processing with external AI backends.
 *
 * <p>Hybrid processing routes pages to either Java-based processing or external
 * AI backends (like docling, hancom, azure, google) based on page triage decisions.
 */
public class HybridConfig {

    /** Default timeout for backend requests in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MS = 30000;

    /** Default maximum concurrent requests to the backend. */
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 4;

    /** Default URL for docling-serve. */
    public static final String DOCLING_DEFAULT_URL = "http://localhost:5001";

    /** Default URL for docling-fast-server. */
    public static final String DOCLING_FAST_DEFAULT_URL = "http://localhost:5002";

    private String url;
    private int timeoutMs = DEFAULT_TIMEOUT_MS;
    private boolean fallbackToJava = true;
    private int maxConcurrentRequests = DEFAULT_MAX_CONCURRENT_REQUESTS;

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
     * Sets the request timeout in milliseconds.
     *
     * @param timeoutMs The timeout in milliseconds.
     * @throws IllegalArgumentException if timeout is not positive.
     */
    public void setTimeoutMs(int timeoutMs) {
        if (timeoutMs <= 0) {
            throw new IllegalArgumentException("Timeout must be positive: " + timeoutMs);
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
     * Gets the default URL for a given hybrid backend.
     *
     * @param hybrid The hybrid backend name (docling, docling-fast, hancom, azure, google).
     * @return The default URL, or null if the backend requires explicit URL.
     */
    public static String getDefaultUrl(String hybrid) {
        if (hybrid == null) {
            return null;
        }
        String lowerHybrid = hybrid.toLowerCase();
        if ("docling".equals(lowerHybrid)) {
            return DOCLING_DEFAULT_URL;
        }
        if ("docling-fast".equals(lowerHybrid)) {
            return DOCLING_FAST_DEFAULT_URL;
        }
        // hancom, azure, google require explicit URL
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
}

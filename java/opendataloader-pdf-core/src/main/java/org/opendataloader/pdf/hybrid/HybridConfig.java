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
 * AI backends (like docling, hancom, azure, google) based on page triage decisions.
 */
public class HybridConfig {

    /** Default timeout for backend requests in milliseconds. */
    public static final int DEFAULT_TIMEOUT_MS = 0;

    /** Default maximum concurrent requests to the backend. */
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 4;

    /** Default URL for docling-serve. */
    public static final String DOCLING_DEFAULT_URL = "http://localhost:5001";

    /** Default URL for docling-fast-server. */
    public static final String DOCLING_FAST_DEFAULT_URL = "http://localhost:5002";

    /** Default URL for Hancom Document AI API. */
    public static final String HANCOM_DEFAULT_URL = "https://dataloader.cloud.hancom.com/studio-lite/api";

    /** Default URL for Hancom AI HOCR SDK API. */
    public static final String HANCOM_AI_DEFAULT_URL = "http://localhost:18008/api/v1";

    private String url;
    private int timeoutMs = DEFAULT_TIMEOUT_MS;
    private boolean fallbackToJava = false;
    private int maxConcurrentRequests = DEFAULT_MAX_CONCURRENT_REQUESTS;
    /** Hybrid triage mode: auto (dynamic triage based on page content). */
    public static final String MODE_AUTO = "auto";
    /** Hybrid triage mode: full (skip triage, send all pages to backend). */
    public static final String MODE_FULL = "full";

    private String mode = MODE_AUTO;

    /** Regionlist strategy: table-first (default) — check TSR overlap, skip if TSR exists. */
    public static final String REGIONLIST_TABLE_FIRST = "table-first";
    /** Regionlist strategy: list-only — always treat label 7 as list, skip TSR check. */
    public static final String REGIONLIST_LIST_ONLY = "list-only";

    private String regionlistStrategy = REGIONLIST_TABLE_FIRST;

    /** Page image cache strategy: "memory" (default) or "disk". */
    private String imageCache = "memory";

    /** Whether to save cropped figure images to disk for debugging. */
    private boolean saveCrops = false;

    /** Output directory for saved crops (set by CLI when --save-crops is used). */
    private String cropOutputDir = null;

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
        // Both "docling" and "docling-fast" (deprecated) use the same server
        if ("docling".equals(lowerHybrid) || "docling-fast".equals(lowerHybrid)) {
            return DOCLING_FAST_DEFAULT_URL;
        }
        if ("hancom".equals(lowerHybrid)) {
            return HANCOM_DEFAULT_URL;
        }
        if ("hancom-ai".equals(lowerHybrid)) {
            return HANCOM_AI_DEFAULT_URL;
        }
        // azure, google require explicit URL
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

    /**
     * Gets the regionlist strategy for label 7 (Table region) handling.
     *
     * @return The regionlist strategy (table-first or list-only).
     */
    public String getRegionlistStrategy() {
        return regionlistStrategy;
    }

    /**
     * Sets the regionlist strategy for label 7 (Table region) handling.
     *
     * <ul>
     *   <li>{@code "table-first"} (default): check TSR overlap, skip if TSR exists, else treat as list</li>
     *   <li>{@code "list-only"}: always treat as list, skip TSR check entirely</li>
     * </ul>
     *
     * @param regionlistStrategy The regionlist strategy to use.
     */
    public void setRegionlistStrategy(String regionlistStrategy) {
        this.regionlistStrategy = regionlistStrategy;
    }

    /**
     * Checks if regionlist strategy is list-only (always treat label 7 as list).
     *
     * @return true if strategy is list-only, false otherwise.
     */
    public boolean isRegionlistListOnly() {
        return REGIONLIST_LIST_ONLY.equals(regionlistStrategy);
    }

    /**
     * Gets the page image cache strategy.
     *
     * @return "memory" or "disk".
     */
    public String getImageCache() {
        return imageCache;
    }

    /**
     * Sets the page image cache strategy.
     *
     * @param imageCache "memory" (in-heap HashMap) or "disk" (temp PNG files).
     */
    public void setImageCache(String imageCache) {
        this.imageCache = imageCache;
    }

    /**
     * Checks if cropped figure images should be saved to disk.
     *
     * @return true if save-crops is enabled.
     */
    public boolean isSaveCrops() {
        return saveCrops;
    }

    /**
     * Sets whether to save cropped figure images to disk.
     *
     * @param saveCrops true to save crops.
     */
    public void setSaveCrops(boolean saveCrops) {
        this.saveCrops = saveCrops;
    }

    /**
     * Gets the output directory for saved crops.
     *
     * @return the crop output directory path, or null if not set.
     */
    public String getCropOutputDir() {
        return cropOutputDir;
    }

    /**
     * Sets the output directory for saved crops.
     *
     * @param cropOutputDir the directory path.
     */
    public void setCropOutputDir(String cropOutputDir) {
        this.cropOutputDir = cropOutputDir;
    }
}

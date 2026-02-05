/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and managing hybrid client instances.
 *
 * <p>This factory provides a central point for instantiating HybridClient
 * implementations based on the specified backend type. Clients are cached
 * and reused to avoid creating multiple thread pools per document.
 *
 * <p>Supported backends:
 * <ul>
 *   <li>{@code docling-fast} - Optimized docling SDK server</li>
 * </ul>
 *
 * <p>Future backends (not yet implemented):
 * <ul>
 *   <li>{@code hancom} - Hancom document parsing service</li>
 *   <li>{@code azure} - Azure Document Intelligence</li>
 *   <li>{@code google} - Google Document AI</li>
 * </ul>
 *
 * @see HybridClient
 * @see HybridConfig
 */
public class HybridClientFactory {

    /** Backend type constant for Docling Fast Server. */
    public static final String BACKEND_DOCLING_FAST = "docling-fast";

    /** Backend type constant for Hancom (not yet implemented). */
    public static final String BACKEND_HANCOM = "hancom";

    /** Backend type constant for Azure (not yet implemented). */
    public static final String BACKEND_AZURE = "azure";

    /** Backend type constant for Google (not yet implemented). */
    public static final String BACKEND_GOOGLE = "google";

    /** Cache of created clients, keyed by backend type. */
    private static final Map<String, HybridClient> CLIENT_CACHE = new ConcurrentHashMap<>();

    private HybridClientFactory() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets or creates a hybrid client for the specified backend.
     *
     * <p>Clients are cached and reused across multiple documents to avoid
     * creating new thread pools for each document. Call {@link #shutdown()}
     * when processing is complete to release resources.
     *
     * @param hybrid The backend type (e.g., "docling", "hancom", "azure", "google").
     * @param config The configuration for the hybrid client.
     * @return A HybridClient instance for the specified backend.
     * @throws IllegalArgumentException If the backend type is unknown or not supported.
     */
    public static HybridClient getOrCreate(String hybrid, HybridConfig config) {
        if (hybrid == null || hybrid.isEmpty()) {
            throw new IllegalArgumentException("Hybrid backend type cannot be null or empty");
        }

        String lowerHybrid = hybrid.toLowerCase();

        return CLIENT_CACHE.computeIfAbsent(lowerHybrid, key -> createClient(key, config));
    }

    /**
     * Creates a new hybrid client instance.
     */
    private static HybridClient createClient(String hybrid, HybridConfig config) {
        if (BACKEND_DOCLING_FAST.equals(hybrid)) {
            return new DoclingFastServerClient(config);
        } else if (BACKEND_HANCOM.equals(hybrid)) {
            return new HancomClient(config);
        } else if (BACKEND_AZURE.equals(hybrid)) {
            throw new UnsupportedOperationException("Azure Document Intelligence backend is not yet implemented");
        } else if (BACKEND_GOOGLE.equals(hybrid)) {
            throw new UnsupportedOperationException("Google Document AI backend is not yet implemented");
        } else {
            throw new IllegalArgumentException("Unknown hybrid backend: " + hybrid +
                ". Supported backends: " + getSupportedBackends());
        }
    }

    /**
     * Creates a hybrid client for the specified backend.
     *
     * @param hybrid The backend type (e.g., "docling", "hancom", "azure", "google").
     * @param config The configuration for the hybrid client.
     * @return A new HybridClient instance for the specified backend.
     * @throws IllegalArgumentException If the backend type is unknown or not supported.
     * @deprecated Use {@link #getOrCreate(String, HybridConfig)} instead to reuse clients.
     */
    @Deprecated
    public static HybridClient create(String hybrid, HybridConfig config) {
        return getOrCreate(hybrid, config);
    }

    /**
     * Creates a hybrid client for the specified backend with default configuration.
     *
     * @param hybrid The backend type (e.g., "docling").
     * @return A new HybridClient instance for the specified backend.
     * @throws IllegalArgumentException If the backend type is unknown or not supported.
     * @deprecated Use {@link #getOrCreate(String, HybridConfig)} instead to reuse clients.
     */
    @Deprecated
    public static HybridClient create(String hybrid) {
        return getOrCreate(hybrid, new HybridConfig());
    }

    /**
     * Shuts down all cached clients and releases resources.
     *
     * <p>This method should be called when all processing is complete,
     * typically at the end of the CLI main method.
     */
    public static void shutdown() {
        for (HybridClient client : CLIENT_CACHE.values()) {
            if (client instanceof DoclingFastServerClient) {
                ((DoclingFastServerClient) client).shutdown();
            } else if (client instanceof HancomClient) {
                ((HancomClient) client).shutdown();
            }
        }
        CLIENT_CACHE.clear();
    }

    /**
     * Checks if a backend type is supported and implemented.
     *
     * @param hybrid The backend type to check.
     * @return true if the backend is supported and implemented, false otherwise.
     */
    public static boolean isSupported(String hybrid) {
        if (hybrid == null || hybrid.isEmpty()) {
            return false;
        }

        String lowerHybrid = hybrid.toLowerCase();
        return BACKEND_DOCLING_FAST.equals(lowerHybrid) || BACKEND_HANCOM.equals(lowerHybrid);
    }

    /**
     * Gets a comma-separated list of supported backend types.
     *
     * @return A string listing all supported backends.
     */
    public static String getSupportedBackends() {
        return String.join(", ", BACKEND_DOCLING_FAST, BACKEND_HANCOM);
    }

    /**
     * Gets a comma-separated list of all known backend types (including not yet implemented).
     *
     * @return A string listing all known backends.
     */
    public static String getAllKnownBackends() {
        return String.join(", ", BACKEND_DOCLING_FAST, BACKEND_HANCOM, BACKEND_AZURE, BACKEND_GOOGLE);
    }
}

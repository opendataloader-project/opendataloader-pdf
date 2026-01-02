/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HybridClientFactory.
 */
class HybridClientFactoryTest {

    @Test
    void testCreateDoclingClient() {
        HybridConfig config = new HybridConfig();
        HybridClient client = HybridClientFactory.create("docling", config);

        assertNotNull(client);
        assertInstanceOf(DoclingClient.class, client);

        // Cleanup
        ((DoclingClient) client).shutdown();
    }

    @Test
    void testCreateDoclingClientWithDefaultConfig() {
        HybridClient client = HybridClientFactory.create("docling");

        assertNotNull(client);
        assertInstanceOf(DoclingClient.class, client);

        // Cleanup
        ((DoclingClient) client).shutdown();
    }

    @Test
    void testCreateDoclingClientCaseInsensitive() {
        HybridConfig config = new HybridConfig();

        HybridClient client1 = HybridClientFactory.create("DOCLING", config);
        assertInstanceOf(DoclingClient.class, client1);
        ((DoclingClient) client1).shutdown();

        HybridClient client2 = HybridClientFactory.create("Docling", config);
        assertInstanceOf(DoclingClient.class, client2);
        ((DoclingClient) client2).shutdown();
    }

    @Test
    void testCreateHancomClientThrowsUnsupported() {
        HybridConfig config = new HybridConfig();

        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> HybridClientFactory.create("hancom", config)
        );

        assertTrue(exception.getMessage().contains("not yet implemented"));
    }

    @Test
    void testCreateAzureClientThrowsUnsupported() {
        HybridConfig config = new HybridConfig();

        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> HybridClientFactory.create("azure", config)
        );

        assertTrue(exception.getMessage().contains("not yet implemented"));
    }

    @Test
    void testCreateGoogleClientThrowsUnsupported() {
        HybridConfig config = new HybridConfig();

        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> HybridClientFactory.create("google", config)
        );

        assertTrue(exception.getMessage().contains("not yet implemented"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "invalid", "other", "pdf"})
    void testCreateUnknownBackendThrows(String backend) {
        HybridConfig config = new HybridConfig();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> HybridClientFactory.create(backend, config)
        );

        assertTrue(exception.getMessage().contains("Unknown hybrid backend"));
        assertTrue(exception.getMessage().contains(backend));
    }

    @Test
    void testCreateNullBackendThrows() {
        HybridConfig config = new HybridConfig();

        assertThrows(IllegalArgumentException.class,
            () -> HybridClientFactory.create(null, config));
    }

    @Test
    void testCreateEmptyBackendThrows() {
        HybridConfig config = new HybridConfig();

        assertThrows(IllegalArgumentException.class,
            () -> HybridClientFactory.create("", config));
    }

    @Test
    void testIsSupportedDocling() {
        assertTrue(HybridClientFactory.isSupported("docling"));
        assertTrue(HybridClientFactory.isSupported("DOCLING"));
        assertTrue(HybridClientFactory.isSupported("Docling"));
    }

    @Test
    void testIsSupportedUnsupportedBackends() {
        assertFalse(HybridClientFactory.isSupported("hancom"));
        assertFalse(HybridClientFactory.isSupported("azure"));
        assertFalse(HybridClientFactory.isSupported("google"));
        assertFalse(HybridClientFactory.isSupported("unknown"));
    }

    @Test
    void testIsSupportedNullAndEmpty() {
        assertFalse(HybridClientFactory.isSupported(null));
        assertFalse(HybridClientFactory.isSupported(""));
    }

    @Test
    void testGetSupportedBackends() {
        String supported = HybridClientFactory.getSupportedBackends();

        assertTrue(supported.contains("docling"));
    }

    @Test
    void testGetAllKnownBackends() {
        String allKnown = HybridClientFactory.getAllKnownBackends();

        assertTrue(allKnown.contains("docling"));
        assertTrue(allKnown.contains("hancom"));
        assertTrue(allKnown.contains("azure"));
        assertTrue(allKnown.contains("google"));
    }

    @Test
    void testBackendConstants() {
        assertEquals("docling", HybridClientFactory.BACKEND_DOCLING);
        assertEquals("hancom", HybridClientFactory.BACKEND_HANCOM);
        assertEquals("azure", HybridClientFactory.BACKEND_AZURE);
        assertEquals("google", HybridClientFactory.BACKEND_GOOGLE);
    }
}

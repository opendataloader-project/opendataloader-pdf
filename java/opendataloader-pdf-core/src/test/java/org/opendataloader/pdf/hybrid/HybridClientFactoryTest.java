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
    void testCreateDoclingFastClient() {
        HybridConfig config = new HybridConfig();
        HybridClient client = HybridClientFactory.create("docling-fast", config);

        assertNotNull(client);
        assertInstanceOf(DoclingFastServerClient.class, client);

        // Cleanup
        ((DoclingFastServerClient) client).shutdown();
    }

    @Test
    void testCreateDoclingFastClientCaseInsensitive() {
        HybridConfig config = new HybridConfig();

        HybridClient client1 = HybridClientFactory.create("DOCLING-FAST", config);
        assertInstanceOf(DoclingFastServerClient.class, client1);
        ((DoclingFastServerClient) client1).shutdown();

        HybridClient client2 = HybridClientFactory.create("Docling-Fast", config);
        assertInstanceOf(DoclingFastServerClient.class, client2);
        ((DoclingFastServerClient) client2).shutdown();
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
    @ValueSource(strings = {"unknown", "invalid", "other", "pdf", "docling"})
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
    void testIsSupportedDoclingFast() {
        assertTrue(HybridClientFactory.isSupported("docling-fast"));
        assertTrue(HybridClientFactory.isSupported("DOCLING-FAST"));
        assertTrue(HybridClientFactory.isSupported("Docling-Fast"));
    }

    @Test
    void testIsSupportedUnsupportedBackends() {
        assertFalse(HybridClientFactory.isSupported("docling"));
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

        assertTrue(supported.contains("docling-fast"));
        assertFalse(supported.contains("docling,"));
    }

    @Test
    void testGetAllKnownBackends() {
        String allKnown = HybridClientFactory.getAllKnownBackends();

        assertTrue(allKnown.contains("docling-fast"));
        assertTrue(allKnown.contains("hancom"));
        assertTrue(allKnown.contains("azure"));
        assertTrue(allKnown.contains("google"));
    }

    @Test
    void testBackendConstants() {
        assertEquals("docling-fast", HybridClientFactory.BACKEND_DOCLING_FAST);
        assertEquals("hancom", HybridClientFactory.BACKEND_HANCOM);
        assertEquals("azure", HybridClientFactory.BACKEND_AZURE);
        assertEquals("google", HybridClientFactory.BACKEND_GOOGLE);
    }
}

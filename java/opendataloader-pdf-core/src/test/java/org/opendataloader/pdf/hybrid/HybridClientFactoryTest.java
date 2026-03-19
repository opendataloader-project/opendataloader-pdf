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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for HybridClientFactory.
 */
class HybridClientFactoryTest {

    @AfterEach
    void tearDown() {
        HybridClientFactory.shutdown();
    }

    @Test
    void testCreateDoclingFastClient() {
        HybridConfig config = new HybridConfig();
        HybridClient client = HybridClientFactory.create("docling-fast", config);

        assertNotNull(client);
        assertInstanceOf(DoclingFastServerClient.class, client);
    }

    @Test
    void testCreateDoclingFastClientCaseInsensitive() {
        HybridConfig config = new HybridConfig();

        HybridClient client1 = HybridClientFactory.create("DOCLING-FAST", config);
        assertInstanceOf(DoclingFastServerClient.class, client1);

        HybridClient client2 = HybridClientFactory.create("Docling-Fast", config);
        assertInstanceOf(DoclingFastServerClient.class, client2);
        assertSame(client1, client2);
    }

    @Test
    void testCreateHancomClient() {
        HybridConfig config = new HybridConfig();
        HybridClient client = HybridClientFactory.create("hancom", config);

        assertNotNull(client);
        assertInstanceOf(HancomClient.class, client);
    }

    @Test
    void testCreateHancomClientCaseInsensitive() {
        HybridConfig config = new HybridConfig();

        HybridClient client1 = HybridClientFactory.create("HANCOM", config);
        assertInstanceOf(HancomClient.class, client1);

        HybridClient client2 = HybridClientFactory.create("Hancom", config);
        assertInstanceOf(HancomClient.class, client2);
        assertSame(client1, client2);
    }

    @Test
    void testGetOrCreateReusesClientForSameDoclingConfig() {
        HybridConfig config1 = new HybridConfig();
        config1.setUrl("http://localhost:5002");

        HybridConfig config2 = new HybridConfig();
        config2.setUrl("http://localhost:5002/");

        HybridClient client1 = HybridClientFactory.getOrCreate("docling-fast", config1);
        HybridClient client2 = HybridClientFactory.getOrCreate("docling-fast", config2);

        assertSame(client1, client2, "Equivalent docling URLs should reuse the same cached client");
        assertEquals("http://localhost:5002", ((DoclingFastServerClient) client1).getBaseUrl());
    }

    @Test
    void testGetOrCreateCreatesDifferentDoclingClientsForDifferentUrls() {
        HybridConfig config1 = new HybridConfig();
        config1.setUrl("http://localhost:5002");

        HybridConfig config2 = new HybridConfig();
        config2.setUrl("http://localhost:5003");

        HybridClient client1 = HybridClientFactory.getOrCreate("docling-fast", config1);
        HybridClient client2 = HybridClientFactory.getOrCreate("docling-fast", config2);

        assertNotSame(client1, client2, "Different docling URLs must not share the same cached client");
        assertEquals("http://localhost:5002", ((DoclingFastServerClient) client1).getBaseUrl());
        assertEquals("http://localhost:5003", ((DoclingFastServerClient) client2).getBaseUrl());
    }

    @Test
    void testGetOrCreateCreatesDifferentDoclingClientsForDifferentTimeouts() {
        HybridConfig config1 = new HybridConfig();
        config1.setUrl("http://localhost:5002");
        config1.setTimeoutMs(5_000);

        HybridConfig config2 = new HybridConfig();
        config2.setUrl("http://localhost:5002");
        config2.setTimeoutMs(10_000);

        HybridClient client1 = HybridClientFactory.getOrCreate("docling-fast", config1);
        HybridClient client2 = HybridClientFactory.getOrCreate("docling-fast", config2);

        assertNotSame(client1, client2, "Different timeouts must not reuse the same cached client");
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
    void testIsSupportedHancom() {
        assertTrue(HybridClientFactory.isSupported("hancom"));
        assertTrue(HybridClientFactory.isSupported("HANCOM"));
        assertTrue(HybridClientFactory.isSupported("Hancom"));
    }

    @Test
    void testIsSupportedUnsupportedBackends() {
        assertFalse(HybridClientFactory.isSupported("docling"));
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
        assertTrue(supported.contains("hancom"));
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

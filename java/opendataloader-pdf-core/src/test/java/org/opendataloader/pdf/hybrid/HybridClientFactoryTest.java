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
}

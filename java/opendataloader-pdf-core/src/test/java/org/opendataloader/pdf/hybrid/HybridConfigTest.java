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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests for HybridConfig.
 */
class HybridConfigTest {

    @Test
    void testDefaultChunkSize() {
        HybridConfig config = new HybridConfig();
        assertEquals(HybridConfig.DEFAULT_CHUNK_SIZE, config.getChunkSize());
    }

    @Test
    void testSetAndGetChunkSize() {
        HybridConfig config = new HybridConfig();
        config.setChunkSize(10);
        assertEquals(10, config.getChunkSize());
    }

    @Test
    void testSetChunkSizeOneAccepted() {
        HybridConfig config = new HybridConfig();
        config.setChunkSize(1);
        assertEquals(1, config.getChunkSize());
    }

    @Test
    void testSetChunkSizeZeroRejected() {
        HybridConfig config = new HybridConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setChunkSize(0));
    }

    @Test
    void testSetChunkSizeNegativeRejected() {
        HybridConfig config = new HybridConfig();
        assertThrows(IllegalArgumentException.class, () -> config.setChunkSize(-5));
    }
}

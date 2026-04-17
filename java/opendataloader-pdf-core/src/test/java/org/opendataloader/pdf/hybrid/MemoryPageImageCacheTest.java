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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MemoryPageImageCacheTest {

    private static BufferedImage createTestImage() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void getOrFetch_storesAndReturns() throws IOException {
        try (MemoryPageImageCache cache = new MemoryPageImageCache()) {
            AtomicInteger fetchCount = new AtomicInteger(0);
            BufferedImage img = cache.getOrFetch(0, idx -> {
                fetchCount.incrementAndGet();
                return createTestImage();
            });

            assertNotNull(img);
            assertEquals(1, fetchCount.get());

            // Second call should use cached value, not call fetcher
            BufferedImage img2 = cache.getOrFetch(0, idx -> {
                fetchCount.incrementAndGet();
                return createTestImage();
            });

            assertNotNull(img2);
            assertSame(img, img2, "Should return same cached instance");
            assertEquals(1, fetchCount.get(), "Fetcher should not be called again");
        }
    }

    @Test
    void evict_removesEntry() throws IOException {
        try (MemoryPageImageCache cache = new MemoryPageImageCache()) {
            AtomicInteger fetchCount = new AtomicInteger(0);

            cache.getOrFetch(0, idx -> {
                fetchCount.incrementAndGet();
                return createTestImage();
            });
            assertEquals(1, fetchCount.get());

            cache.evict(0);

            // After eviction, fetcher should be called again
            cache.getOrFetch(0, idx -> {
                fetchCount.incrementAndGet();
                return createTestImage();
            });
            assertEquals(2, fetchCount.get(), "Fetcher should be called again after eviction");
        }
    }

    @Test
    void evict_nonExistentPage_noError() throws IOException {
        try (MemoryPageImageCache cache = new MemoryPageImageCache()) {
            assertDoesNotThrow(() -> cache.evict(99));
        }
    }

    @Test
    void close_clearsAllEntries() throws IOException {
        MemoryPageImageCache cache = new MemoryPageImageCache();
        AtomicInteger fetchCount = new AtomicInteger(0);

        cache.getOrFetch(0, idx -> { fetchCount.incrementAndGet(); return createTestImage(); });
        cache.getOrFetch(1, idx -> { fetchCount.incrementAndGet(); return createTestImage(); });
        assertEquals(2, fetchCount.get());

        cache.close();

        // After close, fetcher should be called again for both pages
        cache.getOrFetch(0, idx -> { fetchCount.incrementAndGet(); return createTestImage(); });
        cache.getOrFetch(1, idx -> { fetchCount.incrementAndGet(); return createTestImage(); });
        assertEquals(4, fetchCount.get(), "Fetcher should be called again after close");
    }
}

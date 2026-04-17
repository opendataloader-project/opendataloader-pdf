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
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DiskPageImageCacheTest {

    private static BufferedImage createTestImage() {
        return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
    }

    @Test
    void getOrFetch_writesToDisk(@TempDir Path tempDir) throws IOException {
        try (DiskPageImageCache cache = new DiskPageImageCache(tempDir)) {
            AtomicInteger fetchCount = new AtomicInteger(0);

            BufferedImage img = cache.getOrFetch(0, idx -> {
                fetchCount.incrementAndGet();
                return createTestImage();
            });

            assertNotNull(img);
            assertEquals(1, fetchCount.get());
            assertTrue(Files.exists(tempDir.resolve("page-0.png")), "PNG file should exist on disk");
        }
    }

    @Test
    void getOrFetch_secondCallReadesFromDisk(@TempDir Path tempDir) throws IOException {
        try (DiskPageImageCache cache = new DiskPageImageCache(tempDir)) {
            AtomicInteger fetchCount = new AtomicInteger(0);

            cache.getOrFetch(0, idx -> {
                fetchCount.incrementAndGet();
                return createTestImage();
            });
            assertEquals(1, fetchCount.get());

            // Second call should read from disk, not call fetcher
            BufferedImage img2 = cache.getOrFetch(0, idx -> {
                fetchCount.incrementAndGet();
                return createTestImage();
            });

            assertNotNull(img2);
            assertEquals(1, fetchCount.get(), "Fetcher should not be called on second access");
        }
    }

    @Test
    void evict_isNoOp(@TempDir Path tempDir) throws IOException {
        try (DiskPageImageCache cache = new DiskPageImageCache(tempDir)) {
            cache.getOrFetch(0, idx -> createTestImage());
            cache.evict(0);

            assertTrue(Files.exists(tempDir.resolve("page-0.png")),
                "File should still exist after evict (no-op)");
        }
    }

    @Test
    void close_deletesTempFiles(@TempDir Path tempDir) throws IOException {
        // Create a subdirectory inside tempDir to simulate real usage
        Path cacheDir = tempDir.resolve("odl-cache");
        Files.createDirectory(cacheDir);

        DiskPageImageCache cache = new DiskPageImageCache(cacheDir);
        cache.getOrFetch(0, idx -> createTestImage());
        cache.getOrFetch(1, idx -> createTestImage());

        assertTrue(Files.exists(cacheDir.resolve("page-0.png")));
        assertTrue(Files.exists(cacheDir.resolve("page-1.png")));

        cache.close();

        assertFalse(Files.exists(cacheDir.resolve("page-0.png")), "Files should be deleted");
        assertFalse(Files.exists(cacheDir.resolve("page-1.png")), "Files should be deleted");
        assertFalse(Files.exists(cacheDir), "Directory should be deleted");
    }
}

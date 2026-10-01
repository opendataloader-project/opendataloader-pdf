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

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Cache for page images (pdf2img results). Implementations control where
 * images are stored and when they are evicted.
 */
public interface PageImageCache extends AutoCloseable {

    /**
     * Get a cached page image or fetch it using the supplier.
     *
     * @param pageIndex 0-indexed page number
     * @param fetcher called if image not in cache; may throw IOException
     * @return the page image, never null
     * @throws IOException if the fetcher fails
     */
    BufferedImage getOrFetch(int pageIndex, PageImageFetcher fetcher) throws IOException;

    /**
     * Hint that this page is no longer needed.
     * Memory impl evicts immediately; disk impl keeps for potential re-read.
     *
     * @param pageIndex 0-indexed page number
     */
    void evict(int pageIndex);

    /**
     * Clean up all resources. Disk impl deletes temp files.
     */
    @Override
    void close() throws IOException;

    /**
     * Functional interface for fetching a page image on cache miss.
     */
    @FunctionalInterface
    interface PageImageFetcher {
        BufferedImage fetch(int pageIndex) throws IOException;
    }
}

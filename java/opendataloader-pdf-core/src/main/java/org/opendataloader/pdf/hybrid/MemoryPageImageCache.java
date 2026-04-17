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
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory page image cache. Stores images in a HashMap; evict() removes
 * entries so GC can reclaim memory (~25MB per page image).
 */
public class MemoryPageImageCache implements PageImageCache {

    private final Map<Integer, BufferedImage> cache = new HashMap<>();

    @Override
    public BufferedImage getOrFetch(int pageIndex, PageImageFetcher fetcher) throws IOException {
        BufferedImage image = cache.get(pageIndex);
        if (image == null) {
            image = fetcher.fetch(pageIndex);
            cache.put(pageIndex, image);
        }
        return image;
    }

    @Override
    public void evict(int pageIndex) {
        cache.remove(pageIndex);
    }

    @Override
    public void close() {
        cache.clear();
    }
}

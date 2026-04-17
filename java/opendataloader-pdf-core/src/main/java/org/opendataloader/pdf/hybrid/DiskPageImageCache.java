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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Disk-backed page image cache. Stores page images as PNG files in a
 * temporary directory. evict() is a no-op (keeps files for potential re-read).
 * close() deletes the temp directory and all files.
 */
public class DiskPageImageCache implements PageImageCache {

    private static final Logger LOGGER = Logger.getLogger(DiskPageImageCache.class.getCanonicalName());

    private final Path tempDir;

    public DiskPageImageCache() throws IOException {
        this.tempDir = Files.createTempDirectory("odl-pages-");
    }

    // Visible for testing
    DiskPageImageCache(Path tempDir) {
        this.tempDir = tempDir;
    }

    @Override
    public BufferedImage getOrFetch(int pageIndex, PageImageFetcher fetcher) throws IOException {
        Path file = tempDir.resolve("page-" + pageIndex + ".png");
        if (Files.exists(file)) {
            return ImageIO.read(file.toFile());
        }
        BufferedImage image = fetcher.fetch(pageIndex);
        ImageIO.write(image, "png", file.toFile());
        return image;
    }

    @Override
    public void evict(int pageIndex) {
        // no-op: keep on disk for potential re-read
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(tempDir)) {
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempDir)) {
            for (Path entry : stream) {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to delete temp file: {0}", entry);
                }
            }
        }
        Files.deleteIfExists(tempDir);
    }

    /**
     * Returns the temp directory path (for testing).
     */
    Path getTempDir() {
        return tempDir;
    }
}

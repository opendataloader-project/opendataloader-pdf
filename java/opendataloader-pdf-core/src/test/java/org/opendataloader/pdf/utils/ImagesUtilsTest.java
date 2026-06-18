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
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ImagesUtilsTest {

    @Test
    void testCreateImagesDirectory() throws IOException {
        StaticLayoutContainers.clearContainers();
        // Given
        Path tempDir = Files.createTempDirectory("test");
        File testPdf = new File("../../samples/pdf/lorem.pdf");
        String outputFolder = tempDir.toString();

        // When
        try {
            Path path = Paths.get(testPdf.getPath());
            StaticLayoutContainers.setImagesDirectory(outputFolder + File.separator + path.getFileName().toString().substring(0, path.getFileName().toString().length() - 4) + "_images");
            ImagesUtils imagesUtils = new ImagesUtils();
            imagesUtils.createImagesDirectory(StaticLayoutContainers.getImagesDirectory());
            // Then - verify images directory was created in createImagesDirectory()
            String expectedImagesDirName = testPdf.getName().substring(0, testPdf.getName().length() - 4) + "_images";
            Path expectedImagesPath = Path.of(outputFolder, expectedImagesDirName);

            assertTrue(Files.exists(expectedImagesPath), "Images directory should be created in constructor");
            assertTrue(Files.isDirectory(expectedImagesPath), "Images path should be a directory");
        } finally {
            // Cleanup
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    @Test
    void testWriteImageInitializesContrastRatioConsumer() throws IOException {
        StaticLayoutContainers.clearContainers();
        // Given
        Path tempDir = Files.createTempDirectory("htmlgen-test");
        File testPdf = new File("../../samples/pdf/lorem.pdf");
        String outputFolder = tempDir.toString();
        // When
        try {
            // Then - if ContrastRatioConsumer wasn't initialized,
            // it would be null and cause NPE when used
            Path path = Paths.get(testPdf.getAbsolutePath());
            ImagesUtils imagesUtils = new ImagesUtils();
            // Issue #458 hotfix: ImagesUtils no longer keeps a local ContrastRatioConsumer
            // field/getter. Verify the consumer is created in StaticLayoutContainers'
            // ThreadLocal on first writeImage call (same observable semantic as before).
            StaticLayoutContainers.setImagesDirectory(outputFolder + File.separator + path.getFileName().toString().substring(0, path.getFileName().toString().length() - 4) + "_images");
            ImageChunk imageChunk = new ImageChunk(new BoundingBox(0));
            // Initializing contrastRatioConsumer inside writeImage() via StaticLayoutContainers.
            imagesUtils.writeImage(imageChunk, testPdf.getAbsolutePath(),"");
            // After writeImage, StaticLayoutContainers must hold a non-null cached consumer.
            // Use getCachedContrastRatioConsumer to verify the cached state without invoking the initializer.
            assertNotNull(StaticLayoutContainers.getCachedContrastRatioConsumer(),
                    "writeImage should populate the StaticLayoutContainers ContrastRatioConsumer ThreadLocal");
            // Verify file was created
            Path pngPath = Path.of(StaticLayoutContainers.getImagesDirectory(), "imageFile1.png");
            // PNG file is created
            assertTrue(Files.exists(pngPath), "PNG file created successfully");
        } finally {
            // Cleanup
            StaticLayoutContainers.closeContrastRatioConsumer();
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    /**
     * Regression guard for issue #458 (OOM).
     *
     * After writing a BufferedImage to disk (or to the in-memory embed cache),
     * ImagesUtils MUST invoke {@link BufferedImage#flush()} so the underlying
     * raster data and any cached resources become eligible for GC.
     * Otherwise large images accumulate on the heap and trigger
     * {@code java.lang.OutOfMemoryError: Java heap space}.
     *
     * Verifies the contract via a package-private seam
     * {@code writeBufferedImageToFile(BufferedImage, String, String)}.
     */
    @Test
    void writeBufferedImageToFile_callsFlushOnSuccess() throws IOException {
        StaticLayoutContainers.clearContainers();
        Path tempDir = Files.createTempDirectory("flush-test");
        try {
            AtomicInteger flushCalls = new AtomicInteger(0);
            BufferedImage tracking = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB) {
                @Override
                public void flush() {
                    flushCalls.incrementAndGet();
                    super.flush();
                }
            };
            String fileName = tempDir.resolve("flush_image.png").toString();

            ImagesUtils imagesUtils = new ImagesUtils();
            imagesUtils.writeBufferedImageToFile(tracking, fileName, "png");

            assertEquals(1, flushCalls.get(),
                "BufferedImage.flush() must be invoked exactly once after writing to release raster memory (issue #458)");
            assertTrue(Files.exists(Path.of(fileName)), "PNG file should be written");
        } finally {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    /**
     * Regression guard for issue #458 (OOM).
     *
     * If the underlying {@link javax.imageio.ImageIO#write} call throws
     * an IOException, {@link BufferedImage#flush()} MUST still be invoked
     * (try-finally semantics) — otherwise an error path leaks raster memory.
     */
    @Test
    void writeBufferedImageToFile_callsFlushEvenOnIoError() throws IOException {
        StaticLayoutContainers.clearContainers();
        Path tempDir = Files.createTempDirectory("flush-test-err");
        try {
            AtomicInteger flushCalls = new AtomicInteger(0);
            BufferedImage tracking = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB) {
                @Override
                public void flush() {
                    flushCalls.incrementAndGet();
                    super.flush();
                }
            };
            // Unsupported format triggers ImageIO.write to return false / NPE / IOException paths;
            // additionally, target file path points to a non-writable location via an unsupported format
            // and a bogus directory to force a write failure.
            String fileName = tempDir.resolve("does/not/exist/img.png").toString();

            ImagesUtils imagesUtils = new ImagesUtils();
            // Should not throw, but MUST still flush.
            imagesUtils.writeBufferedImageToFile(tracking, fileName, "png");

            assertEquals(1, flushCalls.get(),
                "BufferedImage.flush() must be invoked even when ImageIO.write fails (issue #458)");
        } finally {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    /**
     * Regression guard for issue #458 (OOM) — embedded mode.
     *
     * The {@link ImagesUtils#writeBufferedImageToFile} contract must hold
     * symmetrically for the {@code isEmbedImages() == true} branch:
     * the image is encoded into an in-memory buffer (no file is written)
     * and {@link BufferedImage#flush()} must still be invoked exactly once.
     * Without this, embedded-mode runs accumulate raster memory just like
     * the disk-mode branch did before the hotfix.
     */
    @Test
    void writeBufferedImageToFile_callsFlushInEmbeddedMode() throws IOException {
        StaticLayoutContainers.clearContainers();
        try {
            StaticLayoutContainers.setEmbedImages(true);
            AtomicInteger flushCalls = new AtomicInteger(0);
            BufferedImage tracking = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB) {
                @Override
                public void flush() {
                    flushCalls.incrementAndGet();
                    super.flush();
                }
            };
            String fileName = "embedded/page1_image1.png";

            ImagesUtils imagesUtils = new ImagesUtils();
            imagesUtils.writeBufferedImageToFile(tracking, fileName, "png");

            assertEquals(1, flushCalls.get(),
                "BufferedImage.flush() must also be invoked in embedded mode (issue #458)");
            assertTrue(StaticLayoutContainers.hasEmbeddedImageBytes(fileName),
                "Embedded mode must populate the in-memory bytes cache instead of writing to disk");
            assertNotNull(StaticLayoutContainers.getEmbeddedImageBytes(fileName),
                "Embedded bytes must be retrievable from the cache");
        } finally {
            StaticLayoutContainers.clearContainers();
        }
    }

    /**
     * Regression guard for issue #458 (OOM) — directory-init sentinel.
     *
     * After M2, ImagesUtils uses an explicit {@code imagesDirectoryInitialized}
     * boolean instead of piggy-backing on the (now removed)
     * {@code contrastRatioConsumer} field to decide whether to call
     * {@link ImagesUtils#createImagesDirectory(String)}. The sentinel must
     * fire exactly once regardless of which entry point — {@code writeImage}
     * or {@code writePicture} — comes first, so that downstream image writes
     * never race on missing directories and {@code mkdirs} is not called
     * repeatedly across pages.
     *
     * We verify by subclassing ImagesUtils to count {@code createImagesDirectory}
     * invocations and calling the package-private {@code ensureImagesDirectoryInitialized}
     * twice in succession.
     */
    @Test
    void ensureImagesDirectoryInitialized_createsDirectoryExactlyOnce() throws Exception {
        StaticLayoutContainers.clearContainers();
        Path tempDir = Files.createTempDirectory("sentinel-test");
        try {
            String imagesDir = tempDir.resolve("doc_images").toString();
            StaticLayoutContainers.setImagesDirectory(imagesDir);

            AtomicInteger createCalls = new AtomicInteger(0);
            ImagesUtils imagesUtils = new ImagesUtils() {
                @Override
                public void createImagesDirectory(String path) {
                    createCalls.incrementAndGet();
                    super.createImagesDirectory(path);
                }
            };

            // Invoke the sentinel twice via reflection on the package-private helper,
            // simulating writeImage followed by writePicture (or vice versa).
            java.lang.reflect.Method ensure =
                ImagesUtils.class.getDeclaredMethod("ensureImagesDirectoryInitialized");
            ensure.setAccessible(true);
            ensure.invoke(imagesUtils);
            ensure.invoke(imagesUtils);

            assertEquals(1, createCalls.get(),
                "createImagesDirectory must be called exactly once per ImagesUtils instance regardless of how many writeImage/writePicture calls follow (issue #458 — M2 sentinel)");
            assertTrue(Files.isDirectory(Path.of(imagesDir)),
                "Images directory should have been materialised on disk");
        } finally {
            StaticLayoutContainers.clearContainers();
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }

    /**
     * Regression guard for issue #458 (OOM).
     *
     * After {@link ImagesUtils#writeBufferedImageToFile} returns, the
     * {@link BufferedImage} MUST not be retained by the ImagesUtils
     * instance (no strong reference from a field). We verify this by
     * holding only a {@link WeakReference} to the image, dropping the
     * strong local reference, hinting the GC, and asserting the
     * referent has been collected.
     *
     * Without M2 (removal of the {@code contrastRatioConsumer} field) /
     * without M0 ({@code flush()} in finally), the BufferedImage's
     * underlying raster would remain reachable through the cached
     * {@code ContrastRatioConsumer} and accumulate across pages.
     */
    @Test
    // The `image = null` below is a deliberate, load-bearing assignment: it drops the
    // only strong reference so the WeakReference can be collected. PMD flags it as an
    // UnusedAssignment, but removing it would defeat the GC check, so suppress here.
    @SuppressWarnings("PMD.UnusedAssignment")
    void writeBufferedImageToFile_doesNotRetainImageReference() throws IOException {
        StaticLayoutContainers.clearContainers();
        Path tempDir = Files.createTempDirectory("retain-test");
        try {
            // Structural assertion: ImagesUtils MUST NOT have a contrastRatioConsumer field.
            // A regression that re-introduces this field would cause the GC test to pass
            // spuriously (weak-ref collected) while still accumulating BufferedImages
            // in the ContrastRatioConsumer's internal cache across pages.
            java.lang.reflect.Field[] fields = ImagesUtils.class.getDeclaredFields();
            for (java.lang.reflect.Field field : fields) {
                assertFalse(field.getName().equals("contrastRatioConsumer"),
                    "ImagesUtils MUST NOT declare a 'contrastRatioConsumer' field (issue #458 — M2 regression guard)");
                assertFalse(field.getType().getSimpleName().equals("ContrastRatioConsumer"),
                    "ImagesUtils MUST NOT declare a field of type ContrastRatioConsumer (issue #458 — M2 regression guard)");
            }

            String fileName = tempDir.resolve("retained.png").toString();
            ImagesUtils imagesUtils = new ImagesUtils();

            WeakReference<BufferedImage> weakRef;
            {
                BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
                weakRef = new WeakReference<>(image);
                imagesUtils.writeBufferedImageToFile(image, fileName, "png");
                // Drop the only strong reference held by this test frame.
                image = null;
            }

            // Coax the GC. We can't guarantee immediate collection, but a few
            // explicit nudges are sufficient for a freshly allocated 4 KB image.
            boolean collected = false;
            for (int i = 0; i < 20; i++) {
                System.gc();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (weakRef.get() == null) {
                    collected = true;
                    break;
                }
            }

            assertTrue(collected,
                "BufferedImage written via writeBufferedImageToFile must be GC-eligible " +
                "after the call returns (issue #458 — no field/static retention)");
        } finally {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        }
    }
}

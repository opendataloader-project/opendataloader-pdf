/*
 * Copyright 2025 Hancom Inc.
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            assertNull(imagesUtils.getContrastRatioConsumer());
            StaticLayoutContainers.setImagesDirectory(outputFolder + File.separator + path.getFileName().toString().substring(0, path.getFileName().toString().length() - 4) + "_images");
            ImageChunk imageChunk = new ImageChunk(new BoundingBox(0));
            // Initializing contrastRatioConsumer in writeImage()
            imagesUtils.writeImage(imageChunk, testPdf.getAbsolutePath(),"");
            assertNotNull(imagesUtils.getContrastRatioConsumer());
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
}

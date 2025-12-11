/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
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
            ImagesUtils.createImagesDirectory(StaticLayoutContainers.getImagesDirectory());
            // Then - verify figures directory was created in createImagesDirectory()
            String expectedFiguresDirName = testPdf.getName().substring(0, testPdf.getName().length() - 4) + "_images";
            Path expectedFiguresPath = Path.of(outputFolder, expectedFiguresDirName);

            assertTrue(Files.exists(expectedFiguresPath), "Figures directory should be created in constructor");
            assertTrue(Files.isDirectory(expectedFiguresPath), "Figures path should be a directory");
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
        File testPdf = new File("../../samples/pdf/images-test.pdf");
        String outputFolder = tempDir.toString();
        // When
        try {
            // Then - if ContrastRatioConsumer wasn't initialized,
            // it would be null and cause NPE when used
            Path path = Paths.get(testPdf.getAbsolutePath());
            assertNull(ImagesUtils.getContrastRatioConsumer());
            StaticLayoutContainers.setImagesDirectory(outputFolder + File.separator + path.getFileName().toString().substring(0, path.getFileName().toString().length() - 4) + "_images");
            ImageChunk imageChunk = new ImageChunk(new BoundingBox(0));
            // Initializing contrastRatioConsumer in writeImage()
            ImagesUtils.writeImage(imageChunk, testPdf.getAbsolutePath(),"");
            assertNotNull(ImagesUtils.getContrastRatioConsumer());
            // Verify file was created
            Path htmlPath = Path.of(StaticLayoutContainers.getImagesDirectory(), "imageFile1.png");
            // PNG file is created
            assertTrue(Files.exists(htmlPath), "HtmlGenerator initialized successfully");
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
}

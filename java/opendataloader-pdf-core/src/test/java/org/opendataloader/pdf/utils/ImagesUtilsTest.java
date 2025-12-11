/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.wcag.algorithms.entities.IObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

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
            StaticLayoutContainers.setImagesDirectory(path.getFileName().toString().substring(0, path.getFileName().toString().length() - 4) + "_images");
            ImagesUtils.createImagesDirectory(outputFolder + File.separator + StaticLayoutContainers.getImagesDirectory());
            // Then - verify figures directory was created in writeImage()
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
        String s = testPdf.getAbsolutePath();

        // When
        try {
            // Then - if ContrastRatioConsumer wasn't initialized,
            // it would be null and cause NPE when used
            assertNull(ImagesUtils.contrastRatioConsumer);
            StaticLayoutContainers.resetImageIndex();
            Config config = new Config();
            config.setGenerateHtml(true);
            config.setOutputFolder("../../samples/temp");
            // Initializing contrastRatioConsumer in writeImage()
            DocumentProcessor.processFile(testPdf.getAbsolutePath(), config);
            assertNotNull(ImagesUtils.contrastRatioConsumer);
            String outputFolder = config.getOutputFolder();
            // Verify HTML writer was created
            Path htmlPath = Path.of(outputFolder, "images-test.html");
            // HTML file is created by FileWriter in constructor but is empty until writeToHtml
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

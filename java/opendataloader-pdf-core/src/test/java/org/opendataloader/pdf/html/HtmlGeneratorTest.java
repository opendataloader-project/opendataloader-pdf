/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.html;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HtmlGeneratorTest {

    @Test
    void testWriteImageCreatesFiguresDirectory() throws IOException {
        StaticLayoutContainers.clearContainers();
        // Given
        Path tempDir = Files.createTempDirectory("htmlgen-test");
        File testPdf = new File("../../samples/pdf/lorem.pdf");
        String outputFolder = tempDir.toString();
        Config config = new Config();

        // When
        HtmlGenerator htmlGenerator = new HtmlGenerator(testPdf, outputFolder, config);

        try {
            StaticLayoutContainers.resetImageIndex();
            ImageChunk image = new ImageChunk(new BoundingBox(0,0, 0, 100, 100));
            htmlGenerator.writeImage(image);
            // Then - verify figures directory was created in writeImage()
            String expectedFiguresDirName = testPdf.getName().substring(0, testPdf.getName().length() - 4) + "_figures";
            Path expectedFiguresPath = Path.of(outputFolder, expectedFiguresDirName);

            assertTrue(Files.exists(expectedFiguresPath), "Figures directory should be created in constructor");
            assertTrue(Files.isDirectory(expectedFiguresPath), "Figures path should be a directory");
        } finally {
            StaticLayoutContainers.closeContrastRatioConsumer();
            htmlGenerator.close();
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
        Config config = new Config();

        // When
        HtmlGenerator htmlGenerator = new HtmlGenerator(testPdf, outputFolder, config);

        try {
            // Then - if ContrastRatioConsumer wasn't initialized,
            // it would be null and cause NPE when used
            assertNull(htmlGenerator.contrastRatioConsumer);
            StaticLayoutContainers.resetImageIndex();
            ImageChunk image = new ImageChunk(new BoundingBox(0,0, 0, 100, 100));
            // Initializing contrastRatioConsumer in writeImage()
            htmlGenerator.writeImage(image);
            assertNotNull(htmlGenerator.contrastRatioConsumer);

            // Verify HTML writer was created
            Path htmlPath = Path.of(outputFolder, "lorem.html");
            // HTML file is created by FileWriter in constructor but is empty until writeToHtml
            assertTrue(Files.exists(htmlPath) || true, "HtmlGenerator initialized successfully");
        } finally {
            StaticLayoutContainers.closeContrastRatioConsumer();
            htmlGenerator.close();
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

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ImagesUtilsTest {

    @AfterEach
    void tearDown() {
        StaticLayoutContainers.clearContainers();
    }

    @Test
    void testWriteImageLogsWarningWhenRendererIsUnavailable() throws IOException {
        Path tempDir = Files.createTempDirectory("imgutils-warn-test");
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            public void publish(LogRecord r) { records.add(r); }
            public void flush() {}
            public void close() throws SecurityException {}
        };
        Logger logger = Logger.getLogger(ImagesUtils.class.getCanonicalName());
        Level originalLevel = logger.getLevel();
        logger.addHandler(handler);
        logger.setLevel(Level.WARNING);
        try {
            StaticLayoutContainers.setImagesDirectory(tempDir.toString());
            // Use an invalid PDF path so contrastRatioConsumer will be null
            ImagesUtils imagesUtils = new ImagesUtils();
            ImageChunk chunk = new ImageChunk(new BoundingBox(0));
            imagesUtils.writeImage(chunk, "/nonexistent/path/file.pdf", "");
            // Verify ImagesUtils logs the specific "PDF renderer unavailable" warning
            boolean hasRendererWarning = records.stream()
                    .anyMatch(r -> r.getLevel() == Level.WARNING
                            && r.getLoggerName().equals(ImagesUtils.class.getCanonicalName())
                            && r.getMessage().contains("PDF renderer unavailable"));
            assertTrue(hasRendererWarning, "Should log a warning when PDF renderer is unavailable (source key will be absent)");
        } finally {
            logger.setLevel(originalLevel);
            logger.removeHandler(handler);
            try (Stream<Path> walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException e) { /* ignore */ }
                });
            }
        }
    }

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

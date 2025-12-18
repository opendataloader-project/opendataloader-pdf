/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for image format support in Java ImageIO.
 * This test verifies which image formats can actually be written by the JVM.
 */
class ImageFormatSupportTest {

    @TempDir
    Path tempDir;

    /**
     * Creates a simple test image for format testing.
     */
    private BufferedImage createTestImage() {
        BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setColor(Color.RED);
        g2d.fillRect(0, 0, 50, 50);
        g2d.setColor(Color.BLUE);
        g2d.fillRect(50, 0, 50, 50);
        g2d.setColor(Color.GREEN);
        g2d.fillRect(0, 50, 50, 50);
        g2d.setColor(Color.YELLOW);
        g2d.fillRect(50, 50, 50, 50);
        g2d.dispose();
        return image;
    }

    @Test
    void testPngFormatIsSupported() throws IOException {
        BufferedImage testImage = createTestImage();
        File outputFile = tempDir.resolve("test.png").toFile();

        boolean result = ImageIO.write(testImage, "png", outputFile);

        assertTrue(result, "PNG format should be supported by ImageIO");
        assertTrue(outputFile.exists(), "PNG file should be created");
        assertTrue(outputFile.length() > 0, "PNG file should have content");
    }

    @Test
    void testJpegFormatIsSupported() throws IOException {
        BufferedImage testImage = createTestImage();
        File outputFile = tempDir.resolve("test.jpeg").toFile();

        boolean result = ImageIO.write(testImage, "jpeg", outputFile);

        assertTrue(result, "JPEG format should be supported by ImageIO");
        assertTrue(outputFile.exists(), "JPEG file should be created");
        assertTrue(outputFile.length() > 0, "JPEG file should have content");
    }

    @Test
    void testWebpFormatIsNotSupported() throws IOException {
        BufferedImage testImage = createTestImage();
        File outputFile = tempDir.resolve("test.webp").toFile();

        // WebP is NOT supported by default Java ImageIO
        boolean result = ImageIO.write(testImage, "webp", outputFile);

        assertFalse(result, "WebP format should NOT be supported by standard ImageIO");
    }

    @Test
    void testListAvailableWriterFormats() {
        String[] writerFormats = ImageIO.getWriterFormatNames();
        System.out.println("Available ImageIO writer formats: " + Arrays.toString(writerFormats));

        // PNG and JPEG should always be available
        assertTrue(Arrays.asList(writerFormats).contains("png"), "PNG should be available");
        assertTrue(Arrays.asList(writerFormats).contains("JPEG") || Arrays.asList(writerFormats).contains("jpeg"),
            "JPEG should be available");
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "jpg", "gif", "bmp"})
    void testStandardFormatsAreSupported(String format) throws IOException {
        BufferedImage testImage = createTestImage();
        File outputFile = tempDir.resolve("test." + format).toFile();

        boolean result = ImageIO.write(testImage, format, outputFile);

        assertTrue(result, format.toUpperCase() + " format should be supported by ImageIO");
        assertTrue(outputFile.exists(), format.toUpperCase() + " file should be created");
    }

    @Test
    void testUnsupportedFormatReturnsFalse() throws IOException {
        BufferedImage testImage = createTestImage();
        File outputFile = tempDir.resolve("test.xyz").toFile();

        boolean result = ImageIO.write(testImage, "xyz_unsupported_format", outputFile);

        assertFalse(result, "Unsupported format should return false");
    }
}

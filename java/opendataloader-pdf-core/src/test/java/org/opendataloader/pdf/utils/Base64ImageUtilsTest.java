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
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class Base64ImageUtilsTest {

    @TempDir
    Path tempDir;

    @Test
    void testToDataUri_withPngFormat() throws IOException {
        // Given
        byte[] testContent = "PNG image content".getBytes();
        File testFile = tempDir.resolve("test.png").toFile();
        Files.write(testFile.toPath(), testContent);

        // When
        String dataUri = Base64ImageUtils.toDataUri(testFile, "png");

        // Then
        assertNotNull(dataUri);
        assertTrue(dataUri.startsWith("data:image/png;base64,"));
        String expectedBase64 = Base64.getEncoder().encodeToString(testContent);
        assertEquals("data:image/png;base64," + expectedBase64, dataUri);
    }

    @Test
    void testToDataUri_withJpegFormat() throws IOException {
        // Given
        byte[] testContent = "JPEG image content".getBytes();
        File testFile = tempDir.resolve("test.jpg").toFile();
        Files.write(testFile.toPath(), testContent);

        // When
        String dataUri = Base64ImageUtils.toDataUri(testFile, "jpeg");

        // Then
        assertNotNull(dataUri);
        assertTrue(dataUri.startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void testToDataUri_withNonExistentFile() {
        // Given
        File nonExistentFile = new File("/non/existent/file.png");

        // When
        String dataUri = Base64ImageUtils.toDataUri(nonExistentFile, "png");

        // Then
        assertNull(dataUri);
    }

    @ParameterizedTest
    @CsvSource({
        "png, image/png",
        "PNG, image/png",
        "jpeg, image/jpeg",
        "JPEG, image/jpeg",
        "jpg, image/jpeg",
        "JPG, image/jpeg"
    })
    void testGetMimeType_withValidFormats(String format, String expectedMimeType) {
        assertEquals(expectedMimeType, Base64ImageUtils.getMimeType(format));
    }

    @Test
    void testGetMimeType_withNullFormat() {
        assertEquals("image/png", Base64ImageUtils.getMimeType(null));
    }

    @Test
    void testGetMimeType_withUnknownFormat() {
        // Unknown formats default to PNG
        assertEquals("image/png", Base64ImageUtils.getMimeType("bmp"));
        assertEquals("image/png", Base64ImageUtils.getMimeType("gif"));
        assertEquals("image/png", Base64ImageUtils.getMimeType("webp"));
        assertEquals("image/png", Base64ImageUtils.getMimeType("unknown"));
    }

    @Test
    void testMaxEmbeddedImageSizeConstant() {
        // Verify the constant is 10MB
        assertEquals(10L * 1024 * 1024, Base64ImageUtils.MAX_EMBEDDED_IMAGE_SIZE);
    }

    @Test
    void testToDataUriWithImageAtSizeLimit() throws IOException {
        // Given: Create a file exactly at the size limit
        // Note: We use a smaller size for test performance (1KB instead of 10MB)
        byte[] content = new byte[1024];
        File testFile = tempDir.resolve("at_limit.png").toFile();
        Files.write(testFile.toPath(), content);

        // When
        String dataUri = Base64ImageUtils.toDataUri(testFile, "png");

        // Then: Should succeed for files under the limit
        assertNotNull(dataUri);
        assertTrue(dataUri.startsWith("data:image/png;base64,"));
    }
}

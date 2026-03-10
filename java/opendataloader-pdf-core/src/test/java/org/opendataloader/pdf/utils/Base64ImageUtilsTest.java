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

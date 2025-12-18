/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testDefaultValues() {
        Config config = new Config();

        // Verify default values
        assertFalse(config.isEmbedImages());
        assertEquals(Config.IMAGE_FORMAT_PNG, config.getImageFormat());
    }

    @Test
    void testSetEmbedImages() {
        Config config = new Config();

        config.setEmbedImages(true);
        assertTrue(config.isEmbedImages());

        config.setEmbedImages(false);
        assertFalse(config.isEmbedImages());
    }

    @Test
    void testSetImageFormat() {
        Config config = new Config();

        config.setImageFormat("jpeg");
        assertEquals("jpeg", config.getImageFormat());

        config.setImageFormat("png");
        assertEquals("png", config.getImageFormat());
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "PNG", "jpeg", "JPEG"})
    void testIsValidImageFormat_withValidFormats(String format) {
        assertTrue(Config.isValidImageFormat(format));
    }

    @ParameterizedTest
    @ValueSource(strings = {"bmp", "gif", "tiff", "webp", "invalid", ""})
    void testIsValidImageFormat_withInvalidFormats(String format) {
        assertFalse(Config.isValidImageFormat(format));
    }

    @Test
    void testIsValidImageFormat_withNull() {
        assertFalse(Config.isValidImageFormat(null));
    }

    @Test
    void testGetImageFormatOptions() {
        String options = Config.getImageFormatOptions(", ");

        assertTrue(options.contains("png"));
        assertTrue(options.contains("jpeg"));
        assertFalse(options.contains("webp"));
    }

    @Test
    void testImageFormatConstants() {
        assertEquals("png", Config.IMAGE_FORMAT_PNG);
        assertEquals("jpeg", Config.IMAGE_FORMAT_JPEG);
    }

    @Test
    void testSetImageFormatNormalizesToLowercase() {
        Config config = new Config();

        config.setImageFormat("PNG");
        assertEquals("png", config.getImageFormat());

        config.setImageFormat("JPEG");
        assertEquals("jpeg", config.getImageFormat());
    }

    @Test
    void testSetImageFormatWithNullDefaultsToPng() {
        Config config = new Config();

        config.setImageFormat(null);
        assertEquals("png", config.getImageFormat());
    }

    @ParameterizedTest
    @ValueSource(strings = {"bmp", "gif", "webp", "invalid"})
    void testSetImageFormatThrowsExceptionForInvalidFormat(String format) {
        Config config = new Config();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.setImageFormat(format)
        );
        assertTrue(exception.getMessage().contains("Unsupported image format"));
        assertTrue(exception.getMessage().contains(format));
    }

    // Test existing Config fields to ensure new fields don't break them
    @Test
    void testExistingConfigFields() {
        Config config = new Config();

        // Test default values
        assertTrue(config.isGenerateJSON());
        assertFalse(config.isGenerateMarkdown());
        assertFalse(config.isGenerateHtml());
        assertFalse(config.isGeneratePDF());
        assertFalse(config.isKeepLineBreaks());

        // Test setting values
        config.setGenerateJSON(false);
        assertFalse(config.isGenerateJSON());

        config.setGenerateMarkdown(true);
        assertTrue(config.isGenerateMarkdown());

        config.setGenerateHtml(true);
        assertTrue(config.isGenerateHtml());
    }
}

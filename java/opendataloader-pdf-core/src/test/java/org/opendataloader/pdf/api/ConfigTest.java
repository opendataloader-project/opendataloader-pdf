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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testDefaultValues() {
        Config config = new Config();

        // Verify default values (new defaults: external, xycut)
        assertFalse(config.isEmbedImages());
        assertFalse(config.isImageOutputOff());
        assertEquals(Config.IMAGE_OUTPUT_EXTERNAL, config.getImageOutput());
        assertEquals(Config.IMAGE_FORMAT_PNG, config.getImageFormat());
        assertEquals(Config.READING_ORDER_XYCUT, config.getReadingOrder());
    }

    @Test
    void testSetImageOutputAffectsIsEmbedImages() {
        Config config = new Config();

        config.setImageOutput(Config.IMAGE_OUTPUT_EMBEDDED);
        assertTrue(config.isEmbedImages());
        assertFalse(config.isImageOutputOff());

        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        assertFalse(config.isEmbedImages());
        assertFalse(config.isImageOutputOff());

        config.setImageOutput(Config.IMAGE_OUTPUT_OFF);
        assertFalse(config.isEmbedImages());
        assertTrue(config.isImageOutputOff());
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

    @Test
    void testSetImageOutput() {
        Config config = new Config();

        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        assertEquals(Config.IMAGE_OUTPUT_EXTERNAL, config.getImageOutput());
        assertFalse(config.isEmbedImages());

        config.setImageOutput(Config.IMAGE_OUTPUT_EMBEDDED);
        assertEquals(Config.IMAGE_OUTPUT_EMBEDDED, config.getImageOutput());
        assertTrue(config.isEmbedImages());
    }

    @ParameterizedTest
    @ValueSource(strings = {"off", "OFF", "embedded", "EMBEDDED", "external", "EXTERNAL"})
    void testIsValidImageOutput_withValidModes(String mode) {
        assertTrue(Config.isValidImageOutput(mode));
    }

    @ParameterizedTest
    @ValueSource(strings = {"base64", "file", "invalid", ""})
    void testIsValidImageOutput_withInvalidModes(String mode) {
        assertFalse(Config.isValidImageOutput(mode));
    }

    @Test
    void testGetImageOutputOptions() {
        String options = Config.getImageOutputOptions(", ");

        assertTrue(options.contains("off"));
        assertTrue(options.contains("embedded"));
        assertTrue(options.contains("external"));
    }

    @Test
    void testImageOutputConstants() {
        assertEquals("off", Config.IMAGE_OUTPUT_OFF);
        assertEquals("embedded", Config.IMAGE_OUTPUT_EMBEDDED);
        assertEquals("external", Config.IMAGE_OUTPUT_EXTERNAL);
    }

    @Test
    void testSetImageOutputNormalizesToLowercase() {
        Config config = new Config();

        config.setImageOutput("EXTERNAL");
        assertEquals("external", config.getImageOutput());

        config.setImageOutput("EMBEDDED");
        assertEquals("embedded", config.getImageOutput());
    }

    @Test
    void testSetImageOutputWithNullDefaultsToExternal() {
        Config config = new Config();

        config.setImageOutput(null);
        assertEquals(Config.IMAGE_OUTPUT_EXTERNAL, config.getImageOutput());
    }

    @ParameterizedTest
    @ValueSource(strings = {"base64", "file", "invalid"})
    void testSetImageOutputThrowsExceptionForInvalidMode(String mode) {
        Config config = new Config();

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.setImageOutput(mode)
        );
        assertTrue(exception.getMessage().contains("Unsupported image output mode"));
        assertTrue(exception.getMessage().contains(mode));
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

    // ===== Pages Option Tests =====

    @Test
    void testDefaultPages() {
        Config config = new Config();
        assertNull(config.getPages());
        assertTrue(config.getPageNumbers().isEmpty());
    }

    @Test
    void testSetPages_singlePage() {
        Config config = new Config();
        config.setPages("1");
        assertEquals("1", config.getPages());
        assertEquals(List.of(1), config.getPageNumbers());
    }

    @Test
    void testSetPages_commaSeparated() {
        Config config = new Config();
        config.setPages("1,3,5");
        assertEquals(List.of(1, 3, 5), config.getPageNumbers());
    }

    @Test
    void testSetPages_range() {
        Config config = new Config();
        config.setPages("1-5");
        assertEquals(List.of(1, 2, 3, 4, 5), config.getPageNumbers());
    }

    @Test
    void testSetPages_mixed() {
        Config config = new Config();
        config.setPages("1,3,5-7");
        assertEquals(List.of(1, 3, 5, 6, 7), config.getPageNumbers());
    }

    @Test
    void testSetPages_complexMixed() {
        Config config = new Config();
        config.setPages("1-3,5,7-9");
        assertEquals(List.of(1, 2, 3, 5, 7, 8, 9), config.getPageNumbers());
    }

    @Test
    void testSetPages_withSpaces() {
        Config config = new Config();
        config.setPages(" 1 , 3 , 5 - 7 ");
        assertEquals(List.of(1, 3, 5, 6, 7), config.getPageNumbers());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1-", "-5", "5-3", "0", "-1", "1,,3", "1-2-3", ""})
    void testSetPages_invalidFormat(String invalidPages) {
        Config config = new Config();
        if (invalidPages.isEmpty()) {
            // Empty string should not throw, just set as-is
            config.setPages(invalidPages);
            assertTrue(config.getPageNumbers().isEmpty());
        } else {
            assertThrows(IllegalArgumentException.class, () -> config.setPages(invalidPages));
        }
    }

    @Test
    void testSetPages_nullAndEmpty() {
        Config config = new Config();

        config.setPages(null);
        assertNull(config.getPages());
        assertTrue(config.getPageNumbers().isEmpty());

        config.setPages("");
        assertTrue(config.getPageNumbers().isEmpty());

        config.setPages("   ");
        assertTrue(config.getPageNumbers().isEmpty());
    }

    @Test
    void testSetPages_reverseRangeThrows() {
        Config config = new Config();
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.setPages("5-3")
        );
        assertTrue(exception.getMessage().contains("start page cannot be greater than end page"));
    }

    @Test
    void testSetPages_zeroPageThrows() {
        Config config = new Config();
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.setPages("0")
        );
        assertTrue(exception.getMessage().contains("Page numbers must be positive"));
    }

    @Test
    void testSetPages_negativePageThrows() {
        Config config = new Config();
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> config.setPages("-1")
        );
        // This will throw "Invalid page range format" because "-1" looks like a range
        assertTrue(exception.getMessage().contains("Invalid page range format"));
    }
}

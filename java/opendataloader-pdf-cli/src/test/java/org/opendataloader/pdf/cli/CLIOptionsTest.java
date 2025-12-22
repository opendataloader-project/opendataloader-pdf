/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CLIOptionsTest {

    @TempDir
    Path tempDir;

    private File testPdf;
    private Options options;
    private CommandLineParser parser;

    @BeforeEach
    void setUp() throws IOException {
        testPdf = tempDir.resolve("test.pdf").toFile();
        Files.createFile(testPdf.toPath());
        options = CLIOptions.defineOptions();
        parser = new DefaultParser();
    }

    @Test
    void testDefineOptions_containsImageOutputOption() {
        assertTrue(options.hasOption("image-output"));
    }

    @Test
    void testDefineOptions_containsImageFormatOption() {
        assertTrue(options.hasOption("image-format"));
    }

    @Test
    void testCreateConfig_withImageOutputEmbedded() throws ParseException {
        String[] args = {"--image-output", "embedded", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertTrue(config.isEmbedImages());
        assertEquals(Config.IMAGE_OUTPUT_EMBEDDED, config.getImageOutput());
    }

    @Test
    void testCreateConfig_withImageOutputExternal() throws ParseException {
        String[] args = {"--image-output", "external", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertFalse(config.isEmbedImages());
        assertEquals(Config.IMAGE_OUTPUT_EXTERNAL, config.getImageOutput());
    }

    @Test
    void testCreateConfig_defaultImageOutput() throws ParseException {
        // Default should be embedded (new default)
        String[] args = {testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertTrue(config.isEmbedImages());
        assertEquals(Config.IMAGE_OUTPUT_EMBEDDED, config.getImageOutput());
    }

    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg"})
    void testCreateConfig_withValidImageFormat(String format) throws ParseException {
        String[] args = {"--image-format", format, testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertEquals(format, config.getImageFormat());
    }

    @Test
    void testCreateConfig_withUppercaseImageFormat() throws ParseException {
        String[] args = {"--image-format", "JPEG", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertEquals("jpeg", config.getImageFormat());
    }

    @Test
    void testCreateConfig_withInvalidImageFormat() throws ParseException {
        String[] args = {"--image-format", "bmp", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        assertThrows(IllegalArgumentException.class, () -> {
            CLIOptions.createConfigFromCommandLine(cmd);
        });
    }

    @Test
    void testCreateConfig_withEmptyImageFormat() throws ParseException {
        String[] args = {"--image-format", "", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        assertThrows(IllegalArgumentException.class, () -> {
            CLIOptions.createConfigFromCommandLine(cmd);
        });
    }

    @Test
    void testCreateConfig_withImageOutputAndImageFormat() throws ParseException {
        String[] args = {"--image-output", "embedded", "--image-format", "jpeg", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertTrue(config.isEmbedImages());
        assertEquals("jpeg", config.getImageFormat());
    }

    @Test
    void testCreateConfig_imageFormatWithExternalOutput() throws ParseException {
        String[] args = {"--image-output", "external", "--image-format", "jpeg", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertFalse(config.isEmbedImages());
        assertEquals("jpeg", config.getImageFormat());
    }

    @Test
    void testCreateConfig_withWebpImageFormat_shouldFail() throws ParseException {
        // WebP is not supported
        String[] args = {"--image-format", "webp", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        assertThrows(IllegalArgumentException.class, () -> {
            CLIOptions.createConfigFromCommandLine(cmd);
        });
    }

    @Test
    void testDefaultImageFormat() throws ParseException {
        String[] args = {testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertEquals(Config.IMAGE_FORMAT_PNG, config.getImageFormat());
    }

    @Test
    void testCreateConfig_withInvalidImageOutput() throws ParseException {
        String[] args = {"--image-output", "invalid", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        assertThrows(IllegalArgumentException.class, () -> {
            CLIOptions.createConfigFromCommandLine(cmd);
        });
    }

    @Test
    void testCreateConfig_withUppercaseImageOutput() throws ParseException {
        String[] args = {"--image-output", "EMBEDDED", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertTrue(config.isEmbedImages());
    }

    @Test
    void testCreateConfig_defaultReadingOrder() throws ParseException {
        // Default should be xycut (new default)
        String[] args = {testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertEquals(Config.READING_ORDER_XYCUT, config.getReadingOrder());
    }

    @Test
    void testCreateConfig_withReadingOrderOff() throws ParseException {
        String[] args = {"--reading-order", "off", testPdf.getAbsolutePath()};
        CommandLine cmd = parser.parse(options, args);

        Config config = CLIOptions.createConfigFromCommandLine(cmd);

        assertEquals(Config.READING_ORDER_OFF, config.getReadingOrder());
    }
}

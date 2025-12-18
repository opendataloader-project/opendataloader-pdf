/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the --embed-images feature.
 * Tests the full pipeline from Config to output files.
 */
class EmbedImagesIntegrationTest {

    private static final String SAMPLE_PDF_WITH_IMAGES = "../../samples/pdf/1901.03003.pdf";
    private static final String SAMPLE_PDF_BASENAME = "1901.03003";
    private static final String BASE64_DATA_URI_PREFIX = "data:image/png;base64,";
    private static final String BASE64_JPEG_PREFIX = "data:image/jpeg;base64,";

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // Ensure sample PDF exists
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Warning: Sample PDF not found at " + samplePdf.getAbsolutePath());
        }
    }

    @AfterEach
    void tearDown() {
        // Cleanup is handled by @TempDir
    }

    @Test
    void testEmbedImagesInJsonOutput() throws IOException {
        // Given
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setEmbedImages(true);
        config.setImageFormat("png");
        config.setGenerateJSON(true);
        config.setGenerateHtml(false);
        config.setGenerateMarkdown(false);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path jsonOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        String jsonContent = Files.readString(jsonOutput);
        // Check for Base64 data URI in JSON output
        if (jsonContent.contains("\"type\" : \"image\"")) {
            assertTrue(
                jsonContent.contains(BASE64_DATA_URI_PREFIX) || jsonContent.contains(BASE64_JPEG_PREFIX),
                "JSON should contain Base64 data URI for images when embedImages is true"
            );
            assertTrue(jsonContent.contains("\"format\""), "JSON should contain format field");
        }
    }

    @Test
    void testEmbedImagesInHtmlOutput() throws IOException {
        // Given
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setEmbedImages(true);
        config.setImageFormat("png");
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path htmlOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".html");
        assertTrue(Files.exists(htmlOutput), "HTML output should exist");

        String htmlContent = Files.readString(htmlOutput);
        // Check for Base64 data URI in img src
        if (htmlContent.contains("<img")) {
            assertTrue(
                htmlContent.contains("src=\"data:image/"),
                "HTML img tags should use Base64 data URI when embedImages is true"
            );
        }
    }

    @Test
    void testEmbedImagesInMarkdownOutput() throws IOException {
        // Given
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setEmbedImages(true);
        config.setImageFormat("png");
        config.setGenerateJSON(false);
        config.setAddImageToMarkdown(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path mdOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        // Check for Base64 data URI in markdown image syntax
        if (mdContent.contains("![")) {
            assertTrue(
                mdContent.contains("](data:image/"),
                "Markdown images should use Base64 data URI when embedImages is true"
            );
        }
    }

    @Test
    void testNoEmbedImagesUsesFilePaths() throws IOException {
        // Given
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setEmbedImages(false);
        config.setGenerateJSON(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path jsonOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        String jsonContent = Files.readString(jsonOutput);
        // When embedImages is false, should use source field with file path
        if (jsonContent.contains("\"type\" : \"image\"")) {
            assertTrue(jsonContent.contains("\"source\""), "JSON should contain source field for file path");
            assertFalse(jsonContent.contains("\"data\" : \"data:image"), "JSON should not contain Base64 data");
        }
    }

    @Test
    void testEmbedImagesWithJpegFormat() throws IOException {
        // Given
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setEmbedImages(true);
        config.setImageFormat("jpeg");
        config.setGenerateJSON(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path jsonOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        String jsonContent = Files.readString(jsonOutput);
        if (jsonContent.contains("\"type\" : \"image\"") && jsonContent.contains("\"data\"")) {
            assertTrue(jsonContent.contains("\"format\" : \"jpeg\""), "Format should be jpeg");
        }
    }
}

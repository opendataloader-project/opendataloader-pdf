/*
 * Copyright 2025-2026 Hancom Inc.
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
    private static final String SAMPLE_PDF_IMAGES_DIR = SAMPLE_PDF_BASENAME + "_images";
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
        config.setImageOutput(Config.IMAGE_OUTPUT_EMBEDDED);
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
        assertTrue(jsonContent.contains("\"type\" : \"image\""),
            "Sample PDF should produce at least one image entry");
        assertTrue(
            jsonContent.contains(BASE64_DATA_URI_PREFIX) || jsonContent.contains(BASE64_JPEG_PREFIX),
            "JSON should contain Base64 data URI for images when embedImages is true");
        assertTrue(jsonContent.contains("\"format\""), "JSON should contain format field");

        Path imagesDir = tempDir.resolve(SAMPLE_PDF_IMAGES_DIR);
        assertFalse(Files.exists(imagesDir),
            "Embedded mode must be self-contained: external image directory should not be created");
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
        config.setImageOutput(Config.IMAGE_OUTPUT_EMBEDDED);
        config.setImageFormat("png");
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path htmlOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".html");
        assertTrue(Files.exists(htmlOutput), "HTML output should exist");

        String htmlContent = Files.readString(htmlOutput);
        assertTrue(htmlContent.contains("<img"),
            "Sample PDF should produce at least one <img> tag");
        assertTrue(htmlContent.contains("src=\"data:image/"),
            "HTML img tags should use Base64 data URI when embedImages is true");

        Path imagesDir = tempDir.resolve(SAMPLE_PDF_IMAGES_DIR);
        assertFalse(Files.exists(imagesDir),
            "Embedded mode must be self-contained: external image directory should not be created");
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
        config.setImageOutput(Config.IMAGE_OUTPUT_EMBEDDED);
        config.setImageFormat("png");
        config.setGenerateJSON(false);
        config.setAddImageToMarkdown(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path mdOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        assertTrue(mdContent.contains("!["),
            "Sample PDF should produce at least one markdown image block");
        assertTrue(mdContent.contains("](data:image/"),
            "Markdown images should use Base64 data URI when embedImages is true");

        Path imagesDir = tempDir.resolve(SAMPLE_PDF_IMAGES_DIR);
        assertFalse(Files.exists(imagesDir),
            "Embedded mode must be self-contained: external image directory should not be created");
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
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path jsonOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        String jsonContent = Files.readString(jsonOutput);
        assertTrue(jsonContent.contains("\"type\" : \"image\""),
            "Sample PDF should produce at least one image entry");
        assertTrue(jsonContent.contains("\"source\""), "JSON should contain source field for file path");
        assertFalse(jsonContent.contains("\"data\" : \"data:image"), "JSON should not contain Base64 data");

        Path imagesDir = tempDir.resolve(SAMPLE_PDF_IMAGES_DIR);
        assertTrue(Files.exists(imagesDir),
            "External mode must extract images to disk: image directory should be created");
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
        config.setImageOutput(Config.IMAGE_OUTPUT_EMBEDDED);
        config.setImageFormat("jpeg");
        config.setGenerateJSON(true);

        // When
        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Then
        Path jsonOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        String jsonContent = Files.readString(jsonOutput);
        assertTrue(jsonContent.contains("\"type\" : \"image\""),
            "Sample PDF should produce at least one image entry");
        assertTrue(jsonContent.contains(BASE64_JPEG_PREFIX),
            "JSON should contain JPEG Base64 data URI when imageFormat is jpeg");
        assertTrue(jsonContent.contains("\"format\" : \"jpeg\""), "Format field should be jpeg");

        Path imagesDir = tempDir.resolve(SAMPLE_PDF_IMAGES_DIR);
        assertFalse(Files.exists(imagesDir),
            "Embedded mode must be self-contained: external image directory should not be created");
    }
}

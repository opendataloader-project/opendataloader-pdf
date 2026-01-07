/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the --image-dir feature.
 * Tests the full pipeline from Config to actual image file placement.
 */
class ImageDirIntegrationTest {

    private static final String SAMPLE_PDF_WITH_IMAGES = "../../samples/pdf/1901.03003.pdf";
    private static final String SAMPLE_PDF_BASENAME = "1901.03003";

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        StaticLayoutContainers.clearContainers();
    }

    @Test
    void testCustomImageDir_imagesWrittenToCustomPath() throws Exception {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Path outputDir = tempDir.resolve("output");
        Path customImageDir = tempDir.resolve("my-custom-images");

        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        config.setImageDir(customImageDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Verify images in custom directory
        assertTrue(Files.exists(customImageDir), "Custom image dir should be created");
        assertTrue(Files.list(customImageDir).findAny().isPresent(), "Images should exist in custom dir");

        // Verify default directory NOT created
        Path defaultImageDir = outputDir.resolve(SAMPLE_PDF_BASENAME + "_images");
        assertFalse(Files.exists(defaultImageDir), "Default dir should NOT be created when custom dir is specified");
    }

    @Test
    void testDefaultImageDir_imagesWrittenToDefaultPath() throws Exception {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Path outputDir = tempDir.resolve("output");

        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        // imageDir not set - should use default
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Verify images in default directory
        Path defaultImageDir = outputDir.resolve(SAMPLE_PDF_BASENAME + "_images");
        assertTrue(Files.exists(defaultImageDir), "Default image dir should be created");
        assertTrue(Files.list(defaultImageDir).findAny().isPresent(), "Images should exist in default dir");
    }

    @Test
    void testCustomImageDir_jsonReferencesCorrectPath() throws Exception {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Path customImageDir = tempDir.resolve("custom-images");

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setImageDir(customImageDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output should exist");

        String jsonContent = Files.readString(jsonOutput);

        // JSON should reference custom-images directory
        if (jsonContent.contains("\"source\"")) {
            assertTrue(jsonContent.contains("custom-images/imageFile"),
                    "JSON source should reference custom image directory");
        }
    }

    @Test
    void testCustomImageDir_markdownReferencesCorrectPath() throws Exception {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Path customImageDir = tempDir.resolve("my-images");

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setImageDir(customImageDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(false);
        config.setAddImageToMarkdown(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path mdOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);

        // Markdown should reference custom image directory
        if (mdContent.contains("![")) {
            assertTrue(mdContent.contains("my-images/imageFile"),
                    "Markdown should reference custom image directory");
        }
    }
}

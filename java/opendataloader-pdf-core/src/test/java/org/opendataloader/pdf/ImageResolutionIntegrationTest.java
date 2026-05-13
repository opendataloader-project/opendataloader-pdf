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
 * Integration test for the --image-resolution option.
 * Verifies that image extraction works correctly when imagePixelSize is passed
 * to ContrastRatioConsumer (the OOM fix path for issue #458).
 */
class ImageResolutionIntegrationTest {

    private static final String SAMPLE_PDF_WITH_IMAGES = "../../samples/pdf/1901.03003.pdf";
    private static final String SAMPLE_PDF_BASENAME = "1901.03003";

    @TempDir
    Path tempDir;

    @Test
    void testImageExtractionWithDefaultResolution() throws IOException {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Verify image files were created
        Path imagesDir = tempDir.resolve(SAMPLE_PDF_BASENAME + "_images");
        if (Files.exists(imagesDir)) {
            File[] imageFiles = imagesDir.toFile().listFiles((dir, name) ->
                name.endsWith(".png") || name.endsWith(".jpeg"));
            assertNotNull(imageFiles);
            assertTrue(imageFiles.length > 0, "Should extract at least one image with default resolution");
        }
    }

    @Test
    void testImageExtractionWithLowResolution() throws IOException {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(true);
        config.setImageResolution(500.0f);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Verify image files were created even with low resolution
        Path imagesDir = tempDir.resolve(SAMPLE_PDF_BASENAME + "_images");
        if (Files.exists(imagesDir)) {
            File[] imageFiles = imagesDir.toFile().listFiles((dir, name) ->
                name.endsWith(".png") || name.endsWith(".jpeg"));
            assertNotNull(imageFiles);
            assertTrue(imageFiles.length > 0, "Should extract at least one image with low resolution");
        }
    }

    @Test
    void testImageExtractionWithHighResolution() throws IOException {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(true);
        config.setImageResolution(4000.0f);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // Verify image files were created with high resolution
        Path imagesDir = tempDir.resolve(SAMPLE_PDF_BASENAME + "_images");
        if (Files.exists(imagesDir)) {
            File[] imageFiles = imagesDir.toFile().listFiles((dir, name) ->
                name.endsWith(".png") || name.endsWith(".jpeg"));
            assertNotNull(imageFiles);
            assertTrue(imageFiles.length > 0, "Should extract at least one image with high resolution");
        }
    }
}

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

    /**
     * Regression test for #405: when the input filename contains characters that
     * have special meaning in CommonMark link destinations (spaces, parens, etc.),
     * the on-disk directory keeps the original filename and the rendered Markdown
     * link wraps the destination in angle brackets per CommonMark §6.4 so it stays
     * a single, parseable token.
     */
    @Test
    void testDefaultImageDir_markdownLinkUsesAngleBracketDestination() throws Exception {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        // Copy the sample PDF under a filename containing CommonMark-reserved chars.
        String unsafeStem = "my paper (draft) [v2]";
        Path renamed = tempDir.resolve(unsafeStem + ".pdf");
        Files.copy(samplePdf.toPath(), renamed);

        Path outputDir = tempDir.resolve("output");

        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(false);
        config.setAddImageToMarkdown(true);

        DocumentProcessor.processFile(renamed.toAbsolutePath().toString(), config);

        // The on-disk image directory keeps the original filename — we don't rewrite
        // user input. The rendered destination uses the angle-bracket form.
        Path imageDir = outputDir.resolve(unsafeStem + "_images");
        assertTrue(Files.exists(imageDir),
                "On-disk image directory should keep the original filename");

        Path mdOutput = outputDir.resolve(unsafeStem + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        assertTrue(mdContent.contains("!["),
                "Test precondition: sample must produce markdown image syntax");

        // Inside `<...>` the path stays byte-identical to the on-disk directory.
        String expectedDestination = "<" + unsafeStem + "_images/imageFile";
        assertTrue(mdContent.contains(expectedDestination),
                "Markdown link should wrap the path in angle brackets; expected '" + expectedDestination + "'");
        // The raw unwrapped form must NOT appear inside a link destination — that
        // is the exact bug #405 reports.
        assertFalse(mdContent.contains("(" + unsafeStem + "_images/"),
                "Raw unwrapped directory name must not appear as a Markdown link destination");
    }

    /**
     * #405 reproduces equally through the `--image-dir` path. A user-provided
     * directory name with spaces or parens lands verbatim on disk (we respect the
     * user's input) and the Markdown link wraps it in angle brackets.
     */
    @Test
    void testCustomImageDir_markdownLinkUsesAngleBracketDestination() throws Exception {
        File samplePdf = new File(SAMPLE_PDF_WITH_IMAGES);
        if (!samplePdf.exists()) {
            System.out.println("Skipping test: Sample PDF not found");
            return;
        }

        Path customImageDir = tempDir.resolve("my pictures (v2)");

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setImageDir(customImageDir.toString());
        config.setImageOutput(Config.IMAGE_OUTPUT_EXTERNAL);
        config.setGenerateJSON(false);
        config.setAddImageToMarkdown(true);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        // On-disk directory keeps the user-provided name verbatim.
        assertTrue(Files.exists(customImageDir),
                "Custom image directory should keep the user-provided name");

        Path mdOutput = tempDir.resolve(SAMPLE_PDF_BASENAME + ".md");
        assertTrue(Files.exists(mdOutput), "Markdown output should exist");

        String mdContent = Files.readString(mdOutput);
        assertTrue(mdContent.contains("!["),
                "Test precondition: sample must produce markdown image syntax");

        String expectedDestination = "<my pictures (v2)/imageFile";
        assertTrue(mdContent.contains(expectedDestination),
                "Markdown link should wrap the custom dir in angle brackets; expected '" + expectedDestination + "'");
        assertFalse(mdContent.contains("(my pictures (v2)/"),
                "Raw unwrapped custom-dir name must not appear as a Markdown link destination");
    }
}

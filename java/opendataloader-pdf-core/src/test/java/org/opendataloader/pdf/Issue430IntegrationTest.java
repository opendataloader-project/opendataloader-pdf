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
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.exceptions.InvalidPdfFileException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for #430: an invalid file path must not crash a batch loop
 * over {@link OpenDataLoaderPDF#processFile(String, Config)}.
 *
 * <p>Every input-validity failure (null/blank path, invalid path, missing file,
 * directory, non-{@code .pdf} name, non-PDF content) is reported as a single
 * checked type, {@link InvalidPdfFileException} (a subtype of {@link IOException}),
 * so a caller can catch it to skip the bad file and continue — mirroring how the
 * CLI gracefully skips bad inputs during a directory scan. The closing
 * {@link #testBatchProcessing_continuesAfterInvalidFile()} is the actual #430
 * regression: a valid PDF placed <em>after</em> a bad path still gets processed.
 */
class Issue430IntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/1901.03003.pdf";

    @TempDir
    Path tempDir;

    private Config jsonConfig(Path outputDir) {
        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        config.setGenerateJSON(true);
        return config;
    }

    @Test
    void testProcessFile_nullPath_throwsInvalidPdfFileException() {
        assertThrows(InvalidPdfFileException.class,
            () -> OpenDataLoaderPDF.processFile(null, jsonConfig(tempDir.resolve("out"))));
    }

    @Test
    void testProcessFile_blankPath_throwsInvalidPdfFileException() {
        assertThrows(InvalidPdfFileException.class,
            () -> OpenDataLoaderPDF.processFile("   ", jsonConfig(tempDir.resolve("out"))));
    }

    @Test
    void testProcessFile_nonExistentPath_throwsInvalidPdfFileException() {
        Path missing = tempDir.resolve("does-not-exist.pdf");
        assertThrows(InvalidPdfFileException.class,
            () -> OpenDataLoaderPDF.processFile(missing.toString(), jsonConfig(tempDir.resolve("out"))));
    }

    @Test
    void testProcessFile_directory_throwsInvalidPdfFileException() throws Exception {
        Path dir = tempDir.resolve("a-folder.pdf"); // .pdf name, but it is a directory
        Files.createDirectory(dir);
        assertThrows(InvalidPdfFileException.class,
            () -> OpenDataLoaderPDF.processFile(dir.toString(), jsonConfig(tempDir.resolve("out"))));
    }

    @Test
    void testProcessFile_nonPdfExtension_throwsInvalidPdfFileException() throws Exception {
        // Real %PDF- content, but the name does not end in .pdf — rejected on the
        // extension check before any content is read.
        Path notPdf = tempDir.resolve("document.txt");
        Files.write(notPdf, "%PDF-1.4 ...".getBytes());
        assertThrows(InvalidPdfFileException.class,
            () -> OpenDataLoaderPDF.processFile(notPdf.toString(), jsonConfig(tempDir.resolve("out"))));
    }

    @Test
    void testProcessFile_nonPdfContent_throwsInvalidPdfFileException() throws Exception {
        // .pdf name, but JPEG bytes — rejected by the magic-number guard.
        Path fake = tempDir.resolve("fake.pdf");
        Files.write(fake, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        assertThrows(InvalidPdfFileException.class,
            () -> OpenDataLoaderPDF.processFile(fake.toString(), jsonConfig(tempDir.resolve("out"))));
    }

    @Test
    void testProcessFile_validPdf_succeeds() throws Exception {
        File source = new File(SAMPLE_PDF);
        assertTrue(source.exists(), "Sample PDF not found at " + source.getAbsolutePath());

        Path inputPdf = tempDir.resolve("input.pdf");
        Files.copy(source.toPath(), inputPdf);
        Path outputDir = tempDir.resolve("output");

        OpenDataLoaderPDF.processFile(inputPdf.toAbsolutePath().toString(), jsonConfig(outputDir));

        assertTrue(Files.exists(outputDir.resolve("input.json")),
            "JSON output must be generated for a valid PDF");
    }

    /**
     * The #430 regression. A batch loop processes three entries — valid, missing,
     * valid — catching {@link InvalidPdfFileException} per file. Before the fix the
     * second entry's exception killed the loop and the third file was never
     * processed; after the fix the loop skips the bad entry and the trailing valid
     * PDF still produces output.
     */
    @Test
    void testBatchProcessing_continuesAfterInvalidFile() throws Exception {
        File source = new File(SAMPLE_PDF);
        assertTrue(source.exists(), "Sample PDF not found at " + source.getAbsolutePath());

        Path firstValid = tempDir.resolve("01_valid.pdf");
        Path missing = tempDir.resolve("02_missing.pdf"); // intentionally not created
        Path secondValid = tempDir.resolve("03_valid.pdf");
        Files.copy(source.toPath(), firstValid);
        Files.copy(source.toPath(), secondValid);

        Path outputDir = tempDir.resolve("batch-output");

        String[] inputs = {firstValid.toString(), missing.toString(), secondValid.toString()};
        List<String> processed = new ArrayList<>();
        List<InvalidPdfFileException> skipped = new ArrayList<>();

        for (String input : inputs) {
            try {
                OpenDataLoaderPDF.processFile(input, jsonConfig(outputDir));
                processed.add(input);
            } catch (InvalidPdfFileException skip) {
                skipped.add(skip); // bad input — continue with the next file
            }
        }

        assertEquals(1, skipped.size(), "exactly the missing file should be skipped");
        assertEquals(2, processed.size(), "both valid PDFs must be processed despite the bad entry");
        assertTrue(processed.contains(secondValid.toString()),
            "the valid PDF after the invalid one must still be processed (the #430 regression)");
        assertTrue(Files.exists(outputDir.resolve("01_valid.json")),
            "output for the first valid PDF must exist");
        assertTrue(Files.exists(outputDir.resolve("03_valid.json")),
            "output for the valid PDF after the invalid one must exist");
    }
}

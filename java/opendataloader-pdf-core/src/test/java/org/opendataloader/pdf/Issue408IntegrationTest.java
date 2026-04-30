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
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for #408: after `processFile` returns, the input PDF must no
 * longer be locked by the JVM. The fix in #410 closes the underlying PDDocument
 * (and the verapdf-internal temp PDF stream) so that callers can immediately
 * delete the source file. On Windows this surfaced as "file in use by another
 * process"; POSIX systems allow unlink with open FDs but the leak was real.
 *
 * The test deletes the input PDF after processing; on Windows CI this asserts
 * that the OS file handle has been released (a regression would throw a
 * FileSystemException). POSIX runs document the contract even when the OS would
 * tolerate a stray FD.
 */
class Issue408IntegrationTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/1901.03003.pdf";

    @TempDir
    Path tempDir;

    @Test
    void testProcessFile_releasesInputFileLock() throws Exception {
        File source = new File(SAMPLE_PDF);
        assertTrue(source.exists(), "Sample PDF not found at " + source.getAbsolutePath());

        Path inputPdf = tempDir.resolve("input.pdf");
        Files.copy(source.toPath(), inputPdf);

        Path outputDir = tempDir.resolve("output");

        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        config.setGenerateJSON(true);

        DocumentProcessor.processFile(inputPdf.toAbsolutePath().toString(), config);

        // On Windows a leaked file handle would cause Files.delete to throw
        // FileSystemException("The process cannot access the file...").
        // On POSIX delete always succeeds, but this still documents the contract
        // so a future refactor that re-leaks the handle fails on Windows CI.
        Files.delete(inputPdf);
        assertFalse(Files.exists(inputPdf),
                "Input PDF must be deletable immediately after processFile returns");
    }

    @Test
    void testProcessFile_canBeCalledRepeatedlyOnTheSameJvm() throws Exception {
        File source = new File(SAMPLE_PDF);
        assertTrue(source.exists(), "Sample PDF not found at " + source.getAbsolutePath());

        // Running processFile in a loop exercises the cleanup path's idempotency:
        // each call clears the static containers, and the next call must
        // re-initialize them via preprocessing without NPE.
        for (int i = 0; i < 3; i++) {
            Path inputPdf = tempDir.resolve("input-" + i + ".pdf");
            Files.copy(source.toPath(), inputPdf);

            Path outputDir = tempDir.resolve("output-" + i);

            Config config = new Config();
            config.setOutputFolder(outputDir.toString());
            config.setGenerateJSON(true);

            DocumentProcessor.processFile(inputPdf.toAbsolutePath().toString(), config);

            // Verify cleanup ran on every iteration, not just the last one.
            Files.delete(inputPdf);
        }
    }
}

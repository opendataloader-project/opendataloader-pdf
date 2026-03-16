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
package org.opendataloader.pdf.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CLIMainTest {

    @TempDir
    Path tempDir;

    /**
     * When processing a PDF file throws any exception, CLIMain.run() must return
     * a non-zero exit code. This test uses a malformed PDF with hybrid mode
     * targeting an unreachable server, which triggers an exception during processing.
     *
     * <p>Before this fix, processFile() caught all exceptions and logged them at
     * SEVERE level but never propagated the failure to the exit code.
     *
     * <p>Regression test for https://github.com/opendataloader-project/opendataloader-pdf/issues/287
     */
    @Test
    void testProcessingFailureReturnsNonZeroExitCode() throws IOException {
        // Create a minimal PDF file so processFile is actually invoked
        // (the file must exist and end in .pdf to pass the isPdfFile check)
        Path testPdf = tempDir.resolve("test.pdf");
        Files.write(testPdf, "%PDF-1.4 minimal".getBytes());

        // Use an unreachable hybrid URL — the processing will fail either at
        // the hybrid availability check or during PDF parsing, both of which
        // must result in a non-zero exit code.
        int exitCode = CLIMain.run(new String[]{
            "--hybrid", "docling-fast",
            "--hybrid-url", "http://127.0.0.1:59999",
            testPdf.toString()
        });

        assertNotEquals(0, exitCode,
            "Exit code must be non-zero when file processing fails");
    }

    /**
     * When a directory contains a file that fails processing, run() must return
     * non-zero, even though other files in the directory may succeed.
     */
    @Test
    void testDirectoryWithFailingFileReturnsNonZeroExitCode() throws IOException {
        Path dir = tempDir.resolve("docs");
        Files.createDirectory(dir);
        Path testPdf = dir.resolve("bad.pdf");
        Files.write(testPdf, "%PDF-1.4 minimal".getBytes());

        int exitCode = CLIMain.run(new String[]{
            "--hybrid", "docling-fast",
            "--hybrid-url", "http://127.0.0.1:59999",
            dir.toString()
        });

        assertNotEquals(0, exitCode,
            "Exit code must be non-zero when any file in directory fails");
    }

    /**
     * Normal invocation with no arguments should return 0 (just prints help).
     */
    @Test
    void testNoArgumentsReturnsZero() {
        int exitCode = CLIMain.run(new String[]{});
        assertEquals(0, exitCode);
    }

    /**
     * Invalid CLI arguments (e.g., unrecognized option) must return exit code 2,
     * following POSIX convention for command-line usage errors.
     */
    @Test
    void testInvalidArgumentsReturnsExitCode2() {
        int exitCode = CLIMain.run(new String[]{"--no-such-option"});
        assertEquals(2, exitCode);
    }

    /**
     * Non-existent input file must return non-zero exit code.
     */
    @Test
    void testNonExistentFileReturnsNonZeroExitCode() {
        int exitCode = CLIMain.run(new String[]{"/nonexistent/path/file.pdf"});
        assertNotEquals(0, exitCode,
            "Exit code must be non-zero when input file does not exist");
    }
}

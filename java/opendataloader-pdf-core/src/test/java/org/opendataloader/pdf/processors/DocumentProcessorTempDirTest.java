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
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.exceptions.InvalidPdfFileException;
import org.opendataloader.pdf.exceptions.TempDirectoryNotWritableException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Regression tests for the temporary-directory precondition.
 *
 * <p>veraPDF spills anything past a small in-memory threshold to a temporary
 * file — Standard 14 font metrics, embedded font programs, CMaps and decoded
 * content streams. When that directory is unwritable those reads fail deep
 * inside veraPDF, are logged at {@code FINE} and swallowed, and processing
 * either dies with an unrelated NullPointerException or completes with exit
 * code 0 while silently dropping most of the text. The guard must fail up
 * front with a message naming the directory and the way out.
 */
class DocumentProcessorTempDirTest {

    @TempDir
    Path tempDir;

    /**
     * Relies on POSIX permissions to make a directory unwritable, which does
     * not translate to Windows ACLs. Also skipped for root, who bypasses the
     * permission bits entirely.
     */
    @Test
    @DisabledOnOs(OS.WINDOWS)
    @ResourceLock(Resources.SYSTEM_PROPERTIES)
    void preprocessingRejectsUnwritableTempDirWithTypedException() throws IOException {
        Path unwritable = Files.createDirectory(tempDir.resolve("unwritable"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r-x------")));
        // Reported as skipped rather than passed, so a vacuous run is visible in CI.
        assumeTrue(!Files.isWritable(unwritable), "running as root: permission bits do not apply");

        Path pdf = validPdf();
        String originalTempDir = System.getProperty("java.io.tmpdir");
        TempDirectoryNotWritableException thrown;
        try {
            System.setProperty("java.io.tmpdir", unwritable.toString());
            thrown = assertThrows(
                TempDirectoryNotWritableException.class,
                () -> DocumentProcessor.preprocessing(pdf.toString(), new Config()));
        } finally {
            System.setProperty("java.io.tmpdir", originalTempDir);
        }

        String message = thrown.getMessage();
        assertNotNull(message);
        assertTrue(message.contains(unwritable.toString()),
            "message should name the offending directory so the user can fix it, but was: " + message);
        assertTrue(message.contains("java.io.tmpdir") && message.contains("TMPDIR"),
            "message should tell the user how to point at a writable directory, but was: " + message);
        assertNotNull(thrown.getCause(), "underlying IOException should be preserved for diagnostics");
    }

    /**
     * The guard must stay invisible in a normal environment. Uses a file with
     * no {@code %PDF-} header so the magic-number check — which runs
     * immediately after this one and needs no veraPDF state — stops
     * preprocessing right there: reaching {@code InvalidPdfFileException}
     * proves the temporary-directory check passed, without opening a
     * PDDocument or populating the static containers that a later test in the
     * same JVM would inherit.
     */
    @Test
    void preprocessingAcceptsWritableTempDir() throws IOException {
        Path notAPdf = tempDir.resolve("not-a-pdf.pdf");
        Files.write(notAPdf, "definitely not a pdf".getBytes(StandardCharsets.US_ASCII));

        assertThrows(
            InvalidPdfFileException.class,
            () -> DocumentProcessor.preprocessing(notAPdf.toString(), new Config()),
            "a writable temporary directory must let preprocessing reach the magic-number check");
    }

    private Path validPdf() throws IOException {
        Path pdf = tempDir.resolve("minimal.pdf");
        Files.write(pdf, "%PDF-1.4\n%%EOF\n".getBytes(StandardCharsets.US_ASCII));
        return pdf;
    }
}

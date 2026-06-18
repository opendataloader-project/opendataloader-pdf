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
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.exceptions.InvalidPdfFileException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for DocumentProcessor.preprocessing: every "input is not
 * a usable PDF" failure mode must surface as a typed InvalidPdfFileException
 * with a user-facing message, never a raw veraPDF IOException.
 *
 * <p>Two failure modes are covered:
 * <ul>
 *   <li>Missing {@code %PDF-} header (JPEG, empty file): caught by the
 *       magic-number guard before veraPDF is invoked. Message ends with
 *       {@code (missing %PDF- header)}.</li>
 *   <li>Header present but body corrupt or truncated: caught when
 *       {@code new PDDocument(pdfName)} fails inside veraPDF. Message ends
 *       with {@code (corrupted or truncated content)}, and the original
 *       IOException is preserved as the cause for diagnostics.</li>
 * </ul>
 */
class DocumentProcessorMagicNumberTest {

    @TempDir
    Path tempDir;

    @Test
    void preprocessingRejectsJpegContentWithInvalidPdfFileException() throws IOException {
        Path jpgAsPdf = tempDir.resolve("fake.pdf");
        // JPEG SOI + JFIF APP0 segment header — definitely not PDF.
        Files.write(jpgAsPdf, new byte[]{
            (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
            0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
        });

        InvalidPdfFileException thrown = assertThrows(
            InvalidPdfFileException.class,
            () -> DocumentProcessor.preprocessing(jpgAsPdf.toString(), new Config()));

        String message = thrown.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("fake.pdf"),
            "Message must include the file name; got: " + message);
        assertTrue(message.contains("%PDF-"),
            "Message must mention the %PDF- header; got: " + message);
    }

    @Test
    void preprocessingClassifiesBomPrefixedTruncatedPdfAsCorrupted() throws IOException {
        Path bomPdf = tempDir.resolve("bom.pdf");
        // UTF-8 BOM + spaces + valid PDF header. The magic-number guard must
        // accept this (1024-byte search window). veraPDF will still reject
        // the file because the body is incomplete — but that failure must
        // surface as InvalidPdfFileException with the "corrupted or
        // truncated" message, not as a raw IOException and not with the
        // missing-header wording.
        byte[] prefix = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, ' ', ' '};
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        byte[] content = new byte[prefix.length + header.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(header, 0, content, prefix.length, header.length);
        Files.write(bomPdf, content);

        InvalidPdfFileException thrown = assertThrows(
            InvalidPdfFileException.class,
            () -> DocumentProcessor.preprocessing(bomPdf.toString(), new Config()));

        String message = thrown.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("bom.pdf"),
            "Message must include the file name; got: " + message);
        assertTrue(message.contains("corrupted or truncated"),
            "Body-corruption case must use the 'corrupted or truncated' wording, "
            + "not the missing-header wording; got: " + message);
        assertFalse(message.contains("missing %PDF- header"),
            "Header IS present (just preceded by BOM/whitespace) — must not "
            + "be misreported as missing; got: " + message);
        assertInstanceOf(IOException.class, thrown.getCause(),
            "Original veraPDF IOException must be preserved as cause for diagnostics");
    }

    @Test
    void preprocessingRejectsEmptyFile() throws IOException {
        Path empty = tempDir.resolve("empty.pdf");
        Files.write(empty, new byte[0]);

        assertThrows(InvalidPdfFileException.class,
            () -> DocumentProcessor.preprocessing(empty.toString(), new Config()));
    }

    @Test
    void preprocessingRejectsTruncatedPdfWithInvalidPdfFileException() throws IOException {
        // Simulates a real download that was interrupted near the end of
        // the file: valid %PDF- header at the start, plausible body bytes,
        // but the trailing xref table that veraPDF requires is gone.
        Path truncated = tempDir.resolve("truncated.pdf");
        byte[] head = "%PDF-1.6\n".getBytes(StandardCharsets.US_ASCII);
        byte[] body = new byte[1024];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) ('a' + (i % 26));
        }
        byte[] content = new byte[head.length + body.length];
        System.arraycopy(head, 0, content, 0, head.length);
        System.arraycopy(body, 0, content, head.length, body.length);
        Files.write(truncated, content);

        InvalidPdfFileException thrown = assertThrows(
            InvalidPdfFileException.class,
            () -> DocumentProcessor.preprocessing(truncated.toString(), new Config()));

        String message = thrown.getMessage();
        assertNotNull(message);
        assertTrue(message.contains("truncated.pdf"),
            "Message must include the file name; got: " + message);
        assertTrue(message.contains("corrupted or truncated"),
            "Truncated PDF must surface the 'corrupted or truncated' message; got: "
            + message);
        assertInstanceOf(IOException.class, thrown.getCause(),
            "Original veraPDF IOException must be preserved as cause for diagnostics");
    }
}

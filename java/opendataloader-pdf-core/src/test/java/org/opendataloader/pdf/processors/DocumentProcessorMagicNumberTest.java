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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for PDFDLOSP-14: DocumentProcessor.preprocessing must reject
 * files whose content is not PDF with a typed InvalidPdfFileException, before
 * the veraPDF parser is invoked.
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
    void preprocessingAcceptsHeaderAfterBomAndWhitespace() throws IOException {
        Path bomPdf = tempDir.resolve("bom.pdf");
        // UTF-8 BOM + spaces + valid PDF header. The magic-number guard must
        // accept this (1024-byte search window). It will still fail later in
        // veraPDF because the body is incomplete — that failure must NOT be
        // an InvalidPdfFileException, since the header IS present.
        byte[] prefix = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, ' ', ' '};
        byte[] header = "%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII);
        byte[] content = new byte[prefix.length + header.length];
        System.arraycopy(prefix, 0, content, 0, prefix.length);
        System.arraycopy(header, 0, content, prefix.length, header.length);
        Files.write(bomPdf, content);

        // Any exception thrown must NOT be InvalidPdfFileException — the
        // magic-number guard accepted the file. veraPDF may still reject it
        // later, but that goes through the normal IOException path.
        Throwable thrown = assertThrows(Throwable.class,
            () -> DocumentProcessor.preprocessing(bomPdf.toString(), new Config()));
        assertTrue(!(thrown instanceof InvalidPdfFileException),
            "Magic-number guard must accept %PDF- preceded by BOM/whitespace; "
            + "got InvalidPdfFileException: " + thrown.getMessage());
    }

    @Test
    void preprocessingRejectsEmptyFile() throws IOException {
        Path empty = tempDir.resolve("empty.pdf");
        Files.write(empty, new byte[0]);

        assertThrows(InvalidPdfFileException.class,
            () -> DocumentProcessor.preprocessing(empty.toString(), new Config()));
    }
}

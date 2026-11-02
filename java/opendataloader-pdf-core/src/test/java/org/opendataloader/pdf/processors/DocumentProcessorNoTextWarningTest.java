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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * #548: in local mode a scanned or image-only PDF gave no text and no signal. One WARNING per
 * document names the file and points to OCR in hybrid mode. The JSON output does not change.
 */
class DocumentProcessorNoTextWarningTest {

    private static final String SCANNED_PDF = "../../samples/pdf/chinese_scan.pdf";
    private static final String TEXT_PDF = "../../samples/pdf/lorem.pdf";

    @TempDir
    Path tempDir;

    @Test
    void warnsOnceForAScannedPdf() throws IOException {
        List<String> warnings = noTextWarnings(SCANNED_PDF, "chinese_scan.json");

        assertEquals(1, warnings.size(), "Expected one no-text WARNING. Got: " + warnings);
        String warning = warnings.get(0);
        assertTrue(warning.contains("chinese_scan.pdf"), "The warning names the file: " + warning);
        assertTrue(warning.contains("--hybrid"), "The warning points to OCR in hybrid mode: " + warning);
    }

    @Test
    void doesNotWarnForAPdfWithText() throws IOException {
        List<String> warnings = noTextWarnings(TEXT_PDF, "lorem.json");

        assertTrue(warnings.isEmpty(), "Expected no no-text WARNING. Got: " + warnings);
    }

    /** Converts the PDF to JSON and returns the no-text warnings that DocumentProcessor logged. */
    private List<String> noTextWarnings(String pdfPath, String jsonName) throws IOException {
        File pdf = new File(pdfPath);
        assumeTrue(pdf.exists(), "Fixture not found: " + pdf.getAbsolutePath());

        Logger logger = Logger.getLogger(DocumentProcessor.class.getCanonicalName());
        List<LogRecord> records = Collections.synchronizedList(new ArrayList<>());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord record) {
                records.add(record);
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        try {
            Config config = new Config();
            config.setOutputFolder(tempDir.toString());
            config.setGenerateJSON(true);
            DocumentProcessor.processFile(pdf.getAbsolutePath(), config);
        } finally {
            logger.removeHandler(handler);
        }

        // The conversion ran, so an empty list is not vacuous
        assertTrue(Files.exists(tempDir.resolve(jsonName)), "Expected JSON output " + jsonName);
        synchronized (records) {
            return records.stream()
                .filter(record -> record.getLevel() == Level.WARNING)
                .map(LogRecord::getMessage)
                .filter(message -> message.startsWith("No text was extracted"))
                .collect(Collectors.toList());
        }
    }
}

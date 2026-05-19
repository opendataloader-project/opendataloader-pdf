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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression test for PDFDLOSP-26: every {@code --format} value must emit
 * exactly one INFO-level "Created &lt;path&gt;" log line after writing its
 * output. Tagged-PDF previously skipped this log because
 * {@code AutoTaggingProcessor.createTaggedPDF()} had no {@code Logger}.
 */
class FormatLogRegressionTest {

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "--format {0} emits exactly one Created log")
    @ValueSource(strings = {"json", "text", "html", "pdf", "markdown", "tagged-pdf"})
    void formatEmitsExactlyOneCreatedLog(String format) throws IOException {
        Path pdf = createMinimalPdf(tempDir.resolve("in.pdf"));
        Path outDir = tempDir.resolve(format);
        Files.createDirectories(outDir);

        List<LogRecord> records = new ArrayList<>();
        int exitCode = captureAllLogsOf(records, () -> CLIMain.run(new String[]{
            "--format", format,
            "--output", outDir.toString(),
            pdf.toString()
        }));

        assertEquals(0, exitCode, () ->
            "Exit code must be 0 for --format " + format + "; records: " + summarize(records));

        long createdCount = records.stream()
            .filter(r -> r.getLevel() == Level.INFO)
            .filter(r -> r.getMessage() != null && r.getMessage().contains("Created"))
            .count();

        assertEquals(1L, createdCount, () ->
            "Exactly one INFO 'Created ...' log expected for --format " + format
            + "; got " + createdCount + ". Records: " + summarize(records));
    }

    private static Path createMinimalPdf(Path target) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(target.toFile());
        }
        return target;
    }

    /**
     * Captures every {@link LogRecord} emitted by any logger under the
     * {@code org.opendataloader.pdf} hierarchy. Attaching to the root logger
     * would also include third-party noise; attaching to the package root
     * picks up CLIMain plus every generator/processor in core (JUL records
     * propagate to parent handlers).
     *
     * <p>JUL is global; this assumes sequential test execution
     * (JUnit 5 + Surefire default).
     */
    private static <T> T captureAllLogsOf(List<LogRecord> sink, Callable<T> action) {
        Logger logger = Logger.getLogger("org.opendataloader.pdf");
        Level priorLevel = logger.getLevel();
        boolean priorUseParent = logger.getUseParentHandlers();
        Handler capture = new Handler() {
            @Override public void publish(LogRecord record) { sink.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        };
        capture.setLevel(Level.ALL);
        logger.addHandler(capture);
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        try {
            return action.call();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            logger.removeHandler(capture);
            logger.setLevel(priorLevel);
            logger.setUseParentHandlers(priorUseParent);
        }
    }

    private static String summarize(List<LogRecord> records) {
        StringBuilder sb = new StringBuilder("[");
        for (LogRecord r : records) {
            sb.append('(').append(r.getLevel()).append(": ").append(r.getMessage()).append(") ");
        }
        return sb.append(']').toString();
    }
}

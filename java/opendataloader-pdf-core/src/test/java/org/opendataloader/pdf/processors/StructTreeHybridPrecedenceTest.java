package org.opendataloader.pdf.processors;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;

/**
 * Regression tests for #633: when {@code --use-struct-tree} and {@code --hybrid}
 * are combined on a tagged PDF, the struct-tree path takes precedence and the
 * hybrid backend is never called. That precedence is fine, but it must not be
 * silent — a warning symmetric to the existing no-struct-tree warning must fire.
 */
public class StructTreeHybridPrecedenceTest {

    // Must be an actually-tagged PDF: the warning is gated on the effective
    // isUseStructTree() flag, which preprocessing() only leaves true when the
    // document really has a structure tree. This PDF/UA reference file is tagged.
    private static final String TAGGED_PDF =
        "../../samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-04_Presentation.pdf";
    private static final String OUTPUT_JSON = "PDFUA-Ref-2-04_Presentation.json";

    @TempDir
    Path tempDir;

    /** Captures WARNING-level messages from DocumentProcessor while a body runs. */
    private List<String> captureWarnings(ThrowingRunnable body) throws IOException {
        Logger logger = Logger.getLogger(DocumentProcessor.class.getCanonicalName());
        // synchronized: the tagged path may log from ForkJoinPool workers.
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel() == Level.WARNING) {
                    warnings.add(r.getMessage());
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        try {
            // The warning under test is emitted in extractContents() before the tagged
            // path runs, so a later failure in document processing must not mask it.
            // We assert purely on what was logged; swallow body errors here.
            body.run();
        } catch (IOException | RuntimeException ignored) {
            // intentionally ignored — see comment above
        } finally {
            logger.removeHandler(handler);
        }
        return warnings;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws IOException;
    }

    @Test
    void warnsWhenStructTreeSuppressesHybridOnTaggedPdf() throws IOException {
        File taggedPdf = new File(TAGGED_PDF);
        assumeTrue(taggedPdf.exists(), "Tagged PDF fixture not found: " + taggedPdf.getAbsolutePath());

        List<String> warnings = captureWarnings(() -> {
            Config config = new Config();
            config.setOutputFolder(tempDir.toString());
            config.setGenerateJSON(true);
            config.setUseStructTree(true);
            config.setHybrid("docling-fast");
            DocumentProcessor.processFile(taggedPdf.getAbsolutePath(), config);
        });

        boolean warned = warnings.stream().anyMatch(w ->
            w.contains("use-struct-tree") && w.toLowerCase().contains("hybrid"));
        Assertions.assertTrue(warned,
            "Expected a WARNING that the hybrid backend is suppressed by --use-struct-tree. Got: " + warnings);
    }

    @Test
    void noSuchWarningWhenOnlyStructTree() throws IOException {
        File taggedPdf = new File(TAGGED_PDF);
        assumeTrue(taggedPdf.exists(), "Tagged PDF fixture not found: " + taggedPdf.getAbsolutePath());

        List<String> warnings = captureWarnings(() -> {
            Config config = new Config();
            config.setOutputFolder(tempDir.toString());
            config.setGenerateJSON(true);
            config.setUseStructTree(true);
            DocumentProcessor.processFile(taggedPdf.getAbsolutePath(), config);
        });

        // Prove the struct-tree path actually ran (captureWarnings swallows body
        // errors); otherwise assertFalse could pass vacuously on an early failure.
        Assertions.assertTrue(Files.exists(tempDir.resolve(OUTPUT_JSON)),
            "Struct-tree path should have produced JSON output");
        boolean warned = warnings.stream().anyMatch(w ->
            w.contains("use-struct-tree") && w.toLowerCase().contains("hybrid"));
        Assertions.assertFalse(warned,
            "Should not warn about hybrid when --hybrid was not requested. Got: " + warnings);
    }
}

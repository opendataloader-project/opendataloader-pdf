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
import java.util.logging.SimpleFormatter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;

/**
 * {@code --filter-hidden-text on} runs in the native pipeline only. The two modes that
 * skip it, {@code --hybrid} and {@code --use-struct-tree}, must say so with a WARNING
 * instead of dropping the option silently, and the native path must not warn.
 */
public class HiddenTextFilterModeWarningTest {

    /** One page with white-on-white text. The tests assert on the log only. */
    private static final String PDF = "../../samples/pdf/safety-trigger.pdf";
    private static final String OUTPUT_JSON = "safety-trigger.json";
    // The struct-tree warning is gated on the effective isUseStructTree() flag, which
    // preprocessing() only leaves true for a document that has a structure tree.
    private static final String TAGGED_PDF =
        "../../samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-04_Presentation.pdf";
    private static final String UNREACHABLE_HYBRID_URL = "http://127.0.0.1:1";

    @TempDir
    Path tempDir;

    /** Captures the formatted WARNING messages of DocumentProcessor while a body runs. */
    private List<String> captureWarnings(ThrowingRunnable body) throws IOException {
        Logger logger = Logger.getLogger(DocumentProcessor.class.getCanonicalName());
        List<String> warnings = Collections.synchronizedList(new ArrayList<>());
        SimpleFormatter formatter = new SimpleFormatter();
        Handler handler = new Handler() {
            @Override public void publish(LogRecord r) {
                if (r.getLevel() == Level.WARNING) {
                    warnings.add(formatter.formatMessage(r));
                }
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        logger.addHandler(handler);
        try {
            // The warning under test is logged in extract() before the mode runs, so a
            // later failure of that mode (no hybrid backend here) must not mask it.
            body.run();
        } catch (IOException | RuntimeException ignored) {
            // intentionally ignored, see the comment above
        } finally {
            logger.removeHandler(handler);
        }
        return warnings;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws IOException;
    }

    private Config configWithFilterOn() {
        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);
        config.getFilterConfig().setFilterHiddenText(true);
        return config;
    }

    private static boolean warnedThatFilterIsIgnored(List<String> warnings, String mode) {
        return warnings.stream().anyMatch(w -> w.contains("--filter-hidden-text") && w.contains(mode));
    }

    @Test
    void warnsWhenHybridSkipsTheFilter() throws IOException {
        File pdf = new File(PDF);
        assumeTrue(pdf.exists(), "Fixture not found: " + pdf.getAbsolutePath());

        List<String> warnings = captureWarnings(() -> {
            Config config = configWithFilterOn();
            config.setHybrid("docling-fast");
            config.getHybridConfig().setUrl(UNREACHABLE_HYBRID_URL);
            DocumentProcessor.processFile(pdf.getAbsolutePath(), config);
        });

        Assertions.assertTrue(warnedThatFilterIsIgnored(warnings, "--hybrid"),
            "Expected a WARNING that --hybrid ignores --filter-hidden-text. Got: " + warnings);
    }

    @Test
    void warnsWhenStructTreeSkipsTheFilter() throws IOException {
        File taggedPdf = new File(TAGGED_PDF);
        assumeTrue(taggedPdf.exists(), "Tagged PDF fixture not found: " + taggedPdf.getAbsolutePath());

        List<String> warnings = captureWarnings(() -> {
            Config config = configWithFilterOn();
            config.setUseStructTree(true);
            DocumentProcessor.processFile(taggedPdf.getAbsolutePath(), config);
        });

        Assertions.assertTrue(warnedThatFilterIsIgnored(warnings, "--use-struct-tree"),
            "Expected a WARNING that --use-struct-tree ignores --filter-hidden-text. Got: " + warnings);
    }

    @Test
    void noWarningInTheNativePipeline() throws IOException {
        File pdf = new File(PDF);
        assumeTrue(pdf.exists(), "Fixture not found: " + pdf.getAbsolutePath());

        List<String> warnings = captureWarnings(() ->
            DocumentProcessor.processFile(pdf.getAbsolutePath(), configWithFilterOn()));

        // Prove the native path ran (captureWarnings swallows body errors); otherwise
        // assertFalse could pass vacuously on an early failure.
        Assertions.assertTrue(Files.exists(tempDir.resolve(OUTPUT_JSON)),
            "Native path should have produced JSON output");
        Assertions.assertFalse(warnings.stream().anyMatch(w -> w.contains("--filter-hidden-text")),
            "The native pipeline applies the filter, so it must not warn. Got: " + warnings);
    }
}

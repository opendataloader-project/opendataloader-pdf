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
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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

    /**
     * A non-PDF file given directly on the command line must produce a clear
     * error message on stdout and a non-zero exit code, instead of silently
     * exiting with success.
     *
     * <p>Regression test for PDFDLOSP-5.
     */
    @Test
    void testNonPdfTopLevelArgumentEmitsErrorAndFails() throws IOException {
        Path png = tempDir.resolve("abcd.png");
        Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        String stdout = captureStdoutOf(() -> CLIMain.run(new String[]{png.toString()}));

        assertTrue(stdout.contains("'abcd.png' is not a PDF file"),
            "stdout must mention the offending file name and that it is not a PDF; got: " + stdout);
        assertTrue(stdout.contains("Input must be a PDF file or a folder containing PDF files"),
            "stdout must guide the user to valid input; got: " + stdout);
    }

    @Test
    void testNonPdfTopLevelArgumentReturnsNonZeroExitCode() throws IOException {
        Path png = tempDir.resolve("abcd.png");
        Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        int exitCode = CLIMain.run(new String[]{png.toString()});

        assertNotEquals(0, exitCode,
            "Exit code must be non-zero when the top-level argument is not a PDF");
    }

    /**
     * Non-PDF files inside a directory must continue to be silently skipped —
     * batch-folder processing with mixed file types is a legitimate use case
     * and must not regress.
     */
    @Test
    void testNonPdfFileInsideDirectoryIsSilentlySkipped() throws IOException {
        Path dir = tempDir.resolve("mixed");
        Files.createDirectory(dir);
        Files.write(dir.resolve("note.png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.write(dir.resolve("readme.txt"), "hello".getBytes(StandardCharsets.UTF_8));

        String stdout = captureStdoutOf(() -> CLIMain.run(new String[]{dir.toString()}));

        assertFalse(stdout.contains("is not a PDF file"),
            "Files discovered during directory traversal must not trigger the top-level error; got: " + stdout);
    }

    @Test
    void testDirectoryWithOnlyNonPdfFilesReturnsZero() throws IOException {
        Path dir = tempDir.resolve("non-pdf-only");
        Files.createDirectory(dir);
        Files.write(dir.resolve("note.png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        int exitCode = CLIMain.run(new String[]{dir.toString()});

        assertEquals(0, exitCode,
            "Directories containing only non-PDF files must succeed (silent skip)");
    }

    /**
     * A folder containing zero processable PDFs must emit a clear "No PDF files
     * found" message instead of exiting silently with status 0 — without this
     * the user cannot distinguish "wrong folder", "empty folder", and
     * "successful run". The path shown is the literal argument the user typed
     * (File#getPath), so {@code .} and trailing-slash inputs render correctly.
     *
     * <p>Regression test for PDFDLOSP-15.
     */
    @Test
    void testEmptyDirectoryEmitsNoPdfFoundMessage() throws IOException {
        Path dir = tempDir.resolve("empty");
        Files.createDirectory(dir);

        String[] stdoutHolder = new String[1];
        int exitCode = (int) runCapturingStdout(
            () -> CLIMain.run(new String[]{dir.toString()}),
            stdoutHolder);

        assertEquals(0, exitCode, "Empty folder is not an error");
        assertTrue(stdoutHolder[0].contains("No PDF files found in '" + dir + "'"),
            "stdout must report that no PDFs were found; got: " + stdoutHolder[0]);
    }

    @Test
    void testDirectoryWithOnlyNonPdfFilesEmitsNoPdfFoundMessage() throws IOException {
        Path dir = tempDir.resolve("basic_images");
        Files.createDirectory(dir);
        Files.write(dir.resolve("a.png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        Files.write(dir.resolve("b.jpg"), new byte[]{(byte) 0xFF, (byte) 0xD8});

        String[] stdoutHolder = new String[1];
        int exitCode = (int) runCapturingStdout(
            () -> CLIMain.run(new String[]{dir.toString()}),
            stdoutHolder);

        assertEquals(0, exitCode);
        assertTrue(stdoutHolder[0].contains("No PDF files found in '" + dir + "'"),
            "stdout must report that no PDFs were found; got: " + stdoutHolder[0]);
    }

    /**
     * Subdirectories must aggregate into the top-level summary rather than
     * emit their own "No PDF files found" / "Processed N..." lines — only the
     * user-supplied folder path appears in the output.
     */
    @Test
    void testNestedSubdirectoriesAggregateIntoTopLevelSummary() throws IOException {
        Path top = tempDir.resolve("top");
        Path sub = top.resolve("sub");
        Files.createDirectories(sub);
        Files.write(sub.resolve("nested.pdf"), "%PDF-1.4 minimal".getBytes(StandardCharsets.UTF_8));

        String[] stdoutHolder = new String[1];
        runCapturingStdout(
            () -> CLIMain.run(new String[]{top.toString()}),
            stdoutHolder);

        assertTrue(stdoutHolder[0].contains("Processed 1 PDF file in '" + top + "'"),
            "stdout must summarize at the top-level folder including nested PDFs; got: "
                + stdoutHolder[0]);
        assertFalse(stdoutHolder[0].contains("in '" + sub + "'"),
            "stdout must not emit a separate summary for nested subdirectories; got: "
                + stdoutHolder[0]);
    }

    @Test
    void testDirectoryWithMultiplePdfsEmitsProcessedSummary() throws IOException {
        Path dir = tempDir.resolve("docs");
        Files.createDirectory(dir);
        Files.write(dir.resolve("a.pdf"), "%PDF-1.4 minimal".getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve("b.pdf"), "%PDF-1.4 minimal".getBytes(StandardCharsets.UTF_8));

        String[] stdoutHolder = new String[1];
        runCapturingStdout(
            () -> CLIMain.run(new String[]{dir.toString()}),
            stdoutHolder);

        assertTrue(stdoutHolder[0].contains("Processed 2 PDF files in '" + dir + "'"),
            "stdout must summarize with plural 'files' when count > 1; got: "
                + stdoutHolder[0]);
    }

    /**
     * When the command line mixes a valid PDF argument with a top-level non-PDF
     * argument, the run must fail overall but only the non-PDF entry should
     * produce the user-facing error message.
     */
    @Test
    void testMixedTopLevelArgumentsReportOnlyNonPdfAndFailOverall() throws IOException {
        Path png = tempDir.resolve("note.png");
        Files.write(png, new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});
        Path pdf = tempDir.resolve("doc.pdf");
        Files.write(pdf, "%PDF-1.4 minimal".getBytes(StandardCharsets.UTF_8));

        String[] stdoutHolder = new String[1];
        int exitCode = (int) runCapturingStdout(
            () -> CLIMain.run(new String[]{pdf.toString(), png.toString()}),
            stdoutHolder);

        assertNotEquals(0, exitCode,
            "Exit code must be non-zero when any top-level argument is not a PDF");
        assertTrue(stdoutHolder[0].contains("'note.png' is not a PDF file"),
            "stdout must call out the non-PDF argument by name; got: " + stdoutHolder[0]);
    }

    /**
     * Password-protected PDF with no password supplied must produce a single
     * user-friendly error message (no verapdf stack trace) and exit non-zero.
     *
     * <p>Regression test for PDFDLOSP-9.
     */
    @Test
    void testPasswordProtectedWithoutPasswordEmitsFriendlyError() throws IOException {
        Path pdf = createPasswordProtectedPdf(tempDir.resolve("locked.pdf"), "1234");

        String[] holder = new String[1];
        int exitCode = (int) runCapturingStdout(
            () -> CLIMain.run(new String[]{pdf.toString()}),
            holder);

        assertNotEquals(0, exitCode, "exit code must be non-zero");
        assertTrue(holder[0].contains("'locked.pdf' is password-protected"),
            "stdout must call out the file by name and explain it is password-protected; got: " + holder[0]);
        assertTrue(holder[0].contains("--password"),
            "stdout must guide the user to the --password option; got: " + holder[0]);
        assertFalse(holder[0].contains("InvalidPasswordException"),
            "stdout must not expose internal exception type; got: " + holder[0]);
        assertFalse(holder[0].contains("at org.verapdf"),
            "stdout must not expose a stack trace; got: " + holder[0]);
    }

    /**
     * Password-protected PDF with an incorrect password must produce a single
     * user-friendly error message (no verapdf stack trace) and exit non-zero.
     *
     * <p>Regression test for PDFDLOSP-9.
     */
    @Test
    void testPasswordProtectedWithWrongPasswordEmitsFriendlyError() throws IOException {
        Path pdf = createPasswordProtectedPdf(tempDir.resolve("locked.pdf"), "1234");

        String[] holder = new String[1];
        int exitCode = (int) runCapturingStdout(
            () -> CLIMain.run(new String[]{pdf.toString(), "--password", "wrongpw"}),
            holder);

        assertNotEquals(0, exitCode, "exit code must be non-zero");
        assertTrue(holder[0].contains("Incorrect password for 'locked.pdf'"),
            "stdout must report incorrect password and the file name; got: " + holder[0]);
        assertFalse(holder[0].contains("InvalidPasswordException"),
            "stdout must not expose internal exception type; got: " + holder[0]);
        assertFalse(holder[0].contains("at org.verapdf"),
            "stdout must not expose a stack trace; got: " + holder[0]);
    }

    /**
     * Correct password must keep the existing success path: exit code 0 and
     * the JSON output produced next to the input. Regression guard for the
     * happy case while fixing PDFDLOSP-9.
     */
    @Test
    void testPasswordProtectedWithCorrectPasswordSucceeds() throws IOException {
        Path pdf = createPasswordProtectedPdf(tempDir.resolve("locked.pdf"), "1234");

        int exitCode = CLIMain.run(new String[]{pdf.toString(), "--password", "1234"});

        assertEquals(0, exitCode, "correct password must keep exit code 0");
        assertTrue(Files.exists(tempDir.resolve("locked.json")),
            "JSON output must be produced next to the input PDF");
    }

    private static Path createPasswordProtectedPdf(Path target, String password) throws IOException {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            AccessPermission permissions = new AccessPermission();
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password, password, permissions);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            document.save(target.toFile());
        }
        return target;
    }

    // --- PDFDLOSP-14: magic-number guard regressions ----------------------

    private static final byte[] JPEG_PREFIX = new byte[]{
        (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
        0x00, 0x10, 'J', 'F', 'I', 'F', 0x00
    };

    /**
     * A .pdf-named file whose content is not actually PDF must produce the
     * user-facing magic-number error on stdout, fail with non-zero exit, AND
     * must not leak any veraPDF internal stack trace. Regression for
     * PDFDLOSP-14.
     */
    @Test
    void testPdfExtensionWithNonPdfContentEmitsMagicNumberError() throws IOException {
        Path fakePdf = tempDir.resolve("fake.pdf");
        Files.write(fakePdf, JPEG_PREFIX);

        String[] stdoutHolder = new String[1];
        int exitCode = (int) runCapturingStdout(
            () -> CLIMain.run(new String[]{fakePdf.toString()}),
            stdoutHolder);

        assertNotEquals(0, exitCode,
            "Exit code must be non-zero when input content is not PDF");
        assertTrue(stdoutHolder[0].contains("'fake.pdf' is not a valid PDF file (missing %PDF- header)"),
            "stdout must contain the magic-number error; got: " + stdoutHolder[0]);
    }

    /**
     * Stack-trace leakage guard: the veraPDF internals must NEVER appear on
     * stdout for the non-PDF-content case. This is the core regression we
     * are preventing — if the catch branch is reverted or the guard moves
     * back behind PDDocument construction, this test fails.
     */
    @Test
    void testPdfExtensionWithNonPdfContentDoesNotLeakStackTrace() throws IOException {
        Path fakePdf = tempDir.resolve("fake.pdf");
        Files.write(fakePdf, JPEG_PREFIX);

        String[] stdoutHolder = new String[1];
        runCapturingStdout(
            () -> CLIMain.run(new String[]{fakePdf.toString()}),
            stdoutHolder);

        String out = stdoutHolder[0];
        assertFalse(out.toLowerCase(java.util.Locale.ROOT).contains("verapdf"),
            "Output must not contain 'verapdf'; got: " + out);
        assertFalse(out.contains("java.io.IOException:"),
            "Output must not contain 'java.io.IOException:'; got: " + out);
        assertFalse(out.contains("\tat "),
            "Output must not contain a Java stack trace ('\\tat '); got: " + out);
    }

    /**
     * A directory containing a .pdf-named non-PDF file alongside another
     * non-PDF file must (a) NOT print the top-level magic-number error to
     * stdout, (b) still exit 0 (silent skip preserves PR #496's batch-folder
     * semantics), and (c) emit a single WARNING log line so operators can
     * investigate why a file was skipped. Captures CLIMain's JUL logger
     * directly because java.util.logging routes WARNING to a ConsoleHandler
     * on stderr by default and may be suppressed entirely under --quiet.
     */
    @Test
    void testPdfExtensionNonPdfInsideDirectoryIsSilentlySkipped() throws IOException {
        Path dir = tempDir.resolve("mixed");
        Files.createDirectory(dir);
        Files.write(dir.resolve("fake.pdf"), JPEG_PREFIX);
        Files.write(dir.resolve("note.png"), new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47});

        List<LogRecord> records = new ArrayList<>();
        String[] stdoutHolder = new String[1];
        int exitCode = (int) captureLogsOf(records, () -> runCapturingStdout(
            () -> CLIMain.run(new String[]{dir.toString()}),
            stdoutHolder)).intValue();

        assertEquals(0, exitCode,
            "Directory containing only invalid files must still exit 0 (silent skip)");
        assertFalse(stdoutHolder[0].contains("Error:"),
            "Directory traversal must not surface 'Error:' on stdout; got: " + stdoutHolder[0]);
        assertFalse(stdoutHolder[0].contains("missing %PDF- header"),
            "Directory traversal must not surface the magic-number error on stdout; got: "
            + stdoutHolder[0]);
        long warningMatches = records.stream().filter(r ->
                r.getLevel() == Level.WARNING
                && r.getMessage() != null
                && r.getMessage().contains("'fake.pdf'")
                && r.getMessage().contains("missing %PDF- header")).count();
        assertEquals(1L, warningMatches,
            "Exactly one WARNING log must name the offending file and the missing header; "
            + "got: " + records);
    }

    /**
     * A genuinely corrupted PDF (has %PDF- header, body broken) is OUT of
     * scope for this fix and must continue to fail via the existing SEVERE
     * path — NOT through the new magic-number branch. This test pins that
     * behavior by asserting (a) the user-facing magic-number error never
     * appears, and (b) the SEVERE log line from CLIMain.processFile is
     * actually emitted (proving the existing SEVERE path was the one taken).
     */
    @Test
    void testCorruptPdfStillFailsViaExistingPath() throws IOException {
        Path corrupt = tempDir.resolve("corrupt.pdf");
        // Valid header, but nothing else — veraPDF will fail at parse time.
        Files.write(corrupt, "%PDF-1.4\n<garbage>".getBytes(StandardCharsets.UTF_8));

        List<LogRecord> records = new ArrayList<>();
        String[] stdoutHolder = new String[1];
        int exitCode = (int) captureLogsOf(records, () -> runCapturingStdout(
            () -> CLIMain.run(new String[]{corrupt.toString()}),
            stdoutHolder)).intValue();

        assertNotEquals(0, exitCode,
            "Corrupt PDF must still fail (non-zero exit)");
        assertFalse(stdoutHolder[0].contains("missing %PDF- header"),
            "Corrupt-but-headered PDFs must NOT be misreported as missing the header; got: "
            + stdoutHolder[0]);
        assertTrue(records.stream().anyMatch(r ->
                r.getLevel() == Level.SEVERE
                && r.getMessage() != null
                && r.getMessage().contains("Exception during processing file")),
            "Corrupt PDF must take the SEVERE path (not the magic-number branch); got: "
            + records);
    }

    /**
     * Runs {@code action} with a temporary {@link Handler} attached to
     * {@link CLIMain}'s JUL logger, collecting every {@link LogRecord} into
     * {@code sink}. Restores the prior handler list and level on exit.
     *
     * <p>JUL is global; this helper assumes sequential test execution
     * (JUnit 5 + Surefire default). Returns whatever {@code action} returned.
     */
    private static <T> T captureLogsOf(List<LogRecord> sink, java.util.concurrent.Callable<T> action) {
        Logger logger = Logger.getLogger(CLIMain.class.getCanonicalName());
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

    private static String captureStdoutOf(Runnable action) {
        String[] holder = new String[1];
        runCapturingStdout(() -> {
            action.run();
            return 0;
        }, holder);
        return holder[0];
    }

    // System.setOut is JVM-global; this helper assumes sequential test
    // execution (JUnit 5 + maven-surefire default). Revisit if parallel
    // test execution is enabled — concurrent captures would interleave.
    private static long runCapturingStdout(java.util.concurrent.Callable<Integer> action, String[] stdoutHolder) {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            int exitCode = action.call();
            stdoutHolder[0] = buffer.toString(StandardCharsets.UTF_8);
            return exitCode;
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        } finally {
            System.setOut(originalOut);
        }
    }
}

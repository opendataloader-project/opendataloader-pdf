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

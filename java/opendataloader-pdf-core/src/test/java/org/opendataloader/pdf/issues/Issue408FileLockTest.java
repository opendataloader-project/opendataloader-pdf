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
package org.opendataloader.pdf.issues;

import com.sun.management.UnixOperatingSystemMXBean;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.verapdf.tools.StaticResources;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Issue #408: after {@code processFile} returns, the JVM must not keep the input PDF open.
 *
 * <p>The fix in #410 closes the veraPDF PDDocument. That releases two resources:
 * the handle on the input file, and the temporary files ({@code tmp_pdf_file*.pdf}) that
 * veraPDF uses for decoded streams larger than 10240 bytes. Each test covers one check:
 * <ul>
 *   <li>The temporary-file checks run on all systems. The surefire configuration gives
 *       the test JVM its own {@code java.io.tmpdir}, so files of other processes do not count.</li>
 *   <li>The input-handle check reads {@code /proc/self/fd}, so it runs on Linux (CI).</li>
 *   <li>The open-file count runs on Linux and macOS.</li>
 *   <li>{@code Files.delete} fails on Windows while the input handle is open.</li>
 * </ul>
 *
 * <p>The samples are synthetic. The output stays in {@code target/test-samples/issue-408*}.
 */
class Issue408FileLockTest {

    private static final int PAGES = 3;
    private static final String TEXT = "A synthetic sample for issue 408.";
    // Pushes the object stream past veraPDF's 10240-byte in-memory limit,
    // so veraPDF backs it with a temporary file that only PDDocument.close() releases.
    private static final int PADDING_CHARS = 12_000;
    private static final int IMAGE_SIZE = 64;
    private static final Path PROC_SELF_FD = Paths.get("/proc/self/fd");

    @Test
    void sampleMakesVeraPdfUseATemporaryFile() throws IOException {
        // Positive control: the temporary-file and handle checks prove something only if this happens.
        Path input = TestSamples.emptyDirectory("issue-408-control").resolve("input.pdf");
        writeSample(input, false);

        Set<String> before = veraPdfTemporaryFiles();
        org.verapdf.pd.PDDocument document = new org.verapdf.pd.PDDocument(input.toString());
        try {
            document.getDocument().getObjects();
            Set<String> created = veraPdfTemporaryFiles();
            created.removeAll(before);
            assertThat(created).as("veraPDF temporary files while the sample is open").isNotEmpty();
            if (Files.isDirectory(PROC_SELF_FD)) {
                assertThat(openHandlesTo(input)).as("handles on the input while the sample is open").isPositive();
            }
        } finally {
            document.close();
        }
        assertThat(veraPdfTemporaryFiles()).isSubsetOf(before);
    }

    @Test
    void temporaryFilesAreReleasedAfterProcessing() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-408");
        Path input = directory.resolve("input.pdf");
        writeSample(input, false);

        Set<String> before = veraPdfTemporaryFiles();
        process(input, directory.resolve("output"));

        assertThat(veraPdfTemporaryFiles()).isSubsetOf(before);
        Files.delete(input);
        assertThat(input).doesNotExist();
    }

    @Test
    void noHandleOnTheInputFileAfterProcessing() throws IOException {
        assumeTrue(Files.isDirectory(PROC_SELF_FD), "needs /proc/self/fd (Linux)");
        Path directory = TestSamples.emptyDirectory("issue-408-handle");
        Path input = directory.resolve("input.pdf");
        writeSample(input, false);

        process(input, directory.resolve("output"));

        assertThat(openHandlesTo(input)).isZero();
    }

    @Test
    void cleanupRunsAfterEachProcessing() throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-408-repeated");
        for (int run = 1; run <= 3; run++) {
            processAndDelete(directory, run, false);
            assertThat(StaticResources.getDocument()).isNull();
        }
    }

    @Test
    void openFileCountDoesNotGrowOverRepeatedProcessing() throws IOException {
        OperatingSystemMXBean system = ManagementFactory.getOperatingSystemMXBean();
        assumeTrue(system instanceof UnixOperatingSystemMXBean, "needs a Unix JVM");
        UnixOperatingSystemMXBean unix = (UnixOperatingSystemMXBean) system;
        Path directory = TestSamples.emptyDirectory("issue-408-open-files");
        processAndDelete(directory, 0, false);           // warm-up: the first run opens class files
        long before = unix.getOpenFileDescriptorCount();

        for (int run = 1; run <= 3; run++) {
            processAndDelete(directory, run, false);
        }

        assertThat(unix.getOpenFileDescriptorCount()).isLessThanOrEqualTo(before);
    }

    @Disabled("The input stays open for PDFs with images: wcag-algorithms 1.31.44"
        + " ImagesUtils.loadDocument does not close its FileInputStream. Re-check after a"
        + " wcag-algorithms upgrade. A GC can close the stream first, so a pass does not prove a fix.")
    @Test
    void noHandleOnTheInputFileForPdfWithImages() throws IOException {
        assumeTrue(Files.isDirectory(PROC_SELF_FD), "needs /proc/self/fd (Linux)");
        Path directory = TestSamples.emptyDirectory("issue-408-images");
        processAndDelete(directory, 0, true);            // warm-up: loads the image classes
        Path input = directory.resolve("input-images.pdf");
        writeSample(input, true);

        process(input, directory.resolve("output-images"));

        assertThat(openHandlesTo(input)).isZero();
    }

    private static void processAndDelete(Path directory, int run, boolean withImage) throws IOException {
        Path input = directory.resolve("input-" + run + ".pdf");
        writeSample(input, withImage);
        process(input, directory.resolve("output-" + run));
        Files.delete(input);
        assertThat(input).doesNotExist();
    }

    private static void process(Path input, Path output) throws IOException {
        Config config = new Config();
        config.setOutputFolder(output.toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(input.toString(), config);
    }

    private static void writeSample(Path pdf, boolean withImage) throws IOException {
        try (PDDocument document = TestSamples.textDocument(PAGES, TEXT)) {
            document.getDocumentInformation().setCustomMetadataValue("Padding", "x".repeat(PADDING_CHARS));
            if (withImage) {
                BufferedImage image = new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_RGB);
                PDImageXObject xObject = LosslessFactory.createFromImage(document, image);
                try (PDPageContentStream content = new PDPageContentStream(
                    document, document.getPage(0), PDPageContentStream.AppendMode.APPEND, true)) {
                    content.drawImage(xObject, 72, 500, IMAGE_SIZE, IMAGE_SIZE);
                }
            }
            document.save(pdf.toFile());
        }
    }

    private static Set<String> veraPdfTemporaryFiles() {
        Set<String> names = new HashSet<>();
        File[] files = new File(System.getProperty("java.io.tmpdir"))
            .listFiles((folder, name) -> name.startsWith("tmp_pdf_file") && name.endsWith(".pdf"));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }

    /** Counts the entries in /proc/self/fd that point to the file. */
    private static long openHandlesTo(Path file) throws IOException {
        Path target = file.toRealPath();
        long count = 0;
        try (DirectoryStream<Path> handles = Files.newDirectoryStream(PROC_SELF_FD)) {
            for (Path handle : handles) {
                try {
                    if (target.equals(Files.readSymbolicLink(handle))) {
                        count++;
                    }
                } catch (IOException closedMeanwhile) {
                    // The handle closed after the listing. It does not point to the file now.
                }
            }
        }
        return count;
    }
}

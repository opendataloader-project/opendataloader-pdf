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
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.opendataloader.pdf.api.cli.CLIOptions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #492: {@code --to-stdout} with a format that stdout cannot carry ends with an error.
 *
 * <p>Only one of text and Markdown can go to stdout. Without this check, the CLI writes nothing and ends with exit
 * code 0 for JSON (also the default format), HTML, PDF, and tagged PDF, and it writes only one of two requested
 * formats. With the check, the CLI ends with exit code 2 before it reads the PDF. The first line of stdout names the
 * formats that {@code --to-stdout} supports.
 *
 * <p>The sample is a one-page PDF, made in the test: a title in 24 pt bold and one line of text in 10 pt. Markdown
 * gives the title a heading marker, and text does not.
 */
class Issue492ToStdoutFormatTest {

    private static final String TITLE = "Stdout sample title";
    private static final String TEXT = "Stdout sample text";
    private static final String NOTHING = "--to-stdout wrote nothing and ended with exit code 0";
    private static final String DROPPED = "--to-stdout dropped a requested format and ended with exit code 0";

    @TempDir
    Path dir;

    /** {@code --to-stdout} without {@code --format}: the default format is JSON, which stdout cannot carry. */
    @Test
    void toStdoutWithoutAFormatEndsWithAnError() throws IOException {
        assertRefused(run("--to-stdout"), NOTHING);
    }

    /** {@code --to-stdout} with one format that stdout cannot carry. */
    @ParameterizedTest
    @ValueSource(strings = {"json", "html", "pdf", "tagged-pdf"})
    void formatThatStdoutCannotCarryEndsWithAnError(String format) throws IOException {
        assertRefused(run("--to-stdout", "--format", format), NOTHING);
    }

    /** {@code --to-stdout} with two formats: stdout carries one, and the CLI drops the other without a message. */
    @ParameterizedTest
    @ValueSource(strings = {"markdown,text", "markdown,json", "text,html"})
    void twoFormatsEndWithAnError(String formats) throws IOException {
        assertRefused(run("--to-stdout", "--format", formats), DROPPED);
    }

    /** The help of {@code --to-stdout} names the formats that it supports. */
    @Test
    void helpOfToStdoutNamesTheFormats() {
        String description = CLIOptions.defineOptions().getOption("to-stdout").getDescription();
        assertTrue(description.contains("text") && description.contains("markdown"),
            "the help of --to-stdout does not name its formats: " + description);
    }

    /** Guard: one of text and Markdown, also with a deprecated value of {@code --format}, goes to stdout. */
    @ParameterizedTest
    @ValueSource(strings = {"text", "markdown", "markdown-with-images", "markdown-with-html"})
    void textOrMarkdownGoesToStdout(String format) throws IOException {
        assertWritten(run("--to-stdout", "--format", format), !format.equals("text"));
    }

    /**
     * Guard: the hidden option {@code --markdown}, kept for compatibility, goes to stdout. It does not turn off the
     * default JSON, which stdout never carried.
     */
    @Test
    void hiddenMarkdownOptionGoesToStdout() throws IOException {
        assertWritten(run("--to-stdout", "--markdown"), true);
    }

    /** Guard: without {@code --to-stdout}, two formats still give two files. */
    @Test
    void twoFormatsWithoutToStdoutGiveTwoFiles() throws IOException {
        Result result = run("--format", "markdown,json");
        assertEquals(0, result.exitCode, "the conversion to files failed: " + result.stdout);
        assertTrue(Files.exists(dir.resolve("out").resolve("sample.md")), "no Markdown file");
        assertTrue(Files.exists(dir.resolve("out").resolve("sample.json")), "no JSON file");
    }

    private void assertRefused(Result result, String symptom) throws IOException {
        assertEquals(2, result.exitCode, symptom + " (exit code " + result.exitCode + "): " + result.stdout);
        String firstLine = result.stdout.lines().findFirst().orElse("");
        assertTrue(firstLine.contains("--to-stdout") && firstLine.contains("text") && firstLine.contains("markdown"),
            "the message does not name the formats of --to-stdout: " + firstLine);
        assertTrue(outputFiles().isEmpty(), "the CLI wrote output files: " + outputFiles());
    }

    private void assertWritten(Result result, boolean markdown) {
        assertEquals(0, result.exitCode, "the conversion to stdout failed: " + result.stdout);
        assertTrue(result.stdout.contains(TEXT), "stdout does not have the text of the PDF: " + result.stdout);
        boolean heading = result.stdout.lines().anyMatch(line -> line.matches("#{1,6} " + TITLE));
        assertEquals(markdown, heading, (markdown ? "stdout is not Markdown: " : "stdout is not text: ") + result.stdout);
    }

    private List<Path> outputFiles() throws IOException {
        Path out = dir.resolve("out");
        if (!Files.exists(out)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(out)) {
            return files.filter(Files::isRegularFile).collect(Collectors.toList());
        }
    }

    private Result run(String... options) throws IOException {
        Path pdf = dir.resolve("sample.pdf");
        if (!Files.exists(pdf)) {
            try (PDDocument document = new PDDocument()) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 24);
                    content.newLineAtOffset(72, 700);
                    content.showText(TITLE);
                    content.endText();
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    content.newLineAtOffset(72, 660);
                    content.showText(TEXT);
                    content.endText();
                }
                document.save(pdf.toFile());
            }
        }
        List<String> args = new ArrayList<>(Arrays.asList(options));
        // No --quiet: it turns off the loggers of the whole JVM, and later tests read log records.
        args.addAll(List.of("--output-dir", dir.resolve("out").toString(), pdf.toString()));
        PrintStream originalOut = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode;
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            exitCode = CLIMain.run(args.toArray(new String[0]));
        } finally {
            System.setOut(originalOut);
        }
        return new Result(exitCode, buffer.toString(StandardCharsets.UTF_8));
    }

    private static final class Result {
        final int exitCode;
        final String stdout;

        Result(int exitCode, String stdout) {
            this.exitCode = exitCode;
            this.stdout = stdout;
        }
    }
}

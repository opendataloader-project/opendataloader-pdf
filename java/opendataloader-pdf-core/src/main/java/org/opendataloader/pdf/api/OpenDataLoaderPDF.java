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
package org.opendataloader.pdf.api;

import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main entry point for the opendataloader-pdf library.
 * Use the static method {@link #processFile(String, Config)} to process a PDF.
 */
public final class OpenDataLoaderPDF {

    private static final Logger LOGGER = Logger.getLogger(OpenDataLoaderPDF.class.getName());

    private OpenDataLoaderPDF() {
    }

    /**
     * Processes a PDF file to extract its content and structure based on the provided configuration.
     *
     * <p>Input validation is performed before processing. Callers may catch
     * {@link IllegalArgumentException} to skip invalid entries in a batch loop:
     * <pre>{@code
     * for (String pdf : paths) {
     *     try {
     *         OpenDataLoaderPDF.processFile(pdf, config);
     *     } catch (IllegalArgumentException e) {
     *         // skip invalid path and continue
     *     }
     * }
     * }</pre>
     *
     * @param inputPdfName The path to the input PDF file.
     * @param config       The configuration object specifying output formats and other options.
     * @throws IllegalArgumentException if {@code inputPdfName} is null, blank, syntactically
     *                                  invalid, does not exist, is not a regular file, or does
     *                                  not have a {@code .pdf} extension.
     * @throws IOException              If an error occurs during file reading or processing.
     */
    public static void processFile(String inputPdfName, Config config) throws IOException {
        validateInputFile(inputPdfName);
        DocumentProcessor.processFile(inputPdfName, config);
    }

    /**
     * Validates that {@code inputPdfName} refers to an existing, regular PDF file.
     *
     * @param inputPdfName the path string to validate
     * @throws IllegalArgumentException if the path is null, blank, syntactically invalid,
     *                                  non-existent, not a regular file, or lacks a {@code .pdf} extension
     */
    private static void validateInputFile(String inputPdfName) {
        if (inputPdfName == null || inputPdfName.isBlank()) {
            LOGGER.log(Level.WARNING, "Input PDF path is null or blank");
            throw new IllegalArgumentException("Input PDF path must not be null or blank");
        }

        final Path path;
        try {
            path = Paths.get(inputPdfName);
        } catch (InvalidPathException ex) {
            LOGGER.log(Level.WARNING, "Syntactically invalid path supplied");
            throw new IllegalArgumentException("Invalid file path: " + ex.getReason(), ex);
        }

        final String fileName = path.getFileName().toString();

        if (!Files.exists(path)) {
            LOGGER.log(Level.WARNING, "PDF file does not exist: {0}", fileName);
            throw new IllegalArgumentException("File does not exist: " + fileName);
        }

        if (!Files.isRegularFile(path)) {
            LOGGER.log(Level.WARNING, "Path does not point to a regular file: {0}", fileName);
            throw new IllegalArgumentException("Path is not a regular file: " + fileName);
        }

        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            LOGGER.log(Level.WARNING, "File does not have a .pdf extension: {0}", fileName);
            throw new IllegalArgumentException("File must have a .pdf extension: " + fileName);
        }
    }

    /**
     * Shuts down any cached resources used by the library.
     *
     * <p>This method should be called when processing is complete, typically at CLI exit.
     * It releases resources such as HTTP client thread pools used for hybrid mode backends.
     */
    public static void shutdown() {
        HybridClientFactory.shutdown();
    }
}

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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The main entry point for the opendataloader-pdf library.
 * Use the static method {@link #processFile(String, Config)} to process a PDF.
 */
public final class OpenDataLoaderPDF {

    private static final Logger LOGGER = Logger.getLogger(OpenDataLoaderPDF.class.getCanonicalName());

    private OpenDataLoaderPDF() {
    }

    /**
     * Processes a PDF file to extract its content and structure based on the provided configuration.
     *
     * @param inputPdfName The path to the input PDF file.
     * @param config       The configuration object specifying output formats and other options.
     * @throws IOException If an error occurs during file reading or processing.
     */
    public static void processFile(String inputPdfName, Config config) throws IOException {
        if (!isValidInputFile(inputPdfName)) return;

        DocumentProcessor.processFile(inputPdfName, config);
    }

    /**
     * Validates whether the given path refers to a valid PDF file.
     *
     * @param inputPdfName the path to the input file
     * @return {@code true} if the path is non-null, points to an existing
     *         regular file, and has a .pdf extension; {@code false} otherwise
     */
    private static boolean isValidInputFile(String inputPdfName) {

        if (inputPdfName == null || inputPdfName.isBlank()) {
            LOGGER.log(Level.WARNING, "Input file path is null or empty");
            return false;
        }

        Path path = Paths.get(inputPdfName);

        if (!Files.exists(path)) {
            LOGGER.log(Level.WARNING, () -> "File does not exist: " + inputPdfName);
            return false;
        }

        if (!Files.isRegularFile(path)) {
            LOGGER.log(Level.WARNING, () -> "Not a valid file: " + inputPdfName);
            return false;
        }

        if (!inputPdfName.toLowerCase().endsWith(".pdf")) {
            LOGGER.log(Level.WARNING, () -> "Not a PDF file: " + inputPdfName);
            return false;
        }

        return true;
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

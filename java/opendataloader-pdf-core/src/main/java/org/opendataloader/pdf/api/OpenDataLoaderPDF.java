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

    private static final Logger LOGGER = Logger.getLogger(OpenDataLoaderPDF.class.getCanonicalName());

    private OpenDataLoaderPDF() {
    }

    /**
     * Processes a PDF file to extract its content and structure based on the provided configuration.
     *
     * @param inputPdfName The path to the input PDF file.
     * @param config       The configuration object specifying output formats and other options.
     *
     */
    public static void processFile(String inputPdfName, Config config) {
        try {
            validateInputFile(inputPdfName);
            DocumentProcessor.processFile(inputPdfName, config);
        } catch (IllegalArgumentException | IOException ex) {
            LOGGER.log(Level.WARNING, ex.getMessage());
        }
    }

    /**
     * Validates whether the given path refers to a valid PDF file.
     *
     * @param inputPdfName the path to the input file
     */
    private static void validateInputFile(String inputPdfName) {

        if (inputPdfName == null || inputPdfName.isBlank()) {
            throw new IllegalArgumentException("Input PDF name is null or Empty");
        }

        final Path path;

        try {
            path = Paths.get(inputPdfName);
        } catch (InvalidPathException ex) {
            throw new IllegalArgumentException("Invalid Path: " + inputPdfName);
        }

        if (!Files.exists(path)) {
            throw new IllegalArgumentException("File not fount at " + inputPdfName + " location");
        }

        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Not a valid file " + inputPdfName);
        }

        if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Not a PDF file");
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

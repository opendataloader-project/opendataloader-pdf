/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.api;

import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.IOException;

/**
 * The main entry point for the opendataloader-pdf library.
 * Use the static method {@link #processFile(String, Config)} to process a PDF.
 */
public final class OpenDataLoaderPDF {

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
        DocumentProcessor.processFile(inputPdfName, config);
    }
}

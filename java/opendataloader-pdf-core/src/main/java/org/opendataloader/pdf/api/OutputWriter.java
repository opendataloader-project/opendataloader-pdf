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

import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.processors.ExtractionResult;

import java.io.IOException;

/**
 * Writes configured output files (JSON, Markdown, HTML, PDF, text, images,
 * tagged PDF) from a pre-computed {@link ExtractionResult}.
 *
 * <p>Use this when you have already run extraction once (e.g. via
 * {@link AutoTagger#tag(String, ExtractionResult)}) and want to emit file
 * outputs from that same result without re-extracting.
 *
 * <p>Typical two-phase usage:
 * <pre>{@code
 * Config config = new Config();
 * config.setOutputFolder("/out");
 * config.setGenerateJSON(true);
 * config.setGenerateMarkdown(true);
 *
 * // Phase 1: extract once
 * ExtractionResult extraction =
 *     org.opendataloader.pdf.processors.DocumentProcessor.extractContents(
 *         "input.pdf", config);
 *
 * // Phase 2a: write output files
 * OutputWriter.writeOutputs("input.pdf", extraction, config);
 *
 * // Phase 2b: tag in-memory and reuse the same extraction
 * try (TaggingResult tagged = AutoTagger.tag("input.pdf", extraction)) {
 *     // ... use tagged.getDocument()
 * }
 * }</pre>
 *
 * <p>For the single-call extraction-and-output pipeline, use
 * {@link OpenDataLoaderPDF#processFile} instead.
 */
public final class OutputWriter {

    private OutputWriter() {
    }

    /**
     * Writes the output files configured on {@code config} (e.g.
     * {@code generateJSON}, {@code generateMarkdown}, {@code generateHtml},
     * {@code generatePDF}, {@code generateTaggedPDF}, {@code generateText})
     * using the supplied pre-computed extraction.
     *
     * <p>This method does <em>not</em> re-run extraction. Output behaviour is
     * identical to {@link OpenDataLoaderPDF#processFile} for the same
     * {@link Config}, including stdout mode, image directory resolution, and
     * tagged-PDF generation.
     *
     * @param inputPdfName path to the input PDF file (used for filename derivation
     *                     and tagged-PDF / annotated-PDF re-saves; not re-parsed)
     * @param extraction   pre-computed extraction result (from
     *                     {@code DocumentProcessor.extractContents})
     * @param config       configuration controlling which output formats to emit
     * @throws IOException if writing any output file fails
     */
    public static void writeOutputs(String inputPdfName, ExtractionResult extraction, Config config)
            throws IOException {
        DocumentProcessor.generateOutputs(inputPdfName, extraction.getContents(), config,
                extraction.getElementMetadata());
    }
}

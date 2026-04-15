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

import org.opendataloader.pdf.processors.AutoTaggingProcessor;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;

import java.io.File;
import java.io.IOException;

/**
 * Public API for standalone PDF auto-tagging.
 *
 * <p>Returns an in-memory tagged {@link PDDocument} without writing intermediate files.
 * Use {@link OpenDataLoaderPDF#processFile} for the full extraction + output pipeline.
 *
 * <p>Example usage:
 * <pre>{@code
 * Config config = new Config();
 * config.setHybrid("docling-fast");
 *
 * try (TaggingResult result = AutoTagger.tag("input.pdf", config)) {
 *     PDDocument tagged = result.getDocument();
 *     // use tagged document...
 *     result.saveTo("output_tagged.pdf");
 * }
 * AutoTagger.shutdown();
 * }</pre>
 */
public final class AutoTagger {

    private AutoTagger() {
    }

    /**
     * Extract content from a PDF and produce a tagged PDF document in-memory.
     * Output format flags in config are ignored — this method only produces
     * a tagged PDDocument.
     *
     * @param inputPdf path to the input PDF file
     * @param config   configuration (extraction + hybrid fields are used;
     *                 output format flags are ignored)
     * @return result containing the tagged PDDocument and timing metadata
     * @throws IOException if unable to read or process the PDF
     */
    public static TaggingResult tag(String inputPdf, Config config) throws IOException {
        ExtractionResult extraction = DocumentProcessor.extractContents(inputPdf, config);
        return tag(inputPdf, extraction);
    }

    /**
     * Tag a PDF document from a previously computed {@link ExtractionResult}.
     * Use this when you need both tagged PDF and other output formats from the
     * same extraction — call {@link DocumentProcessor#extractContents} once,
     * then pass the result here and to other output generators.
     *
     * @param inputPdf   path to the input PDF file (used for metadata)
     * @param extraction pre-computed extraction result
     * @return result containing the tagged PDDocument and timing metadata
     * @throws IOException if unable to tag the document
     */
    public static TaggingResult tag(String inputPdf, ExtractionResult extraction) throws IOException {
        long t0 = System.nanoTime();
        PDDocument document = StaticResources.getDocument();
        AutoTaggingProcessor.tagDocument(new File(inputPdf), document, extraction.getContents());
        long taggingNs = System.nanoTime() - t0;

        return new TaggingResult(document, extraction.getExtractionNs(), taggingNs,
            extraction.getHybridTimings());
    }

    /**
     * Release hybrid client resources. Call when processing is complete.
     */
    public static void shutdown() {
        OpenDataLoaderPDF.shutdown();
    }
}

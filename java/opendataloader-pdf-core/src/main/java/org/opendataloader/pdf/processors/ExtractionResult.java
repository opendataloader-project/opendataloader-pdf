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
package org.opendataloader.pdf.processors;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.hybrid.ElementMetadata;
import org.verapdf.pd.PDDocument;
import org.verapdf.wcag.algorithms.entities.IObject;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Internal result of the extraction pipeline (preprocessing + content extraction + sanitization).
 * Carries the extracted contents and timing metadata for use by AutoTagger.
 *
 * <p>Implements {@link AutoCloseable} for use with try-with-resources. The caller is
 * responsible for closing this result, which releases the PDF document: the handle on the
 * input file and the temporary files of veraPDF.
 */
public class ExtractionResult implements AutoCloseable {

    private final List<List<IObject>> contents;
    private final long extractionNs;
    private final JsonNode hybridTimings;
    private final Map<Long, ElementMetadata> elementMetadata;
    private final PDDocument document;

    /** Used by {@link DocumentProcessor#extractContents}: the result owns the document. */
    ExtractionResult(List<List<IObject>> contents, long extractionNs, JsonNode hybridTimings,
                     Map<Long, ElementMetadata> elementMetadata, PDDocument document) {
        this.contents = contents;
        this.extractionNs = extractionNs;
        this.hybridTimings = hybridTimings;
        this.elementMetadata = elementMetadata != null ? elementMetadata : Collections.emptyMap();
        this.document = document;
    }

    public ExtractionResult(List<List<IObject>> contents, long extractionNs, JsonNode hybridTimings,
                             Map<Long, ElementMetadata> elementMetadata) {
        this(contents, extractionNs, hybridTimings, elementMetadata, null);
    }

    public ExtractionResult(List<List<IObject>> contents, long extractionNs, JsonNode hybridTimings) {
        this(contents, extractionNs, hybridTimings, Collections.emptyMap());
    }

    public List<List<IObject>> getContents() {
        return contents;
    }

    public long getExtractionNs() {
        return extractionNs;
    }

    public JsonNode getHybridTimings() {
        return hybridTimings;
    }

    public Map<Long, ElementMetadata> getElementMetadata() {
        return elementMetadata;
    }

    /**
     * Releases the PDF resources of this extraction, as {@link DocumentProcessor#processFile}
     * does after it writes the output: closes the document and clears the static containers.
     *
     * <p>{@link org.opendataloader.pdf.api.AutoTagger#tag(ExtractionResult, Float)} gives the same
     * document to its {@link org.opendataloader.pdf.api.TaggingResult}. Close the
     * {@code TaggingResult} first. A call to this method after it is safe.
     *
     * <p>The static containers hold one document at a time. While they hold the document of a
     * later extraction, this method only closes the document of this extraction, on a new thread,
     * and waits for it. It does not clear the static containers, so the later extraction keeps
     * its state. If the calling thread is interrupted, this method still waits, and then sets the
     * interrupt status again.
     *
     * <p>The static containers and the font cache of veraPDF are thread-local. Call this method
     * on the thread that ran the extraction. On another thread, it does not release the temporary
     * files of the font cache. If that thread holds the document of another extraction, this
     * method only closes the document, as above.
     */
    @Override
    public void close() {
        DocumentProcessor.closePdfResources(document);
    }
}

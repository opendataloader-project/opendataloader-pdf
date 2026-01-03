/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for hybrid PDF processing backends.
 *
 * <p>Hybrid processing routes pages to external AI backends (like docling, hancom, azure)
 * for advanced document parsing capabilities such as table structure extraction and OCR.
 *
 * <p>Implementations of this interface provide HTTP client integration with specific backends.
 */
public interface HybridClient {

    /**
     * Request class containing PDF bytes and processing options.
     */
    final class HybridRequest {
        private final byte[] pdfBytes;
        private final Set<Integer> pageNumbers;
        private final boolean doTableStructure;
        private final boolean doOcr;

        /**
         * Creates a new HybridRequest.
         *
         * @param pdfBytes         The raw PDF file bytes to process.
         * @param pageNumbers      Set of 1-indexed page numbers to process. If empty, process all pages.
         * @param doTableStructure Whether to extract table structure.
         * @param doOcr            Whether to perform OCR on bitmap content.
         */
        public HybridRequest(byte[] pdfBytes, Set<Integer> pageNumbers,
                             boolean doTableStructure, boolean doOcr) {
            this.pdfBytes = pdfBytes;
            this.pageNumbers = pageNumbers != null ? pageNumbers : Collections.emptySet();
            this.doTableStructure = doTableStructure;
            this.doOcr = doOcr;
        }

        /**
         * Creates a request to process all pages with default options.
         *
         * @param pdfBytes The PDF file bytes.
         * @return A new HybridRequest with table structure and OCR enabled.
         */
        public static HybridRequest allPages(byte[] pdfBytes) {
            return new HybridRequest(pdfBytes, Collections.emptySet(), true, true);
        }

        /**
         * Creates a request to process specific pages.
         *
         * @param pdfBytes    The PDF file bytes.
         * @param pageNumbers The 1-indexed page numbers to process.
         * @return A new HybridRequest with table structure and OCR enabled.
         */
        public static HybridRequest forPages(byte[] pdfBytes, Set<Integer> pageNumbers) {
            return new HybridRequest(pdfBytes, pageNumbers, true, true);
        }

        public byte[] getPdfBytes() {
            return pdfBytes;
        }

        public Set<Integer> getPageNumbers() {
            return pageNumbers;
        }

        public boolean isDoTableStructure() {
            return doTableStructure;
        }

        public boolean isDoOcr() {
            return doOcr;
        }
    }

    /**
     * Response class containing parsed document content.
     */
    final class HybridResponse {
        private final String markdown;
        private final JsonNode json;
        private final Map<Integer, JsonNode> pageContents;

        /**
         * Creates a new HybridResponse.
         *
         * @param markdown     The markdown representation of the document.
         * @param json         The full structured JSON output (DoclingDocument format).
         * @param pageContents Per-page JSON content, keyed by 1-indexed page number.
         */
        public HybridResponse(String markdown, JsonNode json, Map<Integer, JsonNode> pageContents) {
            this.markdown = markdown != null ? markdown : "";
            this.json = json;
            this.pageContents = pageContents != null ? pageContents : Collections.emptyMap();
        }

        /**
         * Creates an empty response.
         *
         * @return A new HybridResponse with empty/null values.
         */
        public static HybridResponse empty() {
            return new HybridResponse("", null, Collections.emptyMap());
        }

        public String getMarkdown() {
            return markdown;
        }

        public JsonNode getJson() {
            return json;
        }

        public Map<Integer, JsonNode> getPageContents() {
            return pageContents;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HybridResponse that = (HybridResponse) o;
            return Objects.equals(markdown, that.markdown) &&
                Objects.equals(json, that.json) &&
                Objects.equals(pageContents, that.pageContents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(markdown, json, pageContents);
        }
    }

    /**
     * Converts a PDF document synchronously.
     *
     * @param request The conversion request containing PDF bytes and options.
     * @return The conversion response with parsed content.
     * @throws IOException If an I/O error occurs during the request.
     */
    HybridResponse convert(HybridRequest request) throws IOException;

    /**
     * Converts a PDF document asynchronously.
     *
     * <p>This method is useful for parallel processing where multiple pages
     * can be processed concurrently with the Java backend.
     *
     * @param request The conversion request containing PDF bytes and options.
     * @return A CompletableFuture that completes with the conversion response.
     */
    CompletableFuture<HybridResponse> convertAsync(HybridRequest request);
}

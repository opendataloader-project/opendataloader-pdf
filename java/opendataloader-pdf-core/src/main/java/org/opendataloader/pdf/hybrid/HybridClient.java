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
import java.util.EnumSet;
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
     * Output formats that can be requested from the hybrid backend.
     */
    enum OutputFormat {
        /** JSON structured document format (DoclingDocument). */
        JSON("json"),
        /** Markdown text format. */
        MARKDOWN("md"),
        /** HTML format. */
        HTML("html");

        private final String apiValue;

        OutputFormat(String apiValue) {
            this.apiValue = apiValue;
        }

        /** Returns the API parameter value for this format. */
        public String getApiValue() {
            return apiValue;
        }
    }

    /**
     * Request class containing PDF bytes and processing options.
     *
     * <p>Note: OCR and table structure detection are always enabled on the server side.
     * The DocumentConverter is initialized once at startup with fixed options for performance.
     */
    final class HybridRequest {
        private final byte[] pdfBytes;
        private final Set<Integer> pageNumbers;
        private final Set<OutputFormat> outputFormats;

        /**
         * Creates a new HybridRequest.
         *
         * @param pdfBytes      The raw PDF file bytes to process.
         * @param pageNumbers   Set of 1-indexed page numbers to process. If empty, process all pages.
         * @param outputFormats Set of output formats to request. If empty, defaults to all formats.
         */
        public HybridRequest(byte[] pdfBytes, Set<Integer> pageNumbers,
                             Set<OutputFormat> outputFormats) {
            this.pdfBytes = pdfBytes;
            this.pageNumbers = pageNumbers != null ? pageNumbers : Collections.emptySet();
            this.outputFormats = outputFormats != null && !outputFormats.isEmpty()
                ? EnumSet.copyOf(outputFormats)
                : EnumSet.allOf(OutputFormat.class);
        }

        /**
         * Creates a request to process all pages with default options.
         *
         * @param pdfBytes The PDF file bytes.
         * @return A new HybridRequest for all pages with all output formats.
         */
        public static HybridRequest allPages(byte[] pdfBytes) {
            return new HybridRequest(pdfBytes, Collections.emptySet(), null);
        }

        /**
         * Creates a request to process all pages with specified output formats.
         *
         * @param pdfBytes      The PDF file bytes.
         * @param outputFormats The output formats to request.
         * @return A new HybridRequest for all pages.
         */
        public static HybridRequest allPages(byte[] pdfBytes, Set<OutputFormat> outputFormats) {
            return new HybridRequest(pdfBytes, Collections.emptySet(), outputFormats);
        }

        /**
         * Creates a request to process specific pages.
         *
         * @param pdfBytes    The PDF file bytes.
         * @param pageNumbers The 1-indexed page numbers to process.
         * @return A new HybridRequest for the specified pages.
         */
        public static HybridRequest forPages(byte[] pdfBytes, Set<Integer> pageNumbers) {
            return new HybridRequest(pdfBytes, pageNumbers, null);
        }

        /**
         * Creates a request to process specific pages with specified output formats.
         *
         * @param pdfBytes      The PDF file bytes.
         * @param pageNumbers   The 1-indexed page numbers to process.
         * @param outputFormats The output formats to request.
         * @return A new HybridRequest for the specified pages.
         */
        public static HybridRequest forPages(byte[] pdfBytes, Set<Integer> pageNumbers,
                                             Set<OutputFormat> outputFormats) {
            return new HybridRequest(pdfBytes, pageNumbers, outputFormats);
        }

        public byte[] getPdfBytes() {
            return pdfBytes;
        }

        public Set<Integer> getPageNumbers() {
            return pageNumbers;
        }

        /**
         * Returns the output formats to request from the backend.
         *
         * @return Set of output formats. Never empty.
         */
        public Set<OutputFormat> getOutputFormats() {
            return outputFormats;
        }

        /**
         * Checks if JSON output is requested.
         *
         * @return true if JSON format is included.
         */
        public boolean wantsJson() {
            return outputFormats.contains(OutputFormat.JSON);
        }

        /**
         * Checks if Markdown output is requested.
         *
         * @return true if Markdown format is included.
         */
        public boolean wantsMarkdown() {
            return outputFormats.contains(OutputFormat.MARKDOWN);
        }

        /**
         * Checks if HTML output is requested.
         *
         * @return true if HTML format is included.
         */
        public boolean wantsHtml() {
            return outputFormats.contains(OutputFormat.HTML);
        }
    }

    /**
     * Response class containing parsed document content.
     */
    final class HybridResponse {
        private final String markdown;
        private final String html;
        private final JsonNode json;
        private final Map<Integer, JsonNode> pageContents;

        /**
         * Creates a new HybridResponse.
         *
         * @param markdown     The markdown representation of the document.
         * @param html         The HTML representation of the document.
         * @param json         The full structured JSON output (DoclingDocument format).
         * @param pageContents Per-page JSON content, keyed by 1-indexed page number.
         */
        public HybridResponse(String markdown, String html, JsonNode json, Map<Integer, JsonNode> pageContents) {
            this.markdown = markdown != null ? markdown : "";
            this.html = html != null ? html : "";
            this.json = json;
            this.pageContents = pageContents != null ? pageContents : Collections.emptyMap();
        }

        /**
         * Creates a new HybridResponse (backward compatible constructor).
         *
         * @param markdown     The markdown representation of the document.
         * @param json         The full structured JSON output (DoclingDocument format).
         * @param pageContents Per-page JSON content, keyed by 1-indexed page number.
         */
        public HybridResponse(String markdown, JsonNode json, Map<Integer, JsonNode> pageContents) {
            this(markdown, "", json, pageContents);
        }

        /**
         * Creates an empty response.
         *
         * @return A new HybridResponse with empty/null values.
         */
        public static HybridResponse empty() {
            return new HybridResponse("", "", null, Collections.emptyMap());
        }

        public String getMarkdown() {
            return markdown;
        }

        public String getHtml() {
            return html;
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
                Objects.equals(html, that.html) &&
                Objects.equals(json, that.json) &&
                Objects.equals(pageContents, that.pageContents);
        }

        @Override
        public int hashCode() {
            return Objects.hash(markdown, html, json, pageContents);
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

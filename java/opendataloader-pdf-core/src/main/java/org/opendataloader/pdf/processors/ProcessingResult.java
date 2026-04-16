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

/**
 * Result of {@link DocumentProcessor#processFile} containing optional metadata
 * collected during processing.
 *
 * <p>Currently carries hybrid-server pipeline timings when hybrid mode is used.
 * The timings JSON has the structure:
 * <pre>{
 *   "layout":          {"total_s": 1.2, "avg_s": 0.12, "count": 10},
 *   "ocr":             {"total_s": 2.1, "avg_s": 0.21, "count": 10},
 *   "table_structure":  {"total_s": 0.8, "avg_s": 0.40, "count": 2},
 *   "doc_enrich":       {"total_s": 5.3, "avg_s": 5.30, "count": 1},
 *   ...
 * }</pre>
 */
public class ProcessingResult {

    private static final ProcessingResult EMPTY = new ProcessingResult(null, 0, 0);

    private final JsonNode hybridTimings;
    private final long extractionNs;
    private final long outputNs;

    public ProcessingResult(JsonNode hybridTimings, long extractionNs, long outputNs) {
        this.hybridTimings = hybridTimings;
        this.extractionNs = extractionNs;
        this.outputNs = outputNs;
    }

    /** Returns an empty result. */
    public static ProcessingResult empty() {
        return EMPTY;
    }

    /**
     * Per-step pipeline timings from the hybrid server, or {@code null} if
     * hybrid mode was not used or the server did not return timings.
     */
    public JsonNode getHybridTimings() {
        return hybridTimings;
    }

    /** Time spent on data extraction (parsing + layout analysis + content extraction), in nanoseconds. */
    public long getExtractionNs() {
        return extractionNs;
    }

    /** Time spent on output generation (auto-tagging + JSON/MD/HTML export), in nanoseconds. */
    public long getOutputNs() {
        return outputNs;
    }

    /** Extraction time in milliseconds. */
    public long getExtractionMs() {
        return extractionNs / 1_000_000;
    }

    /** Output generation time in milliseconds. */
    public long getOutputMs() {
        return outputNs / 1_000_000;
    }
}

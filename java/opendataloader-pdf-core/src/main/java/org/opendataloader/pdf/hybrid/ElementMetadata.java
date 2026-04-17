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
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-element metadata produced by hybrid backends.
 * Keyed by IObject.recognizedStructureId in a sidecar Map.
 * All fields are optional — backends populate what they can.
 */
@JsonInclude(JsonInclude.Include.NON_DEFAULT)
public class ElementMetadata {

    /** DLA confidence (0.0~1.0). */
    private double confidence = 1.0;

    /** Original Hancom AI label (0~17), -1 if not applicable. */
    private int sourceLabel = -1;

    /** Heading inference method: "bbox-height" or "fixed". */
    private String headingInferenceMethod;

    /** Bounding box height in pixels, used for heading inference. */
    private Double bboxHeightPx;

    /** Word-cell matching method: "bbox-intersection" or "tsr-text-fallback". */
    private String wordMatchMethod;

    /** Number of words matched in the cell. */
    private int matchedWordCount;

    /** TSR metadata for tables. */
    private TsrMetadata tsr;

    /** Caption metadata for images. */
    private CaptionMetadata caption;

    /** Regionlist resolution metadata for label 7. */
    private RegionlistResolution regionlistResolution;

    /** Text source decision: "stream", "ocr", or "ocr-fallback". */
    private String textSource;

    /** Stream vs OCR text similarity score (auto mode only). */
    private Double streamOcrSimilarity;

    public double getConfidence() {
        return confidence;
    }

    public ElementMetadata setConfidence(double confidence) {
        this.confidence = confidence;
        return this;
    }

    public int getSourceLabel() {
        return sourceLabel;
    }

    public ElementMetadata setSourceLabel(int sourceLabel) {
        this.sourceLabel = sourceLabel;
        return this;
    }

    public String getHeadingInferenceMethod() {
        return headingInferenceMethod;
    }

    public ElementMetadata setHeadingInferenceMethod(String headingInferenceMethod) {
        this.headingInferenceMethod = headingInferenceMethod;
        return this;
    }

    public Double getBboxHeightPx() {
        return bboxHeightPx;
    }

    public ElementMetadata setBboxHeightPx(Double bboxHeightPx) {
        this.bboxHeightPx = bboxHeightPx;
        return this;
    }

    public String getWordMatchMethod() {
        return wordMatchMethod;
    }

    public ElementMetadata setWordMatchMethod(String wordMatchMethod) {
        this.wordMatchMethod = wordMatchMethod;
        return this;
    }

    public int getMatchedWordCount() {
        return matchedWordCount;
    }

    public ElementMetadata setMatchedWordCount(int matchedWordCount) {
        this.matchedWordCount = matchedWordCount;
        return this;
    }

    public TsrMetadata getTsr() {
        return tsr;
    }

    public ElementMetadata setTsr(TsrMetadata tsr) {
        this.tsr = tsr;
        return this;
    }

    public CaptionMetadata getCaption() {
        return caption;
    }

    public ElementMetadata setCaption(CaptionMetadata caption) {
        this.caption = caption;
        return this;
    }

    public RegionlistResolution getRegionlistResolution() {
        return regionlistResolution;
    }

    public ElementMetadata setRegionlistResolution(RegionlistResolution regionlistResolution) {
        this.regionlistResolution = regionlistResolution;
        return this;
    }

    public String getTextSource() {
        return textSource;
    }

    public ElementMetadata setTextSource(String textSource) {
        this.textSource = textSource;
        return this;
    }

    public Double getStreamOcrSimilarity() {
        return streamOcrSimilarity;
    }

    public ElementMetadata setStreamOcrSimilarity(Double streamOcrSimilarity) {
        this.streamOcrSimilarity = streamOcrSimilarity;
        return this;
    }

    /**
     * TSR (Table Structure Recognition) metadata for table elements.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class TsrMetadata {

        private int numCells;
        private String html;
        private long runTimeMs;

        public int getNumCells() {
            return numCells;
        }

        public TsrMetadata setNumCells(int numCells) {
            this.numCells = numCells;
            return this;
        }

        public String getHtml() {
            return html;
        }

        public TsrMetadata setHtml(String html) {
            this.html = html;
            return this;
        }

        public long getRunTimeMs() {
            return runTimeMs;
        }

        public TsrMetadata setRunTimeMs(long runTimeMs) {
            this.runTimeMs = runTimeMs;
            return this;
        }
    }

    /**
     * Caption metadata for image elements.
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class CaptionMetadata {

        private String text;
        private String language;
        private long runTimeMs;

        public String getText() {
            return text;
        }

        public CaptionMetadata setText(String text) {
            this.text = text;
            return this;
        }

        public String getLanguage() {
            return language;
        }

        public CaptionMetadata setLanguage(String language) {
            this.language = language;
            return this;
        }

        public long getRunTimeMs() {
            return runTimeMs;
        }

        public CaptionMetadata setRunTimeMs(long runTimeMs) {
            this.runTimeMs = runTimeMs;
            return this;
        }
    }

    /**
     * Resolution metadata for regionlist elements (label 7).
     */
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public static class RegionlistResolution {

        /** Strategy used: "table-first" or "list-only". */
        private String strategy;

        /** Whether TSR was attempted. */
        private boolean tsrAttempted;

        /** TSR result: "success", "no-cells", "failed", or null. */
        private String tsrResult;

        public String getStrategy() {
            return strategy;
        }

        public RegionlistResolution setStrategy(String strategy) {
            this.strategy = strategy;
            return this;
        }

        public boolean isTsrAttempted() {
            return tsrAttempted;
        }

        public RegionlistResolution setTsrAttempted(boolean tsrAttempted) {
            this.tsrAttempted = tsrAttempted;
            return this;
        }

        public String getTsrResult() {
            return tsrResult;
        }

        public RegionlistResolution setTsrResult(String tsrResult) {
            this.tsrResult = tsrResult;
            return this;
        }
    }
}

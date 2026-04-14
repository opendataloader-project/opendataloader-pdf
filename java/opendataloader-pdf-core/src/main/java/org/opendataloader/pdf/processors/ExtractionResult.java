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
import org.verapdf.wcag.algorithms.entities.IObject;

import java.util.List;

/**
 * Internal result of the extraction pipeline (preprocessing + content extraction + sanitization).
 * Carries the extracted contents and timing metadata for use by AutoTagger.
 */
public class ExtractionResult {

    private final List<List<IObject>> contents;
    private final long extractionNs;
    private final JsonNode hybridTimings;

    public ExtractionResult(List<List<IObject>> contents, long extractionNs, JsonNode hybridTimings) {
        this.contents = contents;
        this.extractionNs = extractionNs;
        this.hybridTimings = hybridTimings;
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
}

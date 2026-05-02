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
import org.opendataloader.pdf.enrichment.EnrichmentPipeline;
import org.opendataloader.pdf.graph.GraphBuilder;
import org.opendataloader.pdf.graph.GraphNode;
import org.verapdf.wcag.algorithms.entities.IObject;

import java.util.Collections;
import java.util.List;

/**
 * Internal result of the extraction pipeline (preprocessing + content extraction + sanitization + enrichment).
 * Carries the extracted contents, raw graph nodes, enriched graph nodes, and timing metadata.
 */
public class ExtractionResult {

    private final List<List<IObject>> contents;
    private final List<GraphNode> graphNodes;
    private final List<GraphNode> enrichedGraphNodes;
    private final long extractionNs;
    private final JsonNode hybridTimings;

    public ExtractionResult(List<List<IObject>> contents, long extractionNs, JsonNode hybridTimings) {
        this.contents = contents;
        this.graphNodes = GraphBuilder.build(contents);
        this.enrichedGraphNodes = EnrichmentPipeline.run(this.graphNodes);
        this.extractionNs = extractionNs;
        this.hybridTimings = hybridTimings;
    }

    private ExtractionResult(List<GraphNode> enrichedNodes) {
        this.contents = Collections.emptyList();
        this.graphNodes = Collections.emptyList();
        this.enrichedGraphNodes = enrichedNodes != null ? Collections.unmodifiableList(enrichedNodes) : Collections.emptyList();
        this.extractionNs = 0L;
        this.hybridTimings = null;
    }

    /** Testing factory — bypasses pipeline, injects pre-built enriched nodes directly. */
    public static ExtractionResult ofEnrichedNodes(List<GraphNode> nodes) {
        return new ExtractionResult(nodes);
    }

    public List<List<IObject>> getContents() {
        return contents;
    }

    public List<GraphNode> getGraphNodes() {
        return graphNodes;
    }

    /** Returns graph nodes after running the full enrichment pipeline (equation numbers, caption links, references, citations). */
    public List<GraphNode> getEnrichedGraphNodes() {
        return enrichedGraphNodes;
    }

    public long getExtractionNs() {
        return extractionNs;
    }

    public int getPageCount() {
        return contents != null ? contents.size() : 0;
    }

    public JsonNode getHybridTimings() {
        return hybridTimings;
    }
}

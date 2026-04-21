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
package org.opendataloader.pdf.enrichment;

import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.llm.LlmFallback;
import org.opendataloader.pdf.llm.NoOpLlmFallback;

import java.util.List;

public final class EnrichmentPipeline {

    private EnrichmentPipeline() {}

    public static List<GraphNode> run(List<GraphNode> nodes) {
        return run(nodes, new NoOpLlmFallback());
    }

    public static List<GraphNode> run(List<GraphNode> nodes, LlmFallback llmFallback) {
        List<GraphNode> result = new EquationNumberEnricher().enrich(nodes == null ? List.of() : nodes);
        result = new CaptionLinkEnricher().enrich(result);
        result = new ReferenceZoneEnricher().enrich(result);
        result = new CitationResolver().resolve(result);
        result = new LlmEnrichmentPass(llmFallback).enrich(result);
        return result;
    }
}

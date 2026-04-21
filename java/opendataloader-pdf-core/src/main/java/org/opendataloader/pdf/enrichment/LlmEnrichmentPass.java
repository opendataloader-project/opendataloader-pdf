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

import java.util.ArrayList;
import java.util.List;

public final class LlmEnrichmentPass {

    private static final double DEFAULT_CONFIDENCE_THRESHOLD = 0.5;

    private final LlmFallback fallback;
    private final double confidenceThreshold;

    public LlmEnrichmentPass(LlmFallback fallback) {
        this(fallback, DEFAULT_CONFIDENCE_THRESHOLD);
    }

    public LlmEnrichmentPass(LlmFallback fallback, double confidenceThreshold) {
        this.fallback = fallback;
        this.confidenceThreshold = confidenceThreshold;
    }

    public List<GraphNode> enrich(List<GraphNode> nodes) {
        List<GraphNode> result = new ArrayList<>(nodes.size());
        for (GraphNode node : nodes) {
            boolean lowConfidence = node.getConfidence() == null || node.getConfidence() < confidenceThreshold;
            boolean unresolved = node.getUnresolvedReason() != null;
            if (lowConfidence || unresolved) {
                GraphNode resolved = fallback.resolve(node, nodes).orElse(node);
                result.add(resolved);
            } else {
                result.add(node);
            }
        }
        return List.copyOf(result);
    }
}

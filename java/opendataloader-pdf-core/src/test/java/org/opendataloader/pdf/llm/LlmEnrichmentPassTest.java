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
package org.opendataloader.pdf.llm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.enrichment.LlmEnrichmentPass;
import org.opendataloader.pdf.graph.GraphNode;

import java.util.List;
import java.util.Optional;

public class LlmEnrichmentPassTest {

    @Test
    public void testNoOpFallbackLeavesNodesUnchanged() {
        GraphNode node = new GraphNode(1, null, 1L, 0.9, null);
        LlmEnrichmentPass pass = new LlmEnrichmentPass(new NoOpLlmFallback());
        List<GraphNode> result = pass.enrich(List.of(node));
        Assertions.assertEquals(1, result.size());
        Assertions.assertSame(node, result.get(0));
    }

    @Test
    public void testLowConfidenceNodeIsReplacedByFallback() {
        GraphNode lowConf = new GraphNode(1, null, 1L, 0.3, null);
        GraphNode replacement = new GraphNode(1, null, 1L, 0.9, null);
        LlmFallback stubFallback = (node, ctx) -> Optional.of(replacement);

        LlmEnrichmentPass pass = new LlmEnrichmentPass(stubFallback);
        List<GraphNode> result = pass.enrich(List.of(lowConf));

        Assertions.assertEquals(1, result.size());
        Assertions.assertSame(replacement, result.get(0));
    }

    @Test
    public void testUnresolvedNodeTriggersFallbackEvenWithNullConfidence() {
        GraphNode unresolved = new GraphNode(1, null, 2L, null, "no match found");
        GraphNode replacement = new GraphNode(1, null, 2L, 0.9, null);
        LlmFallback stubFallback = (node, ctx) -> Optional.of(replacement);

        LlmEnrichmentPass pass = new LlmEnrichmentPass(stubFallback);
        List<GraphNode> result = pass.enrich(List.of(unresolved));

        Assertions.assertEquals(1, result.size());
        Assertions.assertSame(replacement, result.get(0));
    }

    @Test
    public void testFallbackReturnsEmptyKeepsOriginalNode() {
        GraphNode lowConf = new GraphNode(1, null, 1L, 0.3, null);
        LlmFallback emptyFallback = (node, ctx) -> Optional.empty();

        LlmEnrichmentPass pass = new LlmEnrichmentPass(emptyFallback);
        List<GraphNode> result = pass.enrich(List.of(lowConf));

        Assertions.assertEquals(1, result.size());
        Assertions.assertSame(lowConf, result.get(0));
    }

    @Test
    public void testResultIsImmutable() {
        LlmEnrichmentPass pass = new LlmEnrichmentPass(new NoOpLlmFallback());
        List<GraphNode> result = pass.enrich(List.of());
        Assertions.assertThrows(UnsupportedOperationException.class,
            () -> result.add(new GraphNode(0, null, null, null, null)));
    }
}

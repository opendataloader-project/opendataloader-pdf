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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.graph.CaptionNode;
import org.opendataloader.pdf.graph.GraphFixtures;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.TextNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.List;

public class CaptionLinkEnricherTest {

    private final CaptionLinkEnricher enricher = new CaptionLinkEnricher();

    // Caption directly below a figure-like TextNode on the same page.
    // The figure candidate rawId=42 should win; confidence >= 0.8.
    @Test
    public void testNearestCandidateWins() {
        // Figure candidate: x=100..400, y=300..500, page=0, rawId=42
        BoundingBox figureBbox = new BoundingBox(0, 100.0, 300.0, 400.0, 500.0);
        TextNode figure = new TextNode("figure content", 0, figureBbox, 42L, null);

        // Caption directly below: x=120..380, y=510..530, page=0, rawId=99
        BoundingBox captionBbox = new BoundingBox(0, 120.0, 510.0, 380.0, 530.0);
        CaptionNode caption = GraphFixtures.captionNode(0, captionBbox, "Figure 1: Some caption", 99L);

        List<GraphNode> nodes = GraphFixtures.graphWith(figure, caption);
        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(2, result.size());
        CaptionNode enriched = (CaptionNode) result.get(1);
        Assertions.assertEquals(Long.valueOf(42L), enriched.getTargetId());
        Assertions.assertNotNull(enriched.getConfidence());
        Assertions.assertTrue(enriched.getConfidence() >= 0.8,
            "confidence should be >= 0.8 but was " + enriched.getConfidence());
        Assertions.assertNull(enriched.getUnresolvedReason());
    }

    // Two candidates equidistant. Caption text starts with "Figure".
    // Candidate A (rawId=10) is a TextNode with kind-neutral label.
    // Candidate B (rawId=20) is a TextNode tagged as figure-like via kind="figure".
    // Lexical tie-breaker: figure-kind node should win.
    @Test
    public void testLexicalTieBreakerPrefigureWins() {
        // Both candidates at equal vertical distance from caption (above/below)
        // Caption: x=100..400, y=200..220, page=1, rawId=99
        BoundingBox captionBbox = new BoundingBox(0, 100.0, 200.0, 400.0, 220.0);

        // Table candidate (rawId=10) — same distance, kind="table"
        BoundingBox tableBbox = new BoundingBox(0, 100.0, 100.0, 400.0, 190.0);
        CaptionNode tableCaption = GraphFixtures.captionNode(1, captionBbox, "Figure 2: A chart", 99L);
        TextNode tableCandidate = new TextNode("table content", 1, tableBbox, 10L, null);

        // Figure candidate (rawId=20) — same bounding box position (equidistant)
        BoundingBox figureBbox = new BoundingBox(0, 100.0, 100.0, 400.0, 190.0);
        CaptionNode figureCaption = GraphFixtures.captionNode(1, captionBbox, "Figure 2: A chart", 99L);
        TextNode figureCandidate = new TextNode("figure content", 1, figureBbox, 20L, null);

        List<GraphNode> nodes = GraphFixtures.graphWith(tableCandidate, figureCandidate, figureCaption);
        List<GraphNode> result = enricher.enrich(nodes);

        CaptionNode enriched = (CaptionNode) result.get(2);
        // "Figure" prefix + candidate with "figure" in text → rawId=20 wins
        Assertions.assertEquals(Long.valueOf(20L), enriched.getTargetId());
        Assertions.assertNull(enriched.getUnresolvedReason());
    }

    // No candidates on the page → caption stays unresolved.
    // targetId kept as original (null here), unresolvedReason non-null, confidence < threshold.
    @Test
    public void testNoCandidateStaysUnresolved() {
        BoundingBox captionBbox = new BoundingBox(0, 100.0, 100.0, 400.0, 120.0);
        CaptionNode caption = GraphFixtures.captionNode(0, captionBbox, "Figure 3: Alone", 77L);

        // Only the caption — no figure/table candidates
        List<GraphNode> nodes = GraphFixtures.graphWith(caption);
        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(1, result.size());
        CaptionNode enriched = (CaptionNode) result.get(0);
        Assertions.assertNull(enriched.getTargetId());
        Assertions.assertNotNull(enriched.getUnresolvedReason());
        if (enriched.getConfidence() != null) {
            Assertions.assertTrue(enriched.getConfidence() < CaptionLinkEnricher.SCORE_THRESHOLD,
                "unresolved confidence should be < threshold");
        }
    }

    // Result list must be immutable
    @Test
    public void testResultIsImmutable() {
        BoundingBox captionBbox = new BoundingBox(0, 100.0, 100.0, 400.0, 120.0);
        CaptionNode caption = GraphFixtures.captionNode(0, captionBbox, "Figure 1", 1L);
        List<GraphNode> result = enricher.enrich(GraphFixtures.graphWith(caption));
        Assertions.assertThrows(UnsupportedOperationException.class,
            () -> result.add(new GraphNode(0, null, null, null, null)));
    }

    // Original list must not be mutated
    @Test
    public void testOriginalListNotMutated() {
        BoundingBox figureBbox = new BoundingBox(0, 100.0, 300.0, 400.0, 500.0);
        TextNode figure = new TextNode("figure content", 0, figureBbox, 42L, null);
        BoundingBox captionBbox = new BoundingBox(0, 120.0, 510.0, 380.0, 530.0);
        CaptionNode caption = GraphFixtures.captionNode(0, captionBbox, "Figure 1: caption", 99L);

        List<GraphNode> nodes = GraphFixtures.graphWith(figure, caption);
        GraphNode originalCaption = nodes.get(1);
        enricher.enrich(nodes);
        Assertions.assertSame(originalCaption, nodes.get(1));
        Assertions.assertNull(((CaptionNode) originalCaption).getTargetId());
    }
}

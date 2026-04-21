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
import org.opendataloader.pdf.graph.CitationNode;
import org.opendataloader.pdf.graph.CaptionNode;
import org.opendataloader.pdf.graph.EquationNode;
import org.opendataloader.pdf.graph.GraphFixtures;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.ReferenceEntryNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.Collections;
import java.util.List;

public class EnrichmentPipelineTest {

    @Test
    public void testEmptyInputReturnsEmptyList() {
        List<GraphNode> result = EnrichmentPipeline.run(Collections.emptyList());
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    public void testEquationEnriched() {
        // Equation bbox: x=100..200, y=50..70, page 0
        // Number token "(1)": x=220..250, y=55..65 — right-adjacent, xGap=20, yDist~0
        BoundingBox eqBbox = new BoundingBox(0, 100.0, 50.0, 200.0, 70.0);
        BoundingBox numBbox = new BoundingBox(0, 220.0, 55.0, 250.0, 65.0);

        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.equationNode(0, eqBbox, "E=mc^2"),
            GraphFixtures.inlineText(0, numBbox, "(1)")
        );

        List<GraphNode> result = EnrichmentPipeline.run(nodes);

        Assertions.assertEquals(2, result.size());
        EquationNode eq = (EquationNode) result.get(0);
        Assertions.assertEquals("1", eq.getNumber());
    }

    @Test
    public void testCaptionEnriched() {
        // Caption bbox: x=100..300, y=50..70, page 0
        // Figure text bbox: x=100..300, y=85..100 — yDist~22.5, xDist~0, well within threshold
        BoundingBox captionBbox = new BoundingBox(0, 100.0, 50.0, 300.0, 70.0);
        BoundingBox figureBbox = new BoundingBox(0, 100.0, 85.0, 300.0, 100.0);

        CaptionNode caption = GraphFixtures.captionNode(0, captionBbox, "Figure 1: some figure content", 42L);
        GraphNode figureText = GraphFixtures.textNode(0, figureBbox, "some figure content");

        List<GraphNode> nodes = GraphFixtures.graphWith(caption, figureText);

        List<GraphNode> result = EnrichmentPipeline.run(nodes);

        Assertions.assertEquals(2, result.size());
        CaptionNode enrichedCaption = (CaptionNode) result.get(0);
        Assertions.assertNotNull(enrichedCaption.getConfidence());
    }

    @Test
    public void testReferenceZoneEnriched() {
        BoundingBox bbox = new BoundingBox(0, 50.0, 100.0, 400.0, 120.0);

        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, bbox, "References"),
            GraphFixtures.textNode(0, bbox, "[1] Smith 2020. A paper.")
        );

        List<GraphNode> result = EnrichmentPipeline.run(nodes);

        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.get(1) instanceof ReferenceEntryNode);
        ReferenceEntryNode ref = (ReferenceEntryNode) result.get(1);
        Assertions.assertEquals("1", ref.getRefId());
    }

    @Test
    public void testCitationResolved() {
        BoundingBox bbox = new BoundingBox(0, 50.0, 100.0, 400.0, 120.0);

        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, bbox, "References"),
            GraphFixtures.textNode(0, bbox, "[1] Smith 2020. A paper."),
            GraphFixtures.textNode(0, bbox, "as shown in [1] the result holds.")
        );

        List<GraphNode> result = EnrichmentPipeline.run(nodes);

        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.get(1) instanceof ReferenceEntryNode);
        Assertions.assertTrue(result.get(2) instanceof CitationNode);
        CitationNode citation = (CitationNode) result.get(2);
        Assertions.assertTrue(citation.getMarkers().contains("1"));
        Assertions.assertFalse(citation.getResolvedRefIds().isEmpty());
    }

    @Test
    public void testResultIsImmutable() {
        List<GraphNode> result = EnrichmentPipeline.run(Collections.emptyList());
        Assertions.assertThrows(UnsupportedOperationException.class,
            () -> result.add(new GraphNode(0, null, null, null, null)));
    }
}

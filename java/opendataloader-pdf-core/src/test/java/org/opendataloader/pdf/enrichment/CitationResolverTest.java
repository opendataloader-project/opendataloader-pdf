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
import org.opendataloader.pdf.graph.GraphFixtures;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.TextNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.List;

public class CitationResolverTest {

    private static final BoundingBox BBOX = new BoundingBox(0, 50.0, 100.0, 400.0, 120.0);

    private final CitationResolver resolver = new CitationResolver();

    @Test
    public void testSingleMarkerResolved() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.referenceEntryNode(0, BBOX, "3", "[3] Some reference."),
            GraphFixtures.textNode(0, BBOX, "As shown in [3] the result holds.")
        );

        List<GraphNode> result = resolver.resolve(nodes);

        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.get(1) instanceof CitationNode);
        CitationNode citation = (CitationNode) result.get(1);
        Assertions.assertTrue(citation.getMarkers().contains("3"));
        Assertions.assertTrue(citation.getResolvedRefIds().contains("3"));
        Assertions.assertNull(citation.getUnresolvedReason());
    }

    @Test
    public void testRangeMarkerExpanded() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.referenceEntryNode(0, BBOX, "3", "[3] Ref three."),
            GraphFixtures.referenceEntryNode(0, BBOX, "4", "[4] Ref four."),
            GraphFixtures.referenceEntryNode(0, BBOX, "5", "[5] Ref five."),
            GraphFixtures.textNode(0, BBOX, "Prior work [3-5] establishes this.")
        );

        List<GraphNode> result = resolver.resolve(nodes);

        Assertions.assertEquals(4, result.size());
        Assertions.assertTrue(result.get(3) instanceof CitationNode);
        CitationNode citation = (CitationNode) result.get(3);
        Assertions.assertEquals(3, citation.getMarkers().size());
        Assertions.assertTrue(citation.getMarkers().contains("3"));
        Assertions.assertTrue(citation.getMarkers().contains("4"));
        Assertions.assertTrue(citation.getMarkers().contains("5"));
        Assertions.assertEquals(3, citation.getResolvedRefIds().size());
        Assertions.assertNull(citation.getUnresolvedReason());
    }

    @Test
    public void testUnresolvedMarker() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "See [99] for details.")
        );

        List<GraphNode> result = resolver.resolve(nodes);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0) instanceof CitationNode);
        CitationNode citation = (CitationNode) result.get(0);
        Assertions.assertTrue(citation.getMarkers().contains("99"));
        Assertions.assertTrue(citation.getResolvedRefIds().isEmpty());
        Assertions.assertNotNull(citation.getUnresolvedReason());
    }

    @Test
    public void testTextWithoutCitationPassesThrough() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "No citation here.")
        );

        List<GraphNode> result = resolver.resolve(nodes);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0) instanceof TextNode);
    }

    @Test
    public void testResultIsImmutable() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "Text [1] here.")
        );
        List<GraphNode> result = resolver.resolve(nodes);
        Assertions.assertThrows(UnsupportedOperationException.class,
            () -> result.add(new GraphNode(0, null, null, null, null)));
    }

    @Test
    public void testCommaListMarkersResolved() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.referenceEntryNode(0, BBOX, "3", "[3] Ref three."),
            GraphFixtures.referenceEntryNode(0, BBOX, "4", "[4] Ref four."),
            GraphFixtures.referenceEntryNode(0, BBOX, "5", "[5] Ref five."),
            GraphFixtures.textNode(0, BBOX, "Results [3,4,5] confirm this.")
        );

        List<GraphNode> result = resolver.resolve(nodes);

        Assertions.assertTrue(result.get(3) instanceof CitationNode);
        CitationNode citation = (CitationNode) result.get(3);
        Assertions.assertEquals(3, citation.getMarkers().size());
        Assertions.assertEquals(3, citation.getResolvedRefIds().size());
    }
}

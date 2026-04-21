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
import org.opendataloader.pdf.graph.GraphFixtures;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.ReferenceEntryNode;
import org.opendataloader.pdf.graph.TextNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.List;

public class ReferenceZoneEnricherTest {

    private static final BoundingBox BBOX = new BoundingBox(0, 50.0, 100.0, 400.0, 120.0);

    private final ReferenceZoneEnricher enricher = new ReferenceZoneEnricher();

    @Test
    public void testNumericStyleEntriesParsed() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "References"),
            GraphFixtures.textNode(0, BBOX, "[1] Smith et al. Some paper. 2020."),
            GraphFixtures.textNode(0, BBOX, "[2] Jones et al. Another paper. 2021.")
        );

        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.get(0) instanceof TextNode, "heading stays as TextNode");
        Assertions.assertTrue(result.get(1) instanceof ReferenceEntryNode);
        Assertions.assertTrue(result.get(2) instanceof ReferenceEntryNode);

        ReferenceEntryNode ref1 = (ReferenceEntryNode) result.get(1);
        ReferenceEntryNode ref2 = (ReferenceEntryNode) result.get(2);

        Assertions.assertEquals("1", ref1.getRefId());
        Assertions.assertEquals("2", ref2.getRefId());
    }

    @Test
    public void testHeadingDetectedNodesBeforePassThrough() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "Introduction"),
            GraphFixtures.textNode(0, BBOX, "Some body text."),
            GraphFixtures.textNode(0, BBOX, "References"),
            GraphFixtures.textNode(0, BBOX, "[1] Brown, D. Paper title. 2019.")
        );

        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(4, result.size());
        Assertions.assertTrue(result.get(0) instanceof TextNode, "pre-zone node passes through");
        Assertions.assertTrue(result.get(1) instanceof TextNode, "pre-zone node passes through");
        Assertions.assertTrue(result.get(2) instanceof TextNode, "heading stays as TextNode");
        Assertions.assertTrue(result.get(3) instanceof ReferenceEntryNode);
        ReferenceEntryNode ref = (ReferenceEntryNode) result.get(3);
        Assertions.assertEquals("1", ref.getRefId());
    }

    @Test
    public void testAuthorYearStyleIncrementalRefIds() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "references"),
            GraphFixtures.textNode(0, BBOX, "Adams, A. Title one. Journal, 2010."),
            GraphFixtures.textNode(0, BBOX, "Baker, B. Title two. Journal, 2011.")
        );

        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(3, result.size());
        Assertions.assertTrue(result.get(1) instanceof ReferenceEntryNode);
        Assertions.assertTrue(result.get(2) instanceof ReferenceEntryNode);

        ReferenceEntryNode ref1 = (ReferenceEntryNode) result.get(1);
        ReferenceEntryNode ref2 = (ReferenceEntryNode) result.get(2);

        Assertions.assertEquals("r1", ref1.getRefId());
        Assertions.assertEquals("r2", ref2.getRefId());
    }

    @Test
    public void testResultIsImmutable() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "References"),
            GraphFixtures.textNode(0, BBOX, "[1] Test.")
        );
        List<GraphNode> result = enricher.enrich(nodes);
        Assertions.assertThrows(UnsupportedOperationException.class,
            () -> result.add(new GraphNode(0, null, null, null, null)));
    }

    @Test
    public void testNoReferencesHeadingAllPassThrough() {
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.textNode(0, BBOX, "Introduction"),
            GraphFixtures.textNode(0, BBOX, "Body text here.")
        );

        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.get(0) instanceof TextNode);
        Assertions.assertTrue(result.get(1) instanceof TextNode);
    }
}

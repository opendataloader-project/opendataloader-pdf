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
import org.opendataloader.pdf.graph.EquationNode;
import org.opendataloader.pdf.graph.GraphFixtures;
import org.opendataloader.pdf.graph.GraphNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.List;

public class EquationNumberEnricherTest {

    private final EquationNumberEnricher enricher = new EquationNumberEnricher();

    // Equation: x=100..200, y=50..70 (page 0)
    // Number token "(1)": x=210..240, y=55..65  → right-adjacent, same y-band
    @Test
    public void testRightAdjacentTokenBindsNumber() {
        BoundingBox equationBbox = new BoundingBox(0, 100.0, 50.0, 200.0, 70.0);
        BoundingBox tokenBbox = new BoundingBox(0, 210.0, 55.0, 240.0, 65.0);

        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.equationNode(0, equationBbox, "E=mc^2"),
            GraphFixtures.inlineText(0, tokenBbox, "(1)")
        );

        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(2, result.size());
        Assertions.assertTrue(result.get(0) instanceof EquationNode);
        EquationNode enriched = (EquationNode) result.get(0);
        Assertions.assertEquals("1", enriched.getNumber());
        Assertions.assertNotNull(enriched.getConfidence());
        Assertions.assertTrue(enriched.getConfidence() >= 0.8,
            "confidence should be >= 0.8 but was " + enriched.getConfidence());
        Assertions.assertNull(enriched.getUnresolvedReason());
    }

    // Equation alone on page — no text nodes nearby
    @Test
    public void testNoNearbyTokenStaysUnresolved() {
        BoundingBox equationBbox = new BoundingBox(0, 100.0, 50.0, 200.0, 70.0);

        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.equationNode(0, equationBbox, "\\int_0^1 f(x)dx")
        );

        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0) instanceof EquationNode);
        EquationNode enriched = (EquationNode) result.get(0);
        Assertions.assertNull(enriched.getNumber());
        Assertions.assertNotNull(enriched.getUnresolvedReason());
        // confidence should be low (null or < 0.5)
        if (enriched.getConfidence() != null) {
            Assertions.assertTrue(enriched.getConfidence() < 0.5,
                "unresolved confidence should be < 0.5");
        }
    }

    // Two candidates: one right-adjacent (close), one far left on different y
    // Enricher must pick the right-adjacent one
    @Test
    public void testNearestCandidateChosenByDistance() {
        // Equation: x=100..200, y=50..70
        BoundingBox equationBbox = new BoundingBox(0, 100.0, 50.0, 200.0, 70.0);
        // Close right-adjacent token "(2)": x=205..235, y=55..65
        BoundingBox closeBbox = new BoundingBox(0, 205.0, 55.0, 235.0, 65.0);
        // Far token "(3)": x=400..430, y=200..220 — different y-band, far x
        BoundingBox farBbox = new BoundingBox(0, 400.0, 200.0, 430.0, 220.0);

        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.equationNode(0, equationBbox, "a^2+b^2=c^2"),
            GraphFixtures.inlineText(0, closeBbox, "(2)"),
            GraphFixtures.inlineText(0, farBbox, "(3)")
        );

        List<GraphNode> result = enricher.enrich(nodes);

        Assertions.assertTrue(result.get(0) instanceof EquationNode);
        EquationNode enriched = (EquationNode) result.get(0);
        Assertions.assertEquals("2", enriched.getNumber());
    }

    // Result list must be immutable
    @Test
    public void testResultIsImmutable() {
        BoundingBox equationBbox = new BoundingBox(0, 100.0, 50.0, 200.0, 70.0);
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.equationNode(0, equationBbox, "x+y=z")
        );
        List<GraphNode> result = enricher.enrich(nodes);
        Assertions.assertThrows(UnsupportedOperationException.class,
            () -> result.add(new GraphNode(0, null, null, null, null)));
    }

    // Original input list must not be mutated
    @Test
    public void testOriginalListNotMutated() {
        BoundingBox equationBbox = new BoundingBox(0, 100.0, 50.0, 200.0, 70.0);
        BoundingBox tokenBbox = new BoundingBox(0, 210.0, 55.0, 240.0, 65.0);
        List<GraphNode> nodes = GraphFixtures.graphWith(
            GraphFixtures.equationNode(0, equationBbox, "E=mc^2"),
            GraphFixtures.inlineText(0, tokenBbox, "(1)")
        );
        GraphNode originalEquation = nodes.get(0);
        enricher.enrich(nodes);
        Assertions.assertSame(originalEquation, nodes.get(0));
        Assertions.assertNull(((EquationNode) originalEquation).getNumber());
    }

    @Test
    void testAlphabeticSuffixTokenBindsNumber() {
        EquationNode eq = GraphFixtures.equationNode(0, new BoundingBox(0, 100, 300, 300, 350), "E=mc^2");
        GraphNode tokenNode = GraphFixtures.inlineText(0, new BoundingBox(0, 320, 300, 360, 320), "(12a)");

        List<GraphNode> result = new EquationNumberEnricher().enrich(GraphFixtures.graphWith(eq, tokenNode));

        EquationNode enriched = (EquationNode) result.get(0);
        Assertions.assertEquals("12a", enriched.getNumber());
        Assertions.assertNull(enriched.getUnresolvedReason());
    }

    @Test
    void testBareNumberTokenBindsNumber() {
        EquationNode eq = GraphFixtures.equationNode(0, new BoundingBox(0, 100, 300, 300, 350), "a+b=c");
        GraphNode tokenNode = GraphFixtures.inlineText(0, new BoundingBox(0, 320, 300, 360, 320), "42");

        List<GraphNode> result = new EquationNumberEnricher().enrich(GraphFixtures.graphWith(eq, tokenNode));

        EquationNode enriched = (EquationNode) result.get(0);
        Assertions.assertEquals("42", enriched.getNumber());
        Assertions.assertNull(enriched.getUnresolvedReason());
    }
}

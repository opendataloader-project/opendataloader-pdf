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
package org.opendataloader.pdf.quality;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.opendataloader.pdf.graph.CaptionNode;
import org.opendataloader.pdf.graph.CitationNode;
import org.opendataloader.pdf.graph.EquationNode;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.ReferenceEntryNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ParityCheckerTest {

    private static final BoundingBox BBOX = new BoundingBox(0, 0, 10, 10);
    private final ParityChecker checker = new ParityChecker();

    @Test
    public void emptyGraph_allCountsZeroAllRatesOne() {
        ParityReport report = checker.check(Collections.emptyList());
        assertEquals(0, report.getTotalEquations());
        assertEquals(0, report.getUnresolvedEquations());
        assertEquals(0, report.getTotalCaptions());
        assertEquals(0, report.getUnresolvedCaptions());
        assertEquals(0, report.getTotalCitations());
        assertEquals(0, report.getUnresolvedCitations());
        assertEquals(0, report.getTotalReferences());
        assertEquals(1.0, report.equationResolvedRate(), 0.0);
        assertEquals(1.0, report.captionResolvedRate(), 0.0);
        assertEquals(1.0, report.citationResolvedRate(), 0.0);
    }

    @Test
    public void allEquationsResolved_unresolvedZeroRateOne() {
        EquationNode eq1 = new EquationNode("x^2", true, "(1)", "1", 0, BBOX, 1L, 1.0);
        EquationNode eq2 = new EquationNode("y^2", true, "(2)", "2", 0, BBOX, 2L, 1.0);
        List<GraphNode> nodes = Arrays.asList(eq1, eq2);

        ParityReport report = checker.check(nodes);
        assertEquals(2, report.getTotalEquations());
        assertEquals(0, report.getUnresolvedEquations());
        assertEquals(1.0, report.equationResolvedRate(), 0.0);
    }

    @Test
    public void someEquationsUnresolved_unresolvedCountPositiveRateLessThanOne() {
        EquationNode resolved = new EquationNode("x^2", true, "(1)", "1", 0, BBOX, 1L, 1.0);
        EquationNode unresolved = new EquationNode("y^2", true, null, "2", 0, BBOX, 2L, 1.0, "no number found");
        List<GraphNode> nodes = Arrays.asList(resolved, unresolved);

        ParityReport report = checker.check(nodes);
        assertEquals(2, report.getTotalEquations());
        assertEquals(1, report.getUnresolvedEquations());
        assertEquals(0.5, report.equationResolvedRate(), 0.001);
    }

    @Test
    public void unresolvedCaption_unresolvedCaptionsPositive() {
        CaptionNode linked = new CaptionNode("figure", "Fig. 1", 10L, "Caption text", null, 0, BBOX, 1L, 1.0);
        CaptionNode unlinked = new CaptionNode("figure", "Fig. 2", null, "Another caption", null, 0, BBOX, 2L, 1.0, "no target");
        List<GraphNode> nodes = Arrays.asList(linked, unlinked);

        ParityReport report = checker.check(nodes);
        assertEquals(2, report.getTotalCaptions());
        assertEquals(1, report.getUnresolvedCaptions());
        assertEquals(0.5, report.captionResolvedRate(), 0.001);
    }

    @Test
    public void unresolvedCitation_unresolvedCitationsPositive() {
        CitationNode resolved = new CitationNode(
            Arrays.asList("[1]"), Arrays.asList("ref-1"), "span1", 0, BBOX, 1L, 1.0);
        CitationNode unresolved = new CitationNode(
            Arrays.asList("[2]"), Collections.emptyList(), "span2", 0, BBOX, 2L, 1.0, "no matching ref");
        List<GraphNode> nodes = Arrays.asList(resolved, unresolved);

        ParityReport report = checker.check(nodes);
        assertEquals(2, report.getTotalCitations());
        assertEquals(1, report.getUnresolvedCitations());
        assertEquals(0.5, report.citationResolvedRate(), 0.001);
    }

    @Test
    public void referenceEntriesCountedAsHealthSignal() {
        ReferenceEntryNode ref1 = new ReferenceEntryNode("ref-1", "Author et al.", null, 0, BBOX, 1L, 1.0);
        ReferenceEntryNode ref2 = new ReferenceEntryNode("ref-2", "Smith 2020", null, 0, BBOX, 2L, 1.0);
        List<GraphNode> nodes = Arrays.asList(ref1, ref2);

        ParityReport report = checker.check(nodes);
        assertEquals(2, report.getTotalReferences());
    }

    @Test
    public void nullInputReturnsZeroReport() {
        ParityChecker checker = new ParityChecker();
        ParityReport report = checker.check((List<GraphNode>) null);
        Assertions.assertEquals(0, report.getTotalEquations());
        Assertions.assertEquals(1.0, report.equationResolvedRate(), 0.001);
    }
}

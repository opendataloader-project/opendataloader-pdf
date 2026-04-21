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

import org.opendataloader.pdf.graph.CaptionNode;
import org.opendataloader.pdf.graph.CitationNode;
import org.opendataloader.pdf.graph.EquationNode;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.ReferenceEntryNode;
import org.opendataloader.pdf.processors.ExtractionResult;

import java.util.List;

public class ParityChecker {

    public ParityReport check(ExtractionResult result) {
        return check(result.getEnrichedGraphNodes());
    }

    public ParityReport check(List<GraphNode> enrichedNodes) {
        int totalEquations = 0, unresolvedEquations = 0;
        int totalCaptions = 0, unresolvedCaptions = 0;
        int totalCitations = 0, unresolvedCitations = 0;
        int totalReferences = 0;

        for (GraphNode node : enrichedNodes) {
            if (node instanceof EquationNode) {
                totalEquations++;
                if (((EquationNode) node).getNumber() == null) {
                    unresolvedEquations++;
                }
            } else if (node instanceof CaptionNode) {
                totalCaptions++;
                if (((CaptionNode) node).getTargetId() == null) {
                    unresolvedCaptions++;
                }
            } else if (node instanceof CitationNode) {
                totalCitations++;
                if (((CitationNode) node).getResolvedRefIds().isEmpty()) {
                    unresolvedCitations++;
                }
            } else if (node instanceof ReferenceEntryNode) {
                totalReferences++;
            }
        }

        return new ParityReport(
            totalEquations, unresolvedEquations,
            totalCaptions, unresolvedCaptions,
            totalCitations, unresolvedCitations,
            totalReferences
        );
    }
}

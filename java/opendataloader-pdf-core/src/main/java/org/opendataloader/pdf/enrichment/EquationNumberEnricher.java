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

import org.opendataloader.pdf.graph.EquationNode;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.TextNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assigns equation numbers to EquationNode instances by scanning nearby TextNodes
 * for number tokens matching patterns like (1), (12a), 1, 12a.
 */
public class EquationNumberEnricher {

    private static final Pattern NUMBER_TOKEN = Pattern.compile("^\\(?\\s*(\\d+[a-z]?)\\s*\\)?$");
    private static final double SCORE_THRESHOLD = 0.5;
    // Maximum distances used to normalise proximity scores
    private static final double MAX_X_GAP = 200.0;
    private static final double MAX_Y_DISTANCE = 100.0;

    public List<GraphNode> enrich(List<GraphNode> nodes) {
        List<TextNode> candidates = collectTextCandidates(nodes);
        List<GraphNode> result = new ArrayList<>(nodes.size());
        for (GraphNode node : nodes) {
            if (node instanceof EquationNode) {
                result.add(enrichEquation((EquationNode) node, candidates));
            } else {
                result.add(node);
            }
        }
        return List.copyOf(result);
    }

    private List<TextNode> collectTextCandidates(List<GraphNode> nodes) {
        List<TextNode> candidates = new ArrayList<>();
        for (GraphNode node : nodes) {
            if (node instanceof TextNode) {
                TextNode tn = (TextNode) node;
                if (tn.getText() != null && NUMBER_TOKEN.matcher(tn.getText().trim()).matches()) {
                    candidates.add(tn);
                }
            }
        }
        return candidates;
    }

    private EquationNode enrichEquation(EquationNode equation, List<TextNode> candidates) {
        BoundingBox eqBox = equation.getBbox();
        if (eqBox == null || candidates.isEmpty()) {
            return unresolved(equation, "no number token found nearby");
        }

        TextNode best = null;
        double bestScore = -1.0;

        for (TextNode candidate : candidates) {
            BoundingBox cBox = candidate.getBbox();
            if (cBox == null) {
                continue;
            }
            if (!Objects.equals(candidate.getPage(), equation.getPage())) {
                continue;
            }
            double score = score(eqBox, cBox);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        if (best == null || bestScore < SCORE_THRESHOLD) {
            return unresolved(equation, "no number token found nearby");
        }

        String rawText = best.getText().trim();
        Matcher m = NUMBER_TOKEN.matcher(rawText);
        String number = m.matches() ? m.group(1) : rawText;

        return new EquationNode(
            equation.getLatex(),
            equation.isDisplayMode(),
            number,
            equation.getSpan(),
            equation.getPage(),
            equation.getBbox(),
            equation.getRawId(),
            bestScore
        );
    }

    /**
     * Score a candidate token relative to an equation bounding box.
     * Prefers tokens that are right-adjacent (small positive x-gap) and
     * vertically aligned (y-overlap or small y-distance).
     */
    private double score(BoundingBox eq, BoundingBox candidate) {
        double eqRight = eq.getRightX();
        double eqMidY = (eq.getBottomY() + eq.getTopY()) / 2.0;
        double candLeft = candidate.getLeftX();
        double candMidY = (candidate.getBottomY() + candidate.getTopY()) / 2.0;

        double xGap = candLeft - eqRight;
        double yDist = Math.abs(candMidY - eqMidY);

        // x-gap must be positive (candidate is to the right) and within range
        if (xGap < 0 || xGap > MAX_X_GAP) {
            return 0.0;
        }

        double xScore = 1.0 - (xGap / MAX_X_GAP);
        double yScore = Math.max(0.0, 1.0 - (yDist / MAX_Y_DISTANCE));

        // x-proximity is weighted more heavily (60%) than y-alignment (40%)
        return 0.6 * xScore + 0.4 * yScore;
    }

    private EquationNode unresolved(EquationNode equation, String reason) {
        return new EquationNode(
            equation.getLatex(),
            equation.isDisplayMode(),
            null,
            equation.getSpan(),
            equation.getPage(),
            equation.getBbox(),
            equation.getRawId(),
            null,
            reason
        );
    }
}

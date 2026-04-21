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

import org.opendataloader.pdf.graph.CaptionNode;
import org.opendataloader.pdf.graph.GraphNode;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CaptionLinkEnricher {

    public static final double SCORE_THRESHOLD = 0.5;

    private static final double MAX_X_DISTANCE = 400.0;
    private static final double MAX_Y_DISTANCE = 300.0;

    public List<GraphNode> enrich(List<GraphNode> nodes) {
        List<GraphNode> result = new ArrayList<>(nodes.size());
        for (GraphNode node : nodes) {
            if (node instanceof CaptionNode) {
                result.add(enrichCaption((CaptionNode) node, nodes));
            } else {
                result.add(node);
            }
        }
        return List.copyOf(result);
    }

    private CaptionNode enrichCaption(CaptionNode caption, List<GraphNode> nodes) {
        BoundingBox captionBox = caption.getBbox();
        if (captionBox == null) {
            return unresolved(caption, "caption has no bounding box");
        }

        String captionText = caption.getText() == null ? "" : caption.getText();
        boolean prefersTable = captionText.toLowerCase(Locale.ROOT).startsWith("table");
        boolean prefersFigure = captionText.toLowerCase(Locale.ROOT).startsWith("figure");

        GraphNode best = null;
        double bestScore = -1.0;

        for (GraphNode candidate : nodes) {
            if (candidate == caption || candidate instanceof CaptionNode) {
                continue;
            }
            BoundingBox cBox = candidate.getBbox();
            if (cBox == null) {
                continue;
            }
            if (!Objects.equals(candidate.getPage(), caption.getPage())) {
                continue;
            }

            double spatialScore = spatialScore(captionBox, cBox);
            if (spatialScore <= 0.0) {
                continue;
            }

            double lexicalBoost = lexicalBoost(candidate, prefersTable, prefersFigure);
            double totalScore = spatialScore + lexicalBoost;

            if (totalScore > bestScore) {
                bestScore = totalScore;
                best = candidate;
            }
        }

        if (best == null || bestScore < SCORE_THRESHOLD) {
            return unresolved(caption, "no reliable figure/table candidate found nearby");
        }

        double normalizedConfidence = Math.min(1.0, bestScore);
        return new CaptionNode(
            caption.getKind(),
            caption.getLabel(),
            best.getRawId(),
            caption.getText(),
            caption.getSpan(),
            caption.getPage(),
            caption.getBbox(),
            caption.getRawId(),
            normalizedConfidence
        );
    }

    private double spatialScore(BoundingBox caption, BoundingBox candidate) {
        double captionMidX = (caption.getLeftX() + caption.getRightX()) / 2.0;
        double captionMidY = (caption.getBottomY() + caption.getTopY()) / 2.0;
        double candMidX = (candidate.getLeftX() + candidate.getRightX()) / 2.0;
        double candMidY = (candidate.getBottomY() + candidate.getTopY()) / 2.0;

        double xDist = Math.abs(captionMidX - candMidX);
        double yDist = Math.abs(captionMidY - candMidY);

        if (xDist > MAX_X_DISTANCE || yDist > MAX_Y_DISTANCE) {
            return 0.0;
        }

        double xScore = 1.0 - (xDist / MAX_X_DISTANCE);
        double yScore = 1.0 - (yDist / MAX_Y_DISTANCE);

        // Vertical proximity weighted more heavily (70%) — captions are typically above/below targets
        return 0.3 * xScore + 0.7 * yScore;
    }

    private double lexicalBoost(GraphNode candidate, boolean prefersTable, boolean prefersFigure) {
        if (!prefersTable && !prefersFigure) {
            return 0.0;
        }
        String nodeText = extractText(candidate);
        if (nodeText == null) {
            return 0.0;
        }
        String lower = nodeText.toLowerCase(Locale.ROOT);
        if (prefersFigure && (lower.contains("figure") || lower.contains("image") || lower.contains("photo"))) {
            return 0.2;
        }
        if (prefersTable && lower.contains("table")) {
            return 0.2;
        }
        // Penalise if preference is figure but candidate looks like a table (and vice versa)
        if (prefersFigure && lower.contains("table")) {
            return -0.2;
        }
        if (prefersTable && (lower.contains("figure") || lower.contains("image"))) {
            return -0.2;
        }
        return 0.0;
    }

    private String extractText(GraphNode node) {
        if (node instanceof org.opendataloader.pdf.graph.TextNode) {
            return ((org.opendataloader.pdf.graph.TextNode) node).getText();
        }
        return null;
    }

    private CaptionNode unresolved(CaptionNode caption, String reason) {
        return new CaptionNode(
            caption.getKind(),
            caption.getLabel(),
            caption.getTargetId(),
            caption.getText(),
            caption.getSpan(),
            caption.getPage(),
            caption.getBbox(),
            caption.getRawId(),
            null,
            reason
        );
    }
}

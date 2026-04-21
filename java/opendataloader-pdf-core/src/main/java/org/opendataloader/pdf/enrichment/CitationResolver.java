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

import org.opendataloader.pdf.graph.CitationNode;
import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.ReferenceEntryNode;
import org.opendataloader.pdf.graph.TextNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CitationResolver {

    private static final Pattern CITATION_BRACKET = Pattern.compile("\\[([^\\]]+)\\]");

    public List<GraphNode> resolve(List<GraphNode> nodes) {
        Set<String> knownRefIds = collectRefIds(nodes);
        List<GraphNode> result = new ArrayList<>(nodes.size());
        for (GraphNode node : nodes) {
            if (node instanceof TextNode) {
                TextNode tn = (TextNode) node;
                List<String> markers = extractMarkers(tn.getText());
                if (markers.isEmpty()) {
                    result.add(node);
                } else {
                    result.add(buildCitation(tn, markers, knownRefIds));
                }
            } else {
                result.add(node);
            }
        }
        return List.copyOf(result);
    }

    private Set<String> collectRefIds(List<GraphNode> nodes) {
        Set<String> ids = new HashSet<>();
        for (GraphNode node : nodes) {
            if (node instanceof ReferenceEntryNode) {
                String refId = ((ReferenceEntryNode) node).getRefId();
                if (refId != null) {
                    ids.add(refId);
                }
            }
        }
        return ids;
    }

    private List<String> extractMarkers(String text) {
        List<String> markers = new ArrayList<>();
        if (text == null) {
            return markers;
        }
        Matcher m = CITATION_BRACKET.matcher(text);
        while (m.find()) {
            String inner = m.group(1).trim();
            markers.addAll(parseInner(inner));
        }
        return markers;
    }

    private List<String> parseInner(String inner) {
        List<String> markers = new ArrayList<>();
        if (inner.contains("-")) {
            String[] parts = inner.split("-", 2);
            try {
                int start = Integer.parseInt(parts[0].trim());
                int end = Integer.parseInt(parts[1].trim());
                if (start > end) {
                    for (String part : parts) {
                        String t = part.trim();
                        if (!t.isEmpty()) markers.add(t);
                    }
                    return markers;
                }
                for (int i = start; i <= end; i++) {
                    markers.add(String.valueOf(i));
                }
                return markers;
            } catch (NumberFormatException ignored) {
            }
        }
        for (String part : inner.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                markers.add(trimmed);
            }
        }
        return markers;
    }

    private CitationNode buildCitation(TextNode tn, List<String> markers, Set<String> knownRefIds) {
        List<String> resolved = new ArrayList<>();
        boolean anyUnresolved = false;
        for (String marker : markers) {
            if (knownRefIds.contains(marker)) {
                resolved.add(marker);
            } else {
                anyUnresolved = true;
            }
        }
        String unresolvedReason = anyUnresolved ? "one or more citation markers could not be resolved" : null;
        return new CitationNode(markers, resolved, tn.getText(),
            tn.getPage(), tn.getBbox(), tn.getRawId(), tn.getConfidence(), unresolvedReason);
    }
}

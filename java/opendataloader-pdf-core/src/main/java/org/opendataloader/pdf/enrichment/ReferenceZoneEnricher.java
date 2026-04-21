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

import org.opendataloader.pdf.graph.GraphNode;
import org.opendataloader.pdf.graph.ReferenceEntryNode;
import org.opendataloader.pdf.graph.TextNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReferenceZoneEnricher {

    private static final Pattern NUMERIC_REF = Pattern.compile("^\\[(\\d+)\\]");

    public List<GraphNode> enrich(List<GraphNode> nodes) {
        List<GraphNode> result = new ArrayList<>(nodes.size());
        boolean inRefZone = false;
        int authorYearCounter = 0;

        for (GraphNode node : nodes) {
            if (!inRefZone) {
                if (isReferencesHeading(node)) {
                    inRefZone = true;
                    result.add(node);
                } else {
                    result.add(node);
                }
            } else {
                if (node instanceof TextNode) {
                    TextNode tn = (TextNode) node;
                    String text = tn.getText() == null ? "" : tn.getText();
                    Matcher m = NUMERIC_REF.matcher(text);
                    if (m.find()) {
                        String refId = m.group(1);
                        result.add(new ReferenceEntryNode(refId, text, Collections.emptyMap(),
                            tn.getPage(), tn.getBbox(), tn.getRawId(), tn.getConfidence()));
                    } else if (isAuthorYearEntry(text)) {
                        authorYearCounter++;
                        String refId = "r" + authorYearCounter;
                        result.add(new ReferenceEntryNode(refId, text, Collections.emptyMap(),
                            tn.getPage(), tn.getBbox(), tn.getRawId(), tn.getConfidence()));
                    } else {
                        result.add(node);
                    }
                } else {
                    result.add(node);
                }
            }
        }
        return List.copyOf(result);
    }

    private boolean isReferencesHeading(GraphNode node) {
        if (!(node instanceof TextNode)) {
            return false;
        }
        String text = ((TextNode) node).getText();
        return text != null && text.trim().equalsIgnoreCase("references");
    }

    private boolean isAuthorYearEntry(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        char first = text.charAt(0);
        return Character.isUpperCase(first) && !text.startsWith("[");
    }
}

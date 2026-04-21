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
package org.opendataloader.pdf.graph;

import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CitationNode extends GraphNode {

    private final List<String> markers;
    private final List<String> resolvedRefIds;
    private final String span;

    public CitationNode(List<String> markers, List<String> resolvedRefIds, String span,
                        Integer page, BoundingBox bbox, Long rawId, Double confidence) {
        super(page, bbox, rawId, confidence, null);
        this.markers = markers == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(markers));
        this.resolvedRefIds = resolvedRefIds == null ? Collections.emptyList() :
            Collections.unmodifiableList(new ArrayList<>(resolvedRefIds));
        this.span = span;
    }

    public List<String> getMarkers() {
        return markers;
    }

    public List<String> getResolvedRefIds() {
        return resolvedRefIds;
    }

    public String getSpan() {
        return span;
    }
}

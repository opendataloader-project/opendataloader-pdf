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

/**
 * Base node for canonical extraction graph.
 */
public class GraphNode {

    private final Integer page;
    private final BoundingBox bbox;
    private final Long rawId;
    private final Double confidence;
    private final String unresolvedReason;

    public GraphNode(Integer page, BoundingBox bbox, Long rawId, Double confidence, String unresolvedReason) {
        this.page = page;
        this.bbox = bbox == null ? null : new BoundingBox(bbox);
        this.rawId = rawId;
        this.confidence = confidence;
        this.unresolvedReason = unresolvedReason;
    }

    public Integer getPage() {
        return page;
    }

    public BoundingBox getBbox() {
        return bbox == null ? null : new BoundingBox(bbox);
    }

    public Long getRawId() {
        return rawId;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getUnresolvedReason() {
        return unresolvedReason;
    }
}

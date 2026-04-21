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

public class CaptionNode extends GraphNode {

    private final String kind;
    private final String label;
    private final Long targetId;
    private final String text;
    private final String span;

    public CaptionNode(String kind, String label, Long targetId, String text, String span,
                       Integer page, BoundingBox bbox, Long rawId, Double confidence) {
        super(page, bbox, rawId, confidence, null);
        this.kind = kind;
        this.label = label;
        this.targetId = targetId;
        this.text = text;
        this.span = span;
    }

    public String getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getText() {
        return text;
    }

    public String getSpan() {
        return span;
    }
}

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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ReferenceEntryNode extends GraphNode {

    private final String refId;
    private final String text;
    private final Map<String, String> metadata;

    public ReferenceEntryNode(String refId, String text, Map<String, String> metadata,
                              Integer page, BoundingBox bbox, Long rawId, Double confidence) {
        super(page, bbox, rawId, confidence, null);
        this.refId = refId;
        this.text = text;
        this.metadata = metadata == null ? Collections.emptyMap() : Collections.unmodifiableMap(new HashMap<>(metadata));
    }

    public String getRefId() {
        return refId;
    }

    public String getText() {
        return text;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
}

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

public class EquationNode extends GraphNode {

    private final String latex;
    private final boolean displayMode;
    private final String number;
    private final String span;

    public EquationNode(String latex, boolean displayMode, String number, String span,
                        Integer page, BoundingBox bbox, Long rawId, Double confidence) {
        super(page, bbox, rawId, confidence, null);
        this.latex = latex;
        this.displayMode = displayMode;
        this.number = number;
        this.span = span;
    }

    public String getLatex() {
        return latex;
    }

    public boolean isDisplayMode() {
        return displayMode;
    }

    public String getNumber() {
        return number;
    }

    public String getSpan() {
        return span;
    }
}

/*
 * Copyright 2025 Hancom Inc.
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
package org.opendataloader.pdf.entities;

import org.verapdf.wcag.algorithms.entities.BaseObject;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

/**
 * Represents a mathematical formula element with LaTeX content.
 *
 * <p>This class stores formula content in LaTeX format, which can be rendered
 * using MathJax, KaTeX, or similar libraries in the output formats.
 *
 * <p>Extends BaseObject to leverage the standard IObject implementation.
 */
public class SemanticFormula extends BaseObject {

    private final String latex;

    /**
     * Creates a SemanticFormula with the given bounding box and LaTeX content.
     *
     * @param boundingBox The bounding box of the formula
     * @param latex       The LaTeX representation of the formula
     */
    public SemanticFormula(BoundingBox boundingBox, String latex) {
        super(boundingBox);
        this.latex = latex;
    }

    /**
     * Gets the LaTeX representation of the formula.
     *
     * @return The LaTeX string, or empty string if null
     */
    public String getLatex() {
        return latex != null ? latex : "";
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
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

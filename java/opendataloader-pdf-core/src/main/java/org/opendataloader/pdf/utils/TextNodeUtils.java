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
package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.util.logging.Level;
import java.util.logging.Logger;

public class TextNodeUtils {
    private static final Logger LOGGER = Logger.getLogger(TextNodeUtils.class.getName());
    private static final double[] DEFAULT_TEXT_COLOR = {0.0, 0.0, 0.0};

    /**
     * Returns the text color, falling back to default black on NPE.
     * Hybrid backend nodes may lack color info; returning a default
     * keeps heading detection and line merging working normally.
     */
    public static double[] getTextColorOrDefault(SemanticTextNode textNode) {
        try {
            double[] color = textNode.getTextColor();
            return color != null ? color : DEFAULT_TEXT_COLOR;
        } catch (NullPointerException e) {
            LOGGER.log(Level.FINE, "textColor unavailable, using default black", e);
            return DEFAULT_TEXT_COLOR;
        }
    }

    /**
     * Returns the raw text color, or null if unavailable.
     * Use this for serialization where omitting the field is preferred
     * over writing a fabricated default.
     */
    public static double[] getTextColorOrNull(SemanticTextNode textNode) {
        try {
            return textNode.getTextColor();
        } catch (NullPointerException e) {
            LOGGER.log(Level.FINE, "textColor unavailable", e);
            return null;
        }
    }
}

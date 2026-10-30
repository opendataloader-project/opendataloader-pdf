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
package org.opendataloader.pdf.entities;

import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

/**
 * Text the page draws as graphics, not as text: letters filled as outlines,
 * read by OCR.
 *
 * <p>Its value is what OCR read, and its stream infos are the paint operators
 * that draw it. The tagger marks those operators as the element's content and
 * gives the element the value as /ActualText, which is what a reader hears;
 * the operators themselves carry no text a reader could extract.
 */
public class DrawnTextChunk extends TextChunk {

    /** A size for the line's geometry; the drawing has no font. */
    private static final double FONT_SIZE = 12.0;

    public DrawnTextChunk(BoundingBox boundingBox, String text) {
        super(boundingBox, text, FONT_SIZE, FONT_SIZE);
        adjustSymbolEndsToBoundingBox(null);
    }
}

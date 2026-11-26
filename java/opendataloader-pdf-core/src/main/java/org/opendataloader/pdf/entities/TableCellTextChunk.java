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
 * The text of a table cell as the backend read it.
 *
 * <p>Its own box is the cell's box, an even split of the table, so that the
 * outputs stay as they are. {@link #getTextBox()} is the box the backend gave
 * the text itself: where the page draws it. The tagged PDF uses it to ask
 * whether the page already shows this text, because the cell's box can lie
 * over the empty part of a column whose text is drawn at its right edge.
 */
public class TableCellTextChunk extends TextChunk {

    /** A size for the line's geometry; the backend gives no font. */
    private static final double FONT_SIZE = 12.0;

    private final BoundingBox textBox;

    /**
     * @param boundingBox the cell's box
     * @param text        what the backend read in the cell
     * @param textBox     the box the backend gave the text; the cell's box when it gave none
     */
    public TableCellTextChunk(BoundingBox boundingBox, String text, BoundingBox textBox) {
        super(boundingBox, text, FONT_SIZE, FONT_SIZE);
        this.textBox = textBox;
        adjustSymbolEndsToBoundingBox(null);
    }

    /**
     * @return the box of the text as drawn
     */
    public BoundingBox getTextBox() {
        return textBox;
    }
}

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

import org.verapdf.wcag.algorithms.entities.BaseObject;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

/**
 * Represents a picture element with optional description (alt text).
 *
 * <p>This class stores picture metadata including AI-generated descriptions
 * for accessibility purposes. Descriptions are generated using vision-language
 * models when the hybrid server is configured with --enrich-picture-description.
 *
 * <p>Extends BaseObject to leverage the standard IObject implementation.
 */
public class SemanticPicture extends BaseObject {

    private final int index;
    private final String description;

    /**
     * Creates a SemanticPicture with the given bounding box and index.
     *
     * @param boundingBox The bounding box of the picture
     * @param index       The sequential index of the picture
     */
    public SemanticPicture(BoundingBox boundingBox, int index) {
        this(boundingBox, index, null);
    }

    /**
     * Creates a SemanticPicture with the given bounding box, index, and description.
     *
     * @param boundingBox The bounding box of the picture
     * @param index       The sequential index of the picture
     * @param description The AI-generated description (alt text) for accessibility
     */
    public SemanticPicture(BoundingBox boundingBox, int index, String description) {
        super(boundingBox);
        this.index = index;
        this.description = description;
    }

    /**
     * Gets the sequential index of this picture.
     *
     * @return The picture index
     */
    public int getPictureIndex() {
        return index;
    }

    /**
     * Gets the description (alt text) of this picture.
     *
     * @return The description string, or empty string if null
     */
    public String getDescription() {
        return description != null ? description : "";
    }

    /**
     * Checks if this picture has a description.
     *
     * @return true if description is non-null and non-empty
     */
    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }

    /**
     * Returns a sanitized version of the description safe for use as alt text
     * across all output formats (Markdown, HTML, JSON) without format-specific escaping.
     *
     * <p>Removes characters that are structurally significant in at least one output format:
     * <ul>
     *   <li>{@code "} — HTML attribute delimiter</li>
     *   <li>{@code [}, {@code ]} — Markdown alt text delimiters</li>
     *   <li>{@code <}, {@code >} — HTML tag delimiters</li>
     *   <li>{@code &} — HTML entity prefix</li>
     *   <li>{@code \u0000} — null character</li>
     *   <li>Newlines ({@code \n}, {@code \r}) — replaced with a space</li>
     * </ul>
     * Consecutive whitespace is collapsed to a single space and the result is trimmed.
     *
     * @return sanitized description string, or empty string if no description
     */
    public String sanitizeDescription() {
        if (!hasDescription()) {
            return "";
        }
        return description
                .replace("\r\n", " ")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\"", "")
                .replace("[", "")
                .replace("]", "")
                .replace("<", "")
                .replace(">", "")
                .replace("&", "")
                .replace("\u0000", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}

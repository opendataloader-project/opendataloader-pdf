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
}

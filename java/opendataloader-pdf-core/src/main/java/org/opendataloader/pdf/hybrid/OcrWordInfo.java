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
package org.opendataloader.pdf.hybrid;

import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

/**
 * Holds OCR-recognized text and its bounding box in PDF coordinate space.
 *
 * <p>Used to preserve word-level OCR data from the DLA+OCR pipeline so that
 * the enrichment step can create invisible text operators when Java TextChunks
 * are not available (e.g., scanned/image-based pages).
 */
public class OcrWordInfo {

    private final String text;
    private final BoundingBox bbox;

    public OcrWordInfo(String text, BoundingBox bbox) {
        this.text = text;
        this.bbox = bbox;
    }

    /**
     * Gets the OCR-recognized text for this word.
     *
     * @return the recognized text
     */
    public String getText() {
        return text;
    }

    /**
     * Gets the bounding box in PDF coordinate space (72 DPI, bottom-left origin).
     *
     * @return the bounding box
     */
    public BoundingBox getBbox() {
        return bbox;
    }
}

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

import org.verapdf.wcag.algorithms.entities.content.ImageChunk;

/**
 * An ImageChunk enriched with an AI-generated description (alt text).
 *
 * <p>Created when the hybrid backend returns a SemanticPicture whose bounding
 * box overlaps a Java-extracted ImageChunk. The description is matched by
 * bounding-box IoU in HybridDocumentProcessor and propagated to:
 * <ul>
 *   <li>AutoTaggingProcessor — inserts /Alt into the Figure struct element</li>
 *   <li>ImageSerializer — writes "alt" field to JSON output</li>
 *   <li>MarkdownGenerator / HtmlGenerator — uses description as alt text</li>
 * </ul>
 */
public class EnrichedImageChunk extends ImageChunk {

    private final String description;

    public EnrichedImageChunk(ImageChunk source, String description) {
        super(source.getBoundingBox());
        // Copy index so serializers can reference the image file
        setIndex(source.getIndex());
        // Copy stream infos so MCID / struct-tree linkage is preserved
        getStreamInfos().addAll(source.getStreamInfos());
        this.description = description;
    }

    public String getDescription() {
        return description != null ? description : "";
    }

    public boolean hasDescription() {
        return description != null && !description.isEmpty();
    }

    /**
     * Sanitized description safe for use as PDF /Alt, Markdown alt text,
     * HTML alt attribute, and JSON value.
     */
    public String sanitizeDescription() {
        if (!hasDescription()) return "";
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
                // PDF/UA-2 clause 8.4.3.3 forbids Private Use Area code points in /Alt.
                // Strip BMP PUA (U+E000–U+F8FF) and supplementary PUA (encoded as surrogate pairs
                // U+DB80–U+DBFF paired with U+DC00–U+DFFF).
                .replaceAll("[\\uE000-\\uF8FF]|[\\uDB80-\\uDBFF][\\uDC00-\\uDFFF]", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
    }
}

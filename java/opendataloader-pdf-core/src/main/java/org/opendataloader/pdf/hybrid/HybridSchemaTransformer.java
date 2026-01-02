/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.verapdf.wcag.algorithms.entities.IObject;

import java.util.List;
import java.util.Map;

/**
 * Interface for transforming hybrid backend responses to IObject hierarchy.
 *
 * <p>Implementations of this interface convert backend-specific JSON output
 * (e.g., Docling's DoclingDocument format) to the OpenDataLoader IObject
 * structure that downstream processors and generators expect.
 *
 * <p>The transformer ensures schema compatibility between different backends
 * and the Java processing path, allowing seamless integration of results.
 */
public interface HybridSchemaTransformer {

    /**
     * Transforms a hybrid backend response to a list of IObjects per page.
     *
     * <p>The returned structure matches the format expected by downstream
     * processors: a list indexed by page number (0-based), where each entry
     * contains the IObjects for that page.
     *
     * @param response     The hybrid backend response containing JSON output.
     * @param pageHeights  Map of page number (1-indexed) to page height in PDF points.
     *                     Used for coordinate transformation if needed.
     * @return A list of IObject lists, one per page (0-indexed).
     */
    List<List<IObject>> transform(HybridResponse response, Map<Integer, Double> pageHeights);

    /**
     * Transforms per-page JSON content to IObjects for a specific page.
     *
     * <p>This method is useful when processing pages individually or when
     * the backend provides separate responses per page.
     *
     * @param pageNumber   The 1-indexed page number.
     * @param pageContent  The JSON content for the page.
     * @param pageHeight   The page height in PDF points.
     * @return A list of IObjects for the specified page.
     */
    List<IObject> transformPage(int pageNumber, JsonNode pageContent, double pageHeight);

    /**
     * Returns the backend type this transformer handles.
     *
     * @return The backend name (e.g., "docling", "hancom").
     */
    String getBackendType();
}

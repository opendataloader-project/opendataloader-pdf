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
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit guard for {@link DocumentProcessor#getPageBoundingBox(int)} resolving page
 * bounds from the precomputed crop-box cache.
 *
 * <p>On ForkJoinPool worker threads {@code StaticResources.getDocument()} is null
 * (it is a ThreadLocal set only on the main thread). These tests simulate that
 * worker condition (document == null) to verify the cache fallback that fixes the
 * off-page / background filtering regression.
 */
class DocumentProcessorPageBoundingBoxTest {

    @BeforeEach
    void setUp() {
        StaticLayoutContainers.clearContainers();
        // Simulate a worker thread: no document available via the ThreadLocal.
        StaticResources.setDocument(null);
    }

    @AfterEach
    void tearDown() {
        StaticResources.setDocument(null);
        StaticLayoutContainers.clearContainers();
    }

    @Test
    void resolvesFromCacheWhenDocumentUnavailable() {
        // [leftX, bottomY, rightX, topY]
        StaticLayoutContainers.getPageCropBoxesMap().put(0, new double[] {0, 0, 612, 792});

        BoundingBox box = DocumentProcessor.getPageBoundingBox(0);

        assertNotNull(box, "Page bounds must resolve from the cache on a worker thread "
            + "(document ThreadLocal is null there)");
        assertEquals(0, box.getLeftX());
        assertEquals(0, box.getBottomY());
        assertEquals(612, box.getRightX());
        assertEquals(792, box.getTopY());
    }

    @Test
    void returnsNullWhenNeitherCacheNorDocumentAvailable() {
        assertNull(DocumentProcessor.getPageBoundingBox(0),
            "Without a cached crop box and without a document, bounds are unknown");
    }

    @Test
    void returnsFreshInstanceSoCallerMutationDoesNotCorruptCache() {
        StaticLayoutContainers.getPageCropBoxesMap().put(0, new double[] {10, 20, 612, 792});

        // filterOutOfPageContents mutates the returned box via move(...).
        BoundingBox first = DocumentProcessor.getPageBoundingBox(0);
        first.move(-first.getLeftX(), -first.getBottomY());
        assertEquals(0, first.getLeftX());

        BoundingBox second = DocumentProcessor.getPageBoundingBox(0);
        assertEquals(10, second.getLeftX(),
            "A previous caller's move() must not corrupt the cached crop box");
        assertEquals(20, second.getBottomY());
    }
}

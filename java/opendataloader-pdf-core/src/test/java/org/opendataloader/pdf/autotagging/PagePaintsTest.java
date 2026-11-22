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
package org.opendataloader.pdf.autotagging;

import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PagePaintsTest {

    /**
     * Bounding a page reads its content stream, which can be malformed. A page
     * that cannot be bounded hands out nothing: its text stays as OCR read it,
     * and the other pages and the rest of the run go on.
     */
    @Test
    void aPageThatCannotBeBoundedHandsOutNothing() {
        PagePaints paints = new PagePaints(0, () -> {
            throw new IllegalStateException("broken content stream");
        });

        assertEquals(List.of(), paints.takeInside(new BoundingBox(0, 0, 0, 100, 100), 5));
    }
}

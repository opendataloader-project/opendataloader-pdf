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
package org.opendataloader.pdf.issues;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review of #669: sortPageContents sorts every page, also when coordinates are -0.0, 0.0, or NaN.
 * Commit 68dba042 made the comparator consistent for such values; an inconsistent comparator
 * makes List.sort throw IllegalArgumentException ("Comparison method violates its general
 * contract!"), and the page is not converted.
 */
class Review669_SortPageContentsComparatorTest {

    @Test
    void contentsWithNegativeZeroAndNaNCoordinatesAreAllSorted() {
        double[] lefts = {0.0, -0.0, Double.NaN, 1.0};
        Random random = new Random(669);
        for (int round = 0; round < 300; round++) {
            List<IObject> contents = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                double left = lefts[random.nextInt(lefts.length)];
                BoundingBox box = new BoundingBox(0, left, 90.0, left + 10.0, 100.0);
                contents.add(new TextChunk(box, "t" + i, 10, 10));
            }
            List<IObject> sorted = DocumentProcessor.sortPageContents(contents);
            assertEquals(contents.size(), sorted.size(), "round " + round + ": not every element is in the output");
        }
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

class StaticContainersThreadContextTest {

    @Test
    void shouldApplyStaticContainersContextOnWorkerThread() throws Exception {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);

        StaticContainersThreadContext.Snapshot snapshot = StaticContainersThreadContext.capture();

        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0), "test", 10, 30.0));
        contents.add(new TextChunk(new BoundingBox(0, 20.0, 30.0, 30.0, 40.0), "test", 10, 30.0));

        try (ForkJoinPool pool = new ForkJoinPool(1)) {
            List<IObject> processed = pool.submit(() -> {
                StaticContainersThreadContext.apply(snapshot);
                return TextLineProcessor.processTextLines(contents);
            }).get();

            Assertions.assertEquals(1, processed.size());
            Assertions.assertTrue(processed.get(0) instanceof TextLine);
            Assertions.assertEquals("testtest", ((TextLine) processed.get(0)).getValue());
        }
    }
}

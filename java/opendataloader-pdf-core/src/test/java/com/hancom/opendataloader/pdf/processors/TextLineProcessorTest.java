/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class TextLineProcessorTest {

    @Test
    public void testProcessTextLines() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "test", 10, 30.0));
        contents.add(new TextChunk(new BoundingBox(0, 20.0, 30.0, 30.0, 40.0),
            "test", 10, 30.0));
        contents.add(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "test", 10, 20.0));
        contents = TextLineProcessor.processTextLines(contents);
        Assertions.assertEquals(2, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TextLine);
        Assertions.assertEquals("testtest", ((TextLine) contents.get(0)).getValue());
        Assertions.assertTrue(contents.get(1) instanceof TextLine);
        Assertions.assertEquals("test", ((TextLine) contents.get(1)).getValue());
    }

}

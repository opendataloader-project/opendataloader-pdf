/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class SpecialTableProcessorTest {

    @Test
    public void testDetectSpecialTables() {
        StaticContainers.setTableBordersCollection(new TableBordersCollection());
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(0);
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 30.0, 20.0, 40.0),
            "수신", 10, 30.0)));
        contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 20.0, 20.0, 30.0),
            "경유", 10, 20.0)));
        contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "제목", 10, 10.0)));
        contents = SpecialTableProcessor.detectSpecialTables(contents);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof TableBorder);
    }

}

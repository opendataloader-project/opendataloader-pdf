/*
 * Copyright 2025 Hancom Inc.
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

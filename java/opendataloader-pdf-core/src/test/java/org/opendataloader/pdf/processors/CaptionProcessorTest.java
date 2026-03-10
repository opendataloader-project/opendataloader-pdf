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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.maps.AccumulatedNodeMapper;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class CaptionProcessorTest {

    @Test
    public void testProcessCaptions() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setAccumulatedNodeMapper(new AccumulatedNodeMapper());
        List<IObject> contents = new ArrayList<>();
        SemanticParagraph paragraph = new SemanticParagraph();
        contents.add(paragraph);
        paragraph.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "test", 10, 10.0)));
        contents.add(new ImageChunk(new BoundingBox(1, 10.0, 20.0, 20.0, 30.0)));
        CaptionProcessor.processCaptions(contents);
        Assertions.assertEquals(2, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof SemanticCaption);
    }
}

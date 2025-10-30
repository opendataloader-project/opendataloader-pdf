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
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class HeaderFooterProcessorTest {

    @Test
    public void testProcessHeadersAndFooters() {
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticResources.setDocument(null);
        StaticLayoutContainers.setCurrentContentId(0);
        List<List<IObject>> contents = new ArrayList<>();
        List<IObject> page1Contents = new ArrayList<>();
        page1Contents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "Header", 10, 30.0)));
        page1Contents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "Text", 10, 20.0)));
        page1Contents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 10.0, 20.0, 20.0),
            "Footer1", 10, 10.0)));
        List<IObject> page2Contents = new ArrayList<>();
        page2Contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 30.0, 20.0, 40.0),
            "Header", 10, 30.0)));
        page2Contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 20.0, 20.0, 30.0),
            "Different Text", 10, 20.0)));
        page2Contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "Footer2", 10, 10.0)));
        contents.add(page1Contents);
        contents.add(page2Contents);
        HeaderFooterProcessor.processHeadersAndFooters(contents, false);

        Assertions.assertEquals(3, contents.get(0).size());
        Assertions.assertEquals(3, contents.get(1).size());

        Assertions.assertTrue(contents.get(0).get(0) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.HEADER, ((SemanticHeaderOrFooter) contents.get(0).get(0)).getSemanticType());
        Assertions.assertTrue(contents.get(1).get(0) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.HEADER, ((SemanticHeaderOrFooter) contents.get(1).get(0)).getSemanticType());
        Assertions.assertTrue(contents.get(0).get(2) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.FOOTER, ((SemanticHeaderOrFooter) contents.get(0).get(2)).getSemanticType());
        Assertions.assertTrue(contents.get(1).get(2) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.FOOTER, ((SemanticHeaderOrFooter) contents.get(1).get(2)).getSemanticType());
    }
}

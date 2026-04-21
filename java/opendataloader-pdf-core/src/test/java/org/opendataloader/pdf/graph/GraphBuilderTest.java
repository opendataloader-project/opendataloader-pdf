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
package org.opendataloader.pdf.graph;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GraphBuilderTest {

    @Test
    public void testBuildReturnsEmptyForNullAndEmptyInput() {
        List<GraphNode> fromNull = GraphBuilder.build(null);
        List<GraphNode> fromEmpty = GraphBuilder.build(Collections.emptyList());

        Assertions.assertTrue(fromNull.isEmpty());
        Assertions.assertTrue(fromEmpty.isEmpty());
    }

    @Test
    public void testBuildSkipsNullPagesAndNullObjects() {
        SemanticHeading heading = new SemanticHeading();
        heading.add(new TextLine(new TextChunk(new BoundingBox(0, 1.0, 2.0, 3.0, 4.0),
            "heading", 12.0, 12.0)));
        heading.setHeadingLevel(1);

        List<IObject> pageZero = new ArrayList<>();
        pageZero.add(null);
        pageZero.add(heading);

        List<List<IObject>> contents = new ArrayList<>();
        contents.add(null);
        contents.add(pageZero);

        List<GraphNode> nodes = GraphBuilder.build(contents);

        Assertions.assertEquals(1, nodes.size());
        Assertions.assertTrue(nodes.get(0) instanceof HeadingNode);
    }

    @Test
    public void testBuildReturnsImmutableList() {
        SemanticHeading heading = new SemanticHeading();
        heading.add(new TextLine(new TextChunk(new BoundingBox(0, 5.0, 6.0, 7.0, 8.0),
            "immutable", 12.0, 12.0)));
        heading.setHeadingLevel(1);

        List<GraphNode> nodes = GraphBuilder.build(Collections.singletonList(Collections.singletonList(heading)));

        Assertions.assertThrows(UnsupportedOperationException.class,
            () -> nodes.add(new GraphNode(0, null, null, null, null)));
    }

    @Test
    public void testBuildDefensivelyCopiesBoundingBox() {
        BoundingBox sourceBbox = new BoundingBox(0, 10.0, 20.0, 110.0, 40.0);
        SemanticHeading heading = new SemanticHeading();
        heading.add(new TextLine(new TextChunk(sourceBbox, "Introduction", 12.0, 12.0)));
        heading.setHeadingLevel(1);

        List<GraphNode> nodes = GraphBuilder.build(Collections.singletonList(Collections.singletonList(heading)));
        GraphNode node = nodes.get(0);

        Assertions.assertNotSame(sourceBbox, node.getBbox());
        Assertions.assertEquals(sourceBbox.getLeftX(), node.getBbox().getLeftX());
        Assertions.assertEquals(sourceBbox.getTopY(), node.getBbox().getTopY());
        Assertions.assertEquals(sourceBbox.getRightX(), node.getBbox().getRightX());
        Assertions.assertEquals(sourceBbox.getBottomY(), node.getBbox().getBottomY());
    }

    @Test
    public void testBuildMapsKnownAndUnknownNodes() {
        List<IObject> pageZero = new ArrayList<>();

        SemanticHeading heading = new SemanticHeading();
        heading.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 110.0, 40.0),
            "Introduction", 12.0, 12.0)));
        heading.setHeadingLevel(2);
        heading.setRecognizedStructureId(101L);
        pageZero.add(heading);

        SemanticFormula formula = new SemanticFormula(new BoundingBox(0, 20.0, 80.0, 120.0, 120.0), "\\frac{x}{y}");
        formula.setRecognizedStructureId(202L);
        pageZero.add(formula);

        SemanticParagraph captionParagraph = new SemanticParagraph();
        captionParagraph.add(new TextLine(new TextChunk(new BoundingBox(0, 30.0, 130.0, 150.0, 150.0),
            "Figure 1: sample", 11.0, 11.0)));
        SemanticCaption caption = new SemanticCaption(captionParagraph);
        caption.setRecognizedStructureId(303L);
        caption.setLinkedContentId(9001L);
        pageZero.add(caption);

        SemanticParagraph unknown = new SemanticParagraph();
        unknown.add(new TextLine(new TextChunk(new BoundingBox(0, 40.0, 160.0, 180.0, 190.0),
            "plain paragraph", 10.0, 10.0)));
        unknown.setRecognizedStructureId(404L);
        pageZero.add(unknown);

        List<GraphNode> nodes = GraphBuilder.build(Collections.singletonList(pageZero));

        Assertions.assertEquals(4, nodes.size());

        Assertions.assertTrue(nodes.get(0) instanceof HeadingNode);
        HeadingNode headingNode = (HeadingNode) nodes.get(0);
        Assertions.assertEquals(2, headingNode.getLevel());
        Assertions.assertEquals("Introduction", headingNode.getText());
        Assertions.assertEquals(0, headingNode.getPage());
        Assertions.assertEquals(10.0, headingNode.getBbox().getLeftX());
        Assertions.assertEquals(101L, headingNode.getRawId());

        Assertions.assertTrue(nodes.get(1) instanceof EquationNode);
        EquationNode equationNode = (EquationNode) nodes.get(1);
        Assertions.assertEquals("\\frac{x}{y}", equationNode.getLatex());
        Assertions.assertEquals(20.0, equationNode.getBbox().getLeftX());
        Assertions.assertEquals(202L, equationNode.getRawId());

        Assertions.assertTrue(nodes.get(2) instanceof CaptionNode);
        CaptionNode captionNode = (CaptionNode) nodes.get(2);
        Assertions.assertEquals(9001L, captionNode.getTargetId());
        Assertions.assertEquals(303L, captionNode.getRawId());

        Assertions.assertFalse(nodes.get(3) instanceof HeadingNode);
        Assertions.assertFalse(nodes.get(3) instanceof EquationNode);
        Assertions.assertFalse(nodes.get(3) instanceof CaptionNode);
        Assertions.assertEquals(404L, nodes.get(3).getRawId());
        Assertions.assertTrue(nodes.get(3) instanceof TextNode);
        Assertions.assertEquals("plain paragraph", ((TextNode) nodes.get(3)).getText().trim());
    }
}

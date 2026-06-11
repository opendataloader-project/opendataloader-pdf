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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.TextAlignment;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class ParagraphProcessorTest {

    @Test
    public void testProcessParagraphs() {
        StaticContainers.setIsDataLoader(true);
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 30.0, 20.0, 40.0),
            "test", 10, 30.0)));
        contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 20.0, 20.0, 30.0),
            "test", 10, 20.0)));
        contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "test", 10, 10.0)));
        contents = ParagraphProcessor.processParagraphs(contents);
        Assertions.assertEquals(1, contents.size());
        Assertions.assertTrue(contents.get(0) instanceof SemanticParagraph);
    }

    /**
     * Regression test for PR `#567`: right-alignment detection must claim adjacent single-line
     * blocks before the two-line paragraph heuristic.
     *
     * <p>Two adjacent single-line TextLines with right-aligned geometry (identical rightX,
     * differing leftX) satisfy BOTH detection predicates:
     * <ul>
     *   <li>{`@code` areLinesOfParagraphsWithRightAlignments} → would set {`@link` TextAlignment#RIGHT}</li>
     *   <li>{`@code` isTwoLinesParagraph} → would set {`@link` TextAlignment#LEFT}</li>
     * </ul>
     *
     * <p>After the reorder ({`@code` detectParagraphsWithRightAlignments} before
     * {`@code` detectTwoLinesParagraphs}), the right-alignment pass must claim the blocks first,
     * so the merged paragraph carries {`@link` TextAlignment#RIGHT}.
     * If the detection order were reversed the block would be LEFT-aligned instead.
     */
    @Test
    public void testRightAlignmentTakesPrecedenceOverTwoLineHeuristic() {
        StaticContainers.setIsDataLoader(true);

        // Line 1 – shorter, flush right: leftX=15, rightX=20
        // Line 2 – longer,  flush right: leftX=10, rightX=20
        //
        // Geometry properties:
        //   • Both lines share rightX=20                  → ChunksMergeUtils.getAlignment() == RIGHT
        //   • line1.leftX(15) ≥ line2.leftX(10)          → isTwoLinesParagraph leftX  check passes
        //   • line1.rightX(20) ≥ line2.rightX(20)        → isTwoLinesParagraph rightX check passes
        //   • Lines are vertically adjacent (line height = font size = 10)
        //   • Same font size (10)                         → areTextBlocksHaveSameTextSize passes
        //
        // Both heuristics would match, so the test verifies which one wins after the reorder.
        List<IObject> contents = new ArrayList<>();
        contents.add(new TextLine(new TextChunk(
            new BoundingBox(1, 15.0, 20.0, 20.0, 30.0), "short", 10, 20.0)));
        contents.add(new TextLine(new TextChunk(
            new BoundingBox(1, 10.0, 10.0, 20.0, 20.0), "longer line", 10, 10.0)));

        contents = ParagraphProcessor.processParagraphs(contents);

        // The two right-aligned lines must be merged into exactly one paragraph.
        Assertions.assertEquals(1, contents.size(),
            "Two right-aligned single lines should be merged into one paragraph");
        Assertions.assertInstanceOf(SemanticParagraph.class, contents.get(0));

        // The merged block must carry TextAlignment.RIGHT because detectParagraphsWithRightAlignments
        // now runs before detectTwoLinesParagraphs.  If the order were reversed, the block would
        // be LEFT-aligned (set by isTwoLinesParagraph).
        SemanticParagraph para = (SemanticParagraph) contents.get(0);
        TextBlock block = para.getLastColumn().getBlocks().get(0);
        Assertions.assertEquals(TextAlignment.RIGHT, block.getTextAlignment(),
            "detectParagraphsWithRightAlignments must claim the blocks before " +
                "detectTwoLinesParagraphs; expected TextAlignment.RIGHT but got: " +
                block.getTextAlignment());
    }
}

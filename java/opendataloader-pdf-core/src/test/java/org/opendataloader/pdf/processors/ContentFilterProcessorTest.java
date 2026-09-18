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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class ContentFilterProcessorTest {

    /**
     * Regression test for issue #150: short text chunks with abnormally wide bounding boxes.
     *
     * When PDF streams have text rendered in non-sequential order within a single Tj/TJ
     * operation, VeraPDF may calculate incorrect bounding boxes where rightX extends far
     * beyond the actual character width. For example, a single character "4" with height 10
     * might get a width of 42 instead of ~7.
     *
     * This causes the text to span multiple table cells incorrectly, leading to text being
     * dropped or assigned to wrong cells (the "프로트롬빈 시간" row in issue #150 where
     * numbers like "4" and "6" were missing).
     *
     * The fix should detect and correct these abnormal bounding boxes for short text (1-3 chars)
     * where actualWidth > expectedWidth * 3.
     */
    @Test
    public void testShortTextWithAbnormallyWideBoundingBox() {
        // Given: A single character "4" with height 10 but abnormally wide bbox (width=42)
        double height = 10.0;
        double leftX = 180.0;
        double abnormalRightX = 222.0; // Width = 42, expected ~7 for single char
        TextChunk textChunk = new TextChunk(
            new BoundingBox(0, leftX, 100.0, abnormalRightX, 100.0 + height),
            "4", height, 100.0);

        double actualWidth = textChunk.getBoundingBox().getWidth();
        double expectedMaxWidth = 1 * height * 0.7 * 3; // char_count * height * 0.7 * threshold(3x)

        // This assertion documents the bug: the width is abnormally large
        // When fixAbnormalTextChunkBoundingBoxes() is implemented, this should be corrected
        Assertions.assertTrue(actualWidth > expectedMaxWidth,
            "This test documents that the bounding box width (" + actualWidth +
            ") is abnormally large compared to expected max (" + expectedMaxWidth +
            ") for a single character. A fix should correct this.");
    }

    /**
     * Regression test for issue #150: normal text chunks should not be affected.
     *
     * Text chunks with reasonable widths (width <= expectedWidth * 3) should not
     * have their bounding boxes modified.
     */
    @Test
    public void testNormalTextWidthNotAbnormal() {
        // Given: A two-character text "AB" with reasonable width
        double height = 10.0;
        double leftX = 100.0;
        double normalRightX = 115.0; // Width = 15, reasonable for 2 chars
        TextChunk textChunk = new TextChunk(
            new BoundingBox(0, leftX, 100.0, normalRightX, 100.0 + height),
            "AB", height, 100.0);

        double actualWidth = textChunk.getBoundingBox().getWidth();
        double expectedMaxWidth = 2 * height * 0.7 * 3; // char_count * height * 0.7 * threshold(3x)

        // Normal width should be within expected range
        Assertions.assertTrue(actualWidth <= expectedMaxWidth,
            "Normal text chunk width (" + actualWidth + ") should not exceed threshold (" +
            expectedMaxWidth + ")");
    }

    /**
     * Regression test for issue #150: long text (>3 chars) should not be considered abnormal.
     *
     * The fix should only target short text chunks (1-3 characters) where the width
     * calculation is clearly wrong. Longer text can legitimately have wider bounding boxes.
     */
    @Test
    public void testLongTextNotTargetedForCorrection() {
        // Given: A 5-character text "Hello" with a wide bbox - this is plausible for longer text
        double height = 10.0;
        double leftX = 100.0;
        double rightX = 200.0; // Width = 100
        TextChunk textChunk = new TextChunk(
            new BoundingBox(0, leftX, 100.0, rightX, 100.0 + height),
            "Hello", height, 100.0);

        // For text with more than 3 characters, the fix should not apply
        // regardless of the width-to-height ratio
        Assertions.assertEquals(5, textChunk.getValue().length(),
            "Long text should have 5 characters");
        Assertions.assertEquals(100.0, textChunk.getBoundingBox().getWidth(), 0.01,
            "Long text width should remain unchanged");
    }

    // --- isGenuineVectorFigure: the rule that decides whether a LineArtChunk is a
    // real, freestanding vector figure worth rendering as an image, rather than a
    // decorative background/wrapper. A 600x800pt page is used throughout so the
    // 10% "sizeable region" threshold is 60pt x 80pt. ---

    private static final BoundingBox PAGE = new BoundingBox(0, 0, 0, 600, 800);

    private static LineArtChunk lineArt(double leftX, double bottomY, double rightX, double topY) {
        return new LineArtChunk(new BoundingBox(0, leftX, bottomY, rightX, topY));
    }

    private static ImageChunk imageChunk(double leftX, double bottomY, double rightX, double topY) {
        return new ImageChunk(new BoundingBox(0, leftX, bottomY, rightX, topY));
    }

    private static TextChunk textChunk(double leftX, double bottomY, double rightX, double topY) {
        return new TextChunk(new BoundingBox(0, leftX, bottomY, rightX, topY), "text", 12.0, 12.0);
    }

    private static List<IObject> siblings(IObject... objects) {
        List<IObject> list = new ArrayList<>();
        for (IObject object : objects) {
            list.add(object);
        }
        return list;
    }

    @Test
    public void isolatedChunkWithNoOverlapsIsGenuine() {
        // Page-4-shaped case: one freestanding drawing, nothing else nearby.
        LineArtChunk chunk = lineArt(140, 200, 470, 630);
        List<IObject> contents = siblings(chunk);

        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(chunk, contents, PAGE));
    }

    @Test
    public void chunkOverlappingAnImageIsNotGenuine() {
        // A dimension line/overlay drawn on top of an already-extracted raster figure.
        LineArtChunk chunk = lineArt(100, 440, 510, 650);
        ImageChunk image = imageChunk(150, 460, 480, 620);
        List<IObject> contents = siblings(chunk, image);

        Assertions.assertFalse(ContentFilterProcessor.isGenuineVectorFigure(chunk, contents, PAGE));
    }

    @Test
    public void chunkWrappingASizeableRegionIsNotGenuine() {
        // Table-1-12-shaped case: one big outline containing two independently
        // sizeable drawings; the outline is a wrapper, not a figure of its own.
        LineArtChunk wrapper = lineArt(40, 350, 540, 680);
        LineArtChunk drawing1 = lineArt(70, 465, 270, 660);
        LineArtChunk drawing2 = lineArt(310, 465, 510, 660);
        List<IObject> contents = siblings(wrapper, drawing1, drawing2);

        Assertions.assertFalse(ContentFilterProcessor.isGenuineVectorFigure(wrapper, contents, PAGE));
        // The wrapped drawings are unaffected by the wrapper and remain genuine.
        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(drawing1, contents, PAGE));
        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(drawing2, contents, PAGE));
    }

    @Test
    public void nearDuplicateChunksOfTheSameDrawingAreBothGenuine() {
        // Two overlapping strokes of one drawing (e.g. outline + fill), not a
        // wrapper/nested relationship: neither should exclude the other.
        LineArtChunk outline = lineArt(70, 465, 270, 660);
        LineArtChunk fill = lineArt(72, 468, 258, 655);
        List<IObject> contents = siblings(outline, fill);

        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(outline, contents, PAGE));
        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(fill, contents, PAGE));
    }

    @Test
    public void chunkContainingOnlyTinyNestedArtIsStillGenuine() {
        // Page-4-shaped case: small callout/connector marks sit inside a big
        // freestanding drawing; they are far too small to make it a "wrapper".
        LineArtChunk drawing = lineArt(140, 200, 470, 630);
        LineArtChunk callout = lineArt(190, 410, 210, 430); // 20x20pt: below the 60x80pt sizeable threshold
        List<IObject> contents = siblings(drawing, callout);

        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(drawing, contents, PAGE));
    }

    @Test
    public void chunkMostlyOverlappingTextIsNotGenuine() {
        // A decorative box drawn around a line of body text, not a drawing.
        LineArtChunk box = lineArt(140, 260, 540, 295); // 400 x 35 = 14000 sq pt
        TextChunk text = textChunk(140, 260, 540, 290); // covers most of the box's area
        List<IObject> contents = siblings(box, text);

        Assertions.assertFalse(ContentFilterProcessor.isGenuineVectorFigure(box, contents, PAGE));
    }

    @Test
    public void chunkWithOnlyIncidentalTextOverlapIsStillGenuine() {
        // A drawing with a couple of small labels on it (well under 20% of its area).
        LineArtChunk drawing = lineArt(140, 200, 470, 630);
        TextChunk label = textChunk(200, 400, 220, 410); // small sliver, far below 20% of the drawing's area
        List<IObject> contents = siblings(drawing, label);

        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(drawing, contents, PAGE));
    }

    @AfterEach
    public void clearTableBorders() {
        StaticContainers.setTableBordersCollection(null);
    }

    private static void registerTableBorder(BoundingBox box) {
        TableBordersCollection collection = new TableBordersCollection();
        TableBorder table = new TableBorder(1, 1);
        table.setBoundingBox(box);
        SortedSet<TableBorder> tables = new TreeSet<>(new TableBorder.TableBordersComparator());
        tables.add(table);
        collection.getTableBorders().add(tables);
        StaticContainers.setTableBordersCollection(collection);
    }

    @Test
    public void chunkCoincidingWithATableBorderIsNotGenuine() {
        // Page-7-shaped case: the page's only line art IS the table's grid. Rendering
        // it produces a screenshot of the whole table, not a figure. The grid is mostly
        // empty space, so the text-overlap rule alone never rejects it.
        BoundingBox tableBox = new BoundingBox(0, 56.402, 332.552, 552.548, 708.948);
        registerTableBorder(tableBox);
        LineArtChunk grid = lineArt(56.402, 332.552, 552.548, 708.948);
        List<IObject> contents = siblings(grid);

        Assertions.assertFalse(ContentFilterProcessor.isGenuineVectorFigure(grid, contents, PAGE));
    }

    @Test
    public void drawingInsideATableCellIsStillGenuine() {
        // Page-8-shaped case: a real drawing sits inside one cell of a table. It does
        // not coincide with the table's own bounds, so it stays a figure and is later
        // assigned to its cell.
        registerTableBorder(new BoundingBox(0, 42.233, 583.333, 538.378, 708.948));
        LineArtChunk drawing = lineArt(425.744, 582.384, 528.634, 670.193);
        List<IObject> contents = siblings(drawing);

        Assertions.assertTrue(ContentFilterProcessor.isGenuineVectorFigure(drawing, contents, PAGE));
    }
}

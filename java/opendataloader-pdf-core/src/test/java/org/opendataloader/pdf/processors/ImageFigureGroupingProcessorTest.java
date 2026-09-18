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
import org.verapdf.wcag.algorithms.entities.ObjectKey;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageFigureGroupingProcessorTest {

    private static final double DELTA = 0.0001;

    private static ImageChunk imageChunk(double leftX, double bottomY, double rightX, double topY) {
        return new ImageChunk(new BoundingBox(0, leftX, bottomY, rightX, topY));
    }

    /** An image backed by an embedded XObject, as extracted from a real PDF. */
    private static ImageChunk embeddedImage(double leftX, double bottomY, double rightX, double topY) {
        ImageChunk chunk = imageChunk(leftX, bottomY, rightX, topY);
        chunk.getStreamInfos().add(new StreamInfo(0, "Im0", new ObjectKey(1, 0)));
        return chunk;
    }

    private static List<List<IObject>> onePage(IObject... contents) {
        List<IObject> pageContents = new ArrayList<>();
        for (IObject content : contents) {
            pageContents.add(content);
        }
        List<List<IObject>> document = new ArrayList<>();
        document.add(pageContents);
        return document;
    }

    @Test
    public void testAdjacentTilesMergeIntoOnePaddedBox() {
        ImageChunk tileA = imageChunk(0, 0, 100, 100);
        ImageChunk tileB = imageChunk(110, 0, 210, 100);
        List<List<IObject>> document = onePage(tileA, tileB);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(1, pageContents.size());
        BoundingBox merged = pageContents.get(0).getBoundingBox();
        Assertions.assertEquals(-25.0, merged.getLeftX(), DELTA);
        Assertions.assertEquals(-25.0, merged.getBottomY(), DELTA);
        Assertions.assertEquals(235.0, merged.getRightX(), DELTA);
        Assertions.assertEquals(125.0, merged.getTopY(), DELTA);
    }

    @Test
    public void testDistantTilesStaySeparate() {
        ImageChunk tileA = imageChunk(0, 0, 100, 100);
        ImageChunk tileB = imageChunk(180, 0, 280, 100);
        List<List<IObject>> document = onePage(tileA, tileB);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(2, pageContents.size());
        Assertions.assertSame(tileA, pageContents.get(0));
        Assertions.assertSame(tileB, pageContents.get(1));
    }

    @Test
    public void testChainOfThreeTilesMergesTransitively() {
        ImageChunk tileA = imageChunk(0, 0, 100, 100);
        ImageChunk tileB = imageChunk(120, 0, 220, 100);
        ImageChunk tileC = imageChunk(240, 0, 340, 100);
        List<List<IObject>> document = onePage(tileA, tileB, tileC);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(1, pageContents.size());
        BoundingBox merged = pageContents.get(0).getBoundingBox();
        Assertions.assertEquals(-25.0, merged.getLeftX(), DELTA);
        Assertions.assertEquals(-25.0, merged.getBottomY(), DELTA);
        Assertions.assertEquals(365.0, merged.getRightX(), DELTA);
        Assertions.assertEquals(125.0, merged.getTopY(), DELTA);
    }

    @Test
    public void testSingleImageIsLeftUnchanged() {
        ImageChunk tile = imageChunk(0, 0, 100, 100);
        List<List<IObject>> document = onePage(tile);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(1, pageContents.size());
        Assertions.assertSame(tile, pageContents.get(0));
    }

    @Test
    public void testSubFourPointFragmentIsIgnoredByGrouping() {
        ImageChunk tile = imageChunk(0, 0, 100, 100);
        ImageChunk fragment = imageChunk(101, 0, 102.5, 1.5);
        List<List<IObject>> document = onePage(tile, fragment);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(2, pageContents.size());
        Assertions.assertSame(tile, pageContents.get(0));
        Assertions.assertSame(fragment, pageContents.get(1));
    }

    private static LineArtChunk lineArt(double leftX, double bottomY, double rightX, double topY) {
        return new LineArtChunk(new BoundingBox(0, leftX, bottomY, rightX, topY));
    }

    @Test
    public void testLoneCandidateIsConvertedToOnePaddedImage() {
        LineArtChunk chunk = lineArt(0, 0, 100, 100);
        List<IObject> objects = new ArrayList<>(Arrays.asList((IObject) chunk));

        ImageFigureGroupingProcessor.clusterAndReplaceWithImages(objects, Arrays.asList(0), null);

        Assertions.assertEquals(1, objects.size());
        Assertions.assertTrue(objects.get(0) instanceof ImageChunk);
        BoundingBox padded = objects.get(0).getBoundingBox();
        Assertions.assertEquals(-25.0, padded.getLeftX(), DELTA);
        Assertions.assertEquals(-25.0, padded.getBottomY(), DELTA);
        Assertions.assertEquals(125.0, padded.getRightX(), DELTA);
        Assertions.assertEquals(125.0, padded.getTopY(), DELTA);
    }

    @Test
    public void testPaddingOverlapBetweenTwoCandidatesIsResolvedAtTheMidpoint() {
        LineArtChunk left = lineArt(0, 0, 100, 100);
        LineArtChunk right = lineArt(140, 0, 240, 100);
        List<IObject> objects = new ArrayList<>(Arrays.asList((IObject) left, right));

        ImageFigureGroupingProcessor.clusterAndReplaceWithImages(objects, Arrays.asList(0, 1), null);

        Assertions.assertEquals(2, objects.size());
        BoundingBox leftBox = objects.get(0).getBoundingBox();
        BoundingBox rightBox = objects.get(1).getBoundingBox();
        Assertions.assertEquals(120.0, leftBox.getRightX(), DELTA);
        Assertions.assertEquals(120.0, rightBox.getLeftX(), DELTA);
        Assertions.assertTrue(leftBox.getRightX() <= rightBox.getLeftX());
        Assertions.assertEquals(-25.0, leftBox.getLeftX(), DELTA);
        Assertions.assertEquals(265.0, rightBox.getRightX(), DELTA);
    }

    @Test
    public void testOverlappingCandidatesStillMergeIntoOneImage() {
        LineArtChunk outline = lineArt(0, 0, 100, 100);
        LineArtChunk fill = lineArt(10, 10, 90, 90);
        List<IObject> objects = new ArrayList<>(Arrays.asList((IObject) outline, fill));

        ImageFigureGroupingProcessor.clusterAndReplaceWithImages(objects, Arrays.asList(0, 1), null);

        Assertions.assertEquals(1, objects.size());
        BoundingBox merged = objects.get(0).getBoundingBox();
        Assertions.assertEquals(-25.0, merged.getLeftX(), DELTA);
        Assertions.assertEquals(-25.0, merged.getBottomY(), DELTA);
        Assertions.assertEquals(125.0, merged.getRightX(), DELTA);
        Assertions.assertEquals(125.0, merged.getTopY(), DELTA);
    }

    @Test
    public void testRasterImagesNestedInListAreGrouped() {
        ImageChunk first = imageChunk(0, 0, 40, 40);
        ImageChunk second = imageChunk(45, 0, 85, 40);
        ListItem item = new ListItem(new BoundingBox(), null);
        item.getContents().add(first);
        item.getContents().add(second);
        PDFList list = new PDFList();
        list.add(item);
        List<List<IObject>> document = onePage(list);

        ImageFigureGroupingProcessor.groupFigures(document);

        Assertions.assertEquals(1, item.getContents().size());
        Assertions.assertTrue(item.getContents().get(0) instanceof ImageChunk);
        Assertions.assertEquals(-25.0, item.getContents().get(0).getLeftX(), DELTA);
    }

    @Test
    public void testVectorContentInTableCellBecomesClampedImage() {
        org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder table =
                new org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder(1, 1);
        org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell cell =
                new org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell(0, 0, 1, 1, 0L);
        cell.setBoundingBox(new BoundingBox(0, 10, 10, 50, 50));
        org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow row =
                new org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow(0, 1, 0L);
        row.getCells()[0] = cell;
        table.getRows()[0] = row;
        cell.getContents().add(lineArt(20, 20, 40, 40));
        List<List<IObject>> document = onePage(table);

        ImageFigureGroupingProcessor.groupFigures(document);

        Assertions.assertEquals(1, cell.getContents().size());
        Assertions.assertTrue(cell.getContents().get(0) instanceof ImageChunk);
        Assertions.assertEquals(10.0, cell.getContents().get(0).getLeftX(), DELTA);
        Assertions.assertEquals(50.0, cell.getContents().get(0).getRightX(), DELTA);
    }

    @Test
    public void testLoneImageMergesWithItsCalloutOverlays() {
        // Page-5-shaped case: callout circles painted over (and just above) a
        // photo in the content stream. They are absent from the embedded
        // XObject, so the image must be re-rendered from the page with them.
        ImageChunk photo = embeddedImage(127.3, 446.6, 245.9, 645.5);
        LineArtChunk calloutInside = lineArt(132.0, 596.6, 142.6, 607.3);
        LineArtChunk calloutAbove = lineArt(176.4, 647.9, 187.0, 658.5);
        List<List<IObject>> document = onePage(photo, calloutInside, calloutAbove);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(1, pageContents.size());
        Assertions.assertTrue(pageContents.get(0) instanceof ImageChunk);
        BoundingBox merged = pageContents.get(0).getBoundingBox();
        Assertions.assertEquals(102.3, merged.getLeftX(), DELTA);
        Assertions.assertEquals(421.6, merged.getBottomY(), DELTA);
        Assertions.assertEquals(270.9, merged.getRightX(), DELTA);
        Assertions.assertEquals(683.5, merged.getTopY(), DELTA);
        // A synthetic chunk with no XObject key: extraction falls to the page crop.
        Assertions.assertTrue(((ImageChunk) pageContents.get(0)).getStreamInfos().isEmpty());
    }

    @Test
    public void testSurroundingDecorationDoesNotEnlargeALoneImage() {
        // Page-1-shaped case: a warning icon in a NOTE box. The box border
        // touches the icon but is far bigger than it, so merging would crop
        // half the page instead of the icon.
        ImageChunk icon = embeddedImage(131.1, 220.8, 146.7, 234.4);
        LineArtChunk noteBoxBorder = lineArt(127.6, 148.2, 538.6, 218.4);
        List<List<IObject>> document = onePage(icon, noteBoxBorder);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(2, pageContents.size());
        Assertions.assertSame(icon, pageContents.get(0));
        Assertions.assertSame(noteBoxBorder, pageContents.get(1));
    }

    @Test
    public void testPaddingRetreatsFromNeighbouringText() {
        // Page-5-shaped case: body text 23pt above the figure and a legend 6pt
        // below it. Padding must stop at both rather than crop them in, while
        // the figure and its callout stay whole.
        ImageChunk photo = embeddedImage(127.3, 446.6, 245.9, 645.5);
        LineArtChunk calloutAbove = lineArt(176.4, 647.9, 187.0, 658.5);
        TextChunk bodyText = new TextChunk(
            new BoundingBox(0, 127.6, 668.8, 531.5, 692.8), "body", 12.0, 12.0);
        TextChunk legend = new TextChunk(
            new BoundingBox(0, 127.6, 369.5, 286.2, 440.6), "legend", 12.0, 12.0);
        List<List<IObject>> document = onePage(bodyText, photo, calloutAbove, legend);

        ImageFigureGroupingProcessor.groupFigures(document);

        List<IObject> pageContents = document.get(0);
        Assertions.assertEquals(3, pageContents.size());
        BoundingBox merged = pageContents.get(1).getBoundingBox();
        Assertions.assertEquals(668.8, merged.getTopY(), DELTA);
        Assertions.assertEquals(440.6, merged.getBottomY(), DELTA);
        // Sideways there is nothing to retreat from, so the full padding stands.
        Assertions.assertEquals(102.3, merged.getLeftX(), DELTA);
        Assertions.assertEquals(270.9, merged.getRightX(), DELTA);
    }
}

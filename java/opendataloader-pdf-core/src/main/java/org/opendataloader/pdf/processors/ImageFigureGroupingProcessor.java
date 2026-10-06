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

import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Merges adjacent {@link ImageChunk} tiles that together make up a single
 * printed figure into one chunk covering the whole figure. Some PDFs store a
 * figure as many small image tiles laid out in a grid, with dimension lines,
 * hatching or callouts drawn as vector content on top. Extracting each tile
 * separately produces meaningless slices and loses the vector overlays; a
 * single merged chunk with no XObject key instead falls through to the
 * page-render-and-crop extraction path, which captures the whole figure as
 * it looks in the PDF.
 *
 * <p>The same reasoning applies to a lone raster image that the page paints
 * callout numbers, arrows or dimension marks over: those overlays live in the
 * content stream, not in the embedded XObject, so such an image is merged with
 * its overlays and re-rendered too — see {@link #isOverlayOnEmbeddedRaster}.
 *
 * <p>Also provides the same cluster-and-merge mechanics to
 * {@code ContentFilterProcessor}, which uses them to turn freestanding
 * vector-drawn figures ({@link LineArtChunk}s with no raster image behind
 * them) into images — see {@link #clusterAndReplaceWithImages}.
 */
public class ImageFigureGroupingProcessor {

    /**
     * Maximum gap, in PDF points, between two {@link ImageChunk} bounding
     * boxes for them to be treated as tiles of the same figure. Tiles
     * belonging to the same split figure are laid out with small mechanical
     * gaps between them; this bridges those gaps (transitively, for a chain
     * of tiles) without merging genuinely separate figures on the same page.
     */
    private static final double GROUPING_GAP_PT = 30.0;

    /**
     * Padding, in PDF points, added on each side of a merged figure's union
     * bounding box. Recovers callout numbers, dimension lines and other
     * vector overlays that sit just outside the tile bounds.
     */
    private static final double GROUP_PADDING_PT = 25.0;

    private ImageFigureGroupingProcessor() {
    }

    /**
     * Groups adjacent image tiles into figure regions, per page.
     *
     * @param contents the document contents, organized by page
     */
    public static void groupFigures(List<List<IObject>> contents) {
        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            groupFiguresOnPage(contents.get(pageNumber), pageNumber);
        }
    }

    private static void groupFiguresOnPage(List<IObject> pageContents, int pageNumber) {
        if (pageContents == null) {
            return;
        }
        groupImagesInContainer(pageContents, DocumentProcessor.getPageBoundingBox(pageNumber), null);
    }

    /**
     * @param clampBoundingBox padding is clamped to this box, or unclamped if null
     * @param containerBox the enclosing node's own box when that node carries text
     *                     of its own that is not among {@code contents} — a list
     *                     item's label line, say. Padding retreats from it like
     *                     any other neighbour. Null when there is no such text.
     */
    private static void groupImagesInContainer(List<IObject> contents, BoundingBox clampBoundingBox,
                                                  BoundingBox containerBox) {
        if (contents == null) {
            return;
        }
        List<Integer> candidateIndices = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (content instanceof ImageChunk && ImagesUtils.isRenderableImage((ImageChunk) content)) {
                candidateIndices.add(i);
            } else if (content instanceof LineArtChunk && isOverlayOnEmbeddedRaster(contents, content)) {
                candidateIndices.add(i);
            }
        }
        if (candidateIndices.size() >= 2) {
            List<List<Integer>> groups = clusterByProximity(contents, candidateIndices, false);
            if (!groups.isEmpty()) {
                replaceGroupsWithMergedImageChunks(contents, groups, clampBoundingBox, false, containerBox);
            }
        }
        renderOverlaidImagesFromPage(contents);
        for (IObject content : contents) {
            processNestedContainer(content);
        }
    }

    /**
     * Re-renders from the page any embedded raster still standing on its own
     * that vector marks are painted across. Merging above already covers an
     * overlay small enough to belong to one image; this catches the rest —
     * typically a single {@link LineArtChunk} that veraPDF built out of marks
     * spanning several figures, where growing the crop to that chunk's bounds
     * would swallow the neighbouring figure. Re-rendering the image's own
     * bounds leaves the crop exact and brings the marks back.
     */
    private static void renderOverlaidImagesFromPage(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (content instanceof ImageChunk && hasEmbeddedRaster((ImageChunk) content)
                    && ImagesUtils.isRenderableImage((ImageChunk) content)
                    && isOverlaidByLineArt(contents, content.getBoundingBox())) {
                contents.set(i, new ImageChunk(new BoundingBox(content.getBoundingBox())));
            }
        }
    }

    private static boolean isOverlaidByLineArt(List<IObject> contents, BoundingBox imageBox) {
        for (IObject content : contents) {
            if (content instanceof LineArtChunk && intersects(imageBox, content.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    private static void processNestedContainer(IObject content) {
        if (content instanceof PDFList) {
            for (ListItem item : ((PDFList) content).getListItems()) {
                groupImagesInContainer(item.getContents(), null, item.getBoundingBox());
            }
        } else if (content instanceof TableBorder) {
            TableBorder table = (TableBorder) content;
            for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
                TableBorderRow row = table.getRow(rowNumber);
                for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                    TableBorderCell cell = row.getCell(colNumber);
                    if (cell != null && cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                        processTableCell(cell);
                    }
                }
            }
        } else if (content instanceof SemanticHeaderOrFooter) {
            groupImagesInContainer(((SemanticHeaderOrFooter) content).getContents(), null, null);
        }
    }

    private static void processTableCell(TableBorderCell cell) {
        List<IObject> contents = cell.getContents();
        List<Integer> lineArtIndices = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (content instanceof LineArtChunk && ImagesUtils.isRenderableSize(content.getWidth(), content.getHeight())) {
                lineArtIndices.add(i);
            }
        }
        if (!lineArtIndices.isEmpty()) {
            clusterAndReplaceWithImages(contents, lineArtIndices, cell.getBoundingBox());
        }
        groupImagesInContainer(contents, cell.getBoundingBox(), null);
    }

    /**
     * Clusters the given (already-vetted) candidate indices by proximity and
     * replaces each cluster — including single-chunk ones — with one merged
     * {@link ImageChunk}. Package-private: shared with
     * {@code ContentFilterProcessor}'s vector-figure detection, which needs to
     * run while a page's contents are still flat (before table/list
     * structuring), so it cannot reuse {@link #groupFiguresOnPage} directly.
     *
     * @param objects the content list to modify in place
     * @param candidateIndices indices into {@code objects} to cluster and
     *                         replace; every candidate ends up in some group,
     *                         unlike the raster tile grouping above
     * @param clampBoundingBox padding is clamped to this box (e.g. the page,
     *                         or a table cell), or left unclamped if null
     */
    static void clusterAndReplaceWithImages(List<IObject> objects, List<Integer> candidateIndices,
                                             BoundingBox clampBoundingBox) {
        if (candidateIndices.isEmpty()) {
            return;
        }
        List<List<Integer>> groups = clusterByProximity(objects, candidateIndices, true);
        // Unlike raster tiles (which are pre-existing content, unlikely to be packed
        // tightly enough for padding to cause new overlaps), vector figures are
        // frequently adjacent siblings (e.g. two drawings in neighboring table
        // cells), so padding-induced overlaps here are the common case, not the
        // exception — resolve them.
        replaceGroupsWithMergedImageChunks(objects, groups, clampBoundingBox, true, null);
    }

    /**
     * Clusters candidate tile indices into figure groups using union-find
     * over the gap-inflated intersection test, applied transitively.
     *
     * @param includeSingletons if false, single-member clusters are omitted
     *                          (the tile is left completely alone); if true,
     *                          every candidate is returned in some group
     * @return each group sorted ascending by index
     */
    private static List<List<Integer>> clusterByProximity(List<IObject> pageContents, List<Integer> candidateIndices,
                                                            boolean includeSingletons) {
        int n = candidateIndices.size();
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < n; i++) {
            BoundingBox inflatedI = inflate(pageContents.get(candidateIndices.get(i)).getBoundingBox());
            for (int j = i + 1; j < n; j++) {
                // Inflate only one side of the pair: testing the inflated box against the
                // other's raw bounds is equivalent to "the gap between them is at most
                // GROUPING_GAP_PT" — inflating both independently would double the effective
                // tolerance.
                BoundingBox rawJ = pageContents.get(candidateIndices.get(j)).getBoundingBox();
                if (intersects(inflatedI, rawJ)) {
                    union(parent, i, j);
                }
            }
        }

        Map<Integer, List<Integer>> groupsByRoot = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            groupsByRoot.computeIfAbsent(find(parent, i), root -> new ArrayList<>()).add(candidateIndices.get(i));
        }

        List<List<Integer>> groups = new ArrayList<>();
        for (List<Integer> group : groupsByRoot.values()) {
            if (group.size() > 1 || includeSingletons) {
                groups.add(group);
            }
        }
        return groups;
    }

    /**
     * Replaces each group of {@code objects} indices with one merged
     * {@link ImageChunk}, placed at the group's first (lowest) member index.
     * The remaining members are dropped from {@code objects}.
     */
    private static void replaceGroupsWithMergedImageChunks(List<IObject> objects, List<List<Integer>> groups,
                                                             BoundingBox clampBoundingBox, boolean resolveOverlaps,
                                                             BoundingBox containerBox) {
        Map<Integer, List<Integer>> groupsByFirstIndex = new LinkedHashMap<>();
        Set<Integer> absorbedIndices = new HashSet<>();
        for (List<Integer> group : groups) {
            groupsByFirstIndex.put(group.get(0), group);
            absorbedIndices.addAll(group.subList(1, group.size()));
        }

        Map<Integer, BoundingBox> paddedBoxesByFirstIndex = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : groupsByFirstIndex.entrySet()) {
            paddedBoxesByFirstIndex.put(entry.getKey(),
                unionAndPad(objects, entry.getValue(), clampBoundingBox, containerBox));
        }
        if (resolveOverlaps) {
            // Two groups on the same page can each be padded outward toward each other
            // (e.g. two adjacent table cells' figures) and end up overlapping even
            // though their own content never did; pull such pairs back apart.
            resolvePaddingOverlaps(paddedBoxesByFirstIndex);
        }

        List<IObject> mergedContents = new ArrayList<>(objects.size());
        for (int i = 0; i < objects.size(); i++) {
            BoundingBox paddedBox = paddedBoxesByFirstIndex.get(i);
            if (paddedBox != null) {
                mergedContents.add(new ImageChunk(paddedBox));
            } else if (!absorbedIndices.contains(i)) {
                mergedContents.add(objects.get(i));
            }
        }
        objects.clear();
        objects.addAll(mergedContents);
    }

    private static BoundingBox unionAndPad(List<IObject> objects, List<Integer> group, BoundingBox clampBoundingBox,
                                              BoundingBox containerBox) {
        BoundingBox union = new BoundingBox(objects.get(group.get(0)).getBoundingBox());
        for (int i = 1; i < group.size(); i++) {
            union.union(objects.get(group.get(i)).getBoundingBox());
        }
        BoundingBox raw = new BoundingBox(union);
        union.setLeftX(union.getLeftX() - GROUP_PADDING_PT);
        union.setBottomY(union.getBottomY() - GROUP_PADDING_PT);
        union.setRightX(union.getRightX() + GROUP_PADDING_PT);
        union.setTopY(union.getTopY() + GROUP_PADDING_PT);
        if (clampBoundingBox != null) {
            union.setLeftX(Math.max(union.getLeftX(), clampBoundingBox.getLeftX()));
            union.setBottomY(Math.max(union.getBottomY(), clampBoundingBox.getBottomY()));
            union.setRightX(Math.min(union.getRightX(), clampBoundingBox.getRightX()));
            union.setTopY(Math.min(union.getTopY(), clampBoundingBox.getTopY()));
        }
        clipPaddingToNeighbours(objects, group, raw, union);
        if (containerBox != null && intersects(union, containerBox) && !intersects(raw, containerBox)) {
            retreatFrom(union, raw, containerBox);
        }
        return union;
    }

    /**
     * Pulls the padding back off every neighbour the figure's own content does
     * not touch, so a crop does not swallow the paragraph above it or the
     * legend below it. Only the padding is given up: {@code raw}, the union of
     * the figure's own chunks, always survives.
     */
    private static void clipPaddingToNeighbours(List<IObject> objects, List<Integer> group,
                                                   BoundingBox raw, BoundingBox padded) {
        Set<Integer> members = new HashSet<>(group);
        for (int i = 0; i < objects.size(); i++) {
            if (members.contains(i)) {
                continue;
            }
            BoundingBox other = objects.get(i).getBoundingBox();
            if (other == null || !intersects(padded, other) || intersects(raw, other)) {
                continue;
            }
            retreatFrom(padded, raw, other);
        }
    }

    /**
     * Gives up padding on the one side that needs the least of it to clear
     * {@code other}. A side is only a candidate when {@code other} lies wholly
     * beyond {@code raw} on that side — a neighbour that is diagonal to the
     * figure, or that would need part of the figure itself to be cut away, is
     * left alone.
     */
    private static void retreatFrom(BoundingBox padded, BoundingBox raw, BoundingBox other) {
        double left = other.getRightX() <= raw.getLeftX()
            ? other.getRightX() - padded.getLeftX() : Double.MAX_VALUE;
        double right = other.getLeftX() >= raw.getRightX()
            ? padded.getRightX() - other.getLeftX() : Double.MAX_VALUE;
        double bottom = other.getTopY() <= raw.getBottomY()
            ? other.getTopY() - padded.getBottomY() : Double.MAX_VALUE;
        double top = other.getBottomY() >= raw.getTopY()
            ? padded.getTopY() - other.getBottomY() : Double.MAX_VALUE;
        double least = Math.min(Math.min(left, right), Math.min(bottom, top));
        if (least == Double.MAX_VALUE) {
            return;
        }
        if (least == left) {
            padded.setLeftX(other.getRightX());
        } else if (least == right) {
            padded.setRightX(other.getLeftX());
        } else if (least == bottom) {
            padded.setBottomY(other.getTopY());
        } else {
            padded.setTopY(other.getBottomY());
        }
    }

    /**
     * Pulls apart any pair of padded group boxes that ended up overlapping
     * purely because of {@link #GROUP_PADDING_PT} (their un-padded content
     * never overlapped), splitting the gap between them at the midpoint —
     * along whichever axis has the smaller overlap, since that is the axis
     * the two groups are actually adjacent on.
     */
    private static void resolvePaddingOverlaps(Map<Integer, BoundingBox> paddedBoxesByFirstIndex) {
        List<BoundingBox> boxes = new ArrayList<>(paddedBoxesByFirstIndex.values());
        for (int a = 0; a < boxes.size(); a++) {
            for (int b = a + 1; b < boxes.size(); b++) {
                separateIfOverlapping(boxes.get(a), boxes.get(b));
            }
        }
    }

    private static void separateIfOverlapping(BoundingBox boxA, BoundingBox boxB) {
        double overlapWidth = Math.min(boxA.getRightX(), boxB.getRightX()) - Math.max(boxA.getLeftX(), boxB.getLeftX());
        double overlapHeight = Math.min(boxA.getTopY(), boxB.getTopY()) - Math.max(boxA.getBottomY(), boxB.getBottomY());
        if (overlapWidth <= 0 || overlapHeight <= 0) {
            return;
        }
        if (overlapWidth <= overlapHeight) {
            double midX = (Math.max(boxA.getLeftX(), boxB.getLeftX()) + Math.min(boxA.getRightX(), boxB.getRightX())) / 2;
            BoundingBox left = boxA.getLeftX() <= boxB.getLeftX() ? boxA : boxB;
            BoundingBox right = left == boxA ? boxB : boxA;
            left.setRightX(Math.min(left.getRightX(), midX));
            right.setLeftX(Math.max(right.getLeftX(), midX));
        } else {
            double midY = (Math.max(boxA.getBottomY(), boxB.getBottomY()) + Math.min(boxA.getTopY(), boxB.getTopY())) / 2;
            BoundingBox bottom = boxA.getBottomY() <= boxB.getBottomY() ? boxA : boxB;
            BoundingBox top = bottom == boxA ? boxB : boxA;
            bottom.setTopY(Math.min(bottom.getTopY(), midY));
            top.setBottomY(Math.max(top.getBottomY(), midY));
        }
    }

    /**
     * True if {@code lineArt} sits on, or right at the edge of, one of the
     * renderable raster images in {@code contents}: a callout number, arrow,
     * magnifier or dimension mark that the page's content stream paints over
     * the photo rather than the photo itself containing it. Such an overlay is
     * absent from the embedded XObject, so the image it annotates has to be
     * re-rendered from the page — which is what merging it into the image's
     * group achieves.
     *
     * <p>The overlay must fit inside the image's gap-inflated bounds. That
     * keeps out surrounding decoration which merely happens to touch the image
     * — a NOTE box border around a warning icon, a hatching band beside a
     * figure — and which would otherwise drag the crop out over half the page.
     */
    private static boolean isOverlayOnEmbeddedRaster(List<IObject> contents, IObject lineArt) {
        BoundingBox box = lineArt.getBoundingBox();
        for (IObject content : contents) {
            if (content instanceof ImageChunk && ImagesUtils.isRenderableImage((ImageChunk) content)
                    && hasEmbeddedRaster((ImageChunk) content)
                    && contains(inflate(content.getBoundingBox()), box)) {
                return true;
            }
        }
        return false;
    }

    /**
     * True if the chunk carries an embedded XObject, i.e. {@code ImagesUtils}
     * will extract the stored raster rather than re-rendering the page region.
     * A chunk without one (a merged tile group, or a converted vector figure)
     * is already rendered from the page, so whatever is painted over it is
     * captured anyway and needs no merging.
     */
    private static boolean hasEmbeddedRaster(ImageChunk chunk) {
        return chunk.getStreamInfos() != null && !chunk.getStreamInfos().isEmpty()
            && chunk.getStreamInfos().get(0).getXImageObjectKey() != null;
    }

    private static boolean contains(BoundingBox outer, BoundingBox inner) {
        return outer.getLeftX() <= inner.getLeftX() && inner.getRightX() <= outer.getRightX()
            && outer.getBottomY() <= inner.getBottomY() && inner.getTopY() <= outer.getTopY();
    }

    private static BoundingBox inflate(BoundingBox box) {
        BoundingBox inflated = new BoundingBox(box);
        inflated.setLeftX(inflated.getLeftX() - GROUPING_GAP_PT);
        inflated.setBottomY(inflated.getBottomY() - GROUPING_GAP_PT);
        inflated.setRightX(inflated.getRightX() + GROUPING_GAP_PT);
        inflated.setTopY(inflated.getTopY() + GROUPING_GAP_PT);
        return inflated;
    }

    private static boolean intersects(BoundingBox a, BoundingBox b) {
        return a.getLeftX() <= b.getRightX() && b.getLeftX() <= a.getRightX()
            && a.getBottomY() <= b.getTopY() && b.getBottomY() <= a.getTopY();
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    private static void union(int[] parent, int a, int b) {
        int rootA = find(parent, a);
        int rootB = find(parent, b);
        if (rootA != rootB) {
            parent[rootA] = rootB;
        }
    }
}

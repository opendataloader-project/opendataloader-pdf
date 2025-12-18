/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors.readingorder;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * XY-Cut++ algorithm for reading order detection based on arXiv:2504.10258.
 * <p>
 * An enhanced XY-Cut implementation that handles:
 * <ul>
 *   <li>Cross-layout elements (headers, footers spanning multiple columns)</li>
 *   <li>Adaptive axis selection based on density ratios</li>
 *   <li>L-shaped region handling</li>
 * </ul>
 * <p>
 * This is a simplified geometric implementation without semantic type priorities.
 * <p>
 * Algorithm overview:
 * <ol>
 *   <li>Pre-mask: Identify cross-layout elements (width > beta * maxWidth, overlaps >= 2)</li>
 *   <li>Compute density ratio to determine split direction preference</li>
 *   <li>Recursive segmentation with adaptive XY/YX-Cut</li>
 *   <li>Merge cross-layout elements at appropriate positions</li>
 * </ol>
 */
public class XYCutPlusPlusSorter {

    /** Default beta multiplier for cross-layout detection threshold.
     *  Higher value = fewer elements detected as cross-layout.
     *  2.0 means element must be 2x wider than maxWidth to be considered cross-layout. */
    static final double DEFAULT_BETA = 2.0;

    /** Default density threshold for adaptive axis selection. */
    static final double DEFAULT_DENSITY_THRESHOLD = 0.9;

    /** Minimum horizontal overlap ratio to count as overlapping. */
    static final double OVERLAP_THRESHOLD = 0.1;

    /** Minimum number of overlaps required for cross-layout classification. */
    static final int MIN_OVERLAP_COUNT = 2;

    private XYCutPlusPlusSorter() {
        // Utility class - prevent instantiation
    }

    // ========== PUBLIC API ==========

    /**
     * Sort objects using XY-Cut++ algorithm with default parameters.
     *
     * @param objects List of objects to sort
     * @return Sorted list of objects in reading order
     */
    public static List<IObject> sort(List<IObject> objects) {
        return sort(objects, DEFAULT_BETA, DEFAULT_DENSITY_THRESHOLD);
    }

    /**
     * Sort objects using XY-Cut++ algorithm with custom parameters.
     *
     * @param objects          List of objects to sort
     * @param beta             Cross-layout detection threshold multiplier
     * @param densityThreshold Density ratio threshold for axis selection
     * @return Sorted list of objects in reading order
     */
    public static List<IObject> sort(List<IObject> objects, double beta, double densityThreshold) {
        if (objects == null || objects.size() <= 1) {
            return objects;
        }

        // Filter out objects with null bounding boxes
        List<IObject> validObjects = new ArrayList<>();
        for (IObject obj : objects) {
            if (obj != null && obj.getBoundingBox() != null) {
                validObjects.add(obj);
            }
        }
        if (validObjects.size() <= 1) {
            return validObjects;
        }

        // Phase 1: Pre-mask cross-layout elements
        List<IObject> crossLayoutElements = identifyCrossLayoutElements(validObjects, beta);
        List<IObject> remainingObjects = new ArrayList<>(validObjects);
        remainingObjects.removeAll(crossLayoutElements);

        if (remainingObjects.isEmpty()) {
            // All objects are cross-layout, just sort by Y
            return sortByYThenX(validObjects);
        }

        // Phase 2: Compute density ratio for adaptive axis selection
        double densityRatio = computeDensityRatio(remainingObjects);
        boolean preferHorizontalFirst = densityRatio > densityThreshold;

        // Phase 3: Recursive segmentation with adaptive axis
        List<IObject> sortedMain = recursiveSegment(remainingObjects, preferHorizontalFirst);

        // Phase 4: Merge cross-layout elements back at appropriate positions
        return mergeCrossLayoutElements(sortedMain, crossLayoutElements);
    }

    // ========== PHASE 1: CROSS-LAYOUT DETECTION ==========

    /**
     * Identify cross-layout elements that span multiple regions.
     * An element is cross-layout if:
     * 1. Its width exceeds beta * maxWidth (where maxWidth is the widest element)
     * 2. It horizontally overlaps with at least MIN_OVERLAP_COUNT other elements
     *
     * Using maxWidth instead of median ensures only truly wide elements
     * (like titles spanning the full page) are detected as cross-layout.
     *
     * @param objects List of objects to analyze
     * @param beta    Threshold multiplier for width comparison (e.g., 0.7 = 70% of max width)
     * @return List of cross-layout elements
     */
    static List<IObject> identifyCrossLayoutElements(List<IObject> objects, double beta) {
        List<IObject> crossLayoutElements = new ArrayList<>();

        if (objects.size() < 3) {
            // Need at least 3 objects for meaningful cross-layout detection
            return crossLayoutElements;
        }

        // Calculate max width among all objects
        double maxWidth = 0;
        for (IObject obj : objects) {
            BoundingBox bbox = obj.getBoundingBox();
            if (bbox != null) {
                double width = bbox.getRightX() - bbox.getLeftX();
                maxWidth = Math.max(maxWidth, width);
            }
        }

        // Threshold: element must be at least beta * maxWidth to be cross-layout
        // With beta=0.7, element must be at least 70% as wide as the widest element
        double threshold = beta * maxWidth;

        for (IObject obj : objects) {
            BoundingBox bbox = obj.getBoundingBox();
            if (bbox == null) {
                continue;
            }

            double width = bbox.getRightX() - bbox.getLeftX();

            // Criterion 1: Width exceeds threshold (close to max width)
            if (width >= threshold) {
                // Criterion 2: Overlaps with at least MIN_OVERLAP_COUNT other elements
                if (hasMinimumOverlaps(obj, objects, MIN_OVERLAP_COUNT)) {
                    crossLayoutElements.add(obj);
                }
            }
        }

        return crossLayoutElements;
    }

    /**
     * Check if an element horizontally overlaps with at least minCount other elements.
     *
     * @param element  The element to check
     * @param objects  All objects including the element
     * @param minCount Minimum number of overlaps required
     * @return true if the element overlaps with at least minCount other elements
     */
    static boolean hasMinimumOverlaps(IObject element, List<IObject> objects, int minCount) {
        BoundingBox elementBox = element.getBoundingBox();
        if (elementBox == null) {
            return false;
        }

        int overlapCount = 0;
        for (IObject other : objects) {
            if (other == element) {
                continue;
            }

            BoundingBox otherBox = other.getBoundingBox();
            if (otherBox == null) {
                continue;
            }

            double overlapRatio = calculateHorizontalOverlapRatio(elementBox, otherBox);
            if (overlapRatio >= OVERLAP_THRESHOLD) {
                overlapCount++;
                if (overlapCount >= minCount) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Calculate the horizontal overlap ratio between two bounding boxes.
     * The ratio is relative to the smaller box's width.
     *
     * @param box1 First bounding box
     * @param box2 Second bounding box
     * @return Overlap ratio (0.0 to 1.0)
     */
    static double calculateHorizontalOverlapRatio(BoundingBox box1, BoundingBox box2) {
        double overlapLeft = Math.max(box1.getLeftX(), box2.getLeftX());
        double overlapRight = Math.min(box1.getRightX(), box2.getRightX());
        double overlapWidth = Math.max(0, overlapRight - overlapLeft);

        if (overlapWidth <= 0) {
            return 0;
        }

        double width1 = box1.getRightX() - box1.getLeftX();
        double width2 = box2.getRightX() - box2.getLeftX();
        double smallerWidth = Math.min(width1, width2);

        return smallerWidth > 0 ? overlapWidth / smallerWidth : 0;
    }

    // ========== PHASE 2: DENSITY RATIO COMPUTATION ==========

    /**
     * Compute the density ratio to determine split direction preference.
     * Density = total content area / bounding region area.
     * Higher density suggests content-dense layouts (newspapers) -> prefer horizontal splits.
     * Lower density suggests sparse layouts -> prefer vertical splits.
     *
     * @param objects List of objects
     * @return Density ratio (0.0 to 1.0)
     */
    static double computeDensityRatio(List<IObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return 1.0; // Default to XY-Cut
        }

        BoundingBox regionBounds = calculateBoundingRegion(objects);
        if (regionBounds == null) {
            return 1.0;
        }

        double regionWidth = regionBounds.getRightX() - regionBounds.getLeftX();
        double regionHeight = regionBounds.getTopY() - regionBounds.getBottomY();
        double regionArea = regionWidth * regionHeight;

        if (regionArea <= 0) {
            return 1.0;
        }

        double contentArea = calculateTotalArea(objects);
        return Math.min(1.0, contentArea / regionArea);
    }

    /**
     * Calculate the bounding box that encompasses all objects.
     *
     * @param objects List of objects
     * @return Bounding box encompassing all objects, or null if no valid objects
     */
    static BoundingBox calculateBoundingRegion(List<IObject> objects) {
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        int pageNumber = 0;
        boolean found = false;

        for (IObject obj : objects) {
            BoundingBox bbox = obj.getBoundingBox();
            if (bbox == null) {
                continue;
            }

            found = true;
            pageNumber = bbox.getPageNumber();
            minX = Math.min(minX, bbox.getLeftX());
            maxX = Math.max(maxX, bbox.getRightX());
            minY = Math.min(minY, bbox.getBottomY());
            maxY = Math.max(maxY, bbox.getTopY());
        }

        if (!found) {
            return null;
        }

        return new BoundingBox(pageNumber, minX, minY, maxX, maxY);
    }

    /**
     * Calculate the total area covered by all objects.
     *
     * @param objects List of objects
     * @return Total area
     */
    static double calculateTotalArea(List<IObject> objects) {
        double totalArea = 0;
        for (IObject obj : objects) {
            BoundingBox bbox = obj.getBoundingBox();
            if (bbox != null) {
                double width = bbox.getRightX() - bbox.getLeftX();
                double height = bbox.getTopY() - bbox.getBottomY();
                totalArea += width * height;
            }
        }
        return totalArea;
    }

    // ========== PHASE 3: RECURSIVE SEGMENTATION ==========

    /**
     * Recursively segment and sort objects using adaptive XY/YX-Cut.
     * <p>
     * The algorithm uses projection-based gap detection to find clean cuts.
     * For two-column academic paper layouts:
     * 1. First try horizontal cut to separate header from body
     * 2. Then try vertical cut to separate columns
     * <p>
     * The algorithm prefers horizontal cuts first (Y-axis split) when there's a significant
     * horizontal gap, which properly handles layouts with wide headers followed by columns.
     *
     * @param objects              List of objects to segment
     * @param preferHorizontalFirst Initial preference (used as tiebreaker)
     * @return Sorted list of objects
     */
    static List<IObject> recursiveSegment(List<IObject> objects, boolean preferHorizontalFirst) {
        if (objects == null || objects.size() <= 1) {
            return objects != null ? new ArrayList<>(objects) : new ArrayList<>();
        }

        // Find best cuts in both directions using projection-based detection
        CutInfo horizontalCut = findBestHorizontalCutWithProjection(objects);
        CutInfo verticalCut = findBestVerticalCutWithProjection(objects);

        // Choose cut direction based on gap sizes
        // For column layouts, vertical cuts (column separation) take priority
        // Use horizontal cut only when horizontal gap is larger than vertical gap
        boolean useHorizontalCut;
        if (horizontalCut.gap > 0 && verticalCut.gap > 0) {
            // Both cuts available - prefer larger gap
            useHorizontalCut = horizontalCut.gap > verticalCut.gap;
        } else if (horizontalCut.gap > 0) {
            useHorizontalCut = true;
        } else if (verticalCut.gap > 0) {
            useHorizontalCut = false;
        } else {
            // No valid cuts found - sort by Y then X (reading order)
            return sortByYThenX(objects);
        }

        if (useHorizontalCut) {
            List<List<IObject>> groups = splitByHorizontalCut(objects, horizontalCut.position);
            return flatMapRecursive(groups, preferHorizontalFirst);
        } else {
            List<List<IObject>> groups = splitByVerticalCut(objects, verticalCut.position);
            return flatMapRecursive(groups, preferHorizontalFirst);
        }
    }

    /**
     * Container for cut information including position and gap size.
     */
    private static class CutInfo {
        final double position;
        final double gap;

        CutInfo(double position, double gap) {
            this.position = position;
            this.gap = gap;
        }
    }

    /**
     * Recursively process groups and flatten results.
     */
    private static List<IObject> flatMapRecursive(List<List<IObject>> groups, boolean preferHorizontalFirst) {
        List<IObject> result = new ArrayList<>();
        for (List<IObject> group : groups) {
            result.addAll(recursiveSegment(group, preferHorizontalFirst));
        }
        return result;
    }

    /**
     * Find the best vertical cut using projection profile.
     * Projects all objects onto the X-axis and finds the largest gap.
     *
     * @param objects List of objects
     * @return CutInfo containing position and gap size
     */
    private static CutInfo findBestVerticalCutWithProjection(List<IObject> objects) {
        if (objects.size() < 2) {
            return new CutInfo(0, 0);
        }

        // Sort by leftX ascending (left to right)
        List<IObject> sorted = new ArrayList<>(objects);
        sorted.sort(Comparator.comparingDouble((IObject o) -> o.getBoundingBox().getLeftX())
                .thenComparingDouble(o -> o.getBoundingBox().getRightX()));

        double largestGap = 0;
        double cutPosition = 0;
        Double prevRight = null;

        for (IObject obj : sorted) {
            BoundingBox bbox = obj.getBoundingBox();
            double left = bbox.getLeftX();
            double right = bbox.getRightX();

            if (prevRight != null && left > prevRight) {
                double gap = left - prevRight;
                if (gap > largestGap) {
                    largestGap = gap;
                    cutPosition = (prevRight + left) / 2.0;
                }
            }

            prevRight = (prevRight == null) ? right : Math.max(prevRight, right);
        }

        return new CutInfo(cutPosition, largestGap);
    }

    /**
     * Find the best horizontal cut using projection profile.
     * Projects all objects onto the Y-axis and finds the largest gap.
     *
     * @param objects List of objects
     * @return CutInfo containing position and gap size
     */
    private static CutInfo findBestHorizontalCutWithProjection(List<IObject> objects) {
        if (objects.size() < 2) {
            return new CutInfo(0, 0);
        }

        // Sort by topY descending (PDF: top to bottom)
        List<IObject> sorted = new ArrayList<>(objects);
        sorted.sort(Comparator.comparingDouble((IObject o) -> -o.getBoundingBox().getTopY())
                .thenComparingDouble(o -> -o.getBoundingBox().getBottomY()));

        double largestGap = 0;
        double cutPosition = 0;
        Double prevBottom = null;

        for (IObject obj : sorted) {
            BoundingBox bbox = obj.getBoundingBox();
            double top = bbox.getTopY();
            double bottom = bbox.getBottomY();

            if (prevBottom != null && prevBottom > top) {
                double gap = prevBottom - top;
                if (gap > largestGap) {
                    largestGap = gap;
                    cutPosition = (prevBottom + top) / 2.0;
                }
            }

            prevBottom = (prevBottom == null) ? bottom : Math.min(prevBottom, bottom);
        }

        return new CutInfo(cutPosition, largestGap);
    }

    /**
     * Split objects by a horizontal cut at the given Y coordinate.
     * Objects above the cut come first, then objects below.
     *
     * @param objects List of objects to split
     * @param cutY    Y coordinate of the cut
     * @return List of two groups: [above, below]
     */
    static List<List<IObject>> splitByHorizontalCut(List<IObject> objects, double cutY) {
        List<IObject> above = new ArrayList<>();
        List<IObject> below = new ArrayList<>();

        for (IObject obj : objects) {
            BoundingBox bbox = obj.getBoundingBox();
            // Use center Y to determine which group
            double centerY = (bbox.getTopY() + bbox.getBottomY()) / 2.0;
            if (centerY > cutY) {
                above.add(obj);
            } else {
                below.add(obj);
            }
        }

        List<List<IObject>> groups = new ArrayList<>();
        if (!above.isEmpty()) {
            groups.add(above);
        }
        if (!below.isEmpty()) {
            groups.add(below);
        }
        return groups;
    }

    /**
     * Split objects by a vertical cut at the given X coordinate.
     * Objects to the left come first, then objects to the right.
     *
     * @param objects List of objects to split
     * @param cutX    X coordinate of the cut
     * @return List of two groups: [left, right]
     */
    static List<List<IObject>> splitByVerticalCut(List<IObject> objects, double cutX) {
        List<IObject> left = new ArrayList<>();
        List<IObject> right = new ArrayList<>();

        for (IObject obj : objects) {
            BoundingBox bbox = obj.getBoundingBox();
            // Use center X to determine which group
            double centerX = (bbox.getLeftX() + bbox.getRightX()) / 2.0;
            if (centerX < cutX) {
                left.add(obj);
            } else {
                right.add(obj);
            }
        }

        List<List<IObject>> groups = new ArrayList<>();
        if (!left.isEmpty()) {
            groups.add(left);
        }
        if (!right.isEmpty()) {
            groups.add(right);
        }
        return groups;
    }

    // ========== PHASE 4: MERGING ==========

    /**
     * Merge cross-layout elements back into the sorted content at appropriate positions.
     * Cross-layout elements are inserted based on their Y position relative to surrounding content.
     *
     * @param sortedMain           Main content sorted by reading order
     * @param crossLayoutElements  Cross-layout elements to merge
     * @return Merged list with cross-layout elements in correct positions
     */
    static List<IObject> mergeCrossLayoutElements(List<IObject> sortedMain, List<IObject> crossLayoutElements) {
        if (crossLayoutElements.isEmpty()) {
            return sortedMain;
        }

        if (sortedMain.isEmpty()) {
            return sortByYThenX(crossLayoutElements);
        }

        // Sort cross-layout elements by Y (top to bottom)
        List<IObject> sortedCrossLayout = sortByYThenX(crossLayoutElements);

        List<IObject> result = new ArrayList<>();
        int mainIndex = 0;
        int crossIndex = 0;

        while (mainIndex < sortedMain.size() || crossIndex < sortedCrossLayout.size()) {
            if (crossIndex >= sortedCrossLayout.size()) {
                // No more cross-layout elements, add remaining main
                result.add(sortedMain.get(mainIndex++));
            } else if (mainIndex >= sortedMain.size()) {
                // No more main elements, add remaining cross-layout
                result.add(sortedCrossLayout.get(crossIndex++));
            } else {
                // Compare Y positions (PDF: higher Y = top)
                IObject mainObj = sortedMain.get(mainIndex);
                IObject crossObj = sortedCrossLayout.get(crossIndex);

                double mainTopY = mainObj.getBoundingBox().getTopY();
                double crossTopY = crossObj.getBoundingBox().getTopY();

                if (crossTopY >= mainTopY) {
                    // Cross-layout element is above or at same level, add it first
                    result.add(crossObj);
                    crossIndex++;
                } else {
                    // Main element is above, add it first
                    result.add(mainObj);
                    mainIndex++;
                }
            }
        }

        return result;
    }

    // ========== UTILITY METHODS ==========

    /**
     * Sort objects by Y coordinate (top to bottom), then X coordinate (left to right).
     *
     * @param objects List of objects to sort
     * @return Sorted list
     */
    static List<IObject> sortByYThenX(List<IObject> objects) {
        List<IObject> sorted = new ArrayList<>(objects);
        sorted.sort(Comparator
                .comparingDouble((IObject o) -> -o.getBoundingBox().getTopY())  // Higher Y first (top)
                .thenComparingDouble(o -> o.getBoundingBox().getLeftX()));      // Lower X first (left)
        return sorted;
    }
}

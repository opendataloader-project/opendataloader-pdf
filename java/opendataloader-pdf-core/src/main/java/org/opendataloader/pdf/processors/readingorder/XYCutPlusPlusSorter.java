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
package org.opendataloader.pdf.processors.readingorder;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

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
     *  2.0 means element must be 2x wider than maxWidth to be considered cross-layout (effectively disabled). */
    static final double DEFAULT_BETA = 2.0;

    /** Default density threshold for adaptive axis selection. */
    static final double DEFAULT_DENSITY_THRESHOLD = 0.9;

    /** Minimum horizontal overlap ratio to count as overlapping. */
    static final double OVERLAP_THRESHOLD = 0.1;

    /** Minimum number of overlaps required for cross-layout classification. */
    static final int MIN_OVERLAP_COUNT = 2;

    /** Minimum gap size (in points) required to perform a cut.
     *  Prevents splitting on insignificant gaps (e.g., 1-pixel gaps). */
    static final double MIN_GAP_THRESHOLD = 5.0;

    /** Width ratio threshold for narrow outlier filtering.
     *  Elements narrower than this fraction of the region width are considered
     *  potential outliers that may bridge column gaps (e.g., page numbers, footnote markers). */
    static final double NARROW_ELEMENT_WIDTH_RATIO = 0.1;

    /**
     * A horizontal gap across two columns of text is a gap between the paragraphs of the columns, and not between
     * two parts of the page, if in each column it is at most this many font sizes of the text (#396).
     */
    static final double MAX_GAP_BETWEEN_PARAGRAPHS = 2.0;

    /** A line that is at most this many points thick is a rule (#396). A thicker line is a bar or a box. */
    static final double MAX_RULE_WIDTH = 2.0;

    /**
     * Rules that are nearer to each other than this many points draw one shape, for example a frame (#396). The box
     * of a line art and the box around its rules can also differ by this many points.
     */
    static final double RULE_TOUCH_TOLERANCE = 1.0;

    /** A line art of more lines than this is a drawing, not rules. This also limits the work to compare them. */
    static final int MAX_RULES_OF_LINE_ART = 16;

    /** Rounding tolerance for the edges of the gutter, which are computed from the cut position and gap. */
    private static final double GUTTER_EDGE_TOLERANCE = 1e-6;

    /** Sort by topY descending (PDF: top to bottom), then by bottomY descending. */
    private static final Comparator<IObject> TOP_TO_BOTTOM = Comparator
            .comparingDouble((IObject o) -> -o.getBoundingBox().getTopY())
            .thenComparingDouble(o -> -o.getBoundingBox().getBottomY());

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

        // Page headers come first and page footers last. They do not take part in the cuts of the page body: a
        // header or a footer across the gutter would hide the gutter between the columns (#396).
        List<IObject> headers = new ArrayList<>();
        List<IObject> footers = new ArrayList<>();
        List<IObject> body = new ArrayList<>();
        for (IObject obj : validObjects) {
            if (obj instanceof SemanticHeaderOrFooter) {
                boolean footer = ((SemanticHeaderOrFooter) obj).getSemanticType() == SemanticType.FOOTER;
                (footer ? footers : headers).add(obj);
            } else {
                body.add(obj);
            }
        }
        if (headers.isEmpty() && footers.isEmpty()) {
            return sortBody(validObjects, beta, densityThreshold);
        }
        List<IObject> result = sortByYThenX(headers);
        result.addAll(sortBody(body, beta, densityThreshold));
        result.addAll(sortByYThenX(footers));
        return result;
    }

    /** Sort the objects of the page body, which are not null and have a bounding box. */
    private static List<IObject> sortBody(List<IObject> validObjects, double beta, double densityThreshold) {
        if (validObjects.size() <= 1) {
            return new ArrayList<>(validObjects);
        }

        // Rules that the page paints one after the other are one line art, with one box around all of them. Each
        // rule takes part in the cuts alone, so that the box does not hide the gutter between columns (#396).
        Map<IObject, LineArtChunk> lineArtOfRule = new IdentityHashMap<>();
        List<IObject> objects = withRulesOfLineArt(validObjects, lineArtOfRule);

        // Phase 1: Pre-mask cross-layout elements
        List<IObject> crossLayoutElements = identifyCrossLayoutElements(objects, beta);
        List<IObject> remainingObjects = new ArrayList<>(objects);
        remainingObjects.removeAll(crossLayoutElements);

        if (remainingObjects.isEmpty()) {
            // All objects are cross-layout, just sort by Y
            return lineArtInPlaceOfItsRules(sortByYThenX(objects), lineArtOfRule);
        }

        // Phase 2: Compute density ratio for adaptive axis selection
        double densityRatio = computeDensityRatio(remainingObjects);
        boolean preferHorizontalFirst = densityRatio > densityThreshold;

        // Phase 3: Recursive segmentation with adaptive axis
        List<IObject> sortedMain = recursiveSegment(remainingObjects, preferHorizontalFirst);

        // Phase 4: Merge cross-layout elements back at appropriate positions
        return lineArtInPlaceOfItsRules(mergeCrossLayoutElements(sortedMain, crossLayoutElements), lineArtOfRule);
    }

    // ========== RULES OF LINE ART ==========

    /**
     * Keep in each line art of the page the lines that it paints (issue #396). {@link #sort} uses them to find the
     * rules of a line art. Call this before the lines are removed from the page contents.
     * <p>
     * Without the IDs of marked content, veraPDF makes one line art of the paths that a page paints one after the
     * other, with one box around all of them. Each line that such a path paints is also a {@link LineChunk} of the
     * page, with the same paint operator. A line art that already has lines, or that has more than
     * {@link #MAX_RULES_OF_LINE_ART} paints or lines, does not change.
     *
     * @param pageContents The contents of one page, with its lines
     */
    public static void keepLinesOfLineArt(List<IObject> pageContents) {
        Map<String, List<LineChunk>> linesOfPaint = new HashMap<>();
        for (IObject obj : pageContents) {
            if (obj instanceof LineChunk) {
                for (StreamInfo paint : ((LineChunk) obj).getStreamInfos()) {
                    linesOfPaint.computeIfAbsent(paintKey(paint), key -> new ArrayList<>()).add((LineChunk) obj);
                }
            }
        }
        if (linesOfPaint.isEmpty()) {
            return;
        }
        for (IObject obj : pageContents) {
            if (!(obj instanceof LineArtChunk)) {
                continue;
            }
            LineArtChunk lineArt = (LineArtChunk) obj;
            List<LineChunk> knownLines = lineArt.getLineChunks();
            if ((knownLines != null && !knownLines.isEmpty())
                    || lineArt.getStreamInfos().size() > MAX_RULES_OF_LINE_ART) {
                continue;
            }
            Set<LineChunk> lines = Collections.newSetFromMap(new IdentityHashMap<>());
            List<LineChunk> orderedLines = new ArrayList<>();
            for (StreamInfo paint : lineArt.getStreamInfos()) {
                for (LineChunk line : linesOfPaint.getOrDefault(paintKey(paint), Collections.emptyList())) {
                    if (lines.add(line)) {
                        orderedLines.add(line);
                    }
                }
            }
            if (orderedLines.size() >= 2 && orderedLines.size() <= MAX_RULES_OF_LINE_ART) {
                lineArt.setLineChunks(orderedLines);
            }
        }
    }

    private static String paintKey(StreamInfo paint) {
        return paint.getOperatorIndex() + "/" + paint.getXObjectName();
    }

    /**
     * Find the rules of a line art (issue #396): its lines, if each paint of the line art is a line, each line is a
     * rule, no two rules touch, and the box of the line art is the box around its rules. A rule is a horizontal or a
     * vertical line that is at most {@link #MAX_RULE_WIDTH} thick.
     *
     * @return The boxes of the rules; or an empty list if the line art is not two or more such rules, for example a
     *         drawing, a frame, or a bar. Then the box of the line art takes part in the cuts.
     */
    private static List<BoundingBox> findRulesOfLineArt(LineArtChunk lineArt) {
        List<LineChunk> lines = lineArt.getLineChunks();
        if (lines == null || lines.size() < 2 || lines.size() > MAX_RULES_OF_LINE_ART
                || lineArt.getStreamInfos().isEmpty()) {
            return Collections.emptyList();
        }
        for (StreamInfo paint : lineArt.getStreamInfos()) {
            if (!isPaintOfALine(paint, lines)) {
                return Collections.emptyList();
            }
        }
        List<BoundingBox> rules = new ArrayList<>();
        BoundingBox aroundRules = null;
        for (LineChunk line : lines) {
            if (!(line.isHorizontalLine() || line.isVerticalLine()) || line.getWidth() > MAX_RULE_WIDTH) {
                return Collections.emptyList();
            }
            for (BoundingBox rule : rules) {
                if (areNear(rule, line.getBoundingBox())) {
                    return Collections.emptyList();
                }
            }
            rules.add(new BoundingBox(line.getBoundingBox()));
            aroundRules = aroundRules == null ? new BoundingBox(line.getBoundingBox())
                    : aroundRules.union(line.getBoundingBox());
        }
        return isSameBox(aroundRules, lineArt.getBoundingBox()) ? rules : Collections.emptyList();
    }

    private static boolean isPaintOfALine(StreamInfo paint, List<LineChunk> lines) {
        String key = paintKey(paint);
        for (LineChunk line : lines) {
            for (StreamInfo linePaint : line.getStreamInfos()) {
                if (paintKey(linePaint).equals(key)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean areNear(BoundingBox first, BoundingBox second) {
        return first.getLeftX() <= second.getRightX() + RULE_TOUCH_TOLERANCE
                && second.getLeftX() <= first.getRightX() + RULE_TOUCH_TOLERANCE
                && first.getBottomY() <= second.getTopY() + RULE_TOUCH_TOLERANCE
                && second.getBottomY() <= first.getTopY() + RULE_TOUCH_TOLERANCE;
    }

    private static boolean isSameBox(BoundingBox first, BoundingBox second) {
        return Math.abs(first.getLeftX() - second.getLeftX()) <= RULE_TOUCH_TOLERANCE
                && Math.abs(first.getRightX() - second.getRightX()) <= RULE_TOUCH_TOLERANCE
                && Math.abs(first.getBottomY() - second.getBottomY()) <= RULE_TOUCH_TOLERANCE
                && Math.abs(first.getTopY() - second.getTopY()) <= RULE_TOUCH_TOLERANCE;
    }

    /**
     * Put a line art for each rule of a line art that is only rules. The map gets the line art of each of these
     * objects.
     */
    private static List<IObject> withRulesOfLineArt(List<IObject> objects, Map<IObject, LineArtChunk> lineArtOfRule) {
        List<IObject> result = new ArrayList<>();
        for (IObject obj : objects) {
            List<BoundingBox> rules = obj instanceof LineArtChunk
                    ? findRulesOfLineArt((LineArtChunk) obj) : Collections.emptyList();
            if (rules.isEmpty()) {
                result.add(obj);
                continue;
            }
            for (BoundingBox rule : rules) {
                LineArtChunk ruleObject = new LineArtChunk(rule);
                lineArtOfRule.put(ruleObject, (LineArtChunk) obj);
                result.add(ruleObject);
            }
        }
        return result;
    }

    /** Put each line art back at the place of its first rule, and remove the objects of its rules. */
    private static List<IObject> lineArtInPlaceOfItsRules(List<IObject> sorted,
                                                          Map<IObject, LineArtChunk> lineArtOfRule) {
        if (lineArtOfRule.isEmpty()) {
            return sorted;
        }
        List<IObject> result = new ArrayList<>();
        Set<LineArtChunk> placed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IObject obj : sorted) {
            LineArtChunk lineArt = lineArtOfRule.get(obj);
            if (lineArt == null) {
                result.add(obj);
            } else if (placed.add(lineArt)) {
                result.add(lineArt);
            }
        }
        return result;
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
                double width = bbox.getWidth();
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

            double width = bbox.getWidth();

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

        double width1 = box1.getWidth();
        double width2 = box2.getWidth();
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

        double regionArea = regionBounds.getArea();

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
        BoundingBox boundingBox = new BoundingBox();

        for (IObject obj : objects) {
            BoundingBox bbox = obj.getBoundingBox();
            boundingBox.union(bbox);
        }

        return boundingBox.isEmpty() ? null : boundingBox;
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
                totalArea += bbox.getArea();
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
        // Apply minimum gap threshold to avoid splitting on insignificant gaps
        boolean hasValidHorizontalCut = horizontalCut.gap >= MIN_GAP_THRESHOLD;
        boolean hasValidVerticalCut = verticalCut.gap >= MIN_GAP_THRESHOLD;

        boolean useHorizontalCut;
        if (hasValidHorizontalCut && hasValidVerticalCut) {
            // Both cuts available - prefer larger gap
            useHorizontalCut = horizontalCut.gap > verticalCut.gap
                    && !isGapBetweenParagraphsOfColumns(objects, verticalCut, horizontalCut);
            if (useHorizontalCut) {
                CutInfo gutter = findColumnGutter(objects);
                Optional<CutInfo> cutOutsideColumns = findHorizontalCutOutsideColumns(objects, horizontalCut, gutter);
                if (!cutOutsideColumns.isPresent()) {
                    return segmentColumnsFirst(objects, gutter, preferHorizontalFirst);
                }
                horizontalCut = cutOutsideColumns.get();
            }
        } else if (hasValidHorizontalCut) {
            useHorizontalCut = true;
            horizontalCut = findCutThatUncoversColumns(objects, horizontalCut);
        } else if (hasValidVerticalCut) {
            useHorizontalCut = false;
        } else {
            // No valid cuts found - sort by Y then X (reading order)
            return sortByYThenX(objects);
        }

        if (useHorizontalCut) {
            List<List<IObject>> groups = splitByHorizontalCut(objects, horizontalCut.position);
            // Safety: if split produced only one group, fall back to prevent infinite recursion
            if (groups.size() <= 1) {
                return sortByYThenX(objects);
            }
            return flatMapRecursive(groups, preferHorizontalFirst);
        } else {
            List<List<IObject>> groups = splitByVerticalCut(objects, verticalCut.position);
            // Safety: if split produced only one group, fall back to prevent infinite recursion
            if (groups.size() <= 1) {
                return sortByYThenX(objects);
            }
            return flatMapRecursive(groups, preferHorizontalFirst);
        }
    }

    /**
     * Check if the horizontal gap only goes across two columns of text between their paragraphs (issue #396). Then
     * the columns go on below the gap, and the columns are cut first, also if the gap is larger than the gutter.
     * <p>
     * Each side of the vertical cut has content directly above and below the gap: on each side, the gap between the
     * elements above and the elements below is at most {@link #MAX_GAP_BETWEEN_PARAGRAPHS} font sizes of the text
     * of that side at the gap: the smallest font size of the text elements of that side directly above and below the
     * gap. If one side has no text at the gap, or a larger gap, as above a section that starts below both columns, the
     * gap is not only between paragraphs.
     */
    private static boolean isGapBetweenParagraphsOfColumns(List<IObject> objects, CutInfo verticalCut,
                                                           CutInfo horizontalCut) {
        IObject[] lowestAbove = new IObject[2];
        IObject[] highestBelow = new IObject[2];
        for (IObject obj : objects) {
            int side = obj.getCenterX() < verticalCut.position ? 0 : 1;
            if (obj.getCenterY() > horizontalCut.position) {
                if (lowestAbove[side] == null || obj.getBottomY() < lowestAbove[side].getBottomY()) {
                    lowestAbove[side] = obj;
                }
            } else if (highestBelow[side] == null || obj.getTopY() > highestBelow[side].getTopY()) {
                highestBelow[side] = obj;
            }
        }
        for (int side = 0; side < 2; side++) {
            if (lowestAbove[side] == null || highestBelow[side] == null) {
                return false;
            }
            double fontSize = Double.POSITIVE_INFINITY;
            for (IObject obj : new IObject[] {lowestAbove[side], highestBelow[side]}) {
                if (obj instanceof SemanticTextNode) {
                    fontSize = Math.min(fontSize, ((SemanticTextNode) obj).getFontSize());
                }
            }
            double gap = lowestAbove[side].getBottomY() - highestBelow[side].getTopY();
            if (fontSize == Double.POSITIVE_INFINITY || gap > MAX_GAP_BETWEEN_PARAGRAPHS * fontSize) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find the gutter between two columns: the largest vertical gap between the elements that
     * are not narrow. A narrow element, for example a page number, can be in the gutter.
     */
    private static CutInfo findColumnGutter(List<IObject> objects) {
        List<IObject> wide = withoutNarrowElements(objects);
        return wide.size() >= 2 ? findVerticalCutByEdges(wide) : new CutInfo(0, 0);
    }

    /**
     * Find a horizontal cut that is not a gap inside one column of a two-column region (issue #294).
     *
     * @param objects       List of objects in the region
     * @param horizontalCut The largest horizontal gap
     * @param gutter        The gutter between the columns
     * @return horizontalCut; or, if it is inside the longer column, the largest gap above the end of
     *         the shorter column that is wider than the gutter; or empty if the columns must be cut first
     */
    private static Optional<CutInfo> findHorizontalCutOutsideColumns(List<IObject> objects, CutInfo horizontalCut,
                                                                     CutInfo gutter) {
        OptionalDouble shorterColumnEnd = findShorterColumnEndIfGapInsideColumn(objects, horizontalCut, gutter);
        if (!shorterColumnEnd.isPresent()) {
            return Optional.of(horizontalCut);
        }
        CutInfo sectionCut = largestGap(findHorizontalGaps(objects), shorterColumnEnd.getAsDouble());
        if (sectionCut.gap >= MIN_GAP_THRESHOLD && sectionCut.gap > gutter.gap) {
            return Optional.of(sectionCut);
        }
        return Optional.empty();
    }

    /**
     * Check if the horizontal gap is a gap inside the longer column of a two-column region.
     * <p>
     * The shorter column ends above the gap, and the longer column has content above the gap and
     * an element that is not narrow below it. The gap then goes across the region only because the
     * shorter column has ended.
     * Find the first gap of the longer column below the end of the shorter column that is at
     * least as wide as the gutter. If the longer column continues below the end of the shorter
     * column by at least that gap, the longer column continues there. If not, the columns end
     * almost at the same level, and the gap separates the content below them.
     *
     * @return The lowest bottom of the shorter column, or empty if the gap is not inside the longer column
     */
    private static OptionalDouble findShorterColumnEndIfGapInsideColumn(List<IObject> objects,
                                                                        CutInfo horizontalCut,
                                                                        CutInfo gutter) {
        if (gutter.gap < MIN_GAP_THRESHOLD) {
            return OptionalDouble.empty();
        }
        List<IObject> left = new ArrayList<>();
        List<IObject> right = new ArrayList<>();
        for (IObject obj : objects) {
            if (isLeftOfGutter(obj, gutter)) {
                left.add(obj);
            } else if (isRightOfGutter(obj, gutter)) {
                right.add(obj);
            }
        }
        if (left.isEmpty() || right.isEmpty()) {
            return OptionalDouble.empty();
        }
        BoundingBox leftRegion = calculateBoundingRegion(left);
        BoundingBox rightRegion = calculateBoundingRegion(right);
        boolean leftIsLonger = leftRegion.getBottomY() < rightRegion.getBottomY();
        List<IObject> longerColumn = leftIsLonger ? left : right;
        BoundingBox longerRegion = leftIsLonger ? leftRegion : rightRegion;
        double shorterColumnEnd = (leftIsLonger ? rightRegion : leftRegion).getBottomY();
        double cutY = horizontalCut.position;
        if (shorterColumnEnd < cutY || longerRegion.getTopY() < cutY || !hasWideElementBelow(longerColumn, cutY,
                calculateBoundingRegion(objects).getWidth() * NARROW_ELEMENT_WIDTH_RATIO)) {
            return OptionalDouble.empty();
        }
        for (CutInfo gap : findHorizontalGaps(longerColumn)) {
            if (gap.start() < shorterColumnEnd && gap.gap >= gutter.gap) {
                double continuationBelowShorterEnd = shorterColumnEnd - gap.end();
                return continuationBelowShorterEnd >= gap.gap
                        ? OptionalDouble.of(shorterColumnEnd) : OptionalDouble.empty();
            }
        }
        return OptionalDouble.empty();
    }

    /**
     * Check if an element below cutY is at least minWidth wide. A narrow element below the gap,
     * for example a page number, does not continue a column.
     */
    private static boolean hasWideElementBelow(List<IObject> objects, double cutY, double minWidth) {
        for (IObject obj : objects) {
            if (obj.getCenterY() <= cutY && obj.getBoundingBox().getWidth() >= minWidth) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the horizontal cut when no vertical cut is valid (issue #294).
     * <p>
     * Line art across the top or the bottom of the region, for example a rule below the page
     * header or a box around the page footer, can bridge the gutter of a two-column body. Then no
     * vertical cut is valid, and the largest gap can be a gap inside one column. If the elements
     * above the first gap, below the last gap, or both, are line art (and elements in its box)
     * that lies across the gutter of the other elements, cut at that gap first, so that the body
     * can be cut into its columns.
     *
     * @param objects    List of objects in the region
     * @param largestCut The largest horizontal gap
     * @return The first or the last gap of the region, or largestCut
     */
    private static CutInfo findCutThatUncoversColumns(List<IObject> objects, CutInfo largestCut) {
        List<CutInfo> validGaps = new ArrayList<>();
        for (CutInfo gap : findHorizontalGaps(objects)) {
            if (gap.gap >= MIN_GAP_THRESHOLD) {
                validGaps.add(gap);
            }
        }
        if (validGaps.size() < 2) {
            return largestCut;
        }
        CutInfo firstGap = validGaps.get(0);
        CutInfo lastGap = validGaps.get(validGaps.size() - 1);
        List<IObject> top = new ArrayList<>();
        List<IObject> bottom = new ArrayList<>();
        List<IObject> middle = new ArrayList<>();
        for (IObject obj : objects) {
            // Use center Y, as splitByHorizontalCut does
            if (obj.getCenterY() > firstGap.position) {
                top.add(obj);
            } else if (obj.getCenterY() <= lastGap.position) {
                bottom.add(obj);
            } else {
                middle.add(obj);
            }
        }
        if (liesAcrossGutterOfOthers(top, concat(middle, bottom))) {
            return firstGap;
        }
        if (liesAcrossGutterOfOthers(bottom, concat(top, middle))) {
            return lastGap;
        }
        return liesAcrossGutterOfOthers(concat(top, bottom), middle) ? firstGap : largestCut;
    }

    /**
     * Check if the band is line art and elements in its box, the others have a valid vertical cut,
     * and each line art of the band lies across that cut. An element in the box of the line art,
     * for example a page number at the side of a footer box, can be anywhere in the box.
     */
    private static boolean liesAcrossGutterOfOthers(List<IObject> band, List<IObject> others) {
        if (band.isEmpty() || others.size() < 2 || !isLineArtWithContents(band)) {
            return false;
        }
        CutInfo gutter = findBestVerticalCutWithProjection(others);
        if (gutter.gap < MIN_GAP_THRESHOLD) {
            return false;
        }
        for (IObject obj : band) {
            if (obj instanceof LineArtChunk
                    && (obj.getRightX() <= gutter.start() || obj.getLeftX() >= gutter.end())) {
                return false;
            }
        }
        return true;
    }

    private static boolean isLineArtWithContents(List<IObject> band) {
        for (IObject obj : band) {
            if (!(obj instanceof LineArtChunk) && !isInsideLineArt(obj, band)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInsideLineArt(IObject obj, List<IObject> band) {
        for (IObject other : band) {
            if (other instanceof LineArtChunk && other.getLeftX() <= obj.getLeftX()
                    && other.getRightX() >= obj.getRightX() && other.getBottomY() <= obj.getBottomY()
                    && other.getTopY() >= obj.getTopY()) {
                return true;
            }
        }
        return false;
    }

    private static List<IObject> concat(List<IObject> first, List<IObject> second) {
        List<IObject> result = new ArrayList<>(first);
        result.addAll(second);
        return result;
    }

    /**
     * Sort the two columns one after the other. Elements in the gutter (for example a page
     * number) are put back by their Y position, as cross-layout elements are. An element that
     * goes from the gutter into a column goes with that column, by its center.
     */
    private static List<IObject> segmentColumnsFirst(List<IObject> objects, CutInfo gutter,
                                                     boolean preferHorizontalFirst) {
        List<IObject> columns = new ArrayList<>();
        List<IObject> inGutter = new ArrayList<>();
        for (IObject obj : objects) {
            if (isInsideGutter(obj, gutter)) {
                inGutter.add(obj);
            } else {
                columns.add(obj);
            }
        }
        List<IObject> sortedColumns = flatMapRecursive(
                splitByVerticalCut(columns, gutter.position), preferHorizontalFirst);
        return mergeCrossLayoutElements(sortedColumns, inGutter);
    }

    private static boolean isLeftOfGutter(IObject obj, CutInfo gutter) {
        return obj.getRightX() <= gutter.start() + GUTTER_EDGE_TOLERANCE;
    }

    private static boolean isRightOfGutter(IObject obj, CutInfo gutter) {
        return obj.getLeftX() >= gutter.end() - GUTTER_EDGE_TOLERANCE;
    }

    private static boolean isInsideGutter(IObject obj, CutInfo gutter) {
        return obj.getLeftX() >= gutter.start() - GUTTER_EDGE_TOLERANCE
                && obj.getRightX() <= gutter.end() + GUTTER_EDGE_TOLERANCE;
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

        /** The lower edge of the gap: its left edge (vertical gap) or its bottom (horizontal gap). */
        double start() {
            return position - gap / 2.0;
        }

        /** The upper edge of the gap: its right edge (vertical gap) or its top (horizontal gap). */
        double end() {
            return position + gap / 2.0;
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

        CutInfo edgeCut = findVerticalCutByEdges(objects);

        // If the edge gap is already significant, use it directly.
        if (edgeCut.gap >= MIN_GAP_THRESHOLD) {
            return edgeCut;
        }

        // When edge gap is small, narrow outlier elements (e.g., page numbers,
        // footnote markers) may bridge an otherwise clear column gap.
        // Retry without elements narrower than 10% of the region width.
        if (objects.size() >= 3) {
            List<IObject> filtered = withoutNarrowElements(objects);
            if (filtered.size() >= 2 && filtered.size() < objects.size()) {
                CutInfo filteredCut = findVerticalCutByEdges(filtered);
                if (filteredCut.gap > edgeCut.gap && filteredCut.gap >= MIN_GAP_THRESHOLD) {
                    return filteredCut;
                }
            }
        }

        return edgeCut;
    }

    /**
     * Remove the elements that are narrower than NARROW_ELEMENT_WIDTH_RATIO of the region width.
     */
    private static List<IObject> withoutNarrowElements(List<IObject> objects) {
        BoundingBox region = calculateBoundingRegion(objects);
        if (region == null) {
            return new ArrayList<>(objects);
        }
        double narrowThreshold = region.getWidth() * NARROW_ELEMENT_WIDTH_RATIO;
        List<IObject> filtered = new ArrayList<>();
        for (IObject obj : objects) {
            if (obj.getBoundingBox().getWidth() >= narrowThreshold) {
                filtered.add(obj);
            }
        }
        return filtered;
    }

    /**
     * Find vertical cut by edge gaps.
     * Finds the largest gap between rightX of one element and leftX of the next.
     */
    private static CutInfo findVerticalCutByEdges(List<IObject> objects) {
        List<IObject> sorted = new ArrayList<>(objects);
        sorted.sort(Comparator.comparingDouble((IObject o) -> o.getBoundingBox().getLeftX())
                .thenComparingDouble(o -> o.getBoundingBox().getRightX()));

        double largestGap = 0;
        double cutPosition = 0;
        Double prevRight = null;

        for (IObject obj : sorted) {
            double left = obj.getLeftX();
            double right = obj.getRightX();

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
        return largestGap(findHorizontalGaps(objects), Double.NEGATIVE_INFINITY);
    }

    /**
     * Find the gaps in the projection of the objects onto the Y-axis, from top to bottom.
     */
    private static List<CutInfo> findHorizontalGaps(List<IObject> objects) {
        List<IObject> sorted = new ArrayList<>(objects);
        sorted.sort(TOP_TO_BOTTOM);

        List<CutInfo> gaps = new ArrayList<>();
        Double prevBottom = null;

        for (IObject obj : sorted) {
            double top = obj.getTopY();
            double bottom = obj.getBottomY();

            if (prevBottom != null && prevBottom > top) {
                gaps.add(new CutInfo((prevBottom + top) / 2.0, prevBottom - top));
            }

            prevBottom = (prevBottom == null) ? bottom : Math.min(prevBottom, bottom);
        }

        return gaps;
    }

    /**
     * Find the largest gap whose lower edge is at or above lowestStart. Of equal gaps, the highest one.
     *
     * @return CutInfo of the gap, or a gap of 0 if there is no such gap
     */
    private static CutInfo largestGap(List<CutInfo> gaps, double lowestStart) {
        CutInfo largest = new CutInfo(0, 0);
        for (CutInfo gap : gaps) {
            if (gap.gap > largest.gap && gap.start() >= lowestStart) {
                largest = gap;
            }
        }
        return largest;
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
            // Use center Y to determine which group
            double centerY = obj.getCenterY();
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
            // Use center X to determine which group
            double centerX = obj.getCenterX();
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

                double mainTopY = mainObj.getTopY();
                double crossTopY = crossObj.getTopY();

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

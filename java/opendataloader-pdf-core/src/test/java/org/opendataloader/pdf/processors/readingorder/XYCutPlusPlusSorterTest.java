/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors.readingorder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for XYCutPlusPlusSorter.
 */
class XYCutPlusPlusSorterTest {

    @BeforeEach
    void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
    }

    // ========== BASIC FUNCTIONALITY TESTS ==========

    @Test
    void sort_nullList_returnsNull() {
        List<IObject> result = XYCutPlusPlusSorter.sort(null);
        assertNull(result);
    }

    @Test
    void sort_emptyList_returnsEmpty() {
        List<IObject> result = XYCutPlusPlusSorter.sort(new ArrayList<>());
        assertTrue(result.isEmpty());
    }

    @Test
    void sort_singleObject_returnsSame() {
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 90, 20, 80, "A"));

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        assertEquals(1, result.size());
        assertEquals("A", getText(result.get(0)));
    }

    @Test
    void sort_singleColumn_topToBottom() {
        // Single column layout - objects should be sorted top to bottom
        // PDF coordinate: Y increases upward
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 70, 100, 60, "C"));  // bottom
        objects.add(createTextLine(10, 90, 100, 80, "A"));  // top
        objects.add(createTextLine(10, 80, 100, 70, "B"));  // middle

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        assertEquals(3, result.size());
        assertEquals("A", getText(result.get(0)));
        assertEquals("B", getText(result.get(1)));
        assertEquals("C", getText(result.get(2)));
    }

    // ========== CROSS-LAYOUT DETECTION TESTS ==========

    @Test
    void identifyCrossLayoutElements_wideHeader_detected() {
        // Wide header spanning full width, with narrow columns below
        // Header: width=180 (the widest element), Column items: width=40
        // Using maxWidth-based detection: maxWidth=180, beta=0.7, threshold=126
        // Header width 180 >= 126, and overlaps with multiple elements -> detected
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 95, 190, 85, "Header"));  // Wide header
        objects.add(createTextLine(10, 75, 50, 65, "Col1-A"));   // Left column
        objects.add(createTextLine(10, 55, 50, 45, "Col1-B"));
        objects.add(createTextLine(100, 75, 140, 65, "Col2-A")); // Right column
        objects.add(createTextLine(100, 55, 140, 45, "Col2-B"));

        // Use beta=0.7 to detect elements that are at least 70% of max width
        List<IObject> crossLayout = XYCutPlusPlusSorter.identifyCrossLayoutElements(objects, 0.7);

        assertEquals(1, crossLayout.size());
        assertEquals("Header", getText(crossLayout.get(0)));
    }

    @Test
    void identifyCrossLayoutElements_narrowElements_notDetected() {
        // All elements have similar widths - no cross-layout
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 90, 50, 80, "A"));
        objects.add(createTextLine(10, 70, 50, 60, "B"));
        objects.add(createTextLine(10, 50, 50, 40, "C"));

        List<IObject> crossLayout = XYCutPlusPlusSorter.identifyCrossLayoutElements(objects, 1.3);

        assertTrue(crossLayout.isEmpty());
    }

    @Test
    void identifyCrossLayoutElements_wideButNoOverlaps_notDetected() {
        // Wide element but doesn't horizontally overlap with others
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 95, 190, 85, "Wide"));   // Wide, at top
        objects.add(createTextLine(200, 70, 250, 60, "A"));     // Far right, no overlap
        objects.add(createTextLine(260, 70, 310, 60, "B"));     // Far right, no overlap

        List<IObject> crossLayout = XYCutPlusPlusSorter.identifyCrossLayoutElements(objects, 1.3);

        assertTrue(crossLayout.isEmpty());
    }

    @Test
    void hasMinimumOverlaps_sufficientOverlaps_returnsTrue() {
        List<IObject> objects = new ArrayList<>();
        IObject wide = createTextLine(10, 90, 190, 80, "Wide");
        objects.add(wide);
        objects.add(createTextLine(20, 70, 60, 60, "A"));   // Overlaps with wide
        objects.add(createTextLine(100, 70, 140, 60, "B")); // Overlaps with wide

        boolean result = XYCutPlusPlusSorter.hasMinimumOverlaps(wide, objects, 2);

        assertTrue(result);
    }

    @Test
    void hasMinimumOverlaps_insufficientOverlaps_returnsFalse() {
        List<IObject> objects = new ArrayList<>();
        IObject element = createTextLine(10, 90, 50, 80, "Element");
        objects.add(element);
        objects.add(createTextLine(100, 70, 140, 60, "A")); // No horizontal overlap

        boolean result = XYCutPlusPlusSorter.hasMinimumOverlaps(element, objects, 2);

        assertFalse(result);
    }

    // ========== DENSITY RATIO TESTS ==========

    @Test
    void computeDensityRatio_denseLayout_highRatio() {
        // Tightly packed elements - high density
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(0, 100, 100, 50, "A"));   // 100x50 = 5000
        objects.add(createTextLine(0, 50, 100, 0, "B"));     // 100x50 = 5000
        // Total area: 10000, Region: 100x100 = 10000, Density = 1.0

        double density = XYCutPlusPlusSorter.computeDensityRatio(objects);

        assertTrue(density > 0.9);
    }

    @Test
    void computeDensityRatio_sparseLayout_lowRatio() {
        // Widely spaced small elements - low density
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(0, 100, 10, 90, "A"));     // 10x10 = 100
        objects.add(createTextLine(90, 10, 100, 0, "B"));     // 10x10 = 100
        // Total area: 200, Region: 100x100 = 10000, Density = 0.02

        double density = XYCutPlusPlusSorter.computeDensityRatio(objects);

        assertTrue(density < 0.5);
    }

    @Test
    void computeDensityRatio_emptyList_defaultRatio() {
        double density = XYCutPlusPlusSorter.computeDensityRatio(new ArrayList<>());

        assertEquals(1.0, density, 0.001);
    }

    // ========== SPLIT TESTS ==========

    @Test
    void splitByHorizontalCut_validCut_correctGroups() {
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 90, 100, 80, "Top"));
        objects.add(createTextLine(10, 40, 100, 30, "Bottom"));

        List<List<IObject>> groups = XYCutPlusPlusSorter.splitByHorizontalCut(objects, 60.0);

        assertEquals(2, groups.size());
        assertEquals(1, groups.get(0).size());
        assertEquals(1, groups.get(1).size());
        assertEquals("Top", getText(groups.get(0).get(0)));
        assertEquals("Bottom", getText(groups.get(1).get(0)));
    }

    @Test
    void splitByVerticalCut_validCut_correctGroups() {
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 90, 40, 80, "Left"));
        objects.add(createTextLine(80, 90, 110, 80, "Right"));

        List<List<IObject>> groups = XYCutPlusPlusSorter.splitByVerticalCut(objects, 60.0);

        assertEquals(2, groups.size());
        assertEquals(1, groups.get(0).size());
        assertEquals(1, groups.get(1).size());
        assertEquals("Left", getText(groups.get(0).get(0)));
        assertEquals("Right", getText(groups.get(1).get(0)));
    }

    // ========== INTEGRATION TESTS ==========

    @Test
    void sort_twoColumns_leftColumnFirst() {
        // Two-column layout with clear X gap
        // [A] [B]
        // [C] [D]
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 90, 40, 80, "A"));   // left column, top
        objects.add(createTextLine(80, 90, 110, 80, "B"));  // right column, top
        objects.add(createTextLine(10, 70, 40, 60, "C"));   // left column, bottom
        objects.add(createTextLine(80, 70, 110, 60, "D"));  // right column, bottom

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        // With default density threshold, should process left column first
        assertEquals(4, result.size());
        assertEquals("A", getText(result.get(0)));
        assertEquals("C", getText(result.get(1)));
        assertEquals("B", getText(result.get(2)));
        assertEquals("D", getText(result.get(3)));
    }

    @Test
    void sort_twoColumnsWithHeader_headerFirst() {
        // [    HEADER    ]
        // [Col1]  [Col2]
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 95, 190, 85, "Header"));  // Wide header
        objects.add(createTextLine(10, 75, 50, 65, "Col1-A"));   // Left column
        objects.add(createTextLine(10, 55, 50, 45, "Col1-B"));
        objects.add(createTextLine(100, 75, 140, 65, "Col2-A")); // Right column
        objects.add(createTextLine(100, 55, 140, 45, "Col2-B"));

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        // Header should come first as cross-layout element
        assertEquals(5, result.size());
        assertEquals("Header", getText(result.get(0)));
        // Then left column
        assertEquals("Col1-A", getText(result.get(1)));
        assertEquals("Col1-B", getText(result.get(2)));
        // Then right column
        assertEquals("Col2-A", getText(result.get(3)));
        assertEquals("Col2-B", getText(result.get(4)));
    }

    @Test
    void sort_headerAndFooter_correctPositions() {
        // [    HEADER    ]
        // [Col1]  [Col2]
        // [    FOOTER    ]
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 95, 190, 85, "Header"));
        objects.add(createTextLine(10, 75, 50, 65, "Col1"));
        objects.add(createTextLine(100, 75, 140, 65, "Col2"));
        objects.add(createTextLine(10, 15, 190, 5, "Footer"));

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        assertEquals(4, result.size());
        assertEquals("Header", getText(result.get(0)));
        // Columns in middle
        assertTrue(getText(result.get(1)).startsWith("Col"));
        assertTrue(getText(result.get(2)).startsWith("Col"));
        assertEquals("Footer", getText(result.get(3)));
    }

    @Test
    void sort_horizontalSections_largerYGap_horizontalCutFirst() {
        // Layout with larger Y gap than X gap
        // [A] [B]    <- top row (Y: 80-90)
        //            <- Y gap = 40
        // [C] [D]    <- bottom row (Y: 30-40)
        // X gap between columns = 10 (30 to 40)
        // Since Y gap (40) > X gap (10), horizontal cut is chosen first
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 90, 30, 80, "A"));
        objects.add(createTextLine(40, 90, 60, 80, "B"));
        objects.add(createTextLine(10, 40, 30, 30, "C"));
        objects.add(createTextLine(40, 40, 60, 30, "D"));

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        // Larger Y gap -> horizontal cut first -> row-by-row reading
        // Top row: A -> B, then bottom row: C -> D
        assertEquals(4, result.size());
        assertEquals("A", getText(result.get(0)));
        assertEquals("B", getText(result.get(1)));
        assertEquals("C", getText(result.get(2)));
        assertEquals("D", getText(result.get(3)));
    }

    @Test
    void sort_withCustomParameters_respectsParameters() {
        // Test with high beta (less likely to detect cross-layout)
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 95, 190, 85, "Header"));
        objects.add(createTextLine(10, 75, 50, 65, "A"));
        objects.add(createTextLine(100, 75, 140, 65, "B"));

        // With very high beta, header should not be detected as cross-layout
        List<IObject> result = XYCutPlusPlusSorter.sort(objects, 10.0, 0.9);

        // Header not treated specially, so order depends on axis preference
        assertEquals(3, result.size());
    }

    // ========== BOUNDING REGION TESTS ==========

    @Test
    void calculateBoundingRegion_multipleObjects_correctBounds() {
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(10, 90, 50, 80, "A"));   // leftX=10, rightX=50
        objects.add(createTextLine(30, 70, 100, 60, "B"));  // leftX=30, rightX=100

        BoundingBox region = XYCutPlusPlusSorter.calculateBoundingRegion(objects);

        assertNotNull(region);
        assertEquals(10.0, region.getLeftX(), 0.001);   // min leftX
        assertEquals(100.0, region.getRightX(), 0.001); // max rightX
        assertEquals(60.0, region.getBottomY(), 0.001); // min bottomY
        assertEquals(90.0, region.getTopY(), 0.001);    // max topY
    }

    @Test
    void calculateTotalArea_multipleObjects_sumOfAreas() {
        List<IObject> objects = new ArrayList<>();
        objects.add(createTextLine(0, 20, 10, 10, "A"));  // 10 x 10 = 100
        objects.add(createTextLine(0, 40, 20, 20, "B"));  // 20 x 20 = 400

        double area = XYCutPlusPlusSorter.calculateTotalArea(objects);

        assertEquals(500.0, area, 0.001);
    }

    // ========== MERGE TESTS ==========

    @Test
    void mergeCrossLayoutElements_emptyCrossLayout_returnsSortedMain() {
        List<IObject> main = new ArrayList<>();
        main.add(createTextLine(10, 90, 50, 80, "A"));
        main.add(createTextLine(10, 70, 50, 60, "B"));

        List<IObject> result = XYCutPlusPlusSorter.mergeCrossLayoutElements(main, new ArrayList<>());

        assertEquals(2, result.size());
        assertEquals("A", getText(result.get(0)));
        assertEquals("B", getText(result.get(1)));
    }

    @Test
    void mergeCrossLayoutElements_crossLayoutAtTop_insertsFirst() {
        List<IObject> main = new ArrayList<>();
        main.add(createTextLine(10, 70, 50, 60, "Content"));

        List<IObject> crossLayout = new ArrayList<>();
        crossLayout.add(createTextLine(10, 90, 190, 80, "Header"));

        List<IObject> result = XYCutPlusPlusSorter.mergeCrossLayoutElements(main, crossLayout);

        assertEquals(2, result.size());
        assertEquals("Header", getText(result.get(0)));
        assertEquals("Content", getText(result.get(1)));
    }

    // ========== REAL-WORLD LAYOUT TESTS ==========

    /**
     * Test based on actual academic paper layout (2408.02509v1.pdf).
     * This paper has:
     * - Title at top (wide, cross-layout)
     * - Authors below title
     * - Two-column layout for content
     * - ArXiv sidebar on left (narrow, vertical)
     *
     * Expected reading order:
     * 1. Title (ID 95): "Practical Attacks against Black-box..."
     * 2. Authors (ID 96): "Slobodan Jenko..."
     * 3. Abstract - left column (ID 97): "Abstract—Modern code..."
     * 4. Section heading (ID 98): "1. Introduction"
     * 5. Intro para 1 - left (ID 99): "Code completion aims..."
     * 6. Intro para 2 - left (ID 100): "Given the widespread..."
     * 7. Right column para 1 (ID 101): "bilities even under..."
     * 8. Right column para 2 (ID 102): "Our Practical Threat Model..."
     * 9. Right column para 3 (ID 103): "The attacker's goal..."
     * 10. Right column para 4 (ID 104): "Key Challenges..."
     * 11. ArXiv sidebar (ID 105): "arXiv:2408.02509v1..."
     */
    @Test
    void sort_academicPaperTwoColumn_correctReadingOrder() {
        List<IObject> objects = new ArrayList<>();

        // Create objects based on actual bounding boxes from 2408.02509v1.pdf
        // BoundingBox format: [leftX, bottomY, rightX, topY]

        // ID 95: Title (cross-layout, wide)
        objects.add(createTextLineWithId(119.725, 697.936, 492.279, 679.722, "Title", 95));

        // ID 96: Authors
        objects.add(createTextLineWithId(129.831, 653.915, 482.17, 609.655, "Authors", 96));

        // ID 97: Abstract - LEFT column (tall block)
        objects.add(createTextLineWithId(53.397, 598.418, 298.579, 322.175, "Abstract", 97));

        // ID 98: Section heading "1. Introduction" - LEFT column
        objects.add(createTextLineWithId(54.0, 310.895, 134.124, 295.283, "Introduction", 98));

        // ID 99: Introduction paragraph - LEFT column
        objects.add(createTextLineWithId(53.75, 285.545, 298.663, 116.696, "IntroPara1", 99));

        // ID 100: Continuation paragraph - LEFT column (bottom)
        objects.add(createTextLineWithId(53.64, 117.383, 298.66, 71.733, "IntroPara2", 100));

        // ID 101: RIGHT column - "bilities even under..." (top)
        objects.add(createTextLineWithId(314.64, 598.982, 559.748, 474.932, "RightPara1", 101));

        // ID 102: RIGHT column - "Our Practical Threat Model..."
        objects.add(createTextLineWithId(315.0, 470.417, 559.662, 323.607, "RightPara2", 102));

        // ID 103: RIGHT column - "The attacker's goal..."
        objects.add(createTextLineWithId(315.0, 324.708, 559.657, 223.058, "RightPara3", 103));

        // ID 104: RIGHT column - "Key Challenges..." (bottom)
        objects.add(createTextLineWithId(314.64, 218.543, 559.657, 71.733, "RightPara4", 104));

        // ID 105: ArXiv sidebar (very narrow, on left margin)
        objects.add(createTextLineWithId(14.04, 579.2, 36.36, 237.0, "ArXivSidebar", 105));

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        assertEquals(11, result.size());

        // Find positions for all elements
        int titlePos = findPosition(result, "Title");
        int authorsPos = findPosition(result, "Authors");
        int abstractPos = findPosition(result, "Abstract");
        int introPos = findPosition(result, "Introduction");
        int introPara1Pos = findPosition(result, "IntroPara1");
        int introPara2Pos = findPosition(result, "IntroPara2");
        int rightPara1Pos = findPosition(result, "RightPara1");
        int rightPara2Pos = findPosition(result, "RightPara2");
        int rightPara3Pos = findPosition(result, "RightPara3");
        int rightPara4Pos = findPosition(result, "RightPara4");
        // Note: ArXivSidebar position is flexible (similar to MORAN paper's 667)

        // Verify key ordering:
        // 1. Title -> Authors (header section)
        assertTrue(titlePos < authorsPos, "Title should come before Authors");

        // 2. Authors -> Abstract (header before body)
        assertTrue(authorsPos < abstractPos, "Authors should come before Abstract");

        // 3. LEFT column content should come before RIGHT column
        assertTrue(abstractPos < rightPara1Pos, "Abstract should come before right column");
        assertTrue(introPos < rightPara1Pos, "Introduction should come before right column");
        assertTrue(introPara1Pos < rightPara1Pos, "IntroPara1 should come before right column");
        assertTrue(introPara2Pos < rightPara1Pos, "IntroPara2 should come before right column");

        // 4. Left column internal order (top to bottom)
        assertTrue(abstractPos < introPos, "Abstract should come before Introduction");
        assertTrue(introPos < introPara1Pos, "Introduction should come before IntroPara1");
        assertTrue(introPara1Pos < introPara2Pos, "IntroPara1 should come before IntroPara2");

        // 5. Right column internal order (top to bottom)
        assertTrue(rightPara1Pos < rightPara2Pos, "RightPara1 should come before RightPara2");
        assertTrue(rightPara2Pos < rightPara3Pos, "RightPara2 should come before RightPara3");
        assertTrue(rightPara3Pos < rightPara4Pos, "RightPara3 should come before RightPara4");
    }

    /**
     * Test two-column layout where columns have overlapping Y ranges.
     * This simulates the common academic paper layout where left and right
     * columns have content at the same vertical positions.
     */
    @Test
    void sort_twoColumnsOverlappingY_leftColumnFirst() {
        List<IObject> objects = new ArrayList<>();

        // Left column: X range 50-300
        objects.add(createTextLine(50, 600, 300, 500, "Left1"));
        objects.add(createTextLine(50, 490, 300, 400, "Left2"));
        objects.add(createTextLine(50, 390, 300, 300, "Left3"));

        // Right column: X range 310-560 (clear gap at X=300-310)
        objects.add(createTextLine(310, 600, 560, 500, "Right1"));
        objects.add(createTextLine(310, 490, 560, 400, "Right2"));
        objects.add(createTextLine(310, 390, 560, 300, "Right3"));

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        assertEquals(6, result.size());

        // Should read left column first, then right column
        assertEquals("Left1", getText(result.get(0)));
        assertEquals("Left2", getText(result.get(1)));
        assertEquals("Left3", getText(result.get(2)));
        assertEquals("Right1", getText(result.get(3)));
        assertEquals("Right2", getText(result.get(4)));
        assertEquals("Right3", getText(result.get(5)));
    }

    // ========== 1901.03003.pdf READING ORDER TEST ==========

    /**
     * Test based on actual academic paper layout (1901.03003.pdf - MORAN paper).
     *
     * Expected reading order (IDs):
     * 667, 646, 647, 648, 649, 650, [653-662 as group], 656, 663, 664, 665, 666
     *
     * Images 653-662 should be grouped together but internal order is flexible.
     */
    @Test
    void sort_1901_03003_moran_paper_correctReadingOrder() {
        List<IObject> objects = new ArrayList<>();

        // Create objects based on actual bounding boxes from 1901.03003.json
        // BoundingBox format in JSON: [leftX, bottomY, rightX, topY]

        // ID 667: ArXiv sidebar - narrow, on left margin
        objects.add(createTextLineWithId(14.04, 577.52, 36.36, 232.0, "667", 667));

        // ID 646: Title - cross-layout, wide
        objects.add(createTextLineWithId(130.151, 688.839, 465.077, 652.242, "646", 646));

        // ID 647: Authors
        objects.add(createTextLineWithId(82.271, 630.323, 516.716, 567.65, "647", 647));

        // ID 648: Abstract heading
        objects.add(createTextLineWithId(145.995, 544.182, 190.48, 528.628, "648", 648));

        // ID 649: Abstract content - LEFT column
        objects.add(createTextLineWithId(50.112, 512.148, 286.362, 173.942, "649", 649));

        // ID 650: Keywords - LEFT column
        objects.add(createTextLineWithId(50.112, 156.766, 286.359, 129.636, "650", 650));

        // Images 653-662 on RIGHT side (3x3 grid)
        objects.add(createTextLineWithId(315.944, 538.682, 386.808, 496.162, "653", 653));
        objects.add(createTextLineWithId(315.945, 495.167, 386.807, 452.648, "654", 654));
        objects.add(createTextLineWithId(315.945, 451.652, 386.808, 409.132, "655", 655));
        objects.add(createTextLineWithId(392.918, 538.682, 463.783, 496.162, "657", 657));
        objects.add(createTextLineWithId(392.918, 495.166, 463.783, 452.646, "658", 658));
        objects.add(createTextLineWithId(392.918, 451.65, 463.783, 409.13, "659", 659));
        objects.add(createTextLineWithId(469.89, 538.683, 540.76, 496.163, "660", 660));
        objects.add(createTextLineWithId(469.889, 495.167, 540.76, 452.647, "661", 661));
        objects.add(createTextLineWithId(469.89, 451.652, 540.759, 409.131, "662", 662));

        // ID 656: Figure caption - below images
        objects.add(createTextLineWithId(308.862, 410.306, 545.115, 360.946, "656", 656));

        // ID 663: Introduction heading - RIGHT column
        objects.add(createTextLineWithId(308.862, 343.869, 385.698, 328.315, "663", 663));

        // ID 664-666: Introduction paragraphs - RIGHT column
        objects.add(createTextLineWithId(308.862, 321.771, 545.109, 200.233, "664", 664));
        objects.add(createTextLineWithId(308.862, 199.651, 545.109, 105.211, "665", 665));
        objects.add(createTextLineWithId(308.862, 104.629, 545.109, 77.935, "666", 666));

        List<IObject> result = XYCutPlusPlusSorter.sort(objects);

        // Expected order: 667, 646, 647, 648, 649, 650, [images 653-662], 656, 663, 664, 665, 666
        // Images should be grouped but internal order is flexible

        // Find positions (pos667 is not checked since ArXiv sidebar order is flexible)
        int pos646 = findPosition(result, "646");
        int pos647 = findPosition(result, "647");
        int pos648 = findPosition(result, "648");
        int pos649 = findPosition(result, "649");
        int pos650 = findPosition(result, "650");
        int pos656 = findPosition(result, "656");
        int pos663 = findPosition(result, "663");
        int pos664 = findPosition(result, "664");
        int pos665 = findPosition(result, "665");
        int pos666 = findPosition(result, "666");

        // Find image positions
        int[] imgPositions = {
            findPosition(result, "653"),
            findPosition(result, "654"),
            findPosition(result, "655"),
            findPosition(result, "657"),
            findPosition(result, "658"),
            findPosition(result, "659"),
            findPosition(result, "660"),
            findPosition(result, "661"),
            findPosition(result, "662")
        };
        int minImgPos = Arrays.stream(imgPositions).min().getAsInt();
        int maxImgPos = Arrays.stream(imgPositions).max().getAsInt();

        // Verify ordering constraints
        // Note: 667 (ArXiv sidebar) is a special element, order is flexible

        // 1. 646 -> 647 (Title -> Authors)
        assertTrue(pos646 < pos647, "646 should come before 647. Got: 646@" + pos646 + ", 647@" + pos647);

        // 2. 647 -> 648 -> 649 -> 650
        assertTrue(pos647 < pos648, "647 should come before 648. Got: 647@" + pos647 + ", 648@" + pos648);
        assertTrue(pos648 < pos649, "648 should come before 649. Got: 648@" + pos648 + ", 649@" + pos649);
        assertTrue(pos649 < pos650, "649 should come before 650. Got: 649@" + pos649 + ", 650@" + pos650);

        // 3. Images 653-662 should be grouped together (consecutive)
        assertEquals(8, maxImgPos - minImgPos,
                "All 9 images should be consecutive (span of 8). Got span: " + (maxImgPos - minImgPos));

        // 4. 650 -> [images] -> 656 -> 663 -> 664 -> 665 -> 666
        assertTrue(pos650 < minImgPos, "650 should come before images. Got: 650@" + pos650 + ", images start@" + minImgPos);
        assertTrue(maxImgPos < pos656, "Images should come before 656. Got: images end@" + maxImgPos + ", 656@" + pos656);
        assertTrue(pos656 < pos663, "656 should come before 663. Got: 656@" + pos656 + ", 663@" + pos663);
        assertTrue(pos663 < pos664, "663 should come before 664. Got: 663@" + pos663 + ", 664@" + pos664);
        assertTrue(pos664 < pos665, "664 should come before 665. Got: 664@" + pos664 + ", 665@" + pos665);
        assertTrue(pos665 < pos666, "665 should come before 666. Got: 665@" + pos665 + ", 666@" + pos666);
    }

    // ========== HELPER METHODS ==========

    private int findPosition(List<IObject> objects, String text) {
        for (int i = 0; i < objects.size(); i++) {
            if (getText(objects.get(i)).equals(text)) {
                return i;
            }
        }
        return -1;
    }

    private IObject createTextLineWithId(double leftX, double topY, double rightX, double bottomY, String text, int id) {
        return createTextLine(leftX, topY, rightX, bottomY, text);
    }

    /**
     * Helper method to create a TextLine with the specified bounding box and text.
     * BoundingBox constructor: (pageNumber, leftX, bottomY, rightX, topY)
     */
    private IObject createTextLine(double leftX, double topY, double rightX, double bottomY, String text) {
        BoundingBox bbox = new BoundingBox(0, leftX, bottomY, rightX, topY);
        TextChunk chunk = new TextChunk(bbox, text, 10, rightX - leftX);
        return new TextLine(chunk);
    }

    /**
     * Helper method to extract text from a TextLine.
     */
    private String getText(IObject obj) {
        if (obj instanceof TextLine) {
            TextLine textLine = (TextLine) obj;
            if (!textLine.getTextChunks().isEmpty()) {
                return textLine.getTextChunks().get(0).getValue();
            }
        }
        return "";
    }

    // ========== INFINITE RECURSION PREVENTION TESTS (Issue #179) ==========

    /**
     * Test that demonstrates the infinite recursion bug condition.
     * <p>
     * The bug occurs when:
     * 1. A gap is found between object edges (leftX/rightX or topY/bottomY)
     * 2. But all objects' centers fall on the same side of the cut position
     * 3. This causes all objects to be placed in one group
     * 4. The same gap is found again, leading to infinite recursion
     * <p>
     * Example: Two objects where one is very wide and one is narrow
     * - Wide object: leftX=0, rightX=200 (centerX=100)
     * - Narrow object: leftX=202, rightX=204 (centerX=203)
     * - Gap between 200 and 202 → cutPosition = 201
     * - Wide centerX=100 < 201 → left group
     * - Narrow centerX=203 >= 201 → right group [OK, this works]
     * <p>
     * But if:
     * - Wide object: leftX=0, rightX=200 (centerX=100)
     * - Another wide object: leftX=202, rightX=402 (centerX=302)
     * This should also work...
     * <p>
     * The real problematic case is when objects overlap in one dimension
     * but have a gap in another, and the gap-based cut doesn't actually
     * separate the objects by their centers.
     */
    @Test
    void sort_noStackOverflowWithComplexLayout_issue179() {
        // This test ensures that the algorithm completes within a reasonable time
        // even with potentially problematic layouts
        List<IObject> objects = new ArrayList<>();

        // Create a layout where vertical gap exists but horizontal cut might not separate well
        // Simulating complex multi-column layout with overlapping regions
        for (int i = 0; i < 20; i++) {
            // Left column items
            objects.add(createTextLine(50, 700 - i * 30, 250, 690 - i * 30, "L" + i));
            // Right column items
            objects.add(createTextLine(260, 700 - i * 30, 450, 690 - i * 30, "R" + i));
        }

        // Should complete without StackOverflowError
        assertTimeout(Duration.ofSeconds(5), () -> {
            List<IObject> result = XYCutPlusPlusSorter.sort(objects);
            assertEquals(40, result.size());
        });
    }

    /**
     * Test case that specifically triggers the edge-vs-center mismatch bug.
     * <p>
     * The gap detection uses edges (leftX, rightX) but split uses centers.
     * When a very wide object has a small gap to a narrow object,
     * the center of the wide object might be far from the gap.
     */
    @Test
    void sort_wideAndNarrowObjects_noInfiniteRecursion() {
        List<IObject> objects = new ArrayList<>();

        // Wide object: leftX=0, rightX=100, centerX=50
        // Very narrow object at edge: leftX=101, rightX=102, centerX=101.5
        // Gap = 1pt at position 100.5
        // Both centers should be separated correctly

        objects.add(createTextLine(0, 100, 100, 90, "Wide"));
        objects.add(createTextLine(101, 100, 102, 90, "Narrow"));

        assertTimeout(Duration.ofSeconds(2), () -> {
            List<IObject> result = XYCutPlusPlusSorter.sort(objects);
            assertEquals(2, result.size());
        });
    }

    /**
     * Test with objects that have edges creating a gap but centers on same side.
     * This is the exact condition that can cause infinite recursion.
     * <p>
     * Object A: leftX=0, rightX=300, centerX=150
     * Object B: leftX=301, rightX=310, centerX=305.5
     * Gap at 300-301, cutPosition=300.5
     * A.centerX=150 < 300.5 → left
     * B.centerX=305.5 >= 300.5 → right
     * This case works fine.
     * <p>
     * But with slightly different coords:
     * Object A: leftX=0, rightX=150, centerX=75
     * Object B: leftX=151, rightX=155, centerX=153
     * Object C: leftX=156, rightX=160, centerX=158
     * (B and C are narrow, close together)
     * Gap1: 150-151, cutPosition1=150.5
     * All centers < 150.5? No, B and C have centers > 150.5
     * <p>
     * The issue occurs in Y-axis with overlapping objects...
     */
    @Test
    void sort_manySmallGaps_noInfiniteRecursion() {
        List<IObject> objects = new ArrayList<>();

        // Create many small objects with tiny gaps between them
        // This stress tests the gap detection and splitting logic
        for (int i = 0; i < 10; i++) {
            double x = i * 12;  // 12pt apart, objects are 10pt wide
            objects.add(createTextLine(x, 100, x + 10, 90, "O" + i));
        }

        assertTimeout(Duration.ofSeconds(2), () -> {
            List<IObject> result = XYCutPlusPlusSorter.sort(objects);
            assertEquals(10, result.size());
        });
    }

    /**
     * Test case where horizontal cut finds a gap but all objects have
     * centers above the cut position (PDF Y coordinates: higher = top).
     */
    @Test
    void sort_horizontalGapWithCentersOnOneSide_noInfiniteRecursion() {
        List<IObject> objects = new ArrayList<>();

        // Tall object: bottomY=0, topY=200, centerY=100
        // Short object below: bottomY=-10, topY=-5, centerY=-7.5
        // Gap between -5 and 0, cutPosition=-2.5
        // Tall centerY=100 > -2.5 → above group
        // Short centerY=-7.5 < -2.5 → below group
        // Wait, in PDF coordinates, this is reversed...
        // Let me reconsider...

        // PDF: topY > bottomY, and larger Y is "above" on page
        // Object A: bottomY=50, topY=150, centerY=100 (tall)
        // Object B: bottomY=0, topY=10, centerY=5 (short, at bottom)
        // Vertical gap: 10 to 50, gap=40
        // cutPosition = (10+50)/2 = 30
        // A.centerY=100 > 30 → above
        // B.centerY=5 < 30 → below (actually, in horizontal cut, centerY > cutY means above)
        // Wait, I need to check the actual logic...

        // In findBestHorizontalCutWithProjection:
        // prevBottom tracks the lowest point seen so far (scanning top to bottom)
        // gap = prevBottom - top (when there's a gap)

        // Let's create objects that might trigger the issue
        objects.add(createTextLine(50, 200, 150, 100, "TallA"));  // bottomY=100, topY=200
        objects.add(createTextLine(50, 90, 150, 80, "ShortB"));   // bottomY=80, topY=90
        objects.add(createTextLine(200, 200, 300, 100, "TallC")); // bottomY=100, topY=200

        assertTimeout(Duration.ofSeconds(2), () -> {
            List<IObject> result = XYCutPlusPlusSorter.sort(objects);
            assertEquals(3, result.size());
        });
    }

    /**
     * Regression test for issue #179: StackOverflowError in XYCutPlusPlusSorter.
     * <p>
     * This test creates a layout that was reported to cause infinite recursion
     * in v1.10.0. The exact reproduction requires objects where the split
     * operation doesn't make progress (all objects end up in one group).
     */
    @Test
    void sort_issue179_regressionTest() {
        List<IObject> objects = new ArrayList<>();

        // Simulate a complex document layout with many elements
        // that might trigger the edge case
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 3; col++) {
                double x = 50 + col * 180;
                double y = 700 - row * 100;
                // Varying widths to create complex gap patterns
                double width = 50 + (col * 30);
                objects.add(createTextLine(x, y, x + width, y - 20, "R" + row + "C" + col));
            }
        }

        // Add some cross-layout elements
        objects.add(createTextLine(50, 750, 500, 730, "Header"));
        objects.add(createTextLine(50, 50, 500, 30, "Footer"));

        assertTimeout(Duration.ofSeconds(5), () -> {
            List<IObject> result = XYCutPlusPlusSorter.sort(objects);
            assertEquals(17, result.size());  // 15 grid + 2 header/footer
        });
    }
}

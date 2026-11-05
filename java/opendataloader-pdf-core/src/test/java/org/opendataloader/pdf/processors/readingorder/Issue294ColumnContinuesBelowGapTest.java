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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Issue #294: on a two-column page, the left column continues below a large gap after the
 * right column has ended. The gap is wider than the column gutter, but it is a gap inside the
 * left column. The reading order must keep the left column together before the right column.
 *
 * <p>The boxes copy the measurements of the page of the report (page 751 x 544 pt): left column
 * x 48.18-365.76, right column x 385.5-703.09 (gutter 19.74 pt), the gap inside the left column
 * 28.6 pt, and a page number inside the gutter.
 */
class Issue294ColumnContinuesBelowGapTest {

    private static final String SYMPTOM = "the right column was read before the end of the left column";

    @BeforeEach
    void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
    }

    /** The page of the report: the right column ends at y 249.7, the left column goes on to y 59.5. */
    private static List<IObject> reportedPage(double rightColumnBottom) {
        List<IObject> objects = new ArrayList<>();
        objects.add(line("L1", 61.32, 476.727, 227.888, 488.25));
        objects.add(line("L2", 48.18, 419.127, 365.686, 476.73));
        objects.add(line("L3", 48.18, 189.627, 259.268, 406.372));
        objects.add(line("R1", 398.819, 476.727, 459.552, 488.25));
        objects.add(line("R2", 385.5, 453.687, 703.028, 476.73));
        objects.add(line("R3", 385.5, 430.432, 526.903, 440.932));
        objects.add(line("R4", 385.5, 369.507, 703.026, 427.11));
        objects.add(line("R5", 385.5, 346.467, 702.968, 369.51));
        objects.add(line("R6", 385.5, 300.387, 703.086, 346.47));
        objects.add(line("R7", 385.5, 275.992, 535.783, 286.492));
        objects.add(line("R8", 385.5, rightColumnBottom, 702.998, 272.73));
        objects.add(line("L4", 61.2, 145.912, 312.967, 161.032));
        objects.add(line("L5", 48.18, 105.892, 143.565, 116.392));
        objects.add(line("L6", 48.18, 59.487, 365.762, 105.57));
        objects.add(line("PN", 366.3, 30.772, 384.87, 39.772));
        return objects;
    }

    @Test
    void leftColumnBelowTheGapComesBeforeTheRightColumn() {
        List<String> order = withoutPageNumber(sortedLabels(reportedPage(249.687)));
        assertEquals(Arrays.asList("L1", "L2", "L3", "L4", "L5", "L6",
                "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8"), order, SYMPTOM);
    }

    @Test
    void rightColumnThatEndsJustMoreThanTheGapHigherStillComesAfterTheLeftColumn() {
        // The left part above the gap ends at y 189.627. The right column ends 30 pt higher,
        // which is more than the gap of 28.6 pt.
        List<String> order = withoutPageNumber(sortedLabels(reportedPage(219.627)));
        assertEquals(Arrays.asList("L1", "L2", "L3", "L4", "L5", "L6",
                "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8"), order, SYMPTOM);
    }

    @Test
    void leftColumnComesFirstWhenLineArtAcrossThePageBridgesTheGutter() {
        // The page of the report also has line art: a rule across the page above the columns,
        // a box across the page around the page number, and a box around the heading L4.
        // The rule and the box across the page bridge the gutter.
        List<IObject> objects = reportedPage(249.687);
        objects.add(lineArt(48.06, 500.842, 703.14, 501.382));
        objects.add(lineArt(48.06, 27.592, 703.14, 43.402));
        objects.add(lineArt(48.0, 128.362, 366.6, 167.302));
        assertEquals(Arrays.asList("L1", "L2", "L3", "L4", "L5", "L6",
                "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "PN"), sortedLabels(objects), SYMPTOM);
    }

    @Test
    void leftColumnComesFirstWhenThePageNumberIsAtTheSideOfTheFooterBox() {
        // As above, but the page number is at the right end of the box across the bottom of the page.
        List<IObject> objects = reportedPage(249.687);
        objects.removeIf(object -> "PN".equals(label(object)));
        objects.add(line("PN", 683.0, 30.772, 701.57, 39.772));
        objects.add(lineArt(48.06, 500.842, 703.14, 501.382));
        objects.add(lineArt(48.06, 27.592, 703.14, 43.402));
        objects.add(lineArt(48.0, 128.362, 366.6, 167.302));
        assertEquals(Arrays.asList("L1", "L2", "L3", "L4", "L5", "L6",
                "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8", "PN"), sortedLabels(objects), SYMPTOM);
    }

    @Test
    void markerThatStartsInTheGutterStaysWithTheRightColumn() {
        // A narrow marker on the first line of the right column starts in the gutter.
        List<IObject> objects = reportedPage(249.687);
        objects.add(line("RM", 380.0, 476.727, 395.0, 488.25));
        assertEquals(Arrays.asList("L1", "L2", "L3", "L4", "L5", "L6",
                "RM", "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8"),
                withoutPageNumber(sortedLabels(objects)), SYMPTOM);
    }

    @Test
    void pageNumberInTheGutterStaysLast() {
        List<String> order = sortedLabels(reportedPage(249.687));
        assertEquals("PN", order.get(order.size() - 1),
                "the page number in the gutter must stay after both columns: " + order);
    }

    @Test
    void rightColumnThatEndsLessThanTheGapHigherKeepsTheHorizontalCut() {
        // The right column ends 10 pt above the left part, less than the gap of 28.6 pt.
        // The columns above the gap are almost level, so the gap separates the text below it.
        List<String> order = withoutPageNumber(sortedLabels(reportedPage(199.627)));
        assertEquals(Arrays.asList("L1", "L2", "L3", "R1", "R2", "R3", "R4", "R5", "R6", "R7", "R8",
                "L4", "L5", "L6"), order);
    }

    @Test
    void stackedTwoColumnSectionsKeepTheirOrder() {
        List<IObject> objects = new ArrayList<>();
        objects.add(line("A-L1", 48.18, 420, 365.69, 488.25));
        objects.add(line("A-L2", 48.18, 300, 365.69, 410));
        objects.add(line("A-R1", 385.5, 420, 703.03, 488.25));
        objects.add(line("A-R2", 385.5, 300, 703.03, 410));
        objects.add(line("B-L1", 48.18, 180, 365.69, 271.4));
        objects.add(line("B-L2", 48.18, 60, 365.69, 170));
        objects.add(line("B-R1", 385.5, 180, 703.03, 271.4));
        objects.add(line("B-R2", 385.5, 60, 703.03, 170));
        assertEquals(Arrays.asList("A-L1", "A-L2", "A-R1", "A-R2", "B-L1", "B-L2", "B-R1", "B-R2"),
                sortedLabels(objects));
    }

    @Test
    void lowerSectionWithAShortRightColumnKeepsTheSectionsInOrder() {
        // The lower section has the layout of the report: its right column ends at y 150, and its
        // left column continues 40 pt below that end to a gap of 30 pt. The gap between the
        // sections (28.6 pt) is wider than the gutter, so the upper section comes first.
        List<IObject> objects = new ArrayList<>();
        objects.add(line("A-L1", 48.18, 420, 365.69, 488.25));
        objects.add(line("A-L2", 48.18, 300, 365.69, 410));
        objects.add(line("A-R1", 385.5, 420, 703.03, 488.25));
        objects.add(line("A-R2", 385.5, 300, 703.03, 410));
        objects.add(line("B-L1", 48.18, 180, 365.69, 271.4));
        objects.add(line("B-L2", 48.18, 110, 365.69, 170));
        objects.add(line("B-L3", 48.18, 60, 365.69, 80));
        objects.add(line("B-R1", 385.5, 180, 703.03, 271.4));
        objects.add(line("B-R2", 385.5, 150, 703.03, 170));
        assertEquals(Arrays.asList("A-L1", "A-L2", "A-R1", "A-R2", "B-L1", "B-L2", "B-L3", "B-R1", "B-R2"),
                sortedLabels(objects), SYMPTOM);
    }

    @Test
    void pageNumberBelowTheLongerColumnStaysLast() {
        // The left column goes on to the bottom, and the right column ends early. The page
        // number is below the gutter, 5.8 pt from the right column: the gap between the page
        // number and the right column is the largest vertical gap.
        List<IObject> objects = new ArrayList<>();
        objects.add(line("L", 50.112, 78.201, 286.366, 718.246));
        objects.add(line("R", 308.862, 580.713, 545.115, 718.246));
        objects.add(line("PN", 292.157, 48.047, 303.066, 61.192));
        assertEquals(Arrays.asList("L", "R", "PN"), sortedLabels(objects));
    }

    @Test
    void ruleAcrossThePageBetweenTwoSectionsKeepsTheSections() {
        List<IObject> objects = new ArrayList<>();
        objects.add(lineArt(48.06, 500.842, 703.14, 501.382));
        objects.add(line("A-L", 48.18, 420, 365.69, 488.25));
        objects.add(line("A-R", 385.5, 420, 703.03, 488.25));
        objects.add(lineArt(48.06, 400, 703.14, 400.5));
        objects.add(line("B-L", 48.18, 300, 365.69, 390));
        objects.add(line("B-R", 385.5, 300, 703.03, 390));
        objects.add(lineArt(48.06, 27.592, 703.14, 43.402));
        assertEquals(Arrays.asList("A-L", "A-R", "B-L", "B-R"), sortedLabels(objects));
    }

    @Test
    void keyValueRowsWithoutTheLastValueStayRowByRow() {
        List<IObject> objects = new ArrayList<>();
        objects.add(line("K1", 48.18, 476.73, 200, 488.25));
        objects.add(line("V1", 219.74, 476.73, 365.69, 488.25));
        objects.add(line("K2", 48.18, 436.61, 200, 448.13));
        objects.add(line("V2", 219.74, 436.61, 365.69, 448.13));
        objects.add(line("K3", 48.18, 396.49, 200, 408.01));
        objects.add(line("V3", 219.74, 396.49, 365.69, 408.01));
        objects.add(line("K4", 48.18, 356.37, 200, 367.89));
        assertEquals(Arrays.asList("K1", "V1", "K2", "V2", "K3", "V3", "K4"), sortedLabels(objects));
    }

    @Test
    void gapInsideTheRightColumnAfterTheLeftColumnEndedKeepsTheOrder() {
        List<IObject> objects = new ArrayList<>();
        objects.add(line("L1", 48.18, 420, 365.69, 488.25));
        objects.add(line("L2", 48.18, 300, 365.69, 410));
        objects.add(line("R1", 385.5, 420, 703.03, 488.25));
        objects.add(line("R2", 385.5, 200, 703.03, 410));
        objects.add(line("R3", 385.5, 60, 703.03, 171.4));
        assertEquals(Arrays.asList("L1", "L2", "R1", "R2", "R3"), sortedLabels(objects));
    }

    @Test
    void headingAboveTheLeftColumnComesFirst() {
        List<IObject> objects = new ArrayList<>();
        objects.add(line("H", 48.18, 508.25, 276.8, 520));
        objects.add(line("L1", 48.18, 300, 365.69, 479.65));
        objects.add(line("L2", 48.18, 60, 365.69, 290));
        objects.add(line("R1", 385.5, 300, 703.03, 479.65));
        objects.add(line("R2", 385.5, 60, 703.03, 290));
        assertEquals(Arrays.asList("H", "L1", "L2", "R1", "R2"), sortedLabels(objects));
    }

    @Test
    void noteBelowLevelColumnsComesAfterBothColumns() {
        List<IObject> objects = new ArrayList<>();
        objects.add(line("L1", 48.18, 300, 365.69, 488.25));
        objects.add(line("L2", 48.18, 150, 365.69, 290));
        objects.add(line("R1", 385.5, 300, 703.03, 488.25));
        objects.add(line("R2", 385.5, 150, 703.03, 290));
        objects.add(line("N", 48.18, 100, 365.69, 121.4));
        assertEquals(Arrays.asList("L1", "L2", "R1", "R2", "N"), sortedLabels(objects));
    }

    @Test
    void notesBelowColumnsThatEndAlmostLevelComeAfterBothColumns() {
        // The right column ends 5 pt below the left column, in the gap above the first note.
        List<IObject> objects = new ArrayList<>();
        objects.add(line("L1", 48.18, 300, 365.69, 488.25));
        objects.add(line("L2", 48.18, 150, 365.69, 290));
        objects.add(line("R1", 385.5, 300, 703.03, 488.25));
        objects.add(line("R2", 385.5, 145, 703.03, 290));
        objects.add(line("N1", 48.18, 100, 365.69, 121.4));
        objects.add(line("N2", 48.18, 40, 365.69, 70));
        assertEquals(Arrays.asList("L1", "L2", "R1", "R2", "N1", "N2"), sortedLabels(objects));
    }

    /** The labels of the text lines in the sorted order. Line art has no text in the output. */
    private static List<String> sortedLabels(List<IObject> objects) {
        List<String> labels = new ArrayList<>();
        for (IObject object : XYCutPlusPlusSorter.sort(objects)) {
            if (object instanceof TextLine) {
                labels.add(label(object));
            }
        }
        return labels;
    }

    private static String label(IObject object) {
        return object instanceof TextLine ? ((TextLine) object).getTextChunks().get(0).getValue() : null;
    }

    private static List<String> withoutPageNumber(List<String> labels) {
        List<String> result = new ArrayList<>(labels);
        result.remove("PN");
        return result;
    }

    /** A text line with the box (leftX, bottomY, rightX, topY) and the label as its text. */
    private static IObject line(String label, double leftX, double bottomY, double rightX, double topY) {
        BoundingBox box = new BoundingBox(0, leftX, bottomY, rightX, topY);
        return new TextLine(new TextChunk(box, label, 10, rightX - leftX));
    }

    /** Line art (a rule or a box) with the box (leftX, bottomY, rightX, topY). */
    private static IObject lineArt(double leftX, double bottomY, double rightX, double topY) {
        return new LineArtChunk(new BoundingBox(0, leftX, bottomY, rightX, topY));
    }
}

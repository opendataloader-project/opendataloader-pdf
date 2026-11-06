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
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #396: a page header comes before the page body, and a page footer comes after it. Headers and footers do not take
 * part in the cuts of the page body.
 *
 * <p>The boxes are the boxes of an A4 page with two columns: the left column at x 51-292, the right column at
 * x 303-544 (gutter 11 pt).
 */
class Issue396HeaderAndFooterOrderTest {

    @BeforeEach
    void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
    }

    /**
     * A page footer below the left column comes after both columns, also when the last line of the right column
     * starts below the top of the footer.
     */
    @Test
    void footerComesAfterTheBodyAlsoBesideTheEndOfTheRightColumn() {
        List<IObject> objects = new ArrayList<>();
        objects.add(line("L1", 51, 382.8, 292, 742.3));
        objects.add(line("L2", 51, 81.8, 292, 369.3));
        objects.add(line("R1", 303, 394.8, 544, 742.3));
        objects.add(line("R2", 303, 57.8, 544, 381.3));
        objects.add(line("R3", 303, 32.8, 414.2, 44.3));
        objects.add(headerOrFooter(SemanticType.FOOTER, 51, 40.0, 112.5, 50.4));
        assertEquals(Arrays.asList("L1", "L2", "R1", "R2", "R3", "FOOTER"), sortedLabels(objects),
            "the page footer is not after the page body");
    }

    /** A page header above the right column, nearer to the column than the width of the gutter, comes first. */
    @Test
    void headerAboveTheRightColumnComesFirst() {
        List<IObject> objects = new ArrayList<>();
        objects.add(headerOrFooter(SemanticType.HEADER, 400, 748, 544, 757));
        objects.add(line("L1", 51, 382.8, 292, 742.3));
        objects.add(line("L2", 51, 57.8, 292, 369.3));
        objects.add(line("R1", 303, 394.8, 544, 742.3));
        objects.add(line("R2", 303, 57.8, 544, 381.3));
        assertEquals(Arrays.asList("HEADER", "L1", "L2", "R1", "R2"), sortedLabels(objects),
            "the page header is not before the page body");
    }

    /** The labels of the text lines, and HEADER or FOOTER, in the sorted order. */
    private static List<String> sortedLabels(List<IObject> objects) {
        List<String> labels = new ArrayList<>();
        for (IObject object : XYCutPlusPlusSorter.sort(objects)) {
            if (object instanceof TextLine) {
                labels.add(((TextLine) object).getTextChunks().get(0).getValue());
            } else if (object instanceof SemanticHeaderOrFooter) {
                labels.add(((SemanticHeaderOrFooter) object).getSemanticType().name());
            }
        }
        return labels;
    }

    /** A text line with the box (leftX, bottomY, rightX, topY) and the label as its text. */
    private static IObject line(String label, double leftX, double bottomY, double rightX, double topY) {
        BoundingBox box = new BoundingBox(0, leftX, bottomY, rightX, topY);
        return new TextLine(new TextChunk(box, label, 10, rightX - leftX));
    }

    /** A page header or footer with the box (leftX, bottomY, rightX, topY). */
    private static IObject headerOrFooter(SemanticType type, double leftX, double bottomY, double rightX,
                                          double topY) {
        SemanticHeaderOrFooter headerOrFooter = new SemanticHeaderOrFooter(type);
        headerOrFooter.setBoundingBox(new BoundingBox(0, leftX, bottomY, rightX, topY));
        return headerOrFooter;
    }
}

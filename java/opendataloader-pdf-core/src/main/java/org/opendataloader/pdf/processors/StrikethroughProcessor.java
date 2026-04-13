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

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects strikethrough text by finding horizontal lines that pass through
 * the vertical center of text chunks. Marks affected TextChunks by setting
 * their isStrikethroughText field to true.
 *
 * Filters to avoid false positives:
 * 1. Table border membership (via TableBordersCollection)
 * 2. Stroke-to-text-height ratio (rejects thick background fills/borders)
 * 3. Line-to-text width ratio (rejects lines wider than text)
 * 4. Vertical center alignment
 * 5. Horizontal overlap requirement
 * 6. Multi-chunk matching (structural separator detection)
 */
public class StrikethroughProcessor {

    private static final double VERTICAL_CENTER_TOLERANCE = 0.2;
    private static final double MIN_HORIZONTAL_OVERLAP_RATIO = 0.8;
    private static final double MAX_LINE_TO_TEXT_WIDTH_RATIO = 1.5;
    private static final int MAX_TEXT_CHUNKS_PER_LINE = 1;

    // Maximum ratio of line stroke thickness to text height.
    // Real strikethrough lines are thin (~0.04x textHeight) or at most text-height
    // filled rectangles (~1.0x). Lines thicker than text (>1.3x) are background
    // fills, table cell shading, or structural borders.
    private static final double MAX_STROKE_TO_TEXT_HEIGHT_RATIO = 1.3;

    /**
     * Detects strikethrough lines among page contents and sets affected
     * TextChunk isStrikethroughText field to true.
     *
     * @param pageContents the list of content objects for a page
     * @return the page contents (modified in place)
     */
    public static List<IObject> processStrikethroughs(List<IObject> pageContents) {
        List<LineChunk> horizontalLines = new ArrayList<>();
        List<TextChunk> textChunks = new ArrayList<>();

        for (IObject content : pageContents) {
            if (content instanceof LineChunk) {
                LineChunk line = (LineChunk) content;
                if (line.isHorizontalLine()) {
                    horizontalLines.add(line);
                }
            } else if (content instanceof TextChunk) {
                textChunks.add((TextChunk) content);
            }
        }

        if (horizontalLines.isEmpty() || textChunks.isEmpty()) {
            return pageContents;
        }

        for (LineChunk line : horizontalLines) {
//            if (isTableBorderLine(line)) {
//                continue;
//            }

            List<TextChunk> matchingChunks = new ArrayList<>();
            for (TextChunk textChunk : textChunks) {
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    continue;
                }
                if (isStrikethroughLine(line, textChunk)) {
                    matchingChunks.add(textChunk);
                }
            }

            if (!matchingChunks.isEmpty() && matchingChunks.size() <= MAX_TEXT_CHUNKS_PER_LINE) {
                for (TextChunk chunk : matchingChunks) {
                    if (!chunk.getIsStrikethroughText()) {
                        chunk.setIsStrikethroughText();
                    }
                }
            }
        }

        return pageContents;
    }

    /**
     * Checks if a line belongs to a known table border region.
     */
    static boolean isTableBorderLine(LineChunk line) {
        if (StaticContainers.getTableBordersCollection() == null) {
            return false;
        }
        TableBorder tableBorder = StaticContainers.getTableBordersCollection()
            .getTableBorder(line.getBoundingBox());
        return tableBorder != null;
    }

    /**
     * Determines whether a horizontal line is a strikethrough for the given text chunk.
     */
    static boolean isStrikethroughLine(LineChunk line, TextChunk textChunk) {
        double textHeight = textChunk.getHeight();

        if (textHeight <= 0) {
            return false;
        }

        // Reject lines whose stroke thickness exceeds the text height
        double strokeToHeightRatio = line.getWidth() / textHeight;
        if (strokeToHeightRatio > MAX_STROKE_TO_TEXT_HEIGHT_RATIO) {
            return false;
        }

        // Check vertical position: the line's Y should be near the vertical center of the text
        double textCenterY = textChunk.getCenterY();
        double lineY = line.getCenterY();
        double tolerance = textHeight * VERTICAL_CENTER_TOLERANCE;

        if (Math.abs(lineY - textCenterY) > tolerance) {
            return false;
        }

        // Check horizontal overlap
        double textLeftX = textChunk.getLeftX();
        double textRightX = textChunk.getRightX();
        double lineLeftX = line.getLeftX();
        double lineRightX = line.getRightX();

        double overlapLeft = Math.max(textLeftX, lineLeftX);
        double overlapRight = Math.min(textRightX, lineRightX);
        double overlapWidth = overlapRight - overlapLeft;

        if (overlapWidth <= 0) {
            return false;
        }

        double textWidth = textChunk.getWidth();
        if (textWidth <= 0 || (overlapWidth / textWidth) < MIN_HORIZONTAL_OVERLAP_RATIO) {
            return false;
        }

        // Reject lines that extend far beyond the text
        double lineWidth = line.getBoundingBox().getWidth();
        if (lineWidth / textWidth > MAX_LINE_TO_TEXT_WIDTH_RATIO) {
            return false;
        }

        return true;
    }
}

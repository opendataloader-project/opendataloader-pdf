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
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;

/**
 * Detects strikethrough and underlined text by finding horizontal line or line-art rules that
 * pass through the vertical center or baseline of text chunks. Marks affected TextChunks by
 * setting their isStrikethroughText or isUnderlinedText fields to true.
 *
 * Filters to avoid false positives:
 * 1. Rule-to-text-height ratio (rejects thick background fills/borders)
 * 2. Rule-to-text width ratio (rejects rules wider than text)
 * 3. Vertical center or baseline alignment
 * 4. Horizontal overlap requirement
 * 5. Multi-chunk matching using the combined text width
 */
public class TextDecorationProcessor {

    private static final double VERTICAL_CENTER_TOLERANCE = 0.2;
    private static final double MIN_HORIZONTAL_OVERLAP_RATIO = 0.8;
    private static final double MAX_LINE_TO_TEXT_WIDTH_RATIO = 1.5;

    // Real strikethrough marks are thin rules. Text-height rectangles commonly
    // come from glyph bounds, filled artifacts, backgrounds, or table artwork.
    private static final double MAX_RULE_THICKNESS = 2.0;
    private static final double MAX_RULE_TO_TEXT_HEIGHT_RATIO = 0.25;

    /**
     * Detects strikethrough and underlined lines among page contents and sets affected
     * TextChunk isStrikethroughText or isUnderlinedText field to true.
     *
     * @param pageContents the list of content objects for a page
     * @param pageNumber   the number of a page
     * @param detectStrikethrough wether strikethrough text should be detected
     */
    public static void processStrikethroughAndUnderlinedText(List<IObject> pageContents, int pageNumber, boolean detectStrikethrough) {
        if (pageContents == null) {
            return;
        }

        SortedSet<LineChunk> horizontalLines = StaticContainers.getLinesCollection() != null ?
            StaticContainers.getLinesCollection().getHorizontalLines(pageNumber) : Collections.emptySortedSet();
        List<HorizontalRuleCandidate> horizontalRules = new ArrayList<>();

        for (LineChunk lineChunk : horizontalLines) {
            HorizontalRuleCandidate rule = HorizontalRuleCandidate.fromLineChunk(lineChunk);
            if (rule != null) {
                horizontalRules.add(rule);
            }
        }

        List<TextChunk> textChunks = new ArrayList<>();
        for (IObject content : pageContents) {
//            if (content instanceof LineArtChunk) {
//                HorizontalRuleCandidate rule = HorizontalRuleCandidate.fromLineArtChunk((LineArtChunk) content);
//                if (rule != null) {
//                    horizontalRules.add(rule);
//                }
//            } else
            if (content instanceof TextChunk) {
                textChunks.add((TextChunk) content);
            }
        }

        if (horizontalRules.isEmpty() || textChunks.isEmpty()) {
            return;
        }

        for (HorizontalRuleCandidate rule : horizontalRules) {
            List<TextChunk> strikethroughMatches = new ArrayList<>();
            List<TextChunk> underlineMatches = new ArrayList<>();

            for (TextChunk textChunk : textChunks) {
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    continue;
                }
                if (detectStrikethrough && isStrikethroughRule(rule, textChunk)) {
                    strikethroughMatches.add(textChunk);
                }
                if (isUnderlineRule(rule, textChunk)) {
                    underlineMatches.add(textChunk);
                }
            }

            if (detectStrikethrough && isValidMatch(rule, strikethroughMatches)) {
                for (TextChunk chunk : strikethroughMatches) {
                    if (!chunk.getIsStrikethroughText()) {
                        chunk.setIsStrikethroughText();
                    }
                }
            }

            if (isValidMatch(rule, underlineMatches)) {
                for (TextChunk chunk : underlineMatches) {
                    if (!chunk.getIsUnderlinedText()) {
                        chunk.setIsUnderlinedText();
                    }
                }
            }
        }

    }

    /**
     * Determines whether a horizontal line is a strikethrough for the given text chunk.
     */
    static boolean isStrikethroughLine(LineChunk line, TextChunk textChunk) {
        HorizontalRuleCandidate rule = HorizontalRuleCandidate.fromLineChunk(line);
        return rule != null && isStrikethroughRule(rule, textChunk) && isValidMatch(rule, Collections.singletonList(textChunk));
    }

    /**
     * Determines whether a line-art rectangle is a strikethrough for the given text chunk.
     */
//    static boolean isStrikethroughLineArt(LineArtChunk lineArt, TextChunk textChunk) {
//        HorizontalRuleCandidate rule = HorizontalRuleCandidate.fromLineArtChunk(lineArt);
//        // Line-art rectangles are also used for table/background artwork, so
//        // apply the width-ratio guard even for direct single-chunk checks.
//        return rule != null && isStrikethroughRule(rule, textChunk) && isValidMatch(rule, List.of(textChunk));
//    }

    /**
     * Determines whether a horizontal line is an underlined for the given text chunk.
     */
    public static boolean isUnderlineLine(LineChunk line, TextChunk textChunk) {
        HorizontalRuleCandidate rule = HorizontalRuleCandidate.fromLineChunk(line);
        return rule != null && isUnderlineRule(rule, textChunk) && isValidMatch(rule, Collections.singletonList(textChunk));
    }

    /**
     * Determines whether a line-art rectangle is an underlined for the given text chunk.
     */
//    public static boolean isUnderlineLineArt(LineArtChunk lineArt, TextChunk textChunk) {
//        HorizontalRuleCandidate rule = HorizontalRuleCandidate.fromLineArtChunk(lineArt);
//        return rule != null && isUnderlineRule(rule, textChunk) && isValidMatch(rule, List.of(textChunk));
//    }

//    private static boolean isHorizontalLineArt(LineArtChunk lineArt) {
//        BoundingBox bbox = lineArt.getBoundingBox();
//        return bbox != null && bbox.getWidth() > bbox.getHeight();
//    }

    private static boolean isStrikethroughRule(HorizontalRuleCandidate rule, TextChunk textChunk) {
        if (rule == null || textChunk == null) {
            return false;
        }

        double textHeight = textChunk.getHeight();

        if (textHeight <= 0) {
            return false;
        }

        // LineChunk thickness is stroke width; LineArtChunk thickness is rectangle
        // height. In both cases, strikethrough marks should stay thin relative to text.
        double maxRuleThickness = Math.min(MAX_RULE_THICKNESS, textHeight * MAX_RULE_TO_TEXT_HEIGHT_RATIO);
        if (rule.thickness > maxRuleThickness) {
            return false;
        }

        // Check vertical position: the line's Y should be near the vertical center of the text
        double textCenterY = textChunk.getCenterY();
        double lineY = rule.centerY;
        double tolerance = textHeight * VERTICAL_CENTER_TOLERANCE;

        if (Math.abs(lineY - textCenterY) > tolerance) {
            return false;
        }

        // Check horizontal overlap
        double textLeftX = textChunk.getLeftX();
        double textRightX = textChunk.getRightX();
        double lineLeftX = rule.leftX;
        double lineRightX = rule.rightX;

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

        return true;
    }

    private static boolean isUnderlineRule(HorizontalRuleCandidate rule, TextChunk textChunk) {
        if (rule == null || textChunk == null) {
            return false;
        }
        double textHeight = textChunk.getHeight();
        if (textHeight <= 0) return false;

        // Thickness: line width must be less than eps[2] * textHeight
        // (eps[2] = 0.3, i.e. 30% of text height)
        if (rule.thickness >= NodeUtils.UNDERLINED_TEXT_EPSILONS[2] * textHeight) {
            return false;
        }

        // Vertical position: line must be between the baseline and
        // (baseline - eps[1] * textHeight)
        double baseline = textChunk.getBaseLine();
        double lineY = rule.centerY;
        double lowerBound = baseline - NodeUtils.UNDERLINED_TEXT_EPSILONS[1] * textHeight;
        // Assuming Y increases upward, baseline is above the descender zone.
        // lineY should be <= baseline and >= lowerBound.
        if (lineY > baseline || lineY < lowerBound) {
            return false;
        }

        // Horizontal overlap: at least eps[0] * textWidth overlap
        double textLeftX = textChunk.getLeftX();
        double textRightX = textChunk.getRightX();
        double overlap = Math.min(rule.rightX - textLeftX, textRightX - rule.leftX);
        if (overlap <= NodeUtils.UNDERLINED_TEXT_EPSILONS[0] * textChunk.getWidth()) {
            return false;
        }

        return true;
    }

    private static boolean isValidMatch(HorizontalRuleCandidate rule, List<TextChunk> matchingChunks) {
        if (matchingChunks.isEmpty()) {
            return false;
        }

        // Validate against the combined span of all matched chunks. This accepts
        // split text on one visual line while still rejecting table separators
        // and background rules that extend far past the text group.
        double textGroupWidth = 0.0;
        for (TextChunk chunk : matchingChunks) {
            textGroupWidth += chunk.getWidth();
        }
        return textGroupWidth > 0 && rule.width / textGroupWidth <= MAX_LINE_TO_TEXT_WIDTH_RATIO;
    }

    /**
     * Normalized geometry for either LineChunk or LineArtChunk.
     */
    private static class HorizontalRuleCandidate {
        private final double leftX;
        private final double rightX;
        private final double centerY;
        private final double width;
        private final double thickness;

        private HorizontalRuleCandidate(double leftX, double rightX, double centerY, double width, double thickness) {
            this.leftX = leftX;
            this.rightX = rightX;
            this.centerY = centerY;
            this.width = width;
            this.thickness = thickness;
        }

        private static HorizontalRuleCandidate fromLineChunk(LineChunk line) {
            if (line == null || line.getBoundingBox() == null || !line.isHorizontalLine()) {
                return null;
            }
            BoundingBox bbox = line.getBoundingBox();
            return new HorizontalRuleCandidate(line.getLeftX(), line.getRightX(), line.getCenterY(),
                bbox.getWidth(), line.getWidth());
        }

//        private static HorizontalRuleCandidate fromLineArtChunk(LineArtChunk lineArt) {
//            if (lineArt == null || !isHorizontalLineArt(lineArt)) {
//                return null;
//            }
//            BoundingBox bbox = lineArt.getBoundingBox();
//            return new HorizontalRuleCandidate(lineArt.getLeftX(), lineArt.getRightX(), lineArt.getCenterY(),
//                bbox.getWidth(), bbox.getHeight());
//        }
    }
}

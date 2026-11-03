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

import org.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.TextAlignment;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.CaptionUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.*;

public class ParagraphProcessor {

    public static final double DIFFERENT_LINES_PROBABILITY = 0.75;
    /**
     * Two lines whose baselines are more than this many times the usual distance apart have an empty line
     * between them, so they are in two paragraphs (#876).
     */
    private static final double EMPTY_LINE_RATIO = 1.5;
    /** The width of the classes of the distances between baselines, relative to the font size. */
    private static final double LEADING_CLASS_WIDTH = 0.05;
    /** The usual distance must occur this many times on the page, or the font size has none. */
    private static final int MIN_USUAL_LEADING_COUNT = 3;
    /** Distances between baselines out of this range, relative to the font size, are not counted. */
    private static final double MIN_LEADING = 0.8;
    private static final double MAX_LEADING = 3;
    /** Two lines start at the same x when their left ends are this many font sizes apart or less. */
    private static final double SAME_X_RATIO = 0.3;
    /** The largest distance between baselines of single spacing, relative to the font size. */
    private static final double MAX_SINGLE_LEADING = 1.6;
    /** The usual distances between baselines on the page, by font size class; a page runs on one thread. */
    private static final ThreadLocal<Map<Long, Double>> USUAL_LEADINGS = ThreadLocal.withInitial(HashMap::new);

    public static List<IObject> processParagraphs(List<IObject> contents) {
        return processParagraphs(contents, false);
    }

    /**
     * Detects the paragraphs of the body text of a page. A line after an empty line starts a paragraph there
     * (#876); the lines in a table cell, a list item, a header, or a footer keep the rules of
     * {@link #processParagraphs(List)}.
     */
    public static List<IObject> processPageParagraphs(List<IObject> contents) {
        return processParagraphs(contents, true);
    }

    private static List<IObject> processParagraphs(List<IObject> contents, boolean breakAtEmptyLines) {
        DocumentProcessor.setIndexesForContentsList(contents);
        List<TextBlock> blocks = new ArrayList<>();
        for (IObject content : contents) {
            if (content instanceof TextLine) {
                blocks.add(new TextBlock((TextLine) content));
            }
        }
        USUAL_LEADINGS.set(breakAtEmptyLines ? getUsualLeadings(blocks) : new HashMap<>());
        try {
            blocks = detectParagraphsWithJustifyAlignments(blocks);
            blocks = detectFirstAndLastLinesOfParagraphsWithJustifyAlignments(blocks);
            blocks = detectParagraphsWithLeftAlignments(blocks, true);
            blocks = detectParagraphsWithLeftAlignments(blocks, false);
            blocks = detectFirstLinesOfParagraphWithLeftAlignments(blocks);
            blocks = detectParagraphsWithCenterAlignments(blocks);
            blocks = detectParagraphsWithRightAlignments(blocks);
            blocks = detectTwoLinesParagraphs(blocks);
            blocks = processOtherLines(blocks);
        } finally {
            USUAL_LEADINGS.remove();
        }
        return getContentsWithDetectedParagraphs(contents, blocks);
    }

    /**
     * The usual distance between the baselines of the lines of the page, for each font size class, relative to
     * the font size. It is the most common distance between two lines that follow each other, one below the
     * other, in that font size, among the distances of single spacing that occur at least
     * {@link #MIN_USUAL_LEADING_COUNT} times. On a page of short paragraphs, the distance with an empty line
     * occurs more often than the single spacing, so it is not the usual one (#876). A page with no such single
     * spacing, as a double-spaced page, takes the most common distance.
     */
    private static Map<Long, Double> getUsualLeadings(List<TextBlock> lines) {
        Map<Long, Map<Long, Integer>> counts = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            TextLine above = lines.get(i - 1).getLastLine();
            TextLine below = lines.get(i).getFirstLine();
            double fontSize = above.getFontSize();
            double distance = above.getBaseLine() - below.getBaseLine();
            if (fontSize > 0 && NodeUtils.areCloseNumbers(fontSize, below.getFontSize(), 1e-1)
                    && distance > MIN_LEADING * fontSize && distance < MAX_LEADING * fontSize
                    && BoundingBox.areHorizontalOverlapping(above.getBoundingBox(), below.getBoundingBox())) {
                counts.computeIfAbsent(getFontSizeClass(fontSize), size -> new HashMap<>())
                    .merge(Math.round(distance / fontSize / LEADING_CLASS_WIDTH), 1, Integer::sum);
            }
        }
        Map<Long, Double> leadings = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> size : counts.entrySet()) {
            Long single = getMostCommonLeading(size.getValue(), MAX_SINGLE_LEADING);
            Long usual = single != null ? single : getMostCommonLeading(size.getValue(), MAX_LEADING);
            if (usual != null) {
                leadings.put(size.getKey(), usual * LEADING_CLASS_WIDTH);
            }
        }
        return leadings;
    }

    /** The most common distance class up to the maximum that occurs often enough, the smaller one of a tie. */
    private static Long getMostCommonLeading(Map<Long, Integer> counts, double maxLeading) {
        Long usual = null;
        for (Map.Entry<Long, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < MIN_USUAL_LEADING_COUNT || entry.getKey() * LEADING_CLASS_WIDTH > maxLeading) {
                continue;
            }
            if (usual == null || entry.getValue() > counts.get(usual)
                    || (entry.getValue().equals(counts.get(usual)) && entry.getKey() < usual)) {
                usual = entry.getKey();
            }
        }
        return usual;
    }

    private static long getFontSizeClass(double fontSize) {
        return Math.round(fontSize * 2);
    }

    /**
     * Whether an empty line is between the lines: they start at the same x, and they are farther apart than the
     * lines of the page usually are. Lines that start at different x, as a centered formula and the text after
     * it, keep the other rules: their reading order comes from the paragraph.
     */
    private static boolean isEmptyLineBetween(TextBlock previousBlock, TextBlock nextBlock) {
        TextLine above = previousBlock.getLastLine();
        TextLine below = nextBlock.getFirstLine();
        Double usualLeading = USUAL_LEADINGS.get().get(getFontSizeClass(above.getFontSize()));
        if (usualLeading == null
                || Math.abs(above.getLeftX() - below.getLeftX()) > SAME_X_RATIO * above.getFontSize()) {
            return false;
        }
        double distance = above.getBaseLine() - below.getBaseLine();
        return distance > EMPTY_LINE_RATIO * usualLeading * above.getFontSize();
    }

    private static List<IObject> getContentsWithDetectedParagraphs(List<IObject> contents, List<TextBlock> blocks) {
        List<IObject> newContents = new ArrayList<>();
        Iterator<TextBlock> iterator = blocks.iterator();
        TextBlock currentBlock = iterator.hasNext() ? iterator.next() : null;
        Integer currentIndex = currentBlock != null ? currentBlock.getFirstLine().getIndex() : null;
        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (!(content instanceof TextLine)) {
                newContents.add(content);
            } else if (Objects.equals(currentIndex, index)) {
                newContents.add(createParagraphFromTextBlock(currentBlock));
                currentBlock = iterator.hasNext() ? iterator.next() : null;
                currentIndex = currentBlock != null ? currentBlock.getFirstLine().getIndex() : null;
            }
        }
        return newContents;
    }

    private static List<TextBlock> detectParagraphsWithJustifyAlignments(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
                double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
                if (textAlignment == TextAlignment.JUSTIFY && probability > DIFFERENT_LINES_PROBABILITY &&
                    areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.JUSTIFY);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static List<TextBlock> detectParagraphsWithCenterAlignments(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (areLinesOfParagraphsWithCenterAlignments(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.CENTER);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static boolean areLinesOfParagraphsWithCenterAlignments(TextBlock previousBlock, TextBlock nextBlock) {
        TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
        if (textAlignment != TextAlignment.CENTER) {
            return false;
        }
        double probability = getDifferentLinesProbability(previousBlock, nextBlock, true, false);
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        return true;
    }

    private static List<TextBlock> detectFirstAndLastLinesOfParagraphsWithJustifyAlignments(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
                double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
                if (isFirstLineOfBlock(previousBlock, nextBlock, textAlignment, probability)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.JUSTIFY);
                    previousBlock.setHasStartLine(true);
                    previousBlock.setHasEndLine(nextBlock.isHasEndLine());
                } else if (isLastLineOfBlock(previousBlock, nextBlock, textAlignment, probability)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setHasEndLine(true);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static List<TextBlock> detectParagraphsWithLeftAlignments(List<TextBlock> textBlocks, boolean checkStyle) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (areLinesOfParagraphsWithLeftAlignments(previousBlock, nextBlock, checkStyle)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.LEFT);
                    previousBlock.setHasEndLine(false);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static boolean areLinesOfParagraphsWithRightAlignments(TextBlock previousBlock, TextBlock nextBlock) {
        TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
        if (textAlignment != TextAlignment.RIGHT) {
            return false;
        }
        double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        if (previousBlock.getLinesNumber() != 1 && previousBlock.getTextAlignment() != TextAlignment.RIGHT) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        if (nextBlock.getLinesNumber() != 1 && nextBlock.getTextAlignment() != TextAlignment.RIGHT) {
            return false;
        }
        return true;
    }

    private static boolean areLinesOfParagraphsWithLeftAlignments(TextBlock previousBlock, TextBlock nextBlock, boolean checkStyle) {
        TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
        if (textAlignment != TextAlignment.LEFT) {
            return false;
        }
        boolean haveSameStyle = TextChunkUtils.areTextChunksHaveSameStyle(previousBlock.getLastLine().getFirstTextChunk(),
            nextBlock.getFirstLine().getFirstTextChunk());
        if (checkStyle && !haveSameStyle) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        if (BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine())) {
            return false;
        }
        boolean areShouldBeCloseLines = false;
        if (previousBlock.getLinesNumber() != 1) {
            if (previousBlock.getTextAlignment() == TextAlignment.JUSTIFY) {
                if (!haveSameStyle) {
                    return false;
                }
                areShouldBeCloseLines = true;
            } else if (previousBlock.getTextAlignment() != TextAlignment.LEFT) {
                return false;
            }
        }
        if (nextBlock.getLinesNumber() != 1) {
            if (nextBlock.getTextAlignment() == TextAlignment.JUSTIFY) {
                if (!haveSameStyle) {
                    return false;
                }
                areShouldBeCloseLines = true;
            } else if (nextBlock.getTextAlignment() != TextAlignment.LEFT) {
                return false;
            }
        }
        double probability = getDifferentLinesProbability(previousBlock, nextBlock, true, areShouldBeCloseLines);
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        return true;
    }

    private static List<TextBlock> detectFirstLinesOfParagraphWithLeftAlignments(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (isFirstLineOfParagraphWithLeftAlignment(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.LEFT);
                    previousBlock.setHasStartLine(true);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static boolean isFirstLineOfParagraphWithLeftAlignment(TextBlock previousBlock, TextBlock nextBlock) {
        double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
        if (previousBlock.getLinesNumber() != 1) {
            return false;
        }
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        if (BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine())) {
            return false;
        }
        if (nextBlock.isHasStartLine()) {
            return false;
        }
        if (nextBlock.getTextAlignment() != TextAlignment.LEFT) {
            return false;
        }
        if (!CaptionUtils.areOverlapping(previousBlock.getLastLine(), nextBlock.getFirstLine().getBoundingBox())) {
            return false;
        }
        return true;
    }

    private static List<TextBlock> detectTwoLinesParagraphs(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (isTwoLinesParagraph(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.LEFT);
                    previousBlock.setHasStartLine(true);
                    previousBlock.setHasEndLine(true);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static boolean isTwoLinesParagraph(TextBlock previousBlock, TextBlock nextBlock) {
        if (previousBlock.getLinesNumber() != 1 || nextBlock.getLinesNumber() != 1) {
            return false;
        }
        double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        if (BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine())) {
            return false;
        }
        if (previousBlock.getLastLine().getLeftX() < nextBlock.getFirstLine().getLeftX() ||
            previousBlock.getLastLine().getRightX() < nextBlock.getFirstLine().getRightX()) {
            return false;
        }
        return true;
    }

    private static boolean isFirstLineOfBulletedParagraphWithLeftAlignment(TextBlock previousBlock, TextBlock nextBlock) {
        double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        if (previousBlock.getLinesNumber() != 1) {
            return false;
        }
        if (nextBlock.isHasStartLine()) {
            return false;
        }
        if (BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine())) {
            return false;
        }
        if (!BulletedParagraphUtils.isLabeledLine(previousBlock.getFirstLine())) {
            return false;
        }
        if (previousBlock.getLastLine().getLeftX() > nextBlock.getFirstLine().getLeftX()) {
            return false;
        }
        if (nextBlock.getTextAlignment() != TextAlignment.LEFT && nextBlock.getLinesNumber() != 1) {
            return false;
        }
        if (!CaptionUtils.areOverlapping(previousBlock.getLastLine(), nextBlock.getFirstLine().getBoundingBox())) {
            return false;
        }
        return true;
    }

    private static List<TextBlock> detectParagraphsWithRightAlignments(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (areLinesOfParagraphsWithRightAlignments(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.RIGHT);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static List<TextBlock> detectBulletedParagraphsWithLeftAlignments(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (isFirstLineOfBulletedParagraphWithLeftAlignment(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.LEFT);
                    previousBlock.setHasStartLine(true);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static List<TextBlock> processOtherLines(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (isOneParagraph(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static boolean isOneParagraph(TextBlock previousBlock, TextBlock nextBlock) {
        if (!areCloseStyle(previousBlock, nextBlock)) {
            return false;
        }
        double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        if (BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine())) {
            return false;
        }
        if (!CaptionUtils.areOverlapping(previousBlock.getLastLine(), nextBlock.getFirstLine().getBoundingBox())) {
            return false;
        }
        if (previousBlock.getLinesNumber() != 1 && previousBlock.getTextAlignment() != null) {
            return false;
        }
        if (nextBlock.getLinesNumber() != 1 && nextBlock.getTextAlignment() != null) {
            return false;
        }
        return true;
    }

    private static boolean isFirstLineOfBlock(TextBlock previousBlock, TextBlock nextBlock, TextAlignment textAlignment,
                                              double probability) {
        if (previousBlock.getLinesNumber() != 1) {
            return false;
        }
        if (textAlignment != TextAlignment.RIGHT) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        if (nextBlock.getTextAlignment() != TextAlignment.JUSTIFY) {
            return false;
        }
        if (nextBlock.isHasStartLine()) {
            return false;
        }
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        return true;
    }

    private static boolean isLastLineOfBlock(TextBlock previousBlock, TextBlock nextBlock, TextAlignment textAlignment,
                                             double probability) {
        if (nextBlock.getLinesNumber() != 1) {
            return false;
        }
        if (textAlignment != TextAlignment.LEFT) {
            return false;
        }
        if (!areTextBlocksHaveSameTextSize(previousBlock, nextBlock)) {
            return false;
        }
        if (previousBlock.getTextAlignment() != TextAlignment.JUSTIFY) {
            return false;
        }
        if (previousBlock.isHasEndLine()) {
            return false;
        }
        if (probability < DIFFERENT_LINES_PROBABILITY) {
            return false;
        }
        return true;
    }

    public static SemanticParagraph createParagraphFromTextBlock(TextBlock textBlock) {
        SemanticParagraph textParagraph = new SemanticParagraph();
        textParagraph.getColumns().add(new TextColumn());
        textParagraph.getLastColumn().getBlocks().add(textBlock);
        textParagraph.setBoundingBox(textBlock.getBoundingBox());
        textParagraph.setCorrectSemanticScore(1.0);
        textParagraph.setHiddenText(textBlock.isHiddenText());
        return textParagraph;
    }

    private static double getDifferentLinesProbability(TextBlock previousBlock, TextBlock nextBlock,
                                                       boolean areSupportNotSingleLines, boolean areShouldBeCloseLines) {
        if (previousBlock.isHiddenText() != nextBlock.isHiddenText() || isEmptyLineBetween(previousBlock, nextBlock)) {
            return 0;
        }
        if (previousBlock.getLinesNumber() == 1 && nextBlock.getLinesNumber() == 1) {
            return ChunksMergeUtils.mergeLeadingProbability(previousBlock.getLastLine(), nextBlock.getFirstLine());
        }
        if (previousBlock.getLinesNumber() == 1) {
            return ChunksMergeUtils.mergeLeadingProbability(previousBlock.getLastLine(), nextBlock, areShouldBeCloseLines);
        }
        if (nextBlock.getLinesNumber() == 1) {
            return ChunksMergeUtils.mergeLeadingProbability(previousBlock, nextBlock.getFirstLine(), areShouldBeCloseLines);
        }
        if (areSupportNotSingleLines) {
            return ChunksMergeUtils.mergeLeadingProbability(previousBlock, nextBlock);
        }
        return 0;
    }

    private static boolean areCloseStyle(TextBlock previousBlock, TextBlock nextBlock) {
        return NodeUtils.areCloseNumbers(previousBlock.getFontSize(), nextBlock.getFontSize(), 1e-1) &&
            NodeUtils.areCloseNumbers(previousBlock.getFirstLine().getFirstTextChunk().getFontWeight(),
                nextBlock.getFirstLine().getFirstTextChunk().getFontWeight(), 1e-1);
    }

    private static boolean areTextBlocksHaveSameTextSize(TextBlock firstBlock, TextBlock secondBlock) {
        for (Double textSize1 : firstBlock.getTextSizes()) {
            for (Double textSize2 : secondBlock.getTextSizes()) {
                if (NodeUtils.areCloseNumbers(textSize1, textSize2)) {
                    return true;
                }
            }
        }
        return false;
    }
}

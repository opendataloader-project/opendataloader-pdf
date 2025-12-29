/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.TextAlignment;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.CaptionUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

public class ParagraphProcessor {

    public static final double DIFFERENT_LINES_PROBABILITY = 0.75;

    public static List<IObject> processParagraphs(List<IObject> contents) {
        DocumentProcessor.setIndexesForContentsList(contents);
        List<TextBlock> blocks = new ArrayList<>();
        for (IObject content : contents) {
            if (content instanceof TextLine) {
                blocks.add(new TextBlock((TextLine) content));
            }
        }
        blocks = detectParagraphsWithJustifyAlignments(blocks);
        blocks = detectFirstAndLastLinesOfParagraphsWithJustifyAlignments(blocks);
        blocks = detectParagraphsWithLeftAlignments(blocks, true);
        blocks = detectParagraphsWithLeftAlignments(blocks, false);
        blocks = detectFirstLinesOfParagraphWithLeftAlignments(blocks);
        blocks = detectTwoLinesParagraphs(blocks);
        blocks = detectBulletedParagraphsWithLeftAlignments(blocks);
        blocks = detectParagraphsWithCenterAlignments(blocks);
        blocks = detectParagraphsWithRightAlignments(blocks);
        blocks = processOtherLines(blocks);
        return getContentsWithDetectedParagraphs(contents, blocks);
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
                if (textAlignment == TextAlignment.JUSTIFY && probability > DIFFERENT_LINES_PROBABILITY) {
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
                TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
                double probability = getDifferentLinesProbability(previousBlock, nextBlock, false, false);
                if (textAlignment == TextAlignment.CENTER && probability > DIFFERENT_LINES_PROBABILITY) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.CENTER);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
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
        if (previousBlock.getLastLine().getLeftX() <= nextBlock.getFirstLine().getLeftX()) {
            return false;
        }
        if (probability < DIFFERENT_LINES_PROBABILITY) {
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
        return previousBlock.getLinesNumber() == 1 && textAlignment == TextAlignment.RIGHT &&
                nextBlock.getTextAlignment() == TextAlignment.JUSTIFY && !nextBlock.isHasStartLine() && probability > DIFFERENT_LINES_PROBABILITY;
    }

    private static boolean isLastLineOfBlock(TextBlock previousBlock, TextBlock nextBlock, TextAlignment textAlignment,
                                             double probability) {
        return nextBlock.getLinesNumber() == 1 && textAlignment == TextAlignment.LEFT &&
                previousBlock.getTextAlignment() == TextAlignment.JUSTIFY && !previousBlock.isHasEndLine() && probability > DIFFERENT_LINES_PROBABILITY;
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
        if (previousBlock.isHiddenText() != nextBlock.isHiddenText()) {
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
}

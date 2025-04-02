/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.processors;

import com.duallab.layout.utils.BulletedParagraphUtils;
import com.duallab.wcag.algorithms.entities.IObject;
import com.duallab.wcag.algorithms.entities.SemanticParagraph;
import com.duallab.wcag.algorithms.entities.content.TextBlock;
import com.duallab.wcag.algorithms.entities.content.TextColumn;
import com.duallab.wcag.algorithms.entities.content.TextLine;
import com.duallab.wcag.algorithms.entities.enums.TextAlignment;
import com.duallab.wcag.algorithms.semanticalgorithms.utils.CaptionUtils;
import com.duallab.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;

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
        blocks = detectParagraphsWithLeftAlignments(blocks);
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
                double probability = getDifferentLinesProbability(previousBlock, nextBlock);
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
                double probability = getDifferentLinesProbability(previousBlock, nextBlock);
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
                double probability = getDifferentLinesProbability(previousBlock, nextBlock);
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

    private static List<TextBlock> detectParagraphsWithLeftAlignments(List<TextBlock> textBlocks) {
        List<TextBlock> newBlocks = new ArrayList<>();
        if (!textBlocks.isEmpty()) {
            newBlocks.add(textBlocks.get(0));
        }
        if (textBlocks.size() > 1) {
            for (int i = 1; i < textBlocks.size(); i++) {
                TextBlock previousBlock = newBlocks.get(newBlocks.size() - 1);
                TextBlock nextBlock = textBlocks.get(i);
                if (areLinesOfParagraphsWithLeftAlignments(previousBlock, nextBlock)) {
                    previousBlock.add(nextBlock.getLines());
                    previousBlock.setTextAlignment(TextAlignment.LEFT);
                } else {
                    newBlocks.add(nextBlock);
                }
            }
        }
        return newBlocks;
    }

    private static boolean areLinesOfParagraphsWithRightAlignments(TextBlock previousBlock, TextBlock nextBlock) {
        TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
        double probability = getDifferentLinesProbability(previousBlock, nextBlock);
        return textAlignment == TextAlignment.RIGHT && probability > DIFFERENT_LINES_PROBABILITY &&
                (previousBlock.getLinesNumber() == 1 || previousBlock.getTextAlignment() == TextAlignment.RIGHT) &&
                (nextBlock.getLinesNumber() == 1 || nextBlock.getTextAlignment() == TextAlignment.RIGHT);
    }

    private static boolean areLinesOfParagraphsWithLeftAlignments(TextBlock previousBlock, TextBlock nextBlock) {
        TextAlignment textAlignment = ChunksMergeUtils.getAlignment(previousBlock.getLastLine(), nextBlock.getFirstLine());
        double probability = getDifferentLinesProbability(previousBlock, nextBlock);
        return textAlignment == TextAlignment.LEFT && probability > DIFFERENT_LINES_PROBABILITY &&
                (previousBlock.getLinesNumber() == 1 || previousBlock.getTextAlignment() == TextAlignment.LEFT) &&
                (nextBlock.getLinesNumber() == 1 || nextBlock.getTextAlignment() == TextAlignment.LEFT) && 
                !BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine());
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
        double probability = getDifferentLinesProbability(previousBlock, nextBlock);
        return previousBlock.getLinesNumber() == 1 && previousBlock.getLastLine().getLeftX() > nextBlock.getFirstLine().getLeftX() &&
                CaptionUtils.areOverlapping(previousBlock.getLastLine(), nextBlock.getFirstLine().getBoundingBox()) &&
                nextBlock.getTextAlignment() == TextAlignment.LEFT && !nextBlock.isHasStartLine() && 
                probability > DIFFERENT_LINES_PROBABILITY && !BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine());
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
        double probability = getDifferentLinesProbability(previousBlock, nextBlock);
        return previousBlock.getLinesNumber() == 1 && nextBlock.getLinesNumber() == 1 && 
                previousBlock.getLastLine().getLeftX() > nextBlock.getFirstLine().getLeftX() &&
                previousBlock.getLastLine().getRightX() > nextBlock.getFirstLine().getRightX() &&
                probability > DIFFERENT_LINES_PROBABILITY && !BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine());
    }    

    private static boolean isFirstLineOfBulletedParagraphWithLeftAlignment(TextBlock previousBlock, TextBlock nextBlock) {
        double probability = getDifferentLinesProbability(previousBlock, nextBlock);
        return previousBlock.getLinesNumber() == 1 && previousBlock.getLastLine().getLeftX() < nextBlock.getFirstLine().getLeftX() &&
                CaptionUtils.areOverlapping(previousBlock.getLastLine(), nextBlock.getFirstLine().getBoundingBox()) &&
                (nextBlock.getTextAlignment() == TextAlignment.LEFT || nextBlock.getLinesNumber() == 1) && 
                !nextBlock.isHasStartLine() && probability > DIFFERENT_LINES_PROBABILITY &&
                BulletedParagraphUtils.isLabeledLine(previousBlock.getFirstLine()) && 
                !BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine());
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
        double probability = getDifferentLinesProbability(previousBlock, nextBlock);
        return CaptionUtils.areOverlapping(previousBlock.getLastLine(), nextBlock.getFirstLine().getBoundingBox()) &&
                probability > DIFFERENT_LINES_PROBABILITY &&
                (previousBlock.getLinesNumber() == 1 || previousBlock.getTextAlignment() == null) &&
                (nextBlock.getLinesNumber() == 1 || nextBlock.getTextAlignment() == null) && 
                !BulletedParagraphUtils.isLabeledLine(nextBlock.getFirstLine());
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
    
    private static SemanticParagraph createParagraphFromTextBlock(TextBlock textBlock) {
        SemanticParagraph textParagraph = new SemanticParagraph();
        textParagraph.getColumns().add(new TextColumn());
        textParagraph.getLastColumn().getBlocks().add(textBlock);
        textParagraph.getBoundingBox().union(textBlock.getBoundingBox());
        textParagraph.setCorrectSemanticScore(1.0);
        textParagraph.setHiddenText(textBlock.isHiddenText());
        return textParagraph;
    }

    private static double getDifferentLinesProbability(TextBlock previousBlock, TextBlock nextBlock) {
        if (previousBlock.isHiddenText() != nextBlock.isHiddenText()) {
            return 0;
        }
        if (previousBlock.getLinesNumber() == 1 && nextBlock.getLinesNumber() == 1) {
            return ChunksMergeUtils.mergeLeadingProbability(previousBlock.getLastLine(), nextBlock.getFirstLine());
        }
        if (previousBlock.getLinesNumber() == 1) {
            return ChunksMergeUtils.mergeLeadingProbability(previousBlock.getLastLine(), nextBlock);
        }
        if (nextBlock.getLinesNumber() == 1) {
            return ChunksMergeUtils.mergeLeadingProbability(previousBlock, nextBlock.getFirstLine());

        }
        return 0;
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ListUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

public class TextLineProcessor {

    private static final double ONE_LINE_PROBABILITY = 0.75;
    private static final Comparator<TextChunk> TEXT_CHUNK_COMPARATOR =
        Comparator.comparingDouble(o -> o.getBoundingBox().getLeftX());

    public static List<IObject> processTextLines(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        TextLine previousLine = new TextLine(new TextChunk(""));
        boolean isSeparateLine = false;
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    continue;
                }
                TextLine currentLine = new TextLine(textChunk);
                double oneLineProbability = ChunksMergeUtils.countOneLineProbability(new SemanticTextNode(), previousLine, currentLine);
                isSeparateLine |= (oneLineProbability < ONE_LINE_PROBABILITY) || previousLine.isHiddenText() != currentLine.isHiddenText();
                if (isSeparateLine) {
                    previousLine.setBoundingBox(new BoundingBox(previousLine.getBoundingBox()));
                    previousLine = currentLine;
                    newContents.add(previousLine);
                } else {
                    previousLine.add(currentLine);
                }
                isSeparateLine = false;
            } else {
                if (content instanceof TableBorder) {
                    isSeparateLine = true;
                }
                newContents.add(content);
            }
        }
        for (int i = 0; i < newContents.size(); i++) {
            if (newContents.get(i) instanceof TextLine) {
                TextLine textLine = (TextLine) newContents.get(i);
                textLine.getTextChunks().sort(TEXT_CHUNK_COMPARATOR);
                double threshold = textLine.getFontSize() * TextChunkUtils.TEXT_LINE_SPACE_RATIO;
                newContents.set(i, addSpaces(textLine, threshold));
            }
        }
        linkTextLinesWithConnectedLineArtBullet(newContents);
        return newContents;
    }

    private static TextLine addSpaces(TextLine textLine, double threshold) {
        List<TextChunk> textChunks = textLine.getTextChunks();
        Iterator<TextChunk> validation = textChunks.iterator();
        TextChunk prev = validation.next();
        double previousEnd = prev.getBoundingBox().getRightX();
        TextLine newLine = new TextLine();
        newLine.add(prev);
        while (validation.hasNext()) {
            TextChunk curr = validation.next();
            double currentStart = curr.getBoundingBox().getLeftX();
            if (currentStart - previousEnd > threshold) {
                BoundingBox spaceBBox = new BoundingBox(curr.getBoundingBox());
                spaceBBox.setLeftX(previousEnd);
                spaceBBox.setRightX(currentStart);
                TextChunk spaceChunk = new TextChunk(spaceBBox, " ", textLine.getFontSize(), textLine.getBaseLine());
                newLine.add(spaceChunk);
            }
            previousEnd = curr.getBoundingBox().getRightX();
            newLine.add(curr);
        }

        return newLine;
    }

    private static void linkTextLinesWithConnectedLineArtBullet(List<IObject> contents) {
        LineArtChunk lineArtChunk = null;
        for (IObject content : contents) {
            if (content instanceof LineArtChunk) {
                lineArtChunk = (LineArtChunk) content;
                continue;
            }
            if (content instanceof TableBorder) {
                lineArtChunk = null;
            }
            if (content instanceof TextLine && lineArtChunk != null) {
                TextLine textLine = (TextLine) content;
                if (isLineConnectedWithLineArt(textLine, lineArtChunk)) {
                    textLine.setConnectedLineArtLabel(lineArtChunk);
                }
                lineArtChunk = null;
            }
        }
    }

    private static boolean isLineConnectedWithLineArt(TextLine textLine, LineArtChunk lineArt) {
        return lineArt.getRightX() <= textLine.getLeftX() && lineArt.getBoundingBox().getHeight() <
                ListUtils.LIST_LABEL_HEIGHT_EPSILON * textLine.getBoundingBox().getHeight();
    }
}

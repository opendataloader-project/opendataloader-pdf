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
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

public class TextLineProcessor {

    private static final double ONE_LINE_PROBABILITY = 0.75;
    private static final Comparator<TextChunk> TEXT_CHUNK_COMPARATOR =
        Comparator.comparingDouble(o -> o.getBoundingBox().getLeftX());

    public static List<IObject> processTextLines(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        // Track which TextChunk immediately follows a whitespace chunk in stream order,
        // using reference identity so lookups are immune to TextChunk.equals() semantics.
        // Stream order may differ from visual (leftX) order in rare PDFs, but whitespace
        // chunks originate from the same PDF text operator as their adjacent text chunks,
        // so stream-order adjacency is reliable for this signal.
        Set<TextChunk> chunksAfterWhitespace = Collections.newSetFromMap(new IdentityHashMap<>());
        TextLine previousLine = new TextLine(new TextChunk(""));
        boolean isSeparateLine = false;
        boolean pendingWhitespace = false;
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    if (textChunk.isWhiteSpaceChunk()) {
                        pendingWhitespace = true;
                    }
                    continue;
                }
                if (pendingWhitespace) {
                    chunksAfterWhitespace.add(textChunk);
                    pendingWhitespace = false;
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
                pendingWhitespace = false;
            }
        }
        for (int i = 0; i < newContents.size(); i++) {
            IObject content = newContents.get(i);
            if (content instanceof TextLine) {
                TextLine textLine = (TextLine) content;
                textLine.getTextChunks().sort(TEXT_CHUNK_COMPARATOR);
                double threshold = textLine.getFontSize() * TextChunkUtils.TEXT_LINE_SPACE_RATIO;
                newContents.set(i, getTextLineWithSpaces(textLine, threshold, chunksAfterWhitespace));
            }
        }
        linkTextLinesWithConnectedLineArtBullet(newContents);
        return newContents;
    }

    private static TextLine getTextLineWithSpaces(TextLine textLine, double threshold,
                                                   Set<TextChunk> chunksAfterWhitespace) {
        List<TextChunk> textChunks = textLine.getTextChunks();
        TextChunk currentTextChunk = textChunks.get(0);
        double previousEnd = currentTextChunk.getBoundingBox().getRightX();
        TextLine newLine = new TextLine();
        newLine.add(currentTextChunk);
        for (int i = 1; i < textChunks.size(); i++) {
            currentTextChunk = textChunks.get(i);
            double currentStart = currentTextChunk.getBoundingBox().getLeftX();
            boolean hasGap = currentStart - previousEnd > threshold;
            boolean hadWhitespace = chunksAfterWhitespace.contains(currentTextChunk);
            if (hasGap || hadWhitespace) {
                double spaceLeft = Math.min(previousEnd, currentStart);
                double spaceRight = Math.max(previousEnd, currentStart);
                BoundingBox spaceBBox = new BoundingBox(currentTextChunk.getBoundingBox());
                spaceBBox.setLeftX(spaceLeft);
                spaceBBox.setRightX(spaceRight);
                TextChunk spaceChunk = new TextChunk(spaceBBox, " ", textLine.getFontSize(), textLine.getBaseLine());
                newLine.add(spaceChunk);
            }
            previousEnd = currentTextChunk.getBoundingBox().getRightX();
            newLine.add(currentTextChunk);
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

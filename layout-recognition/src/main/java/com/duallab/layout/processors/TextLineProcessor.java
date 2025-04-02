/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.processors;

import com.duallab.wcag.algorithms.entities.IObject;
import com.duallab.wcag.algorithms.entities.SemanticTextNode;
import com.duallab.wcag.algorithms.entities.content.LineArtChunk;
import com.duallab.wcag.algorithms.entities.content.TextChunk;
import com.duallab.wcag.algorithms.entities.content.TextLine;
import com.duallab.wcag.algorithms.entities.geometry.BoundingBox;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import com.duallab.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import com.duallab.wcag.algorithms.semanticalgorithms.utils.ListUtils;

import java.util.ArrayList;
import java.util.List;

public class TextLineProcessor {

    private static final double ONE_LINE_PROBABILITY = 0.75;

    public static List<IObject> processTextLines(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        TextLine previousLine = new TextLine(new TextChunk(""));
        boolean isSeparateLine = false;
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk)content;
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
        linkTextLinesWithConnectedLineArtBullet(newContents);
        return newContents;
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

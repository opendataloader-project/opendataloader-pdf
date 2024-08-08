package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;

import java.util.ArrayList;
import java.util.List;

public class TextLineProcessor {

    private static final double ONE_LINE_PROBABILITY = 0.75;

    public static List<IObject> processTextLines(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        TextLine previousLine = new TextLine(new TextChunk(""));
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk)content;
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    continue;
                }
                TextLine currentLine = new TextLine(textChunk);
                double oneLineProbability = ChunksMergeUtils.countOneLineProbability(new SemanticTextNode(), previousLine, currentLine);
                if (oneLineProbability > ONE_LINE_PROBABILITY) {
                    previousLine.add(currentLine);
                } else {
                    previousLine.setBoundingBox(new BoundingBox(previousLine.getBoundingBox()));
                    previousLine = currentLine;
                    newContents.add(previousLine);
                }
            } else {
                newContents.add(content);
            }
        }
        return newContents;
    }
}

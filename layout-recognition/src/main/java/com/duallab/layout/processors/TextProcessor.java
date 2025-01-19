package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TextProcessor {
    
    private static final double MIN_TEXT_INTERSECTION_PERCENT = 0.5;

    public static void trimTextChunksWhiteSpaces(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject object = contents.get(i);
            if (object instanceof TextChunk) {
                contents.set(i, ChunksMergeUtils.getTrimTextChunk((TextChunk) object));
            }
        }
    }

    public static void removeSameTextChunks(List<IObject> contents) {
        DocumentProcessor.setIndexesForContentsList(contents);
        List<IObject> sortedTextChunks = contents.stream().filter(c -> c instanceof TextChunk).sorted(
                Comparator.comparing(x -> ((TextChunk)x).getValue())).collect(Collectors.toList());
        TextChunk lastTextChunk = null;
        for (IObject object : sortedTextChunks) {
            if (object instanceof TextChunk) {
                TextChunk currentTextChunk = (TextChunk) object;
                if (lastTextChunk != null && areSameTextChunks(lastTextChunk, currentTextChunk)) {
                    contents.set(lastTextChunk.getIndex(), null);
                }
                lastTextChunk = currentTextChunk;
            }
        }
    }
    
    public static boolean areSameTextChunks(TextChunk firstTextChunk, TextChunk secondTextChunk) {
        return Objects.equals(firstTextChunk.getValue(), secondTextChunk.getValue()) &&
                NodeUtils.areCloseNumbers(firstTextChunk.getWidth(), secondTextChunk.getWidth()) &&
                NodeUtils.areCloseNumbers(firstTextChunk.getHeight(), secondTextChunk.getHeight()) &&
                firstTextChunk.getBoundingBox().getIntersectionPercent(secondTextChunk.getBoundingBox()) > MIN_TEXT_INTERSECTION_PERCENT;
    }
}

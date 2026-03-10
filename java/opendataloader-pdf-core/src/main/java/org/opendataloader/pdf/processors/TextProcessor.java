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

import org.verapdf.gf.model.factory.chunks.ChunkParser;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class TextProcessor {

    private static final double MIN_TEXT_INTERSECTION_PERCENT = 0.5;
    private static final double MAX_TOP_DECORATION_IMAGE_EPSILON = 0.3;
    private static final double MAX_BOTTOM_DECORATION_IMAGE_EPSILON = 0.1;
    private static final double MAX_LEFT_DECORATION_IMAGE_EPSILON = 0.1;
    private static final double MAX_RIGHT_DECORATION_IMAGE_EPSILON = 1.5;
    private static final double NEIGHBORS_TEXT_CHUNKS_EPSILON = 0.1;
    private static final double TEXT_MIN_HEIGHT = 1;

    public static void replaceUndefinedCharacters(List<IObject> contents, String replacementCharacterString) {
        if (ChunkParser.REPLACEMENT_CHARACTER_STRING.equals(replacementCharacterString)) {
            return;
        }
        for (IObject object : contents) {
            if (object instanceof TextChunk) {
                TextChunk textChunk = ((TextChunk) object);
                if (textChunk.getValue().contains(ChunkParser.REPLACEMENT_CHARACTER_STRING)) {
                    textChunk.setValue(textChunk.getValue().replace(ChunkParser.REPLACEMENT_CHARACTER_STRING, replacementCharacterString));
                }
            }
        }
    }

    public static void filterTinyText(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject object = contents.get(i);
            if (object instanceof TextChunk) {
                TextChunk textChunk = ((TextChunk) object);
                if (textChunk.getBoundingBox().getHeight() <= TEXT_MIN_HEIGHT) {
                    contents.set(i, null);
                }
            }
        }
    }

    public static void trimTextChunksWhiteSpaces(List<IObject> contents) {
        for (int i = 0; i < contents.size(); i++) {
            IObject object = contents.get(i);
            if (object instanceof TextChunk) {
                contents.set(i, ChunksMergeUtils.getTrimTextChunk((TextChunk) object));
            }
        }
    }

    public static void mergeCloseTextChunks(List<IObject> contents) {
        for (int i = 0; i < contents.size() - 1; i++) {
            IObject object = contents.get(i);
            IObject nextObject = contents.get(i + 1);
            if (object instanceof TextChunk && nextObject instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) object;
                TextChunk nextTextChunk = (TextChunk) nextObject;
                if (TextChunkUtils.areTextChunksHaveSameStyle(textChunk, nextTextChunk) &&
                    TextChunkUtils.areTextChunksHaveSameBaseLine(textChunk, nextTextChunk) &&
                    areNeighborsTextChunks(textChunk, nextTextChunk)) {
                    contents.set(i, null);
                    contents.set(i + 1, TextChunkUtils.unionTextChunks(textChunk, nextTextChunk));
                }
            }
        }
    }

    public static void removeSameTextChunks(List<IObject> contents) {
        DocumentProcessor.setIndexesForContentsList(contents);
        List<IObject> sortedTextChunks = contents.stream().filter(c -> c instanceof TextChunk).sorted(
                Comparator.comparing(x -> ((TextChunk) x).getValue())).collect(Collectors.toList());
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

    public static void removeTextDecorationImages(List<IObject> contents) {
        TextChunk lastTextChunk = null;
        for (int index = 0; index < contents.size(); index++) {
            IObject object = contents.get(index);
            if (object instanceof TextChunk) {
                lastTextChunk = (TextChunk) object;
            } else if (object instanceof ImageChunk && lastTextChunk != null &&
                    isTextChunkDecorationImage((ImageChunk) object, lastTextChunk)) {
                contents.set(index, null);
            }
        }
    }

    public static boolean isTextChunkDecorationImage(ImageChunk imageChunk, TextChunk textChunk) {
        return NodeUtils.areCloseNumbers(imageChunk.getTopY(), textChunk.getTopY(), MAX_TOP_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) &&
                NodeUtils.areCloseNumbers(imageChunk.getBottomY(), textChunk.getBottomY(), MAX_BOTTOM_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) &&
                (NodeUtils.areCloseNumbers(imageChunk.getLeftX(), textChunk.getLeftX(), MAX_LEFT_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) || imageChunk.getLeftX() > textChunk.getLeftX()) &&
                (NodeUtils.areCloseNumbers(imageChunk.getRightX(), textChunk.getRightX(), MAX_RIGHT_DECORATION_IMAGE_EPSILON * textChunk.getHeight()) || imageChunk.getRightX() < textChunk.getRightX());
    }

    private static boolean areNeighborsTextChunks(TextChunk firstTextChunk, TextChunk secondTextChunk) {
        return NodeUtils.areCloseNumbers(firstTextChunk.getTextEnd(), secondTextChunk.getTextStart(),
            NEIGHBORS_TEXT_CHUNKS_EPSILON * firstTextChunk.getBoundingBox().getHeight());
    }
}

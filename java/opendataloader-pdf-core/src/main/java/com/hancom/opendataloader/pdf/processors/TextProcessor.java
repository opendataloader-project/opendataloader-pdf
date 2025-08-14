/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

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
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticFigure;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.CaptionUtils;

import java.util.List;

/**
 * Processor for detecting and linking captions to figures and tables.
 * Identifies text nodes that are likely captions based on proximity and content.
 */
public class CaptionProcessor {

    private static final double CAPTION_PROBABILITY = 0.75;

    private static final double CAPTION_VERTICAL_OFFSET_RATIO = 1;
    private static final double CAPTION_HORIZONTAL_OFFSET_RATIO = 1;
    private static final double SUBTLE_IMAGE_RATIO_THRESHOLD = 0.01;

    /**
     * Processes content to identify and link captions to images and tables.
     *
     * @param contents the list of content objects to process
     */
    public static void processCaptions(List<IObject> contents) {
        DocumentProcessor.setIndexesForContentsList(contents);
        SemanticFigure imageNode = null;
        SemanticTextNode lastTextNode = null;
        for (IObject content : contents) {
            if (content == null) {
                continue;
            }
            if (content instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) content;
                if (textNode.isSpaceNode() || textNode.isEmpty()) {
                    continue;
                }

                if (imageNode != null && isTextNotContainedInImage(imageNode, textNode)) {
                    acceptImageCaption(contents, imageNode, lastTextNode, textNode);
                    imageNode = null;
                }

                lastTextNode = textNode;
            } else if (content instanceof ImageChunk && !isImageSubtle((ImageChunk) content)) {
                if (imageNode != null && isTextNotContainedInImage(imageNode, lastTextNode)) {
                    acceptImageCaption(contents, imageNode, lastTextNode, null);
                    lastTextNode = null;
                }
                imageNode = new SemanticFigure((ImageChunk) content);
                imageNode.setRecognizedStructureId(content.getRecognizedStructureId());
            } else if (content instanceof TableBorder && !((TableBorder) content).isTextBlock()) {
                if (imageNode != null && isTextNotContainedInImage(imageNode, lastTextNode)) {
                    acceptImageCaption(contents, imageNode, lastTextNode, null);
                    lastTextNode = null;
                }
                ImageChunk imageChunk = new ImageChunk(content.getBoundingBox());
                imageChunk.setRecognizedStructureId(content.getRecognizedStructureId());
                imageNode = new SemanticFigure(imageChunk);
                imageNode.setRecognizedStructureId(content.getRecognizedStructureId());
            }
        }
        if (imageNode != null) {
            acceptImageCaption(contents, imageNode, lastTextNode, null);
        }
//        for (IObject content1 : contents) {
//            if (content1 instanceof SemanticTextNode) {
//                SemanticTextNode textNode = (SemanticTextNode)content1;
//                for (IObject content2 : contents) {
//                    if (content2 instanceof ImageChunk) {
//                        SemanticFigure imageNode = new SemanticFigure((ImageChunk) content2);
//                        acceptImageCaption(imageNode, textNode, textNode);
//                    }
//                }
//            }
//        }
    }

    private static boolean isImageSubtle(ImageChunk imageChunk) {
        double imageHeight = imageChunk.getHeight();
        double imageWidth = imageChunk.getWidth();
        if (imageWidth == 0 || imageHeight == 0) {
            return true;
        }
        if (imageHeight > imageWidth) {
            return imageWidth/imageHeight < SUBTLE_IMAGE_RATIO_THRESHOLD;
        } else {
            return imageHeight/imageWidth < SUBTLE_IMAGE_RATIO_THRESHOLD;
        }
    }


    /**
     * Checks if a text node is not contained within an image's bounding box.
     *
     * @param image the image to check against
     * @param text the text node to check
     * @return true if the text is outside the image bounds, false otherwise
     */
    public static boolean isTextNotContainedInImage(SemanticFigure image, SemanticTextNode text) {
        if (text == null) {
            return true;
        }

        double textSize = text.getFontSize();
        return !image.getBoundingBox().contains(text.getBoundingBox(),
                textSize * CAPTION_HORIZONTAL_OFFSET_RATIO,
                textSize * CAPTION_VERTICAL_OFFSET_RATIO);
    }

    private static void acceptImageCaption(List<IObject> contents, SemanticFigure imageNode,
                                           SemanticTextNode previousNode, SemanticTextNode nextNode) {
        if (imageNode.getImages().isEmpty()) {
            return;
        }
        double previousCaptionProbability = CaptionUtils.imageCaptionProbability(previousNode, imageNode);
        double nextCaptionProbability = CaptionUtils.imageCaptionProbability(nextNode, imageNode);
        double captionProbability;
        SemanticTextNode captionNode;
        if (previousCaptionProbability > nextCaptionProbability) {
            captionProbability = previousCaptionProbability;
            captionNode = previousNode;
        } else {
            captionProbability = nextCaptionProbability;
            captionNode = nextNode;
        }
        if (captionProbability >= CAPTION_PROBABILITY) {
            SemanticCaption semanticCaption = new SemanticCaption(captionNode);
            contents.set(captionNode.getIndex(), semanticCaption);
            semanticCaption.setLinkedContentId(imageNode.getRecognizedStructureId());
        }
    }
}

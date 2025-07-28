/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticFigure;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.CaptionUtils;

import java.util.List;

public class CaptionProcessor {

    private static final double CAPTION_PROBABILITY = 0.75;

    private static final double CAPTION_VERTICAL_OFFSET_RATIO = 1;
    private static final double CAPTION_HORIZONTAL_OFFSET_RATIO = 1;

    public static void processCaptions(List<IObject> contents) {
        DocumentProcessor.setIndexesForContentsList(contents);
        SemanticFigure imageNode = null;
        SemanticTextNode lastTextNode = null;
        for (IObject content : contents) {
            if (content == null) {
                continue;
            }
            if (content instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode)content;
                if (textNode.isSpaceNode() || textNode.isEmpty()) {
                    continue;
                }

                if (imageNode != null && isTextNotContainedInImage(imageNode, textNode)) {
                    acceptImageCaption(contents, imageNode, lastTextNode, textNode);
                    imageNode = null;
                }

                lastTextNode = textNode;
            } else if (content instanceof ImageChunk) {
                if (imageNode != null && isTextNotContainedInImage(imageNode, lastTextNode)) {
                    acceptImageCaption(contents, imageNode, lastTextNode, null);
                    lastTextNode = null;
                }
                imageNode = new SemanticFigure((ImageChunk) content);
                imageNode.setRecognizedStructureId(content.getRecognizedStructureId());
            } else if (content instanceof TableBorder && !((TableBorder)content).isTextBlock()) {
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

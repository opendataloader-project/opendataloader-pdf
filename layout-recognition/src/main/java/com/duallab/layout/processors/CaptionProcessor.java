package com.duallab.layout.processors;

import com.duallab.layout.ContentInfo;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticFigure;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.CaptionUtils;

import java.util.List;

public class CaptionProcessor {

    private static final double CAPTION_PROBABILITY = 0.75;

    public static void processCaptions(List<IObject> contents) {
        SemanticFigure imageNode = null;
        SemanticTextNode lastTextNode = null;
        for (IObject content : contents) {
            if (content != null) {
                if (content instanceof SemanticTextNode) {
                    SemanticTextNode textNode = (SemanticTextNode)content;
                    if (!textNode.isSpaceNode() && !textNode.isEmpty()) {
                        if (imageNode != null) {
                            acceptImageCaption(imageNode, lastTextNode, textNode);
                            imageNode = null;
                        }
                        lastTextNode = textNode;
                    }
                } else if (content instanceof ImageChunk) {
                    if (imageNode != null) {
                        acceptImageCaption(imageNode, lastTextNode, null);
                        lastTextNode = null;
                    }
                    imageNode = new SemanticFigure((ImageChunk) content);
                } else if (content instanceof TableBorder) {
                    if (imageNode != null) {
                        acceptImageCaption(imageNode, lastTextNode, null);
                        lastTextNode = null;
                    }
                    ImageChunk imageChunk = new ImageChunk(content.getBoundingBox());
                    imageChunk.setRecognizedStructureId(content.getRecognizedStructureId());
                    imageNode = new SemanticFigure(imageChunk);
                }
            }
        }
        if (imageNode != null) {
            acceptImageCaption(imageNode, lastTextNode, null);
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

    private static void acceptImageCaption(SemanticFigure imageNode, SemanticTextNode previousNode, SemanticTextNode nextNode) {
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
            captionNode.setSemanticType(SemanticType.CAPTION);
            StaticLayoutContainers.getContentInfoMap().put(captionNode, new ContentInfo(DocumentProcessor.getContentsValueForTextNode(captionNode) + ", connected with object with id = " + imageNode.getImages().get(0).getRecognizedStructureId(), PDFWriter.getColor(SemanticType.CAPTION)));
        }
    }
}

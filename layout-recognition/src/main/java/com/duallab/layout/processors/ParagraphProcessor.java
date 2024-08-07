package com.duallab.layout.processors;

import com.duallab.layout.Info;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticPart;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.AccumulatedNodeConsumer;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ParagraphProcessor {

    public static void processParagraphs(List<IObject> contents) {
        SemanticPart part = null;
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode && ((INode) content).getSemanticType() != SemanticType.LIST) {
                if (part == null) {
                    part = AccumulatedNodeConsumer.buildPartFromNode((SemanticTextNode)content);
                } else {
                    AccumulatedNodeConsumer.toPartMergeProbability(part, (SemanticTextNode)content);
                }
            }
        }
//        for (IObject content : contents) {
//            if (content instanceof TextChunk) {//image chunk?
//                SemanticTextNode textNode = new SemanticTextNode((TextChunk)content, SemanticType.SPAN);
//                textNode.setSemanticType(SemanticType.SPAN);
//                if (part == null) {
//                    part = AccumulatedNodeConsumer.buildPartFromNode(textNode);
//                } else {
//                    AccumulatedNodeConsumer.toPartMergeProbability(part, textNode);
//                }
//            }
//        }
        List<SemanticTextNode> textNodes = new LinkedList<>();
        if (part == null) {
            return;
        }
        for (TextColumn column : part.getColumns()) {
            for (TextBlock textBlock : column.getBlocks()) {
                SemanticTextNode textNode = new SemanticTextNode(new MultiBoundingBox());
                for (TextLine line : textBlock.getLines()) {
                    if (textNode.getPageNumber() != null && !Objects.equals(textNode.getPageNumber(), line.getPageNumber())) {
                        textNodes.add(textNode);
                        textNode = new SemanticTextNode(new MultiBoundingBox());
                    }
                    textNode.add(line);
//                    textNode.getLastColumn().add(line);
//                    textNode.getBoundingBox().union(new BoundingBox(line.getBoundingBox()));
                }
                textNodes.add(textNode);
            }
        }
        for (SemanticTextNode textNode : textNodes) {
            DocumentProcessor.replaceContentsToResult(contents, textNode);//refactoring
            textNode.setSemanticType(SemanticType.PARAGRAPH);
            textNode.setCorrectSemanticScore(1.0);
            StaticLayoutContainers.getMap().put(textNode, new Info(DocumentProcessor.getContentsValueForTextNode(textNode), PDFWriter.getColor(SemanticType.PARAGRAPH)));
        }
    }
}

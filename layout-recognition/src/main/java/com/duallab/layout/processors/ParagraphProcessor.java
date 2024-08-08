package com.duallab.layout.processors;

import com.duallab.layout.ContentInfo;
import com.duallab.layout.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.CaptionUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;

import com.duallab.layout.pdf.PDFWriter;

import java.util.ArrayList;
import java.util.List;

public class ParagraphProcessor {

    private static final double DIFFERENT_LINES_PROBABILITY = 0.75;

    public static List<IObject> processParagraphs(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        TextBlock previousBlock = new TextBlock();
        previousBlock.add(new TextLine(new TextChunk()));
        List<SemanticTextNode> paragraphs = new ArrayList<>();
        for (IObject content : contents) {
            if (content instanceof TextLine) {
                TextLine currentLine = (TextLine)content;
                TextLine previousLine = previousBlock.getLastLine();
                double differentLinesProbability;
                if (previousBlock.getLinesNumber() > 1) {
                    differentLinesProbability = ChunksMergeUtils.toParagraphMergeProbability(previousLine, currentLine);
                } else {
                    differentLinesProbability = ChunksMergeUtils.mergeLeadingProbability(previousLine, currentLine);
                }
                if (!CaptionUtils.areOverlapping(previousLine, currentLine.getBoundingBox())) {
                    differentLinesProbability = 0d;
                }
                if (differentLinesProbability > DIFFERENT_LINES_PROBABILITY) {
                    previousBlock.add(currentLine);
                } else {
                    previousBlock = new TextBlock(currentLine);
                    SemanticTextNode textNode = new SemanticTextNode();
                    textNode.getColumns().add(new TextColumn());
                    textNode.getLastColumn().getBlocks().add(previousBlock);
                    textNode.getBoundingBox().union(previousBlock.getBoundingBox());
                    newContents.add(textNode);
                    textNode.setSemanticType(SemanticType.PARAGRAPH);
                    textNode.setCorrectSemanticScore(1.0);
                    paragraphs.add(textNode);
                }
            } else {
                newContents.add(content);
            }
        }
        for (SemanticTextNode textNode : paragraphs) {
            textNode.setBoundingBox(textNode.getFirstColumn().getFirstTextBlock().getBoundingBox());
            StaticLayoutContainers.getContentInfoMap().put(textNode, 
                    new ContentInfo(DocumentProcessor.getContentsValueForTextNode(textNode), PDFWriter.getColor(SemanticType.PARAGRAPH)));
        }
        return newContents;
    }
}

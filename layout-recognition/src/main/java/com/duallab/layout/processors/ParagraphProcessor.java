package com.duallab.layout.processors;

import com.duallab.layout.ContentInfo;
import com.duallab.layout.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
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
        List<SemanticParagraph> paragraphs = new ArrayList<>();
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
                    SemanticParagraph textParagraph = new SemanticParagraph();
                    textParagraph.getColumns().add(new TextColumn());
                    textParagraph.getLastColumn().getBlocks().add(previousBlock);
                    textParagraph.getBoundingBox().union(previousBlock.getBoundingBox());
                    newContents.add(textParagraph);
                    //textNode.setSemanticType(SemanticType.PARAGRAPH);
                    textParagraph.setCorrectSemanticScore(1.0);
                    paragraphs.add(textParagraph);
                }
            } else {
                newContents.add(content);
            }
        }
        for (SemanticParagraph textParagraph : paragraphs) {
            textParagraph.setBoundingBox(textParagraph.getFirstColumn().getFirstTextBlock().getBoundingBox());
            StaticLayoutContainers.getContentInfoMap().put(textParagraph,
                    new ContentInfo(DocumentProcessor.getContentsValueForTextNode(textParagraph), PDFWriter.getColor(SemanticType.PARAGRAPH)));
        }
        return newContents;
    }
}

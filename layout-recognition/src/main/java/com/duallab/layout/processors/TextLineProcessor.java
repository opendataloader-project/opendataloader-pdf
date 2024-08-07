package com.duallab.layout.processors;

import com.duallab.layout.Info;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.Stack;


public class TextLineProcessor {

    public static void processLines(List<IObject> contents) {
        List<SemanticTextNode> lines = new LinkedList<>();
        Stack<Integer> replaceIndexes = new Stack<>();
        Stack<Integer> insertIndexes = new Stack<>();
        SemanticTextNode node = null;
        for (int index = 0; index < contents.size(); index ++) {
            IObject content = contents.get(index);
            if (content instanceof TextChunk) {//image chunk?
                replaceIndexes.add(index);
                SemanticTextNode textNode = new SemanticTextNode((TextChunk)content, SemanticType.SPAN);
                textNode.setSemanticType(SemanticType.SPAN);
                if (node == null) {
                    insertIndexes.add(index);
                    node = textNode;
                    continue;
                }
                TextLine lastLine = node.getLastLine();
                TextLine nextLine = textNode.getFirstLine();
                double oneLineProbability = ChunksMergeUtils.countOneLineProbability(textNode, lastLine, nextLine);
                if (oneLineProbability > 0.75) {
                    lastLine.setNotLineEnd();
                    nextLine.setNotLineStart();
                    node.setLastColumn(new TextColumn(node.getLastColumn()));
                    TextBlock lastBlock = new TextBlock(node.getLastColumn().getLastTextBlock());
                    lastLine = new TextLine(lastLine);
                    lastLine.add(nextLine);
                    lastLine.setBoundingBox(new BoundingBox(lastLine.getBoundingBox()));
                    lastBlock.setLastLine(lastLine);
                    node.getLastColumn().setLastTextBlock(lastBlock);
                    node.getBoundingBox().union(nextLine.getBoundingBox());
                } else {
                    insertIndexes.add(index);
                    lines.add(node);
                    node = textNode;
                }
            }
        }
        if (node != null) {
            lines.add(node);
        }
        DocumentProcessor.replaceContentsToResult(contents, lines, replaceIndexes, insertIndexes);
        for (SemanticTextNode line : lines) {
//            replaceContentsToResult(contents, line);//refactoring
            line.setSemanticType(SemanticType.PARAGRAPH);
            line.setCorrectSemanticScore(1.0);
            line.setBoundingBox(new BoundingBox(line.getBoundingBox()));
            StaticLayoutContainers.getMap().put(line, new Info(DocumentProcessor.getContentsValueForTextNode(line), PDFWriter.getColor(SemanticType.PARAGRAPH)));
        }
    }
}

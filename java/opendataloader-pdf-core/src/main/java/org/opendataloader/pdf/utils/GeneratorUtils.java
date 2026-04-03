package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.List;

public class GeneratorUtils {
    public static String getTextFromTextNode(SemanticTextNode textNode, String strikethroughTextOpening, String strikethroughTextClosing) {
        StringBuilder stringBuilder = new StringBuilder();
        for (TextColumn column : textNode.getColumns()) {
            for (TextBlock block : column.getBlocks()) {
                stringBuilder.append(getTextFromLines(block.getLines(), strikethroughTextOpening, strikethroughTextClosing));
            }
        }
        return stringBuilder.toString();
    }

    public static String getTextFromLines(List<TextLine> textLines, String strikethroughTextOpening, String strikethroughTextClosing) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < textLines.size() - 1; i++) {
            TextLine line = textLines.get(i);
            getTextFromLine(line, stringBuilder, strikethroughTextOpening, strikethroughTextClosing);
            TextChunkUtils.formatLineEnd(stringBuilder);
        }
        getTextFromLine(textLines.get(textLines.size() - 1), stringBuilder, strikethroughTextOpening, strikethroughTextClosing);
        return stringBuilder.toString();
    }

    public static void getTextFromLine(TextLine line, StringBuilder stringBuilder, String strikethroughTextOpening, String strikethroughTextClosing) {
        for (TextChunk chunk : line.getTextChunks()) {
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextOpening);
            }
            stringBuilder.append(chunk.getValue());
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextClosing);
            }
        }
    }
}

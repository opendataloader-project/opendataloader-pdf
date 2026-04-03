package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.List;

public class GeneratorUtils {
    protected static final String strikethroughTextMD = "~~";
    protected static final String strikethroughTextHtmlOpeningTag = "<del>";
    protected static final String strikethroughTextHtmlClosingTag = "</del>";;

    public static String getTextFromTextNode(SemanticTextNode textNode, OutputType outputType) {
        StringBuilder stringBuilder = new StringBuilder();
        for (TextColumn column : textNode.getColumns()) {
            List<TextBlock> blocks = column.getBlocks();
            for (int i = 0; i < blocks.size() - 1; i++) {
                TextBlock block = blocks.get(i);
                stringBuilder.append(getTextFromLines(block.getLines(), outputType));
                TextChunkUtils.formatLineEnd(stringBuilder);
            }
            stringBuilder.append(getTextFromLines(blocks.get(blocks.size() - 1).getLines(), outputType));
        }
        return stringBuilder.toString();
    }

    public static String getTextFromLines(List<TextLine> textLines, OutputType outputType) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < textLines.size() - 1; i++) {
            TextLine line = textLines.get(i);
            switch (outputType) {
                case MD:
                    getTextFromLineForMarkdown(line,  stringBuilder);
                    break;
                case HTML:
                    getTextFromLineForHTML(line, stringBuilder);
                    break;
            }
            TextChunkUtils.formatLineEnd(stringBuilder);
        }
        switch (outputType) {
            case MD:
                getTextFromLineForMarkdown(textLines.get(textLines.size() - 1),  stringBuilder);
                break;
            case HTML:
                getTextFromLineForHTML(textLines.get(textLines.size() - 1), stringBuilder);
                break;
        }
        return stringBuilder.toString();
    }

    public static void getTextFromLineForMarkdown(TextLine line, StringBuilder stringBuilder) {
        for (TextChunk chunk : line.getTextChunks()) {
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextMD);
            }
            stringBuilder.append(chunk.getValue());
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextMD);
            }
        }
    }

    public static void getTextFromLineForHTML(TextLine line, StringBuilder stringBuilder) {
        for (TextChunk chunk : line.getTextChunks()) {
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextHtmlOpeningTag);
            }
            stringBuilder.append(chunk.getValue());
            if (chunk.getIsStrikethroughText()) {
                stringBuilder.append(strikethroughTextHtmlClosingTag);
            }
        }
    }
}

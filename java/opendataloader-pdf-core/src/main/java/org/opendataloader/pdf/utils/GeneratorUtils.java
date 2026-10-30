package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.List;
import java.util.function.BiConsumer;

public class GeneratorUtils {

    public static String getTextFromTextNode(SemanticTextNode textNode, OutputType outputType) {
        return getTextFromTextNode(textNode, getLineWriter(outputType));
    }

    /**
     * Gets the text of a text node, with each line written by {@code lineWriter}.
     */
    public static String getTextFromTextNode(SemanticTextNode textNode, BiConsumer<TextLine, StringBuilder> lineWriter) {
        StringBuilder stringBuilder = new StringBuilder();
        for (TextColumn column : textNode.getColumns()) {
            List<TextBlock> blocks = column.getBlocks();
            for (int i = 0; i < blocks.size() - 1; i++) {
                TextBlock block = blocks.get(i);
                stringBuilder.append(getTextFromLines(block.getLines(), lineWriter));
                TextChunkUtils.formatLineEnd(stringBuilder);
            }
            stringBuilder.append(getTextFromLines(blocks.get(blocks.size() - 1).getLines(), lineWriter));
        }
        return stringBuilder.toString();
    }

    public static String getTextFromLines(List<TextLine> textLines, OutputType outputType) {
        return getTextFromLines(textLines, getLineWriter(outputType));
    }

    /**
     * Gets the text of the lines, with each line written by {@code lineWriter}.
     */
    public static String getTextFromLines(List<TextLine> textLines, BiConsumer<TextLine, StringBuilder> lineWriter) {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < textLines.size() - 1; i++) {
            lineWriter.accept(textLines.get(i), stringBuilder);
            TextChunkUtils.formatLineEnd(stringBuilder);
        }
        lineWriter.accept(textLines.get(textLines.size() - 1), stringBuilder);
        return stringBuilder.toString();
    }

    private static BiConsumer<TextLine, StringBuilder> getLineWriter(OutputType outputType) {
        switch (outputType) {
            case MD:
                return MarkdownGenerator::getTextFromLineForMarkdown;
            case HTML:
                return HtmlGenerator::getTextFromLineForHTML;
            default:
                return (line, stringBuilder) -> { };
        }
    }
}

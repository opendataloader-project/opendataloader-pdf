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

    /** Gets the text of a text node. A link of --include-links can go on over the line ends. */
    public static String getTextFromTextNode(SemanticTextNode textNode, OutputType outputType) {
        TextLinks.OpenLink link = new TextLinks.OpenLink();
        return closeLink(getTextFromTextNode(textNode, getLineWriter(outputType, link)), outputType, link);
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

    /** Gets the text of the lines. A link of --include-links can go on over the line ends. */
    public static String getTextFromLines(List<TextLine> textLines, OutputType outputType) {
        TextLinks.OpenLink link = new TextLinks.OpenLink();
        return closeLink(getTextFromLines(textLines, getLineWriter(outputType, link)), outputType, link);
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

    /** The line writer of the output type. A link at the end of a line stays open in {@code link}. */
    private static BiConsumer<TextLine, StringBuilder> getLineWriter(OutputType outputType, TextLinks.OpenLink link) {
        switch (outputType) {
            case MD:
                return (line, stringBuilder) -> MarkdownGenerator.getTextFromLineForMarkdown(line, stringBuilder, link);
            case HTML:
                return (line, stringBuilder) -> HtmlGenerator.getTextFromLineForHTML(line, stringBuilder, link);
            default:
                return (line, stringBuilder) -> { };
        }
    }

    /** Ends the link that the text ends in, if one is open. */
    private static String closeLink(String text, OutputType outputType, TextLinks.OpenLink link) {
        if (link.getUri() == null) {
            return text;
        }
        StringBuilder stringBuilder = new StringBuilder(text);
        switch (outputType) {
            case MD:
                MarkdownGenerator.closeLink(link, stringBuilder);
                break;
            case HTML:
                HtmlGenerator.closeLink(link, stringBuilder);
                break;
            default:
                break;
        }
        return stringBuilder.toString();
    }
}

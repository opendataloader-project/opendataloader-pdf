package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.html.FormattedHtmlGenerator;
import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.html.HtmlSyntax;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.List;

public class GeneratorUtils {

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
                    MarkdownGenerator.getTextFromLineForMarkdown(line,  stringBuilder);
                    TextChunkUtils.formatLineEnd(stringBuilder);
                    break;
                case HTML:
                    HtmlGenerator.getTextFromLineForHTML(line, stringBuilder, OutputType.HTML);
                    TextChunkUtils.formatLineEnd(stringBuilder);
                    break;
                case FORMATTED_HTML:
                    FormattedHtmlGenerator.getTextFromLineForHTML(line, stringBuilder, OutputType.FORMATTED_HTML);
                    stringBuilder.append(HtmlSyntax.HTML_LINE_BREAK_TAG);
                    break;
            }
        }
        switch (outputType) {
            case MD:
                MarkdownGenerator.getTextFromLineForMarkdown(textLines.get(textLines.size() - 1),  stringBuilder);
                break;
            case HTML:
                HtmlGenerator.getTextFromLineForHTML(textLines.get(textLines.size() - 1), stringBuilder,  OutputType.HTML);
                break;
            case FORMATTED_HTML:
                FormattedHtmlGenerator.getTextFromLineForHTML(textLines.get(textLines.size() - 1),  stringBuilder, OutputType.FORMATTED_HTML);
                break;
        }
        return stringBuilder.toString();
    }
}

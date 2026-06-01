package org.opendataloader.pdf.html;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.utils.OutputType;
import org.verapdf.gf.model.factory.chunks.ChunkParser;
import org.verapdf.wcag.algorithms.entities.BaseObject;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.TextAlignment;
import org.verapdf.wcag.algorithms.entities.enums.TextFormat;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Objects;
import java.util.Stack;

public class FormattedHtmlGenerator extends HtmlGenerator {
    private static final DecimalFormat FORMATTER;

    static {
        FORMATTER = new DecimalFormat("#0.000");
        DecimalFormatSymbols decFormSymbols = FORMATTER.getDecimalFormatSymbols();
        decFormSymbols.setDecimalSeparator('.');
        FORMATTER.setDecimalFormatSymbols(decFormSymbols);
    }

    protected Stack<IObject> nestedObjects = new Stack<>();
    protected double pagesHeight = 0.0;

    protected static final double DEFAULT_FONT_SIZE = 16.0;
    protected static final int DEFAULT_FONT_WEIGHT = 400;

    protected FormattedHtmlGenerator(File inputPdf, Config config) throws IOException {
        super(inputPdf, config);
    }

    @Override
    protected OutputType getOutputType() {
        return OutputType.FORMATTED_HTML;
    }

    @Override
    protected void updatePagesHeight(int pageNumber) {
        pagesHeight += Objects.requireNonNull(DocumentProcessor.getPageBoundingBox(pageNumber)).getHeight();
    }

    @Override
    protected void writeStyleTag() throws IOException {
        htmlWriter.write("<style>\n");
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_OPENING,
            "ul, p, h1, h2, h3, h4, h5, h6, table, figcaption, img, li, figure"));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_POSITION_ABSOLUTE_PROPERTY));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_ZERO_MARGIN_PROPERTY));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_DEFAULT_FONT_WEIGHT_PROPERTY));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_WHITE_SPACE_PROPERTY));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_DEFAULT_LINE_HEIGHT_PROPERTY));
        htmlWriter.write(HtmlSyntax.HTML_STYLE_TAG_ENTRY_CLOSING);

        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_OPENING, "ul"));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_LIST_STYLE_TYPE_NONE_PROPERTY));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_PADDING_ZERO_PROPERTY));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_WIDTH_100_PROPERTY));
        htmlWriter.write(HtmlSyntax.HTML_STYLE_TAG_ENTRY_CLOSING);

        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_OPENING, "table"));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_BORDER_COLLAPSE_PROPERTY));
        htmlWriter.write(HtmlSyntax.HTML_STYLE_TAG_ENTRY_CLOSING);

        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_OPENING, "td, th"));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_ZERO_MARGIN_PROPERTY));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_PADDING_ZERO_PROPERTY));
        htmlWriter.write(HtmlSyntax.HTML_STYLE_TAG_ENTRY_CLOSING);

        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_OPENING, "span"));
        htmlWriter.write(String.format(HtmlSyntax.HTML_STYLE_TAG_ENTRY_VALUE, HtmlSyntax.HTML_DEFAULT_FONT_WEIGHT_PROPERTY));
        htmlWriter.write(HtmlSyntax.HTML_STYLE_TAG_ENTRY_CLOSING);

        htmlWriter.write("</style>\n");
    }

    @Override
    protected String getFormulaStyleAttribute(SemanticFormula formula) {
        String style = HtmlSyntax.HTML_POSITION_ABSOLUTE_PROPERTY + getObjectStyle(formula);
        return String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, style.trim());
    }

    @Override
    protected String getListStyleAttribute(BaseObject list) {
        return String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, getPositionProperty(list).trim());
    }

    @Override
    protected String getImageStyleAttribute(BaseObject image) {
        return String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, getObjectStyle(image).trim());
    }

    @Override
    protected String getTextNodeStyleAttribute(SemanticTextNode node) {
        TextChunk firstChunk = node.getFirstLine().getFirstTextChunk();
        String style = getPositionProperty(node, firstChunk.isBottomUpVerticalText()) + getTextAlignmentProperty(node)
            + String.format(HtmlSyntax.HTML_WIDTH_PROPERTY, formattedPxValue(convertPtToPx(node.getWidth())))
            + String.format(HtmlSyntax.HTML_FONT_SIZE_PROPERTY, formattedPxValue(convertPtToPx(node.getFontSize())));
        style += getLineHeightProperty(node.getLinesNumber(), getTextNodeHeight(node), node.getFontSize());
        style += getRotationProperty(node.getFirstLine().getFirstTextChunk());
        return String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, style.trim());
    }

    @Override
    protected String getListItemStyleAttribute(double nestedListHeight, TextBlock block, boolean isFirstListItem) {
        String itemStyle = "";
        if (block != null) {
            TextChunk firstChunk = block.getFirstLine().getFirstTextChunk();
            if (firstChunk.isBottomUpVerticalText()) {
                itemStyle += String.format(HtmlSyntax.HTML_TOP_PROPERTY,
                    formattedPxValue(convertPtToPx(nestedObjects.get(nestedObjects.size() - 2).getHeight() - block.getWidth())));
                itemStyle += String.format(HtmlSyntax.HTML_LEFT_PROPERTY, formattedPxValue(convertPtToPx(nestedListHeight)));
            } else if (firstChunk.isUpBottomVerticalText()) {
                if (isFirstListItem) {
                    itemStyle += String.format(HtmlSyntax.HTML_LEFT_PROPERTY,
                        formattedPxValue(convertPtToPx(nestedObjects.get(nestedObjects.size() - 2).getWidth() -  block.getWidth())));
                } else {
                    itemStyle += String.format(HtmlSyntax.HTML_LEFT_PROPERTY, formattedPxValue(convertPtToPx(nestedListHeight)));
                }
            }
        }
        if (itemStyle.isEmpty()) {
            itemStyle = nestedListHeight != 0.0 ?
                String.format(HtmlSyntax.HTML_TOP_PROPERTY, formattedPxValue(convertPtToPx(nestedListHeight))) : EMPTY_STRING;
        }
        return itemStyle.isEmpty() ? EMPTY_STRING : String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, itemStyle.trim());
    }

    @Override
    protected String getTextBlockStyleAttribute(TextBlock block) {
        String pStyle = String.format(HtmlSyntax.HTML_WIDTH_PROPERTY, formattedPxValue(convertPtToPx(block.getWidth())))
            + String.format(HtmlSyntax.HTML_FONT_SIZE_PROPERTY, formattedPxValue(convertPtToPx(block.getMostCommonFontSize())));
        TextChunk firstChunk = block.getFirstLine().getFirstTextChunk();
        pStyle += getLineHeightProperty(block.getLinesNumber(), getTextBlockHeight(block), block.getFontSize());
        pStyle += getRotationProperty(firstChunk);
        return String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, pStyle.trim());
    }

    @Override
    protected String getTableStyleAttribute(TableBorder table) {
        String style = getObjectStyle(table) + String.format(HtmlSyntax.HTML_HEIGHT_PROPERTY,
            formattedPxValue(convertPtToPx(table.getHeight())));
        return String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, style.trim());
    }

    @Override
    protected String getCellStyleAttribute(TableBorderCell cell) {
        String style = String.format(HtmlSyntax.HTML_WIDTH_PROPERTY, formattedPxValue(convertPtToPx(cell.getWidth())))
            + String.format(HtmlSyntax.HTML_HEIGHT_PROPERTY, formattedPxValue(convertPtToPx(cell.getHeight()) - TABLE_BORDER));
        return String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, style.trim());
    }

    @Override
    protected double calculateNestedItemsHeight(TextBlock block, TextBlock nextBlock, double nestedListHeight, boolean isFirstListItem) {
        TextChunk firstChunk = block.getFirstLine().getFirstTextChunk();
        if (firstChunk.isBottomUpVerticalText()) {
            return nestedListHeight + nextBlock.getLeftX() - block.getLeftX();
        } else if (firstChunk.isUpBottomVerticalText()) {
            if (isFirstListItem) {
                return nestedListHeight;
            }
            return nestedListHeight + nextBlock.getRightX() - block.getRightX();
        }
        return nestedListHeight + block.getTopY() - nextBlock.getTopY();
    }

    @Override
    protected void writePageSeparator(int pageNumber) throws IOException {
        if (!htmlPageSeparator.isEmpty()) {
            String style = String.format(HtmlSyntax.HTML_TOP_PROPERTY, formattedPxValue(convertPtToPx(pagesHeight)))
                + String.format(HtmlSyntax.HTML_WIDTH_PROPERTY, formattedPxValue(convertPtToPx(Objects.requireNonNull(DocumentProcessor.getPageBoundingBox(pageNumber)).getWidth())))
                + String.format(HtmlSyntax.HTML_TEXT_ALIGNMENT_PROPERTY, TextAlignment.CENTER.name().toLowerCase());
            htmlWriter.write(String.format(HtmlSyntax.HTML_PARAGRAPH_TAG, String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, style.trim())));
            htmlWriter.write(htmlPageSeparator.contains(Config.PAGE_NUMBER_STRING)
                ? htmlPageSeparator.replace(Config.PAGE_NUMBER_STRING, String.valueOf(pageNumber + 1))
                : htmlPageSeparator);
            htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_CLOSE_TAG);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        }
    }

    protected static String getTextStyle(TextChunk chunk) {
        StringBuilder style = new StringBuilder();
        if (chunk.getIsStrikethroughText()) {
            style.append(HtmlSyntax.HTML_STRIKETHROUGH_STYLE_PROPERTY);
        }
        String fontName = chunk.getFontName();
        String fontFamily = null;
        if (ChunkParser.fontNameToFontFamilyMap.containsKey(fontName)) {
            fontFamily = ChunkParser.fontNameToFontFamilyMap.get(fontName);
        }
        if (fontFamily != null) {
            style.append(String.format(HtmlSyntax.HTML_FONT_FAMILY_PROPERTY, fontFamily));
        }
        if (chunk.isItalic()) {
            style.append(HtmlSyntax.HTML_ITALIC_STYLE_PROPERTY);
        }
        if (chunk.getTextFormat() == TextFormat.SUBSCRIPT) {
            style.append(HtmlSyntax.HTML_VERTICAL_ALIGN_SUB_PROPERTY);
        } else if (chunk.getTextFormat() == TextFormat.SUPERSCRIPT) {
            style.append(HtmlSyntax.HTML_VERTICAL_ALIGN_SUPER_PROPERTY);
        }
        double fontSizeInPx = convertPtToPx(chunk.getFontSize());
        if (!NodeUtils.areCloseNumbers(fontSizeInPx, DEFAULT_FONT_SIZE) || isHeading) {
            style.append(String.format(HtmlSyntax.HTML_FONT_SIZE_PROPERTY, formattedPxValue(fontSizeInPx)));
        }
        Color color = chunk.getTextColor();
        if (color != null && (color.getRGB() & 0x00FFFFFF) != (Color.BLACK.getRGB() & 0x00FFFFFF)) {
            style.append(String.format(HtmlSyntax.HTML_FONT_COLOR_PROPERTY,
                color.getRed(), color.getGreen(), color.getBlue()));
        }
        int fontWeight = chunk.getRoundedFontWeight();
        if (fontWeight != DEFAULT_FONT_WEIGHT) {
            style.append(String.format(HtmlSyntax.HTML_FONT_WEIGHT_PROPERTY, fontWeight));
        }
        return style.toString();
    }

    @Override
    protected void addToNestedObjects(IObject object) {
        nestedObjects.add(object);
    }

    @Override
    protected void removeFromNestedObjects() {
        nestedObjects.pop();
    }

    @Override
    protected String getObjectStyle(BaseObject object) {
        return getPositionProperty(object) + String.format(HtmlSyntax.HTML_WIDTH_PROPERTY,
            formattedPxValue(convertPtToPx(object.getWidth())));
    }

    @Override
    protected String getPositionProperty(BaseObject object) {
        return getPositionProperty(object, false);
    }

    protected String getPositionProperty(BaseObject object, boolean isBottomUpText) {
        return String.format(HtmlSyntax.HTML_TOP_PROPERTY, formattedPxValue(convertPtToPx((nestedObjects.isEmpty() ?
            pagesHeight : nestedObjects.peek().getTopY()) - object.getTopY() +
            (isBottomUpText ? object.getHeight() - object.getWidth() : 0)))) +
            String.format(HtmlSyntax.HTML_LEFT_PROPERTY, formattedPxValue(convertPtToPx(nestedObjects.isEmpty()
                ? object.getLeftX() : (object.getLeftX() - nestedObjects.peek().getLeftX()))));
    }

    protected static double calculateLineHeight(int linesNumber, double height, double fontSize) {
        return height / linesNumber / fontSize;
    }

    protected String getTextAlignmentProperty(SemanticTextNode node) {
        TextAlignment textAlignment = node.getColumns().get(0).getBlocks().get(0).getTextAlignment();
        if (textAlignment == null || textAlignment == TextAlignment.LEFT) {
            return EMPTY_STRING;
        }
        return String.format(HtmlSyntax.HTML_TEXT_ALIGNMENT_PROPERTY, textAlignment.name().toLowerCase());
    }

    protected static double convertPtToPx(double sizeInPt) {
        return sizeInPt * 4.0 / 3.0;
    }

    protected static String formattedPxValue(double pxValue) {
        return FORMATTER.format(pxValue);
    }

    protected String getLineHeightProperty(int linesNumber, double height, double fontSize) {
        String style = "";
        if (linesNumber > 1) {
            double lineHeight = calculateLineHeight(linesNumber, height, fontSize);
            if (lineHeight != 0.0) {
                style += String.format(HtmlSyntax.HTML_LINE_HEIGHT_PROPERTY, FORMATTER.format(lineHeight));
            }
        }
        return style;
    }

    protected String getRotationProperty(TextChunk chunk) {
        String style = "";
        if (chunk.isBottomUpVerticalText()) {
            style += String.format(HtmlSyntax.HTML_TRANSFORM_ROTATE_PROPERTY, 270);
        } else if (chunk.isUpBottomVerticalText()) {
            style += String.format(HtmlSyntax.HTML_TRANSFORM_ROTATE_PROPERTY, 90);
        }
        return style;
    }

    protected double getTextBlockHeight(TextBlock block) {
        return calculateHeightFromLines(block.getFirstLine(), block.getLastLine());
    }

    protected double getTextNodeHeight(SemanticTextNode node) {
        return calculateHeightFromLines(node.getFirstLine(), node.getLastLine());
    }

    private double calculateHeightFromLines(TextLine firstLine, TextLine lastLine) {
        TextChunk firstChunk = firstLine.getFirstTextChunk();
        return firstChunk.isUpBottomVerticalText() ?
            firstLine.getRightX() - lastLine.getLeftX() :
            firstChunk.isBottomUpVerticalText() ? firstLine.getLeftX() - lastLine.getRightX()
                : firstLine.getTopY() - lastLine.getBaseLine();
    }
}

/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.html;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.GeneratorUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.opendataloader.pdf.utils.OutputType;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Generates HTML output from PDF document content.
 * Converts semantic elements like paragraphs, headings, tables, and images into HTML format.
 */
public class HtmlGenerator implements Closeable {

    /** Logger for this class. */
    protected static final Logger LOGGER = Logger.getLogger(HtmlGenerator.class.getCanonicalName());

    /** Writer for the HTML output file. */
    protected final FileWriter htmlWriter;
    /** Name of the input PDF file. */
    protected final String pdfFileName;
    /** Absolute path to the input PDF file. */
    protected final Path pdfFilePath;
    /** Name of the output HTML file. */
    protected final String htmlFileName;
    /** Absolute path to the output HTML file. */
    protected final Path htmlFilePath;
    /** Current table nesting level for tracking nested tables. */
    protected int tableNesting = 0;
    /** String to insert between pages in HTML output. */
    protected String htmlPageSeparator = "";
    /**
     * Page numbers (1-based) selected by --pages; an empty set means all pages.
     * Sourced from the raw {@link Config#getPageNumbers()} list (not the
     * validated set built by {@code DocumentProcessor.getValidPageNumbers}).
     * Safe to compare against {@code pageNumber + 1} because the surrounding
     * loop is bounded by the document's actual page count, so out-of-range
     * values from the raw list are never tested for membership.
     */
    protected final Set<Integer> selectedPageNumbers;
    /** Whether to embed images as Base64 data URIs. */
    protected boolean embedImages = false;
    /** Format for extracted images (png or jpeg). */
    protected String imageFormat = Config.IMAGE_FORMAT_PNG;
    /** Whether to include page headers and footers in output. */
    protected boolean includeHeaderFooter = false;

    protected static boolean isHeading = false;

    protected final int TABLE_BORDER = 1;
    protected static final String EMPTY_STRING = "";

    /**
     * Creates a new HtmlGenerator for the specified PDF file.
     *
     * @param inputPdf the input PDF file
     * @param config the configuration settings
     * @throws IOException if unable to create the output file
     */
    public HtmlGenerator(File inputPdf, Config config) throws IOException {
        this.pdfFileName = inputPdf.getName();
        this.pdfFilePath = inputPdf.toPath().toAbsolutePath();
        this.htmlFileName = pdfFileName.substring(0, pdfFileName.length() - 3) + "html";
        this.htmlFilePath = Path.of(config.getOutputFolder(), htmlFileName);
        this.htmlWriter = new FileWriter(htmlFilePath.toFile(), StandardCharsets.UTF_8);
        this.htmlPageSeparator = escapeHtmlAttribute(config.getHtmlPageSeparator());
        this.selectedPageNumbers = new HashSet<>(config.getPageNumbers());
        this.embedImages = config.isEmbedImages();
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
    }

    protected OutputType getOutputType() {
        return OutputType.HTML;
    }

    /**
     * Writes the document contents to HTML format.
     *
     * @param contents the document contents organized by page
     */
    public void writeToHtml(List<List<IObject>> contents) {
        try {
            htmlWriter.write("<!DOCTYPE html>\n");
            htmlWriter.write("<html lang=\"und\">\n<head>\n<meta charset=\"utf-8\">\n");
            htmlWriter.write("<title>" + escapeHtmlText(pdfFileName) + "</title>\n");
            writeStyleTag();
            htmlWriter.write("</head>\n<body>\n");

            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                if (selectedPageNumbers.isEmpty() || selectedPageNumbers.contains(pageNumber + 1)) {
                    updatePagesHeight(pageNumber);
                    writePageSeparator(pageNumber);
                }
                for (IObject content : contents.get(pageNumber)) {
                    this.write(content);
                }
            }

            htmlWriter.write("\n</body>\n</html>");
            LOGGER.log(Level.INFO, "Created {0}", htmlFilePath);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to create html output: " + e.getMessage());
        }
    }

    protected void updatePagesHeight(int pageNumber) {}

    protected void writeStyleTag() throws IOException {
    }

    /**
     * Writes a page separator to the HTML output if configured.
     *
     * @param pageNumber the current page number (0-indexed)
     * @throws IOException if unable to write to the output
     */
    protected void writePageSeparator(int pageNumber) throws IOException {
        if (!htmlPageSeparator.isEmpty()) {
            htmlWriter.write(htmlPageSeparator.contains(Config.PAGE_NUMBER_STRING)
                ? htmlPageSeparator.replace(Config.PAGE_NUMBER_STRING, String.valueOf(pageNumber + 1))
                : htmlPageSeparator);
            htmlWriter.write("\n");
        }
    }

    /**
     * Writes a single content object to the HTML output.
     *
     * @param object the content object to write
     * @throws IOException if unable to write to the output
     */
    protected void write(IObject object) throws IOException {
        if (object instanceof SemanticHeaderOrFooter) {
            if (includeHeaderFooter) {
                writeHeaderOrFooter((SemanticHeaderOrFooter) object);
            }
            return;
        } else if (object instanceof SemanticPicture) {
            writePicture((SemanticPicture) object);
        } else if (object instanceof ImageChunk) {
            writeImage((ImageChunk) object);
        } else if (object instanceof SemanticFormula) {
            writeFormula((SemanticFormula) object);
        } else if (object instanceof SemanticHeading) {
            writeHeading((SemanticHeading) object);
        } else if (object instanceof SemanticParagraph) {
            writeParagraph((SemanticParagraph) object);
        } else if (object instanceof SemanticTextNode) {
            writeSemanticTextNode((SemanticTextNode) object);
        } else if (object instanceof TableBorder) {
            writeTable((TableBorder) object);
        } else if (object instanceof PDFList) {
            writeList((PDFList) object);
        } else if (object instanceof SemanticTOC) {
            writeTOC((SemanticTOC) object);
        } else {
            return;
        }

        if (!isInsideTable()) {
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        }
    }

    /**
     * Writes a header or footer element to the HTML output.
     *
     * @param headerOrFooter the header or footer to write
     * @throws IOException if unable to write to the output
     */
    protected void writeHeaderOrFooter(SemanticHeaderOrFooter headerOrFooter) throws IOException {
        for (IObject content : headerOrFooter.getContents()) {
            write(content);
        }
    }

    /**
     * Writes a formula element to the HTML output using MathJax-compatible markup.
     *
     * @param formula the formula to write
     * @throws IOException if unable to write to the output
     */
    protected void writeFormula(SemanticFormula formula) throws IOException {
        htmlWriter.write(String.format(HtmlSyntax.HTML_MATH_DISPLAY_TAG, getFormulaStyleAttribute(formula)));
        htmlWriter.write("\\[");
        htmlWriter.write(escapeHtmlText(formula.getLatex()));
        htmlWriter.write("\\]");
        htmlWriter.write(HtmlSyntax.HTML_MATH_DISPLAY_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
    }

    /**
     * Writes an image element to the HTML output.
     *
     * @param image the image chunk to write
     */
    protected void writeImage(ImageChunk image) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT,
                StaticLayoutContainers.getImagesDirectory(), File.separator, image.getIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT,
                StaticLayoutContainers.getImagesDirectoryName(), "/", image.getIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = relativePath;
                }
                if (imageSource != null) {
                    String escapedSource = escapeHtmlAttribute(imageSource);
                    // Empty alt is correct HTML for "missing description": screen
                    // readers skip it, and our evidence-report flags it as
                    // alt_source="missing". Never synthesize "figureN".
                    String altText = (image instanceof EnrichedImageChunk && ((EnrichedImageChunk) image).hasDescription())
                            ? ((EnrichedImageChunk) image).sanitizeDescription()
                            : EMPTY_STRING;
                    String altAttribute = EMPTY_STRING;
                    if (!altText.isEmpty()) {
                        altAttribute = String.format("alt=\"%s\"", escapeHtmlAttribute(altText));
                    }
                    String imageString = String.format("<img src=\"%s\" %s%s>",
                        escapedSource, altAttribute, getImageStyleAttribute(image));
                    htmlWriter.write(imageString);
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write image for html output: " + e.getMessage());
        }
    }

    /**
     * Writes a SemanticPicture element with figure/figcaption for description.
     *
     * @param picture the picture to write
     */
    protected void writePicture(SemanticPicture picture) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT,
                StaticLayoutContainers.getImagesDirectory(), File.separator, picture.getPictureIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT,
                StaticLayoutContainers.getImagesDirectoryName(), "/", picture.getPictureIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = relativePath;
                }
                if (imageSource != null) {
                    String altText = picture.hasDescription()
                            ? picture.sanitizeDescription()
                            : EMPTY_STRING;
                    String escapedSource = escapeHtmlAttribute(imageSource);
                    htmlWriter.write(String.format(HtmlSyntax.HTML_FIGURE_TAG, getImageStyleAttribute(picture)));
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                    String altAttribute = EMPTY_STRING;
                    if (!altText.isEmpty()) {
                        altAttribute = String.format("alt=\"%s\"", escapeHtmlAttribute(altText));
                    }
                    String imageString = String.format("<img src=\"%s\" %s>", escapedSource, altAttribute);
                    htmlWriter.write(imageString);
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                    htmlWriter.write(HtmlSyntax.HTML_FIGURE_CLOSE_TAG);
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write picture for html output: " + e.getMessage());
        }
    }

    /**
     * Writes a list element to the HTML output.
     *
     * @param list the PDF list to write
     * @throws IOException if unable to write to the output
     */
    protected void writeList(PDFList list) throws IOException {
        htmlWriter.write(String.format(HtmlSyntax.HTML_UNORDERED_LIST_TAG, getListStyleAttribute(list)));
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        addToNestedObjects(list);
        double nestedItemsHeight = 0.0;
        for (int i = 0; i < list.getListItems().size(); i++) {
            ListItem item = list.getListItems().get(i);
            addToNestedObjects(item);
            htmlWriter.write(String.format(HtmlSyntax.HTML_LIST_ITEM_TAG, getListItemStyleAttribute(nestedItemsHeight,
                item, i == 0)));
            htmlWriter.write(String.format(HtmlSyntax.HTML_PARAGRAPH_TAG, getTextBlockStyleAttribute(item)));
            String value = GeneratorUtils.getTextFromLines(item.getLines(), getOutputType());
            htmlWriter.write(value);
            htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_CLOSE_TAG);
            List<IObject> contents = item.getContents();
            for (IObject object : contents) {
                write(object);
            }
            if (i < list.getListItems().size() - 1) {
                nestedItemsHeight = calculateNestedItemsHeight(item, list.getListItems().get(i + 1), nestedItemsHeight, i == 0);
            }

            htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_CLOSE_TAG);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
            removeFromNestedObjects();
        }
        htmlWriter.write(HtmlSyntax.HTML_UNORDERED_LIST_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        removeFromNestedObjects();
    }

    protected void writeTOC(SemanticTOC toc) throws IOException {
        htmlWriter.write(String.format(HtmlSyntax.HTML_UNORDERED_LIST_TAG, getListStyleAttribute(toc)));
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        addToNestedObjects(toc);
        double nestedItemsHeight = 0.0;
        for (int i = 0; i < toc.getTOCItems().size(); i++) {
            IObject item =  toc.getTOCItems().get(i);
            addToNestedObjects(item);

            if (item instanceof SemanticTOC) {
                htmlWriter.write(String.format(HtmlSyntax.HTML_LIST_ITEM_TAG,
                    getListItemStyleAttribute(nestedItemsHeight, null, i == 0)));
                htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                writeTOC((SemanticTOC) item);
                htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_CLOSE_TAG);
            } else if (item instanceof SemanticTOCI) {
                SemanticTOCI tocItem = (SemanticTOCI) item;
                htmlWriter.write(String.format(HtmlSyntax.HTML_LIST_ITEM_TAG, getListItemStyleAttribute(nestedItemsHeight,
                    ((SemanticTOCI) item), i == 0)));
                htmlWriter.write(String.format(HtmlSyntax.HTML_PARAGRAPH_TAG,
                    getTextBlockStyleAttribute((SemanticTOCI) item)));
                String value = GeneratorUtils.getTextFromLines(tocItem.getLines(), getOutputType());
                htmlWriter.write(value);
                htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_CLOSE_TAG);
                for (IObject object : tocItem.getContents()) {
                    write(object);
                }
                if (i < toc.getTOCItems().size() - 1) {
                    nestedItemsHeight = calculateNestedItemsHeight(tocItem, (SemanticTOCI) toc.getTOCItems().get(i + 1),
                        nestedItemsHeight, i == 0);
                }
                htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_CLOSE_TAG);
            }
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
            removeFromNestedObjects();
        }
        htmlWriter.write(HtmlSyntax.HTML_UNORDERED_LIST_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        removeFromNestedObjects();
    }

    /**
     * Writes a semantic text node as a figure caption to the HTML output.
     *
     * @param textNode the text node to write
     * @throws IOException if unable to write to the output
     */
    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        htmlWriter.write(String.format(HtmlSyntax.HTML_FIGURE_CAPTION_TAG, getTextNodeStyleAttribute(textNode)));
        htmlWriter.write(GeneratorUtils.getTextFromTextNode(textNode, getOutputType()));
        htmlWriter.write(HtmlSyntax.HTML_FIGURE_CAPTION_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
    }

    /**
     * Writes a table element to the HTML output.
     *
     * @param table the table border to write
     * @throws IOException if unable to write to the output
     */
    protected void writeTable(TableBorder table) throws IOException {
        String styleAttribute = getTableStyleAttribute(table);
        addToNestedObjects(table);
        enterTable();
        htmlWriter.write(String.format(HtmlSyntax.HTML_TABLE_TAG, TABLE_BORDER, styleAttribute));
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            htmlWriter.write(HtmlSyntax.HTML_TABLE_ROW_TAG);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    writeCellTag(cell);
                    List<IObject> contents = cell.getContents();
                    if (!contents.isEmpty()) {
                        for (IObject contentItem : contents) {
                            this.write(contentItem);
                        }
                    }
                    if (cell.isHeaderCell()) {
                        htmlWriter.write(HtmlSyntax.HTML_TABLE_HEADER_CLOSE_TAG);
                    } else {
                        htmlWriter.write(HtmlSyntax.HTML_TABLE_CELL_CLOSE_TAG);
                    }
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                }
            }

            htmlWriter.write(HtmlSyntax.HTML_TABLE_ROW_CLOSE_TAG);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        }

        htmlWriter.write(HtmlSyntax.HTML_TABLE_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        leaveTable();
        removeFromNestedObjects();
    }

    /**
     * Writes a paragraph element to the HTML output.
     *
     * @param paragraph the semantic paragraph to write
     * @throws IOException if unable to write to the output
     */
    protected void writeParagraph(SemanticParagraph paragraph) throws IOException {
        double paragraphIndent = paragraph.getColumns().get(0).getBlocks().get(0).getFirstLineIndent();
        htmlWriter.write(String.format(HtmlSyntax.HTML_PARAGRAPH_TAG, getTextNodeStyleAttribute(paragraph)));
        if (paragraphIndent > 0) {
            htmlWriter.write(HtmlSyntax.HTML_INDENT);
        }
        String paragraphValue = GeneratorUtils.getTextFromTextNode(paragraph, getOutputType());

        if (isInsideTable() && StaticContainers.isKeepLineBreaks()) {
            paragraphValue = paragraphValue.replace(HtmlSyntax.HTML_LINE_BREAK, HtmlSyntax.HTML_LINE_BREAK_TAG);
        }

        htmlWriter.write(paragraphValue);
        htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
    }

    /**
     * Writes a heading element to the HTML output.
     *
     * @param heading the semantic heading to write
     * @throws IOException if unable to write to the output
     */
    protected void writeHeading(SemanticHeading heading) throws IOException {
        isHeading = true;
        int headingLevel = Math.min(6, Math.max(1, heading.getHeadingLevel()));
        htmlWriter.write("<h" + headingLevel + getTextNodeStyleAttribute(heading) + ">");
        htmlWriter.write(GeneratorUtils.getTextFromTextNode(heading, getOutputType()));
        htmlWriter.write("</h" + headingLevel + ">");
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        isHeading = false;
    }

    private void writeCellTag(TableBorderCell cell) throws IOException {
        String tag = cell.isHeaderCell() ? "<th" : "<td";
        StringBuilder cellTag = new StringBuilder(tag);
        int colSpan = cell.getColSpan();
        if (colSpan != 1) {
            cellTag.append(" colspan=\"").append(colSpan).append("\"");
        }

        int rowSpan = cell.getRowSpan();
        if (rowSpan != 1) {
            cellTag.append(" rowspan=\"").append(rowSpan).append("\"");
        }

        cellTag.append(getCellStyleAttribute(cell));
        cellTag.append(">");
        htmlWriter.write(cellTag.toString());
    }

    /**
     * Increments the table nesting level when entering a table.
     */
    protected void enterTable() {
        tableNesting++;
    }

    /**
     * Decrements the table nesting level when leaving a table.
     */
    protected void leaveTable() {
        if (tableNesting > 0) {
            tableNesting--;
        }
    }

    /**
     * Checks whether currently writing inside a table.
     *
     * @return true if inside a table, false otherwise
     */
    protected boolean isInsideTable() {
        return tableNesting > 0;
    }

    /**
     * Escapes special characters for use in HTML attributes.
     * Handles quotes, ampersands, less-than, greater-than, and newlines.
     *
     * @param value the string to escape
     * @return the escaped string safe for HTML attribute values
     */
    protected static String escapeHtmlAttribute(String value) {
        if (value == null) {
            return "";
        }
        value = escapeHtmlText(value);
        return value
            .replace("\"", "&quot;")
            .replace("\n", " ")
            .replace("\r", "");
    }

    protected static String escapeHtmlText(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\u0000", "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    public static void getTextFromLineForHTML(TextLine line, StringBuilder stringBuilder, OutputType outputType) {
        for (TextChunk chunk : line.getTextChunks()) {
            String style = outputType == OutputType.HTML ? getTextStyle(chunk) : FormattedHtmlGenerator.getTextStyle(chunk);
            if (!style.isEmpty()) {
                String styleAttribute = String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, style.trim());
                stringBuilder.append(String.format(HtmlSyntax.HTML_SPAN_START_TAG, styleAttribute));
                stringBuilder.append(escapeHtmlText(chunk.getValue()));
                stringBuilder.append(HtmlSyntax.HTML_SPAN_CLOSE_TAG);
            } else {
                stringBuilder.append(escapeHtmlText(chunk.getValue()));
            }
        }
    }

    protected String getTextNodeStyleAttribute(SemanticTextNode node) {
        return EMPTY_STRING;
    }

    protected String getListItemStyleAttribute(double nestedListHeight, TextBlock block, boolean isFirstListItem) {
        return EMPTY_STRING;
    }

    protected String getTextBlockStyleAttribute(TextBlock block) {
        return EMPTY_STRING;
    }

    protected String getObjectStyle(BaseObject object) {
        return EMPTY_STRING;
    }

    protected String getFormulaStyleAttribute(SemanticFormula formula) {
        return EMPTY_STRING;
    }

    protected String getListStyleAttribute(BaseObject list) {
        return EMPTY_STRING;
    }

    protected static String getTextStyle(TextChunk chunk) {
        StringBuilder style = new StringBuilder();
        if (chunk.getIsStrikethroughText() || chunk.getIsUnderlinedText()) {
            style.append(String.format(HtmlSyntax.HTML_TEXT_DECORATION_STYLE_PROPERTY,
                (chunk.getIsStrikethroughText() ? HtmlSyntax.HTML_STRIKETHROUGH_VALUE : "") +
                    (chunk.getIsUnderlinedText() ? HtmlSyntax.HTML_UNDERLINE_VALUE : "")));
        }
        return style.toString();
    }

    protected double calculateNestedItemsHeight(TextBlock block, TextBlock nextBlock, double nestedListHeight, boolean isFirstListItem) {
        return 0.0;
    }
    protected String getPositionProperty(BaseObject object) {
        return EMPTY_STRING;
    }

    protected String getImageStyleAttribute(BaseObject image) {
        return EMPTY_STRING;
    }

    protected String getTableStyleAttribute(TableBorder table) {
        return EMPTY_STRING;
    }

    protected String getCellStyleAttribute(TableBorderCell cell) {
        return EMPTY_STRING;
    }

    protected void addToNestedObjects(IObject object) {}

    protected void removeFromNestedObjects() {}

    @Override
    public void close() throws IOException {
        if (htmlWriter != null) {
            htmlWriter.close();
        }
    }
}

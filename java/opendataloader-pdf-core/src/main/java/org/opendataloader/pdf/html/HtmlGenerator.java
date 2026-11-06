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
import org.opendataloader.pdf.utils.FileUtils;
import org.opendataloader.pdf.utils.GeneratorUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.opendataloader.pdf.utils.OutputType;
import org.opendataloader.pdf.utils.TextLinks;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
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
    /** Opening tag for strikethrough text*/
    protected static final String strikethroughTextHtmlOpeningTag = "<del>";
    /** Closing tag for strikethrough text*/
    protected static final String strikethroughTextHtmlClosingTag = "</del>";;
    /** Start tag of a link, for its escaped address */
    private static final String LINK_START_TAG = "<a href=\"%s\">";
    /** Closing tag of a link */
    private static final String LINK_CLOSE_TAG = "</a>";
    /** Attributes of {@link HtmlSyntax#HTML_UNORDERED_LIST_TAG}: the list writes no bullets of its own. */
    private static final String LIST_ATTRIBUTES = HtmlSyntax.HTML_UNORDERED_LIST_TAG.substring(
        "<ul".length(), HtmlSyntax.HTML_UNORDERED_LIST_TAG.length() - 1);

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
        this.htmlFileName = FileUtils.getOutputBaseName(pdfFileName) + ".html";
        this.htmlFilePath = Path.of(config.getOutputFolder(), htmlFileName);
        this.htmlWriter = new FileWriter(htmlFilePath.toFile(), StandardCharsets.UTF_8);
        this.htmlPageSeparator = escapeHtmlAttribute(config.getHtmlPageSeparator());
        this.selectedPageNumbers = new HashSet<>(config.getPageNumbers());
        this.embedImages = config.isEmbedImages();
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
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
            writeHeadContent();
            htmlWriter.write("</head>\n<body>\n");

            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                if (selectedPageNumbers.isEmpty() || selectedPageNumbers.contains(pageNumber + 1)) {
                    startPage(pageNumber);
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
        htmlWriter.write(startTag(HtmlSyntax.HTML_MATH_DISPLAY_TAG, getFormulaAttributes(formula)));
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
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, image.getIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", image.getIndex(), imageFormat);

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
                            : "";
                    String imageString = startTag(String.format("<img src=\"%s\" alt=\"%s\">", escapedSource,
                        escapeHtmlAttribute(altText)), getImageAttributes(image));
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
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, picture.getPictureIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", picture.getPictureIndex(), imageFormat);

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
                            : "";
                    String escapedSource = escapeHtmlAttribute(imageSource);

                    htmlWriter.write(startTag(HtmlSyntax.HTML_FIGURE_TAG, getImageAttributes(picture)));
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                    String imageString = String.format("<img src=\"%s\" alt=\"%s\">", escapedSource, escapeHtmlAttribute(altText));
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
        htmlWriter.write(startTag("<ul>", getListAttributes(list)));
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        enterContainer(list);
        List<ListItem> items = list.getListItems();
        for (int index = 0; index < items.size(); index++) {
            ListItem item = items.get(index);
            htmlWriter.write(startTag(HtmlSyntax.HTML_LIST_ITEM_TAG, getListItemAttributes(items, index)));
            enterContainer(item);

            htmlWriter.write(startTag(HtmlSyntax.HTML_PARAGRAPH_TAG, getListItemTextAttributes(item)));
            String value = getTextFromLines(item.getLines());
            htmlWriter.write(value);
            htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_CLOSE_TAG);

            for (IObject object : item.getContents()) {
                write(object);
            }
            htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_CLOSE_TAG);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
            leaveContainer(item);
        }
        htmlWriter.write(HtmlSyntax.HTML_UNORDERED_LIST_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        leaveContainer(list);
    }

    protected void writeTOC(SemanticTOC toc) throws IOException {
        htmlWriter.write(startTag("<ul>", getListAttributes(toc)));
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        enterContainer(toc);
        List<IObject> items = toc.getTOCItems();
        for (int index = 0; index < items.size(); index++) {
            IObject item = items.get(index);
            if (item instanceof SemanticTOC) {
                htmlWriter.write(startTag(HtmlSyntax.HTML_LIST_ITEM_TAG, getListItemAttributes(items, index)));
                enterContainer(item);
                htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                writeTOC((SemanticTOC) item);
                htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_CLOSE_TAG);
                leaveContainer(item);
            } else if (item instanceof SemanticTOCI) {
                SemanticTOCI tocItem = (SemanticTOCI) item;
                htmlWriter.write(startTag(HtmlSyntax.HTML_LIST_ITEM_TAG, getListItemAttributes(items, index)));
                enterContainer(item);
                htmlWriter.write(startTag(HtmlSyntax.HTML_PARAGRAPH_TAG, getListItemTextAttributes(tocItem)));
                String value = getTextFromLines(tocItem.getLines());
                htmlWriter.write(value);
                htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_CLOSE_TAG);
                for (IObject object : tocItem.getContents()) {
                    write(object);
                }
                htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_CLOSE_TAG);
                leaveContainer(item);
            }
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        }
        htmlWriter.write(HtmlSyntax.HTML_UNORDERED_LIST_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        leaveContainer(toc);
    }

    /**
     * Writes a semantic text node as a figure caption to the HTML output.
     *
     * @param textNode the text node to write
     * @throws IOException if unable to write to the output
     */
    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        htmlWriter.write(startTag(HtmlSyntax.HTML_FIGURE_CAPTION_TAG, getTextNodeAttributes(textNode)));
        htmlWriter.write(getTextFromTextNode(textNode));
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
        String attributes = getTableAttributes(table);
        enterContainer(table);
        enterTable();
        htmlWriter.write(startTag(HtmlSyntax.HTML_TABLE_TAG, attributes));
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
        leaveContainer(table);
    }

    /**
     * Writes a paragraph element to the HTML output.
     *
     * @param paragraph the semantic paragraph to write
     * @throws IOException if unable to write to the output
     */
    protected void writeParagraph(SemanticParagraph paragraph) throws IOException {
        double paragraphIndent = paragraph.getColumns().get(0).getBlocks().get(0).getFirstLineIndent();

        htmlWriter.write(startTag(HtmlSyntax.HTML_PARAGRAPH_TAG, getTextNodeAttributes(paragraph)));
        if (paragraphIndent > 0) {
            htmlWriter.write(HtmlSyntax.HTML_INDENT);
        }
        String paragraphValue = getTextFromTextNode(paragraph);

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
        int headingLevel = Math.min(6, Math.max(1, heading.getHeadingLevel()));
        htmlWriter.write(startTag("<h" + headingLevel + ">", getTextNodeAttributes(heading)));
        htmlWriter.write(getTextFromTextNode(heading));
        htmlWriter.write("</h" + headingLevel + ">");
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
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
        cellTag.append(">");
        htmlWriter.write(startTag(cellTag.toString(), getCellAttributes(cell)));
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

    /** Appends the text of the line. A link at the end of the line ends there. */
    public static void getTextFromLineForHTML(TextLine line, StringBuilder stringBuilder) {
        TextLinks.OpenLink link = new TextLinks.OpenLink();
        getTextFromLineForHTML(line, stringBuilder, link);
        closeLink(link, stringBuilder);
    }

    /**
     * Appends the text of the line. The text of a URI link becomes an {@code <a>} element. A link at the end of the
     * line stays open (see {@link TextLinks.OpenLink}).
     */
    public static void getTextFromLineForHTML(TextLine line, StringBuilder stringBuilder, TextLinks.OpenLink link) {
        getTextFromLineForHTML(line, stringBuilder, link, HtmlGenerator::getTextStyle);
    }

    /**
     * Appends the text of the line, as {@link #getTextFromLineForHTML(TextLine, StringBuilder, TextLinks.OpenLink)}
     * does, with the CSS declarations of each chunk given by {@code textStyle}. A chunk with no declarations
     * (an empty string or null) is written without a span.
     */
    public static void getTextFromLineForHTML(TextLine line, StringBuilder stringBuilder, TextLinks.OpenLink link,
                                              Function<TextChunk, String> textStyle) {
        for (TextChunk chunk : line.getTextChunks()) {
            for (TextLinks.Part part : TextLinks.split(chunk, 0)) {
                if (!link.isContinuedBy(part)) {
                    closeLink(link, stringBuilder);
                    if (part.getUri() != null) {
                        stringBuilder.append(String.format(LINK_START_TAG, escapeHtmlAttribute(part.getUri())));
                        link.setUri(part.getUri());
                    }
                }
                appendText(textStyle.apply(chunk), part.getText(), stringBuilder);
            }
        }
    }

    /** Ends the open link. The whitespace at the end of the text, as a line end, stays after the link. */
    public static void closeLink(TextLinks.OpenLink link, StringBuilder stringBuilder) {
        if (link.getUri() == null) {
            return;
        }
        int textEnd = stringBuilder.length();
        while (textEnd > 0 && Character.isWhitespace(stringBuilder.charAt(textEnd - 1))) {
            textEnd--;
        }
        stringBuilder.insert(textEnd, LINK_CLOSE_TAG);
        link.setUri(null);
    }

    /** Appends the escaped text, in a span with the style if there is one. */
    private static void appendText(String style, String value, StringBuilder stringBuilder) {
        if (style != null && !style.isEmpty()) {
            String styleAttribute = String.format(HtmlSyntax.HTML_STYLE_ATTRIBUTE, style.trim());
            stringBuilder.append(String.format(HtmlSyntax.HTML_SPAN_START_TAG, styleAttribute));
            stringBuilder.append(escapeHtmlText(value));
            stringBuilder.append(HtmlSyntax.HTML_SPAN_CLOSE_TAG);
        } else {
            stringBuilder.append(escapeHtmlText(value));
        }
    }

    /**
     * The CSS declarations of the text decoration of the chunk, or an empty string. A static method is not
     * overridden: a subclass that styles text differently passes its own function to
     * {@link #getTextFromLineForHTML(TextLine, StringBuilder, TextLinks.OpenLink, Function)}, and can start
     * from this one.
     */
    protected static String getTextStyle(TextChunk chunk) {
        StringBuilder style = new StringBuilder();
        if (chunk.getIsStrikethroughText() || chunk.getIsUnderlinedText()) {
            style.append(String.format(HtmlSyntax.HTML_TEXT_DECORATION_STYLE_PROPERTY,
                (chunk.getIsStrikethroughText() ? HtmlSyntax.HTML_STRIKETHROUGH_VALUE : "") +
                    (chunk.getIsUnderlinedText() ? HtmlSyntax.HTML_UNDERLINE_VALUE : "")));
        }
        return style.toString();
    }

    // ===== Extension points =====
    //
    // A subclass can write more into <head>, follow the pages and the elements that hold
    // others, and add attributes to the start tags. An attribute hook returns the text to put
    // in the start tag before its '>', starting with a space (for example ` style="..."`), or
    // an empty string; null is taken as an empty string. The defaults write the HTML that this
    // class writes on its own.
    //
    // The attributes of an element are asked for before the element is entered, so the
    // elements entered at that time are the ones that hold it.

    /**
     * The start tag with the attributes before its closing {@code '>'}.
     *
     * @param tag a start tag that ends with {@code '>'}
     * @param attributes attributes that start with a space, an empty string, or null
     * @return the start tag with the attributes
     */
    protected static String startTag(String tag, String attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return tag;
        }
        return tag.substring(0, tag.length() - 1) + attributes + ">";
    }

    /**
     * Writes more into {@code <head>}, after {@code <title>}. Writes nothing by default.
     *
     * @throws IOException if unable to write to the output
     */
    protected void writeHeadContent() throws IOException {
    }

    /**
     * Called before each page that --pages selects (every page by default), before its separator.
     *
     * @param pageNumber the page number (0-indexed)
     */
    protected void startPage(int pageNumber) {
    }

    /**
     * Called when the writing of an HTML element that holds others starts: the {@code <ul>} of a list or a
     * table of contents, the {@code <li>} of an item of one, or a {@code <table>}. The elements written until
     * {@link #leaveContainer(IObject)} are inside it. An item of a table of contents that is itself a table of
     * contents is entered twice: once for its {@code <li>} and once for the {@code <ul>} inside it.
     *
     * @param container the element
     */
    protected void enterContainer(IObject container) {
    }

    /**
     * Called when the writing of the element given to {@link #enterContainer(IObject)} ends.
     *
     * @param container the element
     */
    protected void leaveContainer(IObject container) {
    }

    /**
     * Attributes of the {@code <ul>} of a list or a table of contents.
     *
     * @param list the list or table of contents
     * @return the attributes; by default a style that turns the bullets off
     */
    protected String getListAttributes(BaseObject list) {
        return LIST_ATTRIBUTES;
    }

    /**
     * Attributes of the {@code <li>} of a list item or an item of a table of contents.
     *
     * @param items the items of the list
     * @param index the index of the item in {@code items}
     * @return the attributes
     */
    protected String getListItemAttributes(List<? extends IObject> items, int index) {
        return "";
    }

    /**
     * Attributes of the {@code <p>} that holds the text of a list item or an item of a table of contents.
     *
     * @param item the item
     * @return the attributes
     */
    protected String getListItemTextAttributes(TextBlock item) {
        return "";
    }

    /**
     * Attributes of the start tag of a paragraph, a heading, or a caption.
     *
     * @param textNode the text node
     * @return the attributes
     */
    protected String getTextNodeAttributes(SemanticTextNode textNode) {
        return "";
    }

    /**
     * Attributes of the {@code <img>} of an image, or the {@code <figure>} of a picture.
     *
     * @param image the image or picture
     * @return the attributes
     */
    protected String getImageAttributes(BaseObject image) {
        return "";
    }

    /**
     * Attributes of the math display {@code <div>} of a formula.
     *
     * @param formula the formula
     * @return the attributes
     */
    protected String getFormulaAttributes(SemanticFormula formula) {
        return "";
    }

    /**
     * Attributes of the {@code <table>} of a table, after its border attribute.
     *
     * @param table the table
     * @return the attributes
     */
    protected String getTableAttributes(TableBorder table) {
        return "";
    }

    /**
     * Attributes of the {@code <td>} or {@code <th>} of a cell, after its span attributes.
     *
     * @param cell the cell
     * @return the attributes
     */
    protected String getCellAttributes(TableBorderCell cell) {
        return "";
    }

    /**
     * The HTML text of the lines of a list item or an item of a table of contents.
     *
     * @param lines the lines
     * @return the text
     */
    protected String getTextFromLines(List<TextLine> lines) {
        return GeneratorUtils.getTextFromLines(lines, OutputType.HTML);
    }

    /**
     * The HTML text of a paragraph, a heading, or a caption.
     *
     * @param textNode the text node
     * @return the text
     */
    protected String getTextFromTextNode(SemanticTextNode textNode) {
        return GeneratorUtils.getTextFromTextNode(textNode, OutputType.HTML);
    }

    @Override
    public void close() throws IOException {
        if (htmlWriter != null) {
            htmlWriter.close();
        }
    }
}

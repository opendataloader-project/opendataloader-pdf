/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.html;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
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
import java.util.List;
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
    /** Whether to embed images as Base64 data URIs. */
    protected boolean embedImages = false;
    /** Format for extracted images (png or jpeg). */
    protected String imageFormat = Config.IMAGE_FORMAT_PNG;
    /** Whether to include page headers and footers in output. */
    protected boolean includeHeaderFooter = false;

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
        this.htmlPageSeparator = config.getHtmlPageSeparator();
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
            htmlWriter.write("<title>" + pdfFileName + "</title>\n");
            htmlWriter.write("</head>\n<body>\n");

            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                writePageSeparator(pageNumber);
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
        htmlWriter.write(HtmlSyntax.HTML_MATH_DISPLAY_TAG);
        htmlWriter.write("\\[");
        htmlWriter.write(getCorrectString(formula.getLatex()));
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
                    String imageString = String.format("<img src=\"%s\" alt=\"figure%d\">", escapedSource, image.getIndex());
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
                    // Use simple alt text
                    String altText = "figure" + picture.getPictureIndex();
                    String escapedSource = escapeHtmlAttribute(imageSource);

                    // Use figure/figcaption pattern for semantic markup
                    htmlWriter.write(HtmlSyntax.HTML_FIGURE_TAG);
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                    String imageString = String.format("<img src=\"%s\" alt=\"%s\">", escapedSource, altText);
                    htmlWriter.write(imageString);
                    htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);

                    // Add figcaption with description if available
                    if (picture.hasDescription()) {
                        htmlWriter.write(HtmlSyntax.HTML_FIGURE_CAPTION_TAG);
                        htmlWriter.write(getCorrectString(picture.getDescription()));
                        htmlWriter.write(HtmlSyntax.HTML_FIGURE_CAPTION_CLOSE_TAG);
                        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
                    }

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
        htmlWriter.write(HtmlSyntax.HTML_UNORDERED_LIST_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        for (ListItem item : list.getListItems()) {
            htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_TAG);

            htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_TAG);
            htmlWriter.write(getCorrectString(item.toString()));
            htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_CLOSE_TAG);

            for (IObject object : item.getContents()) {
                write(object);
            }
            htmlWriter.write(HtmlSyntax.HTML_LIST_ITEM_CLOSE_TAG);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        }
        htmlWriter.write(HtmlSyntax.HTML_UNORDERED_LIST_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
    }

    /**
     * Writes a semantic text node as a figure caption to the HTML output.
     *
     * @param textNode the text node to write
     * @throws IOException if unable to write to the output
     */
    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        htmlWriter.write(HtmlSyntax.HTML_FIGURE_CAPTION_TAG);
        htmlWriter.write(getCorrectString(textNode.getValue()));
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
        enterTable();
        htmlWriter.write(HtmlSyntax.HTML_TABLE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            htmlWriter.write(HtmlSyntax.HTML_TABLE_ROW_TAG);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    boolean isHeader = rowNumber == 0;
                    writeCellTag(cell, isHeader);
                    List<IObject> contents = cell.getContents();
                    if (!contents.isEmpty()) {
                        for (IObject contentItem : contents) {
                            this.write(contentItem);
                        }
                    }
                    if (isHeader) {
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
    }

    /**
     * Writes a paragraph element to the HTML output.
     *
     * @param paragraph the semantic paragraph to write
     * @throws IOException if unable to write to the output
     */
    protected void writeParagraph(SemanticParagraph paragraph) throws IOException {
        String paragraphValue = paragraph.getValue();
        double paragraphIndent = paragraph.getColumns().get(0).getBlocks().get(0).getFirstLineIndent();

        htmlWriter.write(HtmlSyntax.HTML_PARAGRAPH_TAG);
        if (paragraphIndent > 0) {
            htmlWriter.write(HtmlSyntax.HTML_INDENT);
        }

        if (isInsideTable() && StaticContainers.isKeepLineBreaks()) {
            paragraphValue = paragraphValue.replace(HtmlSyntax.HTML_LINE_BREAK, HtmlSyntax.HTML_LINE_BREAK_TAG);
        }

        htmlWriter.write(getCorrectString(paragraphValue));
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
        htmlWriter.write("<h" + headingLevel + ">");
        htmlWriter.write(getCorrectString(heading.getValue()));
        htmlWriter.write("</h" + headingLevel + ">");
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
    }

    private void writeCellTag(TableBorderCell cell, boolean isHeader) throws IOException {
        String tag = isHeader ? "<th" : "<td";
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
        htmlWriter.write(getCorrectString(cellTag.toString()));
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
     * Removes null characters from the given string.
     *
     * @param value the string to process
     * @return the string with null characters removed, or null if input is null
     */
    protected String getCorrectString(String value) {
        if (value != null) {
            return value.replace("\u0000", "");
        }
        return null;
    }

    /**
     * Escapes special characters for use in HTML attributes.
     * Handles quotes, ampersands, less-than, greater-than, and newlines.
     *
     * @param value the string to escape
     * @return the escaped string safe for HTML attribute values
     */
    protected String escapeHtmlAttribute(String value) {
        if (value == null) {
            return null;
        }
        return value
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", " ")
            .replace("\r", "");
    }

    @Override
    public void close() throws IOException {
        if (htmlWriter != null) {
            htmlWriter.close();
        }
    }
}

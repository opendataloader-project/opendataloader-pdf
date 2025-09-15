/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.html;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HtmlGenerator implements Closeable {

    protected static final Logger LOGGER = Logger.getLogger(HtmlGenerator.class.getCanonicalName());
    protected final FileWriter htmlWriter;
    protected final String pdfFileName;
    protected final Path pdfFilePath;
    protected final String figureDirName;
    protected final Path figureDirPath;
    protected ContrastRatioConsumer contrastRatioConsumer;
    protected final String htmlFileName;
    protected final Path htmlFilePath;
    protected int tableNesting = 0;
    private final String password;

    public HtmlGenerator(File inputPdf, String outputFolder, Config config) throws IOException {
        this.pdfFileName = inputPdf.getName();
        this.pdfFilePath = inputPdf.toPath().toAbsolutePath();
        this.password = config.getPassword();
        this.htmlFileName = pdfFileName.substring(0, pdfFileName.length() - 3) + "html";
        this.htmlFilePath = Path.of(outputFolder, htmlFileName);
        this.figureDirName = pdfFileName.substring(0, pdfFileName.length() - 4) + "_figures";
        this.figureDirPath = Path.of(outputFolder, figureDirName);
        this.htmlWriter = new FileWriter(htmlFilePath.toFile(), StandardCharsets.UTF_8);
    }

    public void writeToHtml(List<List<IObject>> contents) {
        try {
            htmlWriter.write("<!DOCTYPE html>\n");
            htmlWriter.write("<html lang=\"und\">\n<head>\n<meta charset=\"utf-8\">\n");
            htmlWriter.write("<title>" + pdfFileName + "</title>\n");
            htmlWriter.write("</head>\n<body>\n");

            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                for (IObject content : contents.get(pageNumber)) {
                    this.write(content);
                }
            }

            htmlWriter.write("\n</body>\n</html>");
            System.out.println("Created " + htmlFilePath.toString());
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create html output: " + e.getMessage());
        }
    }

    protected void write(IObject object) throws IOException {
        if (object instanceof ImageChunk) {
            writeImage((ImageChunk) object);
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

    protected void writeImage(ImageChunk image) throws IOException {
        int currentImageIndex = StaticLayoutContainers.incrementImageIndex();
        if (currentImageIndex == 1) {
            figureDirPath.toFile().mkdirs();
            contrastRatioConsumer = new ContrastRatioConsumer(this.pdfFilePath.toString(), password, false, null);
        }

        String figureFileName = String.format(HtmlSyntax.IMAGE_FILE_NAME_FORMAT, currentImageIndex);
        Path figureFilePath = figureDirPath.resolve(figureFileName);
        boolean isFileCreated = createImageFile(image, figureFilePath.toString());
        if (isFileCreated) {
            String relativePathName = figureFilePath.subpath(figureDirPath.getNameCount() - 1,
                figureDirPath.getNameCount() + 1).toString().replace("\\", "/");
            String imageString = String.format("<img src=\"%s\" alt=\"figure%d\">", relativePathName, currentImageIndex);
            htmlWriter.write(imageString);
            htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
        }
        //add alt image
    }

    protected boolean createImageFile(ImageChunk image, String fileName) {
        try {
            BoundingBox imageBox = image.getBoundingBox();
            BufferedImage targetImage = contrastRatioConsumer.getPageSubImage(imageBox);
            if (targetImage == null) {
                return false;
            }

            File outputFile = new File(fileName);
            ImageIO.write(targetImage, "png", outputFile);
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create image files: " + e.getMessage());
            return false;
        }
    }

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

    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        htmlWriter.write(HtmlSyntax.HTML_FIGURE_CAPTION_TAG);
        htmlWriter.write(getCorrectString(textNode.getValue()));
        htmlWriter.write(HtmlSyntax.HTML_FIGURE_CAPTION_CLOSE_TAG);
        htmlWriter.write(HtmlSyntax.HTML_LINE_BREAK);
    }

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

    protected void enterTable() {
        tableNesting++;
    }

    protected void leaveTable() {
        if (tableNesting > 0) {
            tableNesting--;
        }
    }

    protected boolean isInsideTable() {
        return tableNesting > 0;
    }

    protected String getCorrectString(String value) {
        if (value != null) {
            return value.replace("\u0000", "");
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (htmlWriter != null) {
            htmlWriter.close();
        }

        if (contrastRatioConsumer != null) {
            contrastRatioConsumer.close();
        }
    }
}

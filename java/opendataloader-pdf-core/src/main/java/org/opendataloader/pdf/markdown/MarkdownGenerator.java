/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.markdown;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarkdownGenerator implements Closeable {

    protected static final Logger LOGGER = Logger.getLogger(MarkdownGenerator.class.getCanonicalName());
    protected final FileWriter markdownWriter;
    protected final String pdfFileName;
    protected final String imageDirectoryName;
    protected ContrastRatioConsumer contrastRatioConsumer;
    protected final String markdownFileName;
    protected int tableNesting = 0;
    protected boolean isImageSupported;
    private final String password;

    MarkdownGenerator(File inputPdf, String outputFolder, Config config) throws IOException {
        String cutPdfFileName = inputPdf.getName();
        this.markdownFileName = outputFolder + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 3) + "md";
        this.imageDirectoryName = outputFolder + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 4) + "_images";
        this.pdfFileName = inputPdf.getAbsolutePath();
        this.markdownWriter = new FileWriter(markdownFileName, StandardCharsets.UTF_8);
        this.isImageSupported = config.isAddImageToMarkdown();
        this.password = config.getPassword();
    }

    public void writeToMarkdown(List<List<IObject>> contents) {
        try {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                for (IObject content : contents.get(pageNumber)) {
                    if (!isSupportedContent(content)) {
                        continue;
                    }
                    this.write(content);
                    writeContentsSeparator();
                }
            }

            LOGGER.log(Level.INFO, "Created {0}", markdownFileName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create markdown output: " + e.getMessage());
        }
    }

    protected boolean isSupportedContent(IObject content) {
        return content instanceof SemanticTextNode || // Heading, Paragraph etc...
            content instanceof TableBorder ||
            content instanceof PDFList ||
            (content instanceof ImageChunk && isImageSupported);
    }

    protected void writeContentsSeparator() throws IOException {
        writeLineBreak();
        writeLineBreak();
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
        }
    }

    protected void writeImage(ImageChunk image) throws IOException {
        int currentImageIndex = StaticLayoutContainers.incrementImageIndex();
        if (currentImageIndex == 1) {
            new File(imageDirectoryName).mkdirs();
            contrastRatioConsumer = StaticLayoutContainers.getContrastRatioConsumer(this.pdfFileName, password, false, null);
        }

        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, imageDirectoryName, File.separator, currentImageIndex);
        boolean isFileCreated = createImageFile(image, fileName);
        if (isFileCreated) {
            String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, "image " + currentImageIndex, fileName);
            markdownWriter.write(getCorrectMarkdownString(imageString));
        }
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
        for (ListItem item : list.getListItems()) {
            if (!isInsideTable()) {
                markdownWriter.write(MarkdownSyntax.LIST_ITEM);
                markdownWriter.write(MarkdownSyntax.SPACE);
            }
            markdownWriter.write(getCorrectMarkdownString(item.toString()));
            writeLineBreak();

            List<IObject> itemContents = item.getContents();
            if (!itemContents.isEmpty()) {
                writeLineBreak();
                writeContents(itemContents);
            }
        }
    }

    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        String value = textNode.getValue();
        if (isInsideTable() && StaticContainers.isKeepLineBreaks()) {
            value = value.replace(MarkdownSyntax.LINE_BREAK, getLineBreak());
        }

        markdownWriter.write(getCorrectMarkdownString(value));
    }

    protected void writeTable(TableBorder table) throws IOException {
        enterTable();
        for (TableBorderRow row : table.getRows()) {
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (TableBorderCell cell : row.getCells()) {
                List<IObject> cellContents = cell.getContents();
                writeContents(cellContents);
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            }
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            //Due to markdown syntax we have to separate column headers
            if (row.getRowNumber() == 0) {
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                for (int i = 0; i < table.getNumberOfColumns(); i++) {
                    markdownWriter.write(MarkdownSyntax.TABLE_HEADER_SEPARATOR);
                    markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                }
                markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            }
        }
        leaveTable();
    }

    protected void writeContents(List<IObject> contents) throws IOException {
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (!isSupportedContent(content)) {
                continue;
            }
            this.write(content);

            boolean isLastContent = i == contents.size() - 1;
            if (!isLastContent) {
                writeContentsSeparator();
            }
        }
    }

    protected void writeParagraph(SemanticParagraph textNode) throws IOException {
        writeSemanticTextNode(textNode);
    }

    protected void writeHeading(SemanticHeading heading) throws IOException {
        if (!isInsideTable()) {
            int headingLevel = heading.getHeadingLevel();
            for (int i = 0; i < headingLevel; i++) {
                markdownWriter.write(MarkdownSyntax.HEADING_LEVEL);
            }
            markdownWriter.write(MarkdownSyntax.SPACE);
        }
        writeSemanticTextNode(heading);
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

    protected String getLineBreak() {
        if (isInsideTable()) {
            return MarkdownSyntax.HTML_LINE_BREAK_TAG;
        } else {
            return MarkdownSyntax.LINE_BREAK;
        }
    }

    protected void writeLineBreak() throws IOException {
        markdownWriter.write(getLineBreak());
    }

    protected String getCorrectMarkdownString(String value) {
        if (value != null) {
            return value.replace("\u0000", " ");
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (markdownWriter != null) {
            markdownWriter.close();
        }

        if (contrastRatioConsumer != null) {
            contrastRatioConsumer.close();
        }
    }
}

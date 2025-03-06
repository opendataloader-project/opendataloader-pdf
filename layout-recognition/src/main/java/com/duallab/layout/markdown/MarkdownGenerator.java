/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.markdown;

import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.utils.Config;
import com.duallab.wcag.algorithms.entities.IObject;
import com.duallab.wcag.algorithms.entities.SemanticHeading;
import com.duallab.wcag.algorithms.entities.SemanticParagraph;
import com.duallab.wcag.algorithms.entities.SemanticTextNode;
import com.duallab.wcag.algorithms.entities.content.ImageChunk;
import com.duallab.wcag.algorithms.entities.geometry.BoundingBox;
import com.duallab.wcag.algorithms.entities.lists.ListItem;
import com.duallab.wcag.algorithms.entities.lists.PDFList;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import com.duallab.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;
import com.duallab.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
        this.markdownWriter = new FileWriter(markdownFileName);
        this.isImageSupported = config.isAddImageToMarkdown();
        this.password = config.getPassword();
    }

    public void writeToMarkdown(List<List<IObject>> contents) {
        try {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                for (IObject content : contents.get(pageNumber)) {
                    this.write(content);
                }
            }

            System.out.println("Created " + markdownFileName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create markdown output: " + e.getMessage());
        }
    }

    protected void write(IObject object) throws IOException {
        if (object instanceof ImageChunk ) {
            if (isImageSupported) {
                writeImage((ImageChunk) object);
            } else {
                return;
            }
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
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }
    }

    protected void writeImage(ImageChunk image) throws IOException {
        int currentImageIndex = StaticLayoutContainers.incrementImageIndex();
        if (currentImageIndex == 1) {
            new File(imageDirectoryName).mkdirs();
            contrastRatioConsumer = new ContrastRatioConsumer(this.pdfFileName, password);
        }

        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, imageDirectoryName, File.separator, currentImageIndex);
        boolean isFileCreated = createImageFile(image, fileName);
        if (isFileCreated) {
            String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, "image " + currentImageIndex, fileName);
            markdownWriter.write(imageString);
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
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (ListItem item : list.getListItems()) {
            markdownWriter.write(item.toString());
            for (IObject object : item.getContents()) {
                write(object);
            }

            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }
    }

    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        markdownWriter.write(textNode.getValue());
    }

    protected void writeTable(TableBorder table) throws IOException {
        enterTable();
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (TableBorderRow row : table.getRows()) {
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (TableBorderCell cell : row.getCells()) {
                for (IObject object : cell.getContents()) {
                    this.write(object);
                }

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

    protected void writeParagraph(SemanticParagraph textNode) throws IOException {
        String value = textNode.getValue();
        if (isInsideTable() && StaticContainers.isKeepLineBreaks()) {
            value = value.replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
        }

        markdownWriter.write(value);
    }

    protected void writeHeading(SemanticHeading heading) throws IOException {
        int headingLevel = heading.getHeadingLevel();
        for (int i = 0; i < headingLevel; i++) {
            markdownWriter.write(MarkdownSyntax.HEADING_LEVEL);
        }

        markdownWriter.write(MarkdownSyntax.SPACE);
        markdownWriter.write(heading.getValue());
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

package com.duallab.layout.markdown;

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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MarkdownGenerator implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(MarkdownGenerator.class.getCanonicalName());
    private final FileWriter markdownWriter;
    private final String pdfFileName;
    private static int imageIndex = 1;
    private final String directory;
    private ContrastRatioConsumer contrastRatioConsumer;

    private MarkdownGenerator(String pdfFileName, String fileName) throws IOException {
        directory = fileName.substring(0, fileName.lastIndexOf("\\"));
        markdownWriter = new FileWriter(fileName);
        this.pdfFileName = pdfFileName;
    }

    public static void writeToMarkdown(String pdfFileName, String outputName, List<List<IObject>> contents) {
        String markdownFileName = outputName.substring(0, outputName.length() - 3) + "md";
        try (MarkdownGenerator generator = new MarkdownGenerator(pdfFileName, markdownFileName)) {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                for (IObject content : contents.get(pageNumber)) {
                    generator.write(content, false);
                }
            }

            System.out.println("Created " + markdownFileName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create markdown output: " + e.getMessage());
        }
    }

    private void write(IObject object, boolean isTable) throws IOException {
        if (object instanceof ImageChunk) {
            writeImage((ImageChunk) object);
        } else if (object instanceof SemanticHeading) {
            writeHeading((SemanticHeading) object);
        } else if (object instanceof SemanticParagraph) {
            writeParagraph((SemanticParagraph) object, isTable);
        } else if (object instanceof SemanticTextNode) {
            writeSemanticTextNode((SemanticTextNode) object);
        } else if (object instanceof TableBorder) {
            writeTable((TableBorder) object);
        } else if (object instanceof PDFList) {
            writeList((PDFList) object);
        } else {
            return;
        }

        if (!isTable) {
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }
    }

    private void writeImage(ImageChunk image) throws IOException {
        int currentImageIndex = imageIndex++;
        if (currentImageIndex == 1) {
            new File(directory + "/images").mkdirs();
            contrastRatioConsumer = new ContrastRatioConsumer(this.pdfFileName);
        }

        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, directory, currentImageIndex);
        boolean isFileCreated = createImageFile(image, fileName);
        if (isFileCreated) {
            String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, "image " + currentImageIndex, fileName);
            markdownWriter.write(imageString);
        }
    }

    private boolean createImageFile(ImageChunk image, String fileName) {
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

    private void writeList(PDFList list) throws IOException {
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (ListItem item : list.getListItems()) {
            markdownWriter.write(item.toString());
            for (IObject object : item.getContents()) {
                write(object, false);
            }

            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }
    }

    private void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        markdownWriter.write(textNode.getValue());
    }

    private void writeTable(TableBorder table) throws IOException {
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (TableBorderRow row : table.getRows()) {
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (TableBorderCell cell : row.getCells()) {
                for (IObject object : cell.getContents()) {
                    this.write(object, true);
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
    }

    private void writeParagraph(SemanticParagraph textNode, boolean isTable) throws IOException {
        String value;
        if (isTable) {
            value = textNode.getValue().replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
        } else {
            value = textNode.getValue();
        }

        markdownWriter.write(value);
    }

    private void writeHeading(SemanticHeading heading) throws IOException {
        int headingLevel = heading.getHeadingLevel();
        for (int i = 0; i < headingLevel; i++) {
            markdownWriter.write(MarkdownSyntax.HEADING_LEVEL);
        }

        markdownWriter.write(MarkdownSyntax.SPACE);
        markdownWriter.write(heading.getValue());
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

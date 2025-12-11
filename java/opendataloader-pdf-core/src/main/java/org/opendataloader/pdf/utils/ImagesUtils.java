package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
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
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImagesUtils {
    private static final Logger LOGGER = Logger.getLogger(ImagesUtils.class.getCanonicalName());
    private static ContrastRatioConsumer contrastRatioConsumer;

    public static ContrastRatioConsumer getContrastRatioConsumer() {
        return contrastRatioConsumer;
    }

    public static void createImagesDirectory(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public static void write(List<List<IObject>> contents, String pdfFilePath, String password) {
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            for (IObject content : contents.get(pageNumber)) {
                writeFromContents(content, pdfFilePath, password);
            }
        }
    }

    private static void writeFromContents(IObject content, String pdfFilePath, String password) {
        if (content instanceof ImageChunk) {
            writeImage(content, pdfFilePath, password);
        } else if (content instanceof PDFList) {
            for (ListItem listItem : ((PDFList) content).getListItems()) {
                for (IObject item : listItem.getContents()) {
                    writeFromContents(item, pdfFilePath, password);
                }
            }
        } else if (content instanceof TableBorder) {
            for (TableBorderRow row : ((TableBorder) content).getRows()) {
                TableBorderCell[] cells = row.getCells();
                for (int columnNumber = 0; columnNumber < cells.length; columnNumber++) {
                    TableBorderCell cell = cells[columnNumber];
                    if (cell.getColNumber() == columnNumber && cell.getRowNumber() == row.getRowNumber()) {
                        for (IObject item : cell.getContents()) {
                            writeFromContents(item, pdfFilePath, password);
                        }
                    }
                }
            }
        } else if (content instanceof SemanticHeaderOrFooter) {
            for (IObject item : ((SemanticHeaderOrFooter) content).getContents()) {
                writeFromContents(item, pdfFilePath, password);
            }
        }
    }

    protected static void writeImage(IObject content, String pdfFilePath, String password) {
        int currentImageIndex = StaticLayoutContainers.incrementImageIndex();
        if (currentImageIndex == 1) {
            createImagesDirectory(StaticLayoutContainers.getImagesDirectory());
            contrastRatioConsumer = StaticLayoutContainers.getContrastRatioConsumer(pdfFilePath, password, false, null);
        }
        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, currentImageIndex);
        content.setIndex(currentImageIndex);
        createImageFile((ImageChunk) content, fileName);
    }

    private static void createImageFile(ImageChunk image, String fileName) {
        try {
            File outputFile = new File(fileName);
            BoundingBox imageBox = image.getBoundingBox();
            BufferedImage targetImage = contrastRatioConsumer != null ? contrastRatioConsumer.getPageSubImage(imageBox) : null;
            if (targetImage == null) {
                return;
            }
            ImageIO.write(targetImage, "png", outputFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create image files: " + e.getMessage());
        }
    }

    public static boolean isImageFileExists(String fileName) {
        File outputFile = new File(fileName);
        return outputFile.exists();
    }
}

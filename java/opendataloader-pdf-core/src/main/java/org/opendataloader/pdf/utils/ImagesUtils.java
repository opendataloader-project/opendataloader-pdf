package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
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

    public static void writeImages(List<List<IObject>> contents, String pdfFilePath, String password) {
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            for (IObject content : contents.get(pageNumber)) {
                write(content, pdfFilePath, password);
            }
        }
    }

    protected static void write(IObject content, String pdfFilePath, String password) {
        if (content instanceof ImageChunk) {
            int currentImageIndex = StaticLayoutContainers.incrementImageIndex();
            if (currentImageIndex == 1) {
                createImagesDirectory(StaticLayoutContainers.getImagesDirectory());
                contrastRatioConsumer = StaticLayoutContainers.getContrastRatioConsumer(pdfFilePath, password, false, null);
            }
            String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, currentImageIndex);
            createImageFile((ImageChunk) content, fileName);
        }
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

    public static boolean checkIfImageFileExists(String fileName) {
        File outputFile = new File(fileName);
        return outputFile.exists();
    }
}

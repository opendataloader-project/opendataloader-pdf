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
package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticPicture;
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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImagesUtils {
    private static final Logger LOGGER = Logger.getLogger(ImagesUtils.class.getCanonicalName());

    /**
     * Tracks whether the images output directory has already been created for this
     * ImagesUtils instance, so we only invoke {@link #createImagesDirectory(String)}
     * once per document — matching the previous behaviour that piggy-backed on the
     * (now removed) {@code contrastRatioConsumer} field as a "first call" sentinel.
     */
    private boolean imagesDirectoryInitialized = false;

    public void createImagesDirectory(String path) {
        // Embedded mode is self-contained: no external image directory is created.
        if (StaticLayoutContainers.isEmbedImages()) {
            return;
        }
        File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public void write(List<List<IObject>> contents, String pdfFilePath, String password) {
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            for (IObject content : contents.get(pageNumber)) {
                writeFromContents(content, pdfFilePath, password);
            }
        }
    }

    private void writeFromContents(IObject content, String pdfFilePath, String password) {
        if (content instanceof ImageChunk) {
            writeImage((ImageChunk) content, pdfFilePath, password);
        } else if (content instanceof SemanticPicture) {
            writePicture((SemanticPicture) content, pdfFilePath, password);
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

    protected void writeImage(ImageChunk chunk, String pdfFilePath, String password) {
        int currentImageIndex = StaticLayoutContainers.incrementImageIndex();
        ensureImagesDirectoryInitialized();
        String imageFormat = StaticLayoutContainers.getImageFormat();
        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, currentImageIndex, imageFormat);
        chunk.setIndex(currentImageIndex);
        createImageFile(chunk.getBoundingBox(), fileName, imageFormat, pdfFilePath, password);
    }

    protected void writePicture(SemanticPicture picture, String pdfFilePath, String password) {
        int pictureIndex = picture.getPictureIndex();
        ensureImagesDirectoryInitialized();
        String imageFormat = StaticLayoutContainers.getImageFormat();
        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, pictureIndex, imageFormat);
        createImageFile(picture.getBoundingBox(), fileName, imageFormat, pdfFilePath, password);
    }

    private void ensureImagesDirectoryInitialized() {
        if (!imagesDirectoryInitialized) {
            createImagesDirectory(StaticLayoutContainers.getImagesDirectory());
            imagesDirectoryInitialized = true;
        }
    }

    /**
     * Issue #458 hotfix: no longer keeps a long-lived reference to
     * {@link ContrastRatioConsumer} on this instance. The consumer is
     * retrieved per call from {@link StaticLayoutContainers} (which caches
     * it in a ThreadLocal), used to fetch the page sub-image, and then
     * dropped — letting the JVM reclaim the rendered BufferedImage as
     * soon as {@link #writeBufferedImageToFile} returns.
     */
    private void createImageFile(BoundingBox imageBox, String fileName, String imageFormat,
                                 String pdfFilePath, String password) {
        ContrastRatioConsumer consumer = StaticLayoutContainers.getContrastRatioConsumer(pdfFilePath, password, false, null);
        BufferedImage targetImage = consumer != null ? consumer.getPageSubImage(imageBox) : null;
        if (targetImage == null) {
            return;
        }
        writeBufferedImageToFile(targetImage, fileName, imageFormat);
    }

    /**
     * Writes the given {@link BufferedImage} to disk (or embed cache) and
     * always invokes {@link BufferedImage#flush()} afterwards — even on
     * I/O failure — so the raster data and any cached resources become
     * eligible for GC.
     *
     * Hotfix for issue #458: without flush(), large page-sub-images
     * accumulate on the heap and trigger {@code OutOfMemoryError}.
     *
     * Package-private to allow direct unit testing of the flush contract.
     */
    void writeBufferedImageToFile(BufferedImage targetImage, String fileName, String imageFormat) {
        try {
            try {
                if (StaticLayoutContainers.isEmbedImages()) {
                    // Embedded mode: encode in memory and cache for downstream base64 inlining;
                    // no disk write — output is a single self-contained file.
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    ImageIO.write(targetImage, imageFormat, buffer);
                    StaticLayoutContainers.cacheEmbeddedImageBytes(fileName, buffer.toByteArray());
                } else {
                    File outputFile = new File(fileName);
                    ImageIO.write(targetImage, imageFormat, outputFile);
                }
            } catch (IOException e) {
                // Same catch covers both branches — encoding-to-buffer (embedded)
                // and writing-to-disk (external). Keep the message neutral.
                LOGGER.log(Level.WARNING, "Unable to encode image: " + e.getMessage());
            }
        } finally {
            // Issue #458: release raster + cached resources so they can be GC'd
            // even if encoding/writing fails.
            targetImage.flush();
        }
    }

    public static boolean isImageFileExists(String fileName) {
        if (StaticLayoutContainers.isEmbedImages() && StaticLayoutContainers.hasEmbeddedImageBytes(fileName)) {
            return true;
        }
        File outputFile = new File(fileName);
        return outputFile.exists();
    }
}

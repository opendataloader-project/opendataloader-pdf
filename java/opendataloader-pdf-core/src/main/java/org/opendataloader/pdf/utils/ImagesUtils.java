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

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
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
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import javax.imageio.ImageIO;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImagesUtils implements AutoCloseable {
    private static final Logger LOGGER = Logger.getLogger(ImagesUtils.class.getCanonicalName());

    private org.apache.pdfbox.pdmodel.PDDocument pdfBoxDocument;
    private final Map<Integer, List<PageImageReference>> pageImagesByNumber = new HashMap<>();
    private String loadedPdfFilePath;
    private String loadedPassword;

    public void createImagesDirectory(String path) {
        File directory = new File(path);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    public void write(List<List<IObject>> contents, String pdfFilePath, String password) {
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            try {
                for (IObject content : contents.get(pageNumber)) {
                    writeFromContents(content, pdfFilePath, password);
                }
            } finally {
                releasePageImages(pageNumber);
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
        if (currentImageIndex == 1) {
            createImagesDirectory(StaticLayoutContainers.getImagesDirectory());
        }
        String imageFormat = StaticLayoutContainers.getImageFormat();
        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, currentImageIndex, imageFormat);
        chunk.setIndex(currentImageIndex);
        createImageFile(chunk.getBoundingBox(), fileName, imageFormat, pdfFilePath, password);
    }

    protected void writePicture(SemanticPicture picture, String pdfFilePath, String password) {
        int pictureIndex = picture.getPictureIndex();
        if (pictureIndex == 1) {
            createImagesDirectory(StaticLayoutContainers.getImagesDirectory());
        }
        String imageFormat = StaticLayoutContainers.getImageFormat();
        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, pictureIndex, imageFormat);
        createImageFile(picture.getBoundingBox(), fileName, imageFormat, pdfFilePath, password);
    }

    private void createImageFile(BoundingBox imageBox, String fileName, String imageFormat, String pdfFilePath, String password) {
        try {
            File outputFile = new File(fileName);
            BufferedImage targetImage = extractPageImage(imageBox, pdfFilePath, password);
            if (targetImage == null) {
                return;
            }
            ImageIO.write(targetImage, imageFormat, outputFile);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create image files: " + e.getMessage());
        }
    }

    protected BufferedImage extractPageImage(BoundingBox imageBox, String pdfFilePath, String password) throws IOException {
        if (imageBox == null || imageBox.getPageNumber() == null) {
            return null;
        }

        ensurePdfDocument(pdfFilePath, password);
        int pageIndex = imageBox.getPageNumber();
        if (pageIndex < 0 || pageIndex >= pdfBoxDocument.getNumberOfPages()) {
            return null;
        }

        PageImageReference pageImage = findBestPageImage(imageBox, pageIndex);
        if (pageImage == null) {
            return null;
        }

        pageImage.markUsed();
        return pageImage.getImage();
    }

    private PageImageReference findBestPageImage(BoundingBox targetBoundingBox, int pageIndex) throws IOException {
        List<PageImageReference> pageImages = getPageImages(pageIndex);
        PageImageReference bestOverlapMatch = null;
        double bestOverlapArea = 0.0;
        double bestAreaDelta = Double.MAX_VALUE;
        for (PageImageReference pageImage : pageImages) {
            if (pageImage.isUsed()) {
                continue;
            }
            double overlapArea = getIntersectionArea(targetBoundingBox, pageImage.getBoundingBox());
            if (overlapArea <= 0.0) {
                continue;
            }
            double areaDelta = getAreaDeltaRatio(targetBoundingBox, pageImage.getBoundingBox());
            if (bestOverlapMatch == null || overlapArea > bestOverlapArea
                || (Double.compare(overlapArea, bestOverlapArea) == 0 && areaDelta < bestAreaDelta)) {
                bestOverlapMatch = pageImage;
                bestOverlapArea = overlapArea;
                bestAreaDelta = areaDelta;
            }
        }
        if (bestOverlapMatch != null) {
            return bestOverlapMatch;
        }
        return null;
    }

    private List<PageImageReference> getPageImages(int pageIndex) throws IOException {
        List<PageImageReference> pageImages = pageImagesByNumber.get(pageIndex);
        if (pageImages != null) {
            return pageImages;
        }

        List<PageImageReference> extractedImages = new ArrayList<>();
        PDPage page = pdfBoxDocument.getPage(pageIndex);
        new PageImageCollector(page, pageIndex, extractedImages).processPage(page);
        pageImagesByNumber.put(pageIndex, extractedImages);
        return extractedImages;
    }

    private double getIntersectionArea(BoundingBox first, BoundingBox second) {
        double left = Math.max(first.getLeftX(), second.getLeftX());
        double right = Math.min(first.getRightX(), second.getRightX());
        double bottom = Math.max(first.getBottomY(), second.getBottomY());
        double top = Math.min(first.getTopY(), second.getTopY());
        if (right <= left || top <= bottom) {
            return 0.0;
        }
        return (right - left) * (top - bottom);
    }

    private double getAreaDeltaRatio(BoundingBox first, BoundingBox second) {
        double firstArea = getBoundingBoxArea(first);
        double secondArea = getBoundingBoxArea(second);
        double largerArea = Math.max(1.0, Math.max(firstArea, secondArea));
        return Math.abs(firstArea - secondArea) / largerArea;
    }

    private double getBoundingBoxArea(BoundingBox boundingBox) {
        return Math.max(0.0, boundingBox.getWidth()) * Math.max(0.0, boundingBox.getHeight());
    }

    private void ensurePdfDocument(String pdfFilePath, String password) throws IOException {
        if (pdfBoxDocument != null) {
            if (Objects.equals(loadedPdfFilePath, pdfFilePath) && Objects.equals(loadedPassword, password)) {
                return;
            }
            releaseAllPageImages();
            closePdfDocument();
        }
        File pdfFile = new File(pdfFilePath);
        pdfBoxDocument = password != null && !password.isEmpty()
            ? Loader.loadPDF(pdfFile, password)
            : Loader.loadPDF(pdfFile);
        loadedPdfFilePath = pdfFilePath;
        loadedPassword = password;
    }

    private void releasePageImages(int pageIndex) {
        List<PageImageReference> pageImages = pageImagesByNumber.remove(pageIndex);
        if (pageImages == null) {
            return;
        }
        for (PageImageReference pageImage : pageImages) {
            pageImage.release();
        }
    }

    private void releaseAllPageImages() {
        for (List<PageImageReference> pageImages : pageImagesByNumber.values()) {
            for (PageImageReference pageImage : pageImages) {
                pageImage.release();
            }
        }
        pageImagesByNumber.clear();
    }

    private void closePdfDocument() throws IOException {
        if (pdfBoxDocument == null) {
            return;
        }
        try {
            pdfBoxDocument.close();
        } finally {
            pdfBoxDocument = null;
            loadedPdfFilePath = null;
            loadedPassword = null;
        }
    }

    public static boolean isImageFileExists(String fileName) {
        File outputFile = new File(fileName);
        return outputFile.exists();
    }

    @Override
    public void close() throws IOException {
        releaseAllPageImages();
        if (pdfBoxDocument != null) {
            closePdfDocument();
        }
    }

    private static final class PageImageReference {
        private final PDImage image;
        private final BoundingBox boundingBox;
        private BufferedImage bufferedImage;
        private boolean used;

        private PageImageReference(PDImage image, BoundingBox boundingBox) {
            this.image = image;
            this.boundingBox = boundingBox;
        }

        private BoundingBox getBoundingBox() {
            return boundingBox;
        }

        private boolean isUsed() {
            return used;
        }

        private void markUsed() {
            used = true;
        }

        private BufferedImage getImage() throws IOException {
            if (bufferedImage == null) {
                if (image instanceof PDImageXObject) {
                    bufferedImage = ((PDImageXObject) image).getOpaqueImage();
                } else {
                    bufferedImage = image.getImage();
                }
            }
            return bufferedImage;
        }

        private void release() {
            if (bufferedImage != null) {
                bufferedImage.flush();
                bufferedImage = null;
            }
        }
    }

    private static final class PageImageCollector extends PDFGraphicsStreamEngine {
        private final int pageIndex;
        private final List<PageImageReference> pageImages;
        private Point2D currentPoint = new Point2D.Float();

        private PageImageCollector(PDPage page, int pageIndex, List<PageImageReference> pageImages) {
            super(page);
            this.pageIndex = pageIndex;
            this.pageImages = pageImages;
        }

        @Override
        public void drawImage(PDImage pdImage) {
            Point2D lowerLeft = transformedPoint(0, 0);
            Point2D lowerRight = transformedPoint(1, 0);
            Point2D upperLeft = transformedPoint(0, 1);
            Point2D upperRight = transformedPoint(1, 1);

            double left = Math.min(Math.min(lowerLeft.getX(), lowerRight.getX()), Math.min(upperLeft.getX(), upperRight.getX()));
            double right = Math.max(Math.max(lowerLeft.getX(), lowerRight.getX()), Math.max(upperLeft.getX(), upperRight.getX()));
            double bottom = Math.min(Math.min(lowerLeft.getY(), lowerRight.getY()), Math.min(upperLeft.getY(), upperRight.getY()));
            double top = Math.max(Math.max(lowerLeft.getY(), lowerRight.getY()), Math.max(upperLeft.getY(), upperRight.getY()));

            pageImages.add(new PageImageReference(pdImage, new BoundingBox(pageIndex, left, bottom, right, top)));
        }

        @Override
        public void appendRectangle(Point2D point0, Point2D point1, Point2D point2, Point2D point3) {
        }

        @Override
        public void clip(int windingRule) {
        }

        @Override
        public void moveTo(float x, float y) {
            currentPoint = new Point2D.Float(x, y);
        }

        @Override
        public void lineTo(float x, float y) {
            currentPoint = new Point2D.Float(x, y);
        }

        @Override
        public void curveTo(float x1, float y1, float x2, float y2, float x3, float y3) {
            currentPoint = new Point2D.Float(x3, y3);
        }

        @Override
        public Point2D getCurrentPoint() {
            return currentPoint;
        }

        @Override
        public void closePath() {
        }

        @Override
        public void endPath() {
        }

        @Override
        public void strokePath() {
        }

        @Override
        public void fillPath(int windingRule) {
        }

        @Override
        public void fillAndStrokePath(int windingRule) {
        }

        @Override
        public void shadingFill(COSName shadingName) {
        }
    }
}

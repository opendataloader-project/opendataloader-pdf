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
package org.opendataloader.pdf.hybrid;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Writes one-page PDFs shaped like scanner output: the page holds a single raster
 * image of text-like bars and no text layer, apart from an optional stamp.
 */
public final class ScannedPagePdf {

    private static final float PIXELS_PER_POINT = 1.5f;
    private static final float STAMP_FONT_SIZE = 8f;
    private static final float STAMP_OFFSET = 12f;
    private static final byte WHITE = (byte) 0xFF;
    private static final byte BLACK = 0;

    private final PDRectangle pageSize;
    private int rotation;
    private boolean jpeg;
    private double imageAreaFraction = 1.0;
    private String stamp;

    private ScannedPagePdf(PDRectangle pageSize) {
        this.pageSize = pageSize;
    }

    public static ScannedPagePdf of(PDRectangle pageSize) {
        return new ScannedPagePdf(pageSize);
    }

    public static PDRectangle landscape(PDRectangle portrait) {
        return new PDRectangle(portrait.getHeight(), portrait.getWidth());
    }

    /** Sets the page's /Rotate entry, as scanners do for pages fed sideways. */
    public ScannedPagePdf rotatedBy(int degrees) {
        this.rotation = degrees;
        return this;
    }

    public ScannedPagePdf encodedAsJpeg() {
        this.jpeg = true;
        return this;
    }

    /** Centres the image and scales both sides so that it covers this fraction of the page area. */
    public ScannedPagePdf imageCovering(double areaFraction) {
        this.imageAreaFraction = areaFraction;
        return this;
    }

    /** Adds one line of real text, like the watermark scanner apps put on every page. */
    public ScannedPagePdf stampedWith(String text) {
        this.stamp = text;
        return this;
    }

    public Path writeTo(Path file) throws IOException {
        float sideScale = (float) Math.sqrt(imageAreaFraction);
        float imageWidth = pageSize.getWidth() * sideScale;
        float imageHeight = pageSize.getHeight() * sideScale;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(pageSize);
            page.setRotation(rotation);
            document.addPage(page);
            BufferedImage raster = renderTextLines(
                    Math.round(imageWidth * PIXELS_PER_POINT), Math.round(imageHeight * PIXELS_PER_POINT));
            PDImageXObject image = jpeg
                    ? JPEGFactory.createFromImage(document, raster)
                    : LosslessFactory.createFromImage(document, raster);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawImage(image,
                        (pageSize.getWidth() - imageWidth) / 2, (pageSize.getHeight() - imageHeight) / 2,
                        imageWidth, imageHeight);
                if (stamp != null) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), STAMP_FONT_SIZE);
                    content.newLineAtOffset(STAMP_OFFSET, STAMP_OFFSET);
                    content.showText(stamp);
                    content.endText();
                }
            }
            document.save(file.toFile());
        }
        return file;
    }

    /**
     * Fills the pixel buffer directly rather than through {@code Graphics2D}, which on
     * macOS connects to the window server and can hang where that is unavailable.
     */
    private static BufferedImage renderTextLines(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
        byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        Arrays.fill(pixels, WHITE);
        int lineHeight = Math.max(2, height / 80);
        for (int lineTop = height / 10; lineTop < height * 9 / 10; lineTop += lineHeight * 3) {
            for (int y = lineTop; y < lineTop + lineHeight; y++) {
                Arrays.fill(pixels, y * width + width / 10, y * width + width * 9 / 10, BLACK);
            }
        }
        return image;
    }
}

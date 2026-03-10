/*
 * Copyright 2025 Hancom Inc.
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
package org.opendataloader.pdf.containers;

import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import java.awt.*;
import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StaticLayoutContainers {
    protected static final Logger LOGGER = Logger.getLogger(StaticLayoutContainers.class.getCanonicalName());

    private static final ThreadLocal<Long> currentContentId = new ThreadLocal<>();
    private static final ThreadLocal<List<SemanticHeading>> headings = new ThreadLocal<>();
    private static final ThreadLocal<Integer> imageIndex = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> isUseStructTree = new ThreadLocal<>();
    private static final ThreadLocal<ContrastRatioConsumer>  contrastRatioConsumer = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> isContrastRatioConsumerFailedToCreate =  new ThreadLocal<>();
    private static final ThreadLocal<String> imagesDirectory = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> embedImages = new ThreadLocal<>();
    private static final ThreadLocal<String> imageFormat = new ThreadLocal<>();

    public static void clearContainers() {
        currentContentId.set(1L);
        headings.set(new LinkedList<>());
        imageIndex.set(1);
        isUseStructTree.set(false);
        contrastRatioConsumer.remove();
        isContrastRatioConsumerFailedToCreate.set(false);
        imagesDirectory.set("");
        embedImages.set(false);
        imageFormat.set(Config.IMAGE_FORMAT_PNG);
    }

    public static long getCurrentContentId() {
        return currentContentId.get();
    }

    public static long incrementContentId() {
        long id = getCurrentContentId();
        StaticLayoutContainers.setCurrentContentId(id + 1);
        return id;
    }

    public static void setCurrentContentId(long currentContentId) {
        StaticLayoutContainers.currentContentId.set(currentContentId);
    }

    public static String getImagesDirectory() {
        return imagesDirectory.get();
    }

    public static String getImagesDirectoryName() {
        String dir = imagesDirectory.get();
        return dir != null && !dir.isEmpty() ? new File(dir).getName() : "";
    }

    public static void setImagesDirectory(String imagesDirectory) {
        StaticLayoutContainers.imagesDirectory.set(imagesDirectory);
    }

    public static ContrastRatioConsumer getContrastRatioConsumer(String sourcePdfPath, String password, boolean enableAntialias, Float imagePixelSize) {
        try {
            if (contrastRatioConsumer.get() == null && !isContrastRatioConsumerFailedToCreate.get()) {
                contrastRatioConsumer.set(new ContrastRatioConsumer(sourcePdfPath, password, enableAntialias, imagePixelSize));
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error setting contrast ratio consumer: " + e.getMessage());
            isContrastRatioConsumerFailedToCreate.set(true);
        }
        return contrastRatioConsumer.get();
    }

    public static void closeContrastRatioConsumer() {
        try {
            if (contrastRatioConsumer.get() != null) {
                contrastRatioConsumer.get().close();
                contrastRatioConsumer.remove();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing contrast ratio consumer: " + e.getMessage());
        }
    }

    public static List<SemanticHeading> getHeadings() {
        return headings.get();
    }

    public static void setHeadings(List<SemanticHeading> headings) {
        StaticLayoutContainers.headings.set(headings);
    }

    public static Boolean isUseStructTree() {
        return isUseStructTree.get();
    }

    public static void setIsUseStructTree(Boolean isUseStructTree) {
        StaticLayoutContainers.isUseStructTree.set(isUseStructTree);
    }

    public static int incrementImageIndex() {
        int imageIndex = StaticLayoutContainers.imageIndex.get();
        StaticLayoutContainers.imageIndex.set(imageIndex + 1);
        return imageIndex;
    }

    public static void resetImageIndex() {
        StaticLayoutContainers.imageIndex.set(1);
    }

    public static boolean isEmbedImages() {
        return Boolean.TRUE.equals(embedImages.get());
    }

    public static void setEmbedImages(boolean embedImages) {
        StaticLayoutContainers.embedImages.set(embedImages);
    }

    public static String getImageFormat() {
        String format = imageFormat.get();
        return format != null ? format : Config.IMAGE_FORMAT_PNG;
    }

    public static void setImageFormat(String format) {
        StaticLayoutContainers.imageFormat.set(format);
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.containers;

import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import java.awt.*;
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

    public static void clearContainers() {
        currentContentId.set(1L);
        headings.set(new LinkedList<>());
        imageIndex.set(1);
        isUseStructTree.set(false);
        contrastRatioConsumer.remove();
        isContrastRatioConsumerFailedToCreate.set(false);
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
}

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

import java.util.LinkedList;
import java.util.List;

public class StaticLayoutContainers {

    private static final ThreadLocal<Long> currentContentId = new ThreadLocal<>();
    private static final ThreadLocal<List<SemanticHeading>> headings = new ThreadLocal<>();
    private static final ThreadLocal<ContrastRatioConsumer> contrastRatioConsumer = new ThreadLocal<>();
    private static final ThreadLocal<Integer> imageIndex = new ThreadLocal<>();

    public static void clearContainers() {
        currentContentId.set(1L);
        headings.set(new LinkedList<>());
        imageIndex.set(1);
        contrastRatioConsumer.set(null);
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

    public static ContrastRatioConsumer getContrastRatioConsumer() {
        return contrastRatioConsumer.get();
    }

    public static void setContrastRatioConsumer(ContrastRatioConsumer contrastRatioConsumer) {
        StaticLayoutContainers.contrastRatioConsumer.set(contrastRatioConsumer);
    }

    public static List<SemanticHeading> getHeadings() {
        return headings.get();
    }

    public static void setHeadings(List<SemanticHeading> headings) {
        StaticLayoutContainers.headings.set(headings);
    }

    public static int incrementImageIndex() {
        int imageIndex = StaticLayoutContainers.imageIndex.get();
        StaticLayoutContainers.imageIndex.set(imageIndex + 1);
        return imageIndex;
    }
}

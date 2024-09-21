package com.duallab.layout.containers;

import org.verapdf.wcag.algorithms.entities.SemanticHeading;

import java.util.LinkedList;
import java.util.List;

public class StaticLayoutContainers {

    private static final ThreadLocal<Long> currentContentId = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> findHiddenText = new ThreadLocal<>();
    private static final ThreadLocal<List<SemanticHeading>> headings = new ThreadLocal<>();
    private static final ThreadLocal<Integer> imageIndex = new ThreadLocal<>();

    public static void clearContainers() {
        currentContentId.set(1L);
        findHiddenText.set(false);
        headings.set(new LinkedList<>());
        imageIndex.set(1);
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

    public static boolean isFindHiddenText() {
        return findHiddenText.get();
    }

    public static void setFindHiddenText(boolean findHiddenText) {
        StaticLayoutContainers.findHiddenText.set(findHiddenText);
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

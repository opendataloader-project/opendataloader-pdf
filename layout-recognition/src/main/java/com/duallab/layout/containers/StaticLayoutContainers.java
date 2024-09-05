package com.duallab.layout.containers;

import org.verapdf.wcag.algorithms.entities.SemanticHeading;

import java.util.LinkedList;
import java.util.List;

public class StaticLayoutContainers {

    private static long currentContentId = 1;
    private static boolean findHiddenText = false;
    private static List<SemanticHeading> headings = new LinkedList<>();

    public static long getCurrentContentId() {
        return currentContentId;
    }

    public static long incrementContentId() {
        return StaticLayoutContainers.currentContentId++;
    }

    public static void setCurrentContentId(long currentContentId) {
        StaticLayoutContainers.currentContentId = currentContentId;
    }

    public static boolean isFindHiddenText() {
        return findHiddenText;
    }

    public static void setFindHiddenText(boolean findHiddenText) {
        StaticLayoutContainers.findHiddenText = findHiddenText;
    }

    public static List<SemanticHeading> getHeadings() {
        return headings;
    }

    public static void setHeadings(List<SemanticHeading> headings) {
        StaticLayoutContainers.headings = headings;
    }

    public static void clear() {
        StaticLayoutContainers.headings.clear();
    }
}

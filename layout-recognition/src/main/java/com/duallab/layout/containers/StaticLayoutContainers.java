package com.duallab.layout.containers;

import com.duallab.layout.ContentInfo;
import org.verapdf.wcag.algorithms.entities.IObject;

import java.util.HashMap;
import java.util.Map;

public class StaticLayoutContainers {

    private static final Map<IObject, ContentInfo> contentInfoMap = new HashMap<>();
    private static long currentContentId = 1;
    private static boolean findHiddenText = false;

    public static long getCurrentContentId() {
        return currentContentId;
    }

    public static long incrementContentId() {
        return StaticLayoutContainers.currentContentId++;
    }

    public static void setCurrentContentId(long currentContentId) {
        StaticLayoutContainers.currentContentId = currentContentId;
    }

    public static Map<IObject, ContentInfo> getContentInfoMap() {
        return contentInfoMap;
    }

    public static boolean isFindHiddenText() {
        return findHiddenText;
    }

    public static void setFindHiddenText(boolean findHiddenText) {
        StaticLayoutContainers.findHiddenText = findHiddenText;
    }
}

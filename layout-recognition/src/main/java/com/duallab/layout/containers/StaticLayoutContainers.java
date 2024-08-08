package com.duallab.layout.containers;

import com.duallab.layout.ContentInfo;
import org.verapdf.wcag.algorithms.entities.IObject;

import java.util.HashMap;
import java.util.Map;

public class StaticLayoutContainers {

    private static final Map<IObject, ContentInfo> contentInfoMap = new HashMap<>();
    private static long contentId = 1;
    private static boolean findHiddenText = false;

    public static long getContentId() {
        return contentId;
    }

    public static long incrementId() {
        return StaticLayoutContainers.contentId++;
    }

    public static void setContentId(long contentId) {
        StaticLayoutContainers.contentId = contentId;
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

package com.duallab.layout.containers;

import com.duallab.layout.Info;
import org.verapdf.wcag.algorithms.entities.IObject;

import java.util.HashMap;
import java.util.Map;

public class StaticLayoutContainers {

    private static final Map<IObject, Info> map = new HashMap<>();

    private static long id = 1;

    public static boolean findHiddenText = false;

    public static long getId() {
        return id;
    }

    public static long incrementId() {
        return StaticLayoutContainers.id++;
    }

    public static void setId(long id) {
        StaticLayoutContainers.id = id;
    }

    public static Map<IObject, Info> getMap() {
        return map;
    }
}

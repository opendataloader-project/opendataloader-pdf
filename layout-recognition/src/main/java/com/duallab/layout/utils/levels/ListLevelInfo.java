package com.duallab.layout.utils.levels;

import org.verapdf.wcag.algorithms.entities.lists.PDFList;

public class ListLevelInfo extends LevelInfo {
    private final String commonPrefix;
    private final String numberingStyle;

    public ListLevelInfo(PDFList pdfList) {
        super(pdfList.getFirstListItem().getFirstLine().getLeftX(), pdfList.getRightX());
//        this.label = pdfList.getFirstListItem().getFirstLine().getValue().substring(0, 1);
        commonPrefix = pdfList.getCommonPrefix();
        numberingStyle = pdfList.getNumberingStyle();
    }

    @Override
    public boolean isList() {
        return true;
    }

    public String getCommonPrefix() {
        return commonPrefix;
    }

    public String getNumberingStyle() {
        return numberingStyle;
    }
}

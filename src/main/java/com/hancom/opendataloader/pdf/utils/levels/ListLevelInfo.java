/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.opendataloader.pdf.utils.levels;

import org.verapdf.wcag.algorithms.entities.lists.PDFList;

public class ListLevelInfo extends LevelInfo {
    private final String commonPrefix;
    private final String numberingStyle;
    private final double maxFontSize;

    public ListLevelInfo(PDFList pdfList) {
        super(pdfList.getFirstListItem().getFirstLine().getLeftX(), pdfList.getRightX());
//        this.label = pdfList.getFirstListItem().getFirstLine().getValue().substring(0, 1);
        commonPrefix = pdfList.getCommonPrefix();
        numberingStyle = pdfList.getNumberingStyle();
        this.maxFontSize = pdfList.getFirstListItem().getFontSize();
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

    @Override
    public double getMaxXGap() {
        return maxFontSize * X_GAP_MULTIPLIER;
    }
}

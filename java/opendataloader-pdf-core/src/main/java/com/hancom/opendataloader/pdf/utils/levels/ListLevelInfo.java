/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
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

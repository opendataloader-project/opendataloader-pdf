/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.utils.levels;

import com.hancom.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

public class TextBulletParagraphLevelInfo extends LevelInfo {
    private final String label;
    private final String labelRegex;
    private final double maxFontSize;

    public TextBulletParagraphLevelInfo(SemanticTextNode semanticTextNode) {
        super(semanticTextNode.getFirstLine().getLeftX(), semanticTextNode.getRightX());
        this.labelRegex = BulletedParagraphUtils.getLabelRegex(semanticTextNode);
        this.label = BulletedParagraphUtils.getLabel(semanticTextNode);
        this.maxFontSize = semanticTextNode.getMaxFontSize();
    }

    @Override
    public boolean isTextBulletParagraph() {
        return true;
    }

    public String getLabel() {
        return label;
    }

    public String getLabelRegex() {
        return labelRegex;
    }

    @Override
    public double getMaxXGap() {
        return maxFontSize * X_GAP_MULTIPLIER;
    }
}

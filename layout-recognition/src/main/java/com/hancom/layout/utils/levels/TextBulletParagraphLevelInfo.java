/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.layout.utils.levels;

import com.hancom.layout.utils.BulletedParagraphUtils;
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

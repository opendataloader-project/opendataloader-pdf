/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.utils.levels;

import com.duallab.wcag.algorithms.entities.SemanticTextNode;
import com.duallab.wcag.algorithms.entities.content.LineArtChunk;

public class LineArtBulletParagraphLevelInfo extends LevelInfo {
    private final LineArtChunk bullet;
    private final double maxFontSize;
    
    public LineArtBulletParagraphLevelInfo(SemanticTextNode textNode) {
        super(0, 0);
        this.bullet = textNode.getFirstLine().getConnectedLineArtLabel();
        this.maxFontSize = textNode.getMaxFontSize();
    }

    @Override
    public boolean isLineArtBulletParagraph() {
        return true;
    }

    public LineArtChunk getBullet() {
        return bullet;
    }

    @Override
    public double getMaxXGap() {
        return maxFontSize * X_GAP_MULTIPLIER;
    }
}

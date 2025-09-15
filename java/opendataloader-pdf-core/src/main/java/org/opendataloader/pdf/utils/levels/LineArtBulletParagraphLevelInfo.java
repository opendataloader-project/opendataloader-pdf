/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.utils.levels;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;

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

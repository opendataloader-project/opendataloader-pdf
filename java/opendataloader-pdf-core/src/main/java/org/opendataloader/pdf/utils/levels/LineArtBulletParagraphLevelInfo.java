/*
 * Copyright 2025 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

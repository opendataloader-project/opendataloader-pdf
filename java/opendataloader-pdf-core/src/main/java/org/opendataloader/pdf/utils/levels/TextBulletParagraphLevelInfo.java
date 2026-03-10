/*
 * Copyright 2025-2026 Hancom Inc.
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

import org.opendataloader.pdf.utils.BulletedParagraphUtils;
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

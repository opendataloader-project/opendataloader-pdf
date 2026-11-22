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
package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

public class TextNodeStatistics {
    private final ModeWeightStatistics fontSizeStatistics;
    private final ModeWeightStatistics fontWeightStatistics;
    private final TextNodeStatisticsConfig config;

    public TextNodeStatistics() {
        this(new TextNodeStatisticsConfig());
    }

    public TextNodeStatistics(TextNodeStatisticsConfig config) {
        this.config = config;

        double sizeScoreMin = config.fontSizeHeadingMin;
        double sizeScoreMax = config.fontSizeHeadingMax;
        double sizeModeMin = config.fontSizeDominantMin;
        double sizeModeMax = config.fontSizeDominantMax;
        fontSizeStatistics = new ModeWeightStatistics(sizeScoreMin, sizeScoreMax, sizeModeMin, sizeModeMax);

        double weightScoreMin = config.fontWeightHeadingMin;
        double weightScoreMax = config.fontWeightHeadingMax;
        double weightModeMin = config.fontWeightDominantMin;
        double weightModeMax = config.fontWeightDominantMax;
        fontWeightStatistics = new ModeWeightStatistics(weightScoreMin, weightScoreMax, weightModeMin, weightModeMax);
    }

    public void addTextNode(SemanticTextNode textNode) {
        if (textNode == null) {
            return;
        }
        fontSizeStatistics.addScore(textNode.getFontSize());
        fontWeightStatistics.addScore(textNode.getFontWeight());
    }

    public double fontSizeRarityBoost(SemanticTextNode textNode) {
        double boost = fontSizeStatistics.getBoost(textNode.getFontSize());
        return boost * config.fontSizeRarityBoost;
    }

    public double fontWeightRarityBoost(SemanticTextNode textNode) {
        double boost = fontWeightStatistics.getBoost(textNode.getFontWeight());
        return boost * config.fontWeightRarityBoost;
    }
}

package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

public class TextNodeStatistics {
    private final ModeWeightStatistics fontSizeStatistics = new ModeWeightStatistics();
    private final ModeWeightStatistics fontWeightStatistics = new ModeWeightStatistics();
    private TextNodeStatisticsConfig config = new TextNodeStatisticsConfig();

    public TextNodeStatistics() {
    }

    public TextNodeStatistics(TextNodeStatisticsConfig config) {
        this.config = config;
    }

    public void addTextNode(SemanticTextNode textNode) {
        if (textNode == null) {
            return;
        }
        fontSizeStatistics.addScore(textNode.getFontSize());
        fontWeightStatistics.addScore(textNode.getFontWeight());
    }

    public double fontSizeRarityBoost(SemanticTextNode textNode) {
        double scoreMax = config.fontSizeHeadingMax;
        double modeMin = config.fontSizeDominantMin;
        double modeMax = config.fontSizeDominantMax;
        double boost = fontSizeStatistics.getBoost(textNode.getFontSize(), scoreMax, modeMin, modeMax);
        return boost * config.fontSizeRarityBoost;
    }

    public double fontWeightRarityBoost(SemanticTextNode textNode) {
        double scoreMax = config.fontWeightHeadingMax;
        double modeMin = config.fontWeightDominantMin;
        double modeMax = config.fontWeightDominantMax;
        double boost = fontWeightStatistics.getBoost(textNode.getFontWeight(), scoreMax, modeMin, modeMax);
        return boost * config.fontWeightRarityBoost;
    }
}

package org.opendataloader.pdf.utils;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import static org.assertj.core.api.Assertions.assertThat;

class TextNodeStatisticsTest {

    @Test
    void fontSizeRarityBoostUsesRelativeRankOfScoresAboveBodyMode() {
        TextNodeStatisticsConfig config = new TextNodeStatisticsConfig();
        TextNodeStatistics statistics = new TextNodeStatistics(config);

        StubSemanticTextNode body = new StubSemanticTextNode(12.0, 400.0);
        StubSemanticTextNode bodySecond = new StubSemanticTextNode(12.0, 400.0);
        StubSemanticTextNode smallBody = new StubSemanticTextNode(10.0, 395.0);
        StubSemanticTextNode mediumHeading = new StubSemanticTextNode(14.0, 410.0);
        StubSemanticTextNode largeHeading = new StubSemanticTextNode(16.0, 430.0);

        statistics.addTextNode(body);
        statistics.addTextNode(bodySecond);
        statistics.addTextNode(smallBody);
        statistics.addTextNode(mediumHeading);
        statistics.addTextNode(largeHeading);

        double boostForBody = statistics.fontSizeRarityBoost(body);
        double boostForMediumHeading = statistics.fontSizeRarityBoost(mediumHeading);
        double boostForLargeHeading = statistics.fontSizeRarityBoost(largeHeading);

        assertThat(boostForBody).isCloseTo(0.0, Offset.offset(0.001));
        assertThat(boostForMediumHeading).isCloseTo(config.fontSizeRarityBoost / 2, Offset.offset(0.001));
        assertThat(boostForLargeHeading).isCloseTo(config.fontSizeRarityBoost, Offset.offset(0.001));
    }

    @Test
    void fontWeightRarityBoostUsesDominantWeightWindow() {
        TextNodeStatisticsConfig config = new TextNodeStatisticsConfig();
        TextNodeStatistics statistics = new TextNodeStatistics(config);

        StubSemanticTextNode body = new StubSemanticTextNode(12.0, 400.0);
        StubSemanticTextNode bodySecond = new StubSemanticTextNode(12.0, 400.0);
        StubSemanticTextNode bodyWithinTolerance = new StubSemanticTextNode(11.0, 395.0);
        StubSemanticTextNode slightlyBolder = new StubSemanticTextNode(14.0, 410.0);
        StubSemanticTextNode boldHeading = new StubSemanticTextNode(16.0, 430.0);

        statistics.addTextNode(body);
        statistics.addTextNode(bodySecond);
        statistics.addTextNode(bodyWithinTolerance);
        statistics.addTextNode(slightlyBolder);
        statistics.addTextNode(boldHeading);

        double boostForBody = statistics.fontWeightRarityBoost(body);
        double boostForSlightlyBolder = statistics.fontWeightRarityBoost(slightlyBolder);
        double boostForBoldHeading = statistics.fontWeightRarityBoost(boldHeading);

        assertThat(boostForBody).isCloseTo(0.0, Offset.offset(0.001));
        assertThat(boostForSlightlyBolder).isCloseTo(config.fontWeightRarityBoost / 2, Offset.offset(0.001));
        assertThat(boostForBoldHeading).isCloseTo(config.fontWeightRarityBoost, Offset.offset(0.001));
    }

    private static class StubSemanticTextNode extends SemanticTextNode {
        private final double fontSize;
        private final double fontWeight;

        StubSemanticTextNode(double fontSize, double fontWeight) {
            this.fontSize = fontSize;
            this.fontWeight = fontWeight;
        }

        @Override
        public double getFontSize() {
            return fontSize;
        }

        @Override
        public double getFontWeight() {
            return fontWeight;
        }
    }
}

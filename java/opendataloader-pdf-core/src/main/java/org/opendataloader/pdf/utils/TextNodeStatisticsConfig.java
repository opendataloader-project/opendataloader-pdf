package org.opendataloader.pdf.utils;

/**
 * Configuration holder that exposes the scoring constants used by {@link TextNodeStatistics}.
 * The defaults mimic the legacy hard-coded probabilities but callers may override them
 * to tune heading detection without touching the algorithm code.
 */
public class TextNodeStatisticsConfig {
    public double fontSizeDominantMin = 10.0;
    public double fontSizeDominantMax = 13.0;
    public double fontSizeHeadingMax = 32.0;
    public double fontSizeRarityBoost = 0.5;

    public double fontWeightDominantMin = 395.0;
    public double fontWeightDominantMax = 405.0;
    public double fontWeightHeadingMax = 900.0;
    public double fontWeightRarityBoost = 0.3;
}

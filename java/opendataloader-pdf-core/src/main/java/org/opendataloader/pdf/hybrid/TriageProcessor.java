/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;

/**
 * Processor for triaging PDF pages to determine the optimal processing path.
 *
 * <p>In hybrid mode, pages are classified as either:
 * <ul>
 *   <li>JAVA - Simple pages processed by the fast Java path</li>
 *   <li>BACKEND - Complex pages (typically with tables) routed to AI backend</li>
 * </ul>
 *
 * <p>The triage uses a <b>conservative strategy</b> that minimizes false negatives
 * (missed tables). It's acceptable to send simple pages to the backend (false positives)
 * since the backend can process them correctly, just slower.
 */
public class TriageProcessor {

    /** Default threshold for LineChunk to total content ratio. */
    public static final double DEFAULT_LINE_RATIO_THRESHOLD = 0.3;

    /** Default minimum aligned line groups to trigger BACKEND routing. */
    // Note: Increased from 3 to 5 (Experiment 002, 2026-01-03)
    // Threshold 3 caused 10 FPs from normal documents with aligned baselines
    public static final int DEFAULT_ALIGNED_LINE_GROUPS_THRESHOLD = 5;

    /** Default gap multiplier for grid pattern detection (relative to text height). */
    public static final double DEFAULT_GRID_GAP_MULTIPLIER = 3.0;

    /** Epsilon for comparing baseline coordinates. */
    private static final double BASELINE_EPSILON = 0.1;

    // ============= Vector Graphics Detection Constants =============

    /** Minimum number of line segments to suggest table borders. */
    private static final int MIN_LINE_COUNT_FOR_TABLE = 8;

    /** Minimum number of horizontal + vertical line pairs for grid pattern. */
    private static final int MIN_GRID_LINES = 3;

    /** Minimum number of line-text-line alternations for row separator pattern. */
    private static final int MIN_ROW_SEPARATOR_PATTERN = 5;

    /** Minimum LineArt chunks to indicate table structure. */
    private static final int MIN_LINE_ART_FOR_TABLE = 8;

    // ============= Aligned Short Lines Detection Constants =============

    /** Tolerance for matching line lengths (5%). */
    private static final double LINE_LENGTH_TOLERANCE = 0.05;

    /** Minimum aligned short lines with same X and length. */
    private static final int MIN_ALIGNED_SHORT_LINES = 2;

    // ============= Consecutive Pattern Detection Constants =============

    /** Minimum consecutive suspicious patterns required. */
    private static final int MIN_CONSECUTIVE_PATTERNS = 2;

    /** High pattern count threshold (skip consecutive check). */
    private static final int HIGH_PATTERN_COUNT_THRESHOLD = 30;

    /** Minimum absolute patterns required. */
    private static final int MIN_TABLE_PATTERNS = 3;

    /** Minimum pattern density (patterns / text chunks). */
    private static final double MIN_PATTERN_DENSITY = 0.10;

    /** Minimum patterns for density check. */
    private static final int MIN_PATTERNS_FOR_DENSITY = 2;

    /** X shift ratio to detect column change (filters multi-column layouts). */
    private static final double MULTI_COLUMN_X_SHIFT_RATIO = 2.0;

    /** X difference epsilon for gap detection. */
    private static final double X_DIFFERENCE_EPSILON = 1.5;

    /**
     * Triage decision indicating which processing path to use.
     */
    public enum TriageDecision {
        /** Process using fast Java path. */
        JAVA,
        /** Route to AI backend for complex content processing. */
        BACKEND
    }

    /**
     * Result of triaging a single page.
     */
    public static final class TriageResult {
        private final int pageNumber;
        private final TriageDecision decision;
        private final double confidence;
        private final TriageSignals signals;

        /**
         * Creates a new triage result.
         *
         * @param pageNumber The 0-indexed page number.
         * @param decision   The triage decision (JAVA or BACKEND).
         * @param confidence Confidence score (0.0 to 1.0). Higher means more certain.
         * @param signals    The extracted signals used for the decision.
         */
        public TriageResult(int pageNumber, TriageDecision decision, double confidence, TriageSignals signals) {
            this.pageNumber = pageNumber;
            this.decision = decision;
            this.confidence = confidence;
            this.signals = signals;
        }

        /**
         * Creates a result indicating JAVA processing path.
         *
         * @param pageNumber The page number.
         * @param confidence The confidence level.
         * @param signals    The extracted signals.
         * @return A new TriageResult with JAVA decision.
         */
        public static TriageResult java(int pageNumber, double confidence, TriageSignals signals) {
            return new TriageResult(pageNumber, TriageDecision.JAVA, confidence, signals);
        }

        /**
         * Creates a result indicating BACKEND processing path.
         *
         * @param pageNumber The page number.
         * @param confidence The confidence level.
         * @param signals    The extracted signals.
         * @return A new TriageResult with BACKEND decision.
         */
        public static TriageResult backend(int pageNumber, double confidence, TriageSignals signals) {
            return new TriageResult(pageNumber, TriageDecision.BACKEND, confidence, signals);
        }

        /**
         * Gets the page number.
         *
         * @return The 0-indexed page number.
         */
        public int getPageNumber() {
            return pageNumber;
        }

        /**
         * Gets the triage decision.
         *
         * @return The decision (JAVA or BACKEND).
         */
        public TriageDecision getDecision() {
            return decision;
        }

        /**
         * Gets the confidence score.
         *
         * @return The confidence score (0.0 to 1.0).
         */
        public double getConfidence() {
            return confidence;
        }

        /**
         * Gets the extracted signals.
         *
         * @return The triage signals.
         */
        public TriageSignals getSignals() {
            return signals;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TriageResult that = (TriageResult) obj;
            return pageNumber == that.pageNumber &&
                   Double.compare(that.confidence, confidence) == 0 &&
                   decision == that.decision &&
                   Objects.equals(signals, that.signals);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pageNumber, decision, confidence, signals);
        }

        @Override
        public String toString() {
            return "TriageResult{" +
                   "pageNumber=" + pageNumber +
                   ", decision=" + decision +
                   ", confidence=" + confidence +
                   ", signals=" + signals +
                   '}';
        }
    }

    /**
     * Signals extracted from page content used for triage decisions.
     */
    public static final class TriageSignals {
        private final int lineChunkCount;
        private final int textChunkCount;
        private final double lineToTextRatio;
        private final int alignedLineGroups;
        private final boolean hasTableBorder;
        private final boolean hasSuspiciousPattern;

        // New vector graphics signals
        private final int horizontalLineCount;
        private final int verticalLineCount;
        private final int lineArtCount;
        private final boolean hasGridLines;
        private final boolean hasTableBorderLines;
        private final boolean hasRowSeparatorPattern;
        private final boolean hasAlignedShortLines;

        // New text pattern signals
        private final int tablePatternCount;
        private final int maxConsecutiveStreak;
        private final double patternDensity;
        private final boolean hasConsecutivePatterns;

        /**
         * Creates new triage signals with basic fields (backward compatibility).
         *
         * @param lineChunkCount       Number of LineChunk objects on the page.
         * @param textChunkCount       Number of TextChunk objects on the page.
         * @param lineToTextRatio      Ratio of LineChunk to total content count.
         * @param alignedLineGroups    Number of groups of TextChunks with aligned baselines.
         * @param hasTableBorder       Whether any TableBorder was detected on this page.
         * @param hasSuspiciousPattern Whether suspicious text patterns were detected.
         */
        public TriageSignals(int lineChunkCount, int textChunkCount, double lineToTextRatio,
                             int alignedLineGroups, boolean hasTableBorder, boolean hasSuspiciousPattern) {
            this(lineChunkCount, textChunkCount, lineToTextRatio, alignedLineGroups,
                    hasTableBorder, hasSuspiciousPattern,
                    0, 0, 0, false, false, false, false,
                    0, 0, 0.0, false);
        }

        /**
         * Creates new triage signals with all fields.
         */
        public TriageSignals(int lineChunkCount, int textChunkCount, double lineToTextRatio,
                             int alignedLineGroups, boolean hasTableBorder, boolean hasSuspiciousPattern,
                             int horizontalLineCount, int verticalLineCount, int lineArtCount,
                             boolean hasGridLines, boolean hasTableBorderLines,
                             boolean hasRowSeparatorPattern, boolean hasAlignedShortLines,
                             int tablePatternCount, int maxConsecutiveStreak, double patternDensity,
                             boolean hasConsecutivePatterns) {
            this.lineChunkCount = lineChunkCount;
            this.textChunkCount = textChunkCount;
            this.lineToTextRatio = lineToTextRatio;
            this.alignedLineGroups = alignedLineGroups;
            this.hasTableBorder = hasTableBorder;
            this.hasSuspiciousPattern = hasSuspiciousPattern;
            this.horizontalLineCount = horizontalLineCount;
            this.verticalLineCount = verticalLineCount;
            this.lineArtCount = lineArtCount;
            this.hasGridLines = hasGridLines;
            this.hasTableBorderLines = hasTableBorderLines;
            this.hasRowSeparatorPattern = hasRowSeparatorPattern;
            this.hasAlignedShortLines = hasAlignedShortLines;
            this.tablePatternCount = tablePatternCount;
            this.maxConsecutiveStreak = maxConsecutiveStreak;
            this.patternDensity = patternDensity;
            this.hasConsecutivePatterns = hasConsecutivePatterns;
        }

        /**
         * Creates empty signals with default values.
         *
         * @return A new TriageSignals with zero/false values.
         */
        public static TriageSignals empty() {
            return new TriageSignals(0, 0, 0.0, 0, false, false,
                    0, 0, 0, false, false, false, false,
                    0, 0, 0.0, false);
        }

        /**
         * Gets the number of LineChunk objects.
         *
         * @return The line chunk count.
         */
        public int getLineChunkCount() {
            return lineChunkCount;
        }

        /**
         * Gets the number of TextChunk objects.
         *
         * @return The text chunk count.
         */
        public int getTextChunkCount() {
            return textChunkCount;
        }

        /**
         * Gets the ratio of LineChunk to total content.
         *
         * @return The line to text ratio.
         */
        public double getLineToTextRatio() {
            return lineToTextRatio;
        }

        /**
         * Gets the number of aligned line groups.
         *
         * @return The aligned line groups count.
         */
        public int getAlignedLineGroups() {
            return alignedLineGroups;
        }

        /**
         * Checks if TableBorder was detected.
         *
         * @return true if TableBorder is present.
         */
        public boolean hasTableBorder() {
            return hasTableBorder;
        }

        /**
         * Checks if suspicious patterns were detected.
         *
         * @return true if suspicious patterns are present.
         */
        public boolean hasSuspiciousPattern() {
            return hasSuspiciousPattern;
        }

        /**
         * Checks if vector graphics indicate table structure.
         *
         * @return true if any vector graphics signal indicates table.
         */
        public boolean hasVectorTableSignal() {
            return hasGridLines || hasTableBorderLines || lineArtCount >= MIN_LINE_ART_FOR_TABLE
                    || hasRowSeparatorPattern || hasAlignedShortLines;
        }

        /**
         * Checks if text patterns indicate table structure (with consecutive validation).
         *
         * @return true if text patterns suggest table.
         */
        public boolean hasTextTablePattern() {
            boolean hasHighPatternCount = tablePatternCount >= HIGH_PATTERN_COUNT_THRESHOLD;
            boolean meetsPatternThreshold = tablePatternCount >= MIN_TABLE_PATTERNS
                    || (patternDensity >= MIN_PATTERN_DENSITY && tablePatternCount >= MIN_PATTERNS_FOR_DENSITY);
            return (hasConsecutivePatterns || hasHighPatternCount) && meetsPatternThreshold;
        }

        public int getHorizontalLineCount() {
            return horizontalLineCount;
        }

        public int getVerticalLineCount() {
            return verticalLineCount;
        }

        public int getLineArtCount() {
            return lineArtCount;
        }

        public boolean hasGridLines() {
            return hasGridLines;
        }

        public boolean hasTableBorderLines() {
            return hasTableBorderLines;
        }

        public boolean hasRowSeparatorPattern() {
            return hasRowSeparatorPattern;
        }

        public boolean hasAlignedShortLines() {
            return hasAlignedShortLines;
        }

        public int getTablePatternCount() {
            return tablePatternCount;
        }

        public int getMaxConsecutiveStreak() {
            return maxConsecutiveStreak;
        }

        public double getPatternDensity() {
            return patternDensity;
        }

        public boolean hasConsecutivePatterns() {
            return hasConsecutivePatterns;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            TriageSignals that = (TriageSignals) obj;
            return lineChunkCount == that.lineChunkCount &&
                   textChunkCount == that.textChunkCount &&
                   Double.compare(that.lineToTextRatio, lineToTextRatio) == 0 &&
                   alignedLineGroups == that.alignedLineGroups &&
                   hasTableBorder == that.hasTableBorder &&
                   hasSuspiciousPattern == that.hasSuspiciousPattern &&
                   horizontalLineCount == that.horizontalLineCount &&
                   verticalLineCount == that.verticalLineCount &&
                   lineArtCount == that.lineArtCount &&
                   hasGridLines == that.hasGridLines &&
                   hasTableBorderLines == that.hasTableBorderLines &&
                   hasRowSeparatorPattern == that.hasRowSeparatorPattern &&
                   hasAlignedShortLines == that.hasAlignedShortLines &&
                   tablePatternCount == that.tablePatternCount &&
                   maxConsecutiveStreak == that.maxConsecutiveStreak &&
                   Double.compare(that.patternDensity, patternDensity) == 0 &&
                   hasConsecutivePatterns == that.hasConsecutivePatterns;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lineChunkCount, textChunkCount, lineToTextRatio,
                                alignedLineGroups, hasTableBorder, hasSuspiciousPattern,
                                horizontalLineCount, verticalLineCount, lineArtCount,
                                hasGridLines, hasTableBorderLines, hasRowSeparatorPattern,
                                hasAlignedShortLines, tablePatternCount, maxConsecutiveStreak,
                                patternDensity, hasConsecutivePatterns);
        }

        @Override
        public String toString() {
            return "TriageSignals{" +
                   "lineChunkCount=" + lineChunkCount +
                   ", textChunkCount=" + textChunkCount +
                   ", lineToTextRatio=" + lineToTextRatio +
                   ", alignedLineGroups=" + alignedLineGroups +
                   ", hasTableBorder=" + hasTableBorder +
                   ", hasSuspiciousPattern=" + hasSuspiciousPattern +
                   ", horizontalLineCount=" + horizontalLineCount +
                   ", verticalLineCount=" + verticalLineCount +
                   ", lineArtCount=" + lineArtCount +
                   ", hasGridLines=" + hasGridLines +
                   ", hasTableBorderLines=" + hasTableBorderLines +
                   ", hasRowSeparatorPattern=" + hasRowSeparatorPattern +
                   ", hasAlignedShortLines=" + hasAlignedShortLines +
                   ", tablePatternCount=" + tablePatternCount +
                   ", maxConsecutiveStreak=" + maxConsecutiveStreak +
                   ", patternDensity=" + patternDensity +
                   ", hasConsecutivePatterns=" + hasConsecutivePatterns +
                   '}';
        }
    }

    /**
     * Configuration for triage thresholds.
     * Allows tuning the sensitivity of the triage decision.
     */
    public static class TriageThresholds {
        private double lineRatioThreshold = DEFAULT_LINE_RATIO_THRESHOLD;
        private int alignedLineGroupsThreshold = DEFAULT_ALIGNED_LINE_GROUPS_THRESHOLD;
        private double gridGapMultiplier = DEFAULT_GRID_GAP_MULTIPLIER;

        /**
         * Creates thresholds with default values.
         */
        public TriageThresholds() {
        }

        /**
         * Gets the line ratio threshold.
         *
         * @return The threshold for LineChunk to content ratio.
         */
        public double getLineRatioThreshold() {
            return lineRatioThreshold;
        }

        /**
         * Sets the line ratio threshold.
         *
         * @param lineRatioThreshold The threshold value (0.0 to 1.0).
         */
        public void setLineRatioThreshold(double lineRatioThreshold) {
            this.lineRatioThreshold = lineRatioThreshold;
        }

        /**
         * Gets the aligned line groups threshold.
         *
         * @return The minimum number of aligned groups to trigger BACKEND.
         */
        public int getAlignedLineGroupsThreshold() {
            return alignedLineGroupsThreshold;
        }

        /**
         * Sets the aligned line groups threshold.
         *
         * @param alignedLineGroupsThreshold The minimum number of aligned groups.
         */
        public void setAlignedLineGroupsThreshold(int alignedLineGroupsThreshold) {
            this.alignedLineGroupsThreshold = alignedLineGroupsThreshold;
        }

        /**
         * Gets the grid gap multiplier.
         *
         * @return The multiplier for text height to detect grid gaps.
         */
        public double getGridGapMultiplier() {
            return gridGapMultiplier;
        }

        /**
         * Sets the grid gap multiplier.
         *
         * @param gridGapMultiplier The multiplier value.
         */
        public void setGridGapMultiplier(double gridGapMultiplier) {
            this.gridGapMultiplier = gridGapMultiplier;
        }
    }

    private TriageProcessor() {
        // Static utility class
    }

    /**
     * Classifies a page for processing path based on its content.
     *
     * <p>Uses a conservative strategy that biases toward BACKEND when uncertain.
     * Signals are evaluated in priority order:
     * <ol>
     *   <li>TableBorder presence (most reliable)</li>
     *   <li>Suspicious text patterns</li>
     *   <li>High LineChunk ratio</li>
     *   <li>Grid pattern detection (aligned baselines with gaps)</li>
     * </ol>
     *
     * @param filteredContents The filtered page contents from ContentFilterProcessor.
     * @param pageNumber       The 0-indexed page number.
     * @param config           The hybrid configuration (may be null for defaults).
     * @return The triage result with decision, confidence, and signals.
     */
    public static TriageResult classifyPage(
            List<IObject> filteredContents,
            int pageNumber,
            HybridConfig config) {
        return classifyPage(filteredContents, pageNumber, new TriageThresholds());
    }

    /**
     * Classifies a page for processing path with custom thresholds.
     *
     * @param filteredContents The filtered page contents from ContentFilterProcessor.
     * @param pageNumber       The 0-indexed page number.
     * @param thresholds       The triage thresholds to use.
     * @return The triage result with decision, confidence, and signals.
     */
    public static TriageResult classifyPage(
            List<IObject> filteredContents,
            int pageNumber,
            TriageThresholds thresholds) {

        // Extract signals from content
        TriageSignals signals = extractSignals(filteredContents, pageNumber, thresholds);

        // Signal 1: TableBorder presence (highest priority, most reliable)
        if (signals.hasTableBorder()) {
            return TriageResult.backend(pageNumber, 1.0, signals);
        }

        // Signal 2: Vector graphics based table detection (grid lines, borders, line art)
        if (signals.hasVectorTableSignal()) {
            return TriageResult.backend(pageNumber, 0.95, signals);
        }

        // Signal 3: Text-based table patterns (with consecutive validation)
        if (signals.hasTextTablePattern()) {
            return TriageResult.backend(pageNumber, 0.9, signals);
        }

        // Signal 4: Suspicious text patterns (catches borderless tables)
        // Note: Disabled (Experiment 003, 2026-01-03)
        // This signal caused 19 FPs (28.4%) by detecting large gaps in non-table layouts
        // Disabling reduces FP by 12 with only +1 FN (Recall: 97.62% → 95.24%)
        // if (signals.hasSuspiciousPattern()) {
        //     return TriageResult.backend(pageNumber, 0.85, signals);
        // }

        // Signal 5: High LineChunk ratio (grid/border elements)
        if (signals.getLineToTextRatio() > thresholds.getLineRatioThreshold()) {
            return TriageResult.backend(pageNumber, 0.8, signals);
        }

        // Signal 6: Grid pattern detection (aligned baselines with gaps)
        if (signals.getAlignedLineGroups() >= thresholds.getAlignedLineGroupsThreshold()) {
            return TriageResult.backend(pageNumber, 0.7, signals);
        }

        // Default: Route to JAVA for simple text-only content
        return TriageResult.java(pageNumber, 0.9, signals);
    }

    /**
     * Extracts triage signals from page contents.
     *
     * @param filteredContents The filtered page contents.
     * @param pageNumber       The 0-indexed page number.
     * @param thresholds       The triage thresholds.
     * @return The extracted signals.
     */
    static TriageSignals extractSignals(
            List<IObject> filteredContents,
            int pageNumber,
            TriageThresholds thresholds) {

        if (filteredContents == null || filteredContents.isEmpty()) {
            return TriageSignals.empty();
        }

        // Use SignalAccumulator to collect all signals in a single pass
        SignalAccumulator accumulator = new SignalAccumulator();

        for (IObject content : filteredContents) {
            if (content instanceof LineChunk) {
                accumulator.processLineChunk((LineChunk) content);
            } else if (content instanceof TextChunk) {
                accumulator.processTextChunk((TextChunk) content);
            } else if (content instanceof LineArtChunk) {
                accumulator.processLineArtChunk();
            }
        }

        // Calculate derived values
        int totalCount = filteredContents.size();
        double lineToTextRatio = totalCount > 0
                ? (double) accumulator.lineChunkCount / totalCount : 0.0;

        // Check for TableBorder in StaticContainers
        boolean hasTableBorder = checkTableBorderPresence(pageNumber);

        // Check for suspicious text patterns (grid-like layout)
        boolean hasSuspiciousPattern = checkSuspiciousPatterns(accumulator.textChunks);

        // Count aligned line groups (potential table columns)
        int alignedLineGroups = countAlignedLineGroups(
                accumulator.textChunks, thresholds.getGridGapMultiplier());

        // Build vector graphics signals
        boolean hasGridLines = accumulator.horizontalLineCount >= MIN_GRID_LINES
                && accumulator.verticalLineCount >= MIN_GRID_LINES;
        boolean hasTableBorderLines = (accumulator.horizontalLineCount + accumulator.verticalLineCount)
                >= MIN_LINE_COUNT_FOR_TABLE;
        boolean hasRowSeparatorPattern = accumulator.rowSeparatorPatternCount >= MIN_ROW_SEPARATOR_PATTERN;
        boolean hasAlignedShortLines = accumulator.hasAlignedShortHorizontalLines();

        // Build text pattern signals
        double patternDensity = accumulator.nonWhitespaceTextCount > 0
                ? (double) accumulator.tablePatternCount / accumulator.nonWhitespaceTextCount : 0.0;
        boolean hasConsecutivePatterns = accumulator.maxConsecutiveStreak >= MIN_CONSECUTIVE_PATTERNS;

        return new TriageSignals(
            accumulator.lineChunkCount,
            accumulator.textChunkCount,
            lineToTextRatio,
            alignedLineGroups,
            hasTableBorder,
            hasSuspiciousPattern,
            accumulator.horizontalLineCount,
            accumulator.verticalLineCount,
            accumulator.lineArtCount,
            hasGridLines,
            hasTableBorderLines,
            hasRowSeparatorPattern,
            hasAlignedShortLines,
            accumulator.tablePatternCount,
            accumulator.maxConsecutiveStreak,
            patternDensity,
            hasConsecutivePatterns
        );
    }

    /**
     * Helper class to accumulate signals during page analysis.
     */
    private static class SignalAccumulator {
        int lineChunkCount = 0;
        int textChunkCount = 0;
        int nonWhitespaceTextCount = 0;
        int horizontalLineCount = 0;
        int verticalLineCount = 0;
        int lineArtCount = 0;
        int tablePatternCount = 0;
        int currentConsecutiveStreak = 0;
        int maxConsecutiveStreak = 0;
        int rowSeparatorPatternCount = 0;
        boolean lastWasHorizontalLine = false;
        TextChunk previousTextChunk = null;
        List<TextChunk> textChunks = new ArrayList<>();
        List<double[]> shortHorizontalLines = new ArrayList<>();

        void processLineChunk(LineChunk lineChunk) {
            lineChunkCount++;
            BoundingBox box = lineChunk.getBoundingBox();
            double width = box.getRightX() - box.getLeftX();
            double height = box.getTopY() - box.getBottomY();

            // Horizontal line: width >> height
            if (width > height * 3) {
                horizontalLineCount++;
                if (!lastWasHorizontalLine) {
                    rowSeparatorPatternCount++;
                }
                // Track short horizontal lines for aligned pattern detection
                shortHorizontalLines.add(new double[]{box.getLeftX(), width});
                lastWasHorizontalLine = true;
            }
            // Vertical line: height >> width
            else if (height > width * 3) {
                verticalLineCount++;
            }
        }

        void processLineArtChunk() {
            lineArtCount++;
        }

        void processTextChunk(TextChunk textChunk) {
            textChunkCount++;
            textChunks.add(textChunk);

            if (textChunk.isWhiteSpaceChunk()) {
                return;
            }

            nonWhitespaceTextCount++;
            lastWasHorizontalLine = false;

            if (previousTextChunk != null) {
                if (areSuspiciousTextChunks(previousTextChunk, textChunk)) {
                    tablePatternCount++;
                    currentConsecutiveStreak++;
                    if (currentConsecutiveStreak > maxConsecutiveStreak) {
                        maxConsecutiveStreak = currentConsecutiveStreak;
                    }
                } else {
                    currentConsecutiveStreak = 0;
                }
            }
            previousTextChunk = textChunk;
        }

        /**
         * Detects suspicious text chunks that may indicate table structure.
         */
        private boolean areSuspiciousTextChunks(TextChunk previous, TextChunk current) {
            // Text going backwards suggests multi-column layout or table
            if (previous.getTopY() < current.getBottomY()) {
                // Filter out multi-column layout: X moves significantly left
                double xShift = previous.getLeftX() - current.getLeftX();
                double textWidth = previous.getRightX() - previous.getLeftX();
                if (textWidth > 0 && xShift > textWidth * MULTI_COLUMN_X_SHIFT_RATIO) {
                    return false;
                }
                return true;
            }
            // Same baseline with large horizontal gap suggests table cell boundaries
            double baselineDiff = Math.abs(previous.getBaseLine() - current.getBaseLine());
            double avgHeight = (previous.getHeight() + current.getHeight()) / 2.0;
            if (baselineDiff < avgHeight * BASELINE_EPSILON) {
                return current.getLeftX() - previous.getRightX() > current.getHeight() * X_DIFFERENCE_EPSILON;
            }
            return false;
        }

        /**
         * Checks for aligned short horizontal lines with same length and X position.
         */
        boolean hasAlignedShortHorizontalLines() {
            if (shortHorizontalLines.size() < MIN_ALIGNED_SHORT_LINES) {
                return false;
            }
            for (int i = 0; i < shortHorizontalLines.size(); i++) {
                double[] refLine = shortHorizontalLines.get(i);
                double refLeftX = refLine[0];
                double refLen = refLine[1];
                int matchCount = 1;
                for (int j = i + 1; j < shortHorizontalLines.size(); j++) {
                    double[] line = shortHorizontalLines.get(j);
                    double leftX = line[0];
                    double len = line[1];
                    double xDiff = Math.abs(refLeftX - leftX);
                    double lenDiff = Math.abs(refLen - len);
                    double maxLen = Math.max(refLen, len);
                    boolean xMatches = maxLen > 0 && xDiff / maxLen <= LINE_LENGTH_TOLERANCE;
                    boolean lenMatches = maxLen > 0 && lenDiff / maxLen <= LINE_LENGTH_TOLERANCE;
                    if (xMatches && lenMatches) {
                        matchCount++;
                        if (matchCount >= MIN_ALIGNED_SHORT_LINES) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    /**
     * Checks if any TableBorder exists for the given page.
     *
     * @param pageNumber The 0-indexed page number.
     * @return true if TableBorder is detected, false otherwise.
     */
    private static boolean checkTableBorderPresence(int pageNumber) {
        try {
            SortedSet<TableBorder> tableBorders =
                StaticContainers.getTableBordersCollection().getTableBorders(pageNumber);
            return tableBorders != null && !tableBorders.isEmpty();
        } catch (Exception e) {
            // StaticContainers may not be initialized in some contexts
            return false;
        }
    }

    /**
     * Checks for suspicious text patterns indicating possible tables.
     * Looks for text chunks on the same baseline with large horizontal gaps.
     *
     * @param textChunks The list of text chunks on the page.
     * @return true if suspicious patterns are detected, false otherwise.
     */
    private static boolean checkSuspiciousPatterns(List<TextChunk> textChunks) {
        if (textChunks.size() < 2) {
            return false;
        }

        TextChunk previous = null;
        for (TextChunk current : textChunks) {
            if (current.isWhiteSpaceChunk()) {
                continue;
            }
            // Check if text chunks are on the same line with large gap
            // Note: Y-overlap check removed (Experiment 001, 2026-01-03)
            // The condition `previous.getTopY() < current.getBottomY()` caused 59% of FPs
            if (previous != null && areOnSameBaseline(previous, current)) {
                double gap = current.getLeftX() - previous.getRightX();
                double avgHeight = (previous.getHeight() + current.getHeight()) / 2.0;
                // Gap larger than 3x text height suggests table columns
                if (gap > avgHeight * 3.0) {
                    return true;
                }
            }
            previous = current;
        }
        return false;
    }

    /**
     * Checks if two text chunks are on the same baseline.
     *
     * @param chunk1 First text chunk.
     * @param chunk2 Second text chunk.
     * @return true if baselines are aligned within epsilon.
     */
    private static boolean areOnSameBaseline(TextChunk chunk1, TextChunk chunk2) {
        double baselineDiff = Math.abs(chunk1.getBaseLine() - chunk2.getBaseLine());
        double avgHeight = (chunk1.getHeight() + chunk2.getHeight()) / 2.0;
        return baselineDiff < avgHeight * BASELINE_EPSILON;
    }

    /**
     * Counts groups of text chunks with aligned baselines and large gaps.
     * Multiple aligned groups suggest a table structure.
     *
     * @param textChunks    The list of text chunks.
     * @param gapMultiplier The gap threshold multiplier.
     * @return The number of aligned groups detected.
     */
    private static int countAlignedLineGroups(List<TextChunk> textChunks, double gapMultiplier) {
        if (textChunks.size() < 2) {
            return 0;
        }

        // Group text chunks by baseline
        Map<Double, List<TextChunk>> baselineGroups = new HashMap<>();
        for (TextChunk chunk : textChunks) {
            if (chunk.isWhiteSpaceChunk()) {
                continue;
            }
            // Round baseline to group similar values
            double roundedBaseline = Math.round(chunk.getBaseLine() * 10.0) / 10.0;

            // Find existing group within epsilon
            Double matchedKey = null;
            for (Double key : baselineGroups.keySet()) {
                if (Math.abs(key - roundedBaseline) < chunk.getHeight() * BASELINE_EPSILON) {
                    matchedKey = key;
                    break;
                }
            }

            if (matchedKey != null) {
                baselineGroups.get(matchedKey).add(chunk);
            } else {
                List<TextChunk> group = new ArrayList<>();
                group.add(chunk);
                baselineGroups.put(roundedBaseline, group);
            }
        }

        // Count groups with multiple chunks and large gaps
        int alignedGroupCount = 0;
        for (List<TextChunk> group : baselineGroups.values()) {
            if (group.size() >= 2) {
                // Sort by X position
                group.sort((a, b) -> Double.compare(a.getLeftX(), b.getLeftX()));

                // Check for large gaps between consecutive chunks
                boolean hasLargeGap = false;
                for (int i = 1; i < group.size(); i++) {
                    TextChunk prev = group.get(i - 1);
                    TextChunk curr = group.get(i);
                    double gap = curr.getLeftX() - prev.getRightX();
                    double avgHeight = (prev.getHeight() + curr.getHeight()) / 2.0;
                    if (gap > avgHeight * gapMultiplier) {
                        hasLargeGap = true;
                        break;
                    }
                }

                if (hasLargeGap) {
                    alignedGroupCount++;
                }
            }
        }

        return alignedGroupCount;
    }

    /**
     * Performs batch triage for all pages in a document.
     *
     * @param pageContents Map of page number to filtered contents.
     * @param config       The hybrid configuration.
     * @return Map of page number to triage result.
     */
    public static Map<Integer, TriageResult> triageAllPages(
            Map<Integer, List<IObject>> pageContents,
            HybridConfig config) {
        return triageAllPages(pageContents, new TriageThresholds());
    }

    /**
     * Performs batch triage for all pages with custom thresholds.
     *
     * @param pageContents Map of page number to filtered contents.
     * @param thresholds   The triage thresholds to use.
     * @return Map of page number to triage result.
     */
    public static Map<Integer, TriageResult> triageAllPages(
            Map<Integer, List<IObject>> pageContents,
            TriageThresholds thresholds) {

        Map<Integer, TriageResult> results = new HashMap<>();

        for (Map.Entry<Integer, List<IObject>> entry : pageContents.entrySet()) {
            int pageNumber = entry.getKey();
            List<IObject> contents = entry.getValue();
            TriageResult result = classifyPage(contents, pageNumber, thresholds);
            results.put(pageNumber, result);
        }

        return results;
    }

    /**
     * Performs batch triage for a list of pages (indexed by position).
     *
     * @param pagesContents List of page contents, where index is page number.
     * @param config        The hybrid configuration.
     * @return Map of page number to triage result.
     */
    public static Map<Integer, TriageResult> triageAllPages(
            List<List<IObject>> pagesContents,
            HybridConfig config) {

        Map<Integer, List<IObject>> pageMap = new HashMap<>();
        for (int i = 0; i < pagesContents.size(); i++) {
            pageMap.put(i, pagesContents.get(i));
        }
        return triageAllPages(pageMap, config);
    }
}

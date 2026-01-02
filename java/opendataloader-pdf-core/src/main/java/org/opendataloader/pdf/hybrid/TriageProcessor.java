/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
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
    public static final int DEFAULT_ALIGNED_LINE_GROUPS_THRESHOLD = 3;

    /** Default gap multiplier for grid pattern detection (relative to text height). */
    public static final double DEFAULT_GRID_GAP_MULTIPLIER = 3.0;

    /** Epsilon for comparing baseline coordinates. */
    private static final double BASELINE_EPSILON = 0.1;

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

        /**
         * Creates new triage signals.
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
            this.lineChunkCount = lineChunkCount;
            this.textChunkCount = textChunkCount;
            this.lineToTextRatio = lineToTextRatio;
            this.alignedLineGroups = alignedLineGroups;
            this.hasTableBorder = hasTableBorder;
            this.hasSuspiciousPattern = hasSuspiciousPattern;
        }

        /**
         * Creates empty signals with default values.
         *
         * @return A new TriageSignals with zero/false values.
         */
        public static TriageSignals empty() {
            return new TriageSignals(0, 0, 0.0, 0, false, false);
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
                   hasSuspiciousPattern == that.hasSuspiciousPattern;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lineChunkCount, textChunkCount, lineToTextRatio,
                                alignedLineGroups, hasTableBorder, hasSuspiciousPattern);
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

        // Signal 2: Suspicious text patterns (catches borderless tables)
        if (signals.hasSuspiciousPattern()) {
            return TriageResult.backend(pageNumber, 0.9, signals);
        }

        // Signal 3: High LineChunk ratio (grid/border elements)
        if (signals.getLineToTextRatio() > thresholds.getLineRatioThreshold()) {
            return TriageResult.backend(pageNumber, 0.8, signals);
        }

        // Signal 4: Grid pattern detection (aligned baselines with gaps)
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

        int lineChunkCount = 0;
        int textChunkCount = 0;
        List<TextChunk> textChunks = new ArrayList<>();

        // Count content types
        for (IObject content : filteredContents) {
            if (content instanceof LineChunk) {
                lineChunkCount++;
            } else if (content instanceof TextChunk) {
                textChunkCount++;
                textChunks.add((TextChunk) content);
            }
        }

        // Calculate line to text ratio
        int totalCount = filteredContents.size();
        double lineToTextRatio = totalCount > 0 ? (double) lineChunkCount / totalCount : 0.0;

        // Check for TableBorder in StaticContainers
        boolean hasTableBorder = checkTableBorderPresence(pageNumber);

        // Check for suspicious text patterns (grid-like layout)
        boolean hasSuspiciousPattern = checkSuspiciousPatterns(textChunks);

        // Count aligned line groups (potential table columns)
        int alignedLineGroups = countAlignedLineGroups(textChunks, thresholds.getGridGapMultiplier());

        return new TriageSignals(
            lineChunkCount,
            textChunkCount,
            lineToTextRatio,
            alignedLineGroups,
            hasTableBorder,
            hasSuspiciousPattern
        );
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
            if (previous != null) {
                // Check if text chunks are on the same line with large gap
                if (areOnSameBaseline(previous, current)) {
                    double gap = current.getLeftX() - previous.getRightX();
                    double avgHeight = (previous.getHeight() + current.getHeight()) / 2.0;
                    // Gap larger than 3x text height suggests table columns
                    if (gap > avgHeight * 3.0) {
                        return true;
                    }
                }
                // Check for overlapping Y coordinates (out of reading order)
                if (previous.getTopY() < current.getBottomY()) {
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

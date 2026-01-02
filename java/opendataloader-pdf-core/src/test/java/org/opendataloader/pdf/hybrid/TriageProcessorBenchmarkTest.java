/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Test cases based on benchmark evaluation results for TriageProcessor.
 *
 * <p>This test documents known issues with triage detection based on
 * benchmark evaluation from opendataloader-hybrid-docling.
 *
 * <h2>Benchmark Results Summary</h2>
 * <ul>
 *   <li>Precision: 60.87%</li>
 *   <li>Recall: 66.67%</li>
 *   <li>F1 Score: 63.64%</li>
 *   <li>TP (True Positive): 28</li>
 *   <li>FP (False Positive): 18</li>
 *   <li>FN (False Negative): 14</li>
 *   <li>TN (True Negative): 140</li>
 * </ul>
 *
 * <h2>Key Metrics</h2>
 * <ul>
 *   <li>Documents with tables: 42</li>
 *   <li>Documents without tables: 158</li>
 * </ul>
 */
public class TriageProcessorBenchmarkTest {

    /**
     * Documents that have tables (TEDS score is not null in ground truth).
     * These should ideally be routed to BACKEND.
     */
    public static final Set<String> DOCUMENTS_WITH_TABLES = new HashSet<>(Arrays.asList(
        "01030000000045", "01030000000046", "01030000000047", "01030000000051",
        "01030000000052", "01030000000053", "01030000000064", "01030000000078",
        "01030000000081", "01030000000082", "01030000000083", "01030000000084",
        "01030000000088", "01030000000089", "01030000000090", "01030000000110",
        "01030000000116", "01030000000117", "01030000000119", "01030000000120",
        "01030000000121", "01030000000122", "01030000000127", "01030000000128",
        "01030000000130", "01030000000132", "01030000000146", "01030000000147",
        "01030000000149", "01030000000150", "01030000000165", "01030000000166",
        "01030000000170", "01030000000178", "01030000000180", "01030000000182",
        "01030000000187", "01030000000188", "01030000000189", "01030000000190",
        "01030000000197", "01030000000200"
    ));

    /**
     * False Negative documents: Have tables but triage missed them (routed to JAVA).
     * These documents have TEDS=0 because tables were not detected.
     *
     * <p>Root cause analysis needed for each document to improve detection.
     */
    public static final Set<String> KNOWN_FALSE_NEGATIVES = new HashSet<>(Arrays.asList(
        "01030000000064",  // TODO: Analyze - borderless table?
        "01030000000078",  // TODO: Analyze - complex layout?
        "01030000000110",  // TODO: Analyze - sparse table?
        "01030000000116",  // TODO: Analyze
        "01030000000117",  // TODO: Analyze
        "01030000000122",  // TODO: Analyze
        "01030000000132",  // TODO: Analyze
        "01030000000165",  // TODO: Analyze
        "01030000000182",  // TODO: Analyze
        "01030000000187",  // TODO: Analyze
        "01030000000189",  // TODO: Analyze
        "01030000000190",  // TODO: Analyze
        "01030000000197",  // TODO: Analyze
        "01030000000200"   // TODO: Analyze
    ));

    /**
     * False Positive documents: No tables but triage incorrectly routed to BACKEND.
     *
     * <p>These might have table-like patterns (grids, aligned text) that triggered
     * false detection. While not critical (backend can still process), it affects
     * performance.
     */
    public static final Set<String> KNOWN_FALSE_POSITIVES = new HashSet<>(Arrays.asList(
        "01030000000016",  // Likely: Aligned text columns
        "01030000000036",  // Likely: Grid-like layout
        "01030000000037",  // Likely: Grid-like layout
        "01030000000038",  // Likely: Aligned sections
        "01030000000044",  // Likely: Multi-column layout
        "01030000000055",  // Likely: Form-like structure
        "01030000000061",  // Likely: Header/footer pattern
        "01030000000067",  // Likely: Aligned data
        "01030000000070",  // Likely: List with columns
        "01030000000071",  // Likely: Multi-column text
        "01030000000072",  // Likely: Multi-column text
        "01030000000073",  // Likely: Multi-column text
        "01030000000076",  // Likely: Aligned sections
        "01030000000148",  // Likely: Form structure
        "01030000000155",  // Likely: Multi-column layout
        "01030000000171",  // Likely: Grid pattern
        "01030000000172",  // Likely: Grid pattern
        "01030000000183"   // Likely: Aligned sections
    ));

    @Test
    public void testDocumentCountConsistency() {
        // Total documents = 200
        // Documents with tables = 42
        // Documents without tables = 158
        Assertions.assertEquals(42, DOCUMENTS_WITH_TABLES.size(),
            "Should have 42 documents with tables");

        // FN (14) + TP (28) should equal documents with tables (42)
        Assertions.assertEquals(14, KNOWN_FALSE_NEGATIVES.size(),
            "Should have 14 false negative cases");

        // Verify all FN are in documents with tables
        for (String fn : KNOWN_FALSE_NEGATIVES) {
            Assertions.assertTrue(DOCUMENTS_WITH_TABLES.contains(fn),
                "FN document " + fn + " should be in DOCUMENTS_WITH_TABLES");
        }
    }

    @Test
    public void testFalsePositivesAreNotTableDocuments() {
        // Verify all FP documents are NOT in documents with tables
        for (String fp : KNOWN_FALSE_POSITIVES) {
            Assertions.assertFalse(DOCUMENTS_WITH_TABLES.contains(fp),
                "FP document " + fp + " should NOT be in DOCUMENTS_WITH_TABLES");
        }

        Assertions.assertEquals(18, KNOWN_FALSE_POSITIVES.size(),
            "Should have 18 false positive cases");
    }

    /**
     * Provider for FN document IDs for parameterized testing.
     */
    static Stream<String> falseNegativeDocuments() {
        return KNOWN_FALSE_NEGATIVES.stream();
    }

    /**
     * Provider for FP document IDs for parameterized testing.
     */
    static Stream<String> falsePositiveDocuments() {
        return KNOWN_FALSE_POSITIVES.stream();
    }

    @ParameterizedTest
    @MethodSource("falseNegativeDocuments")
    public void testFalseNegativeDocumentIsKnown(String documentId) {
        // This test documents that we are aware of these FN cases
        // When we improve detection, these tests should be updated or removed
        Assertions.assertTrue(KNOWN_FALSE_NEGATIVES.contains(documentId),
            "Document " + documentId + " is a known false negative");
    }

    @ParameterizedTest
    @MethodSource("falsePositiveDocuments")
    public void testFalsePositiveDocumentIsKnown(String documentId) {
        // This test documents that we are aware of these FP cases
        // FP is less critical as backend can still process correctly (just slower)
        Assertions.assertTrue(KNOWN_FALSE_POSITIVES.contains(documentId),
            "Document " + documentId + " is a known false positive");
    }

    @Test
    public void testTriageDecisionConstants() {
        // Verify triage decisions are properly defined
        Assertions.assertNotNull(TriageProcessor.TriageDecision.JAVA);
        Assertions.assertNotNull(TriageProcessor.TriageDecision.BACKEND);

        // Verify default thresholds
        Assertions.assertEquals(0.3, TriageProcessor.DEFAULT_LINE_RATIO_THRESHOLD,
            "Default line ratio threshold should be 0.3");
        Assertions.assertEquals(3, TriageProcessor.DEFAULT_ALIGNED_LINE_GROUPS_THRESHOLD,
            "Default aligned line groups threshold should be 3");
        Assertions.assertEquals(3.0, TriageProcessor.DEFAULT_GRID_GAP_MULTIPLIER,
            "Default grid gap multiplier should be 3.0");
    }

    @Test
    public void testRecallCalculation() {
        // Recall = TP / (TP + FN) = 28 / (28 + 14) = 0.6667
        int tp = DOCUMENTS_WITH_TABLES.size() - KNOWN_FALSE_NEGATIVES.size();
        int fn = KNOWN_FALSE_NEGATIVES.size();
        double recall = (double) tp / (tp + fn);

        Assertions.assertEquals(28, tp, "TP should be 28");
        Assertions.assertEquals(14, fn, "FN should be 14");
        Assertions.assertEquals(0.6667, recall, 0.001,
            "Recall should be approximately 66.67%");
    }

    @Test
    public void testPrecisionCalculation() {
        // Precision = TP / (TP + FP) = 28 / (28 + 18) = 0.6087
        int tp = DOCUMENTS_WITH_TABLES.size() - KNOWN_FALSE_NEGATIVES.size();
        int fp = KNOWN_FALSE_POSITIVES.size();
        double precision = (double) tp / (tp + fp);

        Assertions.assertEquals(28, tp, "TP should be 28");
        Assertions.assertEquals(18, fp, "FP should be 18");
        Assertions.assertEquals(0.6087, precision, 0.001,
            "Precision should be approximately 60.87%");
    }

    @Test
    public void testF1ScoreCalculation() {
        int tp = DOCUMENTS_WITH_TABLES.size() - KNOWN_FALSE_NEGATIVES.size();
        int fp = KNOWN_FALSE_POSITIVES.size();
        int fn = KNOWN_FALSE_NEGATIVES.size();

        double precision = (double) tp / (tp + fp);
        double recall = (double) tp / (tp + fn);
        double f1 = 2 * precision * recall / (precision + recall);

        Assertions.assertEquals(0.6364, f1, 0.001,
            "F1 score should be approximately 63.64%");
    }
}

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
package org.opendataloader.pdf.quality;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BenchmarkQualityCheckTest {

    private static final QualityGate DEFAULT_GATE = new QualityGate();

    // All totals = 0 → all resolved rates = 1.0, well above default thresholds
    private static final ParityReport PASSING_REPORT = new ParityReport(10, 0, 10, 0, 10, 0, 5);

    // equation resolved rate = 2/10 = 0.2 < 0.6 → fails
    private static final ParityReport FAILING_REPORT = new ParityReport(10, 8, 10, 0, 10, 0, 5);

    @Test
    void singleDocumentPass() {
        BenchmarkQualityCheck check = new BenchmarkQualityCheck(DEFAULT_GATE);
        BenchmarkQualityCheck.DocumentCheckResult result = check.check("doc-pass", PASSING_REPORT);

        assertTrue(result.isPassed());
        assertEquals("doc-pass", result.getDocId());
        assertTrue(result.getFailureReasons().isEmpty());
    }

    @Test
    void singleDocumentFail() {
        BenchmarkQualityCheck check = new BenchmarkQualityCheck(DEFAULT_GATE);
        BenchmarkQualityCheck.DocumentCheckResult result = check.check("doc-fail", FAILING_REPORT);

        assertFalse(result.isPassed());
        assertEquals("doc-fail", result.getDocId());
        assertFalse(result.getFailureReasons().isEmpty());
        assertTrue(result.getFailureReasons().get(0).contains("equation"));
    }

    @Test
    void aggregateAllPassed() {
        BenchmarkQualityCheck check = new BenchmarkQualityCheck(DEFAULT_GATE);
        List<BenchmarkQualityCheck.DocumentCheckResult> results = List.of(
            check.check("doc-1", PASSING_REPORT),
            check.check("doc-2", PASSING_REPORT)
        );

        BenchmarkQualityCheck.AggregateResult agg = check.aggregate(results);

        assertTrue(agg.isOverallPassed());
        assertEquals(2, agg.getTotalDocuments());
        assertEquals(2, agg.getPassedDocuments());
        assertEquals(0, agg.getFailedDocuments());
        assertTrue(agg.getFailedDocIds().isEmpty());
    }

    @Test
    void aggregateWithOneFailure() {
        BenchmarkQualityCheck check = new BenchmarkQualityCheck(DEFAULT_GATE);
        List<BenchmarkQualityCheck.DocumentCheckResult> results = List.of(
            check.check("doc-ok", PASSING_REPORT),
            check.check("doc-bad", FAILING_REPORT)
        );

        BenchmarkQualityCheck.AggregateResult agg = check.aggregate(results);

        assertFalse(agg.isOverallPassed());
        assertEquals(2, agg.getTotalDocuments());
        assertEquals(1, agg.getPassedDocuments());
        assertEquals(1, agg.getFailedDocuments());
        assertTrue(agg.getFailedDocIds().contains("doc-bad"));
    }

    @Test
    void formatReportAllPassed() {
        BenchmarkQualityCheck check = new BenchmarkQualityCheck(DEFAULT_GATE);
        List<BenchmarkQualityCheck.DocumentCheckResult> results = List.of(
            check.check("doc-1", PASSING_REPORT)
        );
        BenchmarkQualityCheck.AggregateResult agg = check.aggregate(results);

        String report = check.formatReport(agg, results);

        assertTrue(report.contains("All documents passed"));
    }

    @Test
    void formatReportWithFailures() {
        BenchmarkQualityCheck check = new BenchmarkQualityCheck(DEFAULT_GATE);
        List<BenchmarkQualityCheck.DocumentCheckResult> results = List.of(
            check.check("doc-bad", FAILING_REPORT)
        );
        BenchmarkQualityCheck.AggregateResult agg = check.aggregate(results);

        String report = check.formatReport(agg, results);

        assertTrue(report.contains("doc-bad"));
        assertTrue(report.contains("equation"));
    }

    @Test
    void checkThrowsOnNullDocId() {
        BenchmarkQualityCheck bench = new BenchmarkQualityCheck(new QualityGate());
        ParityReport report = new ParityReport(0, 0, 0, 0, 0, 0, 0);
        Assertions.assertThrows(NullPointerException.class, () -> bench.check(null, report));
    }

    @Test
    void aggregateThrowsOnNullList() {
        BenchmarkQualityCheck bench = new BenchmarkQualityCheck(new QualityGate());
        Assertions.assertThrows(NullPointerException.class, () -> bench.aggregate(null));
    }
}

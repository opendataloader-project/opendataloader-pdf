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

import org.junit.Test;
import org.junit.jupiter.api.Assertions;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class QualityGateTest {

    @Test
    public void allRatesAboveThreshold_passesTrue_failureReasonsEmpty() {
        ParityReport report = new ParityReport(10, 0, 10, 0, 10, 0, 5);
        QualityGate gate = new QualityGate();

        assertTrue(gate.passes(report));
        assertTrue(gate.failureReasons(report).isEmpty());
    }

    @Test
    public void equationRateBelowThreshold_passesFalse_failureReasonsContainsEquation() {
        // 3 of 10 resolved → 0.3, below default 0.6
        ParityReport report = new ParityReport(10, 7, 10, 0, 10, 0, 5);
        QualityGate gate = new QualityGate();

        assertFalse(gate.passes(report));
        List<String> reasons = gate.failureReasons(report);
        assertEquals(1, reasons.size());
        assertTrue(reasons.get(0).toLowerCase().contains("equation"));
    }

    @Test
    public void multipleFailures_failureReasonsHasMultipleEntries() {
        // equation: 0.3 (below 0.6), caption: 0.3 (below 0.6), citation: 0.2 (below 0.5)
        ParityReport report = new ParityReport(10, 7, 10, 7, 10, 8, 5);
        QualityGate gate = new QualityGate();

        assertFalse(gate.passes(report));
        List<String> reasons = gate.failureReasons(report);
        assertEquals(3, reasons.size());
    }

    @Test
    public void defaultConstructorUsesDefaultThresholds() {
        QualityGate defaultGate = new QualityGate();
        QualityGate explicitGate = new QualityGate(
            QualityGate.DEFAULT_EQUATION_THRESHOLD,
            QualityGate.DEFAULT_CAPTION_THRESHOLD,
            QualityGate.DEFAULT_CITATION_THRESHOLD
        );

        // Both should agree on a borderline report
        ParityReport report = new ParityReport(10, 4, 10, 4, 10, 5, 0);
        assertEquals(defaultGate.passes(report), explicitGate.passes(report));
        assertEquals(defaultGate.failureReasons(report), explicitGate.failureReasons(report));
    }

    @Test
    public void failureReasonsListIsUnmodifiable() {
        QualityGate gate = new QualityGate(1.0, 1.0, 1.0);
        // Force a failure to get a non-empty list
        ParityReport failing = new ParityReport(2, 2, 2, 2, 2, 2, 0); // all unresolved
        List<String> reasons = gate.failureReasons(failing);
        Assertions.assertThrows(UnsupportedOperationException.class, () -> reasons.add("x"));
    }
}

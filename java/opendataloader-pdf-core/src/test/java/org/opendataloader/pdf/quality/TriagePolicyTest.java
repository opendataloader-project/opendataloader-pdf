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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TriagePolicyTest {

    // Helper: all-resolved report (nothing unresolved, no hard-fail triggers)
    private ParityReport allResolved() {
        // 10 equations all resolved, 10 captions all resolved, 10 citations all resolved, 5 refs
        return new ParityReport(10, 0, 10, 0, 10, 0, 5);
    }

    // Helper: report where equation rate is below 0.6 (soft fail via gate)
    // 10 equations, 5 unresolved → rate = 0.5 < 0.6 threshold
    private ParityReport equationBelowThreshold() {
        return new ParityReport(10, 5, 10, 0, 10, 0, 5);
    }

    // Helper: hard fail — all equations unresolved (rate == 0.0)
    // 10 equations, 10 unresolved → rate = 0.0 → hard fail
    private ParityReport equationTotalWipeout() {
        return new ParityReport(10, 10, 10, 0, 10, 0, 5);
    }

    // Helper: both hard fail AND gate fail simultaneously
    // equations total wipeout (hard fail) + equation rate 0.0 < 0.6 (gate soft fail)
    private ParityReport hardAndSoftFail() {
        // Same as wipeout — rate 0.0 triggers both hard fail and gate fail (0.0 < 0.6)
        return new ParityReport(10, 10, 10, 0, 10, 0, 5);
    }

    @Test
    void allResolved_shouldPass() {
        TriagePolicy policy = new TriagePolicy();
        TriageDecision decision = policy.evaluate(allResolved());

        assertEquals(TriageDecision.Outcome.PASS, decision.getOutcome());
        assertTrue(decision.passed());
        assertFalse(decision.hardFailed());
        assertEquals(1.0, decision.getCompositeScore(), 1e-9);
    }

    @Test
    void equationBelowThreshold_shouldSoftFail() {
        TriagePolicy policy = new TriagePolicy();
        TriageDecision decision = policy.evaluate(equationBelowThreshold());

        assertEquals(TriageDecision.Outcome.SOFT_FAIL, decision.getOutcome());
        assertFalse(decision.passed());
        assertFalse(decision.hardFailed());
        assertFalse(decision.getGateFailureReasons().isEmpty());
    }

    @Test
    void equationTotalWipeout_shouldHardFail() {
        TriagePolicy policy = new TriagePolicy();
        TriageDecision decision = policy.evaluate(equationTotalWipeout());

        assertEquals(TriageDecision.Outcome.HARD_FAIL, decision.getOutcome());
        assertTrue(decision.hardFailed());
        assertFalse(decision.passed());
        assertFalse(decision.getHardFailReasons().isEmpty());
    }

    @Test
    void hardFailTakesPrecedenceOverSoftFail() {
        // When both hard fail and gate fail are triggered, outcome must be HARD_FAIL
        TriagePolicy policy = new TriagePolicy();
        TriageDecision decision = policy.evaluate(hardAndSoftFail());

        assertEquals(TriageDecision.Outcome.HARD_FAIL, decision.getOutcome());
        assertTrue(decision.hardFailed());
        // gate failure reasons should still be non-null (populated)
        assertNotNull(decision.getGateFailureReasons());
        assertFalse(decision.getHardFailReasons().isEmpty());
    }

    @Test
    void triageDecisionFields_shouldBeCorrectlyPopulated() {
        TriagePolicy policy = new TriagePolicy();
        ParityReport report = equationBelowThreshold();
        TriageDecision decision = policy.evaluate(report);

        assertNotNull(decision.getGateFailureReasons());
        assertNotNull(decision.getHardFailReasons());
        assertNotNull(decision.getReport());
        assertSame(report, decision.getReport());
        // composite score is always computed — for 10 eqs, 5 unresolved: eq rate=0.5
        // score = 0.4*0.5 + 0.3*1.0 + 0.3*1.0 = 0.2 + 0.3 + 0.3 = 0.8
        assertEquals(0.8, decision.getCompositeScore(), 1e-9);
    }

    @Test
    void defaultConstructor_shouldProduceWorkingPolicy() {
        // Must not throw NPE or any exception
        TriagePolicy policy = new TriagePolicy();
        assertNotNull(policy);
        TriageDecision decision = policy.evaluate(allResolved());
        assertNotNull(decision);
    }

    @Test
    void evaluateNull_shouldThrowNullPointerException() {
        TriagePolicy policy = new TriagePolicy();
        assertThrows(NullPointerException.class, () -> policy.evaluate(null));
    }
}

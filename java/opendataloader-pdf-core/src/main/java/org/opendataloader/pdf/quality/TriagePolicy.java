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

import java.util.List;
import java.util.Objects;

public final class TriagePolicy {

    private final QualityGate gate;
    private final WeightedScorecard scorecard;
    private final HardFailDetector hardFailDetector;

    public TriagePolicy() {
        this(new QualityGate(), new WeightedScorecard(), new HardFailDetector());
    }

    public TriagePolicy(QualityGate gate, WeightedScorecard scorecard, HardFailDetector hardFailDetector) {
        this.gate = Objects.requireNonNull(gate, "gate must not be null");
        this.scorecard = Objects.requireNonNull(scorecard, "scorecard must not be null");
        this.hardFailDetector = Objects.requireNonNull(hardFailDetector, "hardFailDetector must not be null");
    }

    /**
     * Evaluates a ParityReport and returns a TriageDecision.
     * Decision rules:
     * 1. If hardFailDetector.detect(report) is non-empty → Outcome.HARD_FAIL
     * 2. Else if !gate.passes(report) → Outcome.SOFT_FAIL
     * 3. Else → Outcome.PASS
     * Always computes compositeScore from scorecard regardless of outcome.
     */
    public TriageDecision evaluate(ParityReport report) {
        Objects.requireNonNull(report, "report must not be null");

        List<String> hardFailReasons = hardFailDetector.detect(report);
        List<String> gateFailureReasons = gate.failureReasons(report);
        double compositeScore = scorecard.score(report);

        TriageDecision.Outcome outcome;
        if (!hardFailReasons.isEmpty()) {
            outcome = TriageDecision.Outcome.HARD_FAIL;
        } else if (!gateFailureReasons.isEmpty()) {
            outcome = TriageDecision.Outcome.SOFT_FAIL;
        } else {
            outcome = TriageDecision.Outcome.PASS;
        }

        return new TriageDecision(outcome, compositeScore, gateFailureReasons, hardFailReasons, report);
    }
}

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class TriageDecision {

    public enum Outcome { PASS, SOFT_FAIL, HARD_FAIL }

    private final Outcome outcome;
    private final double compositeScore;
    private final List<String> gateFailureReasons;
    private final List<String> hardFailReasons;
    private final ParityReport report;

    public TriageDecision(Outcome outcome, double compositeScore,
                          List<String> gateFailureReasons, List<String> hardFailReasons,
                          ParityReport report) {
        this.outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        this.compositeScore = compositeScore;
        this.gateFailureReasons = Collections.unmodifiableList(new ArrayList<>(
                gateFailureReasons != null ? gateFailureReasons : Collections.emptyList()));
        this.hardFailReasons = Collections.unmodifiableList(new ArrayList<>(
                hardFailReasons != null ? hardFailReasons : Collections.emptyList()));
        this.report = report;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public double getCompositeScore() {
        return compositeScore;
    }

    public List<String> getGateFailureReasons() {
        return gateFailureReasons;
    }

    public List<String> getHardFailReasons() {
        return hardFailReasons;
    }

    public ParityReport getReport() {
        return report;
    }

    public boolean passed() {
        return outcome == Outcome.PASS;
    }

    public boolean hardFailed() {
        return outcome == Outcome.HARD_FAIL;
    }
}

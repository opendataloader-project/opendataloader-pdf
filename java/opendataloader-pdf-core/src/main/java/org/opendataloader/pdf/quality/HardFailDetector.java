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

import java.util.Objects;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HardFailDetector {

    /**
     * Returns a list of hard-fail conditions that triggered.
     * Hard-fail conditions (all must hold for a pass):
     * 1. If totalEquations > 0, equationResolvedRate() must not be 0.0 exactly
     *    (i.e., at least one equation was resolved — total wipeout is always a hard fail)
     * 2. If totalCitations > 0, there must be at least one ReferenceEntryNode
     *    (totalReferences > 0 — citing with zero parsed refs is a data integrity issue)
     * Returns empty list if no hard-fail triggered.
     */
    public List<String> detect(ParityReport report) {
        Objects.requireNonNull(report, "report must not be null");
        List<String> failures = new ArrayList<>();
        if (report.getTotalEquations() > 0 && report.equationResolvedRate() == 0.0) {
            failures.add("equation total wipeout: " + report.getTotalEquations()
                    + " equations present but resolved rate is 0.0");
        }
        if (report.getTotalCitations() > 0 && report.getTotalReferences() == 0) {
            failures.add("citation integrity failure: " + report.getTotalCitations()
                    + " citations present but totalReferences is 0");
        }
        return Collections.unmodifiableList(failures);
    }
}

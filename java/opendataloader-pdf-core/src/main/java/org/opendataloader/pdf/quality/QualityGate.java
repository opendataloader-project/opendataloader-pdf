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

public final class QualityGate {

    public static final double DEFAULT_EQUATION_THRESHOLD = 0.6;
    public static final double DEFAULT_CAPTION_THRESHOLD = 0.6;
    public static final double DEFAULT_CITATION_THRESHOLD = 0.5;

    private final double equationThreshold;
    private final double captionThreshold;
    private final double citationThreshold;

    public QualityGate() {
        this(DEFAULT_EQUATION_THRESHOLD, DEFAULT_CAPTION_THRESHOLD, DEFAULT_CITATION_THRESHOLD);
    }

    public QualityGate(double equationThreshold, double captionThreshold, double citationThreshold) {
        if (equationThreshold < 0.0 || equationThreshold > 1.0)
            throw new IllegalArgumentException("equationThreshold must be in [0, 1]: " + equationThreshold);
        if (captionThreshold < 0.0 || captionThreshold > 1.0)
            throw new IllegalArgumentException("captionThreshold must be in [0, 1]: " + captionThreshold);
        if (citationThreshold < 0.0 || citationThreshold > 1.0)
            throw new IllegalArgumentException("citationThreshold must be in [0, 1]: " + citationThreshold);
        this.equationThreshold = equationThreshold;
        this.captionThreshold = captionThreshold;
        this.citationThreshold = citationThreshold;
    }

    public boolean passes(ParityReport report) {
        return failureReasons(report).isEmpty();
    }

    public List<String> failureReasons(ParityReport report) {
        List<String> reasons = new ArrayList<>();
        if (report.equationResolvedRate() < equationThreshold) {
            reasons.add(String.format(
                "equation resolved rate %.2f is below threshold %.2f",
                report.equationResolvedRate(), equationThreshold));
        }
        if (report.captionResolvedRate() < captionThreshold) {
            reasons.add(String.format(
                "caption resolved rate %.2f is below threshold %.2f",
                report.captionResolvedRate(), captionThreshold));
        }
        if (report.citationResolvedRate() < citationThreshold) {
            reasons.add(String.format(
                "citation resolved rate %.2f is below threshold %.2f",
                report.citationResolvedRate(), citationThreshold));
        }
        return Collections.unmodifiableList(reasons);
    }
}

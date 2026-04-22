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

public final class WeightedScorecard {

    public static final double DEFAULT_EQUATION_WEIGHT = 0.4;
    public static final double DEFAULT_CAPTION_WEIGHT = 0.3;
    public static final double DEFAULT_CITATION_WEIGHT = 0.3;

    private final double equationWeight;
    private final double captionWeight;
    private final double citationWeight;

    /** Uses default weights (sum to 1.0). */
    public WeightedScorecard() {
        this(DEFAULT_EQUATION_WEIGHT, DEFAULT_CAPTION_WEIGHT, DEFAULT_CITATION_WEIGHT);
    }

    /** Custom weights — must sum to 1.0 (tolerance 1e-6), else throw IllegalArgumentException. */
    public WeightedScorecard(double equationWeight, double captionWeight, double citationWeight) {
        double sum = equationWeight + captionWeight + citationWeight;
        if (Math.abs(sum - 1.0) > 1e-6) {
            throw new IllegalArgumentException(
                    "Weights must sum to 1.0 but got: " + sum);
        }
        this.equationWeight = equationWeight;
        this.captionWeight = captionWeight;
        this.citationWeight = citationWeight;
    }

    /**
     * Computes the composite score:
     * score = equationWeight * report.equationResolvedRate()
     *       + captionWeight  * report.captionResolvedRate()
     *       + citationWeight * report.citationResolvedRate()
     * Returns value in [0.0, 1.0].
     */
    public double score(ParityReport report) {
        return equationWeight * report.equationResolvedRate()
             + captionWeight  * report.captionResolvedRate()
             + citationWeight * report.citationResolvedRate();
    }
}

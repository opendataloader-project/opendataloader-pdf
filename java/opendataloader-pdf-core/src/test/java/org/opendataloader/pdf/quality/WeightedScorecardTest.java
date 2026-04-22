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

public class WeightedScorecardTest {

    // Helper: all resolved (rates all 1.0)
    private ParityReport allResolved() {
        return new ParityReport(10, 0, 10, 0, 10, 0, 5);
    }

    // Helper: all unresolved (rates all 0.0)
    private ParityReport allUnresolved() {
        return new ParityReport(10, 10, 10, 10, 10, 10, 5);
    }

    @Test
    void defaultWeightsSumToOneAndAllResolvedScoresOne() {
        WeightedScorecard sc = new WeightedScorecard();
        double sum = WeightedScorecard.DEFAULT_EQUATION_WEIGHT
                   + WeightedScorecard.DEFAULT_CAPTION_WEIGHT
                   + WeightedScorecard.DEFAULT_CITATION_WEIGHT;
        assertEquals(1.0, sum, 1e-9);
        assertEquals(1.0, sc.score(allResolved()), 1e-9);
    }

    @Test
    void customWeightsMixedRatesCorrectWeightedSum() {
        // weights 0.5 / 0.3 / 0.2
        WeightedScorecard sc = new WeightedScorecard(0.5, 0.3, 0.2);
        // equationResolvedRate = 8/10 = 0.8
        // captionResolvedRate  = 5/10 = 0.5
        // citationResolvedRate = 2/10 = 0.2
        ParityReport report = new ParityReport(10, 2, 10, 5, 10, 8, 3);
        double expected = 0.5 * 0.8 + 0.3 * 0.5 + 0.2 * 0.2;
        assertEquals(expected, sc.score(report), 0.001);
    }

    @Test
    void weightsThatDontSumToOneThrowIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedScorecard(0.5, 0.3, 0.3));
    }

    @Test
    void allUnresolvedScoresZero() {
        WeightedScorecard sc = new WeightedScorecard();
        assertEquals(0.0, sc.score(allUnresolved()), 1e-9);
    }

    @Test
    void partialResolutionScoreIsBetweenZeroAndOne() {
        WeightedScorecard sc = new WeightedScorecard();
        // equationResolvedRate = 5/10 = 0.5, caption = 7/10 = 0.7, citation = 3/10 = 0.3
        ParityReport report = new ParityReport(10, 5, 10, 3, 10, 7, 4);
        double result = sc.score(report);
        assertTrue(result > 0.0 && result < 1.0,
                "Expected score in (0,1) but got: " + result);
    }
}

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
package org.opendataloader.pdf.utils;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ModeWeightStatisticsTest {

    @Test
    void getModeReturnsMostFrequentScoreWithinRange() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 9.0, 14.0);
        statistics.addScore(10.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(14.0);
        statistics.sortByFrequency();

        double mode = statistics.getMode();

        assertThat(mode).isCloseTo(12.0, Offset.offset(0.001));
    }

    @Test
    void getModeReturnsNaNWhenNoScoresWithinRange() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 9.0, 13.0);
        statistics.addScore(5.0);
        statistics.addScore(7.0);
        statistics.sortByFrequency();

        double mode = statistics.getMode();

        assertThat(mode).isCloseTo(0.0, Offset.offset(0.001));
    }

    @Test
    void getBoostGivesFractionalRankForScoresAboveMode() {
        ModeWeightStatistics statistics = new ModeWeightStatistics(10.0, 32.0, 9.0, 13.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(12.0);
        statistics.addScore(10.0);
        statistics.addScore(14.0);
        statistics.addScore(16.0);

        double boostForFourteen = statistics.getBoost(14.0);
        double boostForSixteen = statistics.getBoost(16.0);
        double boostForMode = statistics.getBoost(12.0);

        assertThat(boostForFourteen).isCloseTo(0.5, Offset.offset(0.001));
        assertThat(boostForSixteen).isCloseTo(1.0, Offset.offset(0.001));
        assertThat(boostForMode).isCloseTo(0.0, Offset.offset(0.001));
    }
}

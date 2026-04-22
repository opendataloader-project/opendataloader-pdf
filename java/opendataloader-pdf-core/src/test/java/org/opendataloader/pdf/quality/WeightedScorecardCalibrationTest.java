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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

public class WeightedScorecardCalibrationTest {

    @Test
    void fromJson_validJson_weightsMatchExpectedValuesAndSumToOne() {
        String json = "{\"equationWeight\": 0.4, \"captionWeight\": 0.3, \"citationWeight\": 0.3}";

        WeightedScorecard scorecard = WeightedScorecard.fromJson(json);

        assertThat(scorecard.getEquationWeight()).isCloseTo(0.4, within(1e-9));
        assertThat(scorecard.getCaptionWeight()).isCloseTo(0.3, within(1e-9));
        assertThat(scorecard.getCitationWeight()).isCloseTo(0.3, within(1e-9));
        double sum = scorecard.getEquationWeight() + scorecard.getCaptionWeight() + scorecard.getCitationWeight();
        assertThat(sum).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void fromJson_invalidJson_throwsException() {
        String badJson = "not valid json {{{";

        assertThatThrownBy(() -> WeightedScorecard.fromJson(badJson))
                .isInstanceOfAny(UncheckedIOException.class, RuntimeException.class);
    }

    @Test
    void fromFile_actualWeightsFile_weightsMatchAndSumToOne() throws Exception {
        Path weightsFile = Paths.get(
                System.getProperty("user.dir"),
                "../../benchmarks/config/scorecard-weights-v0.json");

        WeightedScorecard scorecard = WeightedScorecard.fromFile(weightsFile);

        double sum = scorecard.getEquationWeight() + scorecard.getCaptionWeight() + scorecard.getCitationWeight();
        assertThat(sum).isCloseTo(1.0, within(1e-6));
        assertThat(scorecard.getEquationWeight()).isCloseTo(0.4, within(1e-9));
        assertThat(scorecard.getCaptionWeight()).isCloseTo(0.3, within(1e-9));
        assertThat(scorecard.getCitationWeight()).isCloseTo(0.3, within(1e-9));
    }
}

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

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class HardFailDetectorTest {

    private final HardFailDetector detector = new HardFailDetector();

    @Test
    void noEquationsAndNoCitationsNoHardFails() {
        // totalEquations=0, totalCitations=0 → neither condition triggers
        ParityReport report = new ParityReport(0, 0, 5, 1, 0, 0, 0);
        List<String> fails = detector.detect(report);
        assertTrue(fails.isEmpty());
    }

    @Test
    void equationsPresentAllResolvedNoHardFail() {
        // totalEquations=10, unresolvedEquations=0 → rate=1.0 → not 0.0 → pass
        ParityReport report = new ParityReport(10, 0, 5, 1, 0, 0, 0);
        List<String> fails = detector.detect(report);
        assertTrue(fails.isEmpty());
    }

    @Test
    void equationsPresentNoneResolvedHardFailDetected() {
        // totalEquations=10, unresolvedEquations=10 → rate=0.0 → hard fail
        ParityReport report = new ParityReport(10, 10, 5, 1, 0, 0, 0);
        List<String> fails = detector.detect(report);
        assertEquals(1, fails.size());
    }

    @Test
    void citationsPresentWithReferencesNoHardFail() {
        // totalCitations=5, totalReferences=3 → pass
        ParityReport report = new ParityReport(0, 0, 0, 0, 5, 2, 3);
        List<String> fails = detector.detect(report);
        assertTrue(fails.isEmpty());
    }

    @Test
    void citationsPresentZeroReferencesHardFailDetected() {
        // totalCitations=5, totalReferences=0 → hard fail
        ParityReport report = new ParityReport(0, 0, 0, 0, 5, 2, 0);
        List<String> fails = detector.detect(report);
        assertEquals(1, fails.size());
    }

    @Test
    void bothHardFailsAtOnceTwoEntriesInList() {
        // totalEquations=10, unresolvedEquations=10 → rate=0.0 → hard fail
        // totalCitations=5, totalReferences=0 → hard fail
        ParityReport report = new ParityReport(10, 10, 0, 0, 5, 2, 0);
        List<String> fails = detector.detect(report);
        assertEquals(2, fails.size());
    }
}

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

public final class ParityReport {

    private final int totalEquations;
    private final int unresolvedEquations;
    private final int totalCaptions;
    private final int unresolvedCaptions;
    private final int totalCitations;
    private final int unresolvedCitations;
    private final int totalReferences;

    public ParityReport(int totalEquations, int unresolvedEquations,
                        int totalCaptions, int unresolvedCaptions,
                        int totalCitations, int unresolvedCitations,
                        int totalReferences) {
        this.totalEquations = totalEquations;
        this.unresolvedEquations = unresolvedEquations;
        this.totalCaptions = totalCaptions;
        this.unresolvedCaptions = unresolvedCaptions;
        this.totalCitations = totalCitations;
        this.unresolvedCitations = unresolvedCitations;
        this.totalReferences = totalReferences;
    }

    public int getTotalEquations() {
        return totalEquations;
    }

    public int getUnresolvedEquations() {
        return unresolvedEquations;
    }

    public int getTotalCaptions() {
        return totalCaptions;
    }

    public int getUnresolvedCaptions() {
        return unresolvedCaptions;
    }

    public int getTotalCitations() {
        return totalCitations;
    }

    public int getUnresolvedCitations() {
        return unresolvedCitations;
    }

    public int getTotalReferences() {
        return totalReferences;
    }

    public double equationResolvedRate() {
        if (totalEquations == 0) return 1.0;
        return (double) (totalEquations - unresolvedEquations) / totalEquations;
    }

    public double captionResolvedRate() {
        if (totalCaptions == 0) return 1.0;
        return (double) (totalCaptions - unresolvedCaptions) / totalCaptions;
    }

    public double citationResolvedRate() {
        if (totalCitations == 0) return 1.0;
        return (double) (totalCitations - unresolvedCitations) / totalCitations;
    }
}

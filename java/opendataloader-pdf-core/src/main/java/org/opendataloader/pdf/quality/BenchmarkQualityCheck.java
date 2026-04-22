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
import java.util.stream.Collectors;

public final class BenchmarkQualityCheck {

    private final QualityGate gate;

    public BenchmarkQualityCheck(QualityGate gate) {
        this.gate = Objects.requireNonNull(gate, "gate must not be null");
    }

    public DocumentCheckResult check(String docId, ParityReport report) {
        Objects.requireNonNull(docId, "docId must not be null");
        Objects.requireNonNull(report, "report must not be null");
        List<String> reasons = gate.failureReasons(report);
        return new DocumentCheckResult(docId, reasons.isEmpty(), reasons, report);
    }

    public AggregateResult aggregate(List<DocumentCheckResult> results) {
        Objects.requireNonNull(results, "results must not be null");
        int passed = 0;
        List<String> failedDocIds = new ArrayList<>();
        for (DocumentCheckResult r : results) {
            if (r.isPassed()) {
                passed++;
            } else {
                failedDocIds.add(r.getDocId());
            }
        }
        int total = results.size();
        int failed = total - passed;
        return new AggregateResult(total, passed, failed, Collections.unmodifiableList(failedDocIds), failed == 0);
    }

    public String formatReport(AggregateResult aggregate, List<DocumentCheckResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Benchmark Quality Report ===\n");
        sb.append(String.format("Documents: %d, Passed: %d, Failed: %d%n",
            aggregate.getTotalDocuments(),
            aggregate.getPassedDocuments(),
            aggregate.getFailedDocuments()));
        if (aggregate.isOverallPassed()) {
            sb.append("All documents passed.");
        } else {
            sb.append("Failed documents:\n");
            for (DocumentCheckResult r : results) {
                if (!r.isPassed()) {
                    String reasons = r.getFailureReasons().stream().collect(Collectors.joining("; "));
                    sb.append(String.format("  - %s: %s%n", r.getDocId(), reasons));
                }
            }
        }
        return sb.toString();
    }

    public static final class DocumentCheckResult {

        private final String docId;
        private final boolean passed;
        private final List<String> failureReasons;
        private final ParityReport report;

        public DocumentCheckResult(String docId, boolean passed, List<String> failureReasons, ParityReport report) {
            this.docId = docId;
            this.passed = passed;
            this.failureReasons = Collections.unmodifiableList(new ArrayList<>(failureReasons));
            this.report = report;
        }

        public String getDocId() { return docId; }
        public boolean isPassed() { return passed; }
        public List<String> getFailureReasons() { return failureReasons; }
        public ParityReport getReport() { return report; }
    }

    public static final class AggregateResult {

        private final int totalDocuments;
        private final int passedDocuments;
        private final int failedDocuments;
        private final List<String> failedDocIds;
        private final boolean overallPassed;

        public AggregateResult(int totalDocuments, int passedDocuments, int failedDocuments,
                               List<String> failedDocIds, boolean overallPassed) {
            this.totalDocuments = totalDocuments;
            this.passedDocuments = passedDocuments;
            this.failedDocuments = failedDocuments;
            this.failedDocIds = Collections.unmodifiableList(new ArrayList<>(failedDocIds));
            this.overallPassed = overallPassed;
        }

        public int getTotalDocuments() { return totalDocuments; }
        public int getPassedDocuments() { return passedDocuments; }
        public int getFailedDocuments() { return failedDocuments; }
        public List<String> getFailedDocIds() { return failedDocIds; }
        public boolean isOverallPassed() { return overallPassed; }
    }
}

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
package org.opendataloader.pdf.enrichment;

public final class CaptionEnrichment {

    private final Long targetId;
    private final Double confidence;
    private final String unresolvedReason;

    public CaptionEnrichment(Long targetId, Double confidence, String unresolvedReason) {
        this.targetId = targetId;
        this.confidence = confidence;
        this.unresolvedReason = unresolvedReason;
    }

    public Long getTargetId() { return targetId; }
    public Double getConfidence() { return confidence; }
    public String getUnresolvedReason() { return unresolvedReason; }
}

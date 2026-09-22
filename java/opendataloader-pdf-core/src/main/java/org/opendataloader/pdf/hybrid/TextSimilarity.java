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
package org.opendataloader.pdf.hybrid;

/**
 * Computes text similarity for stream vs OCR comparison.
 * Used by enrichment to decide whether stream text is corrupted.
 */
public final class TextSimilarity {

    /** Default similarity threshold. Below this, stream text is considered corrupted. */
    public static final double DEFAULT_THRESHOLD = 0.5;

    private TextSimilarity() {}

    /**
     * Computes normalized similarity between two strings (0.0 = completely different, 1.0 = identical).
     * Uses Levenshtein distance normalized by the longer string's length.
     */
    public static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        int distance = levenshteinDistance(a, b);
        int maxLen = Math.max(a.length(), b.length());
        return 1.0 - ((double) distance / maxLen);
    }

    /**
     * Returns true if stream text should be trusted over OCR text.
     */
    public static boolean trustStream(String streamText, String ocrText, double threshold) {
        if (streamText == null || streamText.isEmpty()) return false;
        if (ocrText == null || ocrText.isEmpty()) return true;
        return similarity(streamText, ocrText) >= threshold;
    }

    private static int levenshteinDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] temp = prev; prev = curr; curr = temp;
        }
        return prev[b.length()];
    }
}

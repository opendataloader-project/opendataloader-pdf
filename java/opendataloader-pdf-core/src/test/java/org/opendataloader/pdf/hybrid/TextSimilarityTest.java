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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextSimilarityTest {

    @Test
    void identical_strings_return_1() {
        assertEquals(1.0, TextSimilarity.similarity("hello", "hello"));
    }

    @Test
    void completely_different_return_low() {
        assertTrue(TextSimilarity.similarity("abc", "xyz") < 0.5);
    }

    @Test
    void corrupted_unicode_detected() {
        assertFalse(TextSimilarity.trustStream(",QWURGXFWLRQ", "Introduction", 0.5));
    }

    @Test
    void similar_strings_trust_stream() {
        assertTrue(TextSimilarity.trustStream("Introduction to Biology", "Introduction to Biology", 0.5));
    }

    @Test
    void minor_ocr_error_still_trusts_stream() {
        assertTrue(TextSimilarity.trustStream("Introduction", "Introductlon", 0.5));
    }

    @Test
    void null_stream_returns_false() {
        assertFalse(TextSimilarity.trustStream(null, "text", 0.5));
    }

    @Test
    void null_ocr_returns_true() {
        assertTrue(TextSimilarity.trustStream("text", null, 0.5));
    }

    @Test
    void empty_stream_returns_false() {
        assertFalse(TextSimilarity.trustStream("", "text", 0.5));
    }

    @Test
    void both_null_returns_zero_similarity() {
        assertEquals(0.0, TextSimilarity.similarity(null, null));
    }

    @Test
    void both_empty_strings_are_identical() {
        assertEquals(1.0, TextSimilarity.similarity("", ""));
    }

    @Test
    void one_empty_one_nonempty_returns_zero() {
        assertEquals(0.0, TextSimilarity.similarity("", "hello"));
        assertEquals(0.0, TextSimilarity.similarity("hello", ""));
    }

    @Test
    void default_threshold_is_0_5() {
        assertEquals(0.5, TextSimilarity.DEFAULT_THRESHOLD);
    }
}

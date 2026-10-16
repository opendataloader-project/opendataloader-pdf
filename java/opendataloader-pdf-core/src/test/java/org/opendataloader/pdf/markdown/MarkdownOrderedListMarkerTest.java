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
package org.opendataloader.pdf.markdown;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #797: a list item whose label is a Markdown ordered list marker is written without
 * the {@code -} marker.
 */
class MarkdownOrderedListMarkerTest {

    @Test
    void decimalLabelsAreOrderedListMarkers() {
        assertTrue(MarkdownGenerator.startsWithOrderedListMarker("1. First step", 3));
        assertTrue(MarkdownGenerator.startsWithOrderedListMarker("1. First step", 2));
        assertTrue(MarkdownGenerator.startsWithOrderedListMarker("12) Twelfth step", 4));
        assertTrue(MarkdownGenerator.startsWithOrderedListMarker("3.", 2));
    }

    @Test
    void otherLabelsAreNotOrderedListMarkers() {
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker("• First item", 2));
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker("a. First item", 3));
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker("(1) First item", 4));
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker("1.First item", 2));
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker("1234567890. Too long", 12));
    }

    @Test
    void invalidLabelLengthsAreNotOrderedListMarkers() {
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker("1. First step", 0));
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker("1.", 3));
        assertFalse(MarkdownGenerator.startsWithOrderedListMarker(null, 2));
    }
}

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

import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import static org.junit.jupiter.api.Assertions.*;

class BulletedParagraphUtilsTest {

    @Test
    void testIsLabeledLineForNumberedLabel() {
        assertTrue(BulletedParagraphUtils.isLabeledLine(createLine("1. test")));
    }

    @Test
    void testIsLabeledLineForPlainText() {
        assertFalse(BulletedParagraphUtils.isLabeledLine(createLine("plain text")));
    }

    @Test
    void testGetLabelRegexForNumberedLabel() {
        assertNotNull(BulletedParagraphUtils.getLabelRegex(createLine("1. test")));
    }

    private static TextLine createLine(String value) {
        return new TextLine(new TextChunk(new BoundingBox(0), value, 10, 20.0));
    }
}

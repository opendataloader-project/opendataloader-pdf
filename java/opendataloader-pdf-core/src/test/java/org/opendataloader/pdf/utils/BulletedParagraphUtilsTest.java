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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

public class BulletedParagraphUtilsTest {

    private TextLine createLine(String text) {
        TextChunk chunk = new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            text, "Font1", 10, 400, 0, 12.0, new double[]{0.0}, null, 0);
        return new TextLine(chunk);
    }

    @Test
    public void testSentenceLikeLabelsAreNotIeeeSectionTitles() {
        TextLine sentence1 = createLine("I. This paper presents a novel approach");
        TextLine sentence2 = createLine("B. We evaluate our method on benchmark datasets");
        TextLine title1 = createLine("I. INTRODUCTION");
        TextLine title2 = createLine("B. Experimental Results");

        Assertions.assertFalse(BulletedParagraphUtils.isIeeeSectionTitle(sentence1),
            "I. This paper presents... should not be classified as IEEE section title");
        Assertions.assertFalse(BulletedParagraphUtils.isIeeeSectionTitle(sentence2),
            "B. We evaluate... should not be classified as IEEE section title");

        Assertions.assertTrue(BulletedParagraphUtils.isIeeeSectionTitle(title1),
            "I. INTRODUCTION should be classified as IEEE section title");
        Assertions.assertTrue(BulletedParagraphUtils.isIeeeSectionTitle(title2),
            "B. Experimental Results should be classified as IEEE section title");
    }

    @Test
    public void testShortAuthorListIsAuthorAffiliationLine() {
        TextLine authorList = createLine("A. Smith, B. Jones");
        TextLine subsection = createLine("A. System Architecture");

        Assertions.assertFalse(BulletedParagraphUtils.isIeeeSectionTitle(authorList),
            "A. Smith, B. Jones should not be classified as IEEE section title");
        Assertions.assertTrue(BulletedParagraphUtils.isIeeeSectionTitle(subsection),
            "A. System Architecture should be classified as IEEE section title");
    }
}

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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #548: a scanned or image-only PDF gives no text in local mode. ExtractedText tells whether the
 * extracted contents hold any text, so that DocumentProcessor can warn.
 */
class ExtractedTextTest {

    @BeforeAll
    static void initStaticContainers() {
        StaticContainers.updateContainers(null);
    }

    @Test
    void noPagesHaveNoText() {
        assertFalse(ExtractedText.isPresent(Collections.emptyList(), false));
        assertFalse(ExtractedText.isPresent(List.of(new ArrayList<>(), new ArrayList<>()), false));
    }

    @Test
    void imagesOnlyHaveNoText() {
        IObject image = new ImageChunk(new BoundingBox(0, 0, 0, 100, 100));
        assertFalse(ExtractedText.isPresent(List.of(List.of(image)), false));
    }

    @Test
    void whitespaceIsNotText() {
        assertFalse(ExtractedText.isPresent(List.of(List.of(paragraph(" \t "))), false));
    }

    /**
     * The output writers drop U+0000, so text made only of it gives no text in the output.
     */
    @Test
    void nulCharactersAreNotText() {
        assertFalse(ExtractedText.isPresent(List.of(List.of(paragraph("\u0000 \u0000"))), false));
        assertFalse(ExtractedText.isPresent(List.of(List.of(new TextChunk("\u0000"))), false));
        assertTrue(ExtractedText.isPresent(List.of(List.of(paragraph("\u0000a"))), false));
    }

    @Test
    void aParagraphOnAnyPageIsText() {
        IObject image = new ImageChunk(new BoundingBox(0, 0, 0, 100, 100));
        assertTrue(ExtractedText.isPresent(List.of(List.of(image), List.of(paragraph("Σύμβαση"))), false));
    }

    @Test
    void aTableCellIsText() {
        assertTrue(ExtractedText.isPresent(List.of(List.of(table("total"))), false));
        assertFalse(ExtractedText.isPresent(List.of(List.of(table(" "))), false));
    }

    @Test
    void aTextChunkIsText() {
        assertTrue(ExtractedText.isPresent(List.of(List.of(new TextChunk("text"))), false));
    }

    @Test
    void headerTextCountsOnlyWhenHeadersAreInTheOutput() {
        SemanticHeaderOrFooter header = new SemanticHeaderOrFooter(SemanticType.HEADER);
        header.addContents(Collections.singletonList(paragraph("Page 1")));
        List<List<IObject>> contents = List.of(List.of(header));

        assertFalse(ExtractedText.isPresent(contents, false));
        assertTrue(ExtractedText.isPresent(contents, true));
    }

    private static SemanticParagraph paragraph(String text) {
        TextColumn column = new TextColumn(new TextLine(new TextChunk(text)));
        return new SemanticParagraph(new BoundingBox(null, 0, 0, 100, 10), List.of(column));
    }

    private static TableBorder table(String... cells) {
        TableBorderRow row = new TableBorderRow(0, cells.length, null);
        for (int c = 0; c < cells.length; c++) {
            TableBorderCell cell = new TableBorderCell(0, c, 1, 1, null);
            cell.addContentObject(paragraph(cells[c]));
            row.getCells()[c] = cell;
        }
        return new TableBorder(null, new TableBorderRow[]{row}, 1, cells.length);
    }
}

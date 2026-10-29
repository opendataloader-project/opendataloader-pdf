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
package org.opendataloader.pdf.text;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.StringWriter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #767: a ToUnicode entry of {@code <0000>} gives a null character (U+0000) in the text of a chunk.
 * The Markdown, HTML, and JSON writers leave it out. The text writer wrote a space for it, so the word
 * before a line break got two spaces ("the IT  department").
 */
class TextGeneratorTest {

    @BeforeAll
    static void initStaticContainers() {
        StaticContainers.updateContainers(null);
    }

    @Test
    void nullCharacterBeforeASpaceLeavesOneSpace() {
        assertEquals("the IT department", write(paragraph("the IT\u0000 department")).trim(),
            "the text output has two spaces where the null character was");
    }

    @Test
    void nullCharacterInAWordIsLeftOutAsInTheOtherWriters() {
        assertEquals("AIagents", write(paragraph("AI\u0000agents")).trim(),
            "the text output has a space where the null character was; Markdown, HTML, and JSON leave it out");
    }

    @Test
    void nullCharacterInATableCellIsLeftOut() {
        assertEquals("email\tGDPR", write(table("e\u0000mail", "GDPR")).trim(),
            "the table cell has a space where the null character was");
    }

    @Test
    void textWithoutANullCharacterDoesNotChange() {
        assertEquals("the IT department", write(paragraph("the IT department")).trim());
        assertEquals("e-mail\tGDPR", write(table("e-mail", "GDPR")).trim());
    }

    private static String write(IObject content) {
        StringWriter out = new StringWriter();
        new TextGenerator(out, new Config()).writeToText(List.of(List.of(content)));
        return out.toString();
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

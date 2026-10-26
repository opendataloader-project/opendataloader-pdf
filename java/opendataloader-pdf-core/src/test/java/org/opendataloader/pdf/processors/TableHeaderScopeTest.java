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
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The Scope a header cell is written with.
 *
 * <p>A financial table carries two or three heading rows over its amounts,
 * and names each row in its first cell. Scope placed by row index alone gave
 * the second heading row Row on its first cell and Both on the rest.
 */
class TableHeaderScopeTest {

    @BeforeEach
    void containers() {
        StaticContainers.updateContainers(null);
    }

    /** A grid whose cells are headers where {@code header[r][c]} is true. */
    private static TableBorder table(boolean[][] header) {
        int rows = header.length;
        int cols = header[0].length;
        TableBorderRow[] rowArray = new TableBorderRow[rows];
        long id = 1;
        for (int r = 0; r < rows; r++) {
            rowArray[r] = new TableBorderRow(r, cols, id++);
            for (int c = 0; c < cols; c++) {
                TableBorderCell cell = new TableBorderCell(r, c, 1, 1, id++);
                cell.setBoundingBox(new BoundingBox(0, c * 50, 500 - r * 20, c * 50 + 50, 520 - r * 20));
                if (header[r][c]) {
                    cell.setSemanticType(SemanticType.TABLE_HEADER);
                }
                rowArray[r].getCells()[c] = cell;
            }
        }
        return new TableBorder(new BoundingBox(0, 0, 400, cols * 50, 520), rowArray, rows, cols);
    }

    @Test
    void everyHeadingRowScopesItsColumns() {
        TableBorder t = table(new boolean[][]{
                {true, true, true},
                {true, true, true},
                {true, false, false}});

        assertEquals("Both", AutoTaggingProcessor.headerScope(t, 0, 0));
        assertEquals("Column", AutoTaggingProcessor.headerScope(t, 0, 1));
        assertEquals("Column", AutoTaggingProcessor.headerScope(t, 1, 0), "the second heading row");
        assertEquals("Column", AutoTaggingProcessor.headerScope(t, 1, 2));
        assertEquals("Row", AutoTaggingProcessor.headerScope(t, 2, 0), "a row header among data");
    }

    /** A table with no heading row: the first cell names the first row, and no column. */
    @Test
    void theFirstCellOfAFirstRowOfDataScopesItsRow() {
        TableBorder t = table(new boolean[][]{
                {true, false},
                {true, false}});

        assertEquals("Row", AutoTaggingProcessor.headerScope(t, 0, 0));
        assertEquals("Row", AutoTaggingProcessor.headerScope(t, 1, 0));
    }

    @Test
    void aHeaderAmongDataOutsideTheFirstColumnIsBoth() {
        TableBorder t = table(new boolean[][]{
                {true, true},
                {false, true}});

        assertEquals("Both", AutoTaggingProcessor.headerScope(t, 1, 1));
    }
}

/*
 * Copyright 2025 Hancom Inc.
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

import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MarkdownHTMLGenerator extends MarkdownGenerator {

    protected MarkdownHTMLGenerator(File inputPdf, Config config) throws IOException {
        super(inputPdf, config);
    }

    @Override
    protected void writeTable(TableBorder table) throws IOException {
        enterTable();
        markdownWriter.write(MarkdownSyntax.HTML_TABLE_TAG);
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            markdownWriter.write(MarkdownSyntax.INDENT);
            markdownWriter.write(MarkdownSyntax.HTML_TABLE_ROW_TAG);
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    boolean isHeader = rowNumber == 0;
                    writeCellTagBegin(cell, isHeader);

                    List<IObject> cellContents = cell.getContents();
                    writeContents(cellContents, true);

                    writeCellTagEnd(isHeader);
                    markdownWriter.write(MarkdownSyntax.LINE_BREAK);
                }
            }

            markdownWriter.write(MarkdownSyntax.INDENT);
            markdownWriter.write(MarkdownSyntax.HTML_TABLE_ROW_CLOSE_TAG);
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }

        markdownWriter.write(MarkdownSyntax.HTML_TABLE_CLOSE_TAG);
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        leaveTable();
    }

    private void writeCellTagBegin(TableBorderCell cell, boolean isHeader) throws IOException {
        markdownWriter.write(MarkdownSyntax.INDENT);
        markdownWriter.write(MarkdownSyntax.INDENT);
        String tag = isHeader ? "<th" : "<td";
        StringBuilder cellTag = new StringBuilder(tag);
        int colSpan = cell.getColSpan();
        if (colSpan != 1) {
            cellTag.append(" colspan=\"").append(colSpan).append("\"");
        }

        int rowSpan = cell.getRowSpan();
        if (rowSpan != 1) {
            cellTag.append(" rowspan=\"").append(rowSpan).append("\"");
        }
        cellTag.append(">");
        markdownWriter.write(getCorrectMarkdownString(cellTag.toString()));
    }

    private void writeCellTagEnd(boolean isHeader) throws IOException {
        if (isHeader) {
            markdownWriter.write(MarkdownSyntax.HTML_TABLE_HEADER_CLOSE_TAG);
        } else {
            markdownWriter.write(MarkdownSyntax.HTML_TABLE_CELL_CLOSE_TAG);
        }
    }
}

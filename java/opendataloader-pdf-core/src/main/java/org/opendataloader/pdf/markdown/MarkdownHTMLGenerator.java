/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
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

    protected MarkdownHTMLGenerator(File inputPdf, String fileName, Config config) throws IOException {
        super(inputPdf, fileName, config);
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
                    writeCellTag(cell, isHeader);
                    List<IObject> contents = cell.getContents();
                    if (!contents.isEmpty()) {
                        for (IObject contentItem : contents) {
                            this.write(contentItem);
                        }
                    }
                    if (isHeader) {
                        markdownWriter.write(MarkdownSyntax.HTML_TABLE_HEADER_CLOSE_TAG);
                    } else {
                        markdownWriter.write(MarkdownSyntax.HTML_TABLE_CELL_CLOSE_TAG);
                    }
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

    private void writeCellTag(TableBorderCell cell, boolean isHeader) throws IOException {
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
}

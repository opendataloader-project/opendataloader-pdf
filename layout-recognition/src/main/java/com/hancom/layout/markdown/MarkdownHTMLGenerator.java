/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.layout.markdown;

import com.hancom.layout.utils.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MarkdownHTMLGenerator extends MarkdownGenerator {

    protected MarkdownHTMLGenerator(File inputPdf, String fileName, Config config) throws IOException {
        super(inputPdf, fileName, config);
    }

    @Override
    protected void writeParagraph(SemanticParagraph paragraph) throws IOException {
        String paragraphValue =  paragraph.getValue();
        double paragraphIndent = paragraph.getColumns().get(0).getBlocks().get(0).getFirstLineIndent();
        if (paragraphIndent > 0) {
            markdownWriter.write(MarkdownSyntax.HTML_INDENT);
        }

        if (isInsideTable() && StaticContainers.isKeepLineBreaks()) {
            paragraphValue = paragraphValue.replace(MarkdownSyntax.LINE_BREAK , MarkdownSyntax.HTML_LINE_BREAK_TAG);
        }

        markdownWriter.write(paragraphValue);
    }

    @Override
    protected void writeTable(TableBorder table) throws IOException {
        enterTable();
        markdownWriter.write(MarkdownSyntax.HTML_TABLE_TAG);
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            markdownWriter.write(MarkdownSyntax.HTML_TABLE_ROW_TAG);
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    writeCellTag(cell);
                    List<IObject> contents = cell.getContents();
                    if (!contents.isEmpty()) {
                        for (IObject contentItem : contents) {
                            this.write(contentItem);
                        }
                    }

                    markdownWriter.write(MarkdownSyntax.HTML_TABLE_CELL_CLOSE_TAG);
                    markdownWriter.write(MarkdownSyntax.LINE_BREAK);
                }
            }

            markdownWriter.write(MarkdownSyntax.HTML_TABLE_ROW_CLOSE_TAG);
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }

        markdownWriter.write(MarkdownSyntax.HTML_TABLE_CLOSE_TAG);
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        leaveTable();
    }

    @Override
    protected void writeList(PDFList list) throws IOException {
        markdownWriter.write(MarkdownSyntax.HTML_UNORDERED_LIST_TAG);
        for (ListItem item : list.getListItems()) {
            markdownWriter.write(MarkdownSyntax.HTML_LIST_ITEM_TAG);
            markdownWriter.write(item.toString());
            for (IObject object : item.getContents()) {
                write(object);
            }
            markdownWriter.write(MarkdownSyntax.HTML_LIST_ITEM_CLOSE_TAG);
        }

        markdownWriter.write(MarkdownSyntax.HTML_UNORDERED_LIST_CLOSE_TAG);
    }

    private void writeCellTag(TableBorderCell cell) throws IOException {
        StringBuilder cellTag = new StringBuilder("<td");
        int colSpan = cell.getColSpan();
        if (colSpan != 1) {
            cellTag.append(" colspan=\"").append(colSpan).append("\"");
        }

        int rowSpan = cell.getRowSpan();
        if (rowSpan != 1) {
            cellTag.append(" rowspan=\"").append(rowSpan).append("\"");
        }
        cellTag.append(">");
        markdownWriter.write(cellTag.toString());
    }
}

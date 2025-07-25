package com.hancom.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.List;

public class SpecialTableProcessor {

    private static final String KOREAN_TABLE_REGEX = "\\(?(수신|경유|제목)\\)?.*";

    public static List<IObject> detectSpecialTables(List<IObject> contents) {
        detectSpecialKoreanTables(contents);
        return DocumentProcessor.removeNullObjectsFromList(contents);
    }

    private static void detectSpecialKoreanTables(List<IObject> contents) {
        List<TextLine> lines = new ArrayList<>();
        Integer index = null;
        for (int currentIndex = 0; currentIndex < contents.size(); currentIndex++) {
            IObject content = contents.get(currentIndex);
            if (content instanceof TextLine) {
                TextLine line = ((TextLine) content);
                if (line.getValue().matches(KOREAN_TABLE_REGEX)) {
                    lines.add(line);
                    contents.set(currentIndex, null);
                    if (index == null) {
                        index = currentIndex;
                    }
                } else if (!lines.isEmpty()) {
                    contents.set(index, detectSpecialKoreanTable(lines));
                    lines.clear();
                }
            } else if (!lines.isEmpty()) {
                contents.set(index, detectSpecialKoreanTable(lines));
                lines.clear();
            }
        }
        if (!lines.isEmpty()) {
            contents.set(index, detectSpecialKoreanTable(lines));
        }
    }

    private static TableBorder detectSpecialKoreanTable(List<TextLine> lines) {
        TableBorder table = new TableBorder(lines.size(), 2);
        for (int rowNumber = 0; rowNumber < lines.size(); rowNumber++) {
            TextLine line = lines.get(rowNumber);
            BoundingBox box = line.getBoundingBox();
            int index = line.getValue().indexOf(":");
            boolean isOneCellRow = index == -1;
            TableBorderRow tableBorderRow = new TableBorderRow(rowNumber, 2, null);
            table.getRows()[rowNumber] = tableBorderRow;
            if (isOneCellRow) {
                TableBorderCell tableBorderCell = new TableBorderCell(rowNumber, 0, 1, 2, null);
                tableBorderCell.addContentObject(line);
                tableBorderCell.setBoundingBox(box);
                tableBorderRow.getCells()[0] = tableBorderCell;
                tableBorderRow.getCells()[1] = tableBorderCell;
            } else {
                TableBorderCell cell1 = new TableBorderCell(rowNumber, 0, 1, 1, null);
                TextLine line1 = new TextLine(line, 0, index - 1);
                cell1.addContentObject(line1);
                cell1.setBoundingBox(line1.getBoundingBox());
                tableBorderRow.getCells()[0] = cell1;
                TableBorderCell cell2 = new TableBorderCell(rowNumber, 1, 1, 1, null);
                TextLine line2 = new TextLine(line, index + 1, line.getValue().length());
                cell2.addContentObject(line2);
                cell2.setBoundingBox(line2.getBoundingBox());
                tableBorderRow.getCells()[1] = cell2;
            }
            tableBorderRow.setBoundingBox(box);
            table.getBoundingBox().union(box);
        }
        TableBorderProcessor.processTableBorder(table, table.getPageNumber());
        return table;
    }
}

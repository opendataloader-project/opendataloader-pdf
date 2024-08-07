package com.duallab.layout.processors;

import com.duallab.layout.Info;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.List;

public class TableBorderProcessor {

    public static void/*List<TableBorderCell>*/ processTableBorders(List<IObject> contents, int pageNumber) {
        for (IObject content : contents) {
            addContentToTableBorder(content);
        }
        TableBordersCollection tableBordersCollection = StaticContainers.getTableBordersCollection();
        //List<TableBorderCell> tableBorderCells = new LinkedList<>();
        for (TableBorder border : tableBordersCollection.getTableBorders(pageNumber)) {
            DocumentProcessor.replaceContentsToResult(contents, border);
            String value = String.format("Table: %s rows, %s columns", border.getNumberOfRows(), border.getNumberOfColumns());
            StaticLayoutContainers.getMap().put(border, new Info(value, PDFWriter.getColor(SemanticType.TABLE)));
//                for (int rowNumber = 0; rowNumber < border.getNumberOfRows(); rowNumber++) {
//                    TableBorderRow row = border.getRow(rowNumber);
//                    for (int colNumber = 0; colNumber < border.getNumberOfColumns(); colNumber++) {
//                        TableBorderCell cell = row.getCell(colNumber);
//                        if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
//                            tableBorderCells.add(cell);
//                        }
//                    }
//                }
        }
        //return tableBorderCells;
    }

    private static void addContentToTableBorder(IObject content) {
        TableBorder tableBorder = StaticContainers.getTableBordersCollection().getTableBorder(content.getBoundingBox());
        if (tableBorder != null) {
            TableBorderCell tableBorderCell = tableBorder.getTableBorderCell(content.getBoundingBox());
            if (tableBorderCell != null) {
                if (content instanceof TextChunk) {
                    SemanticTextNode textNode = new SemanticTextNode((TextChunk)content, SemanticType.SPAN);
                    tableBorderCell.addContent(new TableToken((TextChunk)content, textNode));
                }
            }
        }
    }
}

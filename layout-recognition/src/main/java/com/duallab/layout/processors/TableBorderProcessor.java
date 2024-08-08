package com.duallab.layout.processors;

import com.duallab.layout.ContentInfo;
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

    public static void processTableBorders(List<IObject> contents, int pageNumber) {
        for (IObject content : contents) {
            addContentToTableBorder(content);
        }
        TableBordersCollection tableBordersCollection = StaticContainers.getTableBordersCollection();
        for (TableBorder border : tableBordersCollection.getTableBorders(pageNumber)) {
            DocumentProcessor.replaceContentsToResult(contents, border);
            String value = String.format("Table: %s rows, %s columns", border.getNumberOfRows(), border.getNumberOfColumns());
            StaticLayoutContainers.getContentInfoMap().put(border, new ContentInfo(value, PDFWriter.getColor(SemanticType.TABLE)));
        }
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

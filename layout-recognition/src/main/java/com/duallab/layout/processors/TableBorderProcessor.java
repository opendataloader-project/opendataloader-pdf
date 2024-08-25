package com.duallab.layout.processors;

import com.duallab.layout.ContentInfo;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TableBorderProcessor {

    public static List<IObject> processTableBorders(List<IObject> contents, int pageNumber) {
        List<IObject> newContents = new ArrayList<>();
        Set<TableBorder> processedTableBorders = new HashSet<>(); 
        for (IObject content : contents) {
            TableBorder tableBorder = addContentToTableBorder(content);
            if (tableBorder != null) {
                if (!processedTableBorders.contains(tableBorder)) {
                    processedTableBorders.add(tableBorder);
                    newContents.add(tableBorder);
                }
            } else {
                newContents.add(content);
            }
        }
        for (TableBorder border : processedTableBorders) {
            StaticContainers.getTableBordersCollection().removeTableBorder(border, pageNumber);
            processTableBorder(border, pageNumber);
            String value = String.format("Table: %s rows, %s columns", border.getNumberOfRows(), border.getNumberOfColumns());//todo improve
            StaticLayoutContainers.getContentInfoMap().put(border, new ContentInfo(value, PDFWriter.getColor(SemanticType.TABLE)));
        }
        return newContents;
    }

    private static TableBorder addContentToTableBorder(IObject content) {
        TableBorder tableBorder = StaticContainers.getTableBordersCollection().getTableBorder(content.getBoundingBox());
        if (tableBorder != null) {
            if ((content instanceof LineArtChunk) || (content instanceof LineChunk)) {
                return tableBorder;
            }
            TableBorderCell tableBorderCell = tableBorder.getTableBorderCell(content);
            if (tableBorderCell != null) {
                if (content instanceof TextChunk) {
                    tableBorderCell.addContentObject(content);
                    return tableBorder;
                }
            }
        }
        return null;
    }

    public static void processTableBorder(TableBorder tableBorder, int pageNumber) {
        for (TableBorderRow tableBorderRow : tableBorder.getRows()) {
            for (TableBorderCell tableBorderCell : tableBorderRow.getCells()) {
                tableBorderCell.setContents(processTableCellContent(tableBorderCell.getContents(), pageNumber));
            }
        }
    }

    public static List<IObject> processTableCellContent(List<IObject> contents, int pageNumber) {
        List<IObject> newContents = TableBorderProcessor.processTableBorders(contents, pageNumber);
        newContents = TextLineProcessor.processTextLines(newContents);
        newContents = ListProcessor.processLists(newContents);
        newContents = ParagraphProcessor.processParagraphs(newContents);
        HeadingProcessor.processHeadings(newContents);
        DocumentProcessor.setIDs(newContents);
        CaptionProcessor.processCaptions(newContents);
        ImageProcessor.processImages(newContents);
        return newContents;
    }
}

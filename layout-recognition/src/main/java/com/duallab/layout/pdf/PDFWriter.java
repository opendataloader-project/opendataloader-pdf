package com.duallab.layout.pdf;

import com.duallab.layout.ContentInfo;
import com.duallab.layout.containers.StaticLayoutContainers;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class PDFWriter {
    public static void updatePDF(String pdfName, String password, String outputName, List<List<IObject>> contents, 
                                 List<TextChunk> hiddenTextChunks) throws IOException {
        PDDocument document = PDDocument.load(new File(pdfName), password);
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            for (IObject content : contents.get(pageNumber)) {
                ContentInfo contentInfo = StaticLayoutContainers.getContentInfoMap().get(content);
                if (contentInfo != null) {
                    PDAnnotation annot = draw(document, content.getBoundingBox(), contentInfo.color, contentInfo.contents, 
                            content.getRecognizedStructureId(), null);
                    if (content instanceof TableBorder) {
                        drawTableCells(document, (TableBorder) content, annot);
                    } else if (content instanceof PDFList) {
                        drawListItems(document, (PDFList) content, annot);
                    }
                }
            }
        }
        for (TextChunk textChunk : hiddenTextChunks) {
            draw(document, textChunk.getBoundingBox(), getColor(SemanticType.FIGURE), 
                    String.format("Hidden text, value = \"%s\"", textChunk.getValue()), null, null);
        }
        System.out.println("Created " + outputName);
        document.setAllSecurityToBeRemoved(true);
        document.save(outputName);
        document.close();
    }
    
    private static void drawTableCells(PDDocument document, TableBorder border, PDAnnotation annot) throws IOException {
        for (int rowNumber = 0; rowNumber < border.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = border.getRow(rowNumber);
            for (int colNumber = 0; colNumber < border.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    StringBuilder contentValue = new StringBuilder();
                    for (IObject object : cell.getContents()) {
                        if (object instanceof SemanticTextNode) {
                            contentValue.append(((SemanticTextNode)object).getValue());
                        }
                    }
                    String cellValue = String.format("Table cell: row number %s, column number %s, row span %s, column span %s, text content \"%s\"",
                            cell.getRowNumber() + 1, cell.getColNumber() + 1, cell.getRowSpan(), cell.getColSpan(), contentValue);
                    draw(document, cell.getBoundingBox(), getColor(SemanticType.TABLE), cellValue, null, annot);
                }
            }
        }
    }
    
    private static void drawListItems(PDDocument document, PDFList list, PDAnnotation annot) throws IOException {
        for (ListItem listItem : list.getListItems()) {
            String contentValue = String.format("List item: text content \"%s\"", listItem.toString());
            draw(document, listItem.getBoundingBox(), getColor(SemanticType.LIST), contentValue, null, annot);
        }
    }

    public static PDAnnotation draw(PDDocument document, BoundingBox boundingBox, float[] colorArray,
                                    String contents, Long id, PDAnnotation linkedAnnot) throws IOException {
        PDPage page = document.getPage(boundingBox.getPageNumber());
        PDAnnotationSquareCircle square = new PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE);
        square.setRectangle(new PDRectangle((float)boundingBox.getLeftX(), (float)boundingBox.getBottomY(),
                (float)boundingBox.getWidth(), (float)boundingBox.getHeight()));
        square.setConstantOpacity(0.4f);
        PDColor color = new PDColor(colorArray, PDDeviceRGB.INSTANCE);
        square.setColor(color);
        square.setInteriorColor(color);
        square.setContents((id != null ? "id = " + id + ", " : "") + contents);
        if (linkedAnnot != null) {
            square.setInReplyTo(linkedAnnot);
        }
        page.getAnnotations().add(square);
        return square;
    }

    public static float[] getColor(SemanticType semanticType) {
        if (semanticType == SemanticType.HEADING) {
            return new float[]{0, 0, 1};
        }
        if (semanticType == SemanticType.LIST) {
            return new float[]{0, 1, 0};
        }
        if (semanticType == SemanticType.PARAGRAPH) {
            return new float[]{0, 1, 1};
        }
        if (semanticType == SemanticType.FIGURE) {
            return new float[]{1, 0, 0};
        }
        if (semanticType == SemanticType.TABLE) {
            return new float[]{1, 0, 1};
        }
        if (semanticType == SemanticType.CAPTION) {
            return new float[]{1, 1, 0};
        }
        if (semanticType == SemanticType.PART) {
            return new float[]{0.9f, 0.9f, 0.9f};
        }
        return null;
    }
}

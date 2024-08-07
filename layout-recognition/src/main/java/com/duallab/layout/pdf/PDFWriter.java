package com.duallab.layout.pdf;

import com.duallab.layout.Info;
import com.duallab.layout.containers.StaticLayoutContainers;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle;
import org.verapdf.wcag.algorithms.entities.IObject;
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
    public static void updatePDF(String pdfName, String password, String outputName, List<List<IObject>> contents, List<TextChunk> hiddenTextChunks/*,
            List<TableBorderCell> tableBorderCells*/) throws IOException {
        org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.pdmodel.PDDocument.load(new File(pdfName), password);
//        int id = 1;
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            for (IObject content : contents.get(pageNumber)) {
//        for (int i = contents.size() - 1; i >= 0; i--) {
//            IObject content = contents.get(i);
//            if (!(content instanceof Table)) {
//                continue;
//            }
                Info info = StaticLayoutContainers.getMap().get(content);
                if (info != null /*&& content instanceof INode && (SemanticType.LIST == ((INode) content).getSemanticType()*/ /*&& (info.contents.contains(SemanticType.HEADER.getValue()) || info.contents.contains(SemanticType.FOOTER.getValue())))*/) {
                    PDAnnotation annot = draw(document, content.getBoundingBox(), info.color, info.contents, content.getRecognizedStructureId(), null);
                    if (content instanceof TableBorder) {
                        TableBorder border = (TableBorder) content;
                        for (int rowNumber = 0; rowNumber < border.getNumberOfRows(); rowNumber++) {
                            TableBorderRow row = border.getRow(rowNumber);
                            for (int colNumber = 0; colNumber < border.getNumberOfColumns(); colNumber++) {
                                TableBorderCell cell = row.getCell(colNumber);
                                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                                    StringBuilder contentValue = new StringBuilder();
                                    for (TableToken token : cell.getContent()) {
                                        contentValue.append(token.getValue());
                                    }
                                    String cellValue = String.format("Table cell: row number %s, column number %s, row span %s, column span %s, text content \"%s\"",
                                            cell.getRowNumber() + 1, cell.getColNumber() + 1, cell.getRowSpan(), cell.getColSpan(), contentValue);
                                    draw(document, cell.getBoundingBox(), getColor(SemanticType.TABLE), cellValue, null, annot);
                                }
                            }
                        }
                    } else if (content instanceof PDFList) {
                        PDFList list = (PDFList) content;
                        for (ListItem listItem : list.getListItems()) {
                            String contentValue = String.format("List item: text content \"%s\"", listItem.toString());
                            draw(document, listItem.getBoundingBox(), getColor(SemanticType.LIST), contentValue, null, annot);
                        }
                    }
                }
            }
        }
        for (TextChunk textChunk : hiddenTextChunks) {
            draw(document, textChunk.getBoundingBox(), getColor(SemanticType.FIGURE), String.format("Hidden text, value = \"%s\"", textChunk.getValue()), null, null);
        }
//        for (TableBorderCell cell : tableBorderCells) {
//            StringBuilder contentValue = new StringBuilder();
//            for (TableToken token : cell.getContent()) {
//                contentValue.append(token.getValue());
//            }
//            String cellValue = String.format("Table cell: row number %s, column number %s, row span %s, column span %s, text content \"%s\"",
//                    cell.getRowNumber() + 1, cell.getColNumber() + 1, cell.getRowSpan(), cell.getColSpan(), contentValue);
//            draw(document, cell.getBoundingBox(), getColor(SemanticType.TABLE), cellValue, null);
//        }
        System.out.println("Created " + outputName);
        document.setAllSecurityToBeRemoved(true);
        document.save(outputName);
        document.close();
    }

    public static PDAnnotation draw(org.apache.pdfbox.pdmodel.PDDocument document, BoundingBox boundingBox, float[] colorArray,
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

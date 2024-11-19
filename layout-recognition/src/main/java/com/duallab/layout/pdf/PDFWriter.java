/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.pdf;

import com.duallab.layout.processors.DocumentProcessor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PDFWriter {
    
    private static final List<List<PDAnnotation>> annotations = new ArrayList<>();
    
    public static void updatePDF(File inputPDF, String password, String outputFolder, List<List<IObject>> contents, 
                                 List<TextChunk> hiddenTextChunks) throws IOException {
        try (PDDocument document = PDDocument.load(inputPDF, password)) {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                annotations.add(new ArrayList<>());
                for (IObject content : contents.get(pageNumber)) {
                    drawContent(content);
                }
            }
            for (TextChunk textChunk : hiddenTextChunks) {
                draw(textChunk.getBoundingBox(), getColor(SemanticType.FIGURE),
                        String.format("Hidden text, value = \"%s\"", textChunk.getValue()), null, null, null);
            }
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                document.getPage(pageNumber).getAnnotations().addAll(annotations.get(pageNumber));
            }
            annotations.clear();
            document.setAllSecurityToBeRemoved(true);

            String outputFileName = outputFolder + File.separator +
                    inputPDF.getName().substring(0, inputPDF.getName().length() - 4) + "_annotated.pdf";
            document.save(outputFileName);
            System.out.println("Created " + outputFileName);
        }
    }
    
    private static void drawContent(IObject content) throws IOException {
        drawContent(content, null);
    }

    private static void drawContent(IObject content, PDAnnotation linkedAnnot) throws IOException {
        if (content instanceof LineChunk) {
            return;
        }
        PDAnnotation annot = draw(content.getBoundingBox(), getColor(content), getContents(content),
                content.getRecognizedStructureId(), linkedAnnot, content.getLevel());
        if (content instanceof TableBorder) {
            drawTableCells((TableBorder) content, annot);
        } else if (content instanceof PDFList) {
            drawListItems((PDFList) content, annot);
        } else if (content instanceof SemanticHeaderOrFooter) {
            for (IObject contentItem : ((SemanticHeaderOrFooter)content).getContents()) {
                drawContent(contentItem, annot);
            }
        }
    }
    
    private static void drawTableCells(TableBorder border, PDAnnotation annot) throws IOException {
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
                    draw(cell.getBoundingBox(), getColor(SemanticType.TABLE), cellValue, null, annot, cell.getLevel());
                    for (IObject content : cell.getContents()) {
                        drawContent(content);
                    }
                }
            }
        }
    }
    
    private static void drawListItems(PDFList list, PDAnnotation annot) throws IOException {
        for (ListItem listItem : list.getListItems()) {
            String contentValue = String.format("List item: text content \"%s\"", listItem.toString());
            draw(listItem.getBoundingBox(), getColor(SemanticType.LIST), contentValue, null, annot, listItem.getLevel());
            for (IObject content : listItem.getContents()) {
                drawContent(content);
            }
        }
    }

    public static PDAnnotation draw(BoundingBox boundingBox, float[] colorArray,
                                    String contents, Long id, PDAnnotation linkedAnnot, Integer level) {
        PDAnnotationSquareCircle square = new PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE);
        square.setRectangle(new PDRectangle((float)boundingBox.getLeftX(), (float)boundingBox.getBottomY(),
                (float)boundingBox.getWidth(), (float)boundingBox.getHeight()));
        square.setConstantOpacity(0.4f);
        PDColor color = new PDColor(colorArray, PDDeviceRGB.INSTANCE);
        square.setColor(color);
        square.setInteriorColor(color);
        square.setContents((id != null ? "id = " + id + ", " : "") + (level != null ? "level = " + level + ", " : "") + contents);
        if (linkedAnnot != null) {
            square.setInReplyTo(linkedAnnot);
        }
        annotations.get(boundingBox.getPageNumber()).add(square);
        return square;
    }
    
    public static String getContents(IObject content) {
        if (content instanceof TableBorder) {
            TableBorder border = (TableBorder) content;
            return String.format("Table: %s rows, %s columns, previous table id %s, next table id %s", 
                    border.getNumberOfRows(), border.getNumberOfColumns(), border.getPreviousTableId(), border.getNextTableId());
        }
        if (content instanceof PDFList) {
            PDFList list = (PDFList) content;
            return String.format("List: number of items %s, previous list id %s, next list id %s", 
                    list.getNumberOfListItems(), list.getPreviousListId(), list.getNextListId());
        }
        if (content instanceof INode) {
            INode node = (INode) content;
            if (node.getSemanticType() == SemanticType.HEADER || node.getSemanticType() == SemanticType.FOOTER) {
                return node.getSemanticType().getValue();
            }
            if (node.getSemanticType() == SemanticType.CAPTION) {
                SemanticCaption caption = (SemanticCaption) node;
                return DocumentProcessor.getContentsValueForTextNode(caption) + ", connected with object with id = " +
                        caption.getLinkedContentId();
            }
            if (node.getSemanticType() == SemanticType.HEADING) {
                SemanticHeading heading = (SemanticHeading) node;
                return DocumentProcessor.getContentsValueForTextNode(heading) +
                        ", heading level " + heading.getHeadingLevel();
            }
            if (node instanceof SemanticTextNode) {
                return DocumentProcessor.getContentsValueForTextNode((SemanticTextNode) node);
            }
        }
        if (content instanceof ImageChunk) {
            return String.format("Image: height %.2f, width %.2f", content.getHeight(), content.getWidth());
        }
        if (content instanceof LineArtChunk) {
            return String.format("Line Art: height %.2f, width %.2f", content.getHeight(), content.getWidth());
        }
        if (content instanceof LineChunk) {
            return String.format("Line");
        }
        return "";
    }

    public static float[] getColor(IObject content) {
        if (content instanceof TableBorder) {
            return getColor(SemanticType.TABLE);
        }
        if (content instanceof PDFList) {
            return getColor(SemanticType.LIST);
        }
        if (content instanceof INode) {
            INode node = (INode) content;
            return getColor(node.getSemanticType());
        }
        if (content instanceof ImageChunk) {
            return getColor(SemanticType.FIGURE);
        }
        if (content instanceof LineArtChunk || content instanceof LineChunk) {
            return getColor(SemanticType.PART);
        }
        return new float[]{};
    }

    public static float[] getColor(SemanticType semanticType) {
        if (semanticType == SemanticType.HEADING || semanticType == SemanticType.HEADER || semanticType == SemanticType.FOOTER) {
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

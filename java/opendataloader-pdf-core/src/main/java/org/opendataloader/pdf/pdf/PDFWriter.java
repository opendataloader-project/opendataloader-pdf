/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.pdf;

import org.opendataloader.pdf.processors.DocumentProcessor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.graphics.color.PDColor;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentGroup;
import org.apache.pdfbox.pdmodel.graphics.optionalcontent.PDOptionalContentProperties;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquare;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PDFWriter {

    private static final Logger LOGGER = Logger.getLogger(PDFWriter.class.getCanonicalName());

    private final Map<PDFLayer, PDOptionalContentGroup> optionalContents = new HashMap<>();
    private final List<List<PDAnnotation>> annotations = new ArrayList<>();
    private final List<BoundingBox> pageBoundingBoxes = new ArrayList<>();

    public void updatePDF(File inputPDF, String password, String outputFolder, List<List<IObject>> contents) throws IOException {
        try (PDDocument document = Loader.loadPDF(inputPDF, password)) {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                annotations.add(new ArrayList<>());
                pageBoundingBoxes.add(DocumentProcessor.getPageBoundingBox(pageNumber));
            }
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                for (IObject content : contents.get(pageNumber)) {
                    drawContent(content, PDFLayer.CONTENT);
                }
            }
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                document.getPage(pageNumber).getAnnotations().addAll(annotations.get(pageNumber));
            }
            annotations.clear();
            pageBoundingBoxes.clear();
            createOptContentsForAnnotations(document);
            document.setAllSecurityToBeRemoved(true);

            String outputFileName = outputFolder + File.separator +
                    inputPDF.getName().substring(0, inputPDF.getName().length() - 4) + "_annotated.pdf";
            document.save(outputFileName);
            LOGGER.log(Level.INFO, "Created {0}", outputFileName);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Unable to create annotated PDF output: " + ex.getMessage());
        }
    }

    private void drawContent(IObject content, PDFLayer layer) throws IOException {
        drawContent(content, layer, null);
    }

    private void drawContent(IObject content, PDFLayer layer, PDAnnotation linkedAnnot) throws IOException {
        if ((content instanceof LineChunk)) {
            return;
        }
        PDAnnotation annot = draw(content.getBoundingBox(), getColor(content), getContents(content),
                content.getRecognizedStructureId(), linkedAnnot, content.getLevel(), layer);
        if (content instanceof TableBorder) {
            drawTableCells((TableBorder) content, annot);
        } else if (content instanceof PDFList) {
            drawListItems((PDFList) content, annot);
        } else if (content instanceof SemanticHeaderOrFooter) {
            for (IObject contentItem : ((SemanticHeaderOrFooter) content).getContents()) {
                drawContent(contentItem, PDFLayer.HEADER_AND_FOOTER_CONTENT, annot);
            }
        }
    }

    private void drawTableCells(TableBorder table, PDAnnotation annot) throws IOException {
        if (table.isTextBlock()) {
            for (IObject content : table.getCell(0, 0).getContents()) {
                drawContent(content, PDFLayer.TEXT_BLOCK_CONTENT);
            }
            return;
        }
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    StringBuilder contentValue = new StringBuilder();
                    for (IObject object : cell.getContents()) {
                        if (object instanceof SemanticTextNode) {
                            contentValue.append(((SemanticTextNode) object).getValue());
                        }
                    }
                    String cellValue = String.format("Table cell: row number %s, column number %s, row span %s, column span %s, text content \"%s\"",
                            cell.getRowNumber() + 1, cell.getColNumber() + 1, cell.getRowSpan(), cell.getColSpan(), contentValue);
                    draw(cell.getBoundingBox(), getColor(SemanticType.TABLE), cellValue, null, annot, cell.getLevel(), PDFLayer.TABLE_CELLS);
                    for (IObject content : cell.getContents()) {
                        drawContent(content, PDFLayer.TABLE_CONTENT);
                    }
                }
            }
        }
    }

    private void drawListItems(PDFList list, PDAnnotation annot) throws IOException {
        for (ListItem listItem : list.getListItems()) {
            String contentValue = String.format("List item: text content \"%s\"", listItem.toString());
            draw(listItem.getBoundingBox(), getColor(SemanticType.LIST), contentValue, null, annot, listItem.getLevel(), PDFLayer.LIST_ITEMS);
            for (IObject content : listItem.getContents()) {
                drawContent(content, PDFLayer.LIST_CONTENT);
            }
        }
    }

    public PDAnnotation draw(BoundingBox boundingBox, float[] colorArray,
                                    String contents, Long id, PDAnnotation linkedAnnot, String level, PDFLayer layerName) {
        if (!Objects.equals(boundingBox.getPageNumber(), boundingBox.getLastPageNumber())) {
            if (boundingBox instanceof MultiBoundingBox) {
                PDAnnotation square = null;
                for (int pageNumber = boundingBox.getPageNumber(); pageNumber <= boundingBox.getLastPageNumber(); pageNumber++) {
                    BoundingBox boundingBoxForPage = boundingBox.getBoundingBox(pageNumber);
                    if (boundingBoxForPage != null) {
                        square = draw(boundingBoxForPage, colorArray, contents, id, linkedAnnot, level, layerName);
                    }
                }
                return square;
            } else {
                LOGGER.log(Level.WARNING, "Bounding box on several pages cannot be split");
            }
        }

        BoundingBox movedBoundingBox = new BoundingBox(boundingBox);
        BoundingBox pageBoundingBox = pageBoundingBoxes.get(boundingBox.getPageNumber());
        if (pageBoundingBox != null) {
            movedBoundingBox.move(pageBoundingBox.getLeftX(), pageBoundingBox.getBottomY());
        }
        PDAnnotationSquareCircle square = new PDAnnotationSquare();
        square.setRectangle(new PDRectangle(getFloat(movedBoundingBox.getLeftX()), getFloat(movedBoundingBox.getBottomY()),
                getFloat(movedBoundingBox.getWidth()), getFloat(movedBoundingBox.getHeight())));
        square.setConstantOpacity(0.4f);
        PDColor color = new PDColor(colorArray, PDDeviceRGB.INSTANCE);
        square.setColor(color);
        square.setInteriorColor(color);
        square.setContents((id != null ? "id = " + id + ", " : "") + (level != null ? "level = " + level + ", " : "") + contents);
        if (linkedAnnot != null) {
            square.setInReplyTo(linkedAnnot);
        }
        square.setOptionalContent(getOptionalContent(layerName));
        annotations.get(boundingBox.getPageNumber()).add(square);
        return square;
    }

    private static float getFloat(double value) {
        float floatValue = (float) value;
        if (floatValue == Float.POSITIVE_INFINITY) {
            return Float.MAX_VALUE;
        }
        if (floatValue == Float.NEGATIVE_INFINITY) {
            return -Float.MAX_VALUE;
        }
        return floatValue;
    }

    public static String getContents(IObject content) {
        if (content instanceof TableBorder) {
            TableBorder border = (TableBorder) content;
            if (border.isTextBlock()) {
                return "Text block";
            }
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
            return "Line";
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

    private void createOptContentsForAnnotations(PDDocument document) {
        if (optionalContents.isEmpty()) {
            return;
        }
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        PDOptionalContentProperties oldOCProperties = catalog.getOCProperties();
        if (oldOCProperties == null) {
            oldOCProperties = new PDOptionalContentProperties();
            catalog.setOCProperties(oldOCProperties);
        }

        for (PDOptionalContentGroup group : optionalContents.values()) {
            oldOCProperties.addGroup(group);
            oldOCProperties.setGroupEnabled(group, true);
        }
        optionalContents.clear();
    }

    public PDOptionalContentGroup getOptionalContent(PDFLayer layer) {
        PDOptionalContentGroup group = optionalContents.get(layer);
        if (group == null) {
            COSDictionary cosDictionary = new COSDictionary();
            cosDictionary.setItem(COSName.TYPE, COSName.OCG);
            cosDictionary.setItem(COSName.NAME, new COSString(layer.getValue()));
            group = (PDOptionalContentGroup) PDPropertyList.create(cosDictionary);
            optionalContents.put(layer, group);
        }
        return group;
    }
}

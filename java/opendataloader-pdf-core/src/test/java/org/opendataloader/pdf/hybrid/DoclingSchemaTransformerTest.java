/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for DoclingSchemaTransformer.
 */
public class DoclingSchemaTransformerTest {

    private DoclingSchemaTransformer transformer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transformer = new DoclingSchemaTransformer();
        objectMapper = new ObjectMapper();
        StaticLayoutContainers.setCurrentContentId(1L);
    }

    @Test
    void testGetBackendType() {
        Assertions.assertEquals("docling", transformer.getBackendType());
    }

    @Test
    void testTransformNullJson() {
        HybridResponse response = new HybridResponse("", null, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertTrue(result.isEmpty());
    }

    @Test
    void testTransformEmptyJson() {
        ObjectNode json = objectMapper.createObjectNode();
        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0).isEmpty());
    }

    @Test
    void testTransformSimpleParagraph() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode textNode = texts.addObject();
        textNode.put("label", "text");
        textNode.put("text", "Hello World");
        addProvenance(textNode, 1, 100, 700, 200, 750);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);

        SemanticParagraph paragraph = (SemanticParagraph) result.get(0).get(0);
        Assertions.assertEquals("Hello World", paragraph.getValue());
    }

    @Test
    void testTransformSectionHeader() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode headerNode = texts.addObject();
        headerNode.put("label", "section_header");
        headerNode.put("text", "Introduction");
        addProvenance(headerNode, 1, 100, 750, 300, 780);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticHeading);

        SemanticHeading heading = (SemanticHeading) result.get(0).get(0);
        Assertions.assertEquals("Introduction", heading.getValue());
    }

    @Test
    void testFilterPageHeaderFooter() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        // Add page header - should be filtered
        ObjectNode headerNode = texts.addObject();
        headerNode.put("label", "page_header");
        headerNode.put("text", "Chapter 1");
        addProvenance(headerNode, 1, 100, 800, 200, 820);

        // Add page footer - should be filtered
        ObjectNode footerNode = texts.addObject();
        footerNode.put("label", "page_footer");
        footerNode.put("text", "Page 1");
        addProvenance(footerNode, 1, 100, 20, 150, 40);

        // Add regular text - should be kept
        ObjectNode textNode = texts.addObject();
        textNode.put("label", "text");
        textNode.put("text", "Content");
        addProvenance(textNode, 1, 100, 400, 200, 450);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);
    }

    @Test
    void testTransformCaption() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode captionNode = texts.addObject();
        captionNode.put("label", "caption");
        captionNode.put("text", "Figure 1: Sample image");
        addProvenance(captionNode, 1, 100, 300, 300, 320);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);
    }

    @Test
    void testTransformFootnote() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode footnoteNode = texts.addObject();
        footnoteNode.put("label", "footnote");
        footnoteNode.put("text", "1. Reference source");
        addProvenance(footnoteNode, 1, 100, 50, 300, 70);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);
    }

    @Test
    void testTransformSimpleTable() {
        ObjectNode json = createDoclingDocument();
        ArrayNode tables = json.putArray("tables");

        ObjectNode tableNode = tables.addObject();
        tableNode.put("label", "table");
        addProvenance(tableNode, 1, 50, 200, 350, 400);

        // Add data with grid
        ObjectNode data = tableNode.putObject("data");
        ArrayNode grid = data.putArray("grid");

        // 2x2 table
        ArrayNode row1 = grid.addArray();
        row1.addObject().put("text", "A1");
        row1.addObject().put("text", "B1");

        ArrayNode row2 = grid.addArray();
        row2.addObject().put("text", "A2");
        row2.addObject().put("text", "B2");

        // Add table cells
        ArrayNode tableCells = data.putArray("table_cells");
        addTableCell(tableCells, 0, 0, 1, 1, "A1");
        addTableCell(tableCells, 0, 1, 1, 1, "B1");
        addTableCell(tableCells, 1, 0, 1, 1, "A2");
        addTableCell(tableCells, 1, 1, 1, 1, "B2");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof TableBorder);

        TableBorder table = (TableBorder) result.get(0).get(0);
        Assertions.assertEquals(2, table.getNumberOfRows());
        Assertions.assertEquals(2, table.getNumberOfColumns());
    }

    @Test
    void testTransformTableWithSpans() {
        ObjectNode json = createDoclingDocument();
        ArrayNode tables = json.putArray("tables");

        ObjectNode tableNode = tables.addObject();
        tableNode.put("label", "table");
        addProvenance(tableNode, 1, 50, 200, 350, 400);

        ObjectNode data = tableNode.putObject("data");
        ArrayNode grid = data.putArray("grid");

        // 2x3 table
        ArrayNode row1 = grid.addArray();
        row1.addObject();
        row1.addObject();
        row1.addObject();

        ArrayNode row2 = grid.addArray();
        row2.addObject();
        row2.addObject();
        row2.addObject();

        ArrayNode tableCells = data.putArray("table_cells");
        // First cell spans 2 columns
        addTableCell(tableCells, 0, 0, 1, 2, "Header");
        addTableCell(tableCells, 0, 2, 1, 1, "C1");
        addTableCell(tableCells, 1, 0, 1, 1, "A2");
        addTableCell(tableCells, 1, 1, 1, 1, "B2");
        addTableCell(tableCells, 1, 2, 1, 1, "C2");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof TableBorder);

        TableBorder table = (TableBorder) result.get(0).get(0);
        Assertions.assertEquals(2, table.getNumberOfRows());
        Assertions.assertEquals(3, table.getNumberOfColumns());

        // Check first cell has colspan 2
        Assertions.assertEquals(2, table.getRow(0).getCell(0).getColSpan());
    }

    @Test
    void testTransformMultiplePages() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        // Text on page 1
        ObjectNode text1 = texts.addObject();
        text1.put("label", "text");
        text1.put("text", "Page 1 content");
        addProvenance(text1, 1, 100, 700, 200, 750);

        // Text on page 2
        ObjectNode text2 = texts.addObject();
        text2.put("label", "text");
        text2.put("text", "Page 2 content");
        addProvenance(text2, 2, 100, 700, 200, 750);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);
        pageHeights.put(2, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertEquals(1, result.get(1).size());

        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);
        Assertions.assertTrue(result.get(1).get(0) instanceof SemanticParagraph);

        SemanticParagraph p1 = (SemanticParagraph) result.get(0).get(0);
        SemanticParagraph p2 = (SemanticParagraph) result.get(1).get(0);

        Assertions.assertEquals("Page 1 content", p1.getValue());
        Assertions.assertEquals("Page 2 content", p2.getValue());
    }

    @Test
    void testCoordinateTransformBottomLeft() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode textNode = texts.addObject();
        textNode.put("label", "text");
        textNode.put("text", "Test");

        // Add provenance with BOTTOMLEFT coordinates
        ArrayNode prov = textNode.putArray("prov");
        ObjectNode provItem = prov.addObject();
        provItem.put("page_no", 1);
        ObjectNode bbox = provItem.putObject("bbox");
        bbox.put("l", 100.0);
        bbox.put("t", 750.0);  // top
        bbox.put("r", 200.0);
        bbox.put("b", 700.0);  // bottom
        bbox.put("coord_origin", "BOTTOMLEFT");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());

        IObject obj = result.get(0).get(0);
        Assertions.assertEquals(100.0, obj.getLeftX(), 0.01);
        Assertions.assertEquals(700.0, obj.getBottomY(), 0.01);
        Assertions.assertEquals(200.0, obj.getRightX(), 0.01);
        Assertions.assertEquals(750.0, obj.getTopY(), 0.01);
    }

    @Test
    void testCoordinateTransformTopLeft() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode textNode = texts.addObject();
        textNode.put("label", "text");
        textNode.put("text", "Test");

        // Add provenance with TOPLEFT coordinates
        ArrayNode prov = textNode.putArray("prov");
        ObjectNode provItem = prov.addObject();
        provItem.put("page_no", 1);
        ObjectNode bbox = provItem.putObject("bbox");
        bbox.put("l", 100.0);
        bbox.put("t", 92.0);   // distance from top (92 px from top = 750 from bottom for page height 842)
        bbox.put("r", 200.0);
        bbox.put("b", 142.0);  // distance from top (142 px from top = 700 from bottom)
        bbox.put("coord_origin", "TOPLEFT");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());

        IObject obj = result.get(0).get(0);
        Assertions.assertEquals(100.0, obj.getLeftX(), 0.01);
        Assertions.assertEquals(700.0, obj.getBottomY(), 0.01);
        Assertions.assertEquals(200.0, obj.getRightX(), 0.01);
        Assertions.assertEquals(750.0, obj.getTopY(), 0.01);
    }

    @Test
    void testReadingOrderSort() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        // Add texts in reverse order
        ObjectNode text3 = texts.addObject();
        text3.put("label", "text");
        text3.put("text", "Third");
        addProvenance(text3, 1, 100, 100, 200, 150);

        ObjectNode text1 = texts.addObject();
        text1.put("label", "text");
        text1.put("text", "First");
        addProvenance(text1, 1, 100, 700, 200, 750);

        ObjectNode text2 = texts.addObject();
        text2.put("label", "text");
        text2.put("text", "Second");
        addProvenance(text2, 1, 100, 400, 200, 450);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3, result.get(0).size());

        // Should be sorted top to bottom
        SemanticParagraph p1 = (SemanticParagraph) result.get(0).get(0);
        SemanticParagraph p2 = (SemanticParagraph) result.get(0).get(1);
        SemanticParagraph p3 = (SemanticParagraph) result.get(0).get(2);

        Assertions.assertEquals("First", p1.getValue());
        Assertions.assertEquals("Second", p2.getValue());
        Assertions.assertEquals("Third", p3.getValue());
    }

    @Test
    void testMixedContent() {
        ObjectNode json = createDoclingDocument();

        // Add texts
        ArrayNode texts = json.putArray("texts");
        ObjectNode heading = texts.addObject();
        heading.put("label", "section_header");
        heading.put("text", "Title");
        addProvenance(heading, 1, 100, 750, 300, 780);

        ObjectNode para = texts.addObject();
        para.put("label", "text");
        para.put("text", "Body text");
        addProvenance(para, 1, 100, 600, 300, 650);

        // Add table
        ArrayNode tables = json.putArray("tables");
        ObjectNode tableNode = tables.addObject();
        tableNode.put("label", "table");
        addProvenance(tableNode, 1, 100, 300, 300, 500);

        ObjectNode data = tableNode.putObject("data");
        ArrayNode grid = data.putArray("grid");
        ArrayNode row1 = grid.addArray();
        row1.addObject().put("text", "Cell");

        ArrayNode tableCells = data.putArray("table_cells");
        addTableCell(tableCells, 0, 0, 1, 1, "Cell");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3, result.get(0).size());

        // Sorted by reading order: heading (top), paragraph, table (bottom)
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticHeading);
        Assertions.assertTrue(result.get(0).get(1) instanceof SemanticParagraph);
        Assertions.assertTrue(result.get(0).get(2) instanceof TableBorder);
    }

    @Test
    void testTransformPage() {
        ObjectNode pageContent = objectMapper.createObjectNode();
        ArrayNode texts = pageContent.putArray("texts");

        ObjectNode textNode = texts.addObject();
        textNode.put("label", "text");
        textNode.put("text", "Single page content");
        addProvenance(textNode, 1, 100, 700, 200, 750);

        List<IObject> result = transformer.transformPage(1, pageContent, 842.0);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0) instanceof SemanticParagraph);
    }

    @Test
    void testTextMissingProv() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        // Text without provenance - should be skipped
        ObjectNode textNode = texts.addObject();
        textNode.put("label", "text");
        textNode.put("text", "No position");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0).isEmpty());
    }

    @Test
    void testTableMissingData() {
        ObjectNode json = createDoclingDocument();
        ArrayNode tables = json.putArray("tables");

        // Table without data - should be skipped
        ObjectNode tableNode = tables.addObject();
        tableNode.put("label", "table");
        addProvenance(tableNode, 1, 100, 200, 300, 400);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0).isEmpty());
    }

    @Test
    void testTransformFormula() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode formulaNode = texts.addObject();
        formulaNode.put("label", "formula");
        formulaNode.put("text", "\\frac{f(x+h) - f(x)}{h}");
        addProvenance(formulaNode, 1, 226, 144, 377, 168);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticFormula);

        SemanticFormula formula = (SemanticFormula) result.get(0).get(0);
        Assertions.assertEquals("\\frac{f(x+h) - f(x)}{h}", formula.getLatex());
    }

    @Test
    void testTransformFormulaWithComplexLatex() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        ObjectNode formulaNode = texts.addObject();
        formulaNode.put("label", "formula");
        formulaNode.put("text", "\\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h} = f'(x)");
        addProvenance(formulaNode, 1, 237, 84, 365, 114);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticFormula);

        SemanticFormula formula = (SemanticFormula) result.get(0).get(0);
        Assertions.assertEquals("\\lim_{h \\to 0} \\frac{f(x+h) - f(x)}{h} = f'(x)", formula.getLatex());
    }

    @Test
    void testMixedContentWithFormula() {
        ObjectNode json = createDoclingDocument();
        ArrayNode texts = json.putArray("texts");

        // Add paragraph before formula
        ObjectNode para = texts.addObject();
        para.put("label", "text");
        para.put("text", "The forward difference is defined as");
        addProvenance(para, 1, 90, 180, 468, 190);

        // Add formula
        ObjectNode formulaNode = texts.addObject();
        formulaNode.put("label", "formula");
        formulaNode.put("text", "Q_f(h) = \\frac{f(x+h) - f(x)}{h}");
        addProvenance(formulaNode, 1, 226, 144, 377, 168);

        // Add paragraph after formula
        ObjectNode paraAfter = texts.addObject();
        paraAfter.put("label", "text");
        paraAfter.put("text", "in which h is called the step size");
        addProvenance(paraAfter, 1, 90, 125, 291, 135);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3, result.get(0).size());

        // Sorted by reading order: paragraph (top), formula, paragraph (bottom)
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);
        Assertions.assertTrue(result.get(0).get(1) instanceof SemanticFormula);
        Assertions.assertTrue(result.get(0).get(2) instanceof SemanticParagraph);

        SemanticFormula formula = (SemanticFormula) result.get(0).get(1);
        Assertions.assertEquals("Q_f(h) = \\frac{f(x+h) - f(x)}{h}", formula.getLatex());
    }

    @Test
    void testTransformPictureWithDescription() {
        ObjectNode json = createDoclingDocument();
        ArrayNode pictures = json.putArray("pictures");

        ObjectNode pictureNode = pictures.addObject();
        addProvenance(pictureNode, 1, 100, 300, 400, 500);

        // Add annotations with description
        ArrayNode annotations = pictureNode.putArray("annotations");
        ObjectNode descAnnotation = annotations.addObject();
        descAnnotation.put("kind", "description");
        descAnnotation.put("text", "A bar chart showing quarterly sales data from Q1 to Q4");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticPicture);

        SemanticPicture picture = (SemanticPicture) result.get(0).get(0);
        Assertions.assertTrue(picture.hasDescription());
        Assertions.assertEquals("A bar chart showing quarterly sales data from Q1 to Q4", picture.getDescription());
        Assertions.assertEquals(1, picture.getPictureIndex());
    }

    @Test
    void testTransformPictureWithoutDescription() {
        ObjectNode json = createDoclingDocument();
        ArrayNode pictures = json.putArray("pictures");

        ObjectNode pictureNode = pictures.addObject();
        addProvenance(pictureNode, 1, 100, 300, 400, 500);
        // No annotations

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticPicture);

        SemanticPicture picture = (SemanticPicture) result.get(0).get(0);
        Assertions.assertFalse(picture.hasDescription());
        Assertions.assertEquals("", picture.getDescription());
    }

    @Test
    void testTransformMultiplePicturesWithDescriptions() {
        ObjectNode json = createDoclingDocument();
        ArrayNode pictures = json.putArray("pictures");

        // First picture with description
        ObjectNode picture1 = pictures.addObject();
        addProvenance(picture1, 1, 100, 600, 300, 700);
        ArrayNode annotations1 = picture1.putArray("annotations");
        ObjectNode desc1 = annotations1.addObject();
        desc1.put("kind", "description");
        desc1.put("text", "A flow chart showing the process flow");

        // Second picture without description
        ObjectNode picture2 = pictures.addObject();
        addProvenance(picture2, 1, 100, 300, 300, 400);

        // Third picture with description
        ObjectNode picture3 = pictures.addObject();
        addProvenance(picture3, 1, 100, 100, 300, 200);
        ArrayNode annotations3 = picture3.putArray("annotations");
        ObjectNode desc3 = annotations3.addObject();
        desc3.put("kind", "description");
        desc3.put("text", "A pie chart showing market share distribution");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3, result.get(0).size());

        // Pictures should be sorted by reading order (top to bottom)
        SemanticPicture pic1 = (SemanticPicture) result.get(0).get(0);
        SemanticPicture pic2 = (SemanticPicture) result.get(0).get(1);
        SemanticPicture pic3 = (SemanticPicture) result.get(0).get(2);

        Assertions.assertTrue(pic1.hasDescription());
        Assertions.assertFalse(pic2.hasDescription());
        Assertions.assertTrue(pic3.hasDescription());
    }

    // Helper methods

    private ObjectNode createDoclingDocument() {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("schema_name", "DoclingDocument");
        json.put("version", "1.0.0");
        return json;
    }

    private void addProvenance(ObjectNode node, int pageNo, double l, double b, double r, double t) {
        ArrayNode prov = node.putArray("prov");
        ObjectNode provItem = prov.addObject();
        provItem.put("page_no", pageNo);
        ObjectNode bbox = provItem.putObject("bbox");
        bbox.put("l", l);
        bbox.put("t", t);
        bbox.put("r", r);
        bbox.put("b", b);
        bbox.put("coord_origin", "BOTTOMLEFT");
    }

    private void addTableCell(ArrayNode tableCells, int row, int col, int rowSpan, int colSpan, String text) {
        ObjectNode cell = tableCells.addObject();
        cell.put("start_row_offset_idx", row);
        cell.put("end_row_offset_idx", row + rowSpan);
        cell.put("start_col_offset_idx", col);
        cell.put("end_col_offset_idx", col + colSpan);
        cell.put("row_span", rowSpan);
        cell.put("col_span", colSpan);
        cell.put("text", text);
    }
}

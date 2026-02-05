/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

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
 * Unit tests for HancomSchemaTransformer.
 *
 * <p>Tests the transformation of Hancom VisualInfoDto JSON format to
 * OpenDataLoader IObject hierarchy.
 */
public class HancomSchemaTransformerTest {

    private HancomSchemaTransformer transformer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transformer = new HancomSchemaTransformer();
        objectMapper = new ObjectMapper();
        StaticLayoutContainers.setCurrentContentId(1L);
    }

    @Test
    void testGetBackendType() {
        Assertions.assertEquals("hancom", transformer.getBackendType());
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
        json.putArray("elements");
        json.putArray("pageSizes");

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0).isEmpty());
    }

    @Test
    void testTransformSimpleParagraph() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        addElement(elements, "PARAGRAPH", "text", "Hello World", 0, 100, 92, 100, 50);

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
    void testTransformHeading() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        addElement(elements, "HEADING", "heading", "Introduction", 0, 100, 62, 200, 30);

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
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        // Add page header - should be filtered
        addElement(elements, "PAGE_HEADER", "header", "Chapter 1", 0, 100, 22, 100, 20);

        // Add page footer - should be filtered
        addElement(elements, "PAGE_FOOTER", "footer", "Page 1", 0, 100, 802, 50, 20);

        // Add regular text - should be kept
        addElement(elements, "PARAGRAPH", "text", "Content", 0, 100, 400, 100, 50);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);
    }

    @Test
    void testTransformFormula() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        addElement(elements, "FORMULA", "formula", "\\frac{x+y}{z}", 0, 200, 300, 150, 40);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticFormula);

        SemanticFormula formula = (SemanticFormula) result.get(0).get(0);
        Assertions.assertEquals("\\frac{x+y}{z}", formula.getLatex());
    }

    @Test
    void testTransformFigure() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        addElement(elements, "FIGURE", "figure", "", 0, 100, 200, 300, 200);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticPicture);
    }

    @Test
    void testTransformSimpleTable() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        // Add a 2x2 table with Hancom API structure: content.table.cells
        ObjectNode tableElement = addTableElement(elements, 0, 50, 200, 300, 200);
        ArrayNode cells = addTableContentStructure(tableElement);

        addTableCell(cells, "A1", 0, 0, 1, 1, 50, 200, 150, 100);
        addTableCell(cells, "B1", 0, 1, 1, 1, 200, 200, 150, 100);
        addTableCell(cells, "A2", 1, 0, 1, 1, 50, 300, 150, 100);
        addTableCell(cells, "B2", 1, 1, 1, 1, 200, 300, 150, 100);

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
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        // Add a table with colspan using Hancom API structure
        ObjectNode tableElement = addTableElement(elements, 0, 50, 200, 300, 200);
        ArrayNode cells = addTableContentStructure(tableElement);

        // First cell spans 2 columns
        addTableCell(cells, "Header", 0, 0, 1, 2, 50, 200, 300, 100);
        addTableCell(cells, "A2", 1, 0, 1, 1, 50, 300, 150, 100);
        addTableCell(cells, "B2", 1, 1, 1, 1, 200, 300, 150, 100);

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
        Assertions.assertEquals(2, table.getRow(0).getCell(0).getColSpan());
    }

    @Test
    void testTransformMultiplePages() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");
        ArrayNode pageSizes = (ArrayNode) json.get("pageSizes");

        // Add second page size
        ObjectNode page2Size = pageSizes.addObject();
        page2Size.put("width", 612.0);
        page2Size.put("height", 842.0);

        // Text on page 1 (pageIndex = 0)
        addElement(elements, "PARAGRAPH", "text", "Page 1 content", 0, 100, 100, 200, 50);

        // Text on page 2 (pageIndex = 1)
        addElement(elements, "PARAGRAPH", "text", "Page 2 content", 1, 100, 100, 200, 50);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);
        pageHeights.put(2, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        Assertions.assertEquals(1, result.get(1).size());

        SemanticParagraph p1 = (SemanticParagraph) result.get(0).get(0);
        SemanticParagraph p2 = (SemanticParagraph) result.get(1).get(0);

        Assertions.assertEquals("Page 1 content", p1.getValue());
        Assertions.assertEquals("Page 2 content", p2.getValue());
    }

    @Test
    void testBoundingBoxTransformation() {
        // Hancom uses TOPLEFT origin: (left, top, width, height)
        // OpenDataLoader uses BOTTOMLEFT origin: (left, bottom, right, top)
        // For page height 842:
        //   top=92, height=50 -> bottomY = 842 - 92 - 50 = 700, topY = 842 - 92 = 750
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        addElement(elements, "PARAGRAPH", "text", "Test", 0, 100, 92, 100, 50);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());

        IObject obj = result.get(0).get(0);
        Assertions.assertEquals(100.0, obj.getLeftX(), 0.01);
        Assertions.assertEquals(200.0, obj.getRightX(), 0.01);  // left + width
        Assertions.assertEquals(700.0, obj.getBottomY(), 0.01);  // pageHeight - top - height
        Assertions.assertEquals(750.0, obj.getTopY(), 0.01);     // pageHeight - top
    }

    @Test
    void testReadingOrderSort() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        // Add texts in reverse order (bottom to top)
        addElement(elements, "PARAGRAPH", "text", "Third", 0, 100, 700, 100, 50);   // bottom
        addElement(elements, "PARAGRAPH", "text", "First", 0, 100, 92, 100, 50);    // top
        addElement(elements, "PARAGRAPH", "text", "Second", 0, 100, 400, 100, 50);  // middle

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(3, result.get(0).size());

        // Should be sorted top to bottom (highest topY first)
        SemanticParagraph p1 = (SemanticParagraph) result.get(0).get(0);
        SemanticParagraph p2 = (SemanticParagraph) result.get(0).get(1);
        SemanticParagraph p3 = (SemanticParagraph) result.get(0).get(2);

        Assertions.assertEquals("First", p1.getValue());
        Assertions.assertEquals("Second", p2.getValue());
        Assertions.assertEquals("Third", p3.getValue());
    }

    @Test
    void testMixedContent() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        // Add heading at top
        addElement(elements, "HEADING", "heading", "Title", 0, 100, 50, 200, 30);

        // Add paragraph in middle
        addElement(elements, "PARAGRAPH", "text", "Body text", 0, 100, 150, 300, 50);

        // Add table at bottom using Hancom API structure
        ObjectNode tableElement = addTableElement(elements, 0, 100, 300, 200, 150);
        ArrayNode cells = addTableContentStructure(tableElement);
        addTableCell(cells, "Cell", 0, 0, 1, 1, 100, 300, 200, 150);

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
    void testTransformListItem() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        addElement(elements, "LIST_ITEM", "list", "First item", 0, 100, 200, 200, 30);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        // LIST_ITEM is treated as SemanticParagraph
        Assertions.assertTrue(result.get(0).get(0) instanceof SemanticParagraph);
    }

    @Test
    void testElementMissingBbox() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        // Element without bbox - should be skipped
        ObjectNode element = elements.addObject();
        element.put("id", "1");
        ObjectNode category = element.putObject("category");
        category.put("type", "PARAGRAPH");
        category.put("label", "text");
        ObjectNode content = element.putObject("content");
        content.put("text", "No position");
        element.put("pageIndex", 0);
        // No bbox

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0).isEmpty());
    }

    @Test
    void testTransformPage() {
        ObjectNode pageContent = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) pageContent.get("elements");

        addElement(elements, "PARAGRAPH", "text", "Single page content", 0, 100, 100, 200, 50);

        List<IObject> result = transformer.transformPage(1, pageContent, 842.0);

        Assertions.assertEquals(1, result.size());
        Assertions.assertTrue(result.get(0) instanceof SemanticParagraph);
    }

    @Test
    void testTransformWithHtmlContent() {
        ObjectNode json = createVisualInfoDto();
        ArrayNode elements = (ArrayNode) json.get("elements");

        // Add element with HTML content
        ObjectNode element = elements.addObject();
        element.put("id", "1");
        ObjectNode category = element.putObject("category");
        category.put("type", "PARAGRAPH");
        category.put("label", "text");
        ObjectNode content = element.putObject("content");
        content.put("text", "Plain text");
        content.put("html", "<p>HTML content</p>");
        content.put("markdown", "**Markdown** content");
        ObjectNode bbox = element.putObject("bbox");
        bbox.put("left", 100);
        bbox.put("top", 100);
        bbox.put("width", 200);
        bbox.put("height", 50);
        element.put("pageIndex", 0);

        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);

        List<List<IObject>> result = transformer.transform(response, pageHeights);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(1, result.get(0).size());
        // Should use text content for SemanticParagraph
        SemanticParagraph para = (SemanticParagraph) result.get(0).get(0);
        Assertions.assertEquals("Plain text", para.getValue());
    }

    // Helper methods

    private ObjectNode createVisualInfoDto() {
        ObjectNode json = objectMapper.createObjectNode();
        json.put("runtime", 1000);
        json.put("version", "1.0");

        ObjectNode metadata = json.putObject("metadata");
        metadata.put("fileId", "test-file-id");
        metadata.put("fileName", "test.pdf");

        json.putArray("elements");

        ArrayNode pageSizes = json.putArray("pageSizes");
        ObjectNode page1Size = pageSizes.addObject();
        page1Size.put("width", 612.0);
        page1Size.put("height", 842.0);

        return json;
    }

    private void addElement(ArrayNode elements, String type, String label, String text,
                           int pageIndex, double left, double top, double width, double height) {
        ObjectNode element = elements.addObject();
        element.put("id", String.valueOf(elements.size()));

        ObjectNode category = element.putObject("category");
        category.put("type", type);
        category.put("label", label);

        ObjectNode content = element.putObject("content");
        content.put("text", text);

        ObjectNode bbox = element.putObject("bbox");
        bbox.put("left", left);
        bbox.put("top", top);
        bbox.put("width", width);
        bbox.put("height", height);

        element.put("pageIndex", pageIndex);
    }

    private ObjectNode addTableElement(ArrayNode elements, int pageIndex,
                                       double left, double top, double width, double height) {
        ObjectNode element = elements.addObject();
        element.put("id", String.valueOf(elements.size()));

        ObjectNode category = element.putObject("category");
        category.put("type", "TABLE");
        category.put("label", "table");

        // Create content object (not array) - will be populated by addTableContentStructure
        element.putObject("content");

        ObjectNode bbox = element.putObject("bbox");
        bbox.put("left", left);
        bbox.put("top", top);
        bbox.put("width", width);
        bbox.put("height", height);

        element.put("pageIndex", pageIndex);

        return element;
    }

    /**
     * Creates the Hancom API table content structure: content.table.cells
     * Returns the cells ArrayNode for adding cells.
     */
    private ArrayNode addTableContentStructure(ObjectNode tableElement) {
        ObjectNode content = (ObjectNode) tableElement.get("content");
        content.put("text", "");
        content.put("html", "<table></table>");
        ObjectNode tableNode = content.putObject("table");
        return tableNode.putArray("cells");
    }

    private void addTableCell(ArrayNode cells, String text, int row, int col,
                             int rowSpan, int colSpan,
                             double left, double top, double width, double height) {
        ObjectNode cell = cells.addObject();
        cell.put("cellId", row + "-" + col);
        cell.put("text", text);

        ArrayNode rowspanArr = cell.putArray("rowspan");
        for (int i = 0; i < rowSpan; i++) {
            rowspanArr.add(row + i);
        }

        ArrayNode colspanArr = cell.putArray("colspan");
        for (int i = 0; i < colSpan; i++) {
            colspanArr.add(col + i);
        }

        ObjectNode bbox = cell.putObject("bbox");
        bbox.put("left", left);
        bbox.put("top", top);
        bbox.put("width", width);
        bbox.put("height", height);
    }
}

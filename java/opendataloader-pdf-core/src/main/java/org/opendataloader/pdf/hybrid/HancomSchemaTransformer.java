/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transforms Hancom VisualInfoDto JSON output to OpenDataLoader IObject hierarchy.
 *
 * <p>This transformer handles the Hancom Document AI response format and converts
 * its elements (PARAGRAPH, HEADING, TABLE, FIGURE, etc.) to the equivalent IObject
 * types used by OpenDataLoader's downstream processors and generators.
 *
 * <h2>Schema Mapping</h2>
 * <ul>
 *   <li>PARAGRAPH → SemanticParagraph</li>
 *   <li>HEADING → SemanticHeading</li>
 *   <li>TABLE → TableBorder with rows and cells</li>
 *   <li>FIGURE → SemanticPicture</li>
 *   <li>FORMULA → SemanticFormula</li>
 *   <li>LIST_ITEM → SemanticParagraph</li>
 *   <li>PAGE_HEADER, PAGE_FOOTER → Filtered out (furniture)</li>
 * </ul>
 *
 * <h2>Coordinate System</h2>
 * <p>Hancom uses TOPLEFT origin with (left, top, width, height) format.
 * OpenDataLoader uses BOTTOMLEFT origin with (left, bottom, right, top) format.
 * This transformer handles the coordinate conversion.
 *
 * <h2>Thread Safety</h2>
 * <p>This class is NOT thread-safe. The {@code transform()} method resets
 * internal state (pictureIndex) at the start of each call. Concurrent calls
 * to transform() on the same instance may produce incorrect results.
 * Use separate instances for concurrent transformations.
 */
public class HancomSchemaTransformer implements HybridSchemaTransformer {

    private static final Logger LOGGER = Logger.getLogger(HancomSchemaTransformer.class.getCanonicalName());

    private static final String BACKEND_TYPE = "hancom";

    // Picture index counter (reset per transform call)
    private int pictureIndex;

    // Hancom element types
    private static final String TYPE_PARAGRAPH = "PARAGRAPH";
    private static final String TYPE_HEADING = "HEADING";
    private static final String TYPE_TABLE = "TABLE";
    private static final String TYPE_FIGURE = "FIGURE";
    private static final String TYPE_FORMULA = "FORMULA";
    private static final String TYPE_LIST_ITEM = "LIST_ITEM";
    private static final String TYPE_PAGE_HEADER = "PAGE_HEADER";
    private static final String TYPE_PAGE_FOOTER = "PAGE_FOOTER";

    @Override
    public String getBackendType() {
        return BACKEND_TYPE;
    }

    @Override
    public List<List<IObject>> transform(HybridResponse response, Map<Integer, Double> pageHeights) {
        JsonNode json = response.getJson();
        if (json == null) {
            LOGGER.log(Level.WARNING, "HybridResponse JSON is null, returning empty result");
            return Collections.emptyList();
        }

        // Reset picture index for each transform call
        pictureIndex = 0;

        // Determine number of pages
        int numPages = determinePageCount(json, pageHeights);

        // Initialize result list
        List<List<IObject>> result = new ArrayList<>(numPages);
        for (int i = 0; i < numPages; i++) {
            result.add(new ArrayList<>());
        }

        // Transform elements
        JsonNode elements = json.get("elements");
        if (elements != null && elements.isArray()) {
            for (JsonNode element : elements) {
                transformElement(element, result, pageHeights);
            }
        }

        // Sort each page's contents by reading order (top to bottom, left to right)
        for (List<IObject> pageContents : result) {
            sortByReadingOrder(pageContents);
        }

        return result;
    }

    @Override
    public List<IObject> transformPage(int pageNumber, JsonNode pageContent, double pageHeight) {
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(pageNumber, pageHeight);

        // Create a wrapper response with just this page's content
        HybridResponse singlePageResponse = new HybridResponse("", pageContent, Collections.emptyMap());
        List<List<IObject>> result = transform(singlePageResponse, pageHeights);

        if (result.isEmpty()) {
            return Collections.emptyList();
        }

        // Find the page in the result (pageIndex is 0-based, pageNumber is 1-based)
        int pageIndex = pageNumber - 1;
        if (pageIndex >= 0 && pageIndex < result.size()) {
            return result.get(pageIndex);
        }

        // If result has content but page index doesn't match, return first page
        if (!result.isEmpty() && !result.get(0).isEmpty()) {
            return result.get(0);
        }

        return Collections.emptyList();
    }

    /**
     * Determines the number of pages from the JSON response.
     */
    private int determinePageCount(JsonNode json, Map<Integer, Double> pageHeights) {
        // First check pageHeights if provided
        if (pageHeights != null && !pageHeights.isEmpty()) {
            return pageHeights.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        // Check pageSizes array
        JsonNode pageSizes = json.get("pageSizes");
        if (pageSizes != null && pageSizes.isArray()) {
            return pageSizes.size();
        }

        // Scan elements for max pageIndex
        return scanElementsForPageCount(json);
    }

    /**
     * Scans elements to determine page count.
     */
    private int scanElementsForPageCount(JsonNode json) {
        int maxPage = 0;

        JsonNode elements = json.get("elements");
        if (elements != null && elements.isArray()) {
            for (JsonNode element : elements) {
                JsonNode pageIndex = element.get("pageIndex");
                if (pageIndex != null && pageIndex.isInt()) {
                    maxPage = Math.max(maxPage, pageIndex.asInt() + 1);  // pageIndex is 0-based
                }
            }
        }

        return Math.max(maxPage, 1);  // At least 1 page
    }

    /**
     * Transforms a single Hancom element to an IObject.
     */
    private void transformElement(JsonNode element, List<List<IObject>> result, Map<Integer, Double> pageHeights) {
        // Get category type
        JsonNode category = element.get("category");
        if (category == null) {
            LOGGER.log(Level.FINE, "Element missing category, skipping");
            return;
        }

        String type = getTextValue(category, "type");
        if (type == null) {
            LOGGER.log(Level.FINE, "Element category missing type, skipping");
            return;
        }

        // Skip furniture elements (page headers/footers)
        if (TYPE_PAGE_HEADER.equals(type) || TYPE_PAGE_FOOTER.equals(type)) {
            return;
        }

        // Get page index (0-based)
        int pageIndex = element.has("pageIndex") ? element.get("pageIndex").asInt() : 0;

        // Ensure result list is large enough
        while (result.size() <= pageIndex) {
            result.add(new ArrayList<>());
        }

        // Get bounding box
        JsonNode bboxNode = element.get("bbox");
        if (bboxNode == null) {
            LOGGER.log(Level.FINE, "Element missing bbox, skipping");
            return;
        }

        // Get page height for coordinate conversion
        Double pageHeight = pageHeights != null ? pageHeights.get(pageIndex + 1) : null;
        if (pageHeight == null) {
            // Try to get from pageSizes
            pageHeight = 842.0;  // Default A4 height
        }

        BoundingBox bbox = extractBoundingBox(bboxNode, pageIndex, pageHeight);

        // Get content
        JsonNode contentNode = element.get("content");
        String text = contentNode != null ? getTextValue(contentNode, "text") : null;
        if (text == null) {
            text = "";
        }

        // Create appropriate IObject based on type
        IObject object = null;

        switch (type) {
            case TYPE_PARAGRAPH:
            case TYPE_LIST_ITEM:
                object = createParagraph(text, bbox);
                break;
            case TYPE_HEADING:
                object = createHeading(text, bbox);
                break;
            case TYPE_TABLE:
                object = transformTable(element, bbox, pageIndex, pageHeight);
                break;
            case TYPE_FIGURE:
                object = createPicture(bbox);
                break;
            case TYPE_FORMULA:
                object = createFormula(text, bbox);
                break;
            default:
                // Unknown type, treat as paragraph if has text
                if (!text.isEmpty()) {
                    object = createParagraph(text, bbox);
                }
                break;
        }

        if (object != null) {
            result.get(pageIndex).add(object);
        }
    }

    /**
     * Creates a SemanticParagraph.
     */
    private SemanticParagraph createParagraph(String text, BoundingBox bbox) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(textLine);
        paragraph.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        // Set semantic score to avoid NullPointerException in ListUtils.isContainsHeading()
        paragraph.setCorrectSemanticScore(1.0);

        return paragraph;
    }

    /**
     * Creates a SemanticHeading.
     */
    private SemanticHeading createHeading(String text, BoundingBox bbox) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        SemanticHeading heading = new SemanticHeading();
        heading.add(textLine);
        heading.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        heading.setHeadingLevel(1);  // Default level
        // Set semantic score to avoid NullPointerException in ListUtils.isContainsHeading()
        heading.setCorrectSemanticScore(1.0);

        return heading;
    }

    /**
     * Creates a SemanticFormula.
     */
    private SemanticFormula createFormula(String latex, BoundingBox bbox) {
        SemanticFormula formula = new SemanticFormula(bbox, latex);
        formula.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return formula;
    }

    /**
     * Creates a SemanticPicture.
     */
    private SemanticPicture createPicture(BoundingBox bbox) {
        SemanticPicture picture = new SemanticPicture(bbox, ++pictureIndex, null);
        picture.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return picture;
    }

    /**
     * Transforms a Hancom table element to a TableBorder.
     *
     * <p>Hancom API returns table data in this structure:
     * <pre>
     * {
     *   "content": {
     *     "text": "...",
     *     "html": "&lt;table&gt;...&lt;/table&gt;",
     *     "table": {
     *       "cells": [
     *         {"cellId": "0", "rowspan": [0], "colspan": [0], "bbox": {...}, "text": "..."},
     *         ...
     *       ]
     *     }
     *   }
     * }
     * </pre>
     */
    private TableBorder transformTable(JsonNode element, BoundingBox tableBbox,
                                       int pageIndex, double pageHeight) {
        // Get table cells from content.table.cells
        JsonNode contentNode = element.get("content");
        if (contentNode == null) {
            LOGGER.log(Level.FINE, "Table element missing content, skipping");
            return null;
        }

        // Hancom API: cells are in content.table.cells
        JsonNode tableNode = contentNode.get("table");
        if (tableNode == null) {
            LOGGER.log(Level.FINE, "Table element missing content.table, skipping");
            return null;
        }

        JsonNode cellsNode = tableNode.get("cells");
        if (cellsNode == null || !cellsNode.isArray() || cellsNode.size() == 0) {
            LOGGER.log(Level.FINE, "Table missing cells, skipping");
            return null;
        }

        // Determine table dimensions from cells
        int numRows = 0;
        int numCols = 0;
        Map<String, JsonNode> cellMap = new HashMap<>();

        for (JsonNode cell : cellsNode) {
            JsonNode rowspanNode = cell.get("rowspan");
            JsonNode colspanNode = cell.get("colspan");

            if (rowspanNode != null && rowspanNode.isArray() && rowspanNode.size() > 0) {
                int maxRow = 0;
                for (JsonNode r : rowspanNode) {
                    maxRow = Math.max(maxRow, r.asInt() + 1);
                }
                numRows = Math.max(numRows, maxRow);
            }

            if (colspanNode != null && colspanNode.isArray() && colspanNode.size() > 0) {
                int maxCol = 0;
                for (JsonNode c : colspanNode) {
                    maxCol = Math.max(maxCol, c.asInt() + 1);
                }
                numCols = Math.max(numCols, maxCol);
            }

            // Store cell by row,col key
            int row = rowspanNode != null && rowspanNode.size() > 0 ? rowspanNode.get(0).asInt() : 0;
            int col = colspanNode != null && colspanNode.size() > 0 ? colspanNode.get(0).asInt() : 0;
            String key = row + "," + col;
            cellMap.put(key, cell);
        }

        if (numRows == 0 || numCols == 0) {
            return null;
        }

        // Create TableBorder
        TableBorder table = new TableBorder(numRows, numCols);
        table.setBoundingBox(tableBbox);
        table.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());

        // Build table structure
        double rowHeight = (tableBbox.getTopY() - tableBbox.getBottomY()) / numRows;
        double colWidth = (tableBbox.getRightX() - tableBbox.getLeftX()) / numCols;

        for (int row = 0; row < numRows; row++) {
            TableBorderRow borderRow = new TableBorderRow(row, numCols, 0L);
            double rowTop = tableBbox.getTopY() - (row * rowHeight);
            double rowBottom = rowTop - rowHeight;
            borderRow.setBoundingBox(new BoundingBox(pageIndex,
                tableBbox.getLeftX(), rowBottom, tableBbox.getRightX(), rowTop));

            for (int col = 0; col < numCols; col++) {
                String key = row + "," + col;
                JsonNode cellNode = cellMap.get(key);

                int rowSpan = 1;
                int colSpan = 1;
                String cellText = "";

                if (cellNode != null) {
                    JsonNode rowspanNode = cellNode.get("rowspan");
                    JsonNode colspanNode = cellNode.get("colspan");

                    rowSpan = rowspanNode != null ? rowspanNode.size() : 1;
                    colSpan = colspanNode != null ? colspanNode.size() : 1;

                    cellText = getTextValue(cellNode, "text");
                    if (cellText == null) {
                        cellText = "";
                    }
                }

                TableBorderCell cell = new TableBorderCell(row, col, rowSpan, colSpan, 0L);
                double cellLeft = tableBbox.getLeftX() + (col * colWidth);
                double cellRight = cellLeft + (colSpan * colWidth);
                double cellTop = tableBbox.getTopY() - (row * rowHeight);
                double cellBottom = cellTop - (rowSpan * rowHeight);
                cell.setBoundingBox(new BoundingBox(pageIndex, cellLeft, cellBottom, cellRight, cellTop));

                // Add cell content if present
                if (!cellText.isEmpty()) {
                    SemanticParagraph content = createParagraph(cellText, cell.getBoundingBox());
                    cell.addContentObject(content);
                }

                borderRow.getCells()[col] = cell;
            }

            table.getRows()[row] = borderRow;
        }

        return table;
    }

    /**
     * Extracts a BoundingBox from Hancom bbox JSON.
     *
     * <p>Hancom uses TOPLEFT origin with {left, top, width, height} format.
     * OpenDataLoader uses BOTTOMLEFT origin with {left, bottom, right, top} format.
     *
     * @param bboxNode   The bbox JSON node with left, top, width, height fields
     * @param pageIndex  The 0-indexed page number
     * @param pageHeight The page height for coordinate transformation
     * @return A BoundingBox in OpenDataLoader format
     */
    private BoundingBox extractBoundingBox(JsonNode bboxNode, int pageIndex, double pageHeight) {
        if (bboxNode == null) {
            return new BoundingBox(pageIndex, 0, 0, 0, 0);
        }

        double left = bboxNode.has("left") ? bboxNode.get("left").asDouble() : 0;
        double top = bboxNode.has("top") ? bboxNode.get("top").asDouble() : 0;
        double width = bboxNode.has("width") ? bboxNode.get("width").asDouble() : 0;
        double height = bboxNode.has("height") ? bboxNode.get("height").asDouble() : 0;

        // Convert from TOPLEFT to BOTTOMLEFT origin
        // In TOPLEFT: top is distance from top of page
        // In BOTTOMLEFT: bottom is distance from bottom of page
        double right = left + width;
        double bottomY = pageHeight - top - height;  // Convert top distance to bottom coordinate
        double topY = pageHeight - top;               // Convert top distance to top coordinate

        return new BoundingBox(pageIndex, left, bottomY, right, topY);
    }

    /**
     * Gets a text value from a JSON node.
     */
    private String getTextValue(JsonNode node, String fieldName) {
        if (node != null && node.has(fieldName)) {
            JsonNode field = node.get(fieldName);
            if (field.isTextual()) {
                return field.asText();
            }
        }
        return null;
    }

    /**
     * Sorts page contents by reading order (top to bottom, left to right).
     */
    private void sortByReadingOrder(List<IObject> contents) {
        contents.sort(new Comparator<IObject>() {
            @Override
            public int compare(IObject o1, IObject o2) {
                // Sort by top Y (descending - higher on page first)
                double topDiff = o2.getTopY() - o1.getTopY();
                if (Math.abs(topDiff) > 5.0) {  // Use tolerance for same-line detection
                    return topDiff > 0 ? 1 : -1;
                }
                // Same line, sort by left X (ascending)
                return Double.compare(o1.getLeftX(), o2.getLeftX());
            }
        });
    }
}

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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transforms Docling JSON output to OpenDataLoader IObject hierarchy.
 *
 * <p>This transformer handles the DoclingDocument JSON format and converts
 * its elements (texts, tables, pictures) to the equivalent IObject types
 * used by OpenDataLoader's downstream processors and generators.
 *
 * <h2>Schema Mapping</h2>
 * <ul>
 *   <li>texts (label: text) → SemanticParagraph</li>
 *   <li>texts (label: section_header) → SemanticHeading</li>
 *   <li>texts (label: caption, footnote) → SemanticParagraph</li>
 *   <li>texts (label: page_header, page_footer) → Filtered out (furniture)</li>
 *   <li>tables → TableBorder with rows and cells</li>
 *   <li>pictures → SemanticPicture (with optional description)</li>
 * </ul>
 *
 * <h2>Coordinate System</h2>
 * <p>Both Docling and OpenDataLoader use BOTTOMLEFT origin. Docling provides
 * bbox as {l, t, r, b} while OpenDataLoader uses [left, bottom, right, top].
 * When Docling uses TOPLEFT origin, coordinates are converted appropriately.
 */
public class DoclingSchemaTransformer implements HybridSchemaTransformer {

    private static final Logger LOGGER = Logger.getLogger(DoclingSchemaTransformer.class.getCanonicalName());

    private static final String BACKEND_TYPE = "docling";

    // Picture index counter (reset per transform call)
    private int pictureIndex;

    // Docling text labels
    private static final String LABEL_TEXT = "text";
    private static final String LABEL_SECTION_HEADER = "section_header";
    private static final String LABEL_CAPTION = "caption";
    private static final String LABEL_FOOTNOTE = "footnote";
    private static final String LABEL_PAGE_HEADER = "page_header";
    private static final String LABEL_PAGE_FOOTER = "page_footer";
    private static final String LABEL_LIST_ITEM = "list_item";
    private static final String LABEL_FORMULA = "formula";

    // Docling coordinate origins
    private static final String COORD_ORIGIN_BOTTOMLEFT = "BOTTOMLEFT";
    private static final String COORD_ORIGIN_TOPLEFT = "TOPLEFT";

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

        // Determine number of pages from page info or content
        int numPages = determinePageCount(json, pageHeights);

        // Initialize result list
        List<List<IObject>> result = new ArrayList<>(numPages);
        for (int i = 0; i < numPages; i++) {
            result.add(new ArrayList<>());
        }

        // Transform texts
        JsonNode texts = json.get("texts");
        if (texts != null && texts.isArray()) {
            for (JsonNode textNode : texts) {
                transformText(textNode, result, pageHeights);
            }
        }

        // Transform tables
        JsonNode tables = json.get("tables");
        if (tables != null && tables.isArray()) {
            for (JsonNode tableNode : tables) {
                transformTable(tableNode, result, pageHeights);
            }
        }

        // Transform pictures
        JsonNode pictures = json.get("pictures");
        if (pictures != null && pictures.isArray()) {
            for (JsonNode pictureNode : pictures) {
                transformPicture(pictureNode, result, pageHeights);
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

        // Find the page in the result
        int pageIndex = pageNumber - 1;
        if (pageIndex >= 0 && pageIndex < result.size()) {
            return result.get(pageIndex);
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

        // Check pages array in JSON
        JsonNode pages = json.get("pages");
        if (pages != null && pages.isArray()) {
            return pages.size();
        }

        // Check page_dimensions or similar
        JsonNode pageDimensions = json.get("page_dimensions");
        if (pageDimensions != null && pageDimensions.isObject()) {
            int maxPage = 0;
            Iterator<String> fieldNames = pageDimensions.fieldNames();
            while (fieldNames.hasNext()) {
                try {
                    int pageNum = Integer.parseInt(fieldNames.next());
                    maxPage = Math.max(maxPage, pageNum);
                } catch (NumberFormatException e) {
                    // ignore
                }
            }
            return maxPage;
        }

        // Default to scanning content
        return scanContentForPageCount(json);
    }

    /**
     * Scans content elements to determine page count.
     */
    private int scanContentForPageCount(JsonNode json) {
        int maxPage = 0;

        JsonNode texts = json.get("texts");
        if (texts != null && texts.isArray()) {
            for (JsonNode text : texts) {
                maxPage = Math.max(maxPage, getPageNumberFromProv(text));
            }
        }

        JsonNode tables = json.get("tables");
        if (tables != null && tables.isArray()) {
            for (JsonNode table : tables) {
                maxPage = Math.max(maxPage, getPageNumberFromProv(table));
            }
        }

        return maxPage;
    }

    /**
     * Extracts page number from provenance info.
     */
    private int getPageNumberFromProv(JsonNode node) {
        JsonNode prov = node.get("prov");
        if (prov != null && prov.isArray() && prov.size() > 0) {
            JsonNode firstProv = prov.get(0);
            JsonNode pageNo = firstProv.get("page_no");
            if (pageNo != null && pageNo.isInt()) {
                return pageNo.asInt();
            }
        }
        return 0;
    }

    /**
     * Transforms a Docling text element to an IObject.
     */
    private void transformText(JsonNode textNode, List<List<IObject>> result, Map<Integer, Double> pageHeights) {
        String label = getTextValue(textNode, "label");

        // Skip furniture elements (page headers/footers)
        if (LABEL_PAGE_HEADER.equals(label) || LABEL_PAGE_FOOTER.equals(label)) {
            return;
        }

        // Get provenance for position info
        JsonNode prov = textNode.get("prov");
        if (prov == null || !prov.isArray() || prov.size() == 0) {
            LOGGER.log(Level.FINE, "Text element missing provenance, skipping");
            return;
        }

        JsonNode firstProv = prov.get(0);
        int pageNo = firstProv.has("page_no") ? firstProv.get("page_no").asInt() : 1;
        int pageIndex = pageNo - 1;

        // Ensure result list is large enough
        while (result.size() <= pageIndex) {
            result.add(new ArrayList<>());
        }

        // Get bounding box
        BoundingBox bbox = extractBoundingBox(firstProv.get("bbox"), pageIndex, pageHeights.get(pageNo));

        // Get text content
        String text = getTextValue(textNode, "text");
        if (text == null || text.isEmpty()) {
            text = getTextValue(textNode, "orig");
        }

        // Create appropriate IObject based on label
        IObject object;
        if (LABEL_SECTION_HEADER.equals(label)) {
            object = createHeading(text, bbox, textNode);
        } else if (LABEL_FORMULA.equals(label)) {
            object = createFormula(text, bbox);
        } else {
            object = createParagraph(text, bbox);
        }

        if (object != null) {
            result.get(pageIndex).add(object);
        }
    }

    /**
     * Creates a SemanticHeading from Docling section_header.
     */
    private SemanticHeading createHeading(String text, BoundingBox bbox, JsonNode textNode) {
        int level = 1; // Default level

        // Try to extract level from node metadata
        JsonNode meta = textNode.get("meta");
        if (meta != null && meta.has("level")) {
            level = meta.get("level").asInt(1);
        }

        // Create a text chunk and wrap in TextLine
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        // Create heading using default constructor and add content
        SemanticHeading heading = new SemanticHeading();
        heading.add(textLine);
        heading.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        heading.setHeadingLevel(level);

        return heading;
    }

    /**
     * Creates a SemanticParagraph from Docling text element.
     */
    private SemanticParagraph createParagraph(String text, BoundingBox bbox) {
        // Create a text chunk and wrap in TextLine
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        // Create paragraph using default constructor and add content
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(textLine);
        paragraph.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());

        return paragraph;
    }

    /**
     * Creates a SemanticFormula from Docling formula element.
     *
     * @param latex The LaTeX representation of the formula
     * @param bbox  The bounding box
     * @return A SemanticFormula object
     */
    private SemanticFormula createFormula(String latex, BoundingBox bbox) {
        SemanticFormula formula = new SemanticFormula(bbox, latex);
        formula.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return formula;
    }

    /**
     * Transforms a Docling picture element to a SemanticPicture.
     */
    private void transformPicture(JsonNode pictureNode, List<List<IObject>> result, Map<Integer, Double> pageHeights) {
        // Get provenance for position info
        JsonNode prov = pictureNode.get("prov");
        if (prov == null || !prov.isArray() || prov.size() == 0) {
            LOGGER.log(Level.FINE, "Picture element missing provenance, skipping");
            return;
        }

        JsonNode firstProv = prov.get(0);
        int pageNo = firstProv.has("page_no") ? firstProv.get("page_no").asInt() : 1;
        int pageIndex = pageNo - 1;

        // Ensure result list is large enough
        while (result.size() <= pageIndex) {
            result.add(new ArrayList<>());
        }

        // Get bounding box
        BoundingBox bbox = extractBoundingBox(firstProv.get("bbox"), pageIndex, pageHeights.get(pageNo));

        // Extract description from annotations (if available)
        String description = extractPictureDescription(pictureNode);

        // Create SemanticPicture with description
        SemanticPicture picture = new SemanticPicture(bbox, ++pictureIndex, description);
        picture.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());

        result.get(pageIndex).add(picture);
    }

    /**
     * Extracts picture description from annotations array.
     *
     * <p>Docling stores picture descriptions in the annotations array with kind="description".
     *
     * @param pictureNode The picture JSON node
     * @return The description text, or null if not available
     */
    private String extractPictureDescription(JsonNode pictureNode) {
        JsonNode annotations = pictureNode.get("annotations");
        if (annotations != null && annotations.isArray()) {
            for (JsonNode annotation : annotations) {
                String kind = getTextValue(annotation, "kind");
                if ("description".equals(kind)) {
                    return getTextValue(annotation, "text");
                }
            }
        }
        return null;
    }

    /**
     * Transforms a Docling table element to a TableBorder.
     */
    private void transformTable(JsonNode tableNode, List<List<IObject>> result, Map<Integer, Double> pageHeights) {
        // Get provenance for position info
        JsonNode prov = tableNode.get("prov");
        if (prov == null || !prov.isArray() || prov.size() == 0) {
            LOGGER.log(Level.FINE, "Table element missing provenance, skipping");
            return;
        }

        JsonNode firstProv = prov.get(0);
        int pageNo = firstProv.has("page_no") ? firstProv.get("page_no").asInt() : 1;
        int pageIndex = pageNo - 1;

        // Ensure result list is large enough
        while (result.size() <= pageIndex) {
            result.add(new ArrayList<>());
        }

        // Get table data
        JsonNode data = tableNode.get("data");
        if (data == null) {
            LOGGER.log(Level.FINE, "Table element missing data, skipping");
            return;
        }

        // Get grid dimensions
        JsonNode gridNode = data.get("grid");
        if (gridNode == null || !gridNode.isArray()) {
            LOGGER.log(Level.FINE, "Table missing grid data, skipping");
            return;
        }

        int numRows = gridNode.size();
        int numCols = 0;
        if (numRows > 0 && gridNode.get(0).isArray()) {
            numCols = gridNode.get(0).size();
        }

        if (numRows == 0 || numCols == 0) {
            return;
        }

        // Get table bounding box
        BoundingBox tableBbox = extractBoundingBox(firstProv.get("bbox"), pageIndex, pageHeights.get(pageNo));

        // Create TableBorder
        TableBorder table = new TableBorder(numRows, numCols);
        table.setBoundingBox(tableBbox);
        table.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());

        // Get table cells from data
        JsonNode tableCells = data.get("table_cells");
        Map<String, JsonNode> cellMap = new HashMap<>();
        if (tableCells != null && tableCells.isArray()) {
            for (JsonNode cell : tableCells) {
                int startRow = cell.has("start_row_offset_idx") ? cell.get("start_row_offset_idx").asInt() : 0;
                int startCol = cell.has("start_col_offset_idx") ? cell.get("start_col_offset_idx").asInt() : 0;
                String key = startRow + "," + startCol;
                cellMap.put(key, cell);
            }
        }

        // Build table structure
        double rowHeight = (tableBbox.getTopY() - tableBbox.getBottomY()) / numRows;
        double colWidth = (tableBbox.getRightX() - tableBbox.getLeftX()) / numCols;

        for (int row = 0; row < numRows; row++) {
            TableBorderRow borderRow = new TableBorderRow(row, numCols, 0L);
            double rowTop = tableBbox.getTopY() - (row * rowHeight);
            double rowBottom = rowTop - rowHeight;
            borderRow.setBoundingBox(new BoundingBox(pageIndex, tableBbox.getLeftX(), rowBottom, tableBbox.getRightX(), rowTop));

            for (int col = 0; col < numCols; col++) {
                String key = row + "," + col;
                JsonNode cellNode = cellMap.get(key);

                int rowSpan = 1;
                int colSpan = 1;
                String cellText = "";

                if (cellNode != null) {
                    rowSpan = cellNode.has("row_span") ? cellNode.get("row_span").asInt(1) : 1;
                    colSpan = cellNode.has("col_span") ? cellNode.get("col_span").asInt(1) : 1;
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

        result.get(pageIndex).add(table);
    }

    /**
     * Extracts a BoundingBox from Docling bbox JSON.
     *
     * @param bboxNode   The bbox JSON node with l, t, r, b, coord_origin fields
     * @param pageIndex  The 0-indexed page number
     * @param pageHeight The page height for coordinate transformation
     * @return A BoundingBox in OpenDataLoader format
     */
    private BoundingBox extractBoundingBox(JsonNode bboxNode, int pageIndex, Double pageHeight) {
        if (bboxNode == null) {
            return new BoundingBox(pageIndex, 0, 0, 0, 0);
        }

        double l = bboxNode.has("l") ? bboxNode.get("l").asDouble() : 0;
        double t = bboxNode.has("t") ? bboxNode.get("t").asDouble() : 0;
        double r = bboxNode.has("r") ? bboxNode.get("r").asDouble() : 0;
        double b = bboxNode.has("b") ? bboxNode.get("b").asDouble() : 0;

        String coordOrigin = bboxNode.has("coord_origin") ?
            bboxNode.get("coord_origin").asText() : COORD_ORIGIN_BOTTOMLEFT;

        double left, bottom, right, top;

        if (COORD_ORIGIN_TOPLEFT.equals(coordOrigin) && pageHeight != null) {
            // Convert from TOPLEFT to BOTTOMLEFT
            // In TOPLEFT: t is distance from top, b is distance from top (t < b since t is higher)
            // In BOTTOMLEFT: bottom is distance from bottom, top is distance from bottom
            left = l;
            right = r;
            top = pageHeight - t;  // t was distance from top
            bottom = pageHeight - b;  // b was distance from top
        } else {
            // BOTTOMLEFT origin - Docling uses {l, t, r, b} where t=top, b=bottom
            left = l;
            bottom = b;
            right = r;
            top = t;
        }

        return new BoundingBox(pageIndex, left, bottom, right, top);
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

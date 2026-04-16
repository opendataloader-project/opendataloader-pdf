/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transforms Hancom AI HOCR SDK API responses into IObject hierarchy.
 *
 * <p>The merged JSON from {@link HancomAIClient} contains results from three modules:
 * <ul>
 *   <li>{@code DOCUMENT_LAYOUT_WITH_OCR} — objects with label + bbox + ocrtext</li>
 *   <li>{@code TABLE_STRUCTURE_RECOGNITION} — table cells with html + bbox</li>
 *   <li>{@code IMAGE_CAPTIONING} — per-page captions</li>
 * </ul>
 *
 * <h2>Label Mapping (DLA integer labels → IObject types)</h2>
 * <pre>
 *   0  → Title (SemanticHeading level 1)
 *   1  → Heading (SemanticHeading level 2)
 *   2  → Paragraph (SemanticParagraph)
 *   3  → List item (SemanticParagraph)
 *   4  → Subheading (SemanticHeading level 3)
 *   6  → Author/Meta (SemanticParagraph)
 *   7  → Table region (handled via TABLE_STRUCTURE_RECOGNITION)
 *   9  → Figure (SemanticPicture)
 *  10  → Figure with caption (SemanticPicture)
 *  11  → Caption (SemanticParagraph)
 *  12  → Formula (SemanticFormula)
 *  13  → Footnote (SemanticParagraph)
 *  14  → Page header (filtered)
 *  15  → Page footer (filtered)
 *  17  → Page number (filtered)
 * </pre>
 *
 * <h2>Coordinate System</h2>
 * <p>Hancom AI uses TOPLEFT origin with [left, top, right, bottom] pixel coordinates.
 * OpenDataLoader uses BOTTOMLEFT origin in PDF points. Conversion requires page height
 * and DPI scaling (API renders at 300 DPI → 1 pixel = 72/300 points).
 */
public class HancomAISchemaTransformer implements HybridSchemaTransformer {

    private static final Logger LOGGER = Logger.getLogger(HancomAISchemaTransformer.class.getCanonicalName());

    private static final String BACKEND_TYPE = "hancom-ai";

    // DLA label constants
    private static final int LABEL_TITLE = 0;
    private static final int LABEL_HEADING = 1;
    private static final int LABEL_PARAGRAPH = 2;
    private static final int LABEL_LIST_ITEM = 3;
    private static final int LABEL_SUBHEADING = 4;
    private static final int LABEL_AUTHOR = 6;
    private static final int LABEL_TABLE = 7;
    private static final int LABEL_FIGURE = 9;
    private static final int LABEL_FIGURE_CAPTION = 10;
    private static final int LABEL_CAPTION = 11;
    private static final int LABEL_FORMULA = 12;
    private static final int LABEL_FOOTNOTE = 13;
    private static final int LABEL_PAGE_HEADER = 14;
    private static final int LABEL_PAGE_FOOTER = 15;
    private static final int LABEL_PAGE_NUMBER = 17;

    // DPI conversion: API renders at 300 DPI, PDF uses 72 DPI
    private static final double PIXEL_TO_POINT = 72.0 / 300.0;

    private int pictureIndex;

    @Override
    public String getBackendType() {
        return BACKEND_TYPE;
    }

    @Override
    public List<List<IObject>> transform(HybridResponse response, Map<Integer, Double> pageHeights) {
        JsonNode json = response.getJson();
        if (json == null) {
            LOGGER.log(Level.WARNING, "HybridResponse JSON is null");
            return Collections.emptyList();
        }

        pictureIndex = 0;

        // Get DLA+OCR results (primary source for layout + text)
        JsonNode dlaOcr = json.get("DOCUMENT_LAYOUT_WITH_OCR");
        JsonNode tables = json.get("TABLE_STRUCTURE_RECOGNITION");
        JsonNode figureCaptions = json.get("FIGURE_CAPTIONS");

        // Determine page count from DLA results
        List<JsonNode> dlaPages = extractPages(dlaOcr);
        int numPages = Math.max(dlaPages.size(), pageHeights != null ?
            pageHeights.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) : 0);

        // Initialize result
        List<List<IObject>> result = new ArrayList<>(numPages);
        for (int i = 0; i < numPages; i++) {
            result.add(new ArrayList<>());
        }

        // Build per-figure caption lookup: "pageNum:objectId" → caption
        Map<String, String> figureCaptionMap = buildFigureCaptionMap(figureCaptions);

        // Map table structures by page number
        Map<Integer, JsonNode> tableByPage = extractTablePages(tables);

        // Collect all heading heights (label 1 and 4) across all pages for level inference
        Map<Double, Integer> headingHeightToLevel = buildHeadingHeightToLevelMap(dlaPages);

        // Process DLA+OCR objects
        for (int i = 0; i < dlaPages.size(); i++) {
            JsonNode page = dlaPages.get(i);
            int pageNumber = page.has("page_number") ? page.get("page_number").asInt() : i;
            if (pageNumber >= result.size()) {
                while (result.size() <= pageNumber) result.add(new ArrayList<>());
            }

            double pageHeight = getPageHeight(pageNumber, pageHeights, page);

            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            for (JsonNode obj : objects) {
                IObject iobj = transformObject(obj, pageNumber, pageHeight, figureCaptionMap,
                    headingHeightToLevel);

                if (iobj != null) {
                    result.get(pageNumber).add(iobj);
                }
            }

            // Add table from TABLE_STRUCTURE_RECOGNITION if available
            JsonNode tablePage = tableByPage.get(pageNumber);
            if (tablePage != null && tablePage.has("num_cells") && tablePage.get("num_cells").asInt() > 0) {
                IObject table = transformTablePage(tablePage, pageNumber, pageHeight);
                if (table != null) {
                    result.get(pageNumber).add(table);
                }
            }
        }

        // Sort each page by reading order (top to bottom)
        for (List<IObject> pageContents : result) {
            pageContents.sort((a, b) -> {
                double aTop = a.getBoundingBox() != null ? -a.getBoundingBox().getTopY() : 0;
                double bTop = b.getBoundingBox() != null ? -b.getBoundingBox().getTopY() : 0;
                int cmp = Double.compare(aTop, bTop);
                if (cmp != 0) return cmp;
                double aLeft = a.getBoundingBox() != null ? a.getBoundingBox().getLeftX() : 0;
                double bLeft = b.getBoundingBox() != null ? b.getBoundingBox().getLeftX() : 0;
                return Double.compare(aLeft, bLeft);
            });
        }

        return result;
    }

    @Override
    public List<IObject> transformPage(int pageNumber, JsonNode pageContent, double pageHeight) {
        Map<Integer, Double> heights = new HashMap<>();
        heights.put(pageNumber, pageHeight);
        HybridResponse wrapper = new HybridResponse("", pageContent, Collections.emptyMap());
        List<List<IObject>> all = transform(wrapper, heights);
        int idx = pageNumber - 1;
        if (idx >= 0 && idx < all.size()) return all.get(idx);
        return all.isEmpty() ? Collections.emptyList() : all.get(0);
    }

    /**
     * Transforms a single DLA object to an IObject based on its label.
     */
    private IObject transformObject(JsonNode obj, int pageIndex, double pageHeight,
                                     Map<String, String> figureCaptionMap,
                                     Map<Double, Integer> headingHeightToLevel) {
        int label = obj.has("label") ? obj.get("label").asInt() : -1;

        // Skip furniture
        if (label == LABEL_PAGE_HEADER || label == LABEL_PAGE_FOOTER || label == LABEL_PAGE_NUMBER) {
            return null;
        }

        JsonNode bboxNode = obj.get("bbox");
        if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() < 4) {
            return null;
        }

        BoundingBox bbox = extractBoundingBox(bboxNode, pageIndex, pageHeight);
        String text = obj.has("ocrtext") ? obj.get("ocrtext").asText("") : "";

        switch (label) {
            case LABEL_TITLE:
                return createHeading(text, bbox, 1);
            case LABEL_HEADING:
            case LABEL_SUBHEADING: {
                double pixelHeight = bboxNode.get(3).asDouble() - bboxNode.get(1).asDouble();
                int level = headingHeightToLevel.getOrDefault(pixelHeight, 2);
                return createHeading(text, bbox, level);
            }

            case LABEL_PARAGRAPH:
            case LABEL_LIST_ITEM:
            case LABEL_AUTHOR:
            case LABEL_FOOTNOTE:
            case LABEL_CAPTION:
                return text.isEmpty() ? null : createParagraph(text, bbox);

            case LABEL_TABLE:
                // Table region detected by DLA — treat as paragraph with table-like text
                return text.isEmpty() ? null : createParagraph(text, bbox);

            case LABEL_FIGURE:
            case LABEL_FIGURE_CAPTION: {
                // Look up per-figure caption from IMAGE_CAPTIONING
                int objectId = obj.has("object_id") ? obj.get("object_id").asInt() : -1;
                String key = pageIndex + ":" + objectId;
                String caption = figureCaptionMap.get(key);
                return createPicture(bbox, caption);
            }

            case LABEL_FORMULA:
                return createFormula(text, bbox);

            default:
                return text.isEmpty() ? null : createParagraph(text, bbox);
        }
    }

    /**
     * Transforms a TABLE_STRUCTURE_RECOGNITION page result to a TableBorder.
     */
    private IObject transformTablePage(JsonNode tablePage, int pageIndex, double pageHeight) {
        JsonNode cellsNode = tablePage.get("cells");
        if (cellsNode == null || !cellsNode.isArray() || cellsNode.size() == 0) {
            return null;
        }

        JsonNode tableBboxNode = tablePage.get("table_bbox");
        BoundingBox tableBbox;
        if (tableBboxNode != null && tableBboxNode.isArray() && tableBboxNode.size() >= 4) {
            tableBbox = extractBoundingBox(tableBboxNode, pageIndex, pageHeight);
        } else {
            return null;
        }

        // Determine dimensions
        int numRows = 0;
        int numCols = 0;
        for (JsonNode cell : cellsNode) {
            int row = cell.has("row") ? cell.get("row").asInt() : 0;
            int col = cell.has("col") ? cell.get("col").asInt() : 0;
            int rowspan = cell.has("rowspan") ? cell.get("rowspan").asInt() : 1;
            int colspan = cell.has("colspan") ? cell.get("colspan").asInt() : 1;
            numRows = Math.max(numRows, row + rowspan);
            numCols = Math.max(numCols, col + colspan);
        }

        if (numRows == 0 || numCols == 0) return null;

        TableBorder table = new TableBorder(numRows, numCols);
        table.setBoundingBox(tableBbox);
        table.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());

        double rowHeight = (tableBbox.getTopY() - tableBbox.getBottomY()) / numRows;
        double colWidth = (tableBbox.getRightX() - tableBbox.getLeftX()) / numCols;

        // Initialize rows
        for (int r = 0; r < numRows; r++) {
            double rowTop = tableBbox.getTopY() - (r * rowHeight);
            double rowBottom = rowTop - rowHeight;
            TableBorderRow borderRow = new TableBorderRow(r, numCols, 0L);
            borderRow.setBoundingBox(new BoundingBox(pageIndex,
                tableBbox.getLeftX(), rowBottom, tableBbox.getRightX(), rowTop));
            table.getRows()[r] = borderRow;

            for (int c = 0; c < numCols; c++) {
                TableBorderCell emptyCell = new TableBorderCell(r, c, 1, 1, 0L);
                emptyCell.setBoundingBox(new BoundingBox(pageIndex,
                    tableBbox.getLeftX() + c * colWidth, rowBottom,
                    tableBbox.getLeftX() + (c + 1) * colWidth, rowTop));
                table.getRows()[r].getCells()[c] = emptyCell;
            }
        }

        // Fill cells from API
        for (JsonNode cellNode : cellsNode) {
            int row = cellNode.has("row") ? cellNode.get("row").asInt() : 0;
            int col = cellNode.has("col") ? cellNode.get("col").asInt() : 0;
            int rowspan = cellNode.has("rowspan") ? cellNode.get("rowspan").asInt() : 1;
            int colspan = cellNode.has("colspan") ? cellNode.get("colspan").asInt() : 1;
            String cellText = cellNode.has("text") ? cellNode.get("text").asText("") : "";

            if (row < numRows && col < numCols) {
                TableBorderCell cell = new TableBorderCell(row, col, rowspan, colspan, 0L);
                double cellLeft = tableBbox.getLeftX() + col * colWidth;
                double cellRight = cellLeft + colspan * colWidth;
                double cellTop = tableBbox.getTopY() - row * rowHeight;
                double cellBottom = cellTop - rowspan * rowHeight;
                cell.setBoundingBox(new BoundingBox(pageIndex, cellLeft, cellBottom, cellRight, cellTop));

                if (!cellText.isEmpty()) {
                    cell.addContentObject(createParagraph(cellText, cell.getBoundingBox()));
                }

                table.getRows()[row].getCells()[col] = cell;
            }
        }

        return table;
    }

    // --- Helper: heading level inference ---

    /**
     * Collects all label 1 (heading) and label 4 (subheading) bbox heights across all pages,
     * then assigns heading levels H2~H6 based on descending height order.
     *
     * <p>Taller bbox = bigger font = higher level (H2).
     * Shorter bbox = smaller font = lower level (H3, H4, ...).
     * Same height = same level. Capped at H6.
     * Single unique height defaults to H2.
     *
     * @return map from pixel height to heading level (2~6)
     */
    private Map<Double, Integer> buildHeadingHeightToLevelMap(List<JsonNode> dlaPages) {
        // Collect unique heights using TreeSet (natural descending order via reverseOrder)
        TreeSet<Double> uniqueHeights = new TreeSet<>(Collections.reverseOrder());

        for (JsonNode page : dlaPages) {
            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            for (JsonNode obj : objects) {
                int label = obj.has("label") ? obj.get("label").asInt() : -1;
                if (label != LABEL_HEADING && label != LABEL_SUBHEADING) continue;

                JsonNode bboxNode = obj.get("bbox");
                if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() < 4) continue;

                double pixelHeight = bboxNode.get(3).asDouble() - bboxNode.get(1).asDouble();
                if (pixelHeight > 0) {
                    uniqueHeights.add(pixelHeight);
                }
            }
        }

        // Assign levels: tallest → H2, next → H3, ... capped at H6
        Map<Double, Integer> heightToLevel = new HashMap<>();
        int level = 2;
        for (Double height : uniqueHeights) {
            heightToLevel.put(height, Math.min(level, 6));
            level++;
        }

        return heightToLevel;
    }

    // --- Helper: extract pages from RESULT ---

    private List<JsonNode> extractPages(JsonNode moduleResult) {
        List<JsonNode> pages = new ArrayList<>();
        if (moduleResult == null || !moduleResult.isArray()) return pages;

        // RESULT is [[page0, page1, ...]] (nested array)
        JsonNode inner = moduleResult.size() > 0 && moduleResult.get(0).isArray()
            ? moduleResult.get(0) : moduleResult;

        for (JsonNode page : inner) {
            if (page.isObject()) {
                pages.add(page);
            }
        }
        return pages;
    }

    /**
     * Builds a lookup map from FIGURE_CAPTIONS: "pageNumber:objectId" → caption text.
     */
    private Map<String, String> buildFigureCaptionMap(JsonNode figureCaptions) {
        Map<String, String> map = new HashMap<>();
        if (figureCaptions == null || !figureCaptions.isArray()) return map;

        for (JsonNode cap : figureCaptions) {
            int pageNum = cap.has("page_number") ? cap.get("page_number").asInt() : -1;
            int objectId = cap.has("object_id") ? cap.get("object_id").asInt() : -1;
            String caption = cap.has("caption") ? cap.get("caption").asText("") : "";
            if (pageNum >= 0 && objectId >= 0 && !caption.isEmpty()) {
                map.put(pageNum + ":" + objectId, caption);
            }
        }
        return map;
    }

    private Map<Integer, JsonNode> extractTablePages(JsonNode tableResult) {
        Map<Integer, JsonNode> map = new HashMap<>();
        if (tableResult == null) return map;

        for (JsonNode page : extractPages(tableResult)) {
            int pageNum = page.has("page_number") ? page.get("page_number").asInt() : -1;
            if (pageNum >= 0) {
                map.put(pageNum, page);
            }
        }
        return map;
    }

    // --- Helper: page height ---

    private double getPageHeight(int pageNumber, Map<Integer, Double> pageHeights, JsonNode page) {
        // pageHeights uses 1-indexed keys
        Double h = pageHeights != null ? pageHeights.get(pageNumber + 1) : null;
        if (h != null) return h;

        // Derive from image dimensions (API renders at 300 DPI)
        if (page.has("image_height")) {
            return page.get("image_height").asDouble() * PIXEL_TO_POINT;
        }
        return 842.0; // A4 default
    }

    // --- Helper: coordinate conversion ---

    /**
     * Converts from Hancom AI pixel coordinates [left, top, right, bottom] (TOPLEFT origin, 300 DPI)
     * to OpenDataLoader PDF points (BOTTOMLEFT origin, 72 DPI).
     */
    private BoundingBox extractBoundingBox(JsonNode bboxNode, int pageIndex, double pageHeight) {
        double pixLeft = bboxNode.get(0).asDouble();
        double pixTop = bboxNode.get(1).asDouble();
        double pixRight = bboxNode.get(2).asDouble();
        double pixBottom = bboxNode.get(3).asDouble();

        // Pixel → PDF points
        double left = pixLeft * PIXEL_TO_POINT;
        double right = pixRight * PIXEL_TO_POINT;

        // TOPLEFT → BOTTOMLEFT
        double topY = pageHeight - (pixTop * PIXEL_TO_POINT);
        double bottomY = pageHeight - (pixBottom * PIXEL_TO_POINT);

        return new BoundingBox(pageIndex, left, bottomY, right, topY);
    }

    // --- Helper: IObject creation ---

    private SemanticParagraph createParagraph(String text, BoundingBox bbox) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(textLine);
        paragraph.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        paragraph.setCorrectSemanticScore(1.0);
        return paragraph;
    }

    private SemanticHeading createHeading(String text, BoundingBox bbox, int level) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        SemanticHeading heading = new SemanticHeading();
        heading.add(textLine);
        heading.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        heading.setHeadingLevel(level);
        heading.setCorrectSemanticScore(1.0);
        return heading;
    }

    private SemanticPicture createPicture(BoundingBox bbox, String caption) {
        SemanticPicture picture = new SemanticPicture(bbox, ++pictureIndex, caption);
        picture.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return picture;
    }

    private SemanticFormula createFormula(String text, BoundingBox bbox) {
        SemanticFormula formula = new SemanticFormula(bbox, text);
        formula.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return formula;
    }
}

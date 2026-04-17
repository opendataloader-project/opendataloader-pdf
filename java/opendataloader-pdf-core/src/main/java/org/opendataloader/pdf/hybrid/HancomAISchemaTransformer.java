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
import org.opendataloader.pdf.entities.SemanticFootnote;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticNode;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 *   7  → Regionlist (Table/List region, handled via TABLE_STRUCTURE_RECOGNITION or as list)
 *   8  → TableName (SemanticCaption linked to nearest Table)
 *   9  → Table (handled via TABLE_STRUCTURE_RECOGNITION)
 *  10  → Figure (SemanticPicture)
 *  11  → FigureName (SemanticCaption linked to nearest Figure)
 *  12  → Formula (SemanticFormula)
 *  13  → Footnote (SemanticFootnote → FENote)
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
    private static final int LABEL_REGIONLIST = 7;
    private static final int LABEL_TABLE_NAME = 8;
    private static final int LABEL_TABLE = 9;
    private static final int LABEL_FIGURE = 10;
    private static final int LABEL_FIGURE_NAME = 11;
    private static final int LABEL_FORMULA = 12;
    private static final int LABEL_FOOTNOTE = 13;
    private static final int LABEL_PAGE_HEADER = 14;
    private static final int LABEL_PAGE_FOOTER = 15;
    private static final int LABEL_PAGE_NUMBER = 17;

    // DPI conversion: API renders at 300 DPI, PDF uses 72 DPI
    private static final double PIXEL_TO_POINT = 72.0 / 300.0;

    // Minimum intersection-over-word-area ratio for bbox matching
    private static final double WORD_CELL_OVERLAP_THRESHOLD = 0.5;

    // Bullet/number prefix pattern for list items
    private static final Pattern BULLET_PREFIX_PATTERN = Pattern.compile(
        "^(\u2022|\u2013|\u2014|-|\\d+[.)::]|[a-zA-Z][.):])(\\s+)");

    /**
     * Holds text and bounding box for a DLA+OCR word-level object.
     */
    private static class WordInfo {
        final String text;
        final BoundingBox bbox;

        WordInfo(String text, BoundingBox bbox) {
            this.text = text;
            this.bbox = bbox;
        }
    }

    private int pictureIndex;
    private String regionlistStrategy = HybridConfig.REGIONLIST_TABLE_FIRST;
    private Map<Long, ElementMetadata> elementMetadataMap = new LinkedHashMap<>();

    @Override
    public Map<Long, ElementMetadata> getElementMetadata() {
        return Collections.unmodifiableMap(elementMetadataMap);
    }

    /**
     * Sets the regionlist strategy for label 7 handling.
     *
     * @param regionlistStrategy the strategy ("table-first" or "list-only")
     */
    public void setRegionlistStrategy(String regionlistStrategy) {
        this.regionlistStrategy = regionlistStrategy;
    }

    /**
     * Gets the regionlist strategy.
     *
     * @return the current regionlist strategy
     */
    public String getRegionlistStrategy() {
        return regionlistStrategy;
    }

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
        elementMetadataMap.clear();

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

        // Build map: page_number -> list of TSR table entries
        // New format: TSR is an ArrayNode of per-table entries with dla_bbox + tsr sub-object
        Map<Integer, List<JsonNode>> tablesByPage = new LinkedHashMap<>();
        JsonNode tsrArray = json.get("TABLE_STRUCTURE_RECOGNITION");
        if (tsrArray != null && tsrArray.isArray()) {
            // Check if this is new format (array of table entries with "tsr" sub-object)
            // or old format (nested array of pages with "cells" directly)
            boolean isNewFormat = false;
            if (tsrArray.size() > 0) {
                JsonNode first = tsrArray.get(0);
                isNewFormat = first.isObject() && (first.has("tsr") || first.has("dla_bbox"));
            }

            if (isNewFormat) {
                for (JsonNode entry : tsrArray) {
                    int pageNum = entry.has("page_number") ? entry.get("page_number").asInt() : -1;
                    if (pageNum >= 0) {
                        tablesByPage.computeIfAbsent(pageNum, k -> new ArrayList<>()).add(entry);
                    }
                }
            } else {
                // Legacy format: extract pages and wrap each in a synthetic entry
                Map<Integer, JsonNode> legacyTableByPage = extractTablePages(tsrArray);
                for (Map.Entry<Integer, JsonNode> e : legacyTableByPage.entrySet()) {
                    JsonNode legacyPage = e.getValue();
                    // Wrap legacy page as a table entry with tsr = the page itself
                    // and dla_bbox derived from table_bbox (page-level coords in legacy format)
                    com.fasterxml.jackson.databind.node.ObjectNode syntheticEntry =
                        new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
                    syntheticEntry.put("page_number", e.getKey());
                    JsonNode legacyTableBbox = legacyPage.get("table_bbox");
                    if (legacyTableBbox != null) {
                        syntheticEntry.set("dla_bbox", legacyTableBbox);
                    }
                    syntheticEntry.set("tsr", legacyPage);
                    tablesByPage.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(syntheticEntry);
                }
            }
        }

        // Collect all heading heights (label 1 and 4) across all pages for level inference
        Map<Double, Integer> headingHeightToLevel = buildHeadingHeightToLevelMap(dlaPages);

        // Collect per-page word info for cell-word bbox matching
        Map<Integer, List<WordInfo>> wordsByPage = collectWordsByPage(dlaPages, pageHeights);

        // Process DLA+OCR objects
        for (int i = 0; i < dlaPages.size(); i++) {
            JsonNode page = dlaPages.get(i);
            int pageNumber = page.has("page_number") ? page.get("page_number").asInt() : i;
            if (pageNumber >= result.size()) {
                while (result.size() <= pageNumber) result.add(new ArrayList<>());
            }

            double pageHeight = getPageHeight(pageNumber, pageHeights, page);

            // Collect TSR table bboxes for this page (used by label 7 overlap check)
            // Use dla_bbox (page-level coords) for overlap check, not tsr.table_bbox (crop-relative)
            List<BoundingBox> tsrTableBboxes = new ArrayList<>();
            List<JsonNode> pageTables = tablesByPage.getOrDefault(pageNumber, Collections.emptyList());
            for (JsonNode tableEntry : pageTables) {
                JsonNode dlaBbox = tableEntry.get("dla_bbox");
                if (dlaBbox != null && dlaBbox.isArray() && dlaBbox.size() >= 4) {
                    tsrTableBboxes.add(extractBoundingBox(dlaBbox, pageNumber, pageHeight));
                }
            }

            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            for (JsonNode obj : objects) {
                IObject iobj = transformObject(obj, pageNumber, pageHeight, figureCaptionMap,
                    headingHeightToLevel, tsrTableBboxes);

                if (iobj != null) {
                    result.get(pageNumber).add(iobj);
                }
            }

            // Add tables from TABLE_STRUCTURE_RECOGNITION if available
            List<WordInfo> pageWords = wordsByPage.getOrDefault(pageNumber, Collections.emptyList());
            for (JsonNode tableEntry : pageTables) {
                IObject table = transformTableEntry(tableEntry, pageNumber, pageHeight, pageWords);
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

        // Post-process: group consecutive ListItem objects into PDFList instances
        for (int i = 0; i < result.size(); i++) {
            result.set(i, groupListItems(result.get(i)));
        }

        // Post-process: link SemanticCaption objects to nearest Table/Picture
        for (List<IObject> pageContents : result) {
            linkCaptionsToFloats(pageContents);
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
                                     Map<Double, Integer> headingHeightToLevel,
                                     List<BoundingBox> tsrTableBboxes) {
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

        IObject iobj;
        switch (label) {
            case LABEL_TITLE:
                iobj = createHeading(text, bbox, 1);
                break;
            case LABEL_HEADING:
            case LABEL_SUBHEADING: {
                double pixelHeight = bboxNode.get(3).asDouble() - bboxNode.get(1).asDouble();
                int level = headingHeightToLevel.getOrDefault(pixelHeight, 2);
                iobj = createHeading(text, bbox, level);
                break;
            }

            case LABEL_LIST_ITEM:
                iobj = text.isEmpty() ? null : createListItem(text, bbox);
                break;

            case LABEL_TABLE_NAME:
            case LABEL_FIGURE_NAME:
                iobj = text.isEmpty() ? null : createCaption(text, bbox);
                break;

            case LABEL_FOOTNOTE:
                iobj = text.isEmpty() ? null : createFootnote(text, bbox);
                break;

            case LABEL_PARAGRAPH:
            case LABEL_AUTHOR:
                iobj = text.isEmpty() ? null : createParagraph(text, bbox);
                break;

            case LABEL_REGIONLIST:
                if (HybridConfig.REGIONLIST_LIST_ONLY.equals(regionlistStrategy)) {
                    // list-only: always treat as list, skip TSR check
                    iobj = text.isEmpty() ? null : createListFromText(text, bbox);
                } else {
                    // table-first (default): if TSR covers it, skip (table handled separately)
                    if (hasOverlappingTsr(bbox, tsrTableBboxes)) return null;
                    // No TSR data — treat as list (parse text by newlines into ListItems)
                    iobj = text.isEmpty() ? null : createListFromText(text, bbox);
                }
                break;

            case LABEL_TABLE:
                // Table regions are handled separately by transformTablePage() via TSR
                return null;

            case LABEL_FIGURE: {
                // Look up per-figure caption from IMAGE_CAPTIONING
                int objectId = obj.has("object_id") ? obj.get("object_id").asInt() : -1;
                String key = pageIndex + ":" + objectId;
                String caption = figureCaptionMap.get(key);
                iobj = createPicture(bbox, caption);
                break;
            }

            case LABEL_FORMULA:
                iobj = createFormula(text, bbox);
                break;

            default:
                iobj = text.isEmpty() ? null : createParagraph(text, bbox);
                break;
        }

        // Map confidence to correctSemanticScore (only for SemanticNode subtypes)
        if (iobj instanceof SemanticNode) {
            double confidence = obj.has("confidence") ? obj.get("confidence").asDouble(1.0) : 1.0;
            ((SemanticNode) iobj).setCorrectSemanticScore(confidence);
        }

        // Produce ElementMetadata for this IObject
        if (iobj != null && iobj.getRecognizedStructureId() != null) {
            double confidence = obj.has("confidence") ? obj.get("confidence").asDouble(1.0) : 1.0;
            ElementMetadata meta = new ElementMetadata()
                .setConfidence(confidence)
                .setSourceLabel(label);

            if (label == LABEL_HEADING || label == LABEL_SUBHEADING) {
                double pixelHeight = bboxNode.get(3).asDouble() - bboxNode.get(1).asDouble();
                meta.setHeadingInferenceMethod("bbox-height")
                    .setBboxHeightPx(pixelHeight);
            } else if (label == LABEL_TITLE) {
                meta.setHeadingInferenceMethod("fixed");
            } else if (label == LABEL_FIGURE) {
                int objectId = obj.has("object_id") ? obj.get("object_id").asInt() : -1;
                String key = pageIndex + ":" + objectId;
                String captionText = figureCaptionMap.get(key);
                if (captionText != null) {
                    ElementMetadata.CaptionMetadata capMeta = new ElementMetadata.CaptionMetadata();
                    capMeta.setText(captionText);
                    capMeta.setLanguage(detectLanguage(captionText));
                    meta.setCaption(capMeta);
                }
            } else if (label == LABEL_REGIONLIST) {
                ElementMetadata.RegionlistResolution rlRes = new ElementMetadata.RegionlistResolution();
                rlRes.setStrategy(regionlistStrategy);
                if (HybridConfig.REGIONLIST_LIST_ONLY.equals(regionlistStrategy)) {
                    rlRes.setTsrAttempted(false);
                } else {
                    rlRes.setTsrAttempted(true);
                    rlRes.setTsrResult(hasOverlappingTsr(bbox, tsrTableBboxes) ? "success" : "no-cells");
                }
                meta.setRegionlistResolution(rlRes);
            }

            elementMetadataMap.put(iobj.getRecognizedStructureId(), meta);
        }

        return iobj;
    }

    /**
     * Transforms a single TSR table entry to a TableBorder.
     * Accepts the new per-table entry format with {@code dla_bbox} (crop origin in page pixels)
     * and {@code tsr} sub-object containing cells with array-format rowspan/colspan.
     *
     * <p>Cell bboxes are relative to the crop image. They are converted to page-level coordinates
     * by adding the crop origin ({@code dla_bbox} left/top).
     *
     * @param tableEntry a TSR table entry with {@code dla_bbox} and {@code tsr} fields
     * @param pageWords DLA+OCR word-level objects from the page for bbox matching
     */
    private IObject transformTableEntry(JsonNode tableEntry, int pageIndex, double pageHeight,
                                         List<WordInfo> pageWords) {
        JsonNode tsr = tableEntry.get("tsr");
        if (tsr == null || tsr.isEmpty()) return null;

        JsonNode cellsNode = tsr.get("cells");
        if (cellsNode == null || !cellsNode.isArray() || cellsNode.size() == 0) {
            return null;
        }

        // DLA bbox = crop origin in page pixels (300DPI, top-left origin)
        JsonNode dlaBbox = tableEntry.get("dla_bbox");
        double cropOriginLeft = 0;
        double cropOriginTop = 0;
        if (dlaBbox != null && dlaBbox.isArray() && dlaBbox.size() >= 4) {
            cropOriginLeft = dlaBbox.get(0).asDouble();
            cropOriginTop = dlaBbox.get(1).asDouble();
        }

        // Compute table bbox in page coordinates from dla_bbox
        BoundingBox tableBbox;
        if (dlaBbox != null && dlaBbox.isArray() && dlaBbox.size() >= 4) {
            tableBbox = extractBoundingBox(dlaBbox, pageIndex, pageHeight);
        } else {
            // Fallback: use tsr.table_bbox offset by crop origin
            JsonNode tsrTableBbox = tsr.get("table_bbox");
            if (tsrTableBbox != null && tsrTableBbox.isArray() && tsrTableBbox.size() >= 4) {
                tableBbox = extractBoundingBox(tsrTableBbox, pageIndex, pageHeight);
            } else {
                return null;
            }
        }

        // Determine dimensions from array rowspan/colspan
        int numRows = 0;
        int numCols = 0;
        for (JsonNode cell : cellsNode) {
            JsonNode rs = cell.get("rowspan");
            JsonNode cs = cell.get("colspan");
            if (rs != null && rs.isArray()) {
                for (int i = 0; i < rs.size(); i++) {
                    numRows = Math.max(numRows, rs.get(i).asInt() + 1);
                }
            } else if (rs != null && rs.isNumber()) {
                // backward compat: integer format
                int row = cell.has("row") ? cell.get("row").asInt() : 0;
                numRows = Math.max(numRows, row + rs.asInt());
            }
            if (cs != null && cs.isArray()) {
                for (int i = 0; i < cs.size(); i++) {
                    numCols = Math.max(numCols, cs.get(i).asInt() + 1);
                }
            } else if (cs != null && cs.isNumber()) {
                int col = cell.has("col") ? cell.get("col").asInt() : 0;
                numCols = Math.max(numCols, col + cs.asInt());
            }
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

        // Fill cells from API with bbox-matched word content
        for (JsonNode cellNode : cellsNode) {
            JsonNode rowspanArr = cellNode.get("rowspan");
            JsonNode colspanArr = cellNode.get("colspan");

            int startRow, rowspan, startCol, colspan;

            if (rowspanArr != null && rowspanArr.isArray() && rowspanArr.size() > 0) {
                startRow = rowspanArr.get(0).asInt();
                rowspan = rowspanArr.size();
            } else if (rowspanArr != null && rowspanArr.isNumber()) {
                // backward compat: integer format
                startRow = cellNode.has("row") ? cellNode.get("row").asInt() : 0;
                rowspan = rowspanArr.asInt();
            } else {
                startRow = 0;
                rowspan = 1;
            }

            if (colspanArr != null && colspanArr.isArray() && colspanArr.size() > 0) {
                startCol = colspanArr.get(0).asInt();
                colspan = colspanArr.size();
            } else if (colspanArr != null && colspanArr.isNumber()) {
                startCol = cellNode.has("col") ? cellNode.get("col").asInt() : 0;
                colspan = colspanArr.asInt();
            } else {
                startCol = 0;
                colspan = 1;
            }

            if (startRow < numRows && startCol < numCols) {
                TableBorderCell cell = new TableBorderCell(startRow, startCol, rowspan, colspan, 0L);

                // Compute cell bbox: use TSR cell bbox if available, otherwise fall back to grid
                BoundingBox cellBbox;
                JsonNode cellBboxNode = cellNode.get("bbox");
                if (cellBboxNode != null && cellBboxNode.isArray() && cellBboxNode.size() >= 4) {
                    // Convert cell bbox from crop coords to page coords by adding crop origin
                    double pageCellLeft = cropOriginLeft + cellBboxNode.get(0).asDouble();
                    double pageCellTop = cropOriginTop + cellBboxNode.get(1).asDouble();
                    double pageCellRight = cropOriginLeft + cellBboxNode.get(2).asDouble();
                    double pageCellBottom = cropOriginTop + cellBboxNode.get(3).asDouble();

                    // Convert page pixels (300DPI, top-left) → PDF points (72DPI, bottom-left)
                    double left = pageCellLeft * PIXEL_TO_POINT;
                    double right = pageCellRight * PIXEL_TO_POINT;
                    double topY = pageHeight - (pageCellTop * PIXEL_TO_POINT);
                    double bottomY = pageHeight - (pageCellBottom * PIXEL_TO_POINT);
                    cellBbox = new BoundingBox(pageIndex, left, bottomY, right, topY);
                } else {
                    double cellLeft = tableBbox.getLeftX() + startCol * colWidth;
                    double cellRight = cellLeft + colspan * colWidth;
                    double cellTop = tableBbox.getTopY() - startRow * rowHeight;
                    double cellBottom = cellTop - rowspan * rowHeight;
                    cellBbox = new BoundingBox(pageIndex, cellLeft, cellBottom, cellRight, cellTop);
                }
                cell.setBoundingBox(cellBbox);

                // Match words to this cell via bbox intersection
                String matchedText = matchWordsToCell(cellBbox, pageWords);

                // Fallback to TSR text field if no words matched
                if (matchedText.isEmpty()) {
                    String tsrText = cellNode.has("text") ? cellNode.get("text").asText("") : "";
                    if (!tsrText.isEmpty()) {
                        cell.addContentObject(createParagraph(tsrText, cellBbox));
                    }
                } else {
                    cell.addContentObject(createParagraph(matchedText, cellBbox));
                }

                table.getRows()[startRow].getCells()[startCol] = cell;
            }
        }

        // Produce ElementMetadata for the table
        ElementMetadata tableMeta = new ElementMetadata()
            .setSourceLabel(tableEntry.has("label") ? tableEntry.get("label").asInt() : LABEL_TABLE);
        if (tsr != null) {
            ElementMetadata.TsrMetadata tsrMeta = new ElementMetadata.TsrMetadata();
            tsrMeta.setNumCells(tsr.has("num_cells") ? tsr.get("num_cells").asInt() : 0);
            tsrMeta.setHtml(tsr.has("html") ? tsr.get("html").asText("") : "");
            tsrMeta.setRunTimeMs(tsr.has("run_time") ? tsr.get("run_time").asLong() : 0);
            tableMeta.setTsr(tsrMeta);
        }
        elementMetadataMap.put(table.getRecognizedStructureId(), tableMeta);

        return table;
    }

    // --- Helper: cell-word bbox matching ---

    /**
     * Collects per-page word info (text + bbox in PDF points) from all DLA+OCR objects.
     * Excludes furniture labels (14, 15, 17) and objects without text.
     */
    private Map<Integer, List<WordInfo>> collectWordsByPage(List<JsonNode> dlaPages,
                                                             Map<Integer, Double> pageHeights) {
        Map<Integer, List<WordInfo>> wordsByPage = new HashMap<>();

        for (int i = 0; i < dlaPages.size(); i++) {
            JsonNode page = dlaPages.get(i);
            int pageNumber = page.has("page_number") ? page.get("page_number").asInt() : i;
            double pageHeight = getPageHeight(pageNumber, pageHeights, page);

            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            List<WordInfo> words = new ArrayList<>();
            for (JsonNode obj : objects) {
                int label = obj.has("label") ? obj.get("label").asInt() : -1;
                // Skip furniture labels
                if (label == LABEL_PAGE_HEADER || label == LABEL_PAGE_FOOTER || label == LABEL_PAGE_NUMBER) {
                    continue;
                }

                String text = obj.has("ocrtext") ? obj.get("ocrtext").asText("") : "";
                if (text.isEmpty()) continue;

                JsonNode bboxNode = obj.get("bbox");
                if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() < 4) continue;

                BoundingBox bbox = extractBoundingBox(bboxNode, pageNumber, pageHeight);
                words.add(new WordInfo(text, bbox));
            }

            if (!words.isEmpty()) {
                wordsByPage.put(pageNumber, words);
            }
        }

        return wordsByPage;
    }

    /**
     * Matches DLA+OCR words to a cell by bbox intersection.
     * A word belongs to a cell if intersection_area / word_area > 0.5.
     * Matched words are sorted by reading order (top-to-bottom, left-to-right) and joined with space.
     *
     * @return joined text of matched words, or empty string if no matches
     */
    private String matchWordsToCell(BoundingBox cellBbox, List<WordInfo> pageWords) {
        if (pageWords == null || pageWords.isEmpty()) {
            return "";
        }

        List<WordInfo> matched = new ArrayList<>();
        for (WordInfo word : pageWords) {
            double wordArea = bboxArea(word.bbox);
            if (wordArea <= 0) continue;

            double intersection = bboxIntersectionArea(cellBbox, word.bbox);
            if (intersection / wordArea > WORD_CELL_OVERLAP_THRESHOLD) {
                matched.add(word);
            }
        }

        if (matched.isEmpty()) {
            return "";
        }

        // Sort by reading order: top-to-bottom (descending topY), then left-to-right
        matched.sort((a, b) -> {
            int cmp = Double.compare(b.bbox.getTopY(), a.bbox.getTopY()); // higher topY = higher on page
            if (cmp != 0) return cmp;
            return Double.compare(a.bbox.getLeftX(), b.bbox.getLeftX());
        });

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < matched.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append(matched.get(i).text);
        }
        return sb.toString();
    }

    /**
     * Computes the intersection area of two bounding boxes (both in PDF points, bottom-left origin).
     */
    private double bboxIntersectionArea(BoundingBox a, BoundingBox b) {
        double left = Math.max(a.getLeftX(), b.getLeftX());
        double right = Math.min(a.getRightX(), b.getRightX());
        double bottom = Math.max(a.getBottomY(), b.getBottomY());
        double top = Math.min(a.getTopY(), b.getTopY());
        if (left >= right || bottom >= top) return 0.0;
        return (right - left) * (top - bottom);
    }

    /**
     * Computes the area of a bounding box.
     */
    private double bboxArea(BoundingBox b) {
        double w = b.getRightX() - b.getLeftX();
        double h = b.getTopY() - b.getBottomY();
        return (w > 0 && h > 0) ? w * h : 0.0;
    }

    // --- Helper: TSR overlap check for label 7 ---

    /**
     * Checks if any TSR table bbox overlaps with the given region bbox by more than 50%.
     * Used to determine if a label 7 (Regionlist/Table) region is already covered by TSR data.
     */
    private boolean hasOverlappingTsr(BoundingBox regionBbox, List<BoundingBox> tsrTableBboxes) {
        if (tsrTableBboxes == null || tsrTableBboxes.isEmpty()) return false;

        double regionArea = bboxArea(regionBbox);
        if (regionArea <= 0) return false;

        for (BoundingBox tsrBbox : tsrTableBboxes) {
            double intersection = bboxIntersectionArea(regionBbox, tsrBbox);
            if (intersection / regionArea > WORD_CELL_OVERLAP_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a PDFList from text by splitting on newlines.
     * Each non-empty line becomes a ListItem with bullet detection.
     */
    private PDFList createListFromText(String text, BoundingBox bbox) {
        String[] lines = text.split("\n");
        List<ListItem> items = new ArrayList<>();

        int lineCount = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            lineCount++;
        }

        // Compute approximate per-item height
        double itemHeight = (lineCount > 0 && bbox != null)
            ? (bbox.getTopY() - bbox.getBottomY()) / lineCount : 0;

        int index = 0;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // Approximate per-line bbox
            BoundingBox itemBbox = bbox != null
                ? new BoundingBox(bbox.getPageNumber(),
                    bbox.getLeftX(), bbox.getTopY() - (index + 1) * itemHeight,
                    bbox.getRightX(), bbox.getTopY() - index * itemHeight)
                : new BoundingBox();

            items.add(createListItem(trimmed, itemBbox));
            index++;
        }

        if (items.isEmpty()) return null;

        return buildPDFList(items);
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

    /**
     * Extracts table pages from legacy TSR format (nested array).
     * Used only for backward compatibility with old TSR format.
     */
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

    private SemanticCaption createCaption(String text, BoundingBox bbox) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);
        SemanticCaption caption = new SemanticCaption();
        caption.add(textLine);
        caption.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        caption.setCorrectSemanticScore(1.0);
        return caption;
    }

    private SemanticFootnote createFootnote(String text, BoundingBox bbox) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);
        SemanticFootnote footnote = new SemanticFootnote();
        footnote.add(textLine);
        footnote.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        footnote.setCorrectSemanticScore(1.0);
        return footnote;
    }

    private ListItem createListItem(String text, BoundingBox bbox) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        ListItem item = new ListItem(bbox, StaticLayoutContainers.incrementContentId());
        item.add(textLine);
        item.setLabelLength(detectBulletPrefixLength(text));
        return item;
    }

    // --- Helper: caption-float linking post-processing ---

    /**
     * Links each {@link SemanticCaption} to the nearest Table or Picture on the same page
     * by setting {@code linkedContentId} to the float's {@code recognizedStructureId}.
     */
    private void linkCaptionsToFloats(List<IObject> pageContents) {
        List<SemanticCaption> captions = new ArrayList<>();
        List<IObject> floats = new ArrayList<>();
        for (IObject obj : pageContents) {
            if (obj instanceof SemanticCaption) {
                captions.add((SemanticCaption) obj);
            }
            if (obj instanceof TableBorder || obj instanceof SemanticPicture) {
                floats.add(obj);
            }
        }
        for (SemanticCaption cap : captions) {
            IObject nearest = findNearestFloat(cap, floats);
            if (nearest != null) {
                cap.setLinkedContentId(nearest.getRecognizedStructureId());
            }
        }
    }

    /**
     * Finds the float (Table or Picture) nearest to the caption by center Y distance.
     */
    private IObject findNearestFloat(SemanticCaption caption, List<IObject> floats) {
        if (floats.isEmpty() || caption.getBoundingBox() == null) {
            return null;
        }
        double captionCenterY = caption.getBoundingBox().getCenterY();
        IObject nearest = null;
        double minDist = Double.MAX_VALUE;
        for (IObject f : floats) {
            if (f.getBoundingBox() == null) continue;
            double dist = Math.abs(f.getBoundingBox().getCenterY() - captionCenterY);
            if (dist < minDist) {
                minDist = dist;
                nearest = f;
            }
        }
        return nearest;
    }

    // --- Helper: list grouping post-processing ---

    /**
     * Groups consecutive {@link ListItem} objects into {@link PDFList} instances.
     * Any non-ListItem object breaks the current run and starts a new list.
     */
    private List<IObject> groupListItems(List<IObject> pageContents) {
        List<IObject> grouped = new ArrayList<>();
        List<ListItem> currentRun = new ArrayList<>();

        for (IObject obj : pageContents) {
            if (obj instanceof ListItem) {
                currentRun.add((ListItem) obj);
            } else {
                if (!currentRun.isEmpty()) {
                    grouped.add(buildPDFList(currentRun));
                    currentRun = new ArrayList<>();
                }
                grouped.add(obj);
            }
        }

        // Flush remaining run
        if (!currentRun.isEmpty()) {
            grouped.add(buildPDFList(currentRun));
        }

        return grouped;
    }

    /**
     * Builds a {@link PDFList} from a run of {@link ListItem} objects.
     * Computes union bounding box and assigns a structure ID.
     */
    private PDFList buildPDFList(List<ListItem> items) {
        PDFList list = new PDFList();
        list.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());

        double minLeft = Double.MAX_VALUE;
        double minBottom = Double.MAX_VALUE;
        double maxRight = -Double.MAX_VALUE;
        double maxTop = -Double.MAX_VALUE;
        int pageNumber = 0;

        for (ListItem item : items) {
            list.add(item);
            BoundingBox itemBbox = item.getBoundingBox();
            if (itemBbox != null) {
                pageNumber = itemBbox.getPageNumber();
                minLeft = Math.min(minLeft, itemBbox.getLeftX());
                minBottom = Math.min(minBottom, itemBbox.getBottomY());
                maxRight = Math.max(maxRight, itemBbox.getRightX());
                maxTop = Math.max(maxTop, itemBbox.getTopY());
            }
        }

        if (minLeft != Double.MAX_VALUE) {
            list.setBoundingBox(new BoundingBox(pageNumber, minLeft, minBottom, maxRight, maxTop));
        }

        return list;
    }

    /**
     * Simple language detection heuristic: if the text contains any CJK character, returns "ko";
     * otherwise returns "en".
     */
    static String detectLanguage(String text) {
        if (text == null) return "en";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HANGUL
                || Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
                || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA) {
                return "ko";
            }
        }
        return "en";
    }

    /**
     * Detects bullet/number prefix in text and returns its length (including trailing space).
     * Returns 0 if no bullet prefix is found.
     */
    static int detectBulletPrefixLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        Matcher matcher = BULLET_PREFIX_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.end();
        }
        return 0;
    }
}

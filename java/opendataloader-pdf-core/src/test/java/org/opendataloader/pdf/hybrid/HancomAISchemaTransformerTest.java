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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFootnote;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for HancomAISchemaTransformer heading level inference.
 *
 * <p>Tests that label 1 (ParaTitle) and label 4 (RegionTitle) headings
 * are assigned H2~H6 based on bbox height (font-size proxy), while
 * label 0 (DocTitle) always maps to H1.
 */
public class HancomAISchemaTransformerTest {

    private HancomAISchemaTransformer transformer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        transformer = new HancomAISchemaTransformer();
        objectMapper = new ObjectMapper();
        StaticLayoutContainers.setCurrentContentId(1L);
    }

    // --- Label 0 (DocTitle) always H1 ---

    @Test
    void titleLabel0_alwaysH1() {
        ObjectNode json = createHancomAIJson(
            createObject(0, "Document Title", 100, 50, 500, 100)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).hasSize(1);
        SemanticHeading heading = (SemanticHeading) result.get(0).get(0);
        assertThat(heading.getHeadingLevel()).isEqualTo(1);
        assertThat(heading.getValue()).isEqualTo("Document Title");
    }

    // --- Single heading size defaults to H2 ---

    @Test
    void singleHeadingSize_label1_defaultsToH2() {
        ObjectNode json = createHancomAIJson(
            createObject(1, "Only Heading", 100, 200, 500, 250)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        SemanticHeading heading = (SemanticHeading) result.get(0).get(0);
        assertThat(heading.getHeadingLevel()).isEqualTo(2);
    }

    @Test
    void singleHeadingSize_label4_defaultsToH2() {
        ObjectNode json = createHancomAIJson(
            createObject(4, "Only Subheading", 100, 200, 500, 240)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        SemanticHeading heading = (SemanticHeading) result.get(0).get(0);
        assertThat(heading.getHeadingLevel()).isEqualTo(2);
    }

    // --- Two different heading sizes → H2 and H3 ---

    @Test
    void twoDifferentHeightHeadings_tallIsH2_shortIsH3() {
        // Taller bbox (height 60px) = bigger font = H2
        // Shorter bbox (height 30px) = smaller font = H3
        ObjectNode json = createHancomAIJson(
            createObject(1, "Big Section", 100, 100, 500, 160),    // height=60
            createObject(1, "Small Section", 100, 300, 500, 330)   // height=30
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(2);

        // Results are sorted by reading order (top to bottom), so Big Section first
        SemanticHeading big = (SemanticHeading) result.get(0).get(0);
        SemanticHeading small = (SemanticHeading) result.get(0).get(1);

        assertThat(big.getValue()).isEqualTo("Big Section");
        assertThat(big.getHeadingLevel()).isEqualTo(2);

        assertThat(small.getValue()).isEqualTo("Small Section");
        assertThat(small.getHeadingLevel()).isEqualTo(3);
    }

    // --- Same height → same level ---

    @Test
    void sameHeight_sameLevelHeading() {
        ObjectNode json = createHancomAIJson(
            createObject(1, "Section A", 100, 100, 500, 150),  // height=50
            createObject(1, "Section B", 100, 300, 500, 350)   // height=50
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(2);
        SemanticHeading a = (SemanticHeading) result.get(0).get(0);
        SemanticHeading b = (SemanticHeading) result.get(0).get(1);

        assertThat(a.getHeadingLevel()).isEqualTo(b.getHeadingLevel());
        assertThat(a.getHeadingLevel()).isEqualTo(2);
    }

    // --- Mixed label 1 and label 4 share the same height pool ---

    @Test
    void mixedLabel1And4_shareHeightPool() {
        ObjectNode json = createHancomAIJson(
            createObject(1, "Para Title", 100, 100, 500, 160),     // height=60 → H2
            createObject(4, "Region Title", 100, 300, 500, 330)    // height=30 → H3
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(2);
        SemanticHeading paraTitle = (SemanticHeading) result.get(0).get(0);
        SemanticHeading regionTitle = (SemanticHeading) result.get(0).get(1);

        assertThat(paraTitle.getHeadingLevel()).isEqualTo(2);
        assertThat(regionTitle.getHeadingLevel()).isEqualTo(3);
    }

    // --- Three different heights → H2, H3, H4 ---

    @Test
    void threeDifferentHeights_H2H3H4() {
        ObjectNode json = createHancomAIJson(
            createObject(1, "Large", 100, 100, 500, 200),      // height=100 → H2
            createObject(1, "Medium", 100, 300, 500, 360),      // height=60  → H3
            createObject(4, "Small", 100, 500, 500, 530)        // height=30  → H4
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(3);
        SemanticHeading large = (SemanticHeading) result.get(0).get(0);
        SemanticHeading medium = (SemanticHeading) result.get(0).get(1);
        SemanticHeading small = (SemanticHeading) result.get(0).get(2);

        assertThat(large.getHeadingLevel()).isEqualTo(2);
        assertThat(medium.getHeadingLevel()).isEqualTo(3);
        assertThat(small.getHeadingLevel()).isEqualTo(4);
    }

    // --- Cap at H6 ---

    @Test
    void moreThanFiveSizes_cappedAtH6() {
        // 6 different sizes → H2, H3, H4, H5, H6, H6 (capped)
        ObjectNode json = createHancomAIJson(
            createObject(1, "Size1", 100, 50, 500, 110),       // height=60
            createObject(1, "Size2", 100, 150, 500, 200),      // height=50
            createObject(1, "Size3", 100, 250, 500, 290),      // height=40
            createObject(1, "Size4", 100, 350, 500, 380),      // height=30
            createObject(1, "Size5", 100, 450, 500, 470),      // height=20
            createObject(1, "Size6", 100, 550, 500, 560)       // height=10
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(6);

        // Sorted by reading order (top to bottom, which matches our order)
        assertThat(((SemanticHeading) result.get(0).get(0)).getHeadingLevel()).isEqualTo(2);
        assertThat(((SemanticHeading) result.get(0).get(1)).getHeadingLevel()).isEqualTo(3);
        assertThat(((SemanticHeading) result.get(0).get(2)).getHeadingLevel()).isEqualTo(4);
        assertThat(((SemanticHeading) result.get(0).get(3)).getHeadingLevel()).isEqualTo(5);
        assertThat(((SemanticHeading) result.get(0).get(4)).getHeadingLevel()).isEqualTo(6);
        assertThat(((SemanticHeading) result.get(0).get(5)).getHeadingLevel()).isEqualTo(6);
    }

    // --- Document-wide inference (across pages) ---

    @Test
    void headingLevels_consistentAcrossPages() {
        // Page 0: large heading (height=60)
        // Page 1: small heading (height=30)
        // Both should share same height pool → H2 and H3
        ObjectNode json = createHancomAIJsonMultiPage(
            new ObjectNode[][]{
                {createObject(1, "Page1 Heading", 100, 100, 500, 160)},   // height=60
                {createObject(1, "Page2 Heading", 100, 100, 500, 130)}    // height=30
            }
        );

        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);
        pageHeights.put(2, 842.0);

        HybridResponse response = new HybridResponse("", json, null);
        List<List<IObject>> result = transformer.transform(response, pageHeights);

        assertThat(result).hasSize(2);
        SemanticHeading page1Heading = (SemanticHeading) result.get(0).get(0);
        SemanticHeading page2Heading = (SemanticHeading) result.get(1).get(0);

        assertThat(page1Heading.getHeadingLevel()).isEqualTo(2);
        assertThat(page2Heading.getHeadingLevel()).isEqualTo(3);
    }

    @Test
    void headingLevels_sameHeightDifferentPages_sameLevel() {
        // Same height on different pages → same level
        ObjectNode json = createHancomAIJsonMultiPage(
            new ObjectNode[][]{
                {createObject(1, "Page1 Heading", 100, 100, 500, 150)},   // height=50
                {createObject(4, "Page2 Heading", 100, 100, 500, 150)}    // height=50
            }
        );

        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);
        pageHeights.put(2, 842.0);

        HybridResponse response = new HybridResponse("", json, null);
        List<List<IObject>> result = transformer.transform(response, pageHeights);

        assertThat(result).hasSize(2);
        SemanticHeading h1 = (SemanticHeading) result.get(0).get(0);
        SemanticHeading h2 = (SemanticHeading) result.get(1).get(0);

        assertThat(h1.getHeadingLevel()).isEqualTo(h2.getHeadingLevel());
        assertThat(h1.getHeadingLevel()).isEqualTo(2);
    }

    // --- Title (label 0) is not affected by inference ---

    @Test
    void titleH1_notAffectedByOtherHeadingSizes() {
        // Title + heading: title stays H1 regardless
        ObjectNode json = createHancomAIJson(
            createObject(0, "Doc Title", 100, 50, 500, 120),       // label 0 → always H1
            createObject(1, "Chapter", 100, 200, 500, 260),        // height=60 → H2
            createObject(1, "Subsection", 100, 400, 500, 430)      // height=30 → H3
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(3);
        SemanticHeading title = (SemanticHeading) result.get(0).get(0);
        SemanticHeading chapter = (SemanticHeading) result.get(0).get(1);
        SemanticHeading subsection = (SemanticHeading) result.get(0).get(2);

        assertThat(title.getHeadingLevel()).isEqualTo(1);
        assertThat(chapter.getHeadingLevel()).isEqualTo(2);
        assertThat(subsection.getHeadingLevel()).isEqualTo(3);
    }

    // --- No heading level skipping ---

    @Test
    void noHeadingLevelSkipping() {
        // Even with very different heights, levels are sequential: H2, H3, H4
        ObjectNode json = createHancomAIJson(
            createObject(1, "Huge", 100, 100, 500, 300),        // height=200
            createObject(1, "Tiny", 100, 400, 500, 410),        // height=10
            createObject(1, "Medium", 100, 500, 500, 550)       // height=50
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(3);

        // Sorted by reading order: Huge(top=100), Tiny(top=400), Medium(top=500)
        SemanticHeading huge = (SemanticHeading) result.get(0).get(0);
        SemanticHeading tiny = (SemanticHeading) result.get(0).get(1);
        SemanticHeading medium = (SemanticHeading) result.get(0).get(2);

        assertThat(huge.getHeadingLevel()).isEqualTo(2);      // tallest → H2
        assertThat(medium.getHeadingLevel()).isEqualTo(3);     // middle → H3
        assertThat(tiny.getHeadingLevel()).isEqualTo(4);       // shortest → H4

        // No skipping: levels are 2, 3, 4 (no gap)
    }

    // --- List grouping (label 3) ---

    @Test
    void consecutiveLabel3_groupedIntoSinglePDFList() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "First item", 100, 100, 500, 130),
            createObject(3, "Second item", 100, 150, 500, 180),
            createObject(3, "Third item", 100, 200, 500, 230)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(PDFList.class);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems()).hasSize(3);
        assertThat(list.getListItems().get(0).getFirstLine().getValue()).isEqualTo("First item");
        assertThat(list.getListItems().get(1).getFirstLine().getValue()).isEqualTo("Second item");
        assertThat(list.getListItems().get(2).getFirstLine().getValue()).isEqualTo("Third item");
    }

    @Test
    void nonLabel3_breaksListIntoSeparateInstances() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "Item A", 100, 100, 500, 130),
            createObject(3, "Item B", 100, 150, 500, 180),
            createObject(2, "A paragraph", 100, 200, 500, 230),   // label 2 breaks the run
            createObject(3, "Item C", 100, 250, 500, 280),
            createObject(3, "Item D", 100, 300, 500, 330)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(3);  // PDFList, paragraph, PDFList
        assertThat(result.get(0).get(0)).isInstanceOf(PDFList.class);
        assertThat(result.get(0).get(1)).isInstanceOf(SemanticParagraph.class);
        assertThat(result.get(0).get(2)).isInstanceOf(PDFList.class);

        PDFList list1 = (PDFList) result.get(0).get(0);
        PDFList list2 = (PDFList) result.get(0).get(2);
        assertThat(list1.getListItems()).hasSize(2);
        assertThat(list2.getListItems()).hasSize(2);
    }

    @Test
    void singleLabel3_stillWrappedInPDFList() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "Solo item", 100, 100, 500, 130)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(PDFList.class);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems()).hasSize(1);
    }

    @Test
    void bulletPrefix_setsLabelLength() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "\u2022 Bullet item", 100, 100, 500, 130),  // bullet char + space = 2
            createObject(3, "1. Numbered item", 100, 150, 500, 180),    // "1. " = 3
            createObject(3, "a) Letter item", 100, 200, 500, 230)       // "a) " = 3
        );

        List<List<IObject>> result = transform(json);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems()).hasSize(3);
        assertThat(list.getListItems().get(0).getLabelLength()).isEqualTo(2);
        assertThat(list.getListItems().get(1).getLabelLength()).isEqualTo(3);
        assertThat(list.getListItems().get(2).getLabelLength()).isEqualTo(3);
    }

    @Test
    void noBulletPrefix_labelLengthZero() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "Plain text item", 100, 100, 500, 130)
        );

        List<List<IObject>> result = transform(json);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems().get(0).getLabelLength()).isEqualTo(0);
    }

    @Test
    void dashBulletPrefix_setsLabelLength() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "- Dash item", 100, 100, 500, 130),
            createObject(3, "\u2013 En-dash item", 100, 150, 500, 180),
            createObject(3, "\u2014 Em-dash item", 100, 200, 500, 230)
        );

        List<List<IObject>> result = transform(json);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems().get(0).getLabelLength()).isEqualTo(2);  // "- "
        assertThat(list.getListItems().get(1).getLabelLength()).isEqualTo(2);  // "\u2013 "
        assertThat(list.getListItems().get(2).getLabelLength()).isEqualTo(2);  // "\u2014 "
    }

    @Test
    void pdfList_hasBoundingBoxUnion() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "Item 1", 100, 100, 500, 130),
            createObject(3, "Item 2", 80, 150, 520, 180)
        );

        List<List<IObject>> result = transform(json);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getBoundingBox()).isNotNull();
        // Union bbox should encompass both items
        // After coordinate conversion: leftX should be min(100, 80)*0.24 = 19.2
        // rightX should be max(500, 520)*0.24 = 124.8
        assertThat(list.getBoundingBox().getLeftX()).isLessThanOrEqualTo(
            list.getListItems().get(0).getBoundingBox().getLeftX());
        assertThat(list.getBoundingBox().getRightX()).isGreaterThanOrEqualTo(
            list.getListItems().get(1).getBoundingBox().getRightX());
    }

    @Test
    void pdfList_hasRecognizedStructureId() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "Item", 100, 100, 500, 130)
        );

        List<List<IObject>> result = transform(json);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getRecognizedStructureId()).isNotNull();
    }

    @Test
    void multiDigitNumberBullet_setsLabelLength() {
        ObjectNode json = createHancomAIJson(
            createObject(3, "12. Twelfth item", 100, 100, 500, 130),
            createObject(3, "3) Third item", 100, 150, 500, 180)
        );

        List<List<IObject>> result = transform(json);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems().get(0).getLabelLength()).isEqualTo(4);  // "12. "
        assertThat(list.getListItems().get(1).getLabelLength()).isEqualTo(3);  // "3) "
    }

    // --- Helper methods ---

    private List<List<IObject>> transform(ObjectNode json) {
        HybridResponse response = new HybridResponse("", json, null);
        Map<Integer, Double> pageHeights = new HashMap<>();
        pageHeights.put(1, 842.0);
        return transformer.transform(response, pageHeights);
    }

    /**
     * Creates an object node representing a Hancom AI detected object.
     * bbox format: [left, top, right, bottom]
     */
    private ObjectNode createObject(int label, String text, double left, double top,
                                     double right, double bottom) {
        ObjectNode obj = objectMapper.createObjectNode();
        obj.put("label", label);
        obj.put("ocrtext", text);
        obj.put("confidence", 0.95);

        ArrayNode bbox = obj.putArray("bbox");
        bbox.add(left);
        bbox.add(top);
        bbox.add(right);
        bbox.add(bottom);

        return obj;
    }

    /**
     * Creates an object node with a specific confidence value.
     */
    private ObjectNode createObjectWithConfidence(int label, String text, double left, double top,
                                                   double right, double bottom, double confidence) {
        ObjectNode obj = objectMapper.createObjectNode();
        obj.put("label", label);
        obj.put("ocrtext", text);
        obj.put("confidence", confidence);

        ArrayNode bbox = obj.putArray("bbox");
        bbox.add(left);
        bbox.add(top);
        bbox.add(right);
        bbox.add(bottom);

        return obj;
    }

    /**
     * Creates Hancom AI JSON with a single page containing given objects.
     */
    private ObjectNode createHancomAIJson(ObjectNode... objects) {
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
        ArrayNode pages = dlaOcr.addArray();

        ObjectNode page = pages.addObject();
        page.put("page_number", 0);
        page.put("image_height", 3508);
        ArrayNode objectsArray = page.putArray("objects");
        for (ObjectNode obj : objects) {
            objectsArray.add(obj);
        }

        return json;
    }

    /**
     * Creates Hancom AI JSON with multiple pages.
     * Each inner array is the objects for that page.
     */
    private ObjectNode createHancomAIJsonMultiPage(ObjectNode[][] pageObjects) {
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
        ArrayNode pages = dlaOcr.addArray();

        for (int i = 0; i < pageObjects.length; i++) {
            ObjectNode page = pages.addObject();
            page.put("page_number", i);
            page.put("image_height", 3508);
            ArrayNode objectsArray = page.putArray("objects");
            for (ObjectNode obj : pageObjects[i]) {
                objectsArray.add(obj);
            }
        }

        return json;
    }

    // --- Cell-word bbox intersection matching (Task 4) ---

    /**
     * Helper: creates a Hancom AI JSON with DLA+OCR objects AND TABLE_STRUCTURE_RECOGNITION data.
     * Uses the new per-table entry format with dla_bbox and tsr sub-object.
     *
     * <p>For backward-compat with existing tests where cell bboxes are in page pixels:
     * dla_bbox is set to tableBbox (so overlap checks work correctly), and the cell bboxes
     * are adjusted by subtracting the table origin so they become crop-relative.
     * The tsr.table_bbox is set to a zero-origin version of tableBbox.
     */
    private ObjectNode createHancomAIJsonWithTable(ObjectNode[] dlaObjects,
                                                    double[] tableBbox,
                                                    ObjectNode[] tsrCells) {
        // Make cell bboxes crop-relative by subtracting table origin
        double originLeft = tableBbox[0];
        double originTop = tableBbox[1];
        ObjectNode[] adjustedCells = new ObjectNode[tsrCells.length];
        for (int i = 0; i < tsrCells.length; i++) {
            adjustedCells[i] = tsrCells[i].deepCopy();
            com.fasterxml.jackson.databind.node.ArrayNode cellBbox =
                (com.fasterxml.jackson.databind.node.ArrayNode) adjustedCells[i].get("bbox");
            if (cellBbox != null && cellBbox.size() >= 4) {
                double l = cellBbox.get(0).asDouble() - originLeft;
                double t = cellBbox.get(1).asDouble() - originTop;
                double r = cellBbox.get(2).asDouble() - originLeft;
                double b = cellBbox.get(3).asDouble() - originTop;
                cellBbox.removeAll();
                cellBbox.add(l); cellBbox.add(t); cellBbox.add(r); cellBbox.add(b);
            }
        }
        // tsr.table_bbox in crop coords (zero-origin)
        double[] cropTableBbox = {0, 0, tableBbox[2] - tableBbox[0], tableBbox[3] - tableBbox[1]};
        return createHancomAIJsonWithTableAndOffset(dlaObjects, tableBbox, cropTableBbox, adjustedCells);
    }

    /**
     * Helper: creates Hancom AI JSON with separate dla_bbox (page coords) and crop-relative cells.
     * @param dlaBbox the padded DLA bbox in page pixels [left, top, right, bottom]
     * @param tsrTableBbox the table_bbox in crop-image coords
     * @param tsrCells cells with crop-relative bboxes
     */
    private ObjectNode createHancomAIJsonWithTableAndOffset(ObjectNode[] dlaObjects,
                                                             double[] dlaBbox,
                                                             double[] tsrTableBbox,
                                                             ObjectNode[] tsrCells) {
        ObjectNode json = objectMapper.createObjectNode();

        // DLA+OCR
        ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
        ArrayNode dlaPages = dlaOcr.addArray();
        ObjectNode dlaPage = dlaPages.addObject();
        dlaPage.put("page_number", 0);
        dlaPage.put("image_height", 3508);
        ArrayNode objectsArray = dlaPage.putArray("objects");
        for (ObjectNode obj : dlaObjects) {
            objectsArray.add(obj);
        }

        // TABLE_STRUCTURE_RECOGNITION — new per-table entry format
        ArrayNode tsr = json.putArray("TABLE_STRUCTURE_RECOGNITION");
        ObjectNode tableEntry = tsr.addObject();
        tableEntry.put("page_number", 0);
        tableEntry.put("object_id", 0);
        tableEntry.put("label", 9);

        ArrayNode dlaBboxArr = tableEntry.putArray("dla_bbox");
        for (double v : dlaBbox) dlaBboxArr.add(v);

        ObjectNode tsrObj = tableEntry.putObject("tsr");
        tsrObj.put("page_number", 0);
        tsrObj.put("num_cells", tsrCells.length);

        ArrayNode tbbox = tsrObj.putArray("table_bbox");
        for (double v : tsrTableBbox) tbbox.add(v);

        ArrayNode cellsArray = tsrObj.putArray("cells");
        for (ObjectNode cell : tsrCells) {
            cellsArray.add(cell);
        }

        return json;
    }

    /**
     * Helper: creates a TSR cell node with array-format rowspan/colspan (real API format).
     * rowspan/colspan are arrays of row/col indices this cell spans.
     */
    private ObjectNode createTsrCell(int row, int col, int rowspan, int colspan,
                                      String text, double left, double top,
                                      double right, double bottom) {
        ObjectNode cell = objectMapper.createObjectNode();
        cell.put("cell_id", row * 10 + col);  // synthetic cell_id
        cell.put("text", text);

        // Array-format rowspan: [startRow, startRow+1, ..., startRow+rowspan-1]
        ArrayNode rsArr = cell.putArray("rowspan");
        for (int i = 0; i < rowspan; i++) rsArr.add(row + i);

        // Array-format colspan: [startCol, startCol+1, ..., startCol+colspan-1]
        ArrayNode csArr = cell.putArray("colspan");
        for (int i = 0; i < colspan; i++) csArr.add(col + i);

        ArrayNode bbox = cell.putArray("bbox");
        bbox.add(left);
        bbox.add(top);
        bbox.add(right);
        bbox.add(bottom);
        return cell;
    }

    /**
     * Helper: extract text from a TableBorderCell's first content object (SemanticParagraph).
     */
    private String getCellText(TableBorder table, int row, int col) {
        TableBorderCell cell = table.getCell(row, col);
        if (cell == null || cell.getContents() == null || cell.getContents().isEmpty()) {
            return "";
        }
        IObject content = cell.getContents().get(0);
        if (content instanceof SemanticParagraph) {
            return ((SemanticParagraph) content).getValue();
        }
        return "";
    }

    @Test
    void tableCellText_comesFromBboxWordMatching_notTsrTextField() {
        // DLA+OCR words positioned inside specific cells
        // Table at pixel coords [100, 100, 500, 300] (2 rows x 2 cols)
        // Cell (0,0): [100,100,300,200], Cell (0,1): [300,100,500,200]
        // Cell (1,0): [100,200,300,300], Cell (1,1): [300,200,500,300]
        ObjectNode word1 = createObject(2, "Hello", 120, 120, 280, 180);   // inside cell (0,0)
        ObjectNode word2 = createObject(2, "World", 320, 120, 480, 180);   // inside cell (0,1)
        ObjectNode word3 = createObject(2, "Foo", 120, 220, 280, 280);     // inside cell (1,0)
        ObjectNode word4 = createObject(2, "Bar", 320, 220, 480, 280);     // inside cell (1,1)

        // TSR cells have WRONG text (to prove we use bbox matching, not TSR text)
        ObjectNode tsrCell00 = createTsrCell(0, 0, 1, 1, "WRONG0", 100, 100, 300, 200);
        ObjectNode tsrCell01 = createTsrCell(0, 1, 1, 1, "WRONG1", 300, 100, 500, 200);
        ObjectNode tsrCell10 = createTsrCell(1, 0, 1, 1, "WRONG2", 100, 200, 300, 300);
        ObjectNode tsrCell11 = createTsrCell(1, 1, 1, 1, "WRONG3", 300, 200, 500, 300);

        double[] tableBbox = {100, 100, 500, 300};
        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{word1, word2, word3, word4},
            tableBbox,
            new ObjectNode[]{tsrCell00, tsrCell01, tsrCell10, tsrCell11}
        );

        List<List<IObject>> result = transform(json);

        // Find the TableBorder in results
        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();

        // Cell text should come from DLA words, NOT TSR text field
        assertThat(getCellText(table, 0, 0)).isEqualTo("Hello");
        assertThat(getCellText(table, 0, 1)).isEqualTo("World");
        assertThat(getCellText(table, 1, 0)).isEqualTo("Foo");
        assertThat(getCellText(table, 1, 1)).isEqualTo("Bar");
    }

    @Test
    void tableCellText_wordStraddlingTwoCells_assignedToMajorityOverlap() {
        // Word bbox straddles cell (0,0) and cell (0,1), but majority is in (0,0)
        // Table: [100, 100, 500, 300], 1 row x 2 cols
        // Cell (0,0): [100,100,300,300], Cell (0,1): [300,100,500,300]
        // Word at [120, 120, 310, 280] → overlaps (0,0) by 180x160=28800, (0,1) by 10x160=1600
        // 28800/(190*160)=0.947 > 0.5 → assigned to (0,0)
        // 1600/(190*160)=0.053 < 0.5 → NOT assigned to (0,1)
        ObjectNode word = createObject(2, "Straddler", 120, 120, 310, 280);

        ObjectNode tsrCell0 = createTsrCell(0, 0, 1, 1, "TSR0", 100, 100, 300, 300);
        ObjectNode tsrCell1 = createTsrCell(0, 1, 1, 1, "TSR1", 300, 100, 500, 300);

        double[] tableBbox = {100, 100, 500, 300};
        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{word},
            tableBbox,
            new ObjectNode[]{tsrCell0, tsrCell1}
        );

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();

        // Word assigned to cell (0,0) where majority overlap is
        assertThat(getCellText(table, 0, 0)).isEqualTo("Straddler");
        // Cell (0,1) should fall back to TSR text since no matching words
        assertThat(getCellText(table, 0, 1)).isEqualTo("TSR1");
    }

    @Test
    void tableCellText_noMatchingWords_fallsBackToTsrText() {
        // No DLA words on this page — cells should use TSR text field as fallback
        ObjectNode tsrCell0 = createTsrCell(0, 0, 1, 1, "Fallback Text", 100, 100, 300, 200);
        ObjectNode tsrCell1 = createTsrCell(0, 1, 1, 1, "Also Fallback", 300, 100, 500, 200);

        double[] tableBbox = {100, 100, 500, 200};
        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{},   // no DLA words
            tableBbox,
            new ObjectNode[]{tsrCell0, tsrCell1}
        );

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();

        assertThat(getCellText(table, 0, 0)).isEqualTo("Fallback Text");
        assertThat(getCellText(table, 0, 1)).isEqualTo("Also Fallback");
    }

    @Test
    void tableCellText_multipleWordsInCell_sortedByReadingOrder() {
        // Two words in cell (0,0): one higher up, one lower
        // They should be joined in reading order (top-to-bottom, left-to-right)
        ObjectNode word1 = createObject(2, "First", 120, 120, 280, 150);   // higher (top=120)
        ObjectNode word2 = createObject(2, "Second", 120, 160, 280, 190);  // lower (top=160)

        ObjectNode tsrCell = createTsrCell(0, 0, 1, 1, "WRONG", 100, 100, 300, 200);
        double[] tableBbox = {100, 100, 300, 200};

        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{word2, word1},  // intentionally reversed to test sorting
            tableBbox,
            new ObjectNode[]{tsrCell}
        );

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();

        // Words should be sorted: First (higher) then Second (lower)
        assertThat(getCellText(table, 0, 0)).isEqualTo("First Second");
    }

    // --- Task 5: Caption mapping (label 8 and 11) ---

    @Test
    void caption_tableName_becomesSemanticCaption() {
        // label 8 (TableName) → SemanticCaption
        ObjectNode json = createHancomAIJson(
            createObject(8, "Table 1: Revenue", 100, 300, 500, 330)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(SemanticCaption.class);
        SemanticCaption caption = (SemanticCaption) result.get(0).get(0);
        assertThat(caption.getValue()).isEqualTo("Table 1: Revenue");
    }

    @Test
    void caption_figureName_becomesSemanticCaption() {
        // label 11 (FigureName) → SemanticCaption
        ObjectNode json = createHancomAIJson(
            createObject(11, "Figure 3: Architecture", 100, 400, 500, 430)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(SemanticCaption.class);
        SemanticCaption caption = (SemanticCaption) result.get(0).get(0);
        assertThat(caption.getValue()).isEqualTo("Figure 3: Architecture");
    }

    @Test
    void caption_linkedToNearestFloat() {
        // label 8 near a table → linkedContentId matches table's recognizedStructureId
        // Table at pixel [100, 100, 500, 300], caption label 8 at [100, 310, 500, 340]
        // Also a figure far away at [100, 800, 500, 1000]
        ObjectNode[] dlaObjects = {
            createObject(8, "Table 1: Data", 100, 310, 500, 340),
            createObject(10, "", 100, 800, 500, 1000)
        };

        ObjectNode tsrCell = createTsrCell(0, 0, 1, 1, "cell", 100, 100, 500, 300);
        double[] tableBbox = {100, 100, 500, 300};
        ObjectNode json = createHancomAIJsonWithTable(
            dlaObjects,
            tableBbox,
            new ObjectNode[]{tsrCell}
        );

        List<List<IObject>> result = transform(json);

        // Find caption and table
        SemanticCaption caption = null;
        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof SemanticCaption) caption = (SemanticCaption) obj;
            if (obj instanceof TableBorder) table = (TableBorder) obj;
        }

        assertThat(caption).isNotNull();
        assertThat(table).isNotNull();
        assertThat(caption.getLinkedContentId()).isEqualTo(table.getRecognizedStructureId());
    }

    // --- Task 6: Footnote (label 13) ---

    @Test
    void footnote_becomesSemanticFootnote() {
        // label 13 → SemanticFootnote (not SemanticParagraph)
        ObjectNode json = createHancomAIJson(
            createObject(13, "1. See reference [3].", 100, 700, 500, 720)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(SemanticFootnote.class);
        SemanticFootnote footnote = (SemanticFootnote) result.get(0).get(0);
        assertThat(footnote.getValue()).isEqualTo("1. See reference [3].");
    }

    // --- Task 7: Regionlist (label 7) → Table/List ---

    @Test
    void regionlist_withTsrOverlap_returnsNull() {
        // label 7 with overlapping TSR → null (table handled separately by transformTablePage)
        // DLA object at [100, 100, 500, 300] (label 7), TSR table covers same area
        ObjectNode regionObj = createObject(7, "Table data here", 100, 100, 500, 300);

        ObjectNode tsrCell = createTsrCell(0, 0, 1, 1, "cell", 100, 100, 500, 300);
        double[] tableBbox = {100, 100, 500, 300};
        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{regionObj},
            tableBbox,
            new ObjectNode[]{tsrCell}
        );

        List<List<IObject>> result = transform(json);

        // label 7 region should be skipped (null), only the TSR table should appear
        for (IObject obj : result.get(0)) {
            // No PDFList or SemanticParagraph from label 7 — only the TableBorder from TSR
            assertThat(obj).isNotInstanceOf(PDFList.class);
            assertThat(obj).isNotInstanceOf(SemanticParagraph.class);
        }
        // TSR table should still be present
        assertThat(result.get(0).stream().anyMatch(o -> o instanceof TableBorder)).isTrue();
    }

    @Test
    void regionlist_withoutTsr_becomesList() {
        // label 7 without any TSR data → PDFList from newline-split text
        ObjectNode json = createHancomAIJson(
            createObject(7, "First line\nSecond line\nThird line", 100, 100, 500, 200)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(PDFList.class);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems()).hasSize(3);
        assertThat(list.getListItems().get(0).getFirstLine().getValue()).isEqualTo("First line");
        assertThat(list.getListItems().get(1).getFirstLine().getValue()).isEqualTo("Second line");
        assertThat(list.getListItems().get(2).getFirstLine().getValue()).isEqualTo("Third line");
    }

    @Test
    void regionlist_withoutTsr_singleLine_becomesSingleItemList() {
        // label 7 with single line text → PDFList with one item
        ObjectNode json = createHancomAIJson(
            createObject(7, "Solo table text", 100, 100, 500, 130)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(PDFList.class);

        PDFList list = (PDFList) result.get(0).get(0);
        assertThat(list.getListItems()).hasSize(1);
        assertThat(list.getListItems().get(0).getFirstLine().getValue()).isEqualTo("Solo table text");
    }

    @Test
    void regionlist_withoutTsr_emptyText_returnsNull() {
        // label 7 with empty text → null
        ObjectNode json = createHancomAIJson(
            createObject(7, "", 100, 100, 500, 200)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).isEmpty();
    }

    // --- Task E: regionlistStrategy ---

    @Test
    void regionlistStrategy_listOnly_withTsrOverlap_stillBecomesList() {
        // list-only strategy: label 7 with overlapping TSR should NOT be skipped
        // — it should become a PDFList regardless of TSR presence
        transformer.setRegionlistStrategy(HybridConfig.REGIONLIST_LIST_ONLY);

        ObjectNode regionObj = createObject(7, "Row 1\nRow 2\nRow 3", 100, 100, 500, 300);

        ObjectNode tsrCell = createTsrCell(0, 0, 1, 1, "cell", 100, 100, 500, 300);
        double[] tableBbox = {100, 100, 500, 300};
        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{regionObj},
            tableBbox,
            new ObjectNode[]{tsrCell}
        );

        List<List<IObject>> result = transform(json);

        // Should find a PDFList from label 7 (not skipped despite TSR overlap)
        boolean hasList = result.get(0).stream().anyMatch(o -> o instanceof PDFList);
        assertThat(hasList).isTrue();

        PDFList list = (PDFList) result.get(0).stream()
            .filter(o -> o instanceof PDFList).findFirst().orElse(null);
        assertThat(list).isNotNull();
        assertThat(list.getListItems()).hasSize(3);
        assertThat(list.getListItems().get(0).getFirstLine().getValue()).isEqualTo("Row 1");
    }

    @Test
    void regionlistStrategy_tableFirst_withTsrOverlap_skipsLabel7() {
        // table-first (default): label 7 with TSR overlap is skipped (existing behavior)
        transformer.setRegionlistStrategy(HybridConfig.REGIONLIST_TABLE_FIRST);

        ObjectNode regionObj = createObject(7, "Table data here", 100, 100, 500, 300);

        ObjectNode tsrCell = createTsrCell(0, 0, 1, 1, "cell", 100, 100, 500, 300);
        double[] tableBbox = {100, 100, 500, 300};
        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{regionObj},
            tableBbox,
            new ObjectNode[]{tsrCell}
        );

        List<List<IObject>> result = transform(json);

        // No PDFList from label 7 — only the TableBorder from TSR
        for (IObject obj : result.get(0)) {
            assertThat(obj).isNotInstanceOf(PDFList.class);
        }
        assertThat(result.get(0).stream().anyMatch(o -> o instanceof TableBorder)).isTrue();
    }

    @Test
    void regionlistStrategy_defaultIsTableFirst() {
        // Default strategy should be table-first
        HancomAISchemaTransformer freshTransformer = new HancomAISchemaTransformer();
        assertThat(freshTransformer.getRegionlistStrategy()).isEqualTo(HybridConfig.REGIONLIST_TABLE_FIRST);
    }

    // --- Label 9 (Table) handled by TSR, returns null from transformObject ---

    @Test
    void label9_table_returnsNull() {
        // label 9 = Table → handled by transformTablePage(), transformObject returns null
        ObjectNode json = createHancomAIJson(
            createObject(9, "Some table text", 100, 100, 500, 300)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).isEmpty();
    }

    // --- Label 10 (Figure) creates SemanticPicture ---

    @Test
    void label10_figure_createsPicture() {
        // label 10 = Figure → SemanticPicture
        ObjectNode json = createHancomAIJson(
            createObject(10, "", 100, 100, 500, 400)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        assertThat(result.get(0).get(0)).isInstanceOf(SemanticPicture.class);
    }

    // --- Task 8: confidence → correctSemanticScore ---

    @Test
    void confidence_mappedToCorrectSemanticScore() {
        // object with confidence 0.85 → correctSemanticScore = 0.85
        ObjectNode obj = createObjectWithConfidence(2, "Test paragraph", 100, 100, 500, 130, 0.85);
        ObjectNode json = createHancomAIJson(obj);

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        SemanticParagraph para = (SemanticParagraph) result.get(0).get(0);
        assertThat(para.getCorrectSemanticScore()).isEqualTo(0.85);
    }

    @Test
    void confidence_defaultsTo1WhenMissing() {
        // object without confidence field → correctSemanticScore = 1.0
        ObjectNode obj = objectMapper.createObjectNode();
        obj.put("label", 2);
        obj.put("ocrtext", "No confidence field");
        ArrayNode bbox = obj.putArray("bbox");
        bbox.add(100); bbox.add(100); bbox.add(500); bbox.add(130);

        ObjectNode json = createHancomAIJson(obj);

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        SemanticParagraph para = (SemanticParagraph) result.get(0).get(0);
        assertThat(para.getCorrectSemanticScore()).isEqualTo(1.0);
    }

    @Test
    void confidence_appliedToHeading() {
        // heading with confidence 0.72 → correctSemanticScore = 0.72
        ObjectNode obj = createObjectWithConfidence(0, "Title", 100, 50, 500, 100, 0.72);
        ObjectNode json = createHancomAIJson(obj);

        List<List<IObject>> result = transform(json);

        SemanticHeading heading = (SemanticHeading) result.get(0).get(0);
        assertThat(heading.getCorrectSemanticScore()).isEqualTo(0.72);
    }

    // --- Task D: Array rowspan/colspan and crop-to-page bbox conversion ---

    @Test
    void arrayRowspan_parsedCorrectly() {
        // rowspan [1, 2] → startRow=1, rowspan=2
        // colspan [0] → startCol=0, colspan=1
        // This cell spans rows 1 and 2, column 0 only
        ObjectNode cell = objectMapper.createObjectNode();
        cell.put("cell_id", 3);
        ArrayNode rs = cell.putArray("rowspan");
        rs.add(1); rs.add(2);
        ArrayNode cs = cell.putArray("colspan");
        cs.add(0);
        ArrayNode bbox = cell.putArray("bbox");
        bbox.add(21); bbox.add(126); bbox.add(143); bbox.add(237);

        // Also need a cell at (0,0) to make a valid table
        ObjectNode cell00 = objectMapper.createObjectNode();
        cell00.put("cell_id", 0);
        ArrayNode rs00 = cell00.putArray("rowspan");
        rs00.add(0);
        ArrayNode cs00 = cell00.putArray("colspan");
        cs00.add(0);
        ArrayNode bbox00 = cell00.putArray("bbox");
        bbox00.add(21); bbox00.add(15); bbox00.add(143); bbox00.add(126);

        // Build TSR entry
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
        ArrayNode dlaPages = dlaOcr.addArray();
        ObjectNode dlaPage = dlaPages.addObject();
        dlaPage.put("page_number", 0);
        dlaPage.put("image_height", 3508);
        dlaPage.putArray("objects");

        ArrayNode tsr = json.putArray("TABLE_STRUCTURE_RECOGNITION");
        ObjectNode tableEntry = tsr.addObject();
        tableEntry.put("page_number", 0);
        ArrayNode dlaBbox = tableEntry.putArray("dla_bbox");
        dlaBbox.add(183); dlaBbox.add(607); dlaBbox.add(1590); dlaBbox.add(1533);
        ObjectNode tsrObj = tableEntry.putObject("tsr");
        tsrObj.put("page_number", 0);
        tsrObj.put("num_cells", 2);
        ArrayNode tbbox = tsrObj.putArray("table_bbox");
        tbbox.add(21); tbbox.add(13); tbbox.add(1349); tbbox.add(875);
        ArrayNode cellsArray = tsrObj.putArray("cells");
        cellsArray.add(cell00);
        cellsArray.add(cell);

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();
        // Table should have 3 rows (indices 0, 1, 2) and 1 column
        assertThat(table.getNumberOfRows()).isEqualTo(3);
        assertThat(table.getNumberOfColumns()).isEqualTo(1);

        // Cell at (1, 0) should span 2 rows
        TableBorderCell spanCell = table.getCell(1, 0);
        assertThat(spanCell).isNotNull();
        assertThat(spanCell.getRowSpan()).isEqualTo(2);
        assertThat(spanCell.getColSpan()).isEqualTo(1);
    }

    @Test
    void cropToPageBboxConversion_withKnownOffset() {
        // DLA bbox (crop origin): [100, 200, 500, 600] in page pixels
        // Cell bbox in crop coords: [10, 20, 90, 80]
        // Expected page-level pixel coords: [110, 220, 190, 280]
        // Expected PDF points: [110*0.24, pageH - 280*0.24, 190*0.24, pageH - 220*0.24]
        double[] dlaBbox = {100, 200, 500, 600};
        double[] tsrTableBbox = {0, 0, 400, 400};  // crop-relative

        ObjectNode cell = objectMapper.createObjectNode();
        cell.put("cell_id", 0);
        ArrayNode rs = cell.putArray("rowspan");
        rs.add(0);
        ArrayNode cs = cell.putArray("colspan");
        cs.add(0);
        ArrayNode bbox = cell.putArray("bbox");
        bbox.add(10); bbox.add(20); bbox.add(90); bbox.add(80);

        ObjectNode json = createHancomAIJsonWithTableAndOffset(
            new ObjectNode[]{},
            dlaBbox,
            tsrTableBbox,
            new ObjectNode[]{cell}
        );

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();

        // Cell bbox should be converted from crop to page coords
        TableBorderCell c = table.getCell(0, 0);
        assertThat(c).isNotNull();
        assertThat(c.getBoundingBox()).isNotNull();

        // Page pixel coords: left=110, top=220, right=190, bottom=280
        // PDF points: left=110*0.24=26.4, right=190*0.24=45.6
        // topY = 842 - 220*0.24 = 842 - 52.8 = 789.2
        // bottomY = 842 - 280*0.24 = 842 - 67.2 = 774.8
        double pageHeight = 842.0;
        double expectedLeft = 110.0 * 72.0 / 300.0;
        double expectedRight = 190.0 * 72.0 / 300.0;
        double expectedTopY = pageHeight - (220.0 * 72.0 / 300.0);
        double expectedBottomY = pageHeight - (280.0 * 72.0 / 300.0);

        assertThat(c.getBoundingBox().getLeftX()).isEqualTo(expectedLeft, org.assertj.core.api.Assertions.within(0.01));
        assertThat(c.getBoundingBox().getRightX()).isEqualTo(expectedRight, org.assertj.core.api.Assertions.within(0.01));
        assertThat(c.getBoundingBox().getTopY()).isEqualTo(expectedTopY, org.assertj.core.api.Assertions.within(0.01));
        assertThat(c.getBoundingBox().getBottomY()).isEqualTo(expectedBottomY, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    void backwardCompat_integerRowspanColspan_stillWorks() {
        // Test backward compatibility with integer rowspan/colspan format
        ObjectNode cell = objectMapper.createObjectNode();
        cell.put("row", 0);
        cell.put("col", 0);
        cell.put("rowspan", 2);
        cell.put("colspan", 1);
        cell.put("text", "Merged cell");
        ArrayNode bbox = cell.putArray("bbox");
        bbox.add(100); bbox.add(100); bbox.add(300); bbox.add(300);

        ObjectNode cell2 = objectMapper.createObjectNode();
        cell2.put("row", 0);
        cell2.put("col", 1);
        cell2.put("rowspan", 1);
        cell2.put("colspan", 1);
        cell2.put("text", "Normal cell");
        ArrayNode bbox2 = cell2.putArray("bbox");
        bbox2.add(300); bbox2.add(100); bbox2.add(500); bbox2.add(200);

        double[] dlaBbox = {100, 100, 500, 300};

        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
        ArrayNode dlaPages = dlaOcr.addArray();
        ObjectNode dlaPage = dlaPages.addObject();
        dlaPage.put("page_number", 0);
        dlaPage.put("image_height", 3508);
        dlaPage.putArray("objects");

        ArrayNode tsr = json.putArray("TABLE_STRUCTURE_RECOGNITION");
        ObjectNode tableEntry = tsr.addObject();
        tableEntry.put("page_number", 0);
        ArrayNode dlaBboxArr = tableEntry.putArray("dla_bbox");
        for (double v : dlaBbox) dlaBboxArr.add(v);
        ObjectNode tsrObj = tableEntry.putObject("tsr");
        tsrObj.put("page_number", 0);
        tsrObj.put("num_cells", 2);
        ArrayNode tbbox = tsrObj.putArray("table_bbox");
        tbbox.add(0); tbbox.add(0); tbbox.add(400); tbbox.add(200);
        ArrayNode cellsArray = tsrObj.putArray("cells");
        cellsArray.add(cell);
        cellsArray.add(cell2);

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();
        assertThat(table.getNumberOfRows()).isEqualTo(2);
        assertThat(table.getNumberOfColumns()).isEqualTo(2);

        TableBorderCell mergedCell = table.getCell(0, 0);
        assertThat(mergedCell).isNotNull();
        assertThat(mergedCell.getRowSpan()).isEqualTo(2);
        assertThat(mergedCell.getColSpan()).isEqualTo(1);
    }

    @Test
    void multipleTablesPerPage_allProduced() {
        // Two tables on the same page
        ObjectNode json = objectMapper.createObjectNode();
        ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
        ArrayNode dlaPages = dlaOcr.addArray();
        ObjectNode dlaPage = dlaPages.addObject();
        dlaPage.put("page_number", 0);
        dlaPage.put("image_height", 3508);
        dlaPage.putArray("objects");

        ArrayNode tsr = json.putArray("TABLE_STRUCTURE_RECOGNITION");

        // Table 1
        ObjectNode entry1 = tsr.addObject();
        entry1.put("page_number", 0);
        entry1.put("object_id", 1);
        ArrayNode dlaBbox1 = entry1.putArray("dla_bbox");
        dlaBbox1.add(100); dlaBbox1.add(100); dlaBbox1.add(500); dlaBbox1.add(300);
        ObjectNode tsr1 = entry1.putObject("tsr");
        tsr1.put("page_number", 0);
        tsr1.put("num_cells", 1);
        ArrayNode tbbox1 = tsr1.putArray("table_bbox");
        tbbox1.add(0); tbbox1.add(0); tbbox1.add(400); tbbox1.add(200);
        ArrayNode cells1 = tsr1.putArray("cells");
        ObjectNode c1 = cells1.addObject();
        c1.put("cell_id", 0);
        ArrayNode rs1 = c1.putArray("rowspan"); rs1.add(0);
        ArrayNode cs1 = c1.putArray("colspan"); cs1.add(0);
        ArrayNode b1 = c1.putArray("bbox");
        b1.add(0); b1.add(0); b1.add(400); b1.add(200);

        // Table 2
        ObjectNode entry2 = tsr.addObject();
        entry2.put("page_number", 0);
        entry2.put("object_id", 2);
        ArrayNode dlaBbox2 = entry2.putArray("dla_bbox");
        dlaBbox2.add(100); dlaBbox2.add(500); dlaBbox2.add(500); dlaBbox2.add(700);
        ObjectNode tsr2 = entry2.putObject("tsr");
        tsr2.put("page_number", 0);
        tsr2.put("num_cells", 1);
        ArrayNode tbbox2 = tsr2.putArray("table_bbox");
        tbbox2.add(0); tbbox2.add(0); tbbox2.add(400); tbbox2.add(200);
        ArrayNode cells2 = tsr2.putArray("cells");
        ObjectNode c2 = cells2.addObject();
        c2.put("cell_id", 0);
        ArrayNode rs2 = c2.putArray("rowspan"); rs2.add(0);
        ArrayNode cs2 = c2.putArray("colspan"); cs2.add(0);
        ArrayNode b2 = c2.putArray("bbox");
        b2.add(0); b2.add(0); b2.add(400); b2.add(200);

        List<List<IObject>> result = transform(json);

        long tableCount = result.get(0).stream().filter(o -> o instanceof TableBorder).count();
        assertThat(tableCount).isEqualTo(2);
    }

    // --- Task G: ElementMetadata ---

    @Test
    void metadata_paragraphHasConfidenceAndSourceLabel() {
        ObjectNode obj = createObjectWithConfidence(2, "Test paragraph", 100, 100, 500, 130, 0.88);
        ObjectNode json = createHancomAIJson(obj);

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        IObject iobj = result.get(0).get(0);
        assertThat(iobj.getRecognizedStructureId()).isNotNull();

        Map<Long, ElementMetadata> metadata = transformer.getElementMetadata();
        assertThat(metadata).containsKey(iobj.getRecognizedStructureId());

        ElementMetadata meta = metadata.get(iobj.getRecognizedStructureId());
        assertThat(meta.getConfidence()).isEqualTo(0.88);
        assertThat(meta.getSourceLabel()).isEqualTo(2);
        assertThat(meta.getHeadingInferenceMethod()).isNull();
    }

    @Test
    void metadata_headingHasBboxHeightInference() {
        ObjectNode json = createHancomAIJson(
            createObjectWithConfidence(1, "Section Title", 100, 100, 500, 160, 0.92)
        );

        List<List<IObject>> result = transform(json);

        SemanticHeading heading = (SemanticHeading) result.get(0).get(0);
        Map<Long, ElementMetadata> metadata = transformer.getElementMetadata();
        ElementMetadata meta = metadata.get(heading.getRecognizedStructureId());

        assertThat(meta).isNotNull();
        assertThat(meta.getSourceLabel()).isEqualTo(1);
        assertThat(meta.getHeadingInferenceMethod()).isEqualTo("bbox-height");
        assertThat(meta.getBboxHeightPx()).isEqualTo(60.0);  // 160 - 100
    }

    @Test
    void metadata_titleHasFixedInference() {
        ObjectNode json = createHancomAIJson(
            createObject(0, "Document Title", 100, 50, 500, 100)
        );

        List<List<IObject>> result = transform(json);

        SemanticHeading heading = (SemanticHeading) result.get(0).get(0);
        Map<Long, ElementMetadata> metadata = transformer.getElementMetadata();
        ElementMetadata meta = metadata.get(heading.getRecognizedStructureId());

        assertThat(meta).isNotNull();
        assertThat(meta.getHeadingInferenceMethod()).isEqualTo("fixed");
        assertThat(meta.getBboxHeightPx()).isNull();
    }

    @Test
    void metadata_tableHasTsrMetadata() {
        ObjectNode tsrCell = createTsrCell(0, 0, 1, 1, "cell", 100, 100, 300, 200);
        double[] tableBbox = {100, 100, 300, 200};
        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{},
            tableBbox,
            new ObjectNode[]{tsrCell}
        );

        // Add num_cells to the tsr object
        // The helper already sets num_cells = tsrCells.length

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();

        Map<Long, ElementMetadata> metadata = transformer.getElementMetadata();
        ElementMetadata meta = metadata.get(table.getRecognizedStructureId());

        assertThat(meta).isNotNull();
        assertThat(meta.getSourceLabel()).isEqualTo(9);
        assertThat(meta.getTsr()).isNotNull();
        assertThat(meta.getTsr().getNumCells()).isEqualTo(1);
    }

    @Test
    void metadata_figureHasCaptionMetadata() {
        // Create figure with caption from FIGURE_CAPTIONS
        ObjectNode json = objectMapper.createObjectNode();

        // DLA+OCR with a figure object
        ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
        ArrayNode dlaPages = dlaOcr.addArray();
        ObjectNode page = dlaPages.addObject();
        page.put("page_number", 0);
        page.put("image_height", 3508);
        ArrayNode objects = page.putArray("objects");
        ObjectNode figObj = objects.addObject();
        figObj.put("label", 10);
        figObj.put("ocrtext", "");
        figObj.put("confidence", 0.91);
        figObj.put("object_id", 5);
        ArrayNode bbox = figObj.putArray("bbox");
        bbox.add(100); bbox.add(100); bbox.add(500); bbox.add(400);

        // FIGURE_CAPTIONS
        ArrayNode figureCaptions = json.putArray("FIGURE_CAPTIONS");
        ObjectNode cap = figureCaptions.addObject();
        cap.put("page_number", 0);
        cap.put("object_id", 5);
        cap.put("caption", "A beautiful chart");

        List<List<IObject>> result = transform(json);

        SemanticPicture pic = (SemanticPicture) result.get(0).get(0);
        Map<Long, ElementMetadata> metadata = transformer.getElementMetadata();
        ElementMetadata meta = metadata.get(pic.getRecognizedStructureId());

        assertThat(meta).isNotNull();
        assertThat(meta.getSourceLabel()).isEqualTo(10);
        assertThat(meta.getCaption()).isNotNull();
        assertThat(meta.getCaption().getText()).isEqualTo("A beautiful chart");
        assertThat(meta.getCaption().getLanguage()).isEqualTo("en");
    }

    @Test
    void metadata_regionlistHasResolution() {
        // label 7 without TSR → list-only style resolution
        ObjectNode json = createHancomAIJson(
            createObjectWithConfidence(7, "Line one\nLine two", 100, 100, 500, 200, 0.80)
        );

        List<List<IObject>> result = transform(json);

        assertThat(result.get(0)).hasSize(1);
        IObject iobj = result.get(0).get(0);

        Map<Long, ElementMetadata> metadata = transformer.getElementMetadata();
        ElementMetadata meta = metadata.get(iobj.getRecognizedStructureId());

        assertThat(meta).isNotNull();
        assertThat(meta.getSourceLabel()).isEqualTo(7);
        assertThat(meta.getRegionlistResolution()).isNotNull();
        assertThat(meta.getRegionlistResolution().getStrategy()).isEqualTo("table-first");
        assertThat(meta.getRegionlistResolution().isTsrAttempted()).isTrue();
        assertThat(meta.getRegionlistResolution().getTsrResult()).isEqualTo("no-cells");
    }

    @Test
    void metadata_clearedBetweenTransformCalls() {
        // First transform
        ObjectNode json1 = createHancomAIJson(
            createObject(2, "First call", 100, 100, 500, 130)
        );
        transform(json1);
        assertThat(transformer.getElementMetadata()).hasSize(1);

        // Second transform should clear previous metadata
        ObjectNode json2 = createHancomAIJson(
            createObject(2, "Second call A", 100, 100, 500, 130),
            createObject(2, "Second call B", 100, 200, 500, 230)
        );
        transform(json2);
        assertThat(transformer.getElementMetadata()).hasSize(2);
    }

    @Test
    void tableCellText_pageHeaderFooterWords_excluded() {
        // Words with label 14 (page header), 15 (page footer), 17 (page number) should not
        // be matched to cells even if their bbox overlaps
        ObjectNode headerWord = createObject(14, "HEADER", 120, 120, 280, 180);
        ObjectNode footerWord = createObject(15, "FOOTER", 120, 120, 280, 180);
        ObjectNode pageNumWord = createObject(17, "42", 120, 120, 280, 180);

        ObjectNode tsrCell = createTsrCell(0, 0, 1, 1, "Fallback", 100, 100, 300, 200);
        double[] tableBbox = {100, 100, 300, 200};

        ObjectNode json = createHancomAIJsonWithTable(
            new ObjectNode[]{headerWord, footerWord, pageNumWord},
            tableBbox,
            new ObjectNode[]{tsrCell}
        );

        List<List<IObject>> result = transform(json);

        TableBorder table = null;
        for (IObject obj : result.get(0)) {
            if (obj instanceof TableBorder) {
                table = (TableBorder) obj;
                break;
            }
        }
        assertThat(table).isNotNull();

        // Furniture words excluded, so falls back to TSR text
        assertThat(getCellText(table, 0, 0)).isEqualTo("Fallback");
    }
}

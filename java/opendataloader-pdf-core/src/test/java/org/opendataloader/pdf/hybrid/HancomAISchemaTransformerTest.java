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
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;

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
}

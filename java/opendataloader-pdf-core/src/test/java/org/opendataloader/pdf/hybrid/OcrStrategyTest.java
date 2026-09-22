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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for OCR strategy option (HybridConfig) and OCR word data exposure
 * (HancomAISchemaTransformer.getOcrWordsByPage).
 */
public class OcrStrategyTest {

    @Nested
    class HybridConfigOcrStrategy {

        @Test
        void defaultOcrStrategy_isAuto() {
            HybridConfig config = new HybridConfig();
            assertThat(config.getOcrStrategy()).isEqualTo(HybridConfig.OCR_AUTO);
        }

        @Test
        void setOcrStrategy_auto() {
            HybridConfig config = new HybridConfig();
            config.setOcrStrategy(HybridConfig.OCR_AUTO);
            assertThat(config.getOcrStrategy()).isEqualTo("auto");
            assertThat(config.isOcrAuto()).isTrue();
            assertThat(config.isOcrForce()).isFalse();
        }

        @Test
        void setOcrStrategy_force() {
            HybridConfig config = new HybridConfig();
            config.setOcrStrategy(HybridConfig.OCR_FORCE);
            assertThat(config.getOcrStrategy()).isEqualTo("force");
            assertThat(config.isOcrForce()).isTrue();
            assertThat(config.isOcrAuto()).isFalse();
        }

        @Test
        void setOcrStrategy_off() {
            HybridConfig config = new HybridConfig();
            config.setOcrStrategy(HybridConfig.OCR_AUTO);
            config.setOcrStrategy(HybridConfig.OCR_OFF);
            assertThat(config.getOcrStrategy()).isEqualTo("off");
            assertThat(config.isOcrAuto()).isFalse();
            assertThat(config.isOcrForce()).isFalse();
        }

        @Test
        void constants_haveExpectedValues() {
            assertThat(HybridConfig.OCR_OFF).isEqualTo("off");
            assertThat(HybridConfig.OCR_AUTO).isEqualTo("auto");
            assertThat(HybridConfig.OCR_FORCE).isEqualTo("force");
        }
    }

    @Nested
    class TransformerOcrWordsByPage {

        private HancomAISchemaTransformer transformer;
        private ObjectMapper objectMapper;

        @BeforeEach
        void setUp() {
            transformer = new HancomAISchemaTransformer();
            objectMapper = new ObjectMapper();
            StaticLayoutContainers.setCurrentContentId(1L);
        }

        @org.junit.jupiter.api.AfterEach
        void tearDown() {
            // Reset shared static state so later tests don't inherit our content IDs.
            StaticLayoutContainers.setCurrentContentId(1L);
        }

        @Test
        void ocrWordsByPage_populatedAfterTransform() {
            // Create a page with two DLA+OCR paragraph objects
            ObjectNode json = objectMapper.createObjectNode();
            ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
            ArrayNode pages = dlaOcr.addArray();
            ObjectNode page = pages.addObject();
            page.put("page_number", 0);
            page.put("image_height", 3508);
            ArrayNode objects = page.putArray("objects");

            // Paragraph object with words array
            ObjectNode obj = objects.addObject();
            obj.put("label", 2);
            obj.put("ocrtext", "Hello World");
            ArrayNode bbox = obj.putArray("bbox");
            bbox.add(100); bbox.add(100); bbox.add(500); bbox.add(130);
            ArrayNode words = obj.putArray("words");
            ObjectNode w1 = words.addObject();
            w1.put("text", "Hello");
            ArrayNode wb1 = w1.putArray("bbox");
            wb1.add(100); wb1.add(100); wb1.add(200); wb1.add(130);
            ObjectNode w2 = words.addObject();
            w2.put("text", "World");
            ArrayNode wb2 = w2.putArray("bbox");
            wb2.add(250); wb2.add(100); wb2.add(500); wb2.add(130);

            Map<Integer, Double> pageHeights = new HashMap<>();
            pageHeights.put(1, 842.0);

            HybridResponse response = new HybridResponse("", json, Collections.emptyMap());
            transformer.transform(response, pageHeights);

            Map<Integer, List<OcrWordInfo>> ocrWords = transformer.getOcrWordsByPage();
            assertThat(ocrWords).isNotNull();
            assertThat(ocrWords).containsKey(0);
            assertThat(ocrWords.get(0)).hasSize(2);
            assertThat(ocrWords.get(0).get(0).getText()).isEqualTo("Hello");
            assertThat(ocrWords.get(0).get(1).getText()).isEqualTo("World");
        }

        @Test
        void ocrWordsByPage_fallbackToObjectLevel_whenNoWordsArray() {
            // Object without words array → falls back to object-level ocrtext + bbox
            ObjectNode json = objectMapper.createObjectNode();
            ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
            ArrayNode pages = dlaOcr.addArray();
            ObjectNode page = pages.addObject();
            page.put("page_number", 0);
            page.put("image_height", 3508);
            ArrayNode objects = page.putArray("objects");

            ObjectNode obj = objects.addObject();
            obj.put("label", 2);
            obj.put("ocrtext", "Whole paragraph");
            ArrayNode bbox = obj.putArray("bbox");
            bbox.add(100); bbox.add(100); bbox.add(500); bbox.add(130);

            Map<Integer, Double> pageHeights = new HashMap<>();
            pageHeights.put(1, 842.0);

            HybridResponse response = new HybridResponse("", json, Collections.emptyMap());
            transformer.transform(response, pageHeights);

            Map<Integer, List<OcrWordInfo>> ocrWords = transformer.getOcrWordsByPage();
            assertThat(ocrWords).containsKey(0);
            assertThat(ocrWords.get(0)).hasSize(1);
            assertThat(ocrWords.get(0).get(0).getText()).isEqualTo("Whole paragraph");
        }

        @Test
        void ocrWordsByPage_excludesFurnitureLabels() {
            // Labels 14, 15, 17 should be excluded from OCR words
            ObjectNode json = objectMapper.createObjectNode();
            ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
            ArrayNode pages = dlaOcr.addArray();
            ObjectNode page = pages.addObject();
            page.put("page_number", 0);
            page.put("image_height", 3508);
            ArrayNode objects = page.putArray("objects");

            // Header (label 14) - should be excluded
            ObjectNode header = objects.addObject();
            header.put("label", 14);
            header.put("ocrtext", "Page Header");
            ArrayNode hbbox = header.putArray("bbox");
            hbbox.add(100); hbbox.add(10); hbbox.add(500); hbbox.add(30);

            // Actual content (label 2) - should be included
            ObjectNode content = objects.addObject();
            content.put("label", 2);
            content.put("ocrtext", "Content");
            ArrayNode cbbox = content.putArray("bbox");
            cbbox.add(100); cbbox.add(100); cbbox.add(500); cbbox.add(130);

            Map<Integer, Double> pageHeights = new HashMap<>();
            pageHeights.put(1, 842.0);

            HybridResponse response = new HybridResponse("", json, Collections.emptyMap());
            transformer.transform(response, pageHeights);

            Map<Integer, List<OcrWordInfo>> ocrWords = transformer.getOcrWordsByPage();
            assertThat(ocrWords.get(0)).hasSize(1);
            assertThat(ocrWords.get(0).get(0).getText()).isEqualTo("Content");
        }

        @Test
        void ocrWordsByPage_clearedBetweenTransformCalls() {
            // First transform
            ObjectNode json1 = createSimpleJson("First");
            HybridResponse response1 = new HybridResponse("", json1, Collections.emptyMap());
            Map<Integer, Double> pageHeights = new HashMap<>();
            pageHeights.put(1, 842.0);
            transformer.transform(response1, pageHeights);
            assertThat(transformer.getOcrWordsByPage().get(0)).hasSize(1);

            // Second transform should replace previous data
            ObjectNode json2 = createSimpleJson("Second");
            HybridResponse response2 = new HybridResponse("", json2, Collections.emptyMap());
            transformer.transform(response2, pageHeights);
            assertThat(transformer.getOcrWordsByPage().get(0)).hasSize(1);
            assertThat(transformer.getOcrWordsByPage().get(0).get(0).getText()).isEqualTo("Second");
        }

        @Test
        void ocrWordsByPage_wordInfoHasBbox() {
            ObjectNode json = objectMapper.createObjectNode();
            ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
            ArrayNode pages = dlaOcr.addArray();
            ObjectNode page = pages.addObject();
            page.put("page_number", 0);
            page.put("image_height", 3508);
            ArrayNode objects = page.putArray("objects");

            ObjectNode obj = objects.addObject();
            obj.put("label", 2);
            obj.put("ocrtext", "Test");
            ArrayNode bbox = obj.putArray("bbox");
            bbox.add(100); bbox.add(200); bbox.add(300); bbox.add(250);

            Map<Integer, Double> pageHeights = new HashMap<>();
            pageHeights.put(1, 842.0);

            HybridResponse response = new HybridResponse("", json, Collections.emptyMap());
            transformer.transform(response, pageHeights);

            OcrWordInfo word = transformer.getOcrWordsByPage().get(0).get(0);
            assertThat(word.getBbox()).isNotNull();
            // Verify bbox was converted from pixel to PDF points
            // left = 100 * 72/300 = 24.0, right = 300 * 72/300 = 72.0
            assertThat(word.getBbox().getLeftX()).isCloseTo(24.0, org.assertj.core.api.Assertions.within(0.01));
            assertThat(word.getBbox().getRightX()).isCloseTo(72.0, org.assertj.core.api.Assertions.within(0.01));
        }

        private ObjectNode createSimpleJson(String text) {
            ObjectNode json = objectMapper.createObjectNode();
            ArrayNode dlaOcr = json.putArray("DOCUMENT_LAYOUT_WITH_OCR");
            ArrayNode pages = dlaOcr.addArray();
            ObjectNode page = pages.addObject();
            page.put("page_number", 0);
            page.put("image_height", 3508);
            ArrayNode objects = page.putArray("objects");
            ObjectNode obj = objects.addObject();
            obj.put("label", 2);
            obj.put("ocrtext", text);
            ArrayNode bbox = obj.putArray("bbox");
            bbox.add(100); bbox.add(100); bbox.add(500); bbox.add(130);
            return json;
        }
    }

    @Nested
    class HybridSchemaTransformerDefault {

        @Test
        void defaultGetOcrWordsByPage_returnsEmptyMap() {
            // The interface default should return empty map
            HybridSchemaTransformer transformer = new HybridSchemaTransformer() {
                @Override
                public List<List<IObject>> transform(HybridResponse response, Map<Integer, Double> pageHeights) {
                    return Collections.emptyList();
                }

                @Override
                public List<IObject> transformPage(int pageNumber, com.fasterxml.jackson.databind.JsonNode pageContent, double pageHeight) {
                    return Collections.emptyList();
                }

                @Override
                public String getBackendType() {
                    return "test";
                }
            };

            assertThat(transformer.getOcrWordsByPage()).isEmpty();
        }
    }
}

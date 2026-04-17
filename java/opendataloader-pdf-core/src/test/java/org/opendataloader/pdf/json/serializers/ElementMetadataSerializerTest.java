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
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.hybrid.ElementMetadata;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ElementMetadataSerializerTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(SemanticHeading.class, new HeadingSerializer(SemanticHeading.class));
        module.addSerializer(Double.class, new DoubleSerializer(Double.class));
        objectMapper.registerModule(module);
    }

    @AfterEach
    void tearDown() {
        SerializerUtil.clearElementMetadata();
    }

    private SemanticHeading createHeading(long id) {
        SemanticHeading heading = new SemanticHeading();
        heading.setBoundingBox(new BoundingBox(0, 0, 0, 100, 100));
        heading.setRecognizedStructureId(id);
        heading.setHeadingLevel(2);
        return heading;
    }

    @Test
    void testMetadataWrittenWhenPresent() throws JsonProcessingException {
        long id = 42L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        metadata.put(id, new ElementMetadata()
                .setConfidence(0.85)
                .setSourceLabel(3));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertTrue(json.contains("\"confidence\":0.85"), "Should contain confidence field");
        assertTrue(json.contains("\"source label\":3"), "Should contain source label field");
    }

    @Test
    void testNoMetadataFieldsWhenMapEmpty() throws JsonProcessingException {
        // No metadata set — backward compat
        SemanticHeading heading = createHeading(42L);
        String json = objectMapper.writeValueAsString(heading);

        assertFalse(json.contains("\"confidence\""), "Should not contain confidence without metadata");
        assertFalse(json.contains("\"source label\""), "Should not contain source label without metadata");
    }

    @Test
    void testConfidenceOmittedWhenDefault() throws JsonProcessingException {
        long id = 10L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        metadata.put(id, new ElementMetadata()
                .setConfidence(1.0)
                .setSourceLabel(5));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertFalse(json.contains("\"confidence\""), "confidence=1.0 should be omitted");
        assertTrue(json.contains("\"source label\":5"), "source label should still appear");
    }

    @Test
    void testSourceLabelOmittedWhenNegative() throws JsonProcessingException {
        long id = 11L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        metadata.put(id, new ElementMetadata()
                .setConfidence(0.5)
                .setSourceLabel(-1));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertTrue(json.contains("\"confidence\":0.5"));
        assertFalse(json.contains("\"source label\""), "sourceLabel=-1 should be omitted");
    }

    @Test
    void testHeadingInferenceMetadata() throws JsonProcessingException {
        long id = 20L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        metadata.put(id, new ElementMetadata()
                .setHeadingInferenceMethod("bbox-height")
                .setBboxHeightPx(24.5));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertTrue(json.contains("\"heading inference\""), "Should contain heading inference block");
        assertTrue(json.contains("\"method\":\"bbox-height\""));
        assertTrue(json.contains("\"bbox height px\":24.5"));
    }

    @Test
    void testTsrMetadata() throws JsonProcessingException {
        long id = 30L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        ElementMetadata.TsrMetadata tsr = new ElementMetadata.TsrMetadata()
                .setNumCells(12)
                .setHtml("<table><tr><td>a</td></tr></table>")
                .setRunTimeMs(150);
        metadata.put(id, new ElementMetadata().setTsr(tsr));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertTrue(json.contains("\"tsr\""));
        assertTrue(json.contains("\"num cells\":12"));
        assertTrue(json.contains("\"html\""));
        assertTrue(json.contains("\"run time ms\":150"));
    }

    @Test
    void testCaptionMetadata() throws JsonProcessingException {
        long id = 40L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        ElementMetadata.CaptionMetadata caption = new ElementMetadata.CaptionMetadata()
                .setText("Figure 1: Example")
                .setLanguage("en")
                .setRunTimeMs(200);
        metadata.put(id, new ElementMetadata().setCaption(caption));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertTrue(json.contains("\"caption\""));
        assertTrue(json.contains("\"text\":\"Figure 1: Example\""));
        assertTrue(json.contains("\"language\":\"en\""));
        assertTrue(json.contains("\"run time ms\":200"));
    }

    @Test
    void testRegionlistResolutionMetadata() throws JsonProcessingException {
        long id = 50L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        ElementMetadata.RegionlistResolution rl = new ElementMetadata.RegionlistResolution()
                .setStrategy("table-first")
                .setTsrAttempted(true)
                .setTsrResult("success");
        metadata.put(id, new ElementMetadata().setRegionlistResolution(rl));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertTrue(json.contains("\"regionlist resolution\""));
        assertTrue(json.contains("\"strategy\":\"table-first\""));
        assertTrue(json.contains("\"tsr attempted\":true"));
        assertTrue(json.contains("\"tsr result\":\"success\""));
    }

    @Test
    void testWordMatchMetadata() throws JsonProcessingException {
        long id = 60L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        metadata.put(id, new ElementMetadata()
                .setWordMatchMethod("bbox-intersection")
                .setMatchedWordCount(15));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertTrue(json.contains("\"word match\""));
        assertTrue(json.contains("\"method\":\"bbox-intersection\""));
        assertTrue(json.contains("\"matched words\":15"));
    }

    @Test
    void testNoMetadataForUnknownId() throws JsonProcessingException {
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        metadata.put(99L, new ElementMetadata().setConfidence(0.5));
        SerializerUtil.setElementMetadata(metadata);

        SemanticHeading heading = createHeading(1L); // different ID
        String json = objectMapper.writeValueAsString(heading);

        assertFalse(json.contains("\"confidence\""), "Should not write metadata for unmatched ID");
    }

    @Test
    void testClearElementMetadataRemovesState() throws JsonProcessingException {
        long id = 42L;
        Map<Long, ElementMetadata> metadata = new HashMap<>();
        metadata.put(id, new ElementMetadata().setConfidence(0.5));
        SerializerUtil.setElementMetadata(metadata);
        SerializerUtil.clearElementMetadata();

        SemanticHeading heading = createHeading(id);
        String json = objectMapper.writeValueAsString(heading);

        assertFalse(json.contains("\"confidence\""), "After clear, metadata should not be written");
    }
}

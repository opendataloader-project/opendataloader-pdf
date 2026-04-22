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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for ElementMetadata JSON serialization.
 */
public class ElementMetadataTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultValuesProduceEmptyJson() throws Exception {
        ElementMetadata meta = new ElementMetadata();
        String json = mapper.writeValueAsString(meta);
        assertEquals("{}", json);
    }

    @Test
    void onlyNonDefaultFieldsAppearInJson() throws Exception {
        ElementMetadata meta = new ElementMetadata()
                .setConfidence(0.85)
                .setSourceLabel(3)
                .setHeadingInferenceMethod("bbox-height")
                .setBboxHeightPx(24.5);

        String json = mapper.writeValueAsString(meta);
        JsonNode node = mapper.readTree(json);

        assertEquals(0.85, node.get("confidence").asDouble(), 0.001);
        assertEquals(3, node.get("sourceLabel").asInt());
        assertEquals("bbox-height", node.get("headingInferenceMethod").asText());
        assertEquals(24.5, node.get("bboxHeightPx").asDouble(), 0.001);

        // Default fields must be absent
        assertFalse(node.has("matchedWordCount"));
        assertFalse(node.has("tsr"));
        assertFalse(node.has("caption"));
        assertFalse(node.has("regionlistResolution"));
        assertFalse(node.has("wordMatchMethod"));
    }

    @Test
    void tsrMetadataSerializesCorrectly() throws Exception {
        ElementMetadata.TsrMetadata tsr = new ElementMetadata.TsrMetadata()
                .setNumCells(12)
                .setHtml("<table><tr><td>A</td></tr></table>")
                .setRunTimeMs(150);

        ElementMetadata meta = new ElementMetadata()
                .setSourceLabel(5)
                .setTsr(tsr);

        String json = mapper.writeValueAsString(meta);
        JsonNode node = mapper.readTree(json);

        assertTrue(node.has("tsr"));
        assertEquals(12, node.get("tsr").get("numCells").asInt());
        assertEquals(150, node.get("tsr").get("runTimeMs").asLong());
    }

    @Test
    void captionMetadataSerializesCorrectly() throws Exception {
        ElementMetadata.CaptionMetadata caption = new ElementMetadata.CaptionMetadata()
                .setText("Figure 1: Overview")
                .setLanguage("en");

        ElementMetadata meta = new ElementMetadata()
                .setCaption(caption);

        String json = mapper.writeValueAsString(meta);
        JsonNode node = mapper.readTree(json);

        assertTrue(node.has("caption"));
        assertEquals("Figure 1: Overview", node.get("caption").get("text").asText());
        assertEquals("en", node.get("caption").get("language").asText());
        // runTimeMs is default (0), should be absent
        assertFalse(node.get("caption").has("runTimeMs"));
    }

    @Test
    void regionlistResolutionSerializesCorrectly() throws Exception {
        ElementMetadata.RegionlistResolution resolution = new ElementMetadata.RegionlistResolution()
                .setStrategy("table-first")
                .setTsrAttempted(true)
                .setTsrResult("success");

        ElementMetadata meta = new ElementMetadata()
                .setRegionlistResolution(resolution);

        String json = mapper.writeValueAsString(meta);
        JsonNode node = mapper.readTree(json);

        assertTrue(node.has("regionlistResolution"));
        assertEquals("table-first", node.get("regionlistResolution").get("strategy").asText());
        assertTrue(node.get("regionlistResolution").get("tsrAttempted").asBoolean());
        assertEquals("success", node.get("regionlistResolution").get("tsrResult").asText());
    }

    @Test
    void fluentSettersReturnSameInstance() {
        ElementMetadata meta = new ElementMetadata();
        ElementMetadata returned = meta.setConfidence(0.5).setSourceLabel(2);
        assertTrue(meta == returned);
    }

    @Test
    void roundTripDeserialization() throws Exception {
        ElementMetadata original = new ElementMetadata()
                .setConfidence(0.92)
                .setSourceLabel(7)
                .setWordMatchMethod("bbox-intersection")
                .setMatchedWordCount(5);

        String json = mapper.writeValueAsString(original);
        ElementMetadata deserialized = mapper.readValue(json, ElementMetadata.class);

        assertEquals(original.getConfidence(), deserialized.getConfidence(), 0.001);
        assertEquals(original.getSourceLabel(), deserialized.getSourceLabel());
        assertEquals(original.getWordMatchMethod(), deserialized.getWordMatchMethod());
        assertEquals(original.getMatchedWordCount(), deserialized.getMatchedWordCount());
    }
}

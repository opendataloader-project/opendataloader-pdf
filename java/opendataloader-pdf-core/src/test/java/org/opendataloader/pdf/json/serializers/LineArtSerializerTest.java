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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.json.ObjectMapperHolder;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;

import static org.junit.jupiter.api.Assertions.*;

class LineArtSerializerTest {

    @Test
    void lineArtChunkIsNotSerializedAsImage() throws JsonProcessingException {
        // Verify that LineArtChunk no longer produces {"type":"image",...}.
        // The old LineArtSerializer wrote type=image, misleading RAG consumers
        // who expected an image source path that was never written.
        ObjectMapper objectMapper = ObjectMapperHolder.getObjectMapper();
        LineArtChunk chunk = new LineArtChunk(new BoundingBox(0, 0, 0, 100, 100));

        String json = objectMapper.writeValueAsString(chunk);

        assertFalse(json.contains("\"type\":\"image\""),
                "LineArtChunk must not be serialized with type=image after removing LineArtSerializer");
    }

    @Test
    void tableCellSerializerSkipsLineArtChunkChildren() throws JsonProcessingException {
        // Regression test: TableBorderProcessor can add LineArtChunk to a cell's contents
        // when the chunk overlaps a cell by ≤ LINE_ART_PERCENT (90%). Without the guard
        // in TableCellSerializer, Jackson would throw (no serializer) or emit POJO garbage.
        TableBorderCell cell = new TableBorderCell(0, 0, 1, 1, 0L);
        cell.setBoundingBox(new BoundingBox(0, 0.0, 0.0, 100.0, 100.0));
        cell.addContentObject(new LineArtChunk(new BoundingBox(0, 0.0, 0.0, 50.0, 50.0)));

        ObjectMapper objectMapper = ObjectMapperHolder.getObjectMapper();
        String json = objectMapper.writeValueAsString(cell);
        JsonNode node = objectMapper.readTree(json);

        // kids array must be empty — the only child was a LineArtChunk
        JsonNode kids = node.get("kids");
        assertNotNull(kids, "kids field must be present");
        assertTrue(kids.isArray() && kids.isEmpty(),
                "TableBorderCell kids must be empty when its only child is a LineArtChunk");
        assertFalse(json.contains("lineChunks"),
                "LineArtChunk POJO fields must not appear in serialized output");
    }
}

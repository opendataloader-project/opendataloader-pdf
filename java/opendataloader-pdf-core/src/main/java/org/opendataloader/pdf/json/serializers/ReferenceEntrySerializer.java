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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.opendataloader.pdf.graph.ReferenceEntryNode;
import org.opendataloader.pdf.json.JsonName;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.IOException;

public class ReferenceEntrySerializer extends StdSerializer<ReferenceEntryNode> {

    public ReferenceEntrySerializer(Class<ReferenceEntryNode> t) {
        super(t);
    }

    @Override
    public void serialize(ReferenceEntryNode node, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField(JsonName.TYPE, JsonName.REFERENCE_ENTRY_TYPE);
        if (node.getRawId() != null) {
            gen.writeNumberField(JsonName.ID, node.getRawId());
        }
        if (node.getPage() != null) {
            gen.writeNumberField(JsonName.PAGE_NUMBER, node.getPage());
        }
        BoundingBox bbox = node.getBbox();
        if (bbox != null) {
            gen.writeArrayFieldStart(JsonName.BOUNDING_BOX);
            gen.writeNumber(bbox.getLeftX());
            gen.writeNumber(bbox.getBottomY());
            gen.writeNumber(bbox.getRightX());
            gen.writeNumber(bbox.getTopY());
            gen.writeEndArray();
        }
        if (node.getRefId() != null) {
            gen.writeStringField(JsonName.REF_ID, node.getRefId());
        }
        gen.writeStringField(JsonName.CONTENT, node.getText());
        if (node.getConfidence() != null) {
            gen.writeNumberField(JsonName.CONFIDENCE, node.getConfidence());
        }
        gen.writeEndObject();
    }
}

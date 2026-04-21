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
import org.opendataloader.pdf.graph.CitationNode;
import org.opendataloader.pdf.json.JsonName;

import java.io.IOException;

public class CitationSerializer extends StdSerializer<CitationNode> {

    public CitationSerializer(Class<CitationNode> t) {
        super(t);
    }

    @Override
    public void serialize(CitationNode node, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeStartObject();
        gen.writeStringField(JsonName.TYPE, JsonName.CITATION_TYPE);
        if (node.getRawId() != null) {
            gen.writeNumberField(JsonName.ID, node.getRawId());
        }
        if (node.getPage() != null) {
            gen.writeNumberField(JsonName.PAGE_NUMBER, node.getPage());
        }

        gen.writeArrayFieldStart(JsonName.MARKERS);
        for (String marker : node.getMarkers()) {
            gen.writeString(marker);
        }
        gen.writeEndArray();

        gen.writeArrayFieldStart(JsonName.RESOLVED_REF_IDS);
        for (String refId : node.getResolvedRefIds()) {
            gen.writeString(refId);
        }
        gen.writeEndArray();

        gen.writeStringField(JsonName.SPAN, node.getSpan());

        if (node.getUnresolvedReason() != null) {
            gen.writeStringField(JsonName.UNRESOLVED_REASON, node.getUnresolvedReason());
        }
        gen.writeEndObject();
    }
}

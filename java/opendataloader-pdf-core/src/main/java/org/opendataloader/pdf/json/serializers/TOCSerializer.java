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
import org.opendataloader.pdf.json.JsonName;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTOC;

import java.io.IOException;

public class TOCSerializer extends StdSerializer<SemanticTOC> {

    public TOCSerializer(Class<SemanticTOC> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticTOC toc, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, toc, JsonName.TOC_TYPE);
        if (toc.getPreviousTOCId() != null) {
            jsonGenerator.writeNumberField(JsonName.PREVIOUS_TOC_ID, toc.getPreviousTOCId());
        }
        if (toc.getNextTOCId() != null) {
            jsonGenerator.writeNumberField(JsonName.NEXT_TOC_ID, toc.getNextTOCId());
        }
        jsonGenerator.writeArrayFieldStart(JsonName.TOC_ITEMS);
        for (IObject child : toc.getTOCItems()) {
            jsonGenerator.writePOJO(child);
        }
        jsonGenerator.writeEndArray();
        SerializerUtil.writeMetadataIfPresent(jsonGenerator, toc);
        jsonGenerator.writeEndObject();
    }
}

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
import org.verapdf.wcag.algorithms.entities.SemanticCaption;

import java.io.IOException;

/**
 * Jackson serializer for SemanticCaption objects.
 * Serializes captions with their essential info and linked content ID.
 */
public class CaptionSerializer extends StdSerializer<SemanticCaption> {

    /**
     * Creates a new CaptionSerializer.
     *
     * @param t the class type for SemanticCaption
     */
    public CaptionSerializer(Class<SemanticCaption> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticCaption caption, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, caption, "caption");
        if (caption.getLinkedContentId() != null) {
            jsonGenerator.writeNumberField("linked content id", caption.getLinkedContentId());
        }
        SerializerUtil.writeTextInfo(jsonGenerator, caption);
        jsonGenerator.writeEndObject();
    }
}

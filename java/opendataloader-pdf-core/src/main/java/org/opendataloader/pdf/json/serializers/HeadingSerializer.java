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
import org.verapdf.wcag.algorithms.entities.SemanticHeading;

import java.io.IOException;

/**
 * Jackson serializer for SemanticHeading objects.
 * Serializes headings with their level and text content.
 */
public class HeadingSerializer extends StdSerializer<SemanticHeading> {

    /**
     * Creates a new HeadingSerializer.
     *
     * @param t the class type for SemanticHeading
     */
    public HeadingSerializer(Class<SemanticHeading> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticHeading heading, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, heading, JsonName.HEADING_TYPE);
        jsonGenerator.writeNumberField(JsonName.HEADING_LEVEL, heading.getHeadingLevel());
        SerializerUtil.writeTextInfo(jsonGenerator, heading);
        jsonGenerator.writeEndObject();
    }
}

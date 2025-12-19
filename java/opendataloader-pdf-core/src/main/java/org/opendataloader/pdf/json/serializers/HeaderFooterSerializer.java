/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.opendataloader.pdf.json.JsonName;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;

import java.io.IOException;

/**
 * Jackson serializer for SemanticHeaderOrFooter objects.
 * Serializes headers and footers with their child contents.
 */
public class HeaderFooterSerializer extends StdSerializer<SemanticHeaderOrFooter> {

    /**
     * Creates a new HeaderFooterSerializer.
     *
     * @param t the class type for SemanticHeaderOrFooter
     */
    public HeaderFooterSerializer(Class<SemanticHeaderOrFooter> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticHeaderOrFooter header, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, header, header.getSemanticType().getValue().toLowerCase());
        jsonGenerator.writeArrayFieldStart(JsonName.KIDS);
        for (IObject content : header.getContents()) {
            jsonGenerator.writePOJO(content);
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }
}

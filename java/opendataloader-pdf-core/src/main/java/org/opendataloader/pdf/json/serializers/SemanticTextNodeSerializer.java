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
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.io.IOException;

//For now this class is used to process headers, footers, headings, captions.
public class SemanticTextNodeSerializer extends StdSerializer<SemanticTextNode> {

    public SemanticTextNodeSerializer(Class<SemanticTextNode> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticTextNode textNode, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, textNode, textNode.getSemanticType().toString().toLowerCase());
        SerializerUtil.writeTextInfo(jsonGenerator, textNode);
        jsonGenerator.writeEndObject();
    }
}

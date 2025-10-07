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
import org.verapdf.wcag.algorithms.entities.SemanticCaption;

import java.io.IOException;

public class CaptionSerializer extends StdSerializer<SemanticCaption> {

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

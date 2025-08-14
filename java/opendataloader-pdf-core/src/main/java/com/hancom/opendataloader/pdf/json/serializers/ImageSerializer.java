/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.hancom.opendataloader.pdf.json.JsonName;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;

import java.io.IOException;

public class ImageSerializer extends StdSerializer<ImageChunk> {

    public ImageSerializer(Class<ImageChunk> t) {
        super(t);
    }

    @Override
    public void serialize(ImageChunk imageChunk, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, imageChunk, JsonName.IMAGE_CHUNK_TYPE);
        jsonGenerator.writeEndObject();
    }
}

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
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.json.JsonName;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;

import java.io.File;
import java.io.IOException;

public class ImageSerializer extends StdSerializer<ImageChunk> {

    public ImageSerializer(Class<ImageChunk> t) {
        super(t);
    }

    @Override
    public void serialize(ImageChunk imageChunk, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        String imageFormat = StaticLayoutContainers.getImageFormat();
        String fileName = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, imageChunk.getIndex(), imageFormat);
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, imageChunk, JsonName.IMAGE_CHUNK_TYPE);
        if (ImagesUtils.isImageFileExists(fileName)) {
            if (StaticLayoutContainers.isEmbedImages()) {
                File imageFile = new File(fileName);
                String dataUri = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                if (dataUri != null) {
                    jsonGenerator.writeStringField(JsonName.DATA, dataUri);
                    jsonGenerator.writeStringField(JsonName.IMAGE_FORMAT, imageFormat);
                }
            } else {
                jsonGenerator.writeStringField(JsonName.SOURCE, fileName);
            }
        }
        jsonGenerator.writeEndObject();
    }
}

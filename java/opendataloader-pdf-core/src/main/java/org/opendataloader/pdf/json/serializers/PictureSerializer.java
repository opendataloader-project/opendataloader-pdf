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
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.json.JsonName;
import org.opendataloader.pdf.markdown.MarkdownSyntax;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.ImagesUtils;

import java.io.File;
import java.io.IOException;

/**
 * JSON serializer for SemanticPicture elements.
 *
 * <p>Serializes pictures with their description (alt text) and image source.
 */
public class PictureSerializer extends StdSerializer<SemanticPicture> {

    public PictureSerializer(Class<SemanticPicture> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticPicture picture, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        String imageFormat = StaticLayoutContainers.getImageFormat();
        String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, picture.getPictureIndex(), imageFormat);
        String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", picture.getPictureIndex(), imageFormat);

        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, picture, JsonName.IMAGE_CHUNK_TYPE);

        // Write description if available
        if (picture.hasDescription()) {
            jsonGenerator.writeStringField(JsonName.DESCRIPTION, picture.getDescription());
        }

        if (ImagesUtils.isImageFileExists(absolutePath)) {
            if (StaticLayoutContainers.isEmbedImages()) {
                File imageFile = new File(absolutePath);
                String dataUri = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                if (dataUri != null) {
                    jsonGenerator.writeStringField(JsonName.DATA, dataUri);
                    jsonGenerator.writeStringField(JsonName.IMAGE_FORMAT, imageFormat);
                }
            } else {
                jsonGenerator.writeStringField(JsonName.SOURCE, relativePath);
            }
        }
        jsonGenerator.writeEndObject();
    }
}

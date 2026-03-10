/*
 * Copyright 2025 Hancom Inc.
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

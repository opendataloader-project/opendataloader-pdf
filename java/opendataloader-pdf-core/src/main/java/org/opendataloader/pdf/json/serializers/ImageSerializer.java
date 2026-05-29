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
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
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
        String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, imageChunk.getIndex(), imageFormat);
        String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", imageChunk.getIndex(), imageFormat);
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, imageChunk, JsonName.IMAGE_CHUNK_TYPE);
        // alt / alt_source policy (PDF/UA semantics):
        //   - original /Alt present → alt = original,    alt_source = "original"
        //   - no /Alt, AI caption   → alt = AI caption,  alt_source = "ai-generated"
        //   - neither present       → no alt field,      alt_source = "missing"
        // We never synthesize a placeholder like "Image N": PDF/UA forbids false
        // alternatives and a synthetic string defeats the evidence-report
        // signal a reviewer needs to find genuinely missing alt text.
        String alt = "";
        String altSource = "missing";
        if (imageChunk instanceof EnrichedImageChunk) {
            EnrichedImageChunk eic = (EnrichedImageChunk) imageChunk;
            alt = eic.sanitizeDescription();
            if (!alt.isEmpty()) {
                altSource = eic.getAltSource() == EnrichedImageChunk.AltSource.AI_GENERATED
                    ? "ai-generated" : "original";
            }
        }
        if (!alt.isEmpty()) {
            jsonGenerator.writeStringField(JsonName.ALT, alt);
        }
        jsonGenerator.writeStringField(JsonName.ALT_SOURCE, altSource);
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
        SerializerUtil.writeMetadataIfPresent(jsonGenerator, imageChunk);
        jsonGenerator.writeEndObject();
    }
}

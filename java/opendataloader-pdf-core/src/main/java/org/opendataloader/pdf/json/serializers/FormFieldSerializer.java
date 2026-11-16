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
import org.opendataloader.pdf.entities.SemanticFormField;
import org.opendataloader.pdf.json.JsonName;

import java.io.IOException;

/**
 * JSON serializer for {@link SemanticFormField}: a check box or a radio button of a tagged PDF.
 *
 * <pre>
 * {
 *   "type": "form field",
 *   "id": 3, "page number": 1, "bounding box": [...],
 *   "font": "Helvetica", "font size": 10.5, "text color": "[0.0]",
 *   "content": "Label",
 *   "field role": "check box",
 *   "checked": "on",
 *   "description": "Label"
 * }
 * </pre>
 *
 * <p>A label from the {@code Desc} attribute is not text of the page, so it has no font fields.
 */
public class FormFieldSerializer extends StdSerializer<SemanticFormField> {

    public FormFieldSerializer(Class<SemanticFormField> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticFormField field, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, field, JsonName.FORM_FIELD_TYPE);
        if (field.isLabelOnPage()) {
            SerializerUtil.writeTextInfo(jsonGenerator, field);
        } else {
            jsonGenerator.writeStringField(JsonName.CONTENT, field.getValue());
        }
        jsonGenerator.writeStringField(JsonName.FIELD_ROLE, field.getRole());
        jsonGenerator.writeStringField(JsonName.CHECKED, field.getChecked());
        if (field.getDescription() != null) {
            jsonGenerator.writeStringField(JsonName.DESCRIPTION, field.getDescription());
        }
        SerializerUtil.writeMetadataIfPresent(jsonGenerator, field);
        jsonGenerator.writeEndObject();
    }
}

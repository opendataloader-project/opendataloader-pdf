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
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;

import java.io.IOException;
import java.util.Arrays;

public class ListItemSerializer extends StdSerializer<ListItem> {

    public ListItemSerializer(Class<ListItem> t) {
        super(t);
    }

    @Override
    public void serialize(ListItem item, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, item, JsonName.LIST_ITEM_TYPE);
        jsonGenerator.writeStringField(JsonName.FONT_TYPE, item.getFirstLine().getFirstTextChunk().getFontName());
        jsonGenerator.writePOJOField(JsonName.FONT_SIZE, item.getFontSize());
        jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(
                item.getFirstLine().getFirstTextChunk().getFontColor()));
        jsonGenerator.writeStringField(JsonName.CONTENT, item.toString());
        jsonGenerator.writeArrayFieldStart(JsonName.KIDS);
        for (IObject content : item.getContents()) {
            if (!(content instanceof LineArtChunk)) {
                jsonGenerator.writePOJO(content);
            }
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }
}

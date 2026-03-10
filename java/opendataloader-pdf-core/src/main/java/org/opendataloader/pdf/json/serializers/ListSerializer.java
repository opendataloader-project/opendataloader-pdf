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
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;

import java.io.IOException;

public class ListSerializer extends StdSerializer<PDFList> {

    public ListSerializer(Class<PDFList> t) {
        super(t);
    }

    @Override
    public void serialize(PDFList list, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, list, JsonName.LIST_TYPE);
        jsonGenerator.writeStringField(JsonName.NUMBERING_STYLE, list.getNumberingStyle());
        jsonGenerator.writeNumberField(JsonName.NUMBER_OF_LIST_ITEMS, list.getNumberOfListItems());
        if (list.getPreviousListId() != null) {
            jsonGenerator.writeNumberField(JsonName.PREVIOUS_LIST_ID, list.getPreviousListId());
        }
        if (list.getNextListId() != null) {
            jsonGenerator.writeNumberField(JsonName.NEXT_LIST_ID, list.getNextListId());
        }
        jsonGenerator.writeArrayFieldStart(JsonName.LIST_ITEMS);
        for (ListItem item : list.getListItems()) {
            jsonGenerator.writePOJO(item);
        }

        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }
}

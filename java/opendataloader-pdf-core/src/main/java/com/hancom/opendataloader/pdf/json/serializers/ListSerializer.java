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

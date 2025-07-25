/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.layout.json.serializers;

import com.hancom.layout.json.JsonName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
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

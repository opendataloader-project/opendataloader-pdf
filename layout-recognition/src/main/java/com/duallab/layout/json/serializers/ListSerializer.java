package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;

import java.io.IOException;

public class ListSerializer extends StdSerializer<PDFList> {

	protected ListSerializer(Class<PDFList> t) {
		super(t);
	}

	@Override
	public void serialize(PDFList list, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.LIST_TYPE);
		jsonGenerator.writeNumberField(JsonName.ID, list.getRecognizedStructureId());
		jsonGenerator.writeStringField(JsonName.NUMBERING_STYLE, list.getNumberingStyle());
		jsonGenerator.writeNumberField(JsonName.NUMBER_OF_LIST_ITEMS, list.getNumberOfListItems());
		if (list.getPreviousListId() != null) {
			jsonGenerator.writeNumberField(JsonName.PREVIOUS_LIST_ID, list.getPreviousListId());
		}
		if (list.getNextListId() != null) {
			jsonGenerator.writeNumberField(JsonName.NEXT_LIST_ID, list.getNextListId());
		}
		SerializerUtil.writeEssentialInfo(jsonGenerator, list);
		jsonGenerator.writeArrayFieldStart(JsonName.LIST_ITEMS);
		for (ListItem item : list.getListItems()) {
			jsonGenerator.writePOJO(item);
		}

		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

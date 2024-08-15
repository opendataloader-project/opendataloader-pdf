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
		jsonGenerator.writeNumberField(JsonName.NUMBER_OF_CHILDREN, list.getNumberOfListItems());
		SerializerUtil.writeEssentialInfo(jsonGenerator, list);
		jsonGenerator.writeArrayFieldStart(JsonName.CHILDREN);
		for (ListItem item : list.getListItems()) {
			ObjectSerializer.serialize(jsonGenerator, item);
		}

		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

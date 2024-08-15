package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;

import java.io.IOException;
import java.util.Arrays;

public class ListItemSerializer extends StdSerializer<ListItem> {

	protected ListItemSerializer(Class<ListItem> t) {
		super(t);
	}

	@Override
	public void serialize(ListItem item, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.LIST_ITEM_TYPE);
		jsonGenerator.writeStringField(JsonName.FONT_TYPE, item.getFirstLine().getFirstTextChunk().getFontName());
		jsonGenerator.writeNumberField(JsonName.FONT_SIZE,  item.getFontSize());
		jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(
				item.getFirstLine().getFirstTextChunk().getFontColor()));
		jsonGenerator.writeStringField(JsonName.CONTENT, item.toString());
		SerializerUtil.writeEssentialInfo(jsonGenerator, item);
		jsonGenerator.writeEndObject();
	}
}

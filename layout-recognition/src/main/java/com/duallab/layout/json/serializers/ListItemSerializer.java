/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.json.serializers;

import com.duallab.layout.json.JsonName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.IObject;
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
		jsonGenerator.writeNumberField(JsonName.FONT_SIZE,  item.getFontSize());
		jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(
				item.getFirstLine().getFirstTextChunk().getFontColor()));
		jsonGenerator.writeStringField(JsonName.CONTENT, item.toString());
		jsonGenerator.writeArrayFieldStart(JsonName.KIDS);
		for (IObject content : item.getContents()) {
			jsonGenerator.writePOJO(content);
		}
		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

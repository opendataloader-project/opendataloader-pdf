package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;

import java.io.IOException;

public class TextChunkSerializer extends StdSerializer<TextChunk> {

	protected TextChunkSerializer(Class<TextChunk> t) {
		super(t);
	}

	@Override
	public void serialize(TextChunk textChunk, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.TEXT_CHUNK_TYPE);
		jsonGenerator.writeStringField(JsonName.CONTENT, textChunk.getValue());
		SerializerUtil.writeEssentialInfo(jsonGenerator, textChunk);
		jsonGenerator.writeEndObject();
	}
}

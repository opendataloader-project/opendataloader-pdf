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
import org.verapdf.wcag.algorithms.entities.content.TextChunk;

import java.io.IOException;

public class TextChunkSerializer extends StdSerializer<TextChunk> {

	public TextChunkSerializer(Class<TextChunk> t) {
		super(t);
	}

	@Override
	public void serialize(TextChunk textChunk, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		SerializerUtil.writeEssentialInfo(jsonGenerator, textChunk, JsonName.TEXT_CHUNK_TYPE);
		jsonGenerator.writeStringField(JsonName.CONTENT, textChunk.getValue());
		jsonGenerator.writeEndObject();
	}
}

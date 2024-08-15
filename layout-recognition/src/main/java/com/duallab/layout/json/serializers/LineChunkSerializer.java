package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;

import java.io.IOException;

public class LineChunkSerializer extends StdSerializer<LineChunk> {

	protected LineChunkSerializer(Class<LineChunk> t) {
		super(t);
	}

	@Override
	public void serialize(LineChunk lineChunk, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.LINE_CHUNK_TYPE);
		jsonGenerator.writeNumberField(JsonName.ID, lineChunk.getRecognizedStructureId());
		SerializerUtil.writeEssentialInfo(jsonGenerator, lineChunk);
		jsonGenerator.writeEndObject();
	}
}

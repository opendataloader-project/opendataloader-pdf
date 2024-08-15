package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;

import java.io.IOException;

public class LineArtSerializer extends StdSerializer<LineArtChunk> {

	protected LineArtSerializer(Class<LineArtChunk> t) {
		super(t);
	}

	@Override
	public void serialize(LineArtChunk lineArtChunk, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.IMAGE_CHUNK_TYPE);
		jsonGenerator.writeNumberField(JsonName.ID, lineArtChunk.getRecognizedStructureId());
		SerializerUtil.writeEssentialInfo(jsonGenerator,lineArtChunk);
//		if (!lineArtChunk.getLineChunks().isEmpty()) {
//			jsonGenerator.writeArrayFieldStart("lines");
//			for (LineChunk line : lineArtChunk.getLineChunks()) {
//				jsonGenerator.writeObject(line);
//			}
//			jsonGenerator.writeEndArray();
//		}
		jsonGenerator.writeEndObject();
	}
}

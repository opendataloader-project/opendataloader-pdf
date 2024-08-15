package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;

import java.io.IOException;

public class ImageSerializer extends StdSerializer<ImageChunk> {

	protected ImageSerializer(Class<ImageChunk> t) {
		super(t);
	}

	@Override
	public void serialize(ImageChunk imageChunk, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.IMAGE_CHUNK_TYPE);
		jsonGenerator.writeNumberField(JsonName.ID, imageChunk.getRecognizedStructureId());
		SerializerUtil.writeEssentialInfo(jsonGenerator, imageChunk);
		jsonGenerator.writeEndObject();
	}
}

package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;

import java.io.IOException;

public class HeadingSerializer extends StdSerializer<SemanticHeading> {

	protected HeadingSerializer(Class<SemanticHeading> t) {
		super(t);
	}

	@Override
	public void serialize(SemanticHeading heading, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.HEADING_TYPE);
		jsonGenerator.writeNumberField(JsonName.ID, heading.getRecognizedStructureId());
		jsonGenerator.writeNumberField(JsonName.HEADING_LEVEL, heading.getHeadingLevel());
		SerializerUtil.writeTextInfo(jsonGenerator, heading);
		SerializerUtil.writeEssentialInfo(jsonGenerator, heading);
		jsonGenerator.writeEndObject();
	}
}

package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;

import java.io.IOException;

public class HeaderFooterSerializer extends StdSerializer<SemanticHeaderOrFooter> {

	protected HeaderFooterSerializer(Class<SemanticHeaderOrFooter> t) {
		super(t);
	}

	@Override
	public void serialize(SemanticHeaderOrFooter header, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, header.getSemanticType().getValue());
		jsonGenerator.writeNumberField(JsonName.ID, header.getRecognizedStructureId());
		jsonGenerator.writeArrayFieldStart(JsonName.CHILDREN);
		for (IObject content : header.getContents()) {
			jsonGenerator.writePOJO(content);
		}
		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

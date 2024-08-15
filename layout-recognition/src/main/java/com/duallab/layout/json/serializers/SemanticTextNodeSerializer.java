package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.io.IOException;

//For now this class is used to process headers, footers, headings, captions.
public class SemanticTextNodeSerializer extends StdSerializer<SemanticTextNode> {

	protected SemanticTextNodeSerializer(Class<SemanticTextNode> t) {
		super(t);
	}

	@Override
	public void serialize(SemanticTextNode textNode, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, textNode.getSemanticType().toString().toLowerCase());
		jsonGenerator.writeNumberField(JsonName.ID, textNode.getRecognizedStructureId());
		SerializerUtil.writeTextInfo(jsonGenerator, textNode);
		SerializerUtil.writeEssentialInfo(jsonGenerator, textNode);
		jsonGenerator.writeEndObject();
	}
}

/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;

import java.io.IOException;

public class ParagraphSerializer extends StdSerializer<SemanticParagraph> {

	protected ParagraphSerializer(Class<SemanticParagraph> t) {
		super(t);
	}

	@Override
	public void serialize(SemanticParagraph textParagraph, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.PARAGRAPH_TYPE);
		jsonGenerator.writeNumberField(JsonName.ID, textParagraph.getRecognizedStructureId());
		SerializerUtil.writeTextInfo(jsonGenerator, textParagraph);
		SerializerUtil.writeEssentialInfo(jsonGenerator, textParagraph);
		jsonGenerator.writeEndObject();
	}
}

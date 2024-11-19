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
import org.verapdf.wcag.algorithms.entities.SemanticCaption;

import java.io.IOException;

public class CaptionSerializer extends StdSerializer<SemanticCaption> {

	protected CaptionSerializer(Class<SemanticCaption> t) {
		super(t);
	}

	@Override
	public void serialize(SemanticCaption caption, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, "Caption");
		jsonGenerator.writeNumberField(JsonName.ID, caption.getRecognizedStructureId());
		jsonGenerator.writeNumberField("linked content id", caption.getLinkedContentId());
		SerializerUtil.writeTextInfo(jsonGenerator, caption);
		SerializerUtil.writeEssentialInfo(jsonGenerator, caption);
		jsonGenerator.writeEndObject();
	}
}

/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.opendataloader.pdf.json.serializers;

import com.hancom.opendataloader.pdf.json.JsonName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;

import java.io.IOException;

public class HeadingSerializer extends StdSerializer<SemanticHeading> {

	public HeadingSerializer(Class<SemanticHeading> t) {
		super(t);
	}

	@Override
	public void serialize(SemanticHeading heading, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		SerializerUtil.writeEssentialInfo(jsonGenerator, heading, JsonName.HEADING_TYPE);
		jsonGenerator.writeNumberField(JsonName.HEADING_LEVEL, heading.getHeadingLevel());
		SerializerUtil.writeTextInfo(jsonGenerator, heading);
		jsonGenerator.writeEndObject();
	}
}

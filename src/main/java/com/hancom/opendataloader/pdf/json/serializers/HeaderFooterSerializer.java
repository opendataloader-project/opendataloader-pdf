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
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;

import java.io.IOException;

public class HeaderFooterSerializer extends StdSerializer<SemanticHeaderOrFooter> {

	public HeaderFooterSerializer(Class<SemanticHeaderOrFooter> t) {
		super(t);
	}

	@Override
	public void serialize(SemanticHeaderOrFooter header, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		SerializerUtil.writeEssentialInfo(jsonGenerator, header, header.getSemanticType().getValue().toLowerCase());
		jsonGenerator.writeArrayFieldStart(JsonName.KIDS);
		for (IObject content : header.getContents()) {
			jsonGenerator.writePOJO(content);
		}
		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

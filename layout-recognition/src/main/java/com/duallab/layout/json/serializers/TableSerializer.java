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
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.IOException;
public class TableSerializer extends StdSerializer<TableBorder> {

	protected TableSerializer(Class<TableBorder> t) {
		super(t);
	}

	@Override
	public void serialize(TableBorder table, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.TABLE_TYPE);
		jsonGenerator.writeNumberField(JsonName.ID, table.getRecognizedStructureId());
		jsonGenerator.writeNumberField(JsonName.NUMBER_OF_ROWS, table.getNumberOfRows());
		jsonGenerator.writeNumberField(JsonName.NUMBER_OF_COLUMNS, table.getNumberOfColumns());
		if (table.getPreviousTableId() != null) {
			jsonGenerator.writeNumberField(JsonName.PREVIOUS_TABLE_ID, table.getPreviousTableId());
		}
		if (table.getNextTableId() != null) {
			jsonGenerator.writeNumberField(JsonName.NEXT_TABLE_ID, table.getNextTableId());
		}
		SerializerUtil.writeEssentialInfo(jsonGenerator, table);
		jsonGenerator.writeArrayFieldStart(JsonName.ROWS);
		for (TableBorderRow row : table.getRows()) {
			jsonGenerator.writePOJO(row);
		}

		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

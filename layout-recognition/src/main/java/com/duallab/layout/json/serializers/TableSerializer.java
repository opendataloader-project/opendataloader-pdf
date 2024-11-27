/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.json.serializers;

import com.duallab.layout.json.JsonName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.IOException;
public class TableSerializer extends StdSerializer<TableBorder> {

	public TableSerializer(Class<TableBorder> t) {
		super(t);
	}

	@Override
	public void serialize(TableBorder table, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		SerializerUtil.writeEssentialInfo(jsonGenerator, table, table.isTextBlock() ? JsonName.TEXT_BLOCK : JsonName.TABLE_TYPE);
		if (table.isTextBlock()) {
			jsonGenerator.writeArrayFieldStart(JsonName.KIDS);
			for (IObject content : table.getCell(0, 0).getContents()) {
				jsonGenerator.writePOJO(content);
			}
			jsonGenerator.writeEndArray();
		} else {
			jsonGenerator.writeNumberField(JsonName.NUMBER_OF_ROWS, table.getNumberOfRows());
			jsonGenerator.writeNumberField(JsonName.NUMBER_OF_COLUMNS, table.getNumberOfColumns());
			if (table.getPreviousTableId() != null) {
				jsonGenerator.writeNumberField(JsonName.PREVIOUS_TABLE_ID, table.getPreviousTableId());
			}
			if (table.getNextTableId() != null) {
				jsonGenerator.writeNumberField(JsonName.NEXT_TABLE_ID, table.getNextTableId());
			}
			jsonGenerator.writeArrayFieldStart(JsonName.ROWS);
			for (TableBorderRow row : table.getRows()) {
				jsonGenerator.writePOJO(row);
			}
			jsonGenerator.writeEndArray();
		}
		jsonGenerator.writeEndObject();
	}
}

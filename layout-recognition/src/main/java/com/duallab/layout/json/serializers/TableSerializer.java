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
		SerializerUtil.writeEssentialInfo(jsonGenerator, table);
		jsonGenerator.writeArrayFieldStart(JsonName.ROWS);
		for (TableBorderRow row : table.getRows()) {
			ObjectSerializer.serialize(jsonGenerator, row);
		}

		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

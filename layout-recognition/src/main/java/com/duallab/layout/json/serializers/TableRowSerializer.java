package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.IOException;

public class TableRowSerializer extends StdSerializer<TableBorderRow> {

	protected TableRowSerializer(Class<TableBorderRow> t) {
		super(t);
	}

	@Override
	public void serialize(TableBorderRow row, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.ROW_TYPE);
		jsonGenerator.writeNumberField(JsonName.ROW_NUMBER, row.getRowNumber());
		jsonGenerator.writeArrayFieldStart(JsonName.CELLS);
		for (TableBorderCell cell : row.getCells()) {
			ObjectSerializer.serialize(jsonGenerator, cell);
		}

		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

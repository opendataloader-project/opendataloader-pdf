package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;

import java.io.IOException;

public class TableCellSerializer extends StdSerializer<TableBorderCell> {

	protected TableCellSerializer(Class<TableBorderCell> t) {
		super(t);
	}

	@Override
	public void serialize(TableBorderCell cell, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField(JsonName.TYPE, JsonName.TABLE_CELL_TYPE);
		jsonGenerator.writeNumberField(JsonName.ROW_NUMBER, cell.getRowNumber());
		jsonGenerator.writeNumberField(JsonName.COLUMN_NUMBER, cell.getColNumber());
		SerializerUtil.writeEssentialInfo(jsonGenerator, cell);
		jsonGenerator.writeArrayFieldStart(JsonName.CHILDREN);
		for (TableToken tableToken : cell.getContent()) {
			ObjectSerializer.serialize(jsonGenerator, tableToken);
		}

		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

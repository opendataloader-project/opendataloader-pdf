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
		jsonGenerator.writeNumberField(JsonName.ROW_NUMBER, row.getRowNumber() + 1);
		jsonGenerator.writeArrayFieldStart(JsonName.CELLS);
		TableBorderCell [] cells = row.getCells();
		for (int columnNumber = 0; columnNumber < cells.length; columnNumber++) {
			TableBorderCell cell = cells[columnNumber];
			if (cell.getColNumber() == columnNumber && cell.getRowNumber() == row.getRowNumber()) {
				jsonGenerator.writePOJO(cell);
			}
		}

		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

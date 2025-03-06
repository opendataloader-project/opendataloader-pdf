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
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.IOException;

public class TableRowSerializer extends StdSerializer<TableBorderRow> {

	public TableRowSerializer(Class<TableBorderRow> t) {
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

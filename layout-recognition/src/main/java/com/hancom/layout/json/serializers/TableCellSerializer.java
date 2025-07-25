/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.layout.json.serializers;

import com.hancom.layout.json.JsonName;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;

import java.io.IOException;

public class TableCellSerializer extends StdSerializer<TableBorderCell> {

	public TableCellSerializer(Class<TableBorderCell> t) {
		super(t);
	}

	@Override
	public void serialize(TableBorderCell cell, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		jsonGenerator.writeStartObject();
		SerializerUtil.writeEssentialInfo(jsonGenerator, cell, JsonName.TABLE_CELL_TYPE);
		jsonGenerator.writeNumberField(JsonName.ROW_NUMBER, cell.getRowNumber() + 1);
		jsonGenerator.writeNumberField(JsonName.COLUMN_NUMBER, cell.getColNumber() + 1);
		jsonGenerator.writeNumberField(JsonName.ROW_SPAN, cell.getRowSpan());
		jsonGenerator.writeNumberField(JsonName.COLUMN_SPAN, cell.getColSpan());
		jsonGenerator.writeArrayFieldStart(JsonName.KIDS);
		for (IObject content : cell.getContents()) {
			jsonGenerator.writePOJO(content);
		}
		jsonGenerator.writeEndArray();
		jsonGenerator.writeEndObject();
	}
}

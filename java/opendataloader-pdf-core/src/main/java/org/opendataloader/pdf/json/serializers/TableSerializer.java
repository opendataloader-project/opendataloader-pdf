/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.opendataloader.pdf.json.JsonName;
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

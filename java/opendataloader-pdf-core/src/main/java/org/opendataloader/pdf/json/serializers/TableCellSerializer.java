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

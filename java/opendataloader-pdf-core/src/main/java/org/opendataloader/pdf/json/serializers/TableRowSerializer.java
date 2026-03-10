/*
 * Copyright 2025 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.opendataloader.pdf.json.JsonName;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

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
        TableBorderCell[] cells = row.getCells();
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

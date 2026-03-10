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
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
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
                if (!(content instanceof LineArtChunk)) {
                    jsonGenerator.writePOJO(content);
                }
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

/*
 * Copyright 2025-2026 Hancom Inc.
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
            if (!(content instanceof LineArtChunk)) {
                jsonGenerator.writePOJO(content);
            }
        }
        jsonGenerator.writeEndArray();
        jsonGenerator.writeEndObject();
    }
}

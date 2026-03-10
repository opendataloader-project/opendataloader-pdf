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
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.json.JsonName;

import java.io.IOException;

/**
 * JSON serializer for SemanticFormula objects.
 *
 * <p>Produces JSON output in the format:
 * <pre>
 * {
 *   "type": "formula",
 *   "id": 123,
 *   "page number": 1,
 *   "bounding box": [x1, y1, x2, y2],
 *   "content": "\\frac{a}{b}"
 * }
 * </pre>
 */
public class FormulaSerializer extends StdSerializer<SemanticFormula> {

    public FormulaSerializer(Class<SemanticFormula> t) {
        super(t);
    }

    @Override
    public void serialize(SemanticFormula formula, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeStartObject();
        SerializerUtil.writeEssentialInfo(jsonGenerator, formula, JsonName.FORMULA_TYPE);
        jsonGenerator.writeStringField(JsonName.CONTENT, formula.getLatex());
        jsonGenerator.writeEndObject();
    }
}

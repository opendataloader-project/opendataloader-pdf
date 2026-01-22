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

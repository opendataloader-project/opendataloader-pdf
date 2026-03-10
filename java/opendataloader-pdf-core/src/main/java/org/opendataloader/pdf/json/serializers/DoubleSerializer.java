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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Jackson serializer for Double values.
 * Rounds double values to 3 decimal places for cleaner JSON output.
 */
public class DoubleSerializer extends StdSerializer<Double> {

    /**
     * Creates a new DoubleSerializer.
     *
     * @param t the class type for Double
     */
    public DoubleSerializer(Class<Double> t) {
        super(t);
    }

    private static final int DEFAULT_ROUNDING_VALUE = 3;

    @Override
    public void serialize(Double number, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
            throws IOException {
        jsonGenerator.writeNumber(round(number, DEFAULT_ROUNDING_VALUE));
    }

    private static double round(double value, int decimalPlaces) {
        if (decimalPlaces < 0) {
            throw new IllegalArgumentException();
        }

        BigDecimal bigDecimalValue = new BigDecimal(Double.toString(value));
        bigDecimalValue = bigDecimalValue.setScale(decimalPlaces, RoundingMode.HALF_UP);
        return bigDecimalValue.doubleValue();
    }
}

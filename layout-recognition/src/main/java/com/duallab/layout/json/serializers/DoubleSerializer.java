/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DoubleSerializer extends StdSerializer<Double> {

    protected DoubleSerializer(Class<Double> t) {
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

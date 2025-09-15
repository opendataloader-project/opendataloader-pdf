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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class DoubleSerializer extends StdSerializer<Double> {

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

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.opendataloader.pdf.json.JsonName;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.io.IOException;
import java.util.Arrays;

public class SerializerUtil {
    public static void writeEssentialInfo(JsonGenerator jsonGenerator, IObject object, String type) throws IOException {
        jsonGenerator.writeStringField(JsonName.TYPE, type);
        Long id = object.getRecognizedStructureId();
        if (id != null && id != 0L) {
            jsonGenerator.writeNumberField(JsonName.ID, id);
        }
        if (object.getLevel() != null) {
            jsonGenerator.writeStringField(JsonName.LEVEL, object.getLevel());
        }
        jsonGenerator.writeNumberField(JsonName.PAGE_NUMBER, object.getPageNumber() + 1);
        jsonGenerator.writeArrayFieldStart(JsonName.BOUNDING_BOX);
        jsonGenerator.writePOJO(object.getLeftX());
        jsonGenerator.writePOJO(object.getBottomY());
        jsonGenerator.writePOJO(object.getRightX());
        jsonGenerator.writePOJO(object.getTopY());
        jsonGenerator.writeEndArray();
    }

    public static void writeTextInfo(JsonGenerator jsonGenerator, SemanticTextNode textNode) throws IOException {
        jsonGenerator.writeStringField(JsonName.FONT_TYPE, textNode.getFontName());
        jsonGenerator.writePOJOField(JsonName.FONT_SIZE, textNode.getFontSize());
        try {
            double[] textColor = textNode.getTextColor();
            if (textColor != null) {
                jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(textColor));
            }
        } catch (NullPointerException e) {
            // Ignore - textColor not available for hybrid mode generated content
        }
        jsonGenerator.writeStringField(JsonName.CONTENT, textNode.getValue());
        if (textNode.isHiddenText()) {
            jsonGenerator.writeBooleanField(JsonName.HIDDEN_TEXT, true);
        }
    }
}

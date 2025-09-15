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
        if (object.getRecognizedStructureId() != null) {
            jsonGenerator.writeNumberField(JsonName.ID, object.getRecognizedStructureId());
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
        jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(textNode.getTextColor()));
        jsonGenerator.writeStringField(JsonName.CONTENT, textNode.getValue());
        if (textNode.isHiddenText()) {
            jsonGenerator.writeBooleanField(JsonName.HIDDEN_TEXT, true);
        }
    }
}

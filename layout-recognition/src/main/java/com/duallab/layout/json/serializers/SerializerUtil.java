package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.io.IOException;
import java.util.Arrays;

public class SerializerUtil {
    public static void writeEssentialInfo(JsonGenerator jsonGenerator, IObject object) throws IOException {
        jsonGenerator.writeNumberField(JsonName.PAGE_NUMBER, object.getPageNumber() + 1);
        jsonGenerator.writeArrayFieldStart(JsonName.BOUNDING_BOX);
        jsonGenerator.writePOJO(object.getLeftX());
        jsonGenerator.writePOJO(object.getBottomY());
        jsonGenerator.writePOJO(object.getRightX());
        jsonGenerator.writePOJO(object.getTopY());
        jsonGenerator.writeEndArray();
    }

    public static void writeTextInfo(JsonGenerator jsonGenerator, SemanticTextNode textNode) throws IOException {
        jsonGenerator.writeStringField(JsonName.FONT_TYPE, textNode.getFirstLine().getFirstTextChunk().getFontName());
        jsonGenerator.writePOJOField(JsonName.FONT_SIZE, textNode.getFirstLine().getFirstTextChunk().getFontSize());
        jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(textNode.getTextColor()));
        jsonGenerator.writeStringField(JsonName.CONTENT, textNode.getValue());
    }


}

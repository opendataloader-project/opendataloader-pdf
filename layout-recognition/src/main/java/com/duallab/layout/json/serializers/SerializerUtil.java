package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.io.IOException;
import java.util.Arrays;

public class SerializerUtil {
    public static void writeEssentialInfo(JsonGenerator generator, IObject object) throws IOException {
        generator.writeNumberField(JsonName.PAGE_NUMBER, object.getPageNumber());
        generator.writeArrayFieldStart(JsonName.BOUNDING_BOX);
        generator.writeNumber(object.getLeftX());
        generator.writeNumber(object.getBottomY());
        generator.writeNumber(object.getRightX());
        generator.writeNumber(object.getTopY());
        generator.writeEndArray();
    }

    public static void writeTextInfo(JsonGenerator jsonGenerator, SemanticTextNode textNode) throws IOException {
        jsonGenerator.writeStringField(JsonName.FONT_TYPE, textNode.getFirstLine().getFirstTextChunk().getFontName());
        jsonGenerator.writeNumberField(JsonName.FONT_SIZE,  textNode.getFirstLine().getFirstTextChunk()
                .getFontSize());
        jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(textNode.getTextColor()));
        jsonGenerator.writeStringField(JsonName.CONTENT, textNode.getValue());
    }
}

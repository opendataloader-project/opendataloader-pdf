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
import org.opendataloader.pdf.hybrid.ElementMetadata;
import org.opendataloader.pdf.json.JsonName;
import org.opendataloader.pdf.utils.TextNodeUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public class SerializerUtil {

    private static final ThreadLocal<Map<Long, ElementMetadata>> ELEMENT_METADATA =
            ThreadLocal.withInitial(Collections::emptyMap);

    public static void setElementMetadata(Map<Long, ElementMetadata> metadata) {
        ELEMENT_METADATA.set(metadata != null ? metadata : Collections.emptyMap());
    }

    public static void clearElementMetadata() {
        ELEMENT_METADATA.remove();
    }

    /**
     * Writes element-level metadata fields (confidence, source label, etc.) if available.
     * Call this before writeEndObject() in each serializer.
     */
    public static void writeMetadataIfPresent(JsonGenerator gen, IObject object) throws IOException {
        if (object == null || object.getRecognizedStructureId() == null) return;
        Map<Long, ElementMetadata> metadata = ELEMENT_METADATA.get();
        if (metadata.isEmpty()) return;

        ElementMetadata meta = metadata.get(object.getRecognizedStructureId());
        if (meta == null) return;

        if (meta.getConfidence() != 1.0) {
            gen.writeNumberField(JsonName.CONFIDENCE, meta.getConfidence());
        }
        if (meta.getSourceLabel() >= 0) {
            gen.writeNumberField(JsonName.SOURCE_LABEL, meta.getSourceLabel());
        }

        if (meta.getHeadingInferenceMethod() != null) {
            gen.writeObjectFieldStart("heading inference");
            gen.writeStringField("method", meta.getHeadingInferenceMethod());
            if (meta.getBboxHeightPx() != null) {
                gen.writeNumberField("bbox height px", meta.getBboxHeightPx());
            }
            gen.writeEndObject();
        }

        if (meta.getTsr() != null) {
            gen.writeObjectFieldStart("tsr");
            gen.writeNumberField("num cells", meta.getTsr().getNumCells());
            if (meta.getTsr().getHtml() != null && !meta.getTsr().getHtml().isEmpty()) {
                gen.writeStringField("html", meta.getTsr().getHtml());
            }
            if (meta.getTsr().getRunTimeMs() > 0) {
                gen.writeNumberField("run time ms", meta.getTsr().getRunTimeMs());
            }
            gen.writeEndObject();
        }

        if (meta.getCaption() != null) {
            gen.writeObjectFieldStart("caption");
            if (meta.getCaption().getText() != null) {
                gen.writeStringField("text", meta.getCaption().getText());
            }
            if (meta.getCaption().getLanguage() != null) {
                gen.writeStringField("language", meta.getCaption().getLanguage());
            }
            if (meta.getCaption().getRunTimeMs() > 0) {
                gen.writeNumberField("run time ms", meta.getCaption().getRunTimeMs());
            }
            gen.writeEndObject();
        }

        if (meta.getRegionlistResolution() != null) {
            gen.writeObjectFieldStart("regionlist resolution");
            gen.writeStringField("strategy", meta.getRegionlistResolution().getStrategy());
            gen.writeBooleanField("tsr attempted", meta.getRegionlistResolution().isTsrAttempted());
            if (meta.getRegionlistResolution().getTsrResult() != null) {
                gen.writeStringField("tsr result", meta.getRegionlistResolution().getTsrResult());
            }
            gen.writeEndObject();
        }

        if (meta.getWordMatchMethod() != null) {
            gen.writeObjectFieldStart("word match");
            gen.writeStringField("method", meta.getWordMatchMethod());
            gen.writeNumberField("matched words", meta.getMatchedWordCount());
            gen.writeEndObject();
        }
    }

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
        double[] textColor = TextNodeUtils.getTextColorOrNull(textNode);
        if (textColor != null) {
            jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(textColor));
        }
        jsonGenerator.writeStringField(JsonName.CONTENT, textNode.getValue());
        if (textNode.isHiddenText()) {
            jsonGenerator.writeBooleanField(JsonName.HIDDEN_TEXT, true);
        }
    }
}

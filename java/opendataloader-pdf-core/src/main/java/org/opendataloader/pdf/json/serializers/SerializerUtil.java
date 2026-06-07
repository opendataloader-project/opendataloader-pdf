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
import org.verapdf.tools.TaggedPDFConstants;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;

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

        if (meta.getAiScore() >= 0.0) {
            gen.writeNumberField(JsonName.AI_SCORE, meta.getAiScore());
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

        if (meta.getTextSource() != null) {
            gen.writeStringField("text source", meta.getTextSource());
        }
        if (meta.getStreamOcrSimilarity() != null) {
            gen.writeNumberField("stream ocr similarity", meta.getStreamOcrSimilarity());
        }
    }

    public static void writeEssentialInfo(JsonGenerator jsonGenerator, IObject object, String type) throws IOException {
        jsonGenerator.writeStringField(JsonName.TYPE, type);
        String pdfuaTag = pdfuaTagFor(type, object);
        if (pdfuaTag != null) {
            jsonGenerator.writeStringField(JsonName.PDFUA_TAG, pdfuaTag);
        }
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

    /**
     * Maps an extraction JSON `type` (plus heading level when relevant) to the
     * PDF/UA structure tag that AutoTaggingProcessor will emit for the node.
     * Returns null when the node has no canonical PDF/UA tag (e.g. text chunks
     * that live below the structure-element granularity).
     */
    static String pdfuaTagFor(String type, IObject object) {
        if (type == null) {
            return null;
        }
        switch (type) {
            case JsonName.HEADING_TYPE:
                if (object instanceof SemanticHeading) {
                    int level = ((SemanticHeading) object).getHeadingLevel();
                    if (level >= 1 && level <= 6) {
                        return "H" + level;
                    }
                }
                // Fallback for unleveled headings. PDF/UA-2 deprecates the
                // plain "H" tag in favor of H1..H6; emit it only because we
                // cannot infer a level. Downstream remediation may upgrade
                // this when more context is available.
                return TaggedPDFConstants.H;
            case JsonName.PARAGRAPH_TYPE:
                return TaggedPDFConstants.P;
            case JsonName.IMAGE_CHUNK_TYPE:
                return TaggedPDFConstants.FIGURE;
            case JsonName.FORMULA_TYPE:
                return TaggedPDFConstants.FORMULA;
            case JsonName.CAPTION_TYPE:
                return TaggedPDFConstants.CAPTION;
            case JsonName.LIST_TYPE:
                return TaggedPDFConstants.L;
            case JsonName.LIST_ITEM_TYPE:
                return TaggedPDFConstants.LI;
            case JsonName.TOC_TYPE:
                return TaggedPDFConstants.TOC;
            case JsonName.TOC_ITEM_TYPE:
                return TaggedPDFConstants.TOCI;
            case JsonName.TABLE_TYPE:
                return TaggedPDFConstants.TABLE;
            case JsonName.TABLE_CELL_TYPE:
                return ((TableBorderCell)object).isHeaderCell() ? TaggedPDFConstants.TH : TaggedPDFConstants.TD;
            default:
                // header/footer/footnote/line/text-chunk/text-block
                // either become Artifact or are not promoted to their own
                // PDF/UA structure element. Leave the tag unset for now.
                return null;
        }
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

    public static void writeTextInfo(JsonGenerator jsonGenerator, TextBlock textBlock) throws IOException {
        jsonGenerator.writeStringField(JsonName.FONT_TYPE, textBlock.getFirstLine().getFirstTextChunk().getFontName());
        jsonGenerator.writePOJOField(JsonName.FONT_SIZE, textBlock.getFontSize());
        jsonGenerator.writeStringField(JsonName.TEXT_COLOR, Arrays.toString(
            textBlock.getFirstLine().getFirstTextChunk().getFontColor()));
        jsonGenerator.writeStringField(JsonName.CONTENT, textBlock.toString());
    }
}

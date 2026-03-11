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
package org.opendataloader.pdf.json;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.json.serializers.*;
import org.verapdf.wcag.algorithms.entities.SemanticCaption;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

public class ObjectMapperHolder {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    static {

        SimpleModule module = new SimpleModule("NodeSerializer", new Version(2, 1,
                3, null, null, null));

        TextChunkSerializer textChunkSerializer = new TextChunkSerializer(TextChunk.class);
        module.addSerializer(TextChunk.class, textChunkSerializer);

        TextLineSerializer textLineSerializer = new TextLineSerializer(TextLine.class);
        module.addSerializer(TextLine.class, textLineSerializer);

        ImageSerializer imageSerializer = new ImageSerializer(ImageChunk.class);
        module.addSerializer(ImageChunk.class, imageSerializer);

        TableSerializer tableSerializer = new TableSerializer(TableBorder.class);
        module.addSerializer(TableBorder.class, tableSerializer);

        TableCellSerializer tableCellSerializer = new TableCellSerializer(TableBorderCell.class);
        module.addSerializer(TableBorderCell.class, tableCellSerializer);

        ListSerializer listSerializer = new ListSerializer(PDFList.class);
        module.addSerializer(PDFList.class, listSerializer);

        ListItemSerializer listItemSerializer = new ListItemSerializer(ListItem.class);
        module.addSerializer(ListItem.class, listItemSerializer);

        LineChunkSerializer lineChunkSerializer = new LineChunkSerializer(LineChunk.class);
        module.addSerializer(LineChunk.class, lineChunkSerializer);

        SemanticTextNodeSerializer semanticTextNodeSerializer = new SemanticTextNodeSerializer(SemanticTextNode.class);
        module.addSerializer(SemanticTextNode.class, semanticTextNodeSerializer);

        TableRowSerializer tableRowSerializer = new TableRowSerializer(TableBorderRow.class);
        module.addSerializer(TableBorderRow.class, tableRowSerializer);

        HeadingSerializer headingSerializer = new HeadingSerializer(SemanticHeading.class);
        module.addSerializer(SemanticHeading.class, headingSerializer);

        CaptionSerializer captionSerializer = new CaptionSerializer(SemanticCaption.class);
        module.addSerializer(SemanticCaption.class, captionSerializer);

        DoubleSerializer doubleSerializer = new DoubleSerializer(Double.class);
        module.addSerializer(Double.class, doubleSerializer);

        HeaderFooterSerializer headerFooterSerializer = new HeaderFooterSerializer(SemanticHeaderOrFooter.class);
        module.addSerializer(SemanticHeaderOrFooter.class, headerFooterSerializer);

        FormulaSerializer formulaSerializer = new FormulaSerializer(SemanticFormula.class);
        module.addSerializer(SemanticFormula.class, formulaSerializer);

        PictureSerializer pictureSerializer = new PictureSerializer(SemanticPicture.class);
        module.addSerializer(SemanticPicture.class, pictureSerializer);

        //ParagraphSerializer paragraphSerializer = new ParagraphSerializer(SemanticParagraph.class);
        //module.addSerializer(SemanticParagraph.class, paragraphSerializer);

        objectMapper.registerModule(module);
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}

/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.json;

import com.duallab.layout.json.serializers.*;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.duallab.wcag.algorithms.entities.SemanticHeaderOrFooter;
import com.duallab.wcag.algorithms.entities.SemanticHeading;
import com.duallab.wcag.algorithms.entities.SemanticCaption;
import com.duallab.wcag.algorithms.entities.SemanticTextNode;
import com.duallab.wcag.algorithms.entities.content.ImageChunk;
import com.duallab.wcag.algorithms.entities.content.LineArtChunk;
import com.duallab.wcag.algorithms.entities.content.LineChunk;
import com.duallab.wcag.algorithms.entities.content.TextChunk;
import com.duallab.wcag.algorithms.entities.lists.ListItem;
import com.duallab.wcag.algorithms.entities.lists.PDFList;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import com.duallab.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

public class ObjectMapperHolder {
	private static final ObjectMapper objectMapper = new ObjectMapper();

	static {

		SimpleModule module = new SimpleModule("NodeSerializer", new Version(2, 1,
				3, null, null, null));

		TextChunkSerializer textChunkSerializer = new TextChunkSerializer(TextChunk.class);
		module.addSerializer(TextChunk.class, textChunkSerializer);

		ImageSerializer imageSerializer = new ImageSerializer(ImageChunk.class);
		module.addSerializer(ImageChunk.class, imageSerializer);

		LineArtSerializer lineArtSerializer = new LineArtSerializer(LineArtChunk.class);
		module.addSerializer(LineArtChunk.class, lineArtSerializer);

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

		//ParagraphSerializer paragraphSerializer = new ParagraphSerializer(SemanticParagraph.class);
		//module.addSerializer(SemanticParagraph.class, paragraphSerializer);

		objectMapper.registerModule(module);
	}

	public static ObjectMapper getObjectMapper() {
		return objectMapper;
	}
}

package com.duallab.layout.json.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ObjectSerializer {

	private static final Logger LOGGER = Logger.getLogger(ObjectSerializer.class.getCanonicalName());

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

		//ParagraphSerializer paragraphSerializer = new ParagraphSerializer(SemanticParagraph.class);
		//module.addSerializer(SemanticParagraph.class, paragraphSerializer);

		objectMapper.registerModule(module);
	}

	public static void serialize(JsonGenerator generator, Object object) {
		try {
			objectMapper.writeValue(generator, object);
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Exception during serializing object: " + e.getMessage());
		}
	}
}

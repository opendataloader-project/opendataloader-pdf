/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.json.serializers.tabletransformer;

import com.hancom.opendataloader.pdf.containers.StaticLayoutContainers;
import com.hancom.opendataloader.pdf.json.JsonName;
import com.hancom.opendataloader.pdf.processors.DocumentProcessor;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;

import java.io.File;
import java.io.IOException;

import static com.hancom.opendataloader.pdf.processors.TableTransformerProcessor.transformBBoxFromPDFToImageCoordinates;

public class TableSerializer extends StdSerializer<TableBorder> {

	public TableSerializer(Class<TableBorder> t) {
		super(t);
	}

	@Override
	public void serialize(TableBorder table, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
			throws IOException {
		BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(table.getPageNumber());
		double dpiScaling = StaticLayoutContainers.getContrastRatioConsumer().getDpiScalingForPage(table.getPageNumber());
		jsonGenerator.writeStartObject();
		String fileName = new File(StaticResources.getDocument().getDocument().getFileName()).getName();
		jsonGenerator.writeStringField("image_name",  fileName.substring(0, fileName.length() - 4) + "_page_" + table.getPageNumber() + ".jpg");
		writeBoundingBox(jsonGenerator, "table_bbox", table.getBoundingBox(), pageBoundingBox, dpiScaling);
		jsonGenerator.writeFieldName("image_size");
		jsonGenerator.writeStartArray();
		jsonGenerator.writeNumber((int) (pageBoundingBox.getHeight() * dpiScaling));
		jsonGenerator.writeNumber((int) (pageBoundingBox.getWidth() * dpiScaling));
		jsonGenerator.writeEndArray();
		writeObjects(table, jsonGenerator, pageBoundingBox, dpiScaling);
		jsonGenerator.writeEndObject();
	}

	private void writeObjects(TableBorder table, JsonGenerator jsonGenerator, BoundingBox pageBoundingBox, double dpiScaling) throws IOException {
		jsonGenerator.writeFieldName("objects");
		jsonGenerator.writeStartArray();
		writeObject(jsonGenerator, "table", table.getBoundingBox(), pageBoundingBox, dpiScaling);
		for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
			writeObject(jsonGenerator, "table row", table.getRow(rowNumber).getBoundingBox(), pageBoundingBox, dpiScaling);
		}
		for (int columnNumber = 0; columnNumber < table.getNumberOfColumns(); columnNumber++) {
			BoundingBox boundingBox = new BoundingBox(table.getLeftX(columnNumber), table.getBottomY(),
					table.getRightX(columnNumber), table.getTopY());
			writeObject(jsonGenerator, "table column", boundingBox, pageBoundingBox, dpiScaling);
		}
		for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
			for (int columnNumber = 0; columnNumber < table.getNumberOfColumns(); columnNumber++) {
				TableBorderCell cell = table.getCell(rowNumber, columnNumber);
				if (cell.getRowNumber() == rowNumber && cell.getColNumber() == columnNumber) {
					if (cell.getRowSpan() > 1 || cell.getColSpan() > 1) {
						writeObject(jsonGenerator, "table spanning cell", cell.getBoundingBox(), pageBoundingBox, dpiScaling);
					}
				}
			}
		}
		jsonGenerator.writeEndArray();
	}

	private void writeObject(JsonGenerator jsonGenerator, String label, BoundingBox boundingBox, 
							 BoundingBox pageBoundingBox, double dpiScaling) throws IOException {
		jsonGenerator.writeStartObject();
		jsonGenerator.writeStringField("label", label);
		writeBoundingBox(jsonGenerator, JsonName.BBOX, boundingBox, pageBoundingBox, dpiScaling);
		jsonGenerator.writeEndObject();
	}

	private void writeBoundingBox(JsonGenerator jsonGenerator, String fieldName, BoundingBox boundingBox, 
								  BoundingBox pageBoundingBox, double dpiScaling) throws IOException {
		BoundingBox imageBoundingBox = transformBBoxFromPDFToImageCoordinates(boundingBox, pageBoundingBox, dpiScaling);
		jsonGenerator.writeArrayFieldStart(fieldName);
		jsonGenerator.writePOJO(imageBoundingBox.getLeftX());
		jsonGenerator.writePOJO(imageBoundingBox.getBottomY());
		jsonGenerator.writePOJO(imageBoundingBox.getRightX());
		jsonGenerator.writePOJO(imageBoundingBox.getTopY());
		jsonGenerator.writeEndArray();
	}

}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.text;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Generates a plain text representation of the extracted PDF contents.
 */
public class TextGenerator implements Closeable {

    private static final Logger LOGGER = Logger.getLogger(TextGenerator.class.getCanonicalName());
    private static final String INDENT = "  ";

    private final FileWriter textWriter;
    private final String textFileName;
    private final String lineSeparator = System.lineSeparator();

    public TextGenerator(File inputPdf, String outputFolder) throws IOException {
        String cutPdfFileName = inputPdf.getName();
        this.textFileName = outputFolder + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 3) + "txt";
        this.textWriter = new FileWriter(textFileName, StandardCharsets.UTF_8);
    }

    public void writeToText(List<List<IObject>> contents) {
        try {
            for (int pageIndex = 0; pageIndex < contents.size(); pageIndex++) {
                List<IObject> pageContents = contents.get(pageIndex);
                writeContents(pageContents, 0);
                if (pageIndex < contents.size() - 1) {
                    textWriter.write(lineSeparator);
                }
            }
            LOGGER.log(Level.INFO, "Created {0}", textFileName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to create text output: " + e.getMessage());
        }
    }

    private void writeContents(List<IObject> contents, int indentLevel) throws IOException {
        for (int index = 0; index < contents.size(); index++) {
            write(contents.get(index), indentLevel);
            if (index < contents.size() - 1) {
                textWriter.write(lineSeparator);
            }
        }
    }

    private void write(IObject object, int indentLevel) throws IOException {
        if (object instanceof SemanticHeading) {
            writeMultiline(((SemanticHeading) object).getValue(), indentLevel);
        } else if (object instanceof SemanticParagraph) {
            writeMultiline(((SemanticParagraph) object).getValue(), indentLevel);
        } else if (object instanceof SemanticTextNode) {
            writeMultiline(((SemanticTextNode) object).getValue(), indentLevel);
        } else if (object instanceof PDFList) {
            writeList((PDFList) object, indentLevel);
        } else if (object instanceof TableBorder) {
            writeTable((TableBorder) object, indentLevel);
        }
    }

    private void writeList(PDFList list, int indentLevel) throws IOException {
        for (ListItem item : list.getListItems()) {
            String indent = indent(indentLevel);
            String itemText = compactWhitespace(collectPlainText(item.getContents()));
            if (!itemText.isEmpty()) {
                textWriter.write(indent);
                textWriter.write(itemText);
                textWriter.write(lineSeparator);
            }
            if (!item.getContents().isEmpty()) {
                writeContents(item.getContents(), indentLevel + 1);
            }
        }
    }

    private void writeTable(TableBorder table, int indentLevel) throws IOException {
        String indent = indent(indentLevel);
        for (TableBorderRow row : table.getRows()) {
            String rowText = Arrays.stream(row.getCells())
                .map(cell -> compactWhitespace(collectPlainText(cell.getContents())))
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining("\t"));
            if (rowText.isEmpty()) {
                continue;
            }
            textWriter.write(indent);
            textWriter.write(rowText);
            textWriter.write(lineSeparator);
        }
    }

    private String collectPlainText(List<IObject> contents) {
        StringBuilder builder = new StringBuilder();
        for (IObject content : contents) {
            String piece = extractPlainText(content);
            if (piece.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(piece);
        }
        return builder.toString();
    }

    private String extractPlainText(IObject content) {
        if (content instanceof SemanticHeading) {
            return sanitize(((SemanticHeading) content).getValue());
        } else if (content instanceof SemanticParagraph) {
            return sanitize(((SemanticParagraph) content).getValue());
        } else if (content instanceof SemanticTextNode) {
            return sanitize(((SemanticTextNode) content).getValue());
        } else if (content instanceof PDFList) {
            PDFList list = (PDFList) content;
            return list.getListItems().stream()
                .map(item -> compactWhitespace(collectPlainText(item.getContents())))
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" "));
        } else if (content instanceof TableBorder) {
            TableBorder table = (TableBorder) content;
            return Arrays.stream(table.getRows())
                .map(row -> Arrays.stream(row.getCells())
                    .map(cell -> compactWhitespace(collectPlainText(cell.getContents())))
                    .filter(text -> !text.isEmpty())
                    .collect(Collectors.joining(" ")))
                .filter(text -> !text.isEmpty())
                .collect(Collectors.joining(" "));
        }
        return "";
    }

    private void writeMultiline(String value, int indentLevel) throws IOException {
        if (value == null) {
            return;
        }
        String sanitized = sanitize(value);
        String indent = indent(indentLevel);
        String[] lines = sanitized.split("\r?\n", -1);
        for (String line : lines) {
            if (line.isBlank()) {
                continue;
            }
            textWriter.write(indent);
            textWriter.write(line);
            textWriter.write(lineSeparator);
        }
    }

    private String indent(int level) {
        if (level <= 0) {
            return "";
        }
        return INDENT.repeat(level);
    }

    private String sanitize(String value) {
        return value == null ? "" : value.replace("\u0000", " ");
    }

    private String compactWhitespace(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = sanitize(value);
        return sanitized.replaceAll("\\s+", " ").trim();
    }

    @Override
    public void close() throws IOException {
        if (textWriter != null) {
            textWriter.close();
        }
    }
}

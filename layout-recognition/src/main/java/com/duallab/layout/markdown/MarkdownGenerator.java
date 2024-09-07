package com.duallab.layout.markdown;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.Closeable;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class MarkdownGenerator implements Closeable {

    private final FileWriter markdownWriter;

    private MarkdownGenerator(String fileName) throws IOException {
        markdownWriter = new FileWriter(fileName);
    }

    public static void writeToMarkdown(String outputName, List<List<IObject>> contents) {
        String markdownFileName = outputName.substring(0, outputName.length() - 3) + "md";
        try (MarkdownGenerator generator = new MarkdownGenerator(markdownFileName)) {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                for (IObject content : contents.get(pageNumber)) {
                    generator.write(content, false);
                }
            }

            System.out.println("Created " + markdownFileName);
        } catch (IOException e) {
            System.out.println("Unable to create markdown output. See the stack trace.");
            e.printStackTrace();
        }
    }

    private void write(IObject object, boolean isTable) throws IOException {
        if (object instanceof SemanticHeading) {
            write((SemanticHeading) object);
        } else if (object instanceof SemanticParagraph) {
            write((SemanticParagraph) object, isTable);
        } else if (object instanceof SemanticTextNode) {
            write((SemanticTextNode) object);
        } else if (object instanceof TableBorder) {
            write((TableBorder) object);
        } else if (object instanceof PDFList) {
            write((PDFList) object);
        } else {
            return;
        }

        if (!isTable) {
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }
    }

    private void write(PDFList list) throws IOException {
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (ListItem item : list.getListItems()) {
            markdownWriter.write(item.toString());
            for (IObject object : item.getContents()) {
                write(object, false);
            }

            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }
    }

    private void write(SemanticTextNode textNode) throws IOException {
        markdownWriter.write(textNode.getValue());
    }

    private void write(TableBorder table) throws IOException {
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        for (TableBorderRow row : table.getRows()) {
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (TableBorderCell cell : row.getCells()) {
                for (IObject object : cell.getContents()) {
                    this.write(object, true);
                }

                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            }

            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            //Due to markdown syntax we have to separate column headers
            if (row.getRowNumber() == 0) {
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                for (int i = 0; i < table.getNumberOfColumns(); i++) {
                    markdownWriter.write(MarkdownSyntax.TABLE_HEADER_SEPARATOR);
                    markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                }

                markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            }
        }
    }

    private void write(SemanticParagraph textNode, boolean isTable) throws IOException {
        String value;
        if (isTable) {
            value = textNode.getValue().replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
        } else {
            value = textNode.getValue();
        }

        markdownWriter.write(value);
    }

    private void write(SemanticHeading heading) throws IOException {
        int headingLevel = heading.getHeadingLevel();
        for (int i = 0; i < headingLevel; i++) {
            markdownWriter.write(MarkdownSyntax.HEADING_LEVEL);
        }

        markdownWriter.write(MarkdownSyntax.SPACE);
        markdownWriter.write(heading.getValue());
    }

    @Override
    public void close() throws IOException {
        if (markdownWriter != null) {
            markdownWriter.close();
        }
    }
}

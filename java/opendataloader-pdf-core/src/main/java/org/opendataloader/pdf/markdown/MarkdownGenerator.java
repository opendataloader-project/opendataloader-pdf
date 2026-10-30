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
package org.opendataloader.pdf.markdown;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.EnrichedImageChunk;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.ContentSanitizer;
import org.opendataloader.pdf.utils.GeneratorUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.opendataloader.pdf.utils.OutputType;
import org.opendataloader.pdf.utils.TextLinks;
import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class MarkdownGenerator implements Closeable {

    protected static final Logger LOGGER = Logger.getLogger(MarkdownGenerator.class.getCanonicalName());
    protected final java.io.Writer markdownWriter;
    protected final String markdownFileName;
    protected int tableNesting = 0;
    protected boolean isImageSupported;
    protected String markdownPageSeparator;
    /**
     * Page numbers (1-based) selected by --pages; an empty set means all pages.
     * Sourced from the raw {@link Config#getPageNumbers()} list (not the
     * validated set built by {@code DocumentProcessor.getValidPageNumbers}).
     * Safe to compare against {@code pageNumber + 1} because the surrounding
     * loop is bounded by the document's actual page count, so out-of-range
     * values from the raw list are never tested for membership.
     */
    protected final Set<Integer> selectedPageNumbers;
    protected boolean embedImages = false;
    protected String imageFormat = Config.IMAGE_FORMAT_PNG;
    protected boolean includeHeaderFooter = false;
    protected static final String strikethroughTextMD = "~~";
    private static final Pattern LEADING_HEADING_OR_BULLET_MARKER = Pattern.compile("^( {0,3})(?=(?:#{1,6}|[-+*])(?:[ \\t\\n]|$))");
    private static final Pattern LEADING_ORDERED_LIST_MARKER = Pattern.compile("^( {0,3}\\d{1,9})(?=[.)](?:[ \\t\\n]|$))");
    /**
     * Stands for an image in the text of a line until the text is escaped (#642). It is a noncharacter, which
     * Unicode keeps for internal use, so the text of a document does not have it.
     */
    private static final char INLINE_IMAGE = '\uFDD0';
    /**
     * An image in a line is at most this many times as high as the font size of the line. A line that is higher
     * than this is not written across the page, and holds no image.
     */
    private static final double MAX_INLINE_IMAGE_HEIGHT = 2;
    /**
     * An image in a line is at most this many font sizes before the first word or after the last word, about the
     * width of a space. An image a column gap away is in the next column.
     */
    private static final double MAX_INLINE_IMAGE_DISTANCE = 0.5;
    /** The images in a line of the page that is written, in the order of their centres, by line. */
    private final Map<TextLine, List<ImageChunk>> inlineImagesByLine = new IdentityHashMap<>();
    private final Set<ImageChunk> inlineImages = Collections.newSetFromMap(new IdentityHashMap<>());
    /** The Markdown of the images that the text being written stands for, in order. */
    private final Deque<String> pendingInlineImages = new ArrayDeque<>();
    /** The link of --include-links that the text being written ends in (see {@link TextLinks.OpenLink}). */
    private final TextLinks.OpenLink openLink = new TextLinks.OpenLink();

    MarkdownGenerator(File inputPdf, Config config) throws IOException {
        String cutPdfFileName = inputPdf.getName();
        this.markdownFileName = config.getOutputFolder() + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 3) + "md";
        this.markdownWriter = new FileWriter(markdownFileName, StandardCharsets.UTF_8);
        this.isImageSupported = !config.isImageOutputOff() && config.isGenerateMarkdown();
        this.markdownPageSeparator = config.getMarkdownPageSeparator();
        this.selectedPageNumbers = new HashSet<>(config.getPageNumbers());
        this.embedImages = config.isEmbedImages();
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
    }

    /**
     * Creates a MarkdownGenerator that writes to an arbitrary Writer (e.g., stdout).
     */
    public MarkdownGenerator(java.io.Writer writer, Config config) {
        this.markdownFileName = null;
        this.markdownWriter = writer;
        this.isImageSupported = false;
        this.markdownPageSeparator = config.getMarkdownPageSeparator();
        this.selectedPageNumbers = new HashSet<>(config.getPageNumbers());
        this.embedImages = false;
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
    }

    public void writeToMarkdown(List<List<IObject>> contents) {
        try {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                if (selectedPageNumbers.isEmpty() || selectedPageNumbers.contains(pageNumber + 1)) {
                    writePageSeparator(pageNumber);
                }
                findInlineImages(contents.get(pageNumber));
                for (IObject content : contents.get(pageNumber)) {
                    if (!isSupportedContent(content)) {
                        continue;
                    }
                    this.write(content);
                    writeContentsSeparator();
                }
            }

            LOGGER.log(Level.INFO, "Created {0}", markdownFileName);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to create markdown output: " + e.getMessage());
        }
    }

    protected void writePageSeparator(int pageNumber) throws IOException {
        if (!markdownPageSeparator.isEmpty()) {
            markdownWriter.write(markdownPageSeparator.contains(Config.PAGE_NUMBER_STRING)
                ? markdownPageSeparator.replace(Config.PAGE_NUMBER_STRING, String.valueOf(pageNumber + 1))
                : markdownPageSeparator);
            writeContentsSeparator();
        }
    }

    protected boolean isSupportedContent(IObject content) {
        if (content instanceof SemanticHeaderOrFooter) {
            return includeHeaderFooter;
        }
        return content instanceof SemanticTextNode || // Heading, Paragraph etc...
            content instanceof SemanticFormula ||
            content instanceof SemanticPicture ||
            content instanceof TableBorder ||
            content instanceof PDFList ||
            content instanceof SemanticTOC ||
            (content instanceof ImageChunk && isImageSupported && !inlineImages.contains(content));
    }

    /**
     * Finds the images of the page that are in a line of text, as the icon of a button in a sentence (#642).
     * Such an image is written in its line, not as a block.
     *
     * @param pageContents The contents of the page
     */
    private void findInlineImages(List<IObject> pageContents) {
        inlineImagesByLine.clear();
        inlineImages.clear();
        if (!isImageSupported) {
            return;
        }
        List<ImageChunk> images = new ArrayList<>();
        List<TextLine> lines = new ArrayList<>();
        collectImagesAndLines(pageContents, images, lines);
        for (ImageChunk image : images) {
            TextLine line = getLineOfImage(image, lines);
            if (line != null) {
                inlineImagesByLine.computeIfAbsent(line, key -> new ArrayList<>()).add(image);
                inlineImages.add(image);
            }
        }
        for (List<ImageChunk> lineImages : inlineImagesByLine.values()) {
            lineImages.sort(Comparator.comparingDouble(MarkdownGenerator::getCenterX));
        }
    }

    /** Collects the images and the lines of text that this writer writes, also in lists, tables, and headers. */
    private void collectImagesAndLines(List<IObject> contents, List<ImageChunk> images, List<TextLine> lines) {
        for (IObject content : contents) {
            if (content instanceof ImageChunk) {
                images.add((ImageChunk) content);
            } else if (content instanceof SemanticHeaderOrFooter) {
                if (includeHeaderFooter) {
                    collectImagesAndLines(((SemanticHeaderOrFooter) content).getContents(), images, lines);
                }
            } else if (content instanceof SemanticTextNode) {
                for (TextColumn column : ((SemanticTextNode) content).getColumns()) {
                    lines.addAll(column.getLines());
                }
            } else if (content instanceof PDFList) {
                for (ListItem item : ((PDFList) content).getListItems()) {
                    lines.addAll(item.getLines());
                    collectImagesAndLines(item.getContents(), images, lines);
                }
            } else if (content instanceof TableBorder) {
                TableBorder table = (TableBorder) content;
                for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
                    for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                        TableBorderCell cell = table.getRow(rowNumber).getCell(colNumber);
                        if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                            collectImagesAndLines(cell.getContents(), images, lines);
                        }
                    }
                }
            }
        }
    }

    /**
     * Returns the line that holds the image, or null. The centre of the image is in the height of the line, the
     * line and the image are at most {@link #MAX_INLINE_IMAGE_HEIGHT} times as high as the font size of the line,
     * and the image is in the line or at most {@link #MAX_INLINE_IMAGE_DISTANCE} font sizes before its first word
     * or after its last word. Of two such lines, the nearer one.
     */
    private static TextLine getLineOfImage(ImageChunk image, List<TextLine> lines) {
        double centerY = (image.getBottomY() + image.getTopY()) / 2;
        TextLine nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (TextLine line : lines) {
            double fontSize = line.getFontSize();
            if (!Objects.equals(line.getPageNumber(), image.getPageNumber()) || line.getTextChunks().isEmpty()
                    || fontSize <= 0 || line.getHeight() > MAX_INLINE_IMAGE_HEIGHT * fontSize
                    || centerY < line.getBottomY() || centerY > line.getTopY()
                    || image.getHeight() > MAX_INLINE_IMAGE_HEIGHT * fontSize) {
                continue;
            }
            double distance = Math.max(0, Math.max(line.getLeftX() - image.getRightX(), image.getLeftX() - line.getRightX()));
            if (distance <= MAX_INLINE_IMAGE_DISTANCE * fontSize && distance < nearestDistance) {
                nearest = line;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static double getCenterX(ImageChunk image) {
        return (image.getLeftX() + image.getRightX()) / 2;
    }

    protected void writeContentsSeparator() throws IOException {
        writeLineBreak();
        writeLineBreak();
    }

    protected void write(IObject object) throws IOException {
        if (object instanceof SemanticHeaderOrFooter) {
            writeHeaderOrFooter((SemanticHeaderOrFooter) object);
        } else if (object instanceof SemanticPicture) {
            writePicture((SemanticPicture) object);
        } else if (object instanceof ImageChunk) {
            writeImage((ImageChunk) object);
        } else if (object instanceof SemanticFormula) {
            writeFormula((SemanticFormula) object);
        } else if (object instanceof SemanticHeading) {
            writeHeading((SemanticHeading) object);
        } else if (object instanceof SemanticParagraph) {
            writeParagraph((SemanticParagraph) object);
        } else if (object instanceof SemanticTextNode) {
            writeSemanticTextNode((SemanticTextNode) object);
        } else if (object instanceof TableBorder) {
            writeTable((TableBorder) object);
        } else if (object instanceof PDFList) {
            writeList((PDFList) object);
        } else if (object instanceof SemanticTOC) {
            writeTOC((SemanticTOC) object);
        }
    }

    /**
     * Wraps an image relative path as a CommonMark angle-bracket link destination
     * (`<...>`). The bare form `(my paper.png)` is terminated by the first space or
     * unbalanced parenthesis, so paths inheriting filenames with spaces, parens, or
     * brackets break in renderers (#405). The angle-bracket form is the
     * spec-recommended way to embed such paths and lets the on-disk path stay
     * byte-identical to the rendered link, which preserves user intent for both the
     * default `<stem>_images/` directory and any `--image-dir` value.
     *
     * Only `<`, `>`, and `\` are reserved inside the angle-bracket form; escape
     * those with a backslash. Newlines have no representable form in a link
     * destination — replace them with spaces so the destination stays well-formed.
     */
    static String formatMarkdownLinkDestination(String path) {
        if (path == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(path.length() + 2);
        sb.append('<');
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '<' || c == '>' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c == '\n' || c == '\r') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        sb.append('>');
        return sb.toString();
    }

    protected void writeImage(ImageChunk image) {
        String imageString = getImageMarkdown(image);
        if (imageString != null) {
            try {
                markdownWriter.write(imageString);
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Unable to write image for markdown output: " + e.getMessage());
            }
        }
    }

    /**
     * Returns the Markdown of the image, or null if its file is not there or cannot be embedded.
     */
    private String getImageMarkdown(ImageChunk image) {
        String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, image.getIndex(), imageFormat);
        String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", image.getIndex(), imageFormat);

        if (!ImagesUtils.isImageFileExists(absolutePath)) {
            return null;
        }
        String imageSource;
        if (embedImages) {
            File imageFile = new File(absolutePath);
            imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
            if (imageSource == null) {
                LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                return null;
            }
        } else {
            imageSource = formatMarkdownLinkDestination(relativePath);
        }
        // No "image N" fallback: PDF/UA forbids false alternatives,
        // and an empty Markdown alt lets screen readers skip the
        // image as a decorative element rather than reading a
        // meaningless synthetic label.
        String altText = (image instanceof EnrichedImageChunk && ((EnrichedImageChunk) image).hasDescription())
                ? ((EnrichedImageChunk) image).sanitizeDescription()
                : "";
        return String.format(MarkdownSyntax.IMAGE_FORMAT, getCorrectMarkdownString(altText), imageSource);
    }

    /**
     * Writes a SemanticPicture with its description as alt text.
     *
     * @param picture The picture to write
     */
    protected void writePicture(SemanticPicture picture) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, picture.getPictureIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", picture.getPictureIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = formatMarkdownLinkDestination(relativePath);
                }
                if (imageSource != null) {
                    String altText = picture.hasDescription()
                            ? picture.sanitizeDescription()
                            : "";
                    String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, getCorrectMarkdownString(altText), imageSource);
                    markdownWriter.write(imageString);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write picture for markdown output: " + e.getMessage());
        }
    }

    /**
     * Writes a formula in LaTeX format wrapped in $$ delimiters.
     *
     * @param formula The formula to write
     */
    protected void writeFormula(SemanticFormula formula) throws IOException {
        markdownWriter.write(MarkdownSyntax.MATH_BLOCK_START);
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        markdownWriter.write(formula.getLatex());
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        markdownWriter.write(MarkdownSyntax.MATH_BLOCK_END);
    }

    protected void writeHeaderOrFooter(SemanticHeaderOrFooter headerOrFooter) throws IOException {
        for (IObject content : headerOrFooter.getContents()) {
            if (isSupportedContent(content)) {
                write(content);
                writeContentsSeparator();
            }
        }
    }

    protected void writeList(PDFList list) throws IOException {
        for (ListItem item : list.getListItems()) {
            String itemText;
            if (!isInsideTable()) {
                markdownWriter.write(MarkdownSyntax.LIST_ITEM);
                markdownWriter.write(MarkdownSyntax.SPACE);
            }
            if (!isInsideTable() && NumberingStyleNames.UNORDERED.equals(list.getNumberingStyle())) {
                itemText = escapeLeadingBlockMarker(getTextWithoutLabel(item));
            } else {
                itemText = endLink(GeneratorUtils.getTextFromLines(item.getLines(), this::appendLine));
            }
            markdownWriter.write(replaceInlineImages(getCorrectMarkdownString(itemText)));
            writeLineBreak();

            List<IObject> itemContents = item.getContents();
            if (!itemContents.isEmpty()) {
                writeLineBreak();
                writeContents(itemContents, false);
            }
        }
    }

    /**
     * Returns the number of characters of the label at the start of the text of a list item. The label length
     * of the item counts the characters before sanitization. A replacement of the sanitizer keeps the stream
     * infos of the text that it replaced, so it counts as the length of that text. A replacement that starts in
     * the label and ends in the body stays in the item text.
     *
     * @param item The list item
     * @return the number of characters to remove from the start of the item text
     */
    private static int getLabelEnd(ListItem item) {
        int remaining = item.getLabelLength();
        if (remaining <= 0 || item.getLines().isEmpty()) {
            return 0;
        }
        int end = 0;
        for (TextChunk chunk : item.getFirstLine().getTextChunks()) {
            int valueLength = chunk.getValue().length();
            int replacedLength = getReplacedLength(chunk);
            int length = replacedLength < 0 ? valueLength : replacedLength;
            if (length > remaining) {
                return replacedLength < 0 ? end + remaining : end;
            }
            end += valueLength;
            remaining -= length;
            if (remaining == 0) {
                break;
            }
        }
        return end;
    }

    /**
     * Returns the length of the text that a replacement of the sanitizer replaced, or -1 if the chunk is not a
     * replacement. A longer replacement has a stream info for its extra characters. A shorter replacement has
     * stream infos for more characters than its value.
     *
     * @param chunk The text chunk
     * @return the length of the replaced text, or -1
     */
    private static int getReplacedLength(TextChunk chunk) {
        int length = 0;
        boolean hasExtraCharacters = false;
        for (StreamInfo streamInfo : chunk.getStreamInfos()) {
            if (streamInfo.getOperatorIndex() == ContentSanitizer.EXTRA_CHARACTERS_OPERATOR_INDEX) {
                hasExtraCharacters = true;
            } else {
                length += streamInfo.getEndIndex() - streamInfo.getStartIndex();
            }
        }
        return hasExtraCharacters || length > chunk.getValue().length() ? length : -1;
    }

    protected void writeTOC(SemanticTOC toc) throws IOException {
        for (IObject item : toc.getTOCItems()) {
            if (item instanceof SemanticTOC) {
                writeTOC((SemanticTOC)item);
            } else if (item instanceof SemanticTOCI) {
                SemanticTOCI tocItem = (SemanticTOCI)item;
                markdownWriter.write(getCorrectMarkdownString(GeneratorUtils.getTextFromLines(tocItem.getLines(), OutputType.MD)));
                writeLineBreak();

                List<IObject> itemContents = tocItem.getContents();
                if (!itemContents.isEmpty()) {
                    writeLineBreak();
                    writeContents(itemContents, false);
                }
                writeLineBreak();
            }
        }
    }

    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        String value = endLink(GeneratorUtils.getTextFromTextNode(textNode, this::appendLine));
        if (StaticContainers.isKeepLineBreaks()) {
            if (textNode instanceof SemanticHeading) {
                value = value.replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
            } else if (isInsideTable()) {
                value = value.replace(MarkdownSyntax.LINE_BREAK, getLineBreak());
            }
        } else if (isInsideTable()) {
            // Always replace line breaks with space in table cells for proper markdown table formatting
            value = value.replace(MarkdownSyntax.LINE_BREAK, MarkdownSyntax.SPACE);
        }

        markdownWriter.write(replaceInlineImages(getCorrectMarkdownString(value)));
    }



    protected void writeTable(TableBorder table) throws IOException {
        enterTable();
        for (int rowNumber = 0; rowNumber < table.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = table.getRow(rowNumber);
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (int colNumber = 0; colNumber < table.getNumberOfColumns(); colNumber++) {
                TableBorderCell cell = row.getCell(colNumber);
                if (cell.getRowNumber() == rowNumber && cell.getColNumber() == colNumber) {
                    List<IObject> cellContents = cell.getContents();
                    writeContents(cellContents, true);
                } else {
                    writeSpace();
                }
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            }
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            //Due to markdown syntax we have to separate column headers
            if (rowNumber == 0) {
                markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                for (int i = 0; i < table.getNumberOfColumns(); i++) {
                    markdownWriter.write(MarkdownSyntax.TABLE_HEADER_SEPARATOR);
                    markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
                }
                markdownWriter.write(MarkdownSyntax.LINE_BREAK);
            }
        }
        leaveTable();
    }

    protected void writeContents(List<IObject> contents, boolean isTable) throws IOException {
        boolean wroteAnyContent = false;
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (!isSupportedContent(content)) {
                continue;
            }
            this.write(content);
            boolean isLastContent = i == contents.size() - 1;
            if (!isTable || !isLastContent) {
                writeContentsSeparator();
            }
            wroteAnyContent = true;
        }
        if (!wroteAnyContent && isTable) {
            writeSpace();
        }
    }

    protected void writeParagraph(SemanticParagraph textNode) throws IOException {
        writeSemanticTextNode(textNode);
    }

    protected void writeHeading(SemanticHeading heading) throws IOException {
        if (!isInsideTable()) {
            // Cap heading level to 1-6 per Markdown specification
            int headingLevel = Math.min(6, Math.max(1, heading.getHeadingLevel()));
            for (int i = 0; i < headingLevel; i++) {
                markdownWriter.write(MarkdownSyntax.HEADING_LEVEL);
            }
            markdownWriter.write(MarkdownSyntax.SPACE);
        }
        writeSemanticTextNode(heading);
    }

    protected void enterTable() {
        tableNesting++;
    }

    protected void leaveTable() {
        if (tableNesting > 0) {
            tableNesting--;
        }
    }

    protected boolean isInsideTable() {
        return tableNesting > 0;
    }

    protected String getLineBreak() {
        if (isInsideTable()) {
            return MarkdownSyntax.HTML_LINE_BREAK_TAG;
        } else {
            return MarkdownSyntax.LINE_BREAK;
        }
    }

    protected void writeLineBreak() throws IOException {
        markdownWriter.write(getLineBreak());
    }

    protected void writeSpace() throws IOException {
        markdownWriter.write(MarkdownSyntax.SPACE);
    }

    protected String getCorrectMarkdownString(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("\u0000", "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;");
    }

    /**
     * Escapes a heading marker or a list marker at the start of a list item text, also after up to three
     * spaces. After "- ", CommonMark reads "# text" as a heading and "1. text" as a nested list; "\# text"
     * and "1\. text" stay text. A space, a tab, a line break, or the end of the text ends the marker. After
     * four spaces, CommonMark reads an indented code block instead.
     */
    protected static String escapeLeadingBlockMarker(String itemText) {
        String escaped = LEADING_HEADING_OR_BULLET_MARKER.matcher(itemText).replaceFirst("$1\\\\");
        return LEADING_ORDERED_LIST_MARKER.matcher(escaped).replaceFirst("$1\\\\");
    }

    /** Appends the text of the line. A link at the end of the line ends there. */
    public static void getTextFromLineForMarkdown(TextLine line, StringBuilder stringBuilder) {
        TextLinks.OpenLink link = new TextLinks.OpenLink();
        getTextFromLineForMarkdown(line, stringBuilder, link);
        closeLink(link, stringBuilder);
    }

    /** Appends the text of the line. A link at its end stays open (see {@link TextLinks.OpenLink}). */
    public static void getTextFromLineForMarkdown(TextLine line, StringBuilder stringBuilder, TextLinks.OpenLink link) {
        for (TextChunk chunk : line.getTextChunks()) {
            appendTextChunk(chunk, 0, chunk.getValue().length(), stringBuilder, link);
        }
    }

    /**
     * Returns the Markdown text of a list item without its label. The label end (getLabelEnd) counts the
     * characters of the text, so the label characters are written without the strikethrough
     * markup before the cut. Markup there would move the cut into the markup (#828).
     */
    private String getTextWithoutLabel(ListItem item) {
        List<TextLine> lines = item.getLines();
        StringBuilder stringBuilder = new StringBuilder();
        int labelLength = getLabelEnd(item);
        int labelCharactersLeft = labelLength;
        List<ImageChunk> images = inlineImagesByLine.getOrDefault(lines.get(0), Collections.emptyList());
        int nextImage = 0;
        for (TextChunk chunk : lines.get(0).getTextChunks()) {
            String value = chunk.getValue();
            int labelEnd = Math.min(labelCharactersLeft, value.length());
            stringBuilder.append(value, 0, labelEnd);
            labelCharactersLeft -= labelEnd;
            if (labelEnd < value.length()) {
                nextImage = appendChunkWithImages(chunk, labelEnd, images, nextImage, stringBuilder);
            }
        }
        appendInlineImages(images, nextImage, stringBuilder);
        for (int i = 1; i < lines.size(); i++) {
            TextChunkUtils.formatLineEnd(stringBuilder);
            appendLine(lines.get(i), stringBuilder);
        }
        closeLink(openLink, stringBuilder);
        return stringBuilder.substring(labelLength);
    }

    /**
     * Appends the Markdown text of the line, with a stand-in at the place of each image in the line (#642).
     */
    private void appendLine(TextLine line, StringBuilder stringBuilder) {
        List<ImageChunk> images = inlineImagesByLine.get(line);
        if (images == null) {
            getTextFromLineForMarkdown(line, stringBuilder, openLink);
            return;
        }
        int nextImage = 0;
        for (TextChunk chunk : line.getTextChunks()) {
            nextImage = appendChunkWithImages(chunk, 0, images, nextImage, stringBuilder);
        }
        appendInlineImages(images, nextImage, stringBuilder);
    }

    /**
     * Appends the characters of the chunk from {@code start}, with a stand-in before the first character that
     * starts right of the centre of each image that goes in the chunk.
     *
     * @return the index of the next image of the line
     */
    private int appendChunkWithImages(TextChunk chunk, int start, List<ImageChunk> images, int nextImage,
                                      StringBuilder stringBuilder) {
        String value = chunk.getValue();
        while (nextImage < images.size()) {
            int index = getCharacterIndex(chunk, getCenterX(images.get(nextImage)));
            if (index >= value.length()) {
                break;
            }
            if (index > start) {
                appendTextAfterImage(chunk, start, index, stringBuilder);
                start = index;
            }
            appendInlineImage(images.get(nextImage++), stringBuilder);
        }
        if (start < value.length()) {
            appendTextAfterImage(chunk, start, value.length(), stringBuilder);
        }
        return nextImage;
    }

    /**
     * Returns the index of the first character of the chunk that starts at or right of {@code x}, or the length
     * of the value if none does. Without a position for each character, the chunk is one place: 0 if it starts
     * at or right of {@code x}, else the length.
     */
    private static int getCharacterIndex(TextChunk chunk, double x) {
        String value = chunk.getValue();
        List<Double> symbolEnds = chunk.getSymbolEnds();
        if (symbolEnds == null || symbolEnds.size() != value.length() + 1) {
            return chunk.getLeftX() >= x ? 0 : value.length();
        }
        for (int i = 0; i < value.length(); i++) {
            if (symbolEnds.get(i) >= x) {
                return i;
            }
        }
        return value.length();
    }

    /**
     * Appends the characters of the chunk from {@code start} to {@code end}. After a stand-in, one space separates
     * them.
     */
    private void appendTextAfterImage(TextChunk chunk, int start, int end, StringBuilder stringBuilder) {
        if (stringBuilder.length() > 0 && stringBuilder.charAt(stringBuilder.length() - 1) == INLINE_IMAGE) {
            String value = chunk.getValue();
            while (start < end && Character.isWhitespace(value.charAt(start))) {
                start++;
            }
            if (start == end) {
                return;
            }
            stringBuilder.append(MarkdownSyntax.SPACE);
        }
        appendTextChunk(chunk, start, end, stringBuilder, openLink);
    }

    private void appendInlineImages(List<ImageChunk> images, int nextImage, StringBuilder stringBuilder) {
        for (int i = nextImage; i < images.size(); i++) {
            appendInlineImage(images.get(i), stringBuilder);
        }
    }

    /**
     * Appends a stand-in for the image, after one space, and keeps its Markdown for {@link #replaceInlineImages}.
     * An open link ends before the image.
     */
    private void appendInlineImage(ImageChunk image, StringBuilder stringBuilder) {
        String imageString = getImageMarkdown(image);
        if (imageString == null) {
            return;
        }
        closeLink(openLink, stringBuilder);
        if (stringBuilder.length() > 0 && !Character.isWhitespace(stringBuilder.charAt(stringBuilder.length() - 1))) {
            stringBuilder.append(MarkdownSyntax.SPACE);
        }
        stringBuilder.append(INLINE_IMAGE);
        pendingInlineImages.add(imageString);
    }

    /**
     * Puts the Markdown of each image in the place of its stand-in, after the text is escaped. An image whose
     * stand-in is not in the text goes after the text, so that no image is lost.
     */
    private String replaceInlineImages(String value) {
        if (pendingInlineImages.isEmpty()) {
            return value;
        }
        StringBuilder stringBuilder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (character == INLINE_IMAGE && !pendingInlineImages.isEmpty()) {
                stringBuilder.append(pendingInlineImages.poll());
            } else {
                stringBuilder.append(character);
            }
        }
        while (!pendingInlineImages.isEmpty()) {
            stringBuilder.append(MarkdownSyntax.SPACE).append(pendingInlineImages.poll());
        }
        return stringBuilder.toString();
    }

    /** Ends the link that the text ends in, if one is open. */
    private String endLink(String text) {
        if (openLink.getUri() == null) {
            return text;
        }
        StringBuilder stringBuilder = new StringBuilder(text);
        closeLink(openLink, stringBuilder);
        return stringBuilder.toString();
    }

    /**
     * Appends the characters of the chunk from {@code start} to {@code end}. The text of a URI link becomes a Markdown
     * link.
     */
    private static void appendTextChunk(TextChunk chunk, int start, int end, StringBuilder stringBuilder,
                                        TextLinks.OpenLink link) {
        for (TextLinks.Part part : TextLinks.split(chunk, start, end)) {
            String text = part.getText();
            if (!link.isContinuedBy(part)) {
                closeLink(link, stringBuilder);
                if (part.getUri() != null) {
                    stringBuilder.append('[');
                    link.setUri(part.getUri());
                }
            }
            if (part.getUri() != null) {
                text = text.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]");
            }
            appendText(chunk, text, stringBuilder);
        }
    }

    /** Ends the open link. The whitespace at the end of the text, as a line end, stays after the link. */
    public static void closeLink(TextLinks.OpenLink link, StringBuilder stringBuilder) {
        if (link.getUri() == null) {
            return;
        }
        int textEnd = stringBuilder.length();
        while (textEnd > 0 && Character.isWhitespace(stringBuilder.charAt(textEnd - 1))) {
            textEnd--;
        }
        stringBuilder.insert(textEnd, "](" + formatMarkdownUriDestination(link.getUri()) + ")");
        link.setUri(null);
    }

    /**
     * Writes a URI as a CommonMark link destination without angle brackets: {@link #getCorrectMarkdownString}
     * escapes the angle brackets of the whole text later, and CommonMark reads the entities back in a destination.
     * A backslash escapes each parenthesis and backslash, and spaces and control characters are percent-encoded,
     * so the destination ends at its closing parenthesis.
     */
    static String formatMarkdownUriDestination(String uri) {
        StringBuilder sb = new StringBuilder(uri.length());
        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            if (c == '(' || c == ')' || c == '\\') {
                sb.append('\\').append(c);
            } else if (c <= ' ' || c == '\u007F') {
                sb.append(String.format("%%%02X", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Appends the text, in strikethrough markup if the chunk is struck through. */
    private static void appendText(TextChunk chunk, String value, StringBuilder stringBuilder) {
        if (chunk.getIsStrikethroughText()) {
            stringBuilder.append(strikethroughTextMD);
        }
        stringBuilder.append(value);
        if (chunk.getIsStrikethroughText()) {
            stringBuilder.append(strikethroughTextMD);
        }
    }

    @Override
    public void close() throws IOException {
        if (markdownWriter != null) {
            markdownWriter.close();
        }
    }
}

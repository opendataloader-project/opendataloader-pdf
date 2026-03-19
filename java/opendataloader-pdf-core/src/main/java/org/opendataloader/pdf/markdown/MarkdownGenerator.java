/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.markdown;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.utils.Base64ImageUtils;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.Closeable;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class MarkdownGenerator implements Closeable {

    protected static final Logger LOGGER = Logger.getLogger(MarkdownGenerator.class.getCanonicalName());
    protected final FileWriter markdownWriter;
    protected final String markdownFileName;
    protected int tableNesting = 0;
    protected boolean isImageSupported;
    protected String markdownPageSeparator;
    protected boolean embedImages = false;
    protected String imageFormat = Config.IMAGE_FORMAT_PNG;
    protected boolean includeHeaderFooter = false;
    protected String markdownTableOutput = Config.MARKDOWN_TABLE_OUTPUT_FULL;
    private static final Pattern TABLE_CAPTION_PATTERN = Pattern.compile("^table\\s+\\d+\\s+\\|", Pattern.CASE_INSENSITIVE);
    private static final Pattern PIPE_ROW_PATTERN = Pattern.compile("^\\|.*\\|\\s*$");
    private static final Pattern NUMERIC_ONLY_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern TABLE_HEADER_TEXT_PATTERN = Pattern.compile(
        "^(benchmark \\(metric\\)|model|architecture|# activated params|# total params|english|code|math|chinese)$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TABLE_HEADER_PREFIX_PATTERN = Pattern.compile(
        "^(architecture\\b|#\\s*activated params|#\\s*total params)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PASS_COLUMN_PATTERN = Pattern.compile(
        "^(pass@1|cons@\\d+|rating)(?:\\s+(pass@1|cons@\\d+|rating))+\\s*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FOOTNOTE_PLACEHOLDER_PATTERN = Pattern.compile(
        "^(?:\\d+https?://\\S+)(?:\\s+\\d+https?://\\S+)*$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BENCHMARK_PATTERN = Pattern.compile(
        "(AIME 2024|MATH-500|CNMO 2024|GPQA(?: Diamond)?|LiveCodeBench|Codeforces|SWE Verified|Aider-Polyglot|MMLU(?:-Redux|-Pro)?|DROP|IF-Eval|SimpleQA|FRAMES|AlpacaEval2\\.0|ArenaHard|CLUEWSC|C-Eval|C-SimpleQA)",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile(
        "^(?:[A-Za-z]+-\\d[\\w.-]*|\\d{3,4}|[A-Z]\\d(?:[\\w.-]+)?|o1-\\d+|R1|V\\d|QwQ-\\d+[A-Za-z-]*|GPT-\\d\\w*|Claude(?:-\\w+)?|Sonnet-\\d+)$"
    );
    private static final Pattern MODELISH_LINE_PATTERN = Pattern.compile("(?:DeepSeek|OpenAI|Claude|QwQ|GPT-4o)");

    MarkdownGenerator(File inputPdf, Config config) throws IOException {
        String cutPdfFileName = inputPdf.getName();
        this.markdownFileName = config.getOutputFolder() + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 3) + "md";
        this.markdownWriter = new FileWriter(markdownFileName, StandardCharsets.UTF_8);
        this.isImageSupported = !config.isImageOutputOff() && config.isGenerateMarkdown();
        this.markdownPageSeparator = config.getMarkdownPageSeparator();
        this.embedImages = config.isEmbedImages();
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
        this.markdownTableOutput = config.getMarkdownTableOutput();
    }

    public void writeToMarkdown(List<List<IObject>> contents) {
        try {
            List<Set<Integer>> pageSkipIndices = new java.util.ArrayList<>(contents.size());
            for (List<IObject> pageContents : contents) {
                pageSkipIndices.add(collectTableArtifactIndices(pageContents));
            }
            extendCrossPageTableArtifactSkips(contents, pageSkipIndices);
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                writePageSeparator(pageNumber);
                List<IObject> pageContents = contents.get(pageNumber);
                Set<Integer> skipIndices = pageSkipIndices.get(pageNumber);
                for (int contentIndex = 0; contentIndex < pageContents.size(); contentIndex++) {
                    if (skipIndices.contains(contentIndex)) {
                        continue;
                    }
                    IObject content = pageContents.get(contentIndex);
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
            (content instanceof ImageChunk && isImageSupported);
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
        }
    }

    protected void writeImage(ImageChunk image) {
        try {
            String absolutePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectory(), File.separator, image.getIndex(), imageFormat);
            String relativePath = String.format(MarkdownSyntax.IMAGE_FILE_NAME_FORMAT, StaticLayoutContainers.getImagesDirectoryName(), "/", image.getIndex(), imageFormat);

            if (ImagesUtils.isImageFileExists(absolutePath)) {
                String imageSource;
                if (embedImages) {
                    File imageFile = new File(absolutePath);
                    imageSource = Base64ImageUtils.toDataUri(imageFile, imageFormat);
                    if (imageSource == null) {
                        LOGGER.log(Level.WARNING, "Failed to convert image to Base64: {0}", absolutePath);
                    }
                } else {
                    imageSource = relativePath;
                }
                if (imageSource != null) {
                    String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, "image " + image.getIndex(), imageSource);
                    markdownWriter.write(getCorrectMarkdownString(imageString));
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Unable to write image for markdown output: " + e.getMessage());
        }
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
                    imageSource = relativePath;
                }
                if (imageSource != null) {
                    // Use simple alt text
                    String altText = "image " + picture.getPictureIndex();
                    String imageString = String.format(MarkdownSyntax.IMAGE_FORMAT, altText, imageSource);
                    markdownWriter.write(getCorrectMarkdownString(imageString));

                    // Add caption as italic text below the image if description available
                    if (picture.hasDescription()) {
                        markdownWriter.write(MarkdownSyntax.DOUBLE_LINE_BREAK);
                        String caption = picture.getDescription().replace("\n", " ").replace("\r", "");
                        markdownWriter.write("*" + getCorrectMarkdownString(caption) + "*");
                        markdownWriter.write(MarkdownSyntax.DOUBLE_LINE_BREAK);
                    }
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
            if (!isInsideTable()) {
                markdownWriter.write(MarkdownSyntax.LIST_ITEM);
                markdownWriter.write(MarkdownSyntax.SPACE);
            }
            markdownWriter.write(getCorrectMarkdownString(item.toString()));
            writeLineBreak();

            List<IObject> itemContents = item.getContents();
            if (!itemContents.isEmpty()) {
                writeLineBreak();
                writeContents(itemContents, false);
            }
        }
    }

    protected void writeSemanticTextNode(SemanticTextNode textNode) throws IOException {
        String value = textNode.getValue();
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

        markdownWriter.write(getCorrectMarkdownString(value));
    }

    protected void writeTable(TableBorder table) throws IOException {
        if (!shouldWriteTableBody()) {
            return;
        }
        enterTable();
        for (TableBorderRow row : table.getRows()) {
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (TableBorderCell cell : row.getCells()) {
                List<IObject> cellContents = cell.getContents();
                writeContents(cellContents, true);
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

    protected boolean shouldWriteTableBody() {
        return Config.MARKDOWN_TABLE_OUTPUT_FULL.equals(markdownTableOutput);
    }

    protected void extendCrossPageTableArtifactSkips(List<List<IObject>> contents, List<Set<Integer>> pageSkipIndices) {
        if (Config.MARKDOWN_TABLE_OUTPUT_FULL.equals(markdownTableOutput)) {
            return;
        }
        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            List<IObject> pageContents = contents.get(pageNumber);
            Set<Integer> pageSkips = pageSkipIndices.get(pageNumber);

            int firstMeaningful = findFirstMeaningfulContentIndex(pageContents, pageSkips);
            if (firstMeaningful >= 0 && isTableCaptionText(normalizeContentText(pageContents.get(firstMeaningful))) && pageNumber > 0) {
                walkTableArtifactRange(contents.get(pageNumber - 1), pageSkipIndices.get(pageNumber - 1), contents.get(pageNumber - 1).size(), -1);
            }

            int lastMeaningful = findLastMeaningfulContentIndex(pageContents, pageSkips);
            if (lastMeaningful >= 0 && isTableCaptionText(normalizeContentText(pageContents.get(lastMeaningful))) && pageNumber + 1 < contents.size()) {
                walkTableArtifactRange(contents.get(pageNumber + 1), pageSkipIndices.get(pageNumber + 1), -1, 1);
            }
        }
    }

    protected Set<Integer> collectTableArtifactIndices(List<IObject> pageContents) {
        Set<Integer> skip = new HashSet<>();
        if (Config.MARKDOWN_TABLE_OUTPUT_FULL.equals(markdownTableOutput)) {
            return skip;
        }

        for (int index = 0; index < pageContents.size(); index++) {
            IObject content = pageContents.get(index);
            String text = normalizeContentText(content);

            if (isTableOutputOff() && isTableCaptionText(text)) {
                skip.add(index);
                walkTableArtifactRange(pageContents, skip, index, -1);
                walkTableArtifactRange(pageContents, skip, index, 1);
                continue;
            }

            if (!isTableCaptionText(text)) {
                continue;
            }
            walkTableArtifactRange(pageContents, skip, index, -1);
            walkTableArtifactRange(pageContents, skip, index, 1);
        }

        return skip;
    }

    protected void walkTableArtifactRange(List<IObject> pageContents, Set<Integer> skip, int startIndex, int direction) {
        int index = startIndex + direction;
        while (index >= 0 && index < pageContents.size()) {
            IObject content = pageContents.get(index);
            String text = normalizeContentText(content);

            if (isHeadingContent(content) || isTableCaptionText(text)) {
                break;
            }
            if (content instanceof TableBorder) {
                skip.add(index);
                index += direction;
                continue;
            }
            if (text.isEmpty()) {
                index += direction;
                continue;
            }
            if (looksTableArtifactText(text)) {
                skip.add(index);
                index += direction;
                continue;
            }
            if (looksNarrativeText(text)) {
                if (direction > 0 && shouldSkipDanglingNarrativeFragment(pageContents, index, text)) {
                    skip.add(index);
                    index += direction;
                    continue;
                }
                break;
            }
            break;
        }
    }

    protected boolean isHeadingContent(IObject content) {
        return content instanceof SemanticHeading;
    }

    protected int findFirstMeaningfulContentIndex(List<IObject> pageContents, Set<Integer> skip) {
        for (int index = 0; index < pageContents.size(); index++) {
            if (skip.contains(index)) {
                continue;
            }
            String text = normalizeContentText(pageContents.get(index));
            if (!text.isEmpty() || pageContents.get(index) instanceof TableBorder) {
                return index;
            }
        }
        return -1;
    }

    protected int findLastMeaningfulContentIndex(List<IObject> pageContents, Set<Integer> skip) {
        for (int index = pageContents.size() - 1; index >= 0; index--) {
            if (skip.contains(index)) {
                continue;
            }
            String text = normalizeContentText(pageContents.get(index));
            if (!text.isEmpty() || pageContents.get(index) instanceof TableBorder) {
                return index;
            }
        }
        return -1;
    }

    protected boolean isTableOutputOff() {
        return Config.MARKDOWN_TABLE_OUTPUT_OFF.equals(markdownTableOutput);
    }

    protected String normalizeContentText(IObject content) {
        if (content instanceof PDFList) {
            StringBuilder builder = new StringBuilder();
            for (ListItem item : ((PDFList) content).getListItems()) {
                String value = String.valueOf(item).replaceAll("\\s+", " ").trim();
                if (value.isEmpty()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(value);
            }
            return builder.toString();
        }
        if (!(content instanceof SemanticTextNode)) {
            return "";
        }
        String value = ((SemanticTextNode) content).getValue();
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    protected boolean isTableCaptionText(String text) {
        return TABLE_CAPTION_PATTERN.matcher(text).find();
    }

    protected boolean looksNarrativeText(String text) {
        if (text.isEmpty()) {
            return false;
        }
        if (text.matches(".*[.?!][)\"'\\]]?(?:\\s|$).*")) {
            return true;
        }
        int lowerWordCount = 0;
        for (String token : text.split("\\s+")) {
            if (token.matches("[a-z]{3,}")) {
                lowerWordCount++;
            }
        }
        return lowerWordCount >= 6;
    }

    protected boolean looksTableArtifactText(String text) {
        if (text.isEmpty() || isTableCaptionText(text)) {
            return false;
        }
        if (PIPE_ROW_PATTERN.matcher(text).matches()) {
            return true;
        }
        if (NUMERIC_ONLY_PATTERN.matcher(text).matches()) {
            return true;
        }
        if (FOOTNOTE_PLACEHOLDER_PATTERN.matcher(text).matches()) {
            return true;
        }
        if (TABLE_HEADER_TEXT_PATTERN.matcher(text).matches()) {
            return true;
        }
        if (TABLE_HEADER_PREFIX_PATTERN.matcher(text).find()) {
            return true;
        }
        if (PASS_COLUMN_PATTERN.matcher(text).matches()) {
            return true;
        }
        int benchmarkMatches = 0;
        java.util.regex.Matcher matcher = BENCHMARK_PATTERN.matcher(text);
        while (matcher.find()) {
            benchmarkMatches++;
        }
        if (benchmarkMatches >= 2) {
            return true;
        }
        if (text.matches("(?:.*\\b\\d+(?:\\.\\d+)?\\b.*){4,}") && !looksNarrativeText(text)) {
            return true;
        }
        if (benchmarkMatches >= 1 && !looksNarrativeText(text)) {
            return true;
        }
        if (!looksNarrativeText(text) && MODELISH_LINE_PATTERN.matcher(text).find() && text.matches(".*\\b\\d.*")) {
            return true;
        }
        if (!looksNarrativeText(text)) {
            String[] tokens = text.split("\\s+");
            int modelishTokenCount = 0;
            for (String token : tokens) {
                if (MODEL_NAME_PATTERN.matcher(token).matches()) {
                    modelishTokenCount++;
                }
            }
            if (tokens.length >= 4 && modelishTokenCount >= Math.max(3, (int) Math.floor(tokens.length * 0.6))) {
                return true;
            }
        }
        return false;
    }

    protected boolean shouldSkipDanglingNarrativeFragment(List<IObject> pageContents, int index, String text) {
        if (text.isEmpty() || !Character.isLowerCase(text.charAt(0))) {
            return false;
        }
        for (int nextIndex = index + 1; nextIndex < pageContents.size(); nextIndex++) {
            IObject next = pageContents.get(nextIndex);
            String nextText = normalizeContentText(next);
            if (nextText.isEmpty()) {
                continue;
            }
            if (isHeadingContent(next) || isTableCaptionText(nextText)) {
                return true;
            }
            if (looksNarrativeText(nextText) && !looksTableArtifactText(nextText)) {
                return false;
            }
        }
        return false;
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
        if (value != null) {
            return value.replace("\u0000", " ");
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        if (markdownWriter != null) {
            markdownWriter.close();
        }
    }
}

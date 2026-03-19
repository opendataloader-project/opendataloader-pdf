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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
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
    private static final Pattern TABLE_CAPTION_RE = Pattern.compile("^Table\\s+\\d+\\s*\\|\\s+.+", Pattern.CASE_INSENSITIVE);
    private static final Pattern TABLE_CAPTION_IN_TEXT_RE = Pattern.compile(".*\\b(Table\\s+\\d+\\s*\\|\\s+.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_LABEL_PAIR_RE = Pattern.compile("([A-Za-z][A-Za-z0-9.+\\-]*)\\s+(20\\d{2})");
    private static final Pattern YEAR_TOKEN_RE = Pattern.compile("^(?:19|20)\\d{2}$");
    private static final Pattern BENCHMARK_HEADER_HINT_TOKEN_RE = Pattern.compile("^(?:model|aime|math(?:-\\d+)?|gpqa|livecode|codeforces|diamond|bench)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern WIDE_TABLE_VALUE_TOKEN_RE = Pattern.compile("^(?:-|MoE|\\d+(?:\\.\\d+)?%?|\\d+[A-Za-z]+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern METRIC_TOKEN_RE = Pattern.compile("^(?:pass@\\d+|cons@\\d+|rating|score)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_TOKEN_RE = Pattern.compile("^-?\\d+(?:\\.\\d+)?%?$");
    private static final Pattern MODEL_TOKEN_RE = Pattern.compile("^[A-Za-z][A-Za-z0-9._'\\-]*$");
    private static final int MAX_PARAGRAPHS_FOR_TABLE_RECOVERY = 20;
    private static final int MAX_TABLE_RECOVERY_LEAD_IN = 8;
    private static final int MAX_NON_TEXT_GAP_FOR_TABLE_RECOVERY = 8;

    private static class RecoveredTableRow {
        final String model;
        final List<String> metrics;

        RecoveredTableRow(String model, List<String> metrics) {
            this.model = model;
            this.metrics = metrics;
        }
    }

    MarkdownGenerator(File inputPdf, Config config) throws IOException {
        String cutPdfFileName = inputPdf.getName();
        this.markdownFileName = config.getOutputFolder() + File.separator + cutPdfFileName.substring(0, cutPdfFileName.length() - 3) + "md";
        this.markdownWriter = new FileWriter(markdownFileName, StandardCharsets.UTF_8);
        this.isImageSupported = !config.isImageOutputOff() && config.isGenerateMarkdown();
        this.markdownPageSeparator = config.getMarkdownPageSeparator();
        this.embedImages = config.isEmbedImages();
        this.imageFormat = config.getImageFormat();
        this.includeHeaderFooter = config.isIncludeHeaderFooter();
    }

    public void writeToMarkdown(List<List<IObject>> contents) {
        try {
            for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
                writePageSeparator(pageNumber);
                List<IObject> pageContents = contents.get(pageNumber);
                for (int i = 0; i < pageContents.size(); i++) {
                    int consumed = tryWriteRecoveredFlattenedBenchmarkTable(pageContents, i);
                    if (consumed > 0) {
                        i += consumed - 1;
                        continue;
                    }

                    IObject content = pageContents.get(i);
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

    private int tryWriteRecoveredFlattenedBenchmarkTable(List<IObject> pageContents, int startIndex) throws IOException {
        if (startIndex < 0 || startIndex >= pageContents.size()) {
            return 0;
        }
        if (!isRecoverableTableTextNode(pageContents.get(startIndex))) {
            return 0;
        }

        String startValue = normalizeSpace(((SemanticTextNode) pageContents.get(startIndex)).getValue());
        boolean startsWithCaption = TABLE_CAPTION_RE.matcher(startValue).matches();
        int startMetricCount = countMetricTokens(startValue);
        boolean startsWithMetricHeader = startMetricCount >= 3;
        boolean startsWithPreHeader = isLikelyBenchmarkPreHeader(startValue);
        if (!startsWithCaption && !startsWithMetricHeader && !startsWithPreHeader) {
            return 0;
        }

        List<String> paragraphValues = new ArrayList<>();
        List<Integer> paragraphIndexes = new ArrayList<>();
        int cursor = startIndex;
        int nonTextGap = 0;

        while (cursor < pageContents.size() && paragraphValues.size() < MAX_PARAGRAPHS_FOR_TABLE_RECOVERY) {
            IObject obj = pageContents.get(cursor);
            if (!isRecoverableTableTextNode(obj)) {
                if (!paragraphValues.isEmpty()) {
                    nonTextGap += 1;
                    if (nonTextGap > MAX_NON_TEXT_GAP_FOR_TABLE_RECOVERY) {
                        break;
                    }
                }
                cursor += 1;
                continue;
            }
            nonTextGap = 0;

            String value = normalizeSpace(((SemanticTextNode) obj).getValue());
            if (!value.isEmpty()) {
                paragraphValues.add(value);
                paragraphIndexes.add(cursor);
            }

            cursor += 1;
        }

        if (paragraphValues.size() < 3) {
            return 0;
        }

        if (startsWithPreHeader && !startsWithCaption && !startsWithMetricHeader) {
            return tryRecoverTableWithLeadingHeader(paragraphValues, paragraphIndexes, startIndex);
        }

        if (startsWithCaption) {
            int captionPos = 0;
            int metricPos = -1;
            int metricCount = 0;
            for (int i = 1; i < paragraphValues.size(); i++) {
                int count = countMetricTokens(paragraphValues.get(i));
                if (count >= 3) {
                    metricPos = i;
                    metricCount = count;
                    break;
                }
                if (i > MAX_TABLE_RECOVERY_LEAD_IN) {
                    break;
                }
            }
            if (metricPos < 0) {
                return tryRecoverCaptionWithInlineData(paragraphValues, paragraphIndexes, captionPos, startIndex);
            }

            List<String> metricColumns = buildMetricColumns(paragraphValues.get(metricPos), metricCount);
            if (metricColumns.size() < 3) {
                return 0;
            }

            StringBuilder dataText = new StringBuilder();
            List<RecoveredTableRow> rows = new ArrayList<>();
            int dataEndExclusive = metricPos + 1;
            for (int i = metricPos + 1; i < paragraphValues.size(); i++) {
                String line = paragraphValues.get(i);
                if (TABLE_CAPTION_RE.matcher(line).matches()) {
                    break;
                }
                if (isLikelyProseLine(line) && rows.size() >= 2) {
                    break;
                }
                if (dataText.length() > 0) {
                    dataText.append(' ');
                }
                dataText.append(line);
                dataEndExclusive = i + 1;
                rows = recoverBenchmarkRowsFromText(dataText.toString(), metricColumns.size());
            }
            if (rows.size() < minimumRecoveredRowCount(metricColumns)) {
                return 0;
            }

            markdownWriter.write(getCorrectMarkdownString(paragraphValues.get(captionPos)));
            writeContentsSeparator();
            writeRecoveredTable(metricColumns, rows);
            writeContentsSeparator();

            int lastDataPos = dataEndExclusive - 1;
            int lastConsumedAbsoluteIndex = paragraphIndexes.get(lastDataPos);
            return lastConsumedAbsoluteIndex - startIndex + 1;
        }

        int metricPos = 0;
        int metricCount = startMetricCount;
        int captionPos = -1;
        for (int i = 1; i < paragraphValues.size(); i++) {
            if (TABLE_CAPTION_RE.matcher(paragraphValues.get(i)).matches()) {
                captionPos = i;
                break;
            }
        }

        if (metricPos > MAX_TABLE_RECOVERY_LEAD_IN) {
            return 0;
        }

        List<String> metricColumns = buildMetricColumns(paragraphValues.get(metricPos), metricCount);
        if (metricColumns.size() < 3) {
            return 0;
        }

        if (captionPos < 0) {
            StringBuilder dataText = new StringBuilder();
            List<RecoveredTableRow> rows = new ArrayList<>();
            int dataEndExclusive = metricPos + 1;
            for (int i = metricPos + 1; i < paragraphValues.size(); i++) {
                String line = paragraphValues.get(i);
                if (TABLE_CAPTION_RE.matcher(line).matches()) {
                    break;
                }
                if (isLikelyProseLine(line) && rows.size() >= 2) {
                    break;
                }
                if (dataText.length() > 0) {
                    dataText.append(' ');
                }
                dataText.append(line);
                dataEndExclusive = i + 1;
                rows = recoverBenchmarkRowsFromText(dataText.toString(), metricColumns.size());
            }
            if (rows.size() < minimumRecoveredRowCount(metricColumns)) {
                return 0;
            }

            String nearbyCaption = findNearbyCaptionBefore(pageContents, startIndex);
            if (!nearbyCaption.isEmpty()) {
                markdownWriter.write(getCorrectMarkdownString(nearbyCaption));
                writeContentsSeparator();
            }
            writeRecoveredTable(metricColumns, rows);
            writeContentsSeparator();

            int lastDataPos = dataEndExclusive - 1;
            int lastConsumedAbsoluteIndex = paragraphIndexes.get(lastDataPos);
            return lastConsumedAbsoluteIndex - startIndex + 1;
        }

        if (captionPos < 2) {
            return 0;
        }

        StringBuilder dataText = new StringBuilder();
        for (int i = metricPos + 1; i < captionPos; i++) {
            if (dataText.length() > 0) {
                dataText.append(' ');
            }
            dataText.append(paragraphValues.get(i));
        }

        List<RecoveredTableRow> rows = recoverBenchmarkRowsFromText(dataText.toString(), metricColumns.size());
        if (rows.size() < minimumRecoveredRowCount(metricColumns)) {
            return 0;
        }

        writeRecoveredTable(metricColumns, rows);
        writeContentsSeparator();
        markdownWriter.write(getCorrectMarkdownString(paragraphValues.get(captionPos)));
        writeContentsSeparator();

        int lastConsumedAbsoluteIndex = paragraphIndexes.get(captionPos);
        return lastConsumedAbsoluteIndex - startIndex + 1;
    }

    private int tryRecoverTableWithLeadingHeader(List<String> paragraphValues, List<Integer> paragraphIndexes, int startIndex) throws IOException {
        int metricPos = -1;
        int metricCount = 0;
        for (int i = 1; i < paragraphValues.size(); i++) {
            int count = countMetricTokens(paragraphValues.get(i));
            if (count >= 3) {
                metricPos = i;
                metricCount = count;
                break;
            }
            if (i > MAX_TABLE_RECOVERY_LEAD_IN + 2 && isLikelyProseLine(paragraphValues.get(i))) {
                return 0;
            }
        }
        if (metricPos < 1) {
            return tryRecoverWideComparisonTable(paragraphValues, paragraphIndexes, startIndex);
        }

        List<String> metricColumns = buildMetricColumns(paragraphValues.get(metricPos), metricCount);
        if (metricColumns.size() < 3) {
            return 0;
        }

        int captionPos = -1;
        for (int i = metricPos + 1; i < paragraphValues.size(); i++) {
            if (TABLE_CAPTION_RE.matcher(paragraphValues.get(i)).matches()) {
                captionPos = i;
                break;
            }
        }

        StringBuilder dataText = new StringBuilder();
        int dataEndExclusive = metricPos + 1;
        if (captionPos >= 0) {
            for (int i = metricPos + 1; i < captionPos; i++) {
                if (dataText.length() > 0) {
                    dataText.append(' ');
                }
                dataText.append(paragraphValues.get(i));
                dataEndExclusive = i + 1;
            }
        } else {
            List<RecoveredTableRow> rollingRows = new ArrayList<>();
            for (int i = metricPos + 1; i < paragraphValues.size(); i++) {
                String line = paragraphValues.get(i);
                if (TABLE_CAPTION_RE.matcher(line).matches()) {
                    break;
                }
                if (isLikelyProseLine(line) && rollingRows.size() >= 2) {
                    break;
                }
                if (dataText.length() > 0) {
                    dataText.append(' ');
                }
                dataText.append(line);
                dataEndExclusive = i + 1;
                rollingRows = recoverBenchmarkRowsFromText(dataText.toString(), metricColumns.size());
            }
        }

        List<RecoveredTableRow> rows = recoverBenchmarkRowsFromText(dataText.toString(), metricColumns.size());
        if (rows.size() < minimumRecoveredRowCount(metricColumns)) {
            return 0;
        }

        writeRecoveredTable(metricColumns, rows);
        writeContentsSeparator();

        if (captionPos >= 0) {
            markdownWriter.write(getCorrectMarkdownString(paragraphValues.get(captionPos)));
            writeContentsSeparator();
            int lastConsumedAbsoluteIndex = paragraphIndexes.get(captionPos);
            return lastConsumedAbsoluteIndex - startIndex + 1;
        }

        int lastDataPos = dataEndExclusive - 1;
        int lastConsumedAbsoluteIndex = paragraphIndexes.get(lastDataPos);
        return lastConsumedAbsoluteIndex - startIndex + 1;
    }

    private int tryRecoverWideComparisonTable(List<String> paragraphValues, List<Integer> paragraphIndexes, int startIndex) throws IOException {
        int captionPos = -1;
        for (int i = 0; i < paragraphValues.size(); i++) {
            if (TABLE_CAPTION_RE.matcher(paragraphValues.get(i)).matches()) {
                captionPos = i;
                break;
            }
        }
        if (captionPos < 2) {
            return 0;
        }

        int columnCount = inferWideTableColumnCount(paragraphValues, captionPos);
        if (columnCount < 4) {
            return 0;
        }

        List<String> metricColumns = buildWideComparisonColumns(paragraphValues, captionPos, columnCount);
        List<RecoveredTableRow> rows = new ArrayList<>();
        for (int i = 0; i < captionPos; i++) {
            String line = paragraphValues.get(i);
            if (line.equalsIgnoreCase("Model")) {
                continue;
            }
            if (line.equalsIgnoreCase("English") || line.equalsIgnoreCase("Code") || line.equalsIgnoreCase("Math") || line.equalsIgnoreCase("Chinese")) {
                continue;
            }
            RecoveredTableRow row = parseWideComparisonRow(line, columnCount);
            if (row != null) {
                rows.add(row);
            }
        }

        if (rows.size() < 5) {
            return 0;
        }

        writeRecoveredTable(metricColumns, rows);
        writeContentsSeparator();
        markdownWriter.write(getCorrectMarkdownString(paragraphValues.get(captionPos)));
        writeContentsSeparator();

        int lastConsumedAbsoluteIndex = paragraphIndexes.get(captionPos);
        return lastConsumedAbsoluteIndex - startIndex + 1;
    }

    private int tryRecoverCaptionWithInlineData(List<String> paragraphValues, List<Integer> paragraphIndexes,
                                                int captionPos, int startIndex) throws IOException {
        int inlinePos = -1;
        int firstModelTokenPos = -1;
        String[] inlineTokens = null;

        for (int i = captionPos + 1; i < paragraphValues.size(); i++) {
            String line = paragraphValues.get(i);
            String[] tokens = normalizeSpace(line).split(" ");
            int modelPos = findFirstModelTokenIndex(tokens);
            if (modelPos >= 0) {
                inlinePos = i;
                firstModelTokenPos = modelPos;
                inlineTokens = tokens;
                break;
            }
            if (i > captionPos + MAX_TABLE_RECOVERY_LEAD_IN && isLikelyProseLine(line)) {
                return 0;
            }
        }

        if (inlinePos < 0 || inlineTokens == null) {
            return 0;
        }

        String headerPrefix = joinTokens(inlineTokens, 0, firstModelTokenPos);
        StringBuilder dataText = new StringBuilder(joinTokens(inlineTokens, firstModelTokenPos, inlineTokens.length));
        int dataEndExclusive = inlinePos + 1;

        int metricCount = inferMetricCountFromDataText(dataText.toString());
        if (metricCount < 2) {
            return 0;
        }

        List<String> metricColumns = buildMetricColumnsFromCaptionOrPrefix(paragraphValues.get(captionPos), headerPrefix, metricCount);
        List<RecoveredTableRow> rows = recoverBenchmarkRowsFromText(dataText.toString(), metricCount);

        for (int i = inlinePos + 1; i < paragraphValues.size(); i++) {
            String line = paragraphValues.get(i);
            if (TABLE_CAPTION_RE.matcher(line).matches()) {
                break;
            }
            if (isLikelyProseLine(line) && rows.size() >= 2) {
                break;
            }
            dataText.append(' ').append(line);
            dataEndExclusive = i + 1;

            int inferredMetricCount = inferMetricCountFromDataText(dataText.toString());
            if (inferredMetricCount > metricCount) {
                metricCount = inferredMetricCount;
                metricColumns = buildMetricColumnsFromCaptionOrPrefix(paragraphValues.get(captionPos), headerPrefix, metricCount);
            }
            rows = recoverBenchmarkRowsFromText(dataText.toString(), metricCount);
        }

        if (rows.size() < minimumRecoveredRowCount(metricColumns)) {
            return 0;
        }

        markdownWriter.write(getCorrectMarkdownString(paragraphValues.get(captionPos)));
        writeContentsSeparator();
        writeRecoveredTable(metricColumns, rows);
        writeContentsSeparator();

        int lastDataPos = dataEndExclusive - 1;
        int lastConsumedAbsoluteIndex = paragraphIndexes.get(lastDataPos);
        return lastConsumedAbsoluteIndex - startIndex + 1;
    }

    private String findNearbyCaptionBefore(List<IObject> pageContents, int startIndex) {
        int minIndex = Math.max(0, startIndex - MAX_TABLE_RECOVERY_LEAD_IN);
        for (int i = startIndex - 1; i >= minIndex; i--) {
            IObject object = pageContents.get(i);
            if (!isRecoverableTableTextNode(object)) {
                continue;
            }
            String value = normalizeSpace(((SemanticTextNode) object).getValue());
            if (TABLE_CAPTION_RE.matcher(value).matches()) {
                return value;
            }
            Matcher captionMatcher = TABLE_CAPTION_IN_TEXT_RE.matcher(value);
            if (captionMatcher.matches()) {
                return normalizeSpace(captionMatcher.group(1));
            }
        }
        return "";
    }

    private boolean isRecoverableTableTextNode(IObject object) {
        return object instanceof SemanticTextNode && !(object instanceof SemanticHeaderOrFooter);
    }

    private void writeRecoveredTable(List<String> metricColumns, List<RecoveredTableRow> rows) throws IOException {
        markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR + " Model " + MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
        for (String col : metricColumns) {
            markdownWriter.write(" " + col + " " + MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
        }
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);

        markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR + MarkdownSyntax.TABLE_HEADER_SEPARATOR + MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
        for (int i = 0; i < metricColumns.size(); i++) {
            markdownWriter.write(MarkdownSyntax.TABLE_HEADER_SEPARATOR + MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
        }
        markdownWriter.write(MarkdownSyntax.LINE_BREAK);

        for (RecoveredTableRow row : rows) {
            markdownWriter.write(MarkdownSyntax.TABLE_COLUMN_SEPARATOR + " " + row.model + " " + MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            for (String value : row.metrics) {
                markdownWriter.write(" " + value + " " + MarkdownSyntax.TABLE_COLUMN_SEPARATOR);
            }
            markdownWriter.write(MarkdownSyntax.LINE_BREAK);
        }
    }

    private static List<String> buildMetricColumnsFromCaptionOrPrefix(String caption, String headerPrefix, int metricCount) {
        List<String> fromCaption = extractYearMetricColumns(caption);
        if (fromCaption.size() >= metricCount) {
            return finalizeMetricColumns(fromCaption.subList(fromCaption.size() - metricCount, fromCaption.size()), metricCount);
        }

        List<String> fromHeader = extractYearMetricColumns(headerPrefix);
        if (fromHeader.size() >= metricCount) {
            return finalizeMetricColumns(fromHeader.subList(fromHeader.size() - metricCount, fromHeader.size()), metricCount);
        }

        return finalizeMetricColumns(new ArrayList<>(), metricCount);
    }

    private static List<String> extractYearMetricColumns(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        Matcher matcher = YEAR_LABEL_PAIR_RE.matcher(normalizeSpace(text));
        while (matcher.find()) {
            out.add(matcher.group(1) + " " + matcher.group(2));
        }
        return out;
    }

    private static String joinTokens(String[] tokens, int startInclusive, int endExclusive) {
        StringBuilder out = new StringBuilder();
        for (int i = startInclusive; i < endExclusive && i < tokens.length; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(tokens[i]);
        }
        return out.toString();
    }

    private static int findFirstModelTokenIndex(String[] tokens) {
        for (int i = 0; i < tokens.length - 1; i++) {
            if (!MODEL_TOKEN_RE.matcher(tokens[i]).matches() || !NUMERIC_TOKEN_RE.matcher(tokens[i + 1]).matches()) {
                continue;
            }
            if (isLikelyYearHeaderToken(tokens[i], tokens[i + 1])) {
                continue;
            }
            if (i > 0 && isLikelyYearHeaderToken(tokens[i - 1], tokens[i])) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static boolean isLikelyYearHeaderToken(String labelToken, String yearToken) {
        if (!YEAR_TOKEN_RE.matcher(yearToken).matches()) {
            return false;
        }
        return labelToken.matches("^[A-Z]{2,8}$");
    }

    private static int inferMetricCountFromDataText(String dataText) {
        if (dataText == null || dataText.isBlank()) {
            return 0;
        }

        String[] tokens = normalizeSpace(dataText).split(" ");
        int maxMetrics = 0;
        int i = 0;
        while (i < tokens.length) {
            if (!MODEL_TOKEN_RE.matcher(tokens[i]).matches()) {
                i += 1;
                continue;
            }

            int j = i + 1;
            int metricCount = 0;
            while (j < tokens.length && NUMERIC_TOKEN_RE.matcher(tokens[j]).matches()) {
                metricCount += 1;
                j += 1;
            }
            maxMetrics = Math.max(maxMetrics, metricCount);
            i = Math.max(j, i + 1);
        }

        return maxMetrics;
    }

    private static int minimumRecoveredRowCount(List<String> metricColumns) {
        if (metricColumns.isEmpty()) {
            return 2;
        }
        if (metricColumns.get(0).startsWith("metric_")) {
            return 3;
        }
        return 2;
    }

    private static int inferWideTableColumnCount(List<String> paragraphValues, int captionPos) {
        int bestColumnCount = 0;
        int bestRowCount = 0;
        for (int candidate = 4; candidate <= 8; candidate++) {
            int rowCount = 0;
            for (int i = 0; i < captionPos; i++) {
                if (parseWideComparisonRow(paragraphValues.get(i), candidate) != null) {
                    rowCount += 1;
                }
            }
            if (rowCount > bestRowCount) {
                bestRowCount = rowCount;
                bestColumnCount = candidate;
            }
        }
        if (bestRowCount < 5) {
            return 0;
        }
        return bestColumnCount;
    }

    private static List<String> buildWideComparisonColumns(List<String> paragraphValues, int captionPos, int columnCount) {
        int benchmarkPos = -1;
        for (int i = 0; i < captionPos; i++) {
            String line = paragraphValues.get(i);
            if (line.toLowerCase().contains("benchmark") && line.toLowerCase().contains("metric")) {
                benchmarkPos = i;
                break;
            }
        }

        if (benchmarkPos > 0 && benchmarkPos + 1 < captionPos) {
            List<String> upper = splitLineTokens(paragraphValues.get(benchmarkPos - 1));
            List<String> lower = splitLineTokens(paragraphValues.get(benchmarkPos + 1));
            if (upper.size() == columnCount && lower.size() == columnCount) {
                List<String> merged = new ArrayList<>();
                for (int i = 0; i < columnCount; i++) {
                    merged.add(upper.get(i) + " " + lower.get(i));
                }
                return finalizeMetricColumns(merged, columnCount);
            }
            if (lower.size() == columnCount) {
                return finalizeMetricColumns(lower, columnCount);
            }
            if (upper.size() == columnCount) {
                return finalizeMetricColumns(upper, columnCount);
            }
        }

        for (int i = 0; i < captionPos; i++) {
            List<String> tokens = splitLineTokens(paragraphValues.get(i));
            if (tokens.size() == columnCount && !paragraphValues.get(i).toLowerCase().contains("benchmark")) {
                return finalizeMetricColumns(tokens, columnCount);
            }
        }

        return finalizeMetricColumns(new ArrayList<>(), columnCount);
    }

    private static RecoveredTableRow parseWideComparisonRow(String line, int columnCount) {
        List<String> tokens = splitLineTokens(line);
        if (tokens.size() <= columnCount) {
            return null;
        }

        List<String> values = new ArrayList<>();
        int cursor = tokens.size() - 1;
        while (cursor >= 0 && values.size() < columnCount && WIDE_TABLE_VALUE_TOKEN_RE.matcher(tokens.get(cursor)).matches()) {
            values.add(0, tokens.get(cursor));
            cursor -= 1;
        }
        if (values.size() != columnCount) {
            return null;
        }

        StringBuilder labelBuilder = new StringBuilder();
        for (int i = 0; i <= cursor; i++) {
            if (labelBuilder.length() > 0) {
                labelBuilder.append(' ');
            }
            labelBuilder.append(tokens.get(i));
        }
        String label = normalizeSpace(labelBuilder.toString());
        if (label.isEmpty() || label.equalsIgnoreCase("Model")) {
            return null;
        }
        return new RecoveredTableRow(label, values);
    }

    private static List<String> splitLineTokens(String line) {
        List<String> out = new ArrayList<>();
        String normalized = normalizeSpace(line);
        if (normalized.isEmpty()) {
            return out;
        }
        String[] tokens = normalized.split(" ");
        for (String token : tokens) {
            if (!token.isBlank()) {
                out.add(token.trim());
            }
        }
        return out;
    }

    static List<String> buildMetricColumns(String metricLine, int metricCount) {
        List<String> raw = new ArrayList<>();
        String[] tokens = normalizeSpace(metricLine).split(" ");
        for (String token : tokens) {
            if (METRIC_TOKEN_RE.matcher(token).matches()) {
                raw.add(token);
            }
        }
        if (raw.size() < metricCount) {
            raw.clear();
            for (String token : tokens) {
                if (!token.isBlank()) {
                    raw.add(token);
                }
            }
        }

        return finalizeMetricColumns(raw, metricCount);
    }

    private static List<String> finalizeMetricColumns(List<String> raw, int metricCount) {
        List<String> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (String token : raw) {
            if (out.size() >= metricCount) {
                break;
            }
            String base = token.trim();
            if (base.isEmpty()) {
                continue;
            }
            int count = seen.getOrDefault(base, 0) + 1;
            seen.put(base, count);
            out.add(count == 1 ? base : base + "_" + count);
        }

        while (out.size() < metricCount) {
            out.add("metric_" + (out.size() + 1));
        }
        return out;
    }

    static List<RecoveredTableRow> recoverBenchmarkRowsFromText(String dataText, int metricCount) {
        List<RecoveredTableRow> rows = new ArrayList<>();
        if (dataText == null || dataText.isBlank() || metricCount <= 0) {
            return rows;
        }

        String[] tokens = normalizeSpace(dataText).split(" ");
        int fullRowCount = 0;
        int i = 0;
        while (i < tokens.length) {
            String model = tokens[i];
            if (!MODEL_TOKEN_RE.matcher(model).matches()) {
                i += 1;
                continue;
            }

            List<String> metrics = new ArrayList<>();
            int j = i + 1;
            while (j < tokens.length && metrics.size() < metricCount && NUMERIC_TOKEN_RE.matcher(tokens[j]).matches()) {
                metrics.add(tokens[j]);
                j += 1;
            }

            if (metrics.size() == metricCount) {
                rows.add(new RecoveredTableRow(model, metrics));
                fullRowCount += 1;
                i = j;
                continue;
            }

            int minimumPartialMetrics = Math.max(1, metricCount - 2);
            boolean nextLooksLikeModel = j < tokens.length && MODEL_TOKEN_RE.matcher(tokens[j]).matches();
            if (nextLooksLikeModel && metrics.size() >= minimumPartialMetrics) {
                while (metrics.size() < metricCount) {
                    metrics.add("");
                }
                rows.add(new RecoveredTableRow(model, metrics));
                i = j;
                continue;
            }

            i += 1;
        }

        if (fullRowCount == 0) {
            return new ArrayList<>();
        }

        return rows;
    }

    static int countMetricTokens(String line) {
        if (line == null || line.isBlank()) {
            return 0;
        }
        int count = 0;
        String[] tokens = normalizeSpace(line).split(" ");
        for (String token : tokens) {
            if (METRIC_TOKEN_RE.matcher(token).matches()) {
                count += 1;
            }
        }
        return count;
    }

    static String normalizeSpace(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private static int countNumericTokens(String line) {
        if (line == null || line.isBlank()) {
            return 0;
        }
        int count = 0;
        String[] tokens = normalizeSpace(line).split(" ");
        for (String token : tokens) {
            if (NUMERIC_TOKEN_RE.matcher(token).matches()) {
                count += 1;
            }
        }
        return count;
    }

    private static boolean isLikelyProseLine(String line) {
        String normalized = normalizeSpace(line);
        if (normalized.isEmpty()) {
            return false;
        }
        int tokenCount = normalized.split(" ").length;
        int numericCount = countNumericTokens(normalized);
        boolean hasSentencePunctuation = normalized.contains(".") || normalized.contains(";");
        if (tokenCount >= 14 && hasSentencePunctuation && numericCount <= 4) {
            return true;
        }
        return tokenCount >= 28 && numericCount <= 6;
    }

    private static boolean isLikelyBenchmarkPreHeader(String line) {
        String normalized = normalizeSpace(line);
        if (normalized.isEmpty()) {
            return false;
        }

        String lower = normalized.toLowerCase();
        if (lower.contains("benchmark") && lower.contains("metric")) {
            return true;
        }

        if (normalized.equalsIgnoreCase("Model")) {
            return true;
        }

        String[] tokens = normalized.split(" ");
        if (tokens.length > 10) {
            return false;
        }

        int hintCount = 0;
        for (String token : tokens) {
            String cleaned = token.replaceAll("[^A-Za-z0-9@-]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            if (BENCHMARK_HEADER_HINT_TOKEN_RE.matcher(cleaned).matches()) {
                hintCount += 1;
            }
        }
        if (hintCount >= 2) {
            return true;
        }

        int modelLikeCount = 0;
        for (String token : tokens) {
            String cleaned = token.replaceAll("[^A-Za-z0-9.-]", "");
            if (cleaned.isEmpty()) {
                continue;
            }
            boolean hasUppercase = !cleaned.equals(cleaned.toLowerCase());
            boolean hasDigitOrHyphen = cleaned.matches(".*[0-9-].*");
            if ((hasUppercase || hasDigitOrHyphen) && cleaned.length() <= 24) {
                modelLikeCount += 1;
            }
        }
        return tokens.length >= 4 && modelLikeCount >= 3;
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

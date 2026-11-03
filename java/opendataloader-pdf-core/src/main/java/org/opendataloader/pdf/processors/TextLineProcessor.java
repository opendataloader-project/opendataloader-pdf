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
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ListUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class TextLineProcessor {

    private static final double ONE_LINE_PROBABILITY = 0.75;
    /** The gap between two columns of a block is at least this many font sizes. */
    private static final double MIN_COLUMN_GAP = 6;
    /** The rows of a block of two columns are this many font sizes apart: more than single spacing. */
    private static final double MIN_COLUMN_ROW_DISTANCE = 1.8;
    private static final double MAX_COLUMN_ROW_DISTANCE = 4;
    /** A block of two columns, as a signature block, has at most this many lines. */
    private static final int MAX_COLUMN_BLOCK_LINES = 3;
    /** A roman numeral, as the page label of a preface. */
    private static final Pattern ROMAN_NUMERAL =
        Pattern.compile("(?i)m{0,3}(c[md]|d?c{0,3})(x[cl]|l?x{0,3})(i[xv]|v?i{0,3})");
    private static final Comparator<TextChunk> TEXT_CHUNK_COMPARATOR =
        Comparator.comparingDouble(o -> o.getBoundingBox().getLeftX());

    public static List<IObject> processTextLines(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        // Track which TextChunk immediately follows a whitespace chunk in stream order,
        // using reference identity so lookups are immune to TextChunk.equals() semantics.
        // Stream order may differ from visual (leftX) order in rare PDFs, but whitespace
        // chunks originate from the same PDF text operator as their adjacent text chunks,
        // so stream-order adjacency is reliable for this signal.
        Set<TextChunk> chunksAfterWhitespace = Collections.newSetFromMap(new IdentityHashMap<>());
        TextLine previousLine = new TextLine(new TextChunk(""));
        boolean isSeparateLine = false;
        boolean pendingWhitespace = false;
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    if (textChunk.isWhiteSpaceChunk()) {
                        pendingWhitespace = true;
                    }
                    continue;
                }
                if (pendingWhitespace) {
                    chunksAfterWhitespace.add(textChunk);
                    pendingWhitespace = false;
                }
                TextLine currentLine = new TextLine(textChunk);
                double oneLineProbability = ChunksMergeUtils.countOneLineProbability(new SemanticTextNode(), previousLine, currentLine);
                isSeparateLine |= (oneLineProbability < ONE_LINE_PROBABILITY) || previousLine.isHiddenText() != currentLine.isHiddenText();
                if (isSeparateLine) {
                    previousLine.setBoundingBox(new BoundingBox(previousLine.getBoundingBox()));
                    previousLine = currentLine;
                    newContents.add(previousLine);
                } else {
                    previousLine.add(currentLine);
                }
                isSeparateLine = false;
            } else {
                if (content instanceof TableBorder) {
                    isSeparateLine = true;
                }
                newContents.add(content);
                pendingWhitespace = false;
            }
        }
        for (int i = 0; i < newContents.size(); i++) {
            IObject content = newContents.get(i);
            if (content instanceof TextLine) {
                TextLine textLine = (TextLine) content;
                textLine.getTextChunks().sort(TEXT_CHUNK_COMPARATOR);
                double threshold = textLine.getFontSize() * StaticContainers.getTextLineSpaceRatio();
                newContents.set(i, getTextLineWithSpaces(textLine, threshold, chunksAfterWhitespace));
            }
        }
        linkTextLinesWithConnectedLineArtBullet(newContents);
        return newContents;
    }

    /**
     * Makes one paragraph of each column of a block of two columns (#878). A signature block has this layout: two
     * or three lines, one after the other in the contents and one below the other on the page, with more than
     * single spacing between them, each with one gap of at least {@link #MIN_COLUMN_GAP} font sizes, the gaps at
     * the same place, and text with a letter after each gap. Each line is split at its gap; the parts before the
     * gaps are one paragraph, and the parts after them are another, so that each column is read as one.
     *
     * <p>A table of contents has page numbers or page labels, as {@code iv} or {@code A-12}, after its gaps, so
     * its lines stay one line each. A line with a list label, as a bullet, also stays one line. Single-spaced rows
     * of a form stay one line each; two or three rows of a form with more spacing have the layout of a signature
     * block, so they are split too.
     */
    public static List<IObject> splitTwoColumnBlocks(List<IObject> contents) {
        List<Integer> indexes = new ArrayList<>();
        List<double[]> gaps = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            if (contents.get(i) instanceof TextLine) {
                indexes.add(i);
                gaps.add(getColumnGap((TextLine) contents.get(i)));
            }
        }
        Map<Integer, List<IObject>> splitLines = new HashMap<>();
        int start = 0;
        while (start < indexes.size()) {
            int end = start;
            while (end + 1 < indexes.size() && indexes.get(end + 1) == indexes.get(end).intValue() + 1
                    && gaps.get(end) != null && gaps.get(end + 1) != null
                    && areRowsOfColumns((TextLine) contents.get(indexes.get(end)), gaps.get(end),
                    (TextLine) contents.get(indexes.get(end + 1)), gaps.get(end + 1))) {
                end++;
            }
            int size = end - start + 1;
            if (size >= 2 && size <= MAX_COLUMN_BLOCK_LINES) {
                TextBlock left = null;
                TextBlock right = null;
                for (int k = start; k <= end; k++) {
                    List<TextLine> parts = splitAt((TextLine) contents.get(indexes.get(k)), gaps.get(k)[0]);
                    left = addLine(left, parts.get(0));
                    right = addLine(right, parts.get(1));
                    splitLines.put(indexes.get(k), new ArrayList<>());
                }
                splitLines.put(indexes.get(start), Arrays.asList(ParagraphProcessor.createParagraphFromTextBlock(left),
                    ParagraphProcessor.createParagraphFromTextBlock(right)));
            }
            start = end + 1;
        }
        if (splitLines.isEmpty()) {
            return contents;
        }
        List<IObject> newContents = new ArrayList<>(contents.size() + splitLines.size());
        for (int i = 0; i < contents.size(); i++) {
            List<IObject> lines = splitLines.get(i);
            if (lines != null) {
                newContents.addAll(lines);
            } else {
                newContents.add(contents.get(i));
            }
        }
        return newContents;
    }

    /**
     * The one gap of at least {@link #MIN_COLUMN_GAP} font sizes, as {start, end}, with a letter after it and no
     * page label, or null. A line with a list label has none.
     */
    private static double[] getColumnGap(TextLine line) {
        if (BulletedParagraphUtils.isLabeledLine(line)) {
            return null;
        }
        double minGap = MIN_COLUMN_GAP * line.getFontSize();
        double[] gap = null;
        TextChunk previous = null;
        for (TextChunk chunk : line.getTextChunks()) {
            if (chunk.isWhiteSpaceChunk() || chunk.isEmpty()) {
                continue;
            }
            if (previous != null && chunk.getLeftX() - previous.getRightX() >= minGap) {
                if (gap != null) {
                    return null;
                }
                gap = new double[] {previous.getRightX(), chunk.getLeftX()};
            }
            previous = chunk;
        }
        if (gap == null) {
            return null;
        }
        StringBuilder after = new StringBuilder();
        for (TextChunk chunk : line.getTextChunks()) {
            if (chunk.getLeftX() >= gap[1]) {
                after.append(chunk.getValue());
            }
        }
        String text = after.toString().trim();
        return text.codePoints().anyMatch(Character::isLetter) && !isPageLabel(text) ? gap : null;
    }

    /** Whether the text is one word with a digit, as {@code A-12}, or a roman numeral in one case, as {@code iv}. */
    private static boolean isPageLabel(String text) {
        if (text.codePoints().anyMatch(Character::isWhitespace)) {
            return false;
        }
        if (text.codePoints().anyMatch(Character::isDigit)) {
            return true;
        }
        return ROMAN_NUMERAL.matcher(text).matches() && (text.equals(text.toLowerCase(Locale.ROOT))
            || text.equals(text.toUpperCase(Locale.ROOT)));
    }

    /** Whether the line below is the next row of the columns: more than single spacing, the gaps at one place. */
    private static boolean areRowsOfColumns(TextLine above, double[] aboveGap, TextLine below, double[] belowGap) {
        double fontSize = above.getFontSize();
        double distance = above.getBaseLine() - below.getBaseLine();
        return Math.abs(fontSize - below.getFontSize()) <= 0.1 * fontSize
                && distance >= MIN_COLUMN_ROW_DISTANCE * fontSize && distance <= MAX_COLUMN_ROW_DISTANCE * fontSize
                && aboveGap[0] < belowGap[1] && belowGap[0] < aboveGap[1];
    }

    private static TextBlock addLine(TextBlock block, TextLine line) {
        if (block == null) {
            return new TextBlock(line);
        }
        block.add(Collections.singletonList(line));
        return block;
    }

    /** The two lines of the chunks before and after the x, without the white space at the gap. */
    private static List<TextLine> splitAt(TextLine line, double x) {
        List<TextChunk> leftChunks = new ArrayList<>();
        List<TextChunk> rightChunks = new ArrayList<>();
        for (TextChunk chunk : line.getTextChunks()) {
            (chunk.getRightX() <= x ? leftChunks : rightChunks).add(chunk);
        }
        while (!leftChunks.isEmpty() && leftChunks.get(leftChunks.size() - 1).isWhiteSpaceChunk()) {
            leftChunks.remove(leftChunks.size() - 1);
        }
        while (!rightChunks.isEmpty() && rightChunks.get(0).isWhiteSpaceChunk()) {
            rightChunks.remove(0);
        }
        List<TextLine> lines = new ArrayList<>(2);
        lines.add(newLine(leftChunks));
        lines.add(newLine(rightChunks));
        return lines;
    }

    private static TextLine newLine(List<TextChunk> chunks) {
        TextLine line = new TextLine(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            line.add(chunks.get(i));
        }
        return line;
    }

    private static TextLine getTextLineWithSpaces(TextLine textLine, double threshold,
                                                   Set<TextChunk> chunksAfterWhitespace) {
        List<TextChunk> textChunks = textLine.getTextChunks();
        TextChunk currentTextChunk = textChunks.get(0);
        double previousEnd = currentTextChunk.getTextEnd();
        TextLine newLine = new TextLine();
        newLine.add(currentTextChunk);
        for (int i = 1; i < textChunks.size(); i++) {
            currentTextChunk = textChunks.get(i);
            double currentStart = currentTextChunk.getTextStart();
            boolean hasGap = currentStart - previousEnd > threshold;
            boolean hadWhitespace = chunksAfterWhitespace.contains(currentTextChunk);
            if (hasGap || hadWhitespace) {
                TextChunk spaceChunk = new TextChunk(new BoundingBox(currentTextChunk.getBoundingBox()), " ", currentTextChunk.getFontName(),
                    currentTextChunk.getFontSize(), currentTextChunk.getFontWeight(), currentTextChunk.getItalicAngle(), currentTextChunk.getBaseLine(),
                    currentTextChunk.getFontColor(), null, currentTextChunk.getSlantDegree());
                spaceChunk.setTextStart(previousEnd);
                spaceChunk.setTextEnd(currentStart);
                spaceChunk.adjustSymbolEndsToBoundingBox(null);
                newLine.add(spaceChunk);
            }
            previousEnd = currentTextChunk.getTextEnd();
            newLine.add(currentTextChunk);
        }

        return newLine;
    }

    private static void linkTextLinesWithConnectedLineArtBullet(List<IObject> contents) {
        LineArtChunk lineArtChunk = null;
        for (IObject content : contents) {
            if (content instanceof LineArtChunk) {
                lineArtChunk = (LineArtChunk) content;
                continue;
            }
            if (content instanceof TableBorder) {
                lineArtChunk = null;
            }
            if (content instanceof TextLine && lineArtChunk != null) {
                TextLine textLine = (TextLine) content;
                if (isLineConnectedWithLineArt(textLine, lineArtChunk)) {
                    textLine.setConnectedLineArtLabel(lineArtChunk);
                }
                lineArtChunk = null;
            }
        }
    }

    private static boolean isLineConnectedWithLineArt(TextLine textLine, LineArtChunk lineArt) {
        return lineArt.getRightX() <= textLine.getLeftX() && lineArt.getBoundingBox().getHeight() <
                ListUtils.LIST_LABEL_HEIGHT_EPSILON * textLine.getBoundingBox().getHeight();
    }
}

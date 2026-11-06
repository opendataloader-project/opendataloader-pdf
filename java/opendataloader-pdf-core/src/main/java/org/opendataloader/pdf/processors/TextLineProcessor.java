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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
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
    /** A leader of a table of contents is less than this many font sizes high: a row of dots or dashes. */
    private static final double MAX_LEADER_HEIGHT = 0.3;
    /** Each end of the gap that a leader fills is narrower than this many font sizes. */
    private static final double MAX_LEADER_END_GAP = 3;
    /** The baselines of the parts of one row are at most this many font sizes apart. */
    private static final double MAX_ROW_BASELINE_DIFFERENCE = 0.25;
    /** The labels of the rows of a table of contents start or end at most this many font sizes apart. */
    private static final double MAX_LABEL_ALIGNMENT_DIFFERENCE = 0.25;
    /** A page reference of two words, as {@code p. 12}, {@code pp. 12-14}, or {@code B 7}. */
    private static final Pattern PAGE_REFERENCE = Pattern.compile("\\p{L}{1,4}\\.?\\s+\\d[\\p{L}\\d.\\-\u2013]*");
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
        newContents = joinRowsWithLeaders(newContents);
        linkTextLinesWithConnectedLineArtBullet(newContents);
        return newContents;
    }

    /**
     * Makes one line of each row of a table of contents whose parts are lines of their own (#389). A page can draw
     * the columns of a table of contents in turn: the page numbers of all the rows, then the titles, then the
     * numbers. Then the parts of a row are not one after the other in the contents, and each part is a line.
     *
     * <p>A leader, a line art that is thin and wide, shows a row: the line that ends just before the leader and the
     * page label that starts just after it, on one baseline, are one line. A number label just before that line, on
     * the same baseline and nearer than a column gap, is a part of the row too, if it is in a column of labels or the
     * row is the only one. Each row takes the place of its first part in the contents, with one space between its
     * parts.
     */
    private static List<IObject> joinRowsWithLeaders(List<IObject> contents) {
        List<LineArtChunk> leaders = new ArrayList<>();
        List<TextLine> lines = new ArrayList<>();
        NavigableMap<Double, List<TextLine>> linesByBaseline = new TreeMap<>();
        double maxFontSize = 0;
        for (IObject content : contents) {
            if (content instanceof LineArtChunk) {
                leaders.add((LineArtChunk) content);
            } else if (content instanceof TextLine) {
                TextLine line = (TextLine) content;
                lines.add(line);
                linesByBaseline.computeIfAbsent(line.getBaseLine(), key -> new ArrayList<>()).add(line);
                maxFontSize = Math.max(maxFontSize, line.getFontSize());
            }
        }
        if (leaders.isEmpty() || lines.size() < 2) {
            return contents;
        }
        Map<TextLine, TextLine> nextParts = new IdentityHashMap<>();
        Map<TextLine, TextLine> previousParts = new IdentityHashMap<>();
        for (LineArtChunk leader : leaders) {
            double middle = (leader.getBottomY() + leader.getTopY()) / 2;
            TextLine before = null;
            TextLine after = null;
            for (List<TextLine> sameBaseline : linesByBaseline.subMap(middle - maxFontSize, true,
                    middle + MAX_ROW_BASELINE_DIFFERENCE * maxFontSize, true).values()) {
                for (TextLine line : sameBaseline) {
                    if (!isLeaderOfLine(leader, line)) {
                        continue;
                    }
                    double maxGap = MAX_LEADER_END_GAP * line.getFontSize();
                    double gapBefore = leader.getLeftX() - line.getRightX();
                    double gapAfter = line.getLeftX() - leader.getRightX();
                    if (gapBefore >= 0 && gapBefore < maxGap
                            && (before == null || line.getRightX() > before.getRightX())) {
                        before = line;
                    } else if (gapAfter >= 0 && gapAfter < maxGap
                            && (after == null || line.getLeftX() < after.getLeftX())) {
                        after = line;
                    }
                }
            }
            if (before != null && after != null && !nextParts.containsKey(before) && !previousParts.containsKey(after)
                    && areOnOneRow(before, after) && isPageLabel(after.getValue().trim())) {
                nextParts.put(before, after);
                previousParts.put(after, before);
            }
        }
        if (nextParts.isEmpty()) {
            return contents;
        }
        List<TextLine> rowStarts = new ArrayList<>();
        for (TextLine line : lines) {
            if (nextParts.containsKey(line) && !previousParts.containsKey(line)) {
                rowStarts.add(line);
            }
        }
        Map<TextLine, TextLine> labels = new IdentityHashMap<>();
        for (TextLine first : rowStarts) {
            double maxDifference = MAX_ROW_BASELINE_DIFFERENCE * first.getFontSize();
            TextLine label = null;
            for (List<TextLine> sameBaseline : linesByBaseline.subMap(first.getBaseLine() - maxDifference, true,
                    first.getBaseLine() + maxDifference, true).values()) {
                for (TextLine line : sameBaseline) {
                    double gap = first.getLeftX() - line.getRightX();
                    if (gap >= 0 && gap < MIN_COLUMN_GAP * first.getFontSize() && !nextParts.containsKey(line)
                            && !previousParts.containsKey(line) && areOnOneRow(line, first) && isNumberLabel(line)
                            && (label == null || line.getRightX() > label.getRightX())) {
                        label = line;
                    }
                }
            }
            if (label != null && !labels.containsValue(label)) {
                labels.put(first, label);
            }
        }
        for (Map.Entry<TextLine, TextLine> entry : labels.entrySet()) {
            if (rowStarts.size() == 1 || isInAColumnOfLabels(entry.getValue(), labels.values())) {
                nextParts.put(entry.getValue(), entry.getKey());
                previousParts.put(entry.getKey(), entry.getValue());
            }
        }
        List<IObject> newContents = new ArrayList<>(contents.size());
        Set<TextLine> placedRows = Collections.newSetFromMap(new IdentityHashMap<>());
        for (IObject content : contents) {
            if (!nextParts.containsKey(content) && !previousParts.containsKey(content)) {
                newContents.add(content);
                continue;
            }
            TextLine first = (TextLine) content;
            while (previousParts.containsKey(first)) {
                first = previousParts.get(first);
            }
            if (placedRows.add(first)) {
                TextLine row = new TextLine();
                for (TextLine part = first; part != null; part = nextParts.get(part)) {
                    if (part != first) {
                        row.add(getSpaceChunk(row.getLastTextChunk().getTextEnd(), part.getFirstTextChunk()));
                    }
                    for (TextChunk chunk : part.getTextChunks()) {
                        row.add(chunk);
                    }
                }
                newContents.add(row);
            }
        }
        return newContents;
    }

    /**
     * Whether the line art can be a leader of the line: less than {@link #MAX_LEADER_HEIGHT} font sizes high, wider
     * than one font size, and with its middle between a quarter of a font size below the baseline of the line and
     * one font size above it.
     */
    private static boolean isLeaderOfLine(LineArtChunk lineArt, TextLine line) {
        double fontSize = line.getFontSize();
        BoundingBox box = lineArt.getBoundingBox();
        double middle = (box.getBottomY() + box.getTopY()) / 2;
        return box.getHeight() < MAX_LEADER_HEIGHT * fontSize && box.getWidth() > fontSize
            && middle > line.getBaseLine() - MAX_ROW_BASELINE_DIFFERENCE * fontSize
            && middle < line.getBaseLine() + fontSize;
    }

    /** Whether the two lines are parts of one row: their baselines are near. */
    private static boolean areOnOneRow(TextLine first, TextLine second) {
        double fontSize = Math.min(first.getFontSize(), second.getFontSize());
        return Math.abs(first.getBaseLine() - second.getBaseLine()) <= MAX_ROW_BASELINE_DIFFERENCE * fontSize;
    }

    /**
     * Whether the label is in a column of labels: another label starts or ends at most
     * {@link #MAX_LABEL_ALIGNMENT_DIFFERENCE} font sizes from where it starts or ends. A number of another column
     * next to a row is alone in its place.
     */
    private static boolean isInAColumnOfLabels(TextLine label, Collection<TextLine> labels) {
        double maxDifference = MAX_LABEL_ALIGNMENT_DIFFERENCE * label.getFontSize();
        for (TextLine other : labels) {
            if (other != label && (Math.abs(other.getLeftX() - label.getLeftX()) <= maxDifference
                    || Math.abs(other.getRightX() - label.getRightX()) <= maxDifference)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the line is one word of the form of a page label, as {@code 01}, {@code 2.1}, or {@code IV}. */
    private static boolean isNumberLabel(TextLine line) {
        String text = line.getValue().trim();
        return text.codePoints().noneMatch(Character::isWhitespace) && isPageLabel(text);
    }

    /**
     * Makes one paragraph of each column of a block of two columns (#878). A signature block has this layout: two
     * or three lines, one after the other in the contents and one below the other on the page, with more than
     * single spacing between them, each with one gap of at least {@link #MIN_COLUMN_GAP} font sizes, the gaps at
     * the same place, and text with a letter after each gap. Each line is split at its gap; the parts before the
     * gaps are one paragraph, and the parts after them are another, so that each column is read as one.
     *
     * <p>A table of contents has page numbers or page labels, as {@code iv}, {@code A}, {@code A-12}, or
     * {@code p. 12}, after its gaps, so its lines stay one line each. A line with a list label, as a bullet, also
     * stays one line. Single-spaced rows of a form stay one line each; two or three rows of a form with more
     * spacing have the layout of a signature block, so they are split too.
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
                    List<TextLine> parts = splitAt((TextLine) contents.get(indexes.get(k)), gaps.get(k)[1]);
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

    /**
     * Whether the text is a page label: a page reference of two words, as {@code p. 12}; or one word whose parts,
     * between hyphens and periods, are each one character, a part with a digit, or a roman numeral in one case,
     * as {@code A}, {@code A-12}, {@code iv.}, or {@code A-IV}.
     */
    private static boolean isPageLabel(String text) {
        if (PAGE_REFERENCE.matcher(text).matches()) {
            return true;
        }
        if (text.codePoints().anyMatch(Character::isWhitespace)) {
            return false;
        }
        boolean hasPart = false;
        for (String part : text.split("[-.\u2013]")) {
            if (part.isEmpty()) {
                continue;
            }
            if (!isPageLabelPart(part)) {
                return false;
            }
            hasPart = true;
        }
        return hasPart;
    }

    private static boolean isPageLabelPart(String part) {
        if (part.codePointCount(0, part.length()) == 1 || part.codePoints().anyMatch(Character::isDigit)) {
            return true;
        }
        return ROMAN_NUMERAL.matcher(part).matches() && (part.equals(part.toLowerCase(Locale.ROOT))
            || part.equals(part.toUpperCase(Locale.ROOT)));
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

    /**
     * The two lines of the chunks that start before the x, the end of the gap, and of the others, without the
     * white space at the gap.
     */
    private static List<TextLine> splitAt(TextLine line, double x) {
        List<TextChunk> leftChunks = new ArrayList<>();
        List<TextChunk> rightChunks = new ArrayList<>();
        for (TextChunk chunk : line.getTextChunks()) {
            (chunk.getLeftX() < x ? leftChunks : rightChunks).add(chunk);
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
                newLine.add(getSpaceChunk(previousEnd, currentTextChunk));
            }
            previousEnd = currentTextChunk.getTextEnd();
            newLine.add(currentTextChunk);
        }

        return newLine;
    }

    /** A space from the end of the previous text to the start of the chunk, in the font of the chunk. */
    private static TextChunk getSpaceChunk(double previousEnd, TextChunk chunk) {
        TextChunk spaceChunk = new TextChunk(new BoundingBox(chunk.getBoundingBox()), " ", chunk.getFontName(),
            chunk.getFontSize(), chunk.getFontWeight(), chunk.getItalicAngle(), chunk.getBaseLine(),
            chunk.getFontColor(), null, chunk.getSlantDegree());
        spaceChunk.setTextStart(previousEnd);
        spaceChunk.setTextEnd(chunk.getTextStart());
        spaceChunk.adjustSymbolEndsToBoundingBox(null);
        return spaceChunk;
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

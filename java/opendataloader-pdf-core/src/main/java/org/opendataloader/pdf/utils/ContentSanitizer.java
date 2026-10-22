package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

public class ContentSanitizer {
    /**
     * The operator index of the stream info for the extra characters of a replacement that is longer than the
     * replaced text. No glyph is under these characters.
     */
    public static final int EXTRA_CHARACTERS_OPERATOR_INDEX = -2;

    private static final Logger LOGGER = Logger.getLogger(ContentSanitizer.class.getName());
    private final List<SanitizationRule> rules;
    private final boolean contentSafetyEnabled;

    public ContentSanitizer(List<SanitizationRule> rules) {
        this.rules = rules;
        this.contentSafetyEnabled = true;
    }

    public ContentSanitizer(List<SanitizationRule> rules, boolean contentSafetyEnabled) {
        this.rules = rules;
        this.contentSafetyEnabled = contentSafetyEnabled;
    }

    public void sanitizeContents(List<List<IObject>> contents) {
        if (!contentSafetyEnabled) {
            return;
        }

        for (List<IObject> pageContents : contents) {
            for (IObject obj : pageContents) {
                processObject(obj);
            }
        }
    }

    private void processObject(IObject obj) {
        if (obj instanceof SemanticTextNode) {
            processSemanticTextNode((SemanticTextNode) obj);
        } else if (obj instanceof TextLine) {
            processTextLine((TextLine) obj);
        } else if (obj instanceof PDFList) {
            processPDFList((PDFList) obj);
        } else if (obj instanceof TableBorder) {
            processTableBorder((TableBorder) obj);
        } else if (obj instanceof SemanticHeaderOrFooter) {
            processSemanticHeaderOrFooter((SemanticHeaderOrFooter) obj);
        }
    }

    private void processSemanticHeaderOrFooter(SemanticHeaderOrFooter headerOrFooter) {
        for (IObject obj : headerOrFooter.getContents()) {
            processObject(obj);
        }
    }

    private void processPDFList(PDFList pdfList) {
        for (ListItem listItem : pdfList.getListItems()) {
            for (TextLine textLine : listItem.getLines()) {
                processTextLine(textLine);
            }
            for (IObject obj : listItem.getContents()) {
                processObject(obj);
            }
        }
    }

    private void processTableBorder(TableBorder tableBorder) {
        for (TableBorderRow row : tableBorder.getRows()) {
            TableBorderCell[] cells = row.getCells();
            for (int columnNumber = 0; columnNumber < cells.length; columnNumber++) {
                TableBorderCell cell = cells[columnNumber];
                if (cell.getColNumber() == columnNumber && cell.getRowNumber() == row.getRowNumber()) {
                    for (IObject obj : cell.getContents()) {
                        processObject(obj);
                    }
                }
            }
        }
    }

    private void processSemanticTextNode(SemanticTextNode node) {
        for (TextColumn textColumn : node.getColumns()) {
            for (TextBlock textBlock : textColumn.getBlocks()) {
                for (TextLine textLine : textBlock.getLines()) {
                    processTextLine(textLine);
                }
            }
        }
    }

    private void processTextLine(TextLine textLine) {
        if (textLine == null || textLine.getTextChunks() == null || textLine.getTextChunks().isEmpty()) {
            return;
        }
        String originalText = textLine.getValue();
        if (originalText.isEmpty()) {
            return;
        }

        List<ReplacementInfo> replacements = findAllReplacements(originalText);
        if (replacements.isEmpty()) {
            return;
        }
        List<TextChunk> textChunks = textLine.getTextChunks();
        List<TextChunk> newChunks = applyReplacementsToChunks(textChunks, replacements);

        textChunks.clear();
        textChunks.addAll(newChunks);
    }

    protected List<TextChunk> applyReplacementsToChunks(List<TextChunk> originalChunks,
                                                      List<ReplacementInfo> replacements) {
        List<TextChunk> newChunks = new ArrayList<>();
        List<ChunkInfo> chunkInfos = getChunkInfos(originalChunks);
        int currentChunkIndex = 0;
        int currentPosition = 0;
        replacements.sort(Comparator.comparingInt((ReplacementInfo a) -> a.originalStart)
            .thenComparing(Comparator.comparingInt((ReplacementInfo a) -> a.originalEnd).reversed()));
        removeOverlappingReplacements(replacements);
        for (ReplacementInfo replacement : replacements) {
            while (currentPosition < replacement.originalStart && currentChunkIndex < chunkInfos.size()) {
                ChunkInfo info = chunkInfos.get(currentChunkIndex);
                if (currentPosition >= info.start && currentPosition < info.end) {
                    int chunkStart = currentPosition - info.start;
                    int chunkEnd = Math.min(info.end, replacement.originalStart) - info.start;

                    if (chunkStart < chunkEnd) {
                        TextChunk chunk = originalChunks.get(currentChunkIndex);
                        TextChunk subChunk = TextChunk.getTextChunk(chunk, chunkStart, chunkEnd);
                        if (isNotEmptyChunk(subChunk)) {
                            newChunks.add(subChunk);
                        }
                    }
                    currentPosition = Math.min(info.end, replacement.originalStart);

                    if (currentPosition >= info.end) {
                        currentChunkIndex++;
                    }
                } else {
                    currentChunkIndex++;
                }
            }
            int endChunkIndex = findEndChunkIndex(currentChunkIndex, chunkInfos, replacement);
            String replacementText = replacement.replacementText;
            if (!replacementText.isEmpty()) {
                newChunks.add(createReplacementChunk(originalChunks, currentChunkIndex, replacementText, endChunkIndex, replacement, chunkInfos));
            }
            currentPosition = replacement.originalEnd;
            currentChunkIndex = endChunkIndex;
            if (currentChunkIndex < chunkInfos.size() && currentPosition == chunkInfos.get(endChunkIndex).end) {
                currentChunkIndex++;
            }

        }
        if (currentChunkIndex < chunkInfos.size()) {
            ChunkInfo info = chunkInfos.get(currentChunkIndex);

            if (currentPosition >= info.start && currentPosition < info.end) {
                int chunkStart = currentPosition - info.start;
                TextChunk chunk = originalChunks.get(currentChunkIndex);
                TextChunk subChunk = TextChunk.getTextChunk(chunk, chunkStart, info.length);
                if (isNotEmptyChunk(subChunk)) {
                    newChunks.add(subChunk);
                }
                currentChunkIndex++;
            }
            while (currentChunkIndex < originalChunks.size()) {
                info = chunkInfos.get(currentChunkIndex);
                if (currentPosition < info.start) {
                    TextChunk chunk = originalChunks.get(currentChunkIndex);
                    if (isNotEmptyChunk(chunk)) {
                        newChunks.add(chunk);
                    }
                }
                currentChunkIndex++;
            }
        }

        return newChunks;
    }

    private static boolean doReplacementsOverlap(ReplacementInfo a, ReplacementInfo b) {
        return Math.max(a.originalStart, b.originalStart) < Math.min(a.originalEnd, b.originalEnd);
    }

    private static void removeOverlappingReplacements(List<ReplacementInfo> replacements) {
        if (replacements.size() <= 1) {
            return;
        }

        int index = 1;
        ReplacementInfo lastReplacement = replacements.get(0);

        for (int i = 1; i < replacements.size(); i++) {
            ReplacementInfo cur = replacements.get(i);
            if (!doReplacementsOverlap(lastReplacement, cur)) {
                replacements.set(index++, cur);
                lastReplacement = cur;
            } else {
                LOGGER.log(Level.INFO,"Dropping overlapping replacement: " + cur.replacementText +
                    " (start = " + cur.originalStart + ", end = " + cur.originalEnd + ") overlaps with "
                    + lastReplacement.replacementText + " (start = " + lastReplacement.originalStart +
                    ", end = " + lastReplacement.originalEnd + ")");
            }
        }
        replacements.subList(index, replacements.size()).clear();
    }

    private TextChunk createReplacementChunk(List<TextChunk> originalChunks, int currentChunkIndex, String replacementText,
                                             int endChunkIndex, ReplacementInfo replacement, List<ChunkInfo> chunkInfos) {
        TextChunk sourceChunk = originalChunks.get(currentChunkIndex);
        TextChunk replacementChunk = new TextChunk(sourceChunk);
        replacementChunk.setValue(replacementText);
        updateBBoxForReplacement(replacementChunk, originalChunks, currentChunkIndex, endChunkIndex, replacement.originalStart, replacement.originalEnd, chunkInfos);
        updateStreamInfosForReplacement(replacementChunk, originalChunks, currentChunkIndex, endChunkIndex, replacement.originalStart, replacement.originalEnd, chunkInfos);
        return replacementChunk;
    }

    // A tagged PDF marks the glyphs of each chunk with its stream infos, so the replacement gets only those
    // of the replaced text. A split of a chunk (for example, at a link annotation) counts the stream info
    // lengths as characters of the value. Thus the extra characters of a longer replacement get a stream
    // info with EXTRA_CHARACTERS_OPERATOR_INDEX. veraPDF uses -1 for a space that it inserts, so the
    // tagged PDF can tell the two apart when it merges stream infos.
    private static void updateStreamInfosForReplacement(TextChunk replacementChunk, List<TextChunk> originalChunks,
                                                        int startChunkIndex, int endChunkIndex,
                                                        int replacementStart, int replacementEnd,
                                                        List<ChunkInfo> chunkInfos) {
        List<StreamInfo> streamInfos = replacementChunk.getStreamInfos();
        streamInfos.clear();
        int length = 0;
        for (int index = startChunkIndex; index <= endChunkIndex; index++) {
            ChunkInfo info = chunkInfos.get(index);
            int chunkStart = Math.max(replacementStart, info.start) - info.start;
            int chunkEnd = Math.min(replacementEnd, info.end) - info.start;
            // Do not use TextChunk.getTextChunk: --replace-invalid-chars can make a value longer than its
            // symbol ends and stream infos.
            int position = 0;
            for (StreamInfo streamInfo : originalChunks.get(index).getStreamInfos()) {
                int streamInfoLength = streamInfo.getEndIndex() - streamInfo.getStartIndex();
                int start = Math.max(chunkStart, position);
                int end = Math.min(chunkEnd, position + streamInfoLength);
                if (start < end) {
                    StreamInfo replacedPart = new StreamInfo(streamInfo);
                    replacedPart.setStartIndex(streamInfo.getStartIndex() + start - position);
                    replacedPart.setEndIndex(streamInfo.getStartIndex() + end - position);
                    streamInfos.add(replacedPart);
                    length += end - start;
                }
                position += streamInfoLength;
            }
        }
        int extraLength = replacementChunk.getValue().length() - length;
        if (extraLength > 0) {
            streamInfos.add(new StreamInfo(EXTRA_CHARACTERS_OPERATOR_INDEX, null, 0, extraLength));
        }
    }

    private int findEndChunkIndex(int currentChunkIndex, List<ChunkInfo> chunkInfos, ReplacementInfo replacement) {
        int endChunkIndex = -1;
        for (int i = currentChunkIndex; i < chunkInfos.size(); i++) {
            ChunkInfo info = chunkInfos.get(i);
            if (replacement.originalEnd > info.start &&
                replacement.originalEnd <= info.end) {
                endChunkIndex = i;
                break;
            }
        }
        if (endChunkIndex == -1) {
            endChunkIndex = chunkInfos.size() - 1;
        }
        return endChunkIndex;
    }

    private boolean isNotEmptyChunk(TextChunk chunk) {
        return chunk != null && chunk.getValue() != null && !chunk.getValue().isEmpty();
    }

    protected List<ReplacementInfo> findAllReplacements(String originalText) {
        List<ReplacementInfo> replacements = new ArrayList<>();

        for (SanitizationRule rule : rules) {
            Matcher matcher = rule.getPattern().matcher(originalText);
            try {
                while (matcher.find()) {
                    // Replace only a non-empty group. This also skips zero-length matches.
                    int group = rule.getMatchGroup();
                    if (matcher.end(group) > matcher.start(group)) {
                        replacements.add(new ReplacementInfo(matcher.start(group), matcher.end(group),
                            rule.getReplacement()));
                    }
                }
            } catch (StackOverflowError error) {
                // A user pattern with nested repetition can overflow the stack on a long line.
                throw new SanitizationRuleException("Sanitization rule '" + describe(rule)
                        + "' is too complex for a text line of " + originalText.length() + " characters");
            }
        }
        return replacements;
    }

    private static String describe(SanitizationRule rule) {
        return rule.getId() != null ? rule.getId() : rule.getPattern().pattern();
    }

    private void updateBBoxForReplacement(TextChunk replacementChunk,
                                          List<TextChunk> originalChunks,
                                          int startChunkIndex, int endChunkIndex,
                                          int replacementStart, int replacementEnd,
                                          List<ChunkInfo> chunkInfos) {
        TextChunk firstChunk = originalChunks.get(startChunkIndex);
        TextChunk lastChunk = originalChunks.get(endChunkIndex);
        ChunkInfo firstInfo = chunkInfos.get(startChunkIndex);
        ChunkInfo lastInfo = chunkInfos.get(endChunkIndex);
        int startInFirstChunk = replacementStart - firstInfo.start;
        int endInLastChunk = replacementEnd - lastInfo.start;
        double left = firstChunk.getSymbolStartCoordinate(startInFirstChunk);
        double right = lastChunk.getSymbolStartCoordinate(endInLastChunk);

        BoundingBox bBox = replacementChunk.getBoundingBox();
        bBox.setLeftX(left);
        bBox.setRightX(right);
        replacementChunk.adjustSymbolEndsToBoundingBox(null);
    }

    protected static class ReplacementInfo {
        int originalStart;
        int originalEnd;
        String replacementText;

        ReplacementInfo(int originalStart, int originalEnd, String replacementText) {
            this.originalStart = originalStart;
            this.originalEnd = originalEnd;
            this.replacementText = replacementText;
        }
    }

    private static class ChunkInfo {
        int start;
        int end;
        int length;

        ChunkInfo(int start, int length) {
            this.start = start;
            this.length = length;
            this.end = start + length;
        }
    }

    private List<ChunkInfo> getChunkInfos(List<TextChunk> textChunks) {
        List<ChunkInfo> infos = new ArrayList<>();
        int currentPosition = 0;

        for (TextChunk chunk : textChunks) {
            String chunkText = chunk.getValue() != null ? chunk.getValue() : "";
            int chunkLength = chunkText.length();
            infos.add(new ChunkInfo(currentPosition, chunkLength));
            currentPosition += chunkLength;
        }

        return infos;
    }
}

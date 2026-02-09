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

import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;

public class ContentSanitizer {

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
        removeOverlappingReplacement(replacements);
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

    private static void removeOverlappingReplacement(List<ReplacementInfo> replacements) {
        if (replacements == null || replacements.size() <= 1) {
            return;
        }

        int write = 1;
        ReplacementInfo lastKept = replacements.get(0);

        for (int i = 1; i < replacements.size(); i++) {
            ReplacementInfo cur = replacements.get(i);
            if (!doReplacementsOverlap(lastKept, cur)) {
                replacements.set(write++, cur);
                lastKept = cur;
            }
        }
        replacements.subList(write, replacements.size()).clear();
    }

    private TextChunk createReplacementChunk(List<TextChunk> originalChunks, int currentChunkIndex, String replacementText,
                                             int endChunkIndex, ReplacementInfo replacement, List<ChunkInfo> chunkInfos) {
        TextChunk sourceChunk = originalChunks.get(currentChunkIndex);
        TextChunk replacementChunk = new TextChunk(sourceChunk);
        replacementChunk.setValue(replacementText);
        updateBBoxForReplacement(replacementChunk, originalChunks, currentChunkIndex, endChunkIndex, replacement.originalStart, replacement.originalEnd, chunkInfos);
        return replacementChunk;
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
            while (matcher.find()) {
                replacements.add(new ReplacementInfo(matcher.start(), matcher.end(), rule.getReplacement()));
            }
        }
        return replacements;
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

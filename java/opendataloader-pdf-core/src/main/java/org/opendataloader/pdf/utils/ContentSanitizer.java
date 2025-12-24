package org.opendataloader.pdf.utils;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;

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

        for (List<IObject> row : contents) {
            for (IObject obj : row) {
                processObject(obj);
            }
        }
    }

    private String applySanitization(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String result = text;
        for (SanitizationRule rule : rules) {
            result = rule.getPattern().matcher(result).replaceAll(rule.getReplacement());
        }
        return result;
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
            for (TableBorderCell cell : row.getCells()) {
                for (IObject obj : cell.getContents()) {
                    processObject(obj);
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

        String sanitizedText = applySanitization(originalText);
        if (originalText.equals(sanitizedText)) {
            return;
        }

        List<ReplacementInfo> replacements = findAllReplacements(originalText);
        if (replacements.isEmpty()) {
            return;
        }
        List<TextChunk> textChunks = textLine.getTextChunks();
        List<TextChunk> newChunks = applyReplacementsToChunks(textChunks, replacements);

        removeEmptyChunks(newChunks);

        textChunks.clear();
        textChunks.addAll(newChunks);
    }

    private List<TextChunk> applyReplacementsToChunks(List<TextChunk> originalChunks,
                                                      List<ReplacementInfo> replacements) {
        List<TextChunk> newChunks = new ArrayList<>();
        List<ChunkInfo> chunkInfos = getChunkInfos(originalChunks);
        int currentChunkIndex = 0;
        int currentPosition = 0;
        replacements.sort((a, b) -> Integer.compare(a.originalStart, b.originalStart));

        for (ReplacementInfo replacement : replacements) {
            while (currentPosition < replacement.originalStart && currentChunkIndex < chunkInfos.size()) {
                ChunkInfo info = chunkInfos.get(currentChunkIndex);
                if (currentPosition >= info.start && currentPosition < info.end) {
                    int chunkStart = currentPosition - info.start;
                    int chunkEnd = Math.min(info.end, replacement.originalStart) - info.start;

                    if (chunkStart < chunkEnd) {
                        TextChunk chunk = originalChunks.get(currentChunkIndex);
                        TextChunk subChunk = TextChunk.getTextChunk(chunk, chunkStart, chunkEnd);
                        if (subChunk != null && !isEmptyChunk(subChunk)) {
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
            String replacementText = replacement.replacementText;
            if (!replacementText.isEmpty()) {
                TextChunk chunk = originalChunks.get(0);
                TextChunk replacementChunk = new TextChunk(chunk);
                replacementChunk.setValue(replacementText);
                newChunks.add(replacementChunk);
            }
            currentPosition = replacement.originalEnd;
            while (currentChunkIndex < chunkInfos.size() &&
                chunkInfos.get(currentChunkIndex).end <= currentPosition) {
                currentChunkIndex++;
            }
        }
        while (currentChunkIndex < originalChunks.size()) {
            ChunkInfo info = chunkInfos.get(currentChunkIndex);

            if (currentPosition >= info.start && currentPosition < info.end) {
                int chunkStart = currentPosition - info.start;
                TextChunk chunk = originalChunks.get(currentChunkIndex);
                TextChunk subChunk = TextChunk.getTextChunk(chunk, chunkStart, info.length);
                if (subChunk != null && !isEmptyChunk(subChunk)) {
                    newChunks.add(subChunk);
                }
            } else if (currentPosition < info.start) {
                TextChunk chunk = originalChunks.get(currentChunkIndex);
                if (!isEmptyChunk(chunk)) {
                    newChunks.add(chunk);
                }
            }
            currentChunkIndex++;
        }
        return newChunks;
    }

    private void removeEmptyChunks(List<TextChunk> chunks) {
        if (chunks == null) {
            return;
        }

        chunks.removeIf(this::isEmptyChunk);
    }

    private boolean isEmptyChunk(TextChunk chunk) {
        return chunk == null || chunk.getValue() == null || chunk.getValue().isEmpty();
    }

    private List<ReplacementInfo> findAllReplacements(String originalText) {
        List<ReplacementInfo> replacements = new ArrayList<>();

        for (SanitizationRule rule : rules) {
            Matcher matcher = rule.getPattern().matcher(originalText);
            while (matcher.find()) {
                replacements.add(new ReplacementInfo(matcher.start(), matcher.end(), rule.getReplacement()));
            }
        }
        return replacements;
    }

    private static class ReplacementInfo {
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

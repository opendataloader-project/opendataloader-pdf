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
import java.util.regex.Pattern;
import java.util.ArrayList;

public class ContentSanitizer {

    private static class SanitizationRule {
        private final Pattern pattern;
        private final String replacement;

        public SanitizationRule(Pattern pattern, String replacement) {
            this.pattern = pattern;
            this.replacement = replacement;
        }

        public Pattern getPattern() {
            return pattern;
        }

        public String getReplacement() {
            return replacement;
        }
    }

    private static final List<SanitizationRule> DEFAULT_RULES;

    static {
        DEFAULT_RULES = new ArrayList<>();
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"),
            "email@example.com"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("^+\\d+(?:-\\d+)+$"),
            "+00-0000-0000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("[A-Z]{1,2}\\d{6,9}"),
            "AA0000000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}\\b"),
            "0000-0000-0000-0000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{10,18}\\b"),
            "0000000000000000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
            "0.0.0.0"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b([0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\\b"),
            "0.0.0.0::1"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b(?:[0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}\\b"),
            "00:00:00:00:00:00"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("\\b\\d{15}\\b"),
            "000000000000000"
        ));
        DEFAULT_RULES.add(new SanitizationRule(
            Pattern.compile("https?://[A-Za-z0-9.-]+(:\\d+)?(/\\S*)?"),
            "https://example.com"
        ));
    }

    private final List<SanitizationRule> rules;
    private final boolean contentSafetyEnabled;

    public ContentSanitizer() {
        this.rules = DEFAULT_RULES;
        this.contentSafetyEnabled = true;
    }

    public ContentSanitizer(boolean contentSafetyEnabled) {
        this.rules = DEFAULT_RULES;
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
        if (obj == null) {
            return;
        }

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
        if (headerOrFooter == null || headerOrFooter.getContents() == null) {
            return;
        }

        for (IObject obj : headerOrFooter.getContents()) {
            processObject(obj);
        }
    }

    private void processPDFList(PDFList pdfList) {
        if (pdfList == null || pdfList.getListItems() == null) {
            return;
        }

        for (ListItem listItem : pdfList.getListItems()) {
            processListItem(listItem);
        }
    }

    private void processListItem(ListItem listItem) {
        if (listItem == null || listItem.getContents() == null) {
            return;
        }

        for (IObject obj : listItem.getLines()) {
            processObject(obj);
        }
    }

    private void processTableBorder(TableBorder tableBorder) {
        if (tableBorder == null || tableBorder.getRows() == null) {
            return;
        }

        for (TableBorderRow row : tableBorder.getRows()) {
            processTableBorderRow(row);
        }
    }

    private void processTableBorderRow(TableBorderRow row) {
        if (row == null || row.getCells() == null) {
            return;
        }
        for (TableBorderCell cell : row.getCells()) {
            processTableBorderCell(cell);
        }
    }

    private void processTableBorderCell(TableBorderCell cell) {
        if (cell == null || cell.getContents() == null) {
            return;
        }

        for (IObject obj : cell.getContents()) {
            processObject(obj);
        }
    }

    private void processSemanticTextNode(SemanticTextNode node) {
        String fullText = node.getValue();
        if (fullText == null || fullText.isEmpty()) {
            return;
        }
        String sanitizedText = applySanitization(fullText);
        if (fullText.equals(sanitizedText)) {
            return;
        }
        List<ReplacementInfo> replacements = findReplacements(fullText, sanitizedText);
        if (replacements.isEmpty()) {
            return;
        }
        List<TextLine> allTextLines = collectAllTextLines(node);
        for (TextLine textLine : allTextLines) {
            applyReplacementsToTextLine(textLine, replacements, fullText, sanitizedText);
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

        List<ReplacementInfo> replacements = findReplacements(originalText, sanitizedText);
        if (replacements.isEmpty()) {
            return;
        }
        List<TextChunk> textChunks = textLine.getTextChunks();
        List<TextChunk> newChunks = applyReplacementsToChunks(
            textChunks, replacements, sanitizedText);

        removeEmptyChunks(newChunks);

        textChunks.clear();
        textChunks.addAll(newChunks);
    }

    private List<TextLine> collectAllTextLines(SemanticTextNode node) {
        List<TextLine> result = new ArrayList<>();
        collectTextLinesFromSemanticTextNode(node, result);
        return result;
    }

    private void collectTextLinesFromSemanticTextNode(SemanticTextNode node, List<TextLine> result) {
        if (node == null || node.getColumns() == null) {
            return;
        }

        for (TextColumn column : node.getColumns()) {
            collectTextLinesFromTextColumn(column, result);
        }
    }

    private void collectTextLinesFromTextColumn(TextColumn column, List<TextLine> result) {
        if (column == null || column.getBlocks() == null) {
            return;
        }

        for (TextBlock block : column.getBlocks()) {
            collectTextLinesFromTextBlock(block, result);
        }
    }

    private void collectTextLinesFromTextBlock(TextBlock block, List<TextLine> result) {
        if (block == null || block.getLines() == null) {
            return;
        }

        result.addAll(block.getLines());
    }

    private void applyReplacementsToTextLine(TextLine textLine, List<ReplacementInfo> replacements,
                                             String originalFullText, String sanitizedFullText) {
        if (textLine == null || textLine.getTextChunks() == null || replacements.isEmpty()) {
            return;
        }

        String lineText = textLine.getValue();
        if (lineText.isEmpty()) {
            return;
        }

        int lineStart = originalFullText.indexOf(lineText);
        if (lineStart == -1) {
            return;
        }
        int lineEnd = lineStart + lineText.length();
        List<ReplacementInfo> lineReplacements = new ArrayList<>();
        for (ReplacementInfo replacement : replacements) {
            if (replacement.originalEnd > lineStart && replacement.originalStart < lineEnd) {
                lineReplacements.add(replacement);
            }
        }

        if (lineReplacements.isEmpty()) {
            return;
        }
        List<TextChunk> newChunks = applyReplacementsToChunks(
            textLine.getTextChunks(), lineReplacements, sanitizedFullText);
        removeEmptyChunks(newChunks);
        textLine.getTextChunks().clear();
        textLine.getTextChunks().addAll(newChunks);
    }

    private List<TextChunk> applyReplacementsToChunks(List<TextChunk> originalChunks,
                                                      List<ReplacementInfo> replacements,
                                                      String sanitizedText) {
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
            String replacementText = sanitizedText.substring(replacement.sanitizedStart, replacement.sanitizedEnd);
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

    private static class ReplacementInfo {
        int originalStart;
        int originalEnd;
        int sanitizedStart;
        int sanitizedEnd;

        ReplacementInfo(int originalStart, int originalEnd, int sanitizedStart, int sanitizedEnd) {
            this.originalStart = originalStart;
            this.originalEnd = originalEnd;
            this.sanitizedStart = sanitizedStart;
            this.sanitizedEnd = sanitizedEnd;
        }
    }

    private List<ReplacementInfo> findReplacements(String originalText, String sanitizedText) {
        List<ReplacementInfo> replacements = new ArrayList<>();

        if (originalText.equals(sanitizedText)) {
            return replacements;
        }
        int prefixLen = 0;
        int minLen = Math.min(originalText.length(), sanitizedText.length());
        while (prefixLen < minLen && originalText.charAt(prefixLen) == sanitizedText.charAt(prefixLen)) {
            prefixLen++;
        }
        int suffixLen = 0;
        while (suffixLen < minLen - prefixLen &&
            originalText.charAt(originalText.length() - 1 - suffixLen) ==
                sanitizedText.charAt(sanitizedText.length() - 1 - suffixLen)) {
            suffixLen++;
        }
        int origStart = prefixLen;
        int origEnd = originalText.length() - suffixLen;
        int sanitStart = prefixLen;
        int sanitEnd = sanitizedText.length() - suffixLen;
        replacements.add(new ReplacementInfo(origStart, origEnd, sanitStart, sanitEnd));

        return replacements;
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

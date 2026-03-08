/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Fallback extractor for PDFs where the primary artifact pipeline yields no page content
 * even though PDFBox can still extract visible text.
 */
public final class PdfBoxTextFallbackProcessor {

    private static final Logger LOGGER = Logger.getLogger(PdfBoxTextFallbackProcessor.class.getCanonicalName());
    private static final double PAGE_MARGIN = 36.0;
    private static final double MIN_PARAGRAPH_HEIGHT = 12.0;
    private static final Set<String> HEADING_CONNECTORS = new HashSet<>(Arrays.asList(
        "a", "an", "and", "as", "at", "by", "for", "from", "in", "of", "on", "or", "the", "to", "with"
    ));
    private static final Set<String> FRONT_MATTER_KEYWORDS = new HashSet<>(Arrays.asList(
        "about", "acknowledgments", "acknowledgements", "appendix", "book", "chapter", "conclusion", "contents",
        "copyright", "epilogue", "foreword", "glossary", "index", "introduction", "notes", "part", "preface",
        "prologue", "section", "table", "translator's"
    ));

    private PdfBoxTextFallbackProcessor() {
    }

    static void backfillIfDocumentEmpty(String inputPdfName, List<List<IObject>> contents, Set<Integer> pagesToProcess)
        throws IOException {
        if (!isDocumentEmpty(contents, pagesToProcess)) {
            return;
        }

        Set<Integer> targetPages = getPagesToBackfill(contents, pagesToProcess);
        if (targetPages.isEmpty()) {
            return;
        }

        try (PDDocument document = Loader.loadPDF(new File(inputPdfName))) {
            int recoveredPages = 0;
            for (Integer pageNumber : targetPages) {
                String pageText = extractPageText(document, pageNumber);
                List<String> paragraphs = splitPageTextIntoParagraphs(pageText);
                if (paragraphs.isEmpty()) {
                    continue;
                }
                contents.set(pageNumber, buildRecoveredPageContents(document, pageNumber, paragraphs));
                recoveredPages++;
            }

            if (recoveredPages > 0) {
                LOGGER.log(Level.INFO,
                    "Recovered {0} empty page(s) using PDFBox text fallback for {1}",
                    new Object[]{recoveredPages, inputPdfName});
            }
        }
    }

    static boolean isDocumentEmpty(List<List<IObject>> contents, Set<Integer> pagesToProcess) {
        if (contents == null || contents.isEmpty()) {
            return true;
        }
        boolean sawPage = false;
        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                continue;
            }
            sawPage = true;
            List<IObject> pageContents = contents.get(pageNumber);
            if (pageContents != null && !pageContents.isEmpty()) {
                return false;
            }
        }
        return sawPage;
    }

    static List<String> splitPageTextIntoParagraphs(String pageText) {
        String normalized = String.valueOf(pageText)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\f', '\n')
            .trim();

        List<String> paragraphs = new ArrayList<>();
        if (normalized.isEmpty()) {
            return paragraphs;
        }

        String[] blocks = normalized.split("\\n\\s*\\n+");
        for (String block : blocks) {
            List<String> lines = new ArrayList<>();
            for (String line : block.split("\\n")) {
                String cleaned = line.replaceAll("\\s+", " ").trim();
                if (!cleaned.isEmpty()) {
                    lines.add(cleaned);
                }
            }
            if (lines.isEmpty()) {
                continue;
            }
            if (shouldKeepLinesSeparate(lines)) {
                paragraphs.addAll(lines);
            } else {
                paragraphs.add(String.join(" ", lines).replaceAll("\\s+", " ").trim());
            }
        }
        return paragraphs;
    }

    private static Set<Integer> getPagesToBackfill(List<List<IObject>> contents, Set<Integer> pagesToProcess) {
        Set<Integer> pages = new LinkedHashSet<>();
        if (contents == null) {
            return pages;
        }
        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            if (!shouldProcessPage(pageNumber, pagesToProcess)) {
                continue;
            }
            pages.add(pageNumber);
        }
        return pages;
    }

    private static boolean shouldProcessPage(int pageNumber, Set<Integer> pagesToProcess) {
        return pagesToProcess == null || pagesToProcess.contains(pageNumber);
    }

    private static boolean shouldKeepLinesSeparate(List<String> lines) {
        if (lines.size() <= 1) {
            return true;
        }
        int structuredLines = 0;
        for (String line : lines) {
            if (looksStructured(line)) {
                structuredLines++;
            }
        }
        return structuredLines >= Math.max(2, (int) Math.ceil(lines.size() * 0.6));
    }

    private static boolean looksStructured(String line) {
        return line.contains("...")
            || line.matches(".*\\s\\d+$")
            || line.matches("^[A-Z0-9 ,;:'\"()\\-]+$")
            || line.matches("^(chapter|part|section|contents|table of contents|foreword|preface|introduction|epilogue)\\b.*");
    }

    private static String extractPageText(PDDocument document, int pageNumber) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageNumber + 1);
        stripper.setEndPage(pageNumber + 1);
        return stripper.getText(document);
    }

    private static List<IObject> buildRecoveredPageContents(PDDocument document, int pageNumber, List<String> paragraphs) {
        List<RecoveredBlock> blocks = new ArrayList<>();
        for (String paragraph : paragraphs) {
            blocks.addAll(recoverBlocks(paragraph));
        }

        List<IObject> pageContents = new ArrayList<>();
        BoundingBox pageBox = getPageBoundingBox(document, pageNumber);
        double left = pageBox.getLeftX() + PAGE_MARGIN;
        double right = Math.max(left + PAGE_MARGIN, pageBox.getRightX() - PAGE_MARGIN);
        double topLimit = pageBox.getTopY() - PAGE_MARGIN;
        double bottomLimit = pageBox.getBottomY() + PAGE_MARGIN;
        double usableHeight = Math.max(MIN_PARAGRAPH_HEIGHT, topLimit - bottomLimit);
        double slotHeight = Math.max(MIN_PARAGRAPH_HEIGHT, usableHeight / Math.max(1, blocks.size()));

        for (int index = 0; index < blocks.size(); index++) {
            RecoveredBlock block = blocks.get(index);
            double top = topLimit - (index * slotHeight);
            double bottom = Math.max(bottomLimit, top - Math.max(MIN_PARAGRAPH_HEIGHT, slotHeight * 0.8));
            if (bottom >= top) {
                bottom = Math.max(pageBox.getBottomY(), top - MIN_PARAGRAPH_HEIGHT);
            }
            BoundingBox bbox = new BoundingBox(pageNumber, left, bottom, right, top);
            pageContents.add(block.isHeading
                ? createHeading(block.text, bbox, block.headingLevel)
                : createParagraph(block.text, bbox));
        }
        return pageContents;
    }

    private static List<RecoveredBlock> recoverBlocks(String paragraph) {
        List<RecoveredBlock> blocks = new ArrayList<>();
        String normalized = normalizeRecoveredText(paragraph);
        if (normalized.isEmpty()) {
            return blocks;
        }
        if (looksHeadingLine(normalized)) {
            blocks.add(RecoveredBlock.heading(normalized, inferHeadingLevel(normalized)));
            return blocks;
        }

        LeadingHeadingSplit split = splitLeadingHeading(normalized);
        if (split != null) {
            blocks.add(RecoveredBlock.heading(split.heading, inferHeadingLevel(split.heading)));
            if (!split.remainder.isEmpty()) {
                blocks.add(RecoveredBlock.paragraph(split.remainder));
            }
            return blocks;
        }

        blocks.add(RecoveredBlock.paragraph(normalized));
        return blocks;
    }

    static List<String> recoverBlockTextsForTesting(String paragraph) {
        List<String> values = new ArrayList<>();
        for (RecoveredBlock block : recoverBlocks(paragraph)) {
            values.add((block.isHeading ? "H:" : "P:") + block.text);
        }
        return values;
    }

    private static String normalizeRecoveredText(String text) {
        return String.valueOf(text == null ? "" : text)
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static boolean looksHeadingLine(String text) {
        String normalized = normalizeRecoveredText(text);
        if (normalized.isEmpty() || normalized.length() > 140 || looksTocEntry(normalized)) {
            return false;
        }
        if (normalized.contains(". ") || normalized.contains("? ") || normalized.contains("! ")) {
            return false;
        }
        if (normalized.endsWith(".") || normalized.endsWith("?") || normalized.endsWith("!")) {
            return false;
        }
        if (isHeadingKeywordLead(normalized)) {
            return true;
        }
        return uppercaseWordRatio(normalized) >= 0.7;
    }

    private static boolean looksTocEntry(String text) {
        String normalized = normalizeRecoveredText(text);
        return normalized.contains("...") || (normalized.length() <= 140 && normalized.matches(".*\\s\\d+$"));
    }

    private static boolean isHeadingKeywordLead(String text) {
        String lower = text.toLowerCase();
        if (lower.startsWith("table of contents") || lower.startsWith("contents")) {
            return true;
        }
        if (lower.startsWith("chapter ") || lower.startsWith("part ") || lower.startsWith("appendix ")) {
            return true;
        }
        String firstWord = firstAlphaWord(lower);
        return !firstWord.isEmpty() && FRONT_MATTER_KEYWORDS.contains(firstWord);
    }

    private static String firstAlphaWord(String text) {
        for (String token : text.split("\\s+")) {
            String cleaned = cleanToken(token).toLowerCase();
            if (!cleaned.isEmpty()) {
                return cleaned;
            }
        }
        return "";
    }

    private static double uppercaseWordRatio(String text) {
        List<String> words = Arrays.stream(text.split("\\s+"))
            .map(PdfBoxTextFallbackProcessor::cleanToken)
            .filter(token -> !token.isEmpty())
            .collect(Collectors.toList());
        if (words.isEmpty()) {
            return 0;
        }
        long uppercaseWords = words.stream().filter(PdfBoxTextFallbackProcessor::isUppercaseWord).count();
        return (double) uppercaseWords / words.size();
    }

    private static LeadingHeadingSplit splitLeadingHeading(String text) {
        if (looksTocEntry(text)) {
            return null;
        }

        String[] tokens = text.split("\\s+");
        if (tokens.length < 2) {
            return null;
        }

        List<String> headingTokens = new ArrayList<>();
        boolean started = false;
        boolean allowSingleTitleCase = false;
        boolean consumedTrailingTitleCase = false;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            String cleaned = cleanToken(token);
            if (cleaned.matches("^\\(?\\d+[A-Za-z]?\\)?$")) {
                break;
            }

            boolean headingToken = false;
            if (!started) {
                headingToken = isHeadingStarter(cleaned);
            } else if (isUppercaseWord(cleaned) || isRomanNumeral(cleaned)) {
                headingToken = true;
            } else if (!consumedTrailingTitleCase && HEADING_CONNECTORS.contains(cleaned.toLowerCase())) {
                headingToken = true;
                allowSingleTitleCase = cleaned.equalsIgnoreCase("by");
            } else if (allowSingleTitleCase && isTitleCaseWord(cleaned)) {
                headingToken = true;
                allowSingleTitleCase = false;
                consumedTrailingTitleCase = true;
            }

            if (!headingToken) {
                break;
            }

            started = true;
            headingTokens.add(token);
        }

        if (headingTokens.isEmpty()) {
            return null;
        }

        String heading = normalizeRecoveredText(String.join(" ", headingTokens));
        if (!looksHeadingLine(heading) || headingTokens.size() >= tokens.length) {
            return null;
        }

        String remainder = normalizeRecoveredText(String.join(" ", Arrays.copyOfRange(tokens, headingTokens.size(), tokens.length)));
        if (remainder.isEmpty()) {
            return null;
        }
        return new LeadingHeadingSplit(heading, remainder);
    }

    private static boolean isHeadingStarter(String cleaned) {
        if (cleaned.isEmpty()) {
            return false;
        }
        if (isUppercaseWord(cleaned) || isRomanNumeral(cleaned)) {
            return true;
        }
        if (isTitleCaseWord(cleaned)) {
            return FRONT_MATTER_KEYWORDS.contains(cleaned.toLowerCase());
        }
        return false;
    }

    private static String cleanToken(String token) {
        return String.valueOf(token == null ? "" : token)
            .replaceAll("^[^A-Za-z0-9']+", "")
            .replaceAll("[^A-Za-z0-9']+$", "");
    }

    private static boolean isUppercaseWord(String token) {
        String cleaned = cleanToken(token);
        if (cleaned.isEmpty()) {
            return false;
        }
        boolean hasLetter = false;
        for (int i = 0; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isLetter(c)) {
                hasLetter = true;
                if (!Character.isUpperCase(c)) {
                    return false;
                }
            }
        }
        return hasLetter;
    }

    private static boolean isRomanNumeral(String token) {
        String cleaned = cleanToken(token);
        return !cleaned.isEmpty() && cleaned.matches("(?i)^[ivxlcdm]+$");
    }

    private static boolean isTitleCaseWord(String token) {
        String cleaned = cleanToken(token);
        if (cleaned.length() < 2 || !Character.isUpperCase(cleaned.charAt(0))) {
            return false;
        }
        boolean sawLower = false;
        for (int i = 1; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (Character.isLetter(c)) {
                if (!Character.isLowerCase(c)) {
                    return false;
                }
                sawLower = true;
            }
        }
        return sawLower;
    }

    private static int inferHeadingLevel(String text) {
        String lower = normalizeRecoveredText(text).toLowerCase();
        if (lower.startsWith("table of contents") || lower.startsWith("contents")) {
            return 1;
        }
        if (lower.startsWith("chapter ") || lower.startsWith("part ") || lower.startsWith("appendix ")) {
            return 2;
        }
        return uppercaseWordRatio(text) >= 0.85 ? 2 : 3;
    }

    private static BoundingBox getPageBoundingBox(PDDocument document, int pageNumber) {
        PDRectangle cropBox = document.getPage(pageNumber).getCropBox();
        if (cropBox == null) {
            return new BoundingBox(pageNumber, 0, 0, 612, 792);
        }
        return new BoundingBox(pageNumber,
            cropBox.getLowerLeftX(),
            cropBox.getLowerLeftY(),
            cropBox.getUpperRightX(),
            cropBox.getUpperRightY());
    }

    private static SemanticParagraph createParagraph(String text, BoundingBox bbox) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(textLine);
        paragraph.setBoundingBox(bbox);
        paragraph.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        paragraph.setCorrectSemanticScore(1.0);
        return paragraph;
    }

    private static org.verapdf.wcag.algorithms.entities.SemanticHeading createHeading(String text, BoundingBox bbox,
                                                                                     int headingLevel) {
        TextChunk textChunk = new TextChunk(bbox, text, 12.0, 12.0);
        textChunk.adjustSymbolEndsToBoundingBox(null);
        TextLine textLine = new TextLine(textChunk);

        org.verapdf.wcag.algorithms.entities.SemanticHeading heading = new org.verapdf.wcag.algorithms.entities.SemanticHeading();
        heading.add(textLine);
        heading.setBoundingBox(bbox);
        heading.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        heading.setHeadingLevel(Math.max(1, headingLevel));
        heading.setCorrectSemanticScore(1.0);
        return heading;
    }

    private static final class LeadingHeadingSplit {
        private final String heading;
        private final String remainder;

        private LeadingHeadingSplit(String heading, String remainder) {
            this.heading = heading;
            this.remainder = remainder;
        }
    }

    private static final class RecoveredBlock {
        private final String text;
        private final boolean isHeading;
        private final int headingLevel;

        private RecoveredBlock(String text, boolean isHeading, int headingLevel) {
            this.text = text;
            this.isHeading = isHeading;
            this.headingLevel = headingLevel;
        }

        private static RecoveredBlock paragraph(String text) {
            return new RecoveredBlock(text, false, 0);
        }

        private static RecoveredBlock heading(String text, int headingLevel) {
            return new RecoveredBlock(text, true, headingLevel);
        }
    }
}

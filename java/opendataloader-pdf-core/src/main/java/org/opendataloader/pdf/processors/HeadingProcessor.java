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

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.opendataloader.pdf.utils.TextNodeStatistics;
import org.opendataloader.pdf.utils.TextNodeUtils;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextBlock;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.text.TextStyle;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Processor for detecting and classifying headings in PDF content.
 * Uses font size, weight, and position to identify potential headings.
 */
public class HeadingProcessor {
    private static final double HEADING_PROBABILITY = 0.75;
    private static final double BULLETED_HEADING_PROBABILITY = 0.1;

    /**
     * Processes content to identify and mark headings.
     *
     * @param contents the list of content objects to process
     * @param isTableCell whether the content is inside a table cell
     */
    public static void processHeadings(List<IObject> contents, boolean isTableCell) {
        TextNodeStatistics textNodeStatistics = new TextNodeStatistics();
        List<SemanticTextNode> textNodes = new LinkedList<>();
        Map<SemanticTextNode, PDFList> textNodeToListMap = new HashMap<>();
        for (IObject content : contents) {
            processContent(textNodes, content, textNodeStatistics, textNodeToListMap);
        }

        int textNodesCount = textNodes.size();
        if (isTableCell && textNodesCount < 2) {
            return;
        }
        for (int index = 0; index < textNodesCount; index++) {
            SemanticTextNode textNode = textNodes.get(index);
            SemanticTextNode prevNode = index != 0 ? textNodes.get(index - 1) : null;
            SemanticTextNode nextNode = index + 1 < textNodesCount ? textNodes.get(index + 1) : null;

            if (isDropCap(textNode, nextNode) || isEquation(textNode) || isTableContent(textNode)) {
                if (textNode.getSemanticType() == SemanticType.HEADING) {
                    textNode.setSemanticType(SemanticType.PARAGRAPH);
                }
                continue;
            }
            
            boolean isIeeeSection = BulletedParagraphUtils.isIeeeSectionTitle(textNode.getFirstLine());
            boolean isConsecutiveList = isConsecutiveSameStyleList(prevNode, textNode) || isConsecutiveSameStyleList(textNode, nextNode);

            if (isIeeeSection && !isConsecutiveList) {
                textNode.setSemanticType(SemanticType.HEADING);
            } else if (textNode.getSemanticType() != SemanticType.HEADING) {
                double probability = NodeUtils.headingProbability(textNode, prevNode, nextNode, textNode);
                probability += textNodeStatistics.fontSizeRarityBoost(textNode);
                probability += textNodeStatistics.fontWeightRarityBoost(textNode);
                if (BulletedParagraphUtils.isBulletedParagraph(textNode)) {
                    probability += BULLETED_HEADING_PROBABILITY;
                }
                if (probability > HEADING_PROBABILITY && textNode.getSemanticType() != SemanticType.LIST && !isConsecutiveList) {
                    textNode.setSemanticType(SemanticType.HEADING);
                }
            }

            if (textNode.getSemanticType() == SemanticType.HEADING && textNode.getInitialSemanticType() == SemanticType.LIST) {
                PDFList list = textNodeToListMap.get(textNode);
                if (list != null && isNotHeadings(list)) {
                    continue;
                }
                if (list != null) {
                    int listIndex = contents.indexOf(list);
                    if (listIndex != -1) {
                        contents.remove(listIndex);
                        contents.addAll(listIndex, disassemblePDFList(list));
                    }
                }
            }
        }
        setHeadings(contents);
    }

    private static List<IObject> disassemblePDFList(PDFList list) {
        List<IObject> contents = new LinkedList<>();
        for (ListItem item : list.getListItems()) {
            SemanticTextNode node = convertListItemToSemanticTextNode(item);
            node.setSemanticType(SemanticType.HEADING);
            contents.add(node);
            contents.addAll(item.getContents());
        }
        return contents;
    }

    private static SemanticTextNode convertListItemToSemanticTextNode(TextBlock textBlock) {
        SemanticTextNode semanticTextNode = new SemanticTextNode(SemanticType.LIST);
        for (TextLine line : textBlock.getLines()) {
            semanticTextNode.add(line);
        }
        return semanticTextNode;
    }

    private static List<SemanticTextNode> getTextNodesFromContents(List<IObject> contents) {
        List<SemanticTextNode> textNodes = new LinkedList<>();
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode) {
                textNodes.add((SemanticTextNode) content);
            }
        }
        return textNodes;
    }

    private static void processContent(List<SemanticTextNode> textNodes, IObject content, TextNodeStatistics textNodeStatistics,
                                       Map<SemanticTextNode, PDFList> possibleHeadingsInList) {
        if (content instanceof SemanticTextNode) {
            SemanticTextNode textNode = (SemanticTextNode) content;
            if (!textNode.isSpaceNode()) {
                textNodes.add(textNode);
                textNodeStatistics.addTextNode(textNode);
            }
        } else if (content instanceof TableBorder && ((TableBorder) content).isTextBlock()) {
            TableBorder textBlock = (TableBorder) content;
            TableBorderCell cell = textBlock.getCell(0, 0);
            List<SemanticTextNode> cellTextNodes = getTextNodesFromContents(cell.getContents());
            if (cellTextNodes.size() == 1) {
                SemanticTextNode cellNode = cellTextNodes.get(0);
                if (BulletedParagraphUtils.isIeeeSectionTitle(cellNode.getFirstLine())) {
                    processContent(textNodes, cellNode, textNodeStatistics, possibleHeadingsInList);
                }
            }
        } else if (content instanceof PDFList) {
            PDFList list = (PDFList) content;
            ListItem listItem = list.getFirstListItem();
            SemanticTextNode textNode = convertListItemToSemanticTextNode(listItem);
            textNodes.add(textNode);
            textNodeStatistics.addTextNode(textNode);
            possibleHeadingsInList.put(textNode, list);
        }
    }

    private static boolean isNotHeadings(PDFList list) {
        for (int i = 0; i < list.getListItems().size() - 1; i++) {
            boolean onlyLineArtChunks = true;
            List<ListItem> listItems = list.getListItems();
            if (listItems.get(i).getContents().isEmpty()) {
                return true;
            }
            for (IObject item : listItems.get(i).getContents()) {
                if (!(item instanceof LineArtChunk)) {
                    onlyLineArtChunks = false;
                    break;
                }
            }
            if (onlyLineArtChunks) {
                return true;
            }
        }
        return false;
    }

    private static void setHeadings(List<IObject> contents) {
        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (content instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) content;
                SemanticTextNode nextNode = (index + 1 < contents.size() && contents.get(index + 1) instanceof SemanticTextNode) ? (SemanticTextNode) contents.get(index + 1) : null;
                if (isDropCap(textNode, nextNode) || isEquation(textNode) || isTableContent(textNode)) {
                    textNode.setSemanticType(SemanticType.PARAGRAPH);
                    continue;
                }
                if (textNode.getSemanticType() == SemanticType.HEADING && !(content instanceof SemanticHeading)) {
                    SemanticHeading heading = new SemanticHeading(textNode);
                    contents.set(index, heading);
                    StaticLayoutContainers.getHeadings().add(heading);
                }
            }
            if (content instanceof TableBorder) {
                TableBorder table = (TableBorder) content;
                if (table.isTextBlock()) {
                    List<IObject> textBlockContents = table.getCell(0, 0).getContents();
                    List<SemanticTextNode> cellNodes = getTextNodesFromContents(textBlockContents);
                    if (cellNodes.size() == 1 && BulletedParagraphUtils.isIeeeSectionTitle(cellNodes.get(0).getFirstLine())) {
                        setHeadings(textBlockContents);
                    }
                }
            }
        }
    }

    /**
     * Checks if two consecutive text nodes form an ordered list of the exact same numbering style.
     *
     * @param textNode the current text node
     * @param nextNode the next text node
     * @return true if both nodes share the same section title numbering style, false otherwise
     */
    private static boolean isConsecutiveSameStyleList(SemanticTextNode textNode, SemanticTextNode nextNode) {
        if (textNode == null || nextNode == null) return false;
        TextLine line1 = textNode.getFirstLine();
        TextLine line2 = nextNode.getFirstLine();
        if (line1 == null || line2 == null) return false;

        if (BulletedParagraphUtils.isIeeeSectionTitle(line1) && BulletedParagraphUtils.isIeeeSectionTitle(line2)) {
            return false;
        }

        boolean isRoman1 = BulletedParagraphUtils.isRomanSectionTitle(line1);
        boolean isRoman2 = BulletedParagraphUtils.isRomanSectionTitle(line2);
        if (isRoman1 && isRoman2) {
            return true;
        }

        boolean isAlpha1 = BulletedParagraphUtils.isAlphaSubsectionTitle(line1);
        boolean isAlpha2 = BulletedParagraphUtils.isAlphaSubsectionTitle(line2);
        if (isAlpha1 && isAlpha2) {
            return true;
        }

        boolean isNumeric1 = BulletedParagraphUtils.isNumericSectionTitle(line1);
        boolean isNumeric2 = BulletedParagraphUtils.isNumericSectionTitle(line2);
        if (isNumeric1 && isNumeric2) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a text node is a decorative drop cap fragment (1–2 characters followed by uppercase continuation text).
     *
     * @param node the text node to check
     * @param nextNode the following text node in layout order
     * @return true if the node is a decorative drop cap fragment, false otherwise
     */
    private static boolean isDropCap(SemanticTextNode node, SemanticTextNode nextNode) {
        if (node == null || nextNode == null) {
            return false;
        }

        String text = node.getValue();
        if (text == null) {
            return false;
        }

        text = text.trim();
        if (text.isEmpty()) {
            return false;
        }

        int codePoints = text.codePointCount(0, text.length());
        if (codePoints < 1 || codePoints > 2) {
            return false;
        }

        if (text.contains(".") || text.contains(":") || Character.isWhitespace(text.charAt(0))) {
            return false;
        }

        String next = nextNode.getValue();
        if (next == null) {
            return false;
        }

        next = next.stripLeading();
        if (next.length() < 3) {
            return false;
        }

        int uppercasePrefix = 0;
        for (int i = 0; i < next.length();) {
            int cp = next.codePointAt(i);
            if (!Character.isUpperCase(cp)) {
                break;
            }
            uppercasePrefix++;
            i += Character.charCount(cp);
        }

        return uppercasePrefix >= 3;
    }

    /**
     * Checks if a text node is a mathematical equation or formula line.
     *
     * @param node the text node to check
     * @return true if the node is an equation or formula, false otherwise
     */
    private static boolean isEquation(SemanticTextNode node) {
        String value = node.getValue();
        if (value == null) return false;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return false;

        boolean hasEqNumber = trimmed.matches(".*[\\(\\[](eq\\.?\\s*)?\\d+([a-z]|\\.\\d+)?[\\)\\]]\\s*$");

        boolean hasMathSymbols = containsMathOperators(trimmed);

        boolean hasSubOrSuperScript = trimmed.contains("_") || trimmed.contains("^") || trimmed.contains("ˆ")
                || (trimmed.contains("[") && trimmed.contains("]"));

        if (hasEqNumber && (hasMathSymbols || hasSubOrSuperScript)) {
            return true;
        }

        boolean hasEqualityRelation = trimmed.contains("=") || trimmed.contains("≈") || trimmed.contains("≡")
                || trimmed.contains("≤") || trimmed.contains("≥") || trimmed.contains("≠")
                || trimmed.contains("∝") || trimmed.contains("∈") || trimmed.contains("→") || trimmed.contains("⇒");

        if (hasEqualityRelation && (hasMathSymbols || hasSubOrSuperScript)
                && !BulletedParagraphUtils.isIeeeSectionTitle(node.getFirstLine())) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a text string contains mathematical operators or Greek letters.
     *
     * @param text the text string to check
     * @return true if the string contains math operators or Greek characters, false otherwise
     */
    private static boolean containsMathOperators(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= '\u2200' && c <= '\u22FF') || (c >= '\u0370' && c <= '\u03FF')
                    || c == '=' || c == '+' || c == '±' || c == '×' || c == '÷') {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a text node belongs to a table structure using parser table metadata,
     * horizontal chunk gap alignment, and column header notation.
     *
     * @param node the text node to check
     * @return true if the node is part of a table or multi-column layout, false otherwise
     */
    private static boolean isTableContent(SemanticTextNode node) {
        if (node == null) {
            return false;
        }
        SemanticType type = node.getSemanticType();
        if (type == SemanticType.TABLE_HEADER || type == SemanticType.TABLE_CELL || type == SemanticType.TABLE) {
            return true;
        }

        TextLine firstLine = node.getFirstLine();
        if (firstLine == null) {
            return false;
        }

        // 2. Document layout check: multi-column line with horizontal gap > fontSize * 1.0 between adjacent chunks
        List<TextChunk> chunks = firstLine.getTextChunks();
        if (chunks != null && chunks.size() >= 2) {
            double fontSize = firstLine.getFontSize();
            if (fontSize <= 0) fontSize = 10.0;
            for (int i = 0; i < chunks.size() - 1; i++) {
                TextChunk c1 = chunks.get(i);
                TextChunk c2 = chunks.get(i + 1);
                if (c1.getBoundingBox() != null && c2.getBoundingBox() != null) {
                    double gap = c2.getBoundingBox().getLeftX() - c1.getBoundingBox().getRightX();
                    if (gap > Math.max(8.0, fontSize)) {
                        return true;
                    }
                }
            }
        }

        // 3. Tabular column header notation & metric token check
        String val = node.getValue();
        if (val != null) {
            String[] words = val.trim().split("\\s+");
            if (words.length >= 3) {
                int metricTokens = 0;
                for (String w : words) {
                    if (w.contains("@") || w.contains("%") || w.contains("±") || w.matches("^[A-Z][a-z]+\\.$")) {
                        metricTokens++;
                    }
                }
                if (metricTokens >= 2 && !BulletedParagraphUtils.isIeeeSectionTitle(firstLine)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Detects and assigns heading levels based on text style.
     * Groups headings by text style and assigns levels from 1 upwards.
     */
    public static void detectHeadingsLevels() {
        SortedMap<TextStyle, Set<SemanticHeading>> map = new TreeMap<>();
        List<SemanticHeading> headings = StaticLayoutContainers.getHeadings();
        List<SemanticHeading> colorlessHeadings = new ArrayList<>();
        for (SemanticHeading heading : headings) {
            if (TextNodeUtils.getTextColorOrNull(heading) == null) {
                colorlessHeadings.add(heading);
                continue;
            }
            TextStyle textStyle = TextStyle.getTextStyle(heading);
            map.computeIfAbsent(textStyle, k -> new HashSet<>()).add(heading);
        }
        int level = 1;
        TextStyle previousTextStyle = null;
        for (Map.Entry<TextStyle, Set<SemanticHeading>> entry : map.entrySet()) {
            if (previousTextStyle != null && previousTextStyle.compareTo(entry.getKey()) != 0) {
                level++;
            }
            previousTextStyle = entry.getKey();
            for (SemanticHeading heading : entry.getValue()) {
                heading.setHeadingLevel(level);
            }
        }
        // Headings without color info get level based on font size relative to existing levels
        for (SemanticHeading heading : colorlessHeadings) {
            heading.setHeadingLevel(findClosestLevel(heading, map));
        }
    }

    private static int findClosestLevel(SemanticHeading heading, SortedMap<TextStyle, Set<SemanticHeading>> map) {
        if (map.isEmpty()) {
            return 1;
        }
        double fontSize = heading.getFontSize();
        int bestLevel = 1;
        double bestDiff = Double.MAX_VALUE;
        int level = 1;
        TextStyle previousStyle = null;
        for (Map.Entry<TextStyle, Set<SemanticHeading>> entry : map.entrySet()) {
            if (previousStyle != null && previousStyle.compareTo(entry.getKey()) != 0) {
                level++;
            }
            previousStyle = entry.getKey();
            SemanticHeading representative = entry.getValue().iterator().next();
            double diff = Math.abs(representative.getFontSize() - fontSize);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestLevel = level;
            }
        }
        return bestLevel;
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.verapdf.as.ASAtom;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.enums.TextAlignment;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListInterval;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.lists.TextListInterval;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemInfo;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemTextInfo;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ListLabelsUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ListUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ListProcessor {

    private static final Logger LOGGER = Logger.getLogger(ListProcessor.class.getCanonicalName());

    private static final double LIST_ITEM_PROBABILITY = 0.7;
    private static final double LIST_ITEM_BASELINE_DIFFERENCE = 1.2;
    private static final double LIST_ITEM_X_INTERVAL_RATIO = 0.3;
    private static final Pattern ATTACHMENTS_PATTERN = Pattern.compile("^붙\\s*임\\s*(?=.)");

    private static final Map<String, ASAtom> listNumberingMap = new HashMap<>();

    static {
        listNumberingMap.put(NumberingStyleNames.ENGLISH_LETTERS, ASAtom.ORDERED);
        listNumberingMap.put(NumberingStyleNames.ENGLISH_LETTERS_UPPER_CASE, ASAtom.UPPER_ALPHA);
        listNumberingMap.put(NumberingStyleNames.ENGLISH_LETTERS_LOWER_CASE, ASAtom.LOWER_ALPHA);
        listNumberingMap.put(NumberingStyleNames.ROMAN_NUMBERS_LOWER_CASE, ASAtom.LOWER_ROMAN);
        listNumberingMap.put(NumberingStyleNames.ROMAN_NUMBERS, ASAtom.ORDERED);
        listNumberingMap.put(NumberingStyleNames.ROMAN_NUMBERS_UPPER_CASE, ASAtom.UPPER_ROMAN);
        listNumberingMap.put(NumberingStyleNames.KOREAN_LETTERS, ASAtom.ORDERED);
        listNumberingMap.put(NumberingStyleNames.ARABIC_NUMBERS, ASAtom.DECIMAL);
        listNumberingMap.put(NumberingStyleNames.CIRCLED_ARABIC_NUMBERS, ASAtom.ORDERED);
        listNumberingMap.put(NumberingStyleNames.UNORDERED,ASAtom.UNORDERED);
        listNumberingMap.put(NumberingStyleNames.UNKNOWN, ASAtom.NONE);
    }

    public static ASAtom getListNumbering(String numberingStyle) {
        return listNumberingMap.get(numberingStyle);
    }

    public static void processLists(List<List<IObject>> contents, boolean isTableCell) {
        List<TextListInterval> intervalsList = getTextLabelListIntervals(contents);
        for (TextListInterval interval : intervalsList) {
            for (ListItemTextInfo info : interval.getListItemsInfos()) {
                info.getListItemValue().setListLine(true);
            }
        }
        for (TextListInterval interval : intervalsList) {
//            if (interval.getNumberOfColumns() > 1/*== interval.getNumberOfListItems()*/) {//to fix bounding box for multi-column lists
//                continue;
//            }
            if (!isCorrectList(interval)) {//todo move to arabic number list recognition
                continue;
            }
            Integer currentPageNumber = interval.getListItemsInfos().get(0).getPageNumber();
            int index = 0;
            PDFList previousList = null;
            interval.setCommonSuffixLengthToAllInfos();
            for (int i = 0; i < interval.getNumberOfListItems(); i++) {
                ListItemInfo currentInfo = interval.getListItemsInfos().get(i);
                if (!Objects.equals(currentInfo.getPageNumber(), currentPageNumber)) {
                    PDFList list = calculateList(interval, index, i - 1, contents.get(isTableCell ? 0 : currentPageNumber));
                    for (ListItem listItem : list.getListItems()) {
                        listItem.setContents(processListItemContent(listItem.getContents()));
                    }
                    if (previousList != null) {
                        PDFList.setListConnected(previousList, list);
                    }
                    currentPageNumber = currentInfo.getPageNumber();
                    index = i;
                    previousList = list;
                }
            }
            PDFList list = calculateList(interval, index, interval.getNumberOfListItems() - 1, contents.get(isTableCell ? 0 : currentPageNumber));
            for (ListItem listItem : list.getListItems()) {
                listItem.setContents(processListItemContent(listItem.getContents()));
            }
            if (previousList != null) {
                PDFList.setListConnected(previousList, list);
            }
        }
        contents.replaceAll(DocumentProcessor::removeNullObjectsFromList);
    }

    private static List<IObject> processListItemContent(List<IObject> contents) {
        List<IObject> newContents = ParagraphProcessor.processParagraphs(contents);
        newContents = ListProcessor.processListsFromTextNodes(newContents);
        DocumentProcessor.setIDs(newContents);
        List<List<IObject>> contentsList = new ArrayList<>(1);
        contentsList.add(newContents);
        ListProcessor.checkNeighborLists(contentsList);
        newContents = contentsList.get(0);
        return newContents;
    }

    private static void processTextNodeListItemContent(List<IObject> contents) {
        DocumentProcessor.setIDs(contents);
    }

    private static List<TextListInterval> getTextLabelListIntervals(List<List<IObject>> contents) {
        List<TextListInterval> listIntervals = new ArrayList<>();
        for (List<IObject> pageContents : contents) {
            for (int i = 0; i < pageContents.size(); i++) {
                IObject content = pageContents.get(i);
                if (!(content instanceof TextLine)) {
                    continue;
                }
                TextLine line = (TextLine) content;
                String value = line.getValue();
                if (value.isEmpty() || line.isHiddenText()) {
                    continue;
                }
                ListItemTextInfo listItemTextInfo = createListItemTextInfo(i, line, value);
                processListItem(listIntervals, listItemTextInfo);
            }
        }
        LinkedHashSet<TextListInterval> intervalsList = new LinkedHashSet<>();
        for (TextListInterval interval : listIntervals) {
            if (interval != null && interval.getListItemsInfos().size() > 1) {
                intervalsList.add(interval);
            }
        }
        List<TextListInterval> result = new ArrayList<>(intervalsList);
        Collections.reverse(result);
        return result;
    }

    private static void processListItem(List<TextListInterval> listIntervals, ListItemTextInfo listItemTextInfo) {
        double maxXGap = getMaxXGap(listItemTextInfo.getListItemValue().getFontSize());
        boolean isSingle = true;
        boolean shouldHaveSameLeft = false;
        boolean shouldHaveSameLeftDifference = false;
        boolean isUnordered = true;
        Double previousLeftDifference = null;
        for (int index = listIntervals.size() - 1; index >= 0; index--) {
            TextListInterval interval = listIntervals.get(index);
            ListItemTextInfo preivousListItemTextInfo = interval.getLastListItemInfo();
            double leftDifference = listItemTextInfo.getListItemValue().getLeftX() -
                    preivousListItemTextInfo.getListItemValue().getLeftX();
            boolean haveSameLeft = NodeUtils.areCloseNumbers(leftDifference, 0, maxXGap);
            try {
                if (NodeUtils.areCloseNumbers(leftDifference, 0, 4 * maxXGap) &&
                        ListLabelsUtils.isTwoListItemsOfOneList(interval, listItemTextInfo,
                                !haveSameLeft, isUnordered)) {
                    listIntervals.add(interval);
                    isSingle = false;
                    break;
                }
            } catch (StringIndexOutOfBoundsException e) {
                // Malformed label cannot be matched; treat as new list (isSingle remains true)
                LOGGER.log(Level.WARNING, "Malformed list label, starting new list: " + listItemTextInfo.getListItemValue().getValue(), e);
                break;
            }
            if (shouldHaveSameLeftDifference && !NodeUtils.areCloseNumbers(previousLeftDifference, leftDifference)) {
                break;
            }
            if (leftDifference > maxXGap) {
                isUnordered = false;
                shouldHaveSameLeftDifference = true;
            }
            previousLeftDifference = leftDifference;
            if (haveSameLeft) {
                shouldHaveSameLeft = true;
            } else if (shouldHaveSameLeft) {
                isUnordered = false;
//                break;
            }
            if (interval.getListItemsInfos().size() > 1 && haveSameLeft &&
                    !NumberingStyleNames.UNORDERED.equals(interval.getNumberingStyle())) {
                isUnordered = false;
            }
        }
        if (isSingle) {
            TextListInterval listInterval = new TextListInterval();
            listInterval.getListItemsInfos().add(listItemTextInfo);
            listIntervals.add(listInterval);
        }
    }

    private static ListItemTextInfo createListItemTextInfo(int i, TextLine line, String value) {
        Matcher matcher = ATTACHMENTS_PATTERN.matcher(value);
        if (matcher.find()) {
            int length = matcher.group().length();
            line = new TextLine(line);
            line.getBoundingBox().setLeftX(line.getSymbolStartCoordinate(length));
            value = value.substring(length);
        }
        return new ListItemTextInfo(i, SemanticType.PARAGRAPH,
                line, value, true);
    }

    private static PDFList calculateList(TextListInterval interval, int startIndex, int endIndex, List<IObject> pageContents) {
        PDFList list = new PDFList();
        list.setNumberingStyle(interval.getNumberingStyle());
        list.setCommonPrefix(interval.getCommonPrefix());
        boolean isListSet = false;
        for (int index = startIndex; index <= endIndex; index++) {
            ListItemTextInfo currentInfo = interval.getListItemsInfos().get(index);
            int nextIndex = index != endIndex ? interval.getListItemsInfos().get(index + 1).getIndex() : pageContents.size();
            ListItem listItem = new ListItem(new BoundingBox(), null);
            IObject object = pageContents.get(currentInfo.getIndex());
            if (object == null || object instanceof PDFList) {
                LOGGER.log(Level.INFO, "List item is connected with different lists");
                continue;
            }
            pageContents.set(currentInfo.getIndex(), isListSet ? null : list);
            isListSet = true;
            if (object instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) object;
                for (TextLine textLine : textNode.getFirstColumn().getLines()) {
                    listItem.add(textLine);
                }
            } else {
                TextLine textLine = (TextLine) object;
                listItem.add(textLine);
            }
            if (index != endIndex) {
                addContentToListItem(nextIndex, currentInfo, pageContents, listItem);
            } else {
                addContentToLastPageListItem(nextIndex, currentInfo, pageContents, listItem);
            }
            listItem.setLabelLength(currentInfo.getLabelLength());
            list.add(listItem);
        }
        if (list.getListItems().isEmpty()) {
            LOGGER.log(Level.WARNING, "List is not added to contents");
        }
        return list;
    }

    private static void addContentToListItem(int nextIndex, ListItemInfo currentInfo, List<IObject> pageContents,
                                             ListItem listItem) {
        boolean isListItem = true;
        TextLine previousTextLine = null;
        for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
            IObject content = pageContents.get(index);
            if (content instanceof TextLine) {
                TextLine currentTextLine = (TextLine) content;
                if (previousTextLine != null) {
                    if (isListItem && isListItemLine(listItem, previousTextLine, currentTextLine)) {
                        listItem.add(previousTextLine);
                    } else {
                        isListItem = false;
                        listItem.getContents().add(previousTextLine);
                    }
                }
                previousTextLine = currentTextLine;
            } else if (content != null) {
                if (previousTextLine != null) {
                    if (isListItem && isListItemLine(listItem, previousTextLine, null)) {
                        listItem.add(previousTextLine);
                    } else {
                        isListItem = false;
                        listItem.getContents().add(previousTextLine);
                    }
                    previousTextLine = null;
                }
                listItem.getContents().add(content);
            }
            pageContents.set(index, null);
        }
        if (previousTextLine != null) {
            if (isListItem && isListItemLine(listItem, previousTextLine, null)) {
                listItem.add(previousTextLine);
            } else {
                listItem.getContents().add(previousTextLine);
            }
        }
    }

    private static void addContentToLastPageListItem(int nextIndex, ListItemInfo currentInfo, List<IObject> pageContents,
                                                     ListItem listItem) {
        TextLine previousTextLine = null;
        Integer previousIndex = null;
        for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
            IObject content = pageContents.get(index);
            if (!(content instanceof TextLine)) {
                continue;
            }
            TextLine nextLine = (TextLine) content;
            if (previousTextLine != null) {
                if (isListItemLine(listItem, previousTextLine, nextLine)) {
                    listItem.add(previousTextLine);
                    pageContents.set(previousIndex, null);
                } else {
                    previousTextLine = null;
                    break;
                }
            }
            previousTextLine = nextLine;
            previousIndex = index;
        }
        if (previousTextLine != null) {
            if (isListItemLine(listItem, previousTextLine, null)) {
                listItem.add(previousTextLine);
                pageContents.set(previousIndex, null);
            }
        }
    }

    private static boolean isListItemLine(ListItem listItem, TextLine currentLine, TextLine nextLine) {
        TextLine listLine = listItem.getLastLine();
        if (ChunksMergeUtils.mergeLeadingProbability(listLine, currentLine) < LIST_ITEM_PROBABILITY) {
            return false;
        }
        if (nextLine != null) {
            if (Math.abs(listLine.getBaseLine() - currentLine.getBaseLine()) >
                    LIST_ITEM_BASELINE_DIFFERENCE * Math.abs(currentLine.getBaseLine() - nextLine.getBaseLine())) {
                return false;
            }
        }
        if (listItem.getLinesNumber() > 1) {
            TextAlignment alignment = ChunksMergeUtils.getAlignment(listLine, currentLine);
            if (alignment != TextAlignment.JUSTIFY && alignment != TextAlignment.LEFT) {
                return false;
            }
        } else {
            double maxXGap = getMaxXGap(listLine.getFontSize());
            if (currentLine.getLeftX() < listLine.getLeftX() - maxXGap) {
                return false;
            }
        }
        if (BulletedParagraphUtils.isLabeledLine(currentLine)) {
            return false;
        }
        if (currentLine.isListLine()) {
            return false;
        }
        return true;
    }

    private static double getMaxXGap(double fontSize) {
        return fontSize * LIST_ITEM_X_INTERVAL_RATIO;
    }

    public static List<IObject> processListsFromTextNodes(List<IObject> contents) {
        List<SemanticTextNode> textNodes = new ArrayList<>();
        List<Integer> textNodesIndexes = new ArrayList<>();
        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (content instanceof SemanticTextNode) {
                textNodes.add((SemanticTextNode) content);
                textNodesIndexes.add(index);
            }
        }
        List<ListItemTextInfo> textChildrenInfo = calculateTextChildrenInfo(textNodes);
        List<INode> nodes = new LinkedList<>(textNodes);
        Set<ListInterval> intervals = ListUtils.getChildrenListIntervals(ListLabelsUtils.getListItemsIntervals(textChildrenInfo), nodes);
        for (ListInterval interval : intervals) {
            updateListInterval(interval, textNodesIndexes);
            TextListInterval textListInterval = new TextListInterval(interval);
            if (!isCorrectList(textListInterval)) {
                continue;
            }
            textListInterval.setCommonSuffixLengthToAllInfos();
            PDFList list = calculateList(textListInterval, 0, interval.getNumberOfListItems() - 1, contents);
            for (ListItem listItem : list.getListItems()) {
                processTextNodeListItemContent(listItem.getContents());
            }
        }
        return DocumentProcessor.removeNullObjectsFromList(contents);
    }

    private static List<ListItemTextInfo> calculateTextChildrenInfo(List<SemanticTextNode> textNodes) {
        List<ListItemTextInfo> textChildrenInfo = new ArrayList<>(textNodes.size());
        for (int i = 0; i < textNodes.size(); i++) {
            SemanticTextNode textNode = textNodes.get(i);
            if (textNode.isSpaceNode() || textNode.isEmpty()) {
                continue;
            }
            TextLine line = textNode.getFirstNonSpaceLine();
            TextLine secondLine = textNode.getNonSpaceLine(1);
            textChildrenInfo.add(new ListItemTextInfo(i, textNode.getSemanticType(),
                    line, line.getValue(), secondLine == null));
        }
        return textChildrenInfo;
    }

    private static void updateListInterval(ListInterval interval, List<Integer> textNodesIndexes) {
        for (ListItemInfo itemInfo : interval.getListItemsInfos()) {
            itemInfo.setIndex(textNodesIndexes.get(itemInfo.getIndex()));
        }
    }

    private static boolean isCorrectList(TextListInterval interval) {//move inside arabic numeration detection
        return !isDoubles(interval);
    }

    private static boolean isDoubles(TextListInterval interval) {
        for (ListItemTextInfo listItemTextInfo : interval.getListItemsInfos()) {
            if (listItemTextInfo != null) {
                if (!listItemTextInfo.getListItemValue().getValue().matches("^\\d+\\.\\d+$")) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public static void checkNeighborLists(List<List<IObject>> contents) {
        PDFList previousList = null;
        SemanticTextNode middleContent = null;
        for (List<IObject> pageContents : contents) {
            DocumentProcessor.setIndexesForContentsList(pageContents);
            for (IObject content : pageContents) {
                if (content instanceof PDFList) {
                    PDFList currentList = (PDFList) content;
                    if (previousList != null) {
                        if (previousList.getNextList() == null && currentList.getPreviousList() == null) {
                            if (isNeighborLists(previousList, currentList, middleContent)) {
                                if (middleContent != null) {
                                    pageContents.set(middleContent.getIndex(), null);
                                    addMiddleContentToList(previousList, currentList, middleContent);
                                }
                                if (Objects.equals(previousList.getPageNumber(), currentList.getPageNumber()) &&
                                        BoundingBox.areHorizontalOverlapping(previousList.getBoundingBox(), currentList.getBoundingBox())) {
                                    previousList.add(currentList);
                                    pageContents.set(currentList.getIndex(), null);
                                    currentList = null;
                                } else {
                                    PDFList.setListConnected(previousList, currentList);
                                }
                            }
                        } else if (Objects.equals(previousList.getNextListId(), currentList.getRecognizedStructureId())) {
                            if (middleContent != null && isMiddleContentPartOfList(previousList, middleContent, currentList)) {
                                pageContents.set(middleContent.getIndex(), null);
                                addMiddleContentToList(previousList, currentList, middleContent);
                            }
                        }
                    }
                    if (currentList != null) {
                        previousList = currentList;
                    }
                    middleContent = null;
                } else {
                    if (!HeaderFooterProcessor.isHeaderOrFooter(content) &&
                            !(content instanceof LineChunk) && !(content instanceof LineArtChunk) &&
                            !(content instanceof ImageChunk)) {
                        if (middleContent == null && content instanceof SemanticTextNode) {
                            middleContent = (SemanticTextNode) content;
                        } else {
                            middleContent = null;
                            previousList = null;
                        }
                    }
                }
            }
        }
        contents.replaceAll(DocumentProcessor::removeNullObjectsFromList);
    }

    private static void addMiddleContentToList(PDFList previousList, PDFList currentList, SemanticTextNode middleContent) {
        ListItem lastListItem = previousList.getLastListItem();
        if (Objects.equals(lastListItem.getPageNumber(), middleContent.getPageNumber()) &&
                BoundingBox.areHorizontalOverlapping(lastListItem.getBoundingBox(), middleContent.getBoundingBox())) {
            for (TextColumn textColumn : middleContent.getColumns()) {
                lastListItem.add(textColumn.getLines());
            }
            previousList.getBoundingBox().union(middleContent.getBoundingBox());
        } else {
            addFirstLBodyToList(currentList, middleContent);
        }
    }

    private static void addFirstLBodyToList(PDFList currentList, SemanticTextNode middleContent) {
        ListItem listItem = new ListItem(new BoundingBox(), middleContent.getRecognizedStructureId());
        for (TextColumn textColumn : middleContent.getColumns()) {
            listItem.add(textColumn.getLines());
        }
        currentList.add(0, listItem);
    }

    public static boolean isNeighborLists(PDFList previousList, PDFList currentList, SemanticTextNode middleContent) {
        List<ListItemTextInfo> textChildrenInfo = getTextChildrenInfosForNeighborLists(previousList, currentList);
        Set<ListInterval> listIntervals = ListLabelsUtils.getListItemsIntervals(textChildrenInfo);
        if (listIntervals.size() != 1) {
            return false;
        }
        ListInterval interval = listIntervals.iterator().next();
        if (interval.getNumberOfListItems() != textChildrenInfo.size()) {
            return false;
        }
        if (middleContent != null && !isMiddleContentPartOfList(previousList, middleContent, currentList)) {
            return false;
        }
        return true;
    }

    private static boolean isMiddleContentPartOfList(PDFList previousList, SemanticTextNode middleContent, PDFList currentList) {
        if (middleContent.getLeftX() < currentList.getLeftX()) {
            return false;
        }
        if (!Objects.equals(middleContent.getPageNumber(), currentList.getPageNumber())) {
            return false;
        }
        for (ListItem listItem : currentList.getListItems()) {
            if (listItem.getLinesNumber() > 1) {
                double xInterval = getMaxXGap(Math.max(listItem.getFontSize(), middleContent.getFontSize()));
                if (!NodeUtils.areCloseNumbers(listItem.getSecondLine().getLeftX(), middleContent.getLeftX(), xInterval)) {
                    return false;
                }
                break;
            }
        }
        return true;
    }

    private static List<ListItemTextInfo> getTextChildrenInfosForNeighborLists(PDFList previousList, PDFList currentList) {
        List<ListItemTextInfo> textChildrenInfo = new ArrayList<>(4);
        if (previousList.getNumberOfListItems() > 1) {
            textChildrenInfo.add(createListItemTextInfoFromListItem(0, previousList.getPenultListItem()));
        }
        textChildrenInfo.add(createListItemTextInfoFromListItem(1, previousList.getLastListItem()));
        textChildrenInfo.add(createListItemTextInfoFromListItem(2, currentList.getFirstListItem()));
        if (currentList.getNumberOfListItems() > 1) {
            textChildrenInfo.add(createListItemTextInfoFromListItem(3, currentList.getSecondListItem()));
        }
        return textChildrenInfo;
    }

    private static ListItemTextInfo createListItemTextInfoFromListItem(int index, ListItem listItem) {
        TextLine line = listItem.getFirstLine();
        return new ListItemTextInfo(index, SemanticType.LIST_ITEM, line, line.getValue(), listItem.getLinesNumber() == 1);
    }
}

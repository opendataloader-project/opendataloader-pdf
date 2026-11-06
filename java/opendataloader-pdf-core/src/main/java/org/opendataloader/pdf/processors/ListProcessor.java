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
import org.verapdf.as.ASAtom;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
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
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
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
    private static final double FONT_TOLERANCE = 0.1;
    private static final double LIST_ITEM_BASELINE_DIFFERENCE = 1.2;
    private static final double LIST_ITEM_X_INTERVAL_RATIO = 0.3;
    private static final Pattern ATTACHMENTS_PATTERN = Pattern.compile("^붙\\s*임\\s*(?=.)");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile("\\d+\\.\\d+");

    /**
     * Maximum number of intervals to scan backward when matching a TextLine to an existing list.
     * Prevents O(n²) scaling on large documents. A higher value is safer but slower.
     */
    private static final int MAX_LIST_INTERVAL_LOOKBACK = 500;

    private static final Map<String, ASAtom> numberingStyleToListNumbering = new HashMap<>();
    private static final Map<ASAtom, String> listNumberingToNumberingStyle = new HashMap<>();

    static {
        numberingStyleToListNumbering.put(NumberingStyleNames.ENGLISH_LETTERS, ASAtom.ORDERED);
        numberingStyleToListNumbering.put(NumberingStyleNames.ENGLISH_LETTERS_UPPER_CASE, ASAtom.UPPER_ALPHA);
        numberingStyleToListNumbering.put(NumberingStyleNames.ENGLISH_LETTERS_LOWER_CASE, ASAtom.LOWER_ALPHA);
        numberingStyleToListNumbering.put(NumberingStyleNames.ROMAN_NUMBERS_LOWER_CASE, ASAtom.LOWER_ROMAN);
        numberingStyleToListNumbering.put(NumberingStyleNames.ROMAN_NUMBERS, ASAtom.ORDERED);
        numberingStyleToListNumbering.put(NumberingStyleNames.ROMAN_NUMBERS_UPPER_CASE, ASAtom.UPPER_ROMAN);
        numberingStyleToListNumbering.put(NumberingStyleNames.KOREAN_LETTERS, ASAtom.ORDERED);
        numberingStyleToListNumbering.put(NumberingStyleNames.ARABIC_NUMBERS, ASAtom.DECIMAL);
        numberingStyleToListNumbering.put(NumberingStyleNames.CIRCLED_ARABIC_NUMBERS, ASAtom.ORDERED);
        numberingStyleToListNumbering.put(NumberingStyleNames.UNORDERED,ASAtom.UNORDERED);
        numberingStyleToListNumbering.put(NumberingStyleNames.UNKNOWN, ASAtom.NONE);
        numberingStyleToListNumbering.put(NumberingStyleNames.ORDERED, ASAtom.ORDERED);
        numberingStyleToListNumbering.put(NumberingStyleNames.DESCRIPTION, ASAtom.DESCRIPTION);

        listNumberingToNumberingStyle.put(ASAtom.UPPER_ALPHA, NumberingStyleNames.ENGLISH_LETTERS_UPPER_CASE);
        listNumberingToNumberingStyle.put(ASAtom.LOWER_ALPHA,  NumberingStyleNames.ENGLISH_LETTERS_LOWER_CASE);
        listNumberingToNumberingStyle.put(ASAtom.UPPER_ROMAN, NumberingStyleNames.ROMAN_NUMBERS_UPPER_CASE);
        listNumberingToNumberingStyle.put(ASAtom.LOWER_ROMAN, NumberingStyleNames.ROMAN_NUMBERS_LOWER_CASE);
        listNumberingToNumberingStyle.put(ASAtom.DECIMAL, NumberingStyleNames.ARABIC_NUMBERS);
        listNumberingToNumberingStyle.put(ASAtom.CIRCLE, NumberingStyleNames.UNORDERED);
        listNumberingToNumberingStyle.put(ASAtom.DISC, NumberingStyleNames.UNORDERED);
        listNumberingToNumberingStyle.put(ASAtom.SQUARE, NumberingStyleNames.UNORDERED);
        listNumberingToNumberingStyle.put(ASAtom.UNORDERED, NumberingStyleNames.UNORDERED);
        listNumberingToNumberingStyle.put(ASAtom.ORDERED, NumberingStyleNames.ORDERED);
        listNumberingToNumberingStyle.put(ASAtom.NONE, NumberingStyleNames.UNKNOWN);
        listNumberingToNumberingStyle.put(ASAtom.DESCRIPTION, NumberingStyleNames.DESCRIPTION);
    }

    public static ASAtom getListNumbering(String numberingStyle) {
        return numberingStyleToListNumbering.get(numberingStyle);
    }

    public static String getNumberingStyle(ASAtom listNumbering) {
        return listNumberingToNumberingStyle.get(listNumbering);
    }

    public static void processLists(List<List<IObject>> contents, boolean isTableCell) {
        List<TextListInterval> intervalsList = getTextLabelListIntervals(contents, isTableCell);
        for (TextListInterval interval : intervalsList) {
            for (ListItemTextInfo info : interval.getListItemsInfos()) {
                info.getListItemValue().setListLine(true);
            }
        }
        // The lines of each page, before the lists take some of them. The lines of a table cell keep the rules
        // without empty lines, as in the paragraph detection.
        List<ParagraphProcessor.PageLines> pagesLines = new ArrayList<>(contents.size());
        // For each object of each page that a list took into one of its items, that list (#888).
        List<PDFList[]> pagesOwners = new ArrayList<>(contents.size());
        for (List<IObject> pageContents : contents) {
            pagesLines.add(isTableCell || intervalsList.isEmpty() ? null : ParagraphProcessor.getPageLines(pageContents));
            pagesOwners.add(intervalsList.isEmpty() ? null : new PDFList[pageContents.size()]);
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
                int page = isTableCell ? 0 : currentPageNumber;
                if (!Objects.equals(currentInfo.getPageNumber(), currentPageNumber)) {
                    if (index < i) {
                        PDFList list = calculateListPart(interval, index, i - 1, contents.get(page), pagesLines.get(page),
                            pagesOwners.get(page), -1);
                        if (previousList != null) {
                            PDFList.setListConnected(previousList, list);
                        }
                        previousList = list;
                    }
                    currentPageNumber = currentInfo.getPageNumber();
                    index = i;
                } else if (index < i) {
                    int otherListIndex = getIndexOfOtherListWithItem(interval, i, contents.get(page), pagesOwners.get(page));
                    if (otherListIndex >= 0) {
                        // The item before ends before the other list, so that it does not take that list. The items
                        // after this item make a new list, in their place.
                        PDFList list = calculateListPart(interval, index, i - 1, contents.get(page), pagesLines.get(page),
                            pagesOwners.get(page), otherListIndex);
                        if (previousList != null) {
                            PDFList.setListConnected(previousList, list);
                        }
                        previousList = null;
                        index = i + 1;
                    }
                }
            }
            if (index < interval.getNumberOfListItems()) {
                int page = isTableCell ? 0 : currentPageNumber;
                PDFList list = calculateListPart(interval, index, interval.getNumberOfListItems() - 1, contents.get(page),
                    pagesLines.get(page), pagesOwners.get(page), -1);
                if (previousList != null) {
                    PDFList.setListConnected(previousList, list);
                }
            }
        }
        contents.replaceAll(DocumentProcessor::removeNullObjectsFromList);
    }

    /** Detects the paragraphs and the lists of the content of an item; the lines of its page find the empty lines. */
    private static List<IObject> processListItemContent(List<IObject> contents, ParagraphProcessor.PageLines pageLines) {
        List<IObject> newContents = ParagraphProcessor.processParagraphs(contents, pageLines);
        newContents = ListProcessor.processListsFromTextNodes(newContents);
        List<List<IObject>> contentsList = new ArrayList<>(1);
        contentsList.add(newContents);
        ListProcessor.checkNeighborLists(contentsList);
        newContents = contentsList.get(0);
        return newContents;
    }

    private static void processTextNodeListItemContent(List<IObject> contents) {
        DocumentProcessor.setIDs(contents, true);
    }

    private static List<TextListInterval> getTextLabelListIntervals(List<List<IObject>> contents, boolean isTableCell) {
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
        List<TextListInterval> result = new ArrayList<>(intervalsList.size());
        for (TextListInterval interval : intervalsList) {
            for (TextListInterval part : splitAtTextBetweenItems(interval, contents, isTableCell)) {
                if (part.getListItemsInfos().size() > 1) {
                    result.add(part);
                }
            }
        }
        Collections.reverse(result);
        return result;
    }

    /**
     * Splits a list of bullets where a title is between two items, as the title of the next section (#354). The list
     * detection joins a bullet to the list of the bullet above it in the column, and the content stream can draw the
     * titles of a page before or after its bullets. A numbered list stays as it is: its numbers go on across a line
     * between two items.
     */
    private static List<TextListInterval> splitAtTextBetweenItems(TextListInterval interval,
                                                                  List<List<IObject>> contents, boolean isTableCell) {
        if (!NumberingStyleNames.UNORDERED.equals(interval.getNumberingStyle())) {
            return Collections.singletonList(interval);
        }
        List<ListItemTextInfo> infos = interval.getListItemsInfos();
        List<TextListInterval> parts = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < infos.size(); i++) {
            ListItemTextInfo above = infos.get(i - 1);
            ListItemTextInfo below = infos.get(i);
            if (Objects.equals(above.getPageNumber(), below.getPageNumber()) && hasLineThatStartsTextBetween(
                    contents.get(isTableCell ? 0 : below.getPageNumber()), above.getListItemValue(),
                    below.getListItemValue())) {
                parts.add(createIntervalPart(interval, start, i));
                start = i;
            }
        }
        if (start == 0) {
            return Collections.singletonList(interval);
        }
        parts.add(createIntervalPart(interval, start, infos.size()));
        return parts;
    }

    /** The items of the interval from the start index to the end index (exclusive), with its numbering style. */
    private static TextListInterval createIntervalPart(TextListInterval interval, int start, int end) {
        TextListInterval part = new TextListInterval(new ArrayList<>(interval.getListItemsInfos().subList(start, end)));
        part.setNumberingStyle(interval.getNumberingStyle());
        return part;
    }

    /**
     * Whether a line of the page, between the lines of two items and in their column, is a title there: it is in a
     * larger or a heavier font than the item above, it starts at the items or to the left of them, and it does not
     * go on from the line above it. A line in the font of the items, as a paragraph of an item or a caption, is a
     * part of the item above (#876).
     */
    private static boolean hasLineThatStartsTextBetween(List<IObject> pageContents, TextLine itemLine,
                                                        TextLine nextItemLine) {
        List<TextLine> lines = new ArrayList<>();
        for (IObject object : pageContents) {
            if (object instanceof TextLine && object != itemLine && object != nextItemLine) {
                TextLine line = (TextLine) object;
                if (!line.isHiddenText() && !line.getValue().isEmpty()
                        && line.getBaseLine() < itemLine.getBaseLine() && line.getBaseLine() > nextItemLine.getBaseLine()
                        && line.getLeftX() < itemLine.getRightX() && line.getRightX() > itemLine.getLeftX()) {
                    lines.add(line);
                }
            }
        }
        lines.sort((first, second) -> Double.compare(second.getBaseLine(), first.getBaseLine()));
        double maxXGap = getMaxXGap(itemLine.getFontSize());
        TextLine lineAbove = itemLine;
        for (TextLine line : lines) {
            if (line.getLeftX() < itemLine.getLeftX() + maxXGap && !continuesLine(lineAbove, line)
                    && isInALargerOrHeavierFont(line, itemLine)) {
                return true;
            }
            lineAbove = line;
        }
        return false;
    }

    /**
     * Whether the line is in a larger font than the other line, or each of its words is in a heavier font than each
     * word of the other line, as a title above a text. The sizes and the weights are compared with the tolerance of
     * the paragraph detection (0.1).
     */
    private static boolean isInALargerOrHeavierFont(TextLine line, TextLine otherLine) {
        if (line.getFontSize() > otherLine.getFontSize()
                && !NodeUtils.areCloseNumbers(line.getFontSize(), otherLine.getFontSize(), FONT_TOLERANCE)) {
            return true;
        }
        double lightest = Double.POSITIVE_INFINITY;
        for (TextChunk chunk : line.getTextChunks()) {
            if (!chunk.isWhiteSpaceChunk() && !chunk.isEmpty()) {
                lightest = Math.min(lightest, chunk.getFontWeight());
            }
        }
        double heaviest = Double.NEGATIVE_INFINITY;
        for (TextChunk chunk : otherLine.getTextChunks()) {
            if (!chunk.isWhiteSpaceChunk() && !chunk.isEmpty()) {
                heaviest = Math.max(heaviest, chunk.getFontWeight());
            }
        }
        return lightest != Double.POSITIVE_INFINITY && heaviest != Double.NEGATIVE_INFINITY && lightest > heaviest
                && !NodeUtils.areCloseNumbers(lightest, heaviest, FONT_TOLERANCE);
    }

    private static void processListItem(List<TextListInterval> listIntervals, ListItemTextInfo listItemTextInfo) {
        double maxXGap = getMaxXGap(listItemTextInfo.getListItemValue().getFontSize());
        boolean isSingle = true;
        boolean shouldHaveSameLeft = false;
        boolean shouldHaveSameLeftDifference = false;
        boolean isUnordered = true;
        Double previousLeftDifference = null;
        int minIndex = Math.max(0, listIntervals.size() - MAX_LIST_INTERVAL_LOOKBACK);
        for (int index = listIntervals.size() - 1; index >= minIndex; index--) {
            TextListInterval interval = listIntervals.get(index);
            ListItemTextInfo preivousListItemTextInfo = interval.getLastListItemInfo();
            if (Objects.equals(listItemTextInfo.getPageNumber(), preivousListItemTextInfo.getPageNumber()) &&
                listItemTextInfo.getListItemValue().getTopY() > preivousListItemTextInfo.getListItemValue().getTopY()) {
                break;
            }
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
                LOGGER.log(Level.WARNING, "Malformed list label, starting new list: " + listItemTextInfo.getListItemValue().getValue());
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

    /** Makes the list of the items, as {@link #calculateList}, and detects the paragraphs and lists in its items. */
    private static PDFList calculateListPart(TextListInterval interval, int startIndex, int endIndex,
                                             List<IObject> pageContents, ParagraphProcessor.PageLines pageLines,
                                             PDFList[] owners, int lastItemEnd) {
        PDFList list = calculateList(interval, startIndex, endIndex, pageContents, false, pageLines, owners, lastItemEnd);
        for (ListItem listItem : list.getListItems()) {
            listItem.setContents(processListItemContent(listItem.getContents(), pageLines));
        }
        return list;
    }

    /**
     * The index of another list that took the item into one of its items, if that list is between the item before
     * and the item, its numbering style is another one, and it does not start after the start of the item before
     * (#888), or -1. The item before takes the content up to its next item, so it would take that list, with the
     * item in it. Two lists of one style there, as the numbers of the exercises of a page with two columns, stay as
     * they are. A list that starts after the start of the item before is a list in that item.
     */
    private static int getIndexOfOtherListWithItem(TextListInterval interval, int item, List<IObject> pageContents,
                                                   PDFList[] owners) {
        int itemIndex = interval.getListItemsInfos().get(item).getIndex();
        PDFList owner = owners != null && pageContents.get(itemIndex) == null ? owners[itemIndex] : null;
        if (owner == null || Objects.equals(owner.getNumberingStyle(), interval.getNumberingStyle())) {
            return -1;
        }
        TextLine lineBefore = interval.getListItemsInfos().get(item - 1).getListItemValue();
        if (owner.getLeftX() > lineBefore.getLeftX() + getMaxXGap(lineBefore.getFontSize())) {
            return -1;
        }
        for (int index = interval.getListItemsInfos().get(item - 1).getIndex() + 1; index < itemIndex; index++) {
            if (pageContents.get(index) == owner) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Makes the list of the items from the start index to the end index, which are on one page. The lines of the
     * page, if not null, find the empty lines below the last item. The owners, if not null, get the list for each
     * object of the page that it takes. If the last item end is not negative, the last item takes the content up
     * to that index, as the other items do.
     */
    private static PDFList calculateList(TextListInterval interval, int startIndex, int endIndex, List<IObject> pageContents,
                                         boolean isHybrid, ParagraphProcessor.PageLines pageLines, PDFList[] owners,
                                         int lastItemEnd) {
        PDFList list = new PDFList();
        list.setNumberingStyle(interval.getNumberingStyle());
        list.setCommonPrefix(interval.getCommonPrefix());
        double rightX = getColumnRightX(interval, startIndex, endIndex, pageContents);
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
            // The label length tells where the item text starts, which the lines below the item need.
            listItem.setLabelLength(currentInfo.getLabelLength());
            if (index != endIndex) {
                addContentToListItem(nextIndex, currentInfo, pageContents, listItem, rightX, list, owners);
            } else if (lastItemEnd >= 0) {
                addContentToListItem(lastItemEnd, currentInfo, pageContents, listItem, rightX, list, owners);
            } else {
                addContentToLastPageListItem(nextIndex, currentInfo, pageContents, listItem, rightX, pageLines, list,
                    owners);
            }
            if (isHybrid) {
                listItem.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
            }
            list.add(listItem);
        }
        if (list.getListItems().isEmpty()) {
            LOGGER.log(Level.WARNING, "List is not added to contents");
        }
        return list;
    }

    private static void addContentToListItem(int nextIndex, ListItemInfo currentInfo, List<IObject> pageContents,
                                             ListItem listItem, double rightX, PDFList list, PDFList[] owners) {
        boolean isListItem = true;
        TextLine previousTextLine = null;
        TextLine lineAbove = null;
        for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
            IObject content = pageContents.get(index);
            if (content != null && (isAboveListItem(listItem, content) || continuesLine(lineAbove, content))) {
                // The content stream can draw a page element, as a column title, between two items (#626).
                // An object above the item is not its content: it stays on the page, with the lines that
                // continue it.
                lineAbove = content instanceof TextLine ? (TextLine) content : null;
                continue;
            }
            if (content != null) {
                lineAbove = null;
            }
            if (content instanceof TextLine) {
                TextLine currentTextLine = (TextLine) content;
                if (previousTextLine != null) {
                    if (isListItem && isListItemLine(listItem, previousTextLine, currentTextLine, rightX)) {
                        listItem.add(previousTextLine);
                    } else {
                        isListItem = false;
                        listItem.getContents().add(previousTextLine);
                    }
                }
                previousTextLine = currentTextLine;
            } else if (content != null) {
                if (previousTextLine != null) {
                    if (isListItem && isListItemLine(listItem, previousTextLine, null, rightX)) {
                        listItem.add(previousTextLine);
                    } else {
                        isListItem = false;
                        listItem.getContents().add(previousTextLine);
                    }
                    previousTextLine = null;
                }
                listItem.getContents().add(content);
            }
            if (content != null) {
                take(pageContents, index, list, owners);
            }
        }
        if (previousTextLine != null) {
            if (isListItem && isListItemLine(listItem, previousTextLine, null, rightX)) {
                listItem.add(previousTextLine);
            } else {
                listItem.getContents().add(previousTextLine);
            }
        }
    }

    /**
     * Whether the object is completely above the first line of the item. The items of a list are in one column
     * (their lines start at about the same x), so such an object is not a part of the item.
     */
    private static boolean isAboveListItem(ListItem listItem, IObject object) {
        return object.getBottomY() > listItem.getFirstLine().getTopY();
    }

    /** Takes the object at the index out of the page, into an item of the list, and keeps which list took it. */
    private static void take(List<IObject> pageContents, int index, PDFList list, PDFList[] owners) {
        pageContents.set(index, null);
        if (owners != null) {
            owners[index] = list;
        }
    }

    /** Whether the object is a line that continues the given line in one paragraph. */
    private static boolean continuesLine(TextLine line, IObject object) {
        return line != null && object instanceof TextLine
                && ChunksMergeUtils.mergeLeadingProbability(line, (TextLine) object) >= LIST_ITEM_PROBABILITY;
    }

    private static void addContentToLastPageListItem(int nextIndex, ListItemInfo currentInfo, List<IObject> pageContents,
                                                     ListItem listItem, double rightX,
                                                     ParagraphProcessor.PageLines pageLines, PDFList list,
                                                     PDFList[] owners) {
        TextLine previousTextLine = null;
        Integer previousIndex = null;
        for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
            IObject content = pageContents.get(index);
            if (!(content instanceof TextLine)) {
                continue;
            }
            TextLine nextLine = (TextLine) content;
            if (previousTextLine != null) {
                if (isLastItemLine(listItem, previousTextLine, nextLine, rightX, pageLines)) {
                    listItem.add(previousTextLine);
                    take(pageContents, previousIndex, list, owners);
                } else {
                    previousTextLine = null;
                    break;
                }
            }
            previousTextLine = nextLine;
            previousIndex = index;
        }
        if (previousTextLine != null) {
            if (isLastItemLine(listItem, previousTextLine, null, rightX, pageLines)) {
                listItem.add(previousTextLine);
                take(pageContents, previousIndex, list, owners);
            }
        }
    }

    /**
     * Whether the line continues the text of the last item of the list on the page. A line after an empty line
     * does not (#876): the item of a section title takes no line of the paragraph after the empty line below the
     * title, so the title stays a line of its own. The lines of the page find the empty lines, as in the
     * paragraph detection.
     */
    private static boolean isLastItemLine(ListItem listItem, TextLine line, TextLine nextLine, double rightX,
                                          ParagraphProcessor.PageLines pageLines) {
        return !ParagraphProcessor.isEmptyLineBetween(pageLines, listItem.getLastLine(), line)
            && isListItemLine(listItem, line, nextLine, rightX);
    }

    private static boolean isListItemLine(ListItem listItem, TextLine currentLine, TextLine nextLine, double rightX) {
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
        if (BulletedParagraphUtils.isLabeledLine(currentLine) && !wrapsItemText(listItem, listLine, currentLine, rightX)) {
            return false;
        }
        if (currentLine.isListLine()) {
            return false;
        }
        return true;
    }

    /**
     * Whether a line that starts with a label character only wraps the text of the item (#875): the line starts
     * where the item text starts, after the label, and its first word does not fit between the end of the line
     * above and the right edge of the text column. Lines below a line that is not full can be the items of a
     * nested list, so they are not a wrap.
     */
    private static boolean wrapsItemText(ListItem listItem, TextLine lineAbove, TextLine line, double rightX) {
        Double textStart = getItemTextStart(listItem);
        double maxXGap = getMaxXGap(line.getFontSize());
        if (textStart == null || Math.abs(line.getLeftX() - textStart) > maxXGap) {
            return false;
        }
        Double firstWordEnd = getFirstWordEnd(line);
        return firstWordEnd != null && lineAbove.getRightX() + maxXGap + firstWordEnd - line.getLeftX() > rightX;
    }

    /** The x where the text of the item starts, after its label, or null if the item has no label. */
    private static Double getItemTextStart(ListItem listItem) {
        TextLine firstLine = listItem.getFirstLine();
        String value = firstLine.getValue();
        int index = listItem.getLabelLength();
        if (index <= 0) {
            return null;
        }
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index < value.length() ? firstLine.getSymbolStartCoordinate(index) : null;
    }

    private static Double getFirstWordEnd(TextLine line) {
        String value = line.getValue();
        int start = 0;
        while (start < value.length() && Character.isWhitespace(value.charAt(start))) {
            start++;
        }
        int end = start;
        while (end < value.length() && !Character.isWhitespace(value.charAt(end))) {
            end++;
        }
        return end > start ? line.getSymbolEndCoordinate(end - 1) : null;
    }

    /**
     * The right edge of the text column of the list: the right end of the widest text of the page that is
     * beside the items, as a body line of the same column. The widest item alone is not the edge: in a list of
     * short items, it is not a full line. The text of another column is not beside the items.
     */
    private static double getColumnRightX(TextListInterval interval, int startIndex, int endIndex,
                                          List<IObject> pageContents) {
        double leftX = Double.POSITIVE_INFINITY;
        double rightX = Double.NEGATIVE_INFINITY;
        for (int index = startIndex; index <= endIndex; index++) {
            IObject object = pageContents.get(interval.getListItemsInfos().get(index).getIndex());
            if (object != null) {
                leftX = Math.min(leftX, object.getLeftX());
                rightX = Math.max(rightX, object.getRightX());
            }
        }
        double columnRightX = rightX;
        for (IObject object : pageContents) {
            if ((object instanceof TextLine || object instanceof SemanticTextNode || object instanceof PDFList)
                    && object.getLeftX() < rightX && object.getRightX() > leftX) {
                columnRightX = Math.max(columnRightX, object.getRightX());
            }
        }
        return columnRightX;
    }

    private static double getMaxXGap(double fontSize) {
        return fontSize * LIST_ITEM_X_INTERVAL_RATIO;
    }

    public static List<IObject> processListsFromTextNodes(List<IObject> contents) {
        return processListsFromTextNodes(contents, false);
    }

    public static List<IObject> processListsFromTextNodes(List<IObject> contents, boolean isHybrid) {
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
            PDFList list = calculateList(textListInterval, 0, interval.getNumberOfListItems() - 1, contents, isHybrid,
                null, null, -1);
            if (isHybrid) {
                for (ListItem listItem : list.getListItems()) {
                    processTextNodeListItemContent(listItem.getContents());
                }
            }
        }
        return DocumentProcessor.removeNullObjectsFromList(contents);
    }

    /**
     * Puts a list below a line that starts with a bullet into an item of that line (#755). The list detection
     * needs two items, so one such line stays a paragraph, and the list below it is its sibling. The line is the
     * parent item of the list when the list follows it without an empty line, in the same font size, and with its
     * markers at or after the text of the line. A numbered line, as a section title, is not a parent item.
     */
    public static List<IObject> nestListsBelowBulletLines(List<IObject> contents) {
        IObject previous = null;
        int previousIndex = -1;
        ListItem parentItem = null;
        for (int index = 0; index < contents.size(); index++) {
            IObject object = contents.get(index);
            if (object == null || isLineOfText(object, previous)) {
                continue;
            }
            if (object instanceof PDFList && parentItem != null && isNextChildList(previous, (PDFList) object)) {
                // A second list that goes on below the first child list is a child of the same line too.
                parentItem.getContents().add(object);
                contents.set(index, null);
                previous = object;
                continue;
            }
            parentItem = null;
            if (object instanceof PDFList && previous instanceof SemanticParagraph
                    && isParentLine((SemanticParagraph) previous, (PDFList) object)) {
                PDFList parent = createParentList((SemanticParagraph) previous, (PDFList) object);
                contents.set(previousIndex, parent);
                contents.set(index, null);
                parentItem = parent.getListItems().get(0);
                previous = object;
                continue;
            }
            previous = object;
            previousIndex = index;
        }
        return DocumentProcessor.removeNullObjectsFromList(contents);
    }

    /** Whether the list goes on below the child list, without an empty line, with its markers at the same x. */
    private static boolean isNextChildList(IObject childList, PDFList list) {
        TextLine line = list.getListItems().isEmpty() ? null : list.getListItems().get(0).getFirstLine();
        if (!(childList instanceof PDFList) || line == null) {
            return false;
        }
        double fontSize = line.getFontSize();
        double gap = childList.getBottomY() - list.getTopY();
        return gap >= -getMaxXGap(fontSize) && gap <= fontSize
                && Math.abs(list.getLeftX() - childList.getLeftX()) <= getMaxXGap(fontSize);
    }

    /** Whether the object is a line in the box of the paragraph before it, as an underline of its text. */
    private static boolean isLineOfText(IObject object, IObject previous) {
        if (!(previous instanceof SemanticParagraph) || !(object instanceof LineChunk || object instanceof LineArtChunk)) {
            return false;
        }
        double tolerance = getMaxXGap(((SemanticParagraph) previous).getFontSize());
        return object.getLeftX() >= previous.getLeftX() - tolerance && object.getRightX() <= previous.getRightX() + tolerance
                && object.getBottomY() >= previous.getBottomY() - tolerance && object.getTopY() <= previous.getTopY() + tolerance;
    }

    private static boolean isParentLine(SemanticParagraph paragraph, PDFList list) {
        TextLine line = paragraph.getFirstLine();
        if (line == null || list.getListItems().isEmpty() || !BulletedParagraphUtils.startsWithBullet(line)) {
            return false;
        }
        double fontSize = line.getFontSize();
        TextLine listLine = list.getListItems().get(0).getFirstLine();
        double maxXGap = getMaxXGap(fontSize);
        int textIndex = getBulletLabelLength(line);
        if (listLine == null || Math.abs(listLine.getFontSize() - fontSize) > maxXGap || textIndex >= line.getValue().length()) {
            return false;
        }
        double gap = paragraph.getBottomY() - list.getTopY();
        return gap >= -maxXGap && gap <= fontSize
                && list.getLeftX() > paragraph.getLeftX() + maxXGap
                && list.getLeftX() >= line.getSymbolStartCoordinate(textIndex) - maxXGap;
    }

    /** The length of the bullet at the start of the line and of the white space after it. */
    private static int getBulletLabelLength(TextLine line) {
        String value = line.getValue();
        int index = line.getConnectedLineArtLabel() != null ? 0 : 1;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static PDFList createParentList(SemanticParagraph paragraph, PDFList list) {
        ListItem item = new ListItem(new BoundingBox(), null);
        for (TextLine textLine : paragraph.getFirstColumn().getLines()) {
            item.add(textLine);
        }
        item.setLabelLength(getBulletLabelLength(item.getFirstLine()));
        item.getContents().add(list);
        PDFList parent = new PDFList();
        parent.setNumberingStyle(NumberingStyleNames.UNORDERED);
        parent.add(item);
        // The IDs are set before this step: the list takes the ID of the paragraph that it replaces.
        parent.setRecognizedStructureId(paragraph.getRecognizedStructureId());
        item.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return parent;
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
                if (!DOUBLE_PATTERN.matcher(listItemTextInfo.getListItemValue().getValue()).matches()) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }

    public static void checkNeighborListsInTable(TableBorder tableBorder) {
        for (int rowNumber = 0; rowNumber < tableBorder.getNumberOfRows(); rowNumber++) {
            TableBorderRow row = tableBorder.getRow(rowNumber);
            for (int colNumber = 0; colNumber < tableBorder.getNumberOfColumns(); colNumber++) {
                TableBorderCell tableBorderCell = row.getCell(colNumber);
                if (tableBorderCell.getRowNumber() == rowNumber && tableBorderCell.getColNumber() == colNumber) {
                    List<List<IObject>> contentsList = new ArrayList<>(1);
                    contentsList.add(tableBorderCell.getContents());
                    checkNeighborLists(contentsList);
                    tableBorderCell.setContents(contentsList.get(0));
                }
            }
        }
    }

    public static void checkNeighborLists(List<List<IObject>> contents) {
        PDFList previousList = null;
        SemanticTextNode middleContent = null;
        for (List<IObject> pageContents : contents) {
            DocumentProcessor.setIndexesForContentsList(pageContents);
            for (IObject content : pageContents) {
                if (content instanceof TableBorder) {
                    TableBorder tableBorder = (TableBorder) content;
                    checkNeighborListsInTable(tableBorder);
                }
                if (content instanceof PDFList) {
                    PDFList currentList = (PDFList) content;
                    if (previousList != null) {
                        if (previousList.getNextList() == null && currentList.getPreviousList() == null) {
                            if (isNeighborLists(previousList, currentList, middleContent)) {
                                if (middleContent != null) {
                                    addMiddleContentToList(previousList, currentList, middleContent, pageContents);
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
                                addMiddleContentToList(previousList, currentList, middleContent, pageContents);
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

    private static void addMiddleContentToList(PDFList previousList, PDFList currentList, SemanticTextNode middleContent, List<IObject> pageContents) {
        ListItem lastListItem = previousList.getLastListItem();
        if (Objects.equals(lastListItem.getPageNumber(), middleContent.getPageNumber()) &&
                BoundingBox.areHorizontalOverlapping(lastListItem.getBoundingBox(), middleContent.getBoundingBox())) {
            for (TextColumn textColumn : middleContent.getColumns()) {
                lastListItem.add(textColumn.getLines());
            }
            previousList.getBoundingBox().union(middleContent.getBoundingBox());
            pageContents.set(middleContent.getIndex(), null);
        } else {
            if (middleContent.getTopY() > currentList.getTopY()) {
                addFirstLBodyToList(currentList, middleContent);
                pageContents.set(middleContent.getIndex(), null);
            }
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
        if (startsTextBetweenBulletLists(previousList, middleContent, currentList)) {
            return false;
        }
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

    /**
     * Whether the content between two lists of bullets on one page is a title between them, as the title of a
     * section (#354): its first line is in a larger or a heavier font than the last item, it starts at the bullets or
     * to the left of them, and it does not go on from the last item. The lists stay apart, as
     * {@link #splitAtTextBetweenItems} made them.
     */
    private static boolean startsTextBetweenBulletLists(PDFList previousList, SemanticTextNode middleContent,
                                                        PDFList currentList) {
        if (!NumberingStyleNames.UNORDERED.equals(previousList.getNumberingStyle())
                || !NumberingStyleNames.UNORDERED.equals(currentList.getNumberingStyle())) {
            return false;
        }
        ListItem lastItem = previousList.getLastListItem();
        TextLine line = middleContent.getFirstLine();
        if (line == null || !Objects.equals(lastItem.getPageNumber(), middleContent.getPageNumber())) {
            return false;
        }
        TextLine itemLine = lastItem.getFirstLine();
        return line.getLeftX() < itemLine.getLeftX() + getMaxXGap(itemLine.getFontSize())
                && !continuesLine(lastItem.getLastLine(), line) && isInALargerOrHeavierFont(line, itemLine);
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

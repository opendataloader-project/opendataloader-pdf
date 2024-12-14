/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.processors;

import com.duallab.layout.utils.BulletedParagraphUtils;
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
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemInfo;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemTextInfo;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ListProcessor {

    private static final Logger LOGGER = Logger.getLogger(ListProcessor.class.getCanonicalName());

    private static final double LIST_ITEM_PROBABILITY = 0.7;

    private static final double LIST_ITEM_X_INTERVAL_RATIO = 0.3;
    private static final Pattern ATTACHMENTS_PATTERN = Pattern.compile("^붙\\s*임\\s*(?=.)");

    public static void processLists(List<List<IObject>> contents, boolean isTableCell) {
        List<ListInterval> intervalsList = getTextLabelListIntervals(contents);
        for (ListInterval interval : intervalsList) {
            for (ListItemInfo info : interval.getListItemsInfos()) {
                ((ListItemTextInfo) info).getListItemValue().setListLine(true);
            }
        }
        for (ListInterval interval : intervalsList) {
//            if (interval.getNumberOfColumns() > 1/*== interval.getNumberOfListItems()*/) {//to fix bounding box for multi-column lists
//                continue;
//            }
            if (!isCorrectList(interval)) {//todo move to arabic number list recognition
                continue;
            }
            Integer currentPageNumber = interval.getListItemsInfos().get(0).getPageNumber();
            int index = 0;
            PDFList previousList = null;
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
    
    private static List<ListInterval> getTextLabelListIntervals(List<List<IObject>> contents) {
        Stack<List<ListItemTextInfo>> stack = new Stack<>();
        Stack<Double> leftStack = new Stack<>();
        leftStack.push(-Double.MAX_VALUE);
        List<ListItemTextInfo> textChildrenInfoList = new ArrayList<>();
        List<ListInterval> intervalsList = new LinkedList<>();
        for (List<IObject> pageContents : contents) {
            for (int i = 0; i < pageContents.size(); i++) {
                IObject content = pageContents.get(i);
                if (!(content instanceof TextLine)) {
                    continue;
                }
                TextLine line = (TextLine) content;
                String value = line.getValue();
                if (value.isEmpty()) {
                    continue;
                }
                ListItemTextInfo listItemTextInfo = createListItemTextInfo(i, line, value);
                double maxXInterval = getMaxXInterval(line.getFontSize());
                while (!NodeUtils.areCloseNumbers(leftStack.peek(), line.getLeftX(), maxXInterval) && leftStack.peek() > line.getLeftX()) {
                    intervalsList.addAll(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList));
                    if (stack.isEmpty()) {
                        textChildrenInfoList = new ArrayList<>();
                        break;
                    }
                    textChildrenInfoList = stack.pop();
                    leftStack.pop();
                }
                if (NodeUtils.areCloseNumbers(leftStack.peek(), line.getLeftX(), maxXInterval)) {
                    textChildrenInfoList.add(listItemTextInfo);
                } else if (leftStack.peek() < line.getLeftX()) {
                    leftStack.push(line.getLeftX());
                    stack.push(textChildrenInfoList);
                    textChildrenInfoList = new ArrayList<>();
                    textChildrenInfoList.add(listItemTextInfo);
                }
            }
        }
        while (!stack.isEmpty()) {
            intervalsList.addAll(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList));
            textChildrenInfoList = stack.pop();
            leftStack.pop();
        }
        return intervalsList;
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

    private static PDFList calculateList(ListInterval interval, int startIndex, int endIndex, List<IObject> pageContents) {
        PDFList list = new PDFList(0L);
        list.setNumberingStyle(interval.getNumberingStyle());
        boolean isListSet = false;
        for (int index = startIndex; index <= endIndex; index++) {
            ListItemInfo currentInfo = interval.getListItemsInfos().get(index);
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
                list.add(listItem);
                continue;
            }
            TextLine textLine = (TextLine)object;
            listItem.add(textLine);
            if (index != endIndex) {
                addContentToListItem(nextIndex, currentInfo, pageContents, listItem);
            } else {
                addContentToLastPageListItem(nextIndex, currentInfo, pageContents, listItem);
            }
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
        for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
            IObject content = pageContents.get(index);
            if (content instanceof TextLine) {
                if (isListItem && isListItemLine(listItem, (TextLine) content)) {
                    listItem.add((TextLine) content);
                } else {
                    isListItem = false;
                    listItem.getContents().add(content);
                }
            } else if (content != null) {
                listItem.getContents().add(content);
            }
            pageContents.set(index, null);
        }
    }

    private static void addContentToLastPageListItem(int nextIndex, ListItemInfo currentInfo, List<IObject> pageContents, 
                                                     ListItem listItem) {
        for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
            IObject content = pageContents.get(index);
            if (!(content instanceof TextLine)) {
                continue;
            }
            TextLine nextLine = (TextLine) content;
            if (isListItemLine(listItem, nextLine)) {
                listItem.add(nextLine);
                pageContents.set(index, null);
            } else {
                break;
            }
        }
    }

    private static boolean isListItemLine(ListItem listItem, TextLine nextLine) {
        TextLine listLine = listItem.getLastLine();
        if (ChunksMergeUtils.mergeLeadingProbability(listLine, nextLine) < LIST_ITEM_PROBABILITY) {
            return false;
        }
        if (listItem.getLinesNumber() > 1) {
            TextAlignment alignment = ChunksMergeUtils.getAlignment(listLine, nextLine);
            return alignment == TextAlignment.JUSTIFY || alignment == TextAlignment.LEFT;
        }
        if (nextLine.getLeftX() <= listLine.getLeftX()) {
            return false;
        }
        if (BulletedParagraphUtils.isLabeledLine(nextLine)) {
            return false;
        }
        if (nextLine.isListLine()) {
            return false;
        }
        return true;
    }

    private static double getMaxXInterval(double fontSize) {
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
            if (!isCorrectList(interval)) {
                continue;
            }
            PDFList list = calculateList(interval, 0, interval.getNumberOfListItems() - 1, contents);
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

    private static boolean isCorrectList(ListInterval interval) {//move inside arabic numeration detection
        return !isDoubles(interval);
    }
    
    private static boolean isDoubles(ListInterval interval) {
        for (ListItemInfo listItemTextInfo : interval.getListItemsInfos()) {
            if (listItemTextInfo instanceof ListItemTextInfo) {
                if (!((ListItemTextInfo)listItemTextInfo).getListItemValue().getValue().matches("^\\d+\\.\\d+$")) {
                    return false;
                }
            } else {
                return false;
            }
        }
        return true;
    }
    
    //todo for lists inside tables and lists
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
                                PDFList.setListConnected(previousList, currentList);
                                if (Objects.equals(previousList.getPageNumber(), currentList.getPageNumber()) &&
                                        BoundingBox.areHorizontalOverlapping(previousList.getBoundingBox(), currentList.getBoundingBox())) {
                                    previousList.add(currentList);
                                    pageContents.set(currentList.getIndex(), null);
                                    currentList = null;
                                }
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
        if (middleContent != null) {
            if (middleContent.getLeftX() < currentList.getLeftX()) {
                return false;
            }
            if (!Objects.equals(middleContent.getPageNumber(), currentList.getPageNumber())) {
                return false;
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

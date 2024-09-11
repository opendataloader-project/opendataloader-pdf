package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListInterval;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemInfo;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemTextInfo;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ChunksMergeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ListLabelsUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ListProcessor {

    private static final Logger LOGGER = Logger.getLogger(ListProcessor.class.getCanonicalName());

    private static final double LIST_ITEM_PROBABILITY = 0.7;

    private static final double LIST_ITEM_X_INTERVAL_RATIO = 0.3;

    public static List<IObject> processLists(List<IObject> contents) {
        List<ListInterval> intervalsList = getListIntervalsList(contents);
        for (ListInterval interval : intervalsList) {
//            if (interval.getNumberOfColumns() > 1/*== interval.getNumberOfListItems()*/) {//to fix bounding box for multi-column lists
//                continue;
//            }
            if (!isCorrectList(interval)) {//todo move to arabic number list recognition
                continue;
            }
            PDFList list = calculateList(interval, contents);
            for (ListItem listItem : list.getListItems()) {
                processListItemContent(listItem.getContents());
            }
        }
        List<IObject> newContents = new ArrayList<>();
        for (IObject content : contents) {
            if (content != null) {
                newContents.add(content);
            }
        }
        return newContents;
    }

    public static void processListItemContent(List<IObject> contents) {
//        List<IObject> newContents = TextLineProcessor.processTextLines(contents);
//        newContents = ParagraphProcessor.processParagraphs(newContents);
        DocumentProcessor.setIDs(contents);
    }
    
    private static List<ListInterval> getListIntervalsList(List<IObject> contents) {
        Stack<List<ListItemTextInfo>> stack = new Stack<>();
        Stack<Double> leftStack = new Stack<>();
        leftStack.push(-Double.MAX_VALUE);
        List<ListItemTextInfo> textChildrenInfoList = new ArrayList<>();
        List<ListInterval> intervalsList = new LinkedList<>();
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (!(content instanceof TextLine)) {
                continue;
            }
            TextLine line = (TextLine) content;
            String value = line.getValue().trim();
            if (value.isEmpty()) {
                continue;
            }
            ListItemTextInfo listItemTextInfo = new ListItemTextInfo(i, SemanticType.PARAGRAPH,
                    line, value, true);
            double maxXInterval = getMaxXInterval(line.getFontSize());
            while (!NodeUtils.areCloseNumbers(leftStack.peek(), line.getLeftX(), maxXInterval) && leftStack.peek() > line.getLeftX()) {
                intervalsList.addAll(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList));
//                intervalsSet.addAll(ListUtils.getChildrenListIntervals(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList), nodes));
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
        while (!stack.isEmpty()) {
            intervalsList.addAll(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList));
//                    ListUtils.getTextLinesListIntervals(, 
//                    textLines));
            textChildrenInfoList = stack.pop();
            leftStack.pop();
        }
        return intervalsList;
    }

    private static PDFList calculateList(ListInterval interval, List<IObject> contents) {
        PDFList list = new PDFList(0L);
        list.setNumberingStyle(interval.getNumberingStyle());
        for (int i = 0; i < interval.getNumberOfListItems(); i++) {
            ListItemInfo currentInfo = interval.getListItemsInfos().get(i);
            int nextIndex = i != interval.getNumberOfListItems() - 1 ? interval.getListItemsInfos().get(i + 1).getIndex() : contents.size();
            ListItem listItem = new ListItem(new BoundingBox(), null);
            TextLine textLine = (TextLine)contents.get(currentInfo.getIndex());
            if (textLine == null) {
                LOGGER.log(Level.WARNING, "List item is null");
                continue;
            }
            contents.set(currentInfo.getIndex(), i == 0 ? list : null);
            listItem.add(textLine);
            if (i != interval.getNumberOfListItems() - 1) {
                for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
                    IObject content = contents.get(index);
                    if (content instanceof TextLine) {
                        listItem.add((TextLine) content);
                    } else if (content != null) {
                        listItem.getContents().add(content);
                    }
                    contents.set(index, null);
                }
            } else {
                for (int index = currentInfo.getIndex() + 1; index < nextIndex; index++) {
                    IObject content = contents.get(index);
                    if (!(content instanceof TextLine)) {
                        continue;
                    }
                    TextLine nextLine = (TextLine) content;
                    if (isListItemLine(listItem, nextLine)) {
                        listItem.add(nextLine);
                        contents.set(index, null);
                    } else {
                        break;
                    }
                }
            }
            list.add(listItem);
        }
        return list;
    }
    
    private static boolean isListItemLine(ListItem listItem, TextLine nextLine) {
        TextLine listLine = listItem.getLastLine();
        if (ChunksMergeUtils.mergeLeadingProbability(listLine, nextLine) < LIST_ITEM_PROBABILITY) {
            return false;
        }
        if (listItem.getLinesNumber() > 1) {
            return NodeUtils.areCloseNumbers(listLine.getLeftX(), nextLine.getLeftX());
        }
        return nextLine.getLeftX() > listLine.getLeftX();
    }

    private static double getMaxXInterval(double fontSize) {
        return fontSize * LIST_ITEM_X_INTERVAL_RATIO;
    }
//    private static void processLists(List<IObject> contents) {
//        List<SemanticTextNode> textNodes = new LinkedList<>();
//        for (IObject content : contents) {
//            if (content instanceof SemanticTextNode) {
//                textNodes.add((SemanticTextNode) content);
//            }
//        }
//        List<ListItemTextInfo> textChildrenInfo = new ArrayList<>(textNodes.size());
//        List<INode> nodes = new LinkedList<>(textNodes);
//        for (int i = 0; i < textNodes.size(); i++) {
//            SemanticTextNode textNode = textNodes.get(i);
//            if (textNode.isSpaceNode() || textNode.isEmpty()) {
//                continue;
//            }
//            TextLine line = DocumentProcessor.getTextLine(textNode.getFirstNonSpaceLine());
//            TextLine secondLine = textNode.getNonSpaceLine(1);
//            textChildrenInfo.add(new ListItemTextInfo(i, textNode.getSemanticType(),
//                    line, line.getValue().trim(), secondLine == null));
//        }
//        Set<ListInterval> intervals = ListUtils.getChildrenListIntervals(ListLabelsUtils.getListItemsIntervals(textChildrenInfo), nodes);
//        for (ListInterval interval : intervals) {
////            if (interval.getNumberOfColumns() > 1/*== interval.getNumberOfListItems()*/) {//to fix bounding box for multi column lists
////                continue;
////            }
//            if (!isCorrectList(interval)) {
//                continue;
//            }
//            BoundingBox box = new BoundingBox();
//            for (int i = interval.getStart(); i <= interval.getEnd(); i++) {//fix?
//                box.union(textNodes.get(i).getBoundingBox());
////                textNodes.get(i).setSemanticType(SemanticType.LIST_ITEM);
//            }
//            if (!Objects.equals(box.getPageNumber(), box.getLastPageNumber())) {
//                continue;
//            }
//            SemanticTextNode list = new SemanticTextNode(box);
//            list.setSemanticType(SemanticType.LIST);
//            DocumentProcessor.replaceContentsToResult(contents, list);
//            String value = String.format("List: number of items %s", (interval.getEnd() - interval.getStart() + 1));
//            StaticLayoutContainers.getContentInfoMap().put(list, new ContentInfo(value, PDFWriter.getColor(SemanticType.LIST)));
//        }
//    }

    private static boolean isCorrectList(ListInterval interval) {
        boolean doubles = true;
        for (ListItemInfo listItemTextInfo : interval.getListItemsInfos()) {
            if (listItemTextInfo instanceof ListItemTextInfo) {
                if (!((ListItemTextInfo)listItemTextInfo).getListItemValue().getValue().matches("^\\d+\\.\\d+$")) {
                    doubles = false;
                    return true;
                }
            } else {
                doubles = false;
                return true;
            }
        }
        return false;//!doubles;
    }
    
    //todo for lists inside tables and lists
    public static void checkNeighborLists(List<List<IObject>> contents) {
        PDFList previousList = null;
        for (List<IObject> iObjects : contents) {
            for (IObject content : iObjects) {
                if (content instanceof PDFList) {
                    PDFList currentList = (PDFList) content;
                    if (previousList != null) {
                        checkNeighborLists(previousList, currentList);
                    }
                    previousList = currentList;
                } else {
                    if (!HeaderFooterProcessor.isHeaderOrFooter(content)) {
                        previousList = null;
                    }
                }
            }
        }
    }
    
    public static void checkNeighborLists(PDFList previousList, PDFList currentList) {
        Set<ListInterval> listIntervals = ListLabelsUtils.getListItemsIntervals(getTextChildrenInfosForNeighborLists(previousList, currentList));
        if (listIntervals.size() != 1) {
            return;
        }
        ListInterval interval = listIntervals.iterator().next();
        if (interval.getNumberOfListItems() != 4) {
            return;
        }
        currentList.setPreviousListId(previousList.getRecognizedStructureId());
    }

    private static List<ListItemTextInfo> getTextChildrenInfosForNeighborLists(PDFList previousList, PDFList currentList) {
        List<ListItemTextInfo> textChildrenInfo = new ArrayList<>(4);
        textChildrenInfo.add(createListItemTextInfoFromListItem(0, previousList.getPenultListItem()));
        textChildrenInfo.add(createListItemTextInfoFromListItem(0, previousList.getLastListItem()));
        textChildrenInfo.add(createListItemTextInfoFromListItem(0, currentList.getFirstListItem()));
        textChildrenInfo.add(createListItemTextInfoFromListItem(0, currentList.getSecondListItem()));
        return textChildrenInfo;
    }
    
    private static ListItemTextInfo createListItemTextInfoFromListItem(int index, ListItem listItem) {
        TextLine line = listItem.getFirstLine();
        String value = line.getValue().trim();
        return new ListItemTextInfo(index, SemanticType.LIST_ITEM, line, value, listItem.getLinesNumber() == 1);
    }
}

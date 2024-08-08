package com.duallab.layout.processors;

import com.duallab.layout.ContentInfo;
import com.duallab.layout.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
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

import com.duallab.layout.pdf.PDFWriter;

public class ListProcessor {

    public static void processLists(List<IObject> contents) {
        List<TextLine> textLines = new LinkedList<>();
        for (IObject content : contents) {
            if (content instanceof TextLine) {
                textLines.add((TextLine) content);
            }
        }
        Stack<List<ListItemTextInfo>> stack = new Stack<>();
        Stack<Double> leftStack = new Stack<>();
        leftStack.push(-Double.MAX_VALUE);
        List<ListItemTextInfo> textChildrenInfoList = new ArrayList<>();
        Set<ListInterval> intervalsSet = new HashSet<>();
        int pageNumber = 0;
        for (int i = 0; i < textLines.size(); i++) {
            TextLine textLine = textLines.get(i);
            if (textLine.isSpaceLine() || textLine.isEmpty()) {
                continue;
            }
            TextLine line = DocumentProcessor.getTrimTextLine(textLine);
            ListItemTextInfo listItemTextInfo = new ListItemTextInfo(i, SemanticType.PARAGRAPH,
                    line, line.getValue().trim(), true);
            while (((!NodeUtils.areCloseNumbers(leftStack.peek(), line.getLeftX()) && leftStack.peek() > line.getLeftX()) ||
                    line.getPageNumber() != pageNumber)) {
                intervalsSet.addAll(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList));
//                intervalsSet.addAll(ListUtils.getChildrenListIntervals(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList), nodes));
                if (stack.isEmpty()) {
                    textChildrenInfoList = new ArrayList<>();
                    break;
                }
                textChildrenInfoList = stack.pop();
                leftStack.pop();
            }
            if (NodeUtils.areCloseNumbers(leftStack.peek(), line.getLeftX())) {
                textChildrenInfoList.add(listItemTextInfo);
            } else if (leftStack.peek() < line.getLeftX()) {
                leftStack.push(line.getLeftX());
                stack.push(textChildrenInfoList);
                textChildrenInfoList = new ArrayList<>();
                textChildrenInfoList.add(listItemTextInfo);
            }
            pageNumber = line.getPageNumber();
        }
        while (!stack.isEmpty()) {
            intervalsSet.addAll(ListLabelsUtils.getListItemsIntervals(textChildrenInfoList));
//                    ListUtils.getTextLinesListIntervals(, 
//                    textLines));
            textChildrenInfoList = stack.pop();
            leftStack.pop();
        }
        for (ListInterval interval : intervalsSet) {
//            if (interval.getNumberOfColumns() > 1/*== interval.getNumberOfListItems()*/) {//to fix bounding box for multi-column lists
//                continue;
//            }
            if (!isCorrectList(interval)) {
                continue;
            }
            PDFList list = calculateList(interval, textLines);
            if (!Objects.equals(list.getPageNumber(), list.getLastPageNumber())) {
                continue;
            }
            DocumentProcessor.replaceContentsToResult(contents, list);
            String value = String.format("List: number of items %s", interval.getNumberOfListItems());
            StaticLayoutContainers.getContentInfoMap().put(list, new ContentInfo(value, PDFWriter.getColor(SemanticType.LIST)));
        }
    }

    private static PDFList calculateList(ListInterval interval, List<TextLine> textLines) {
        SemanticTextNode list = new SemanticTextNode();
        PDFList list1 = new PDFList(0L);
        for (int i = 0; i < interval.getNumberOfListItems(); i++) {
            ListItemInfo currentInfo = interval.getListItemsInfos().get(i);
            int nextIndex = i != interval.getNumberOfListItems() - 1 ? interval.getListItemsInfos().get(i + 1).getIndex() : textLines.size();
            ListItem listItem = new ListItem(new BoundingBox(), null);
            TextLine textLine = DocumentProcessor.getTrimTextLine(textLines.get(currentInfo.getIndex()));
            listItem.add(textLine);
            list.add(textLine);
            int index = currentInfo.getIndex() + 1;
            while (index < nextIndex) {
                TextLine line = list.getLastLine();
                TextLine nextLine = DocumentProcessor.getTrimTextLine(textLines.get(index));
                if (isListItemLine(line, nextLine)) {
                    listItem.add(nextLine);
                    list.add(nextLine);
                } else {
                    break;
                }
                index++;
            }
            list1.add(listItem);
        }
        list.setSemanticType(SemanticType.LIST);
        return list1;
    }
    
    private static boolean isListItemLine(TextLine listLine, TextLine nextLine) {
        return ChunksMergeUtils.mergeLeadingProbability(listLine, nextLine) > 0.7 &&
                (NodeUtils.areCloseNumbers(listLine.getLeftX(), nextLine.getLeftX()) ||
                        nextLine.getLeftX() > listLine.getLeftX()) && Objects.equals(listLine.getPageNumber(), nextLine.getPageNumber());
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
                if (!((ListItemTextInfo)listItemTextInfo).getListItemValue().getValue().matches("^\\d+\\.\\d+.*")) {
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
}

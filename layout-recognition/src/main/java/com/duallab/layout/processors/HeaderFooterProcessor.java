package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeader;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListInterval;
import org.verapdf.wcag.algorithms.entities.lists.ListIntervalsCollection;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemInfo;
import org.verapdf.wcag.algorithms.entities.lists.info.ListItemTextInfo;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.ListLabelsUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.*;

import java.util.*;

public class HeaderFooterProcessor {

    public static void processHeadersAndFooters(List<List<IObject>> contents) {
        //support of 2-pages/books style
        //to check same/intersect position of headers/footers, but on different pages
        //sort before
        List<List<IObject>> sortedContents = new ArrayList<>();
        for (List<IObject> content : contents) {
            sortedContents.add(sortContents(content));
        }
        List<SemanticTextNode> headers = findPossibleHeaders(sortedContents);
        processHeadersOrFooters(headers, true);
        List<SemanticTextNode> footers = findPossibleFooters(sortedContents);
        processHeadersOrFooters(footers, false);
    }

    private static void processHeadersOrFooters(List<SemanticTextNode> textNodes, boolean isHeader) {
        Set<ListInterval> intervals = getHeadersOrFootersIntervals(textNodes);
        for (ListInterval interval : intervals) {
            BoundingBox box = new BoundingBox();
            for (int i = interval.getStart(); i <= interval.getEnd(); i++) {
                SemanticTextNode textNode = textNodes.get(i);
                box.union(textNode.getBoundingBox());
                textNode.setSemanticType(isHeader ? SemanticType.HEADER : SemanticType.FOOTER);
            }
        }
    }

    private static Set<ListInterval> getHeadersOrFootersIntervals(List<SemanticTextNode> textNodes) {
        List<ListItemTextInfo> textChildrenInfo = new ArrayList<>(textNodes.size());
        for (int i = 0; i < textNodes.size(); i++) {
            SemanticTextNode textNode = textNodes.get(i);
            if (textNode == null) {
//                textChildrenInfo.add(new ListItemTextInfo(i, null,
//                        line, line.getValue().trim(), secondLine == null));
            } else {
                TextLine line = textNode.getFirstNonSpaceLine();
                TextLine secondLine = textNode.getNonSpaceLine(1);
                textChildrenInfo.add(new ListItemTextInfo(i, textNode.getSemanticType(),
                        line, line.getValue().trim(), secondLine == null));
            }

        }
        Set<ListInterval> intervals = getHeadersOfFooterIntervals(textChildrenInfo);
        return intervals;
    }

    public static Set<ListInterval> getHeadersOfFooterIntervals(List<ListItemTextInfo> itemsInfo) {
        ListIntervalsCollection listIntervals = new ListIntervalsCollection();
        listIntervals.putAll((new AlfaLettersListLabelsDetectionAlgorithm1()).getItemsIntervals(itemsInfo));
        listIntervals.putAll((new AlfaLettersListLabelsDetectionAlgorithm2()).getItemsIntervals(itemsInfo));
        listIntervals.putAll((new KoreanLettersListLabelsDetectionAlgorithm()).getItemsIntervals(itemsInfo));
        listIntervals.putAll((new RomanNumbersListLabelsDetectionAlgorithm()).getItemsIntervals(itemsInfo));
        ArabicNumbersListLabelsDetectionAlgorithm arabicNumbersListLabelsDetectionAlgorithm = new ArabicNumbersListLabelsDetectionAlgorithm();
        arabicNumbersListLabelsDetectionAlgorithm.isHeaderOrFooter = true;
        listIntervals.putAll((arabicNumbersListLabelsDetectionAlgorithm).getItemsIntervals(itemsInfo));
        ListIntervalsCollection correctIntervals = new ListIntervalsCollection(getEqualsItems(itemsInfo));
        for (ListInterval listInterval : listIntervals.getSet()) {
            List<String> labels = new LinkedList<>();
            for (ListItemInfo info : listInterval.getListItemsInfos()) {
                labels.add(((ListItemTextInfo)info).getListItem());
            }
            if (ListLabelsUtils.isListLabels(labels)) {
                correctIntervals.put(listInterval);
            }
        }
        return correctIntervals.getSet();
    }

    private static List<SemanticTextNode> findPossibleHeaders(List<List<IObject>> contents) {
        List<SemanticTextNode> textNodes = new LinkedList<>();
        int currentPage = 0;
        for (List<IObject> iObjects : contents) {
            for (IObject content : iObjects) {
                while (currentPage < content.getPageNumber()) {
//                textNodes.add(null);
                    currentPage++;
                }
                if (currentPage == content.getPageNumber()) {
                    if (content instanceof SemanticTextNode) {
                        SemanticTextNode textNode = (SemanticTextNode) content;
                        if (textNode.isSpaceNode() || textNode.isEmpty()) {
                            continue;
                        }
                        textNodes.add(textNode);
                        currentPage++;
                    } else if (!(content instanceof LineChunk || content instanceof LineArtChunk)) {
//                    textNodes.add(null);
                        currentPage++;
                    }
                }
            }
        }
        return textNodes;
    }

    private static List<SemanticTextNode> findPossibleFooters(List<List<IObject>> contents) {
        List<SemanticTextNode> textNodes = new LinkedList<>();
        int currentPage = 0;
        IObject previousNode = null;
        for (List<IObject> iObjects : contents) {
            for (IObject content : iObjects) {
                if (previousNode != null && previousNode.getPageNumber() < content.getPageNumber()) {
                    if (previousNode instanceof SemanticTextNode) {
                        textNodes.add((SemanticTextNode) previousNode);
                        previousNode = null;
                    }
                }
                if (content instanceof SemanticTextNode) {
                    SemanticTextNode textNode = (SemanticTextNode) content;
                    if (textNode.isSpaceNode() || textNode.isEmpty()) {
                        continue;
                    }
                }
                if (content instanceof LineChunk || content instanceof LineArtChunk) {
                    continue;
                }
                previousNode = content;
                while (currentPage < previousNode.getPageNumber()) {
//                textNodes.add(null);
                    currentPage++;
                }
            }
        }
        if (previousNode instanceof SemanticTextNode) {
            textNodes.add((SemanticTextNode)previousNode);
        }
        return textNodes;
    }

    public static Set<ListInterval> getEqualsItems(List<ListItemTextInfo> itemsInfo) {
        Set<ListInterval> listIntervals = new HashSet<>();
        String value = null;
        ListInterval interval = new ListInterval();
        for (ListItemTextInfo info : itemsInfo) {
            if (!Objects.equals(info.getListItem(), value)) {
                if (interval.getNumberOfListItems() > 1) {
                    listIntervals.add(interval);
                }
                value = info.getListItem();
                interval = new ListInterval();
            }
            interval.getListItemsInfos().add(info);
        }
        if (interval.getNumberOfListItems() > 1) {
            listIntervals.add(interval);
        }
        return listIntervals;
    }
}

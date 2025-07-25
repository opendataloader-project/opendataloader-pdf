/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.layout.processors;

import com.hancom.layout.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
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
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.*;

import java.util.*;
import java.util.stream.Collectors;

public class HeaderFooterProcessor {

    public static void processHeadersAndFooters(List<List<IObject>> contents) {
        DocumentProcessor.setIndexesForDocumentContents(contents);
        List<List<IObject>> sortedContents = new ArrayList<>();
        for (List<IObject> content : contents) {
            sortedContents.add(DocumentProcessor.sortContents(content));
        }
        List<List<IObject>> filteredSortedContents = new ArrayList<>();
        for (List<IObject> content : sortedContents) {
            filteredSortedContents.add(content.stream().filter(c -> !(c instanceof LineChunk) && !(c instanceof LineArtChunk)).collect(Collectors.toList()));
        }
        List<SemanticHeaderOrFooter> footers = getHeadersOrFooters(filteredSortedContents, false);
        List<SemanticHeaderOrFooter> headers = getHeadersOrFooters(filteredSortedContents, true);
        for (int pageNumber = 0; pageNumber < contents.size(); pageNumber++) {
            contents.set(pageNumber, updatePageContents(contents.get(pageNumber), headers.get(pageNumber), footers.get(pageNumber)));
        }
        processHeadersOrFootersContents(footers);
        processHeadersOrFootersContents(headers);
    }
    
    private static void processHeadersOrFootersContents(List<SemanticHeaderOrFooter> headersOrFooters) {
        for (SemanticHeaderOrFooter headerOrFooter : headersOrFooters) {
            if (headerOrFooter != null) {
                headerOrFooter.setContents(processHeaderOrFooterContent(headerOrFooter.getContents()));
            }
        }
    }
    
    private static List<IObject> updatePageContents(List<IObject> pageContents, SemanticHeaderOrFooter header, SemanticHeaderOrFooter footer) {
        SortedSet<Integer> headerAndFooterIndexes = new TreeSet<>();
        headerAndFooterIndexes.addAll(getHeaderOrFooterContentsIndexes(header));
        headerAndFooterIndexes.addAll(getHeaderOrFooterContentsIndexes(footer));
        if (headerAndFooterIndexes.isEmpty()) {
            return pageContents;
        }
        List<IObject> result = new ArrayList<>();
        if (header != null) {
            result.add(header);
        }
        Iterator<Integer> iterator = headerAndFooterIndexes.iterator();
        int nextHeaderOrFooterIndex = iterator.hasNext() ? iterator.next() : pageContents.size();
        for (int index = 0; index < pageContents.size(); index++) {
            if (index < nextHeaderOrFooterIndex) {
                result.add(pageContents.get(index));
            } else {
                nextHeaderOrFooterIndex = iterator.hasNext() ? iterator.next() : pageContents.size();
            }
        }
        if (footer != null) {
            result.add(footer);
        }
        return result;
    }

    private static Set<Integer> getHeaderOrFooterContentsIndexes(SemanticHeaderOrFooter header) {
        if (header == null) {
            return Collections.emptySet();
        }
        SortedSet<Integer> set = new TreeSet<>();
        for (IObject content : header.getContents()) {
            set.add(content.getIndex());
        }
        return set;
    }
    
    private static List<SemanticHeaderOrFooter> getHeadersOrFooters(List<List<IObject>> sortedContents, boolean isHeaderDetection) {
        List<SemanticHeaderOrFooter> headersOrFooters = new ArrayList<>(sortedContents.size());
        List<Integer> numberOfHeaderOrFooterContentsForEachPage = getNumberOfHeaderOrFooterContentsForEachPage(sortedContents, isHeaderDetection);
        for (int pageNumber = 0; pageNumber < sortedContents.size(); pageNumber++) {
            Integer currentIndex = numberOfHeaderOrFooterContentsForEachPage.get(pageNumber);
            if (currentIndex == 0) {
                headersOrFooters.add(null);
                continue;
            }
            List<IObject> pageContents = sortedContents.get(pageNumber);
            List<IObject> headerContents = filterHeaderOrFooterContents(isHeaderDetection ? pageContents.subList(0, currentIndex) :
                    pageContents.subList(pageContents.size() - currentIndex, pageContents.size()), pageNumber, isHeaderDetection);
            if (headerContents.isEmpty()) {
                headersOrFooters.add(null);
                continue;
            }
            SemanticHeaderOrFooter semanticHeaderOrFooter = new SemanticHeaderOrFooter(isHeaderDetection ? SemanticType.HEADER : SemanticType.FOOTER);
            semanticHeaderOrFooter.addContents(headerContents);
            semanticHeaderOrFooter.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
            headersOrFooters.add(semanticHeaderOrFooter);
        }
        return headersOrFooters;
    }
    
    private static List<IObject> processHeaderOrFooterContent(List<IObject> contents) {
        List<IObject> newContents = ParagraphProcessor.processParagraphs(contents);
        newContents = ListProcessor.processListsFromTextNodes(newContents);
        HeadingProcessor.processHeadings(newContents);
        DocumentProcessor.setIDs(newContents);
        CaptionProcessor.processCaptions(newContents);
        return newContents;
    }
    
    private static List<Integer> getNumberOfHeaderOrFooterContentsForEachPage(List<List<IObject>> sortedContents, boolean isHeaderDetection) {
        List<Integer> numberOfHeaderOrFooterContentsForEachPage = new ArrayList<>(sortedContents.size());
        for (int pageNumber = 0; pageNumber < sortedContents.size(); pageNumber++) {
            numberOfHeaderOrFooterContentsForEachPage.add(0);
        }
        int currentIndex = 0;
        while (true) {
            List<IObject> contents = new ArrayList<>(sortedContents.size());
            for (int pageNumber = 0; pageNumber < sortedContents.size(); pageNumber++) {
                if (numberOfHeaderOrFooterContentsForEachPage.get(pageNumber) != currentIndex) {
                    contents.add(null);
                    continue;
                }
                List<IObject> pageContents = sortedContents.get(pageNumber);
                int index = isHeaderDetection ? currentIndex : pageContents.size() - 1 - currentIndex;
                if (index >= 0 && index < pageContents.size()) {
                    contents.add(pageContents.get(index));
                } else {
                    contents.add(null);
                }
            }
            Set<Integer> newIndexes = getIndexesOfHeaderOrFootersContents(contents);
            if (newIndexes.isEmpty()) {
                break;
            }
            for (Integer newIndex : newIndexes) {
                numberOfHeaderOrFooterContentsForEachPage.set(newIndex, currentIndex + 1);
            }
            currentIndex++;
        }
        return numberOfHeaderOrFooterContentsForEachPage;
    }
    
    private static Set<Integer> getIndexesOfHeaderOrFootersContents(List<IObject> contents) {
        Set<Integer> result = new HashSet<>(contents.size());
        for (int pageNumber = 0; pageNumber < contents.size() - 1; pageNumber++) {
            IObject currentObject = contents.get(pageNumber);
            IObject nextObject = contents.get(pageNumber + 1);
            if (currentObject != null && nextObject != null) {
                if (arePossibleHeadersOrFooters(currentObject, nextObject, 1)) {
                    result.add(pageNumber);
                    result.add(pageNumber + 1);
                }
            }
        }
        //2-page style
        for (int pageNumber = 0; pageNumber < contents.size() - 2; pageNumber++) {
            IObject currentObject = contents.get(pageNumber);
            IObject nextObject = contents.get(pageNumber + 2);
            if (currentObject != null && nextObject != null) {
                if (arePossibleHeadersOrFooters(currentObject, nextObject, 2)) {
                    result.add(pageNumber);
                    result.add(pageNumber + 2);
                }
            }
        }
        return result;
    }

    public static boolean isHeaderOrFooter(IObject content) {
        if (content instanceof INode) {
            INode node = (INode) content;
            if (node.getSemanticType() == SemanticType.HEADER || node.getSemanticType() == SemanticType.FOOTER) {
                return true;
            }
        }
        return false;
    }

    private static List<IObject> filterHeaderOrFooterContents(List<IObject> contents, int pageNumber, boolean isHeaderDetection) {
        BoundingBox boundingBox = DocumentProcessor.getPageBoundingBox(pageNumber);
        if (boundingBox == null) {
            return contents;
        }
        List<IObject> result = new ArrayList<>();
        for (IObject content : contents) {
            if (isHeaderDetection) {
                if (content.getBottomY() < boundingBox.getCenterY()) {
                    continue;
                }
            } else {
                if (content.getTopY() > boundingBox.getCenterY()) {
                    continue;
                }
            }
            result.add(content);
        }
        return result;
    }
    
    private static boolean arePossibleHeadersOrFooters(IObject object1, IObject object2, int increment) {
        if (object1 instanceof TextLine && object2 instanceof TextLine) {
            if (!BoundingBox.areOverlapsBoundingBoxesExcludingPages(object1.getBoundingBox(), object2.getBoundingBox())) {
                return false;
            }
            TextLine line1 = (TextLine) object1;
            TextLine line2 = (TextLine) object2;
            if (!NodeUtils.areCloseNumbers(line1.getFontSize(), line2.getFontSize())) {
                return false;
            }
            SemanticTextNode textNode1 = new SemanticTextNode();
            textNode1.add(line1);
            SemanticTextNode textNode2 = new SemanticTextNode();
            textNode2.add(line2);
            //todo check boundingBoxes
            if (Objects.equals(textNode1.getValue(), textNode2.getValue())) {
                return true;
            }
            List<SemanticTextNode> textNodes = new ArrayList<>(2);
            textNodes.add(textNode1);
            textNodes.add(textNode2);
            if (getHeadersOrFootersIntervals(textNodes, increment).size() == 1) {
                return true;
            }
        } else {
            if (BoundingBox.areSameBoundingBoxesExcludingPages(object1.getBoundingBox(), object2.getBoundingBox())) {
                return true;
            }
        }
        return false;
    }

    private static Set<ListInterval> getHeadersOrFootersIntervals(List<SemanticTextNode> textNodes, int increment) {
        List<ListItemTextInfo> textChildrenInfo = new ArrayList<>(textNodes.size());
        for (int i = 0; i < textNodes.size(); i++) {
            SemanticTextNode textNode = textNodes.get(i);
            TextLine line = textNode.getFirstNonSpaceLine();
            TextLine secondLine = textNode.getNonSpaceLine(1);
            textChildrenInfo.add(new ListItemTextInfo(i, textNode.getSemanticType(), 
                    line, line.getValue().trim(), secondLine == null));
        }
        Set<ListInterval> intervals = getHeadersOfFooterIntervals(textChildrenInfo, increment);
        return intervals;
    }

    private static Set<ListInterval> getHeadersOfFooterIntervals(List<ListItemTextInfo> itemsInfo, int increment) {
        ListIntervalsCollection listIntervals = new ListIntervalsCollection();
        listIntervals.putAll((new AlfaLettersListLabelsDetectionAlgorithm1(increment)).getItemsIntervals(itemsInfo));
        listIntervals.putAll((new AlfaLettersListLabelsDetectionAlgorithm2(increment)).getItemsIntervals(itemsInfo));
        listIntervals.putAll((new KoreanLettersListLabelsDetectionAlgorithm(increment)).getItemsIntervals(itemsInfo));
        listIntervals.putAll((new RomanNumbersListLabelsDetectionAlgorithm(increment)).getItemsIntervals(itemsInfo));
        ArabicNumbersListLabelsDetectionAlgorithm arabicNumbersListLabelsDetectionAlgorithm = new ArabicNumbersListLabelsDetectionAlgorithm(increment);
        arabicNumbersListLabelsDetectionAlgorithm.setHeaderOrFooterDetection(true);
        listIntervals.putAll((arabicNumbersListLabelsDetectionAlgorithm).getItemsIntervals(itemsInfo));
        ListIntervalsCollection correctIntervals = new ListIntervalsCollection(getEqualsItems(itemsInfo));
        for (ListInterval listInterval : listIntervals.getSet()) {
            List<String> labels = new LinkedList<>();
            for (ListItemInfo info : listInterval.getListItemsInfos()) {
                labels.add(((ListItemTextInfo)info).getListItem());
            }
            if (ListLabelsUtils.isListLabels(labels, increment)) {
                correctIntervals.put(listInterval);
            }
        }
        return correctIntervals.getSet();
    }

    private static Set<ListInterval> getEqualsItems(List<ListItemTextInfo> itemsInfo) {
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

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

import org.verapdf.wcag.algorithms.entities.*;
import org.verapdf.wcag.algorithms.entities.content.*;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.tocs.TOCIInfo;
import org.verapdf.wcag.algorithms.semanticalgorithms.tocs.TOCInterval;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.TOCDetectionConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;

public class TableOfContentsProcessor {

    private static final double TOC_ITEM_X_INTERVAL_RATIO = 0.3;

    private final Set<String> pageLabels = new HashSet<>();

    public void processTableOfContents(List<List<IObject>> contents) {
        pageLabels.clear();
        calculatePageLabels();
        List<TOCInterval> intervalsList = getTocIntervals(contents);
        for (TOCInterval interval : intervalsList) {
            for (TOCIInfo info : interval.getTOCItemsInfos()) {
                info.getTOCItemValue().setListLine(true);
            }
        }
        for (TOCInterval interval : intervalsList) {
            Integer currentPageNumber = interval.getTOCItemsInfos().get(0).getPageNumber();
            int index = 0;
            SemanticTOC previousTOC = null;
            for (int i = 0; i < interval.getNumberOfTOCItems(); i++) {
                TOCIInfo currentInfo = interval.getTOCItemsInfos().get(i);
                if (!Objects.equals(currentInfo.getPageNumber(), currentPageNumber)) {
                    SemanticTOC toc = calculateTableOfContents(interval, index, i - 1,
                        contents.get(currentPageNumber));
                    processTOC(previousTOC, toc);
                    currentPageNumber = currentInfo.getPageNumber();
                    index = i;
                    previousTOC = toc;
                }
            }
            SemanticTOC toc = calculateTableOfContents(interval, index, interval.getNumberOfTOCItems() - 1,
                contents.get(currentPageNumber));
            processTOC(previousTOC, toc);
        }
        contents.replaceAll(DocumentProcessor::removeNullObjectsFromList);
    }

    private static void processTOC(SemanticTOC previousTOC, SemanticTOC toc) {
        for (IObject tocItem : toc.getTOCItems()) {
            if (tocItem instanceof SemanticTOCI) {
                SemanticTOCI toci = (SemanticTOCI) tocItem;
                toci.setContents(processTOCItemContent(toci.getContents()));
            } else {
                //
            }
        }
        if (previousTOC != null) {
            SemanticTOC.setTOCConnected(previousTOC, toc);
        }
    }

    private void calculatePageLabels() {
        for (int pageNumber = 0; pageNumber < StaticContainers.getDocument().getNumberOfPages(); pageNumber++) {
            String pageLabel = StaticContainers.getDocument().getPage(pageNumber).getPageLabel();
            if (pageLabel != null) {
                pageLabels.add(pageLabel);
            }
        }
    }

    private static List<IObject> processTOCItemContent(List<IObject> contents) {
        List<IObject> newContents = ParagraphProcessor.processParagraphs(contents);
        DocumentProcessor.setIDs(newContents);
        return newContents;
    }

    private List<TOCInterval> getTocIntervals(List<List<IObject>> contents) {
        List<TOCInterval> tocIntervals = new ArrayList<>();
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
                TOCIInfo tocItemTextInfo = createTOCIInfo(i, line, value);
                processTOCItem(tocIntervals, tocItemTextInfo);
            }
        }
        LinkedHashSet<TOCInterval> intervalsList = new LinkedHashSet<>();
        for (TOCInterval interval : tocIntervals) {
            if (interval != null && interval.getTOCItemsInfos().size() > 2) {
                intervalsList.add(interval);
            }
        }
        List<TOCInterval> result = new ArrayList<>(intervalsList);
        Collections.reverse(result);
        return result;
    }

    private void processTOCItem(List<TOCInterval> tocIntervals, TOCIInfo tocItemTextInfo) {
        if (!hasPageNumber(tocItemTextInfo)) {
            return;
        }
        boolean isSingle = true;
        if (!tocIntervals.isEmpty()) {
            TOCInterval interval = tocIntervals.get(tocIntervals.size() - 1);
            if (isTwoTOCItemsOfOneTOC(interval, tocItemTextInfo)) {
                tocIntervals.add(interval);
                isSingle = false;
            }
        }
        if (isSingle) {
            TOCInterval tocInterval = new TOCInterval();
            tocInterval.getTOCItemsInfos().add(tocItemTextInfo);
            tocIntervals.add(tocInterval);
        }
    }

    private static boolean isTwoTOCItemsOfOneTOC(TOCInterval interval, TOCIInfo currentItem) {
        double maxXGap = getMaxXGap(currentItem.getTOCItemValue().getFontSize());
        TOCIInfo previousItem = interval.getLastTOCItemInfo();
        if (previousItem == null) {
            return false;
        }
        if (Objects.equals(currentItem.getPageNumber(), previousItem.getPageNumber()) &&
            currentItem.getTOCItemValue().getTopY() > previousItem.getTOCItemValue().getTopY()) {
            return false;
        }
        if (!NodeUtils.areCloseNumbers(previousItem.getTOCItemValue().getRightX(),
            currentItem.getTOCItemValue().getRightX(), maxXGap)) {
            return false;
        }
        interval.getTOCItemsInfos().add(currentItem);
        return true;
    }

    private boolean hasPageNumber(TOCIInfo tocItem) {
        String textValue = tocItem.getText();
        if (textValue.matches(".+[" + TOCDetectionConsumer.SPACES + "\\.]\\d+") && !textValue.matches(".*\\d+\\.\\d+")) {
            return true;
        }
        for (String pageLabel : pageLabels) {
            if (textValue.length() > pageLabel.length() && textValue.endsWith(pageLabel)) {
                return true;
            }
        }
        return false;
    }

    private static TOCIInfo createTOCIInfo(int i, TextLine line, String value) {
        TOCIInfo tociInfo = new TOCIInfo();
        tociInfo.setIndex(i);
        tociInfo.setTOCItemValue(line);
        tociInfo.setText(value);
        return tociInfo;
    }

    private static SemanticTOC calculateTableOfContents(TOCInterval interval, int startIndex, int endIndex,
                                                        List<IObject> pageContents) {
        SemanticTOC toc = new SemanticTOC();
        for (int index = startIndex; index <= endIndex; index++) {
            TOCIInfo currentInfo = interval.getTOCItemsInfos().get(index);
            SemanticTOCI tocItem = new SemanticTOCI(new BoundingBox(), null);
            TextLine textLine = (TextLine) pageContents.get(currentInfo.getIndex());
            if (index != startIndex) {
                int nextIndex = interval.getTOCItemsInfos().get(index - 1).getIndex();
                addContentToTOCItem(nextIndex, currentInfo, pageContents, tocItem);
            } else {
                //
            }
            pageContents.set(interval.getTOCItemsInfos().get(index).getIndex(), index == startIndex ? toc : null);
            tocItem.add(textLine);
            toc.add(tocItem);
        }
        return toc;
    }

    private static void addContentToTOCItem(int nextIndex, TOCIInfo currentInfo, List<IObject> pageContents,
                                             SemanticTOCI tocItem) {

        List<TextLine> lines = new LinkedList<>();
        for (int index = currentInfo.getIndex() - 1; index > nextIndex; index--) {
            IObject content = pageContents.get(index);
            if (content instanceof TextLine) {
                TextLine currentTextLine = (TextLine) content;
                lines.add(0, currentTextLine);
            } else if (content != null) {
                tocItem.getContents().add(0, content);
            }
            pageContents.set(index, null);
        }
        tocItem.add(lines);
    }

    private static double getMaxXGap(double fontSize) {
        return fontSize * TOC_ITEM_X_INTERVAL_RATIO;
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.utils.BulletedParagraphUtils;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.text.TextStyle;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;

public class HeadingProcessor {

    private static final double HEADING_PROBABILITY = 0.75;
    private static final double BULLETED_HEADING_PROBABILITY = 0.1;

    public static void processHeadings(List<IObject> contents) {
        List<SemanticTextNode> textNodes = new LinkedList<>();
        for (IObject content : contents) {
            processContent(textNodes, content);
        }
        for (int index = 0; index < textNodes.size() - 1; index++) {
            SemanticTextNode textNode = textNodes.get(index);
            if (textNode.getSemanticType() == SemanticType.HEADING) {
                continue;
            }
            double probability = NodeUtils.headingProbability(textNode,
                    index != 0 ? textNodes.get(index - 1) : null,
                    textNodes.get(index + 1), textNodes.get(index));
            if (BulletedParagraphUtils.isBulletedParagraph(textNode)) {
                probability += BULLETED_HEADING_PROBABILITY;
            }
            if (probability > HEADING_PROBABILITY && textNode.getSemanticType() != SemanticType.LIST) {
                textNode.setSemanticType(SemanticType.HEADING);
            }
        }
        setHeadings(contents);
    }

    private static void processContent(List<SemanticTextNode> textNodes, IObject content) {
        if (content instanceof SemanticTextNode) {
            SemanticTextNode textNode = (SemanticTextNode) content;
            if (!textNode.isSpaceNode()) {
                textNodes.add(textNode);
            }
        } else if (content instanceof TableBorder) {
            TableBorder table = (TableBorder) content;
            if (table.isTextBlock()) {
                List<IObject> contents = table.getCell(0, 0).getContents();
                for (IObject textBlockContent : contents) {
                    processContent(textNodes, textBlockContent);
                }
            }
        } else if (content instanceof PDFList) {
            PDFList list = (PDFList) content;
            SemanticTextNode textNode = new SemanticTextNode();
            textNode.add(list.getListItems().get(0).getFirstLine());
        }
    }

    private static void setHeadings(List<IObject> contents) {
        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (content instanceof SemanticTextNode && ((INode) content).getSemanticType() == SemanticType.HEADING && !(content instanceof SemanticHeading)) {
                SemanticHeading heading = new SemanticHeading((SemanticTextNode) content);
                contents.set(index, heading);
                StaticLayoutContainers.getHeadings().add(heading);
            }
            if (content instanceof TableBorder) {
                TableBorder table = (TableBorder) content;
                if (table.isTextBlock()) {
                    List<IObject> textBlockContents = table.getCell(0, 0).getContents();
                    setHeadings(textBlockContents);
                }
            }
        }
    }

    public static void detectHeadingsLevels() {
        SortedMap<TextStyle, Set<SemanticHeading>> map = new TreeMap<>();
        List<SemanticHeading> headings = StaticLayoutContainers.getHeadings();
        for (SemanticHeading heading : headings) {
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
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticFigure;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.Table;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ClusterTableConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.LinkedList;
import java.util.List;

public class ClusterTableProcessor {

    public static void processClusterDetectionLists(List<IObject> contents) {
        StaticContainers.setIsDataLoader(false);
        ClusterTableConsumer clusterTableConsumer = new ClusterTableConsumer();
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    continue;
                }
                SemanticTextNode semanticTextNode = new SemanticTextNode(textChunk);
                clusterTableConsumer.accept(new TableToken(textChunk, semanticTextNode), semanticTextNode);
//            } else if (content instanceof ImageChunk) {
//                SemanticFigure semanticFigure = new SemanticFigure((ImageChunk) content);
//                clusterTableConsumer.accept(new TableToken((ImageChunk) content, semanticFigure), semanticFigure);
            }
        }
        clusterTableConsumer.processEnd();
//        for (PDFList list : clusterTableConsumer.getLists()) {
//            replaceContentsToResult(contents, list);
//        }
        for (Table table : clusterTableConsumer.getTables()) {
            replaceContentsToResult(contents, table);
//            String value = "Table: " + table.getNumberOfColumns() + " columns, " + table.getNumberOfRows() + " rows";
//            map.put(table, new Info(value, getColor(SemanticType.TABLE)));
        }
        if (clusterTableConsumer.getTables().size() > 0) {
            System.out.println("Cluster tables number: " + clusterTableConsumer.getTables().size());
        }
        StaticContainers.setIsDataLoader(true);
    }

//    public static void findListAndTablesImageMethod(List<SemanticTextNode> nodes) {
//        ClusterTableConsumer clusterTableConsumer = new ClusterTableConsumer();
//        for (SemanticTextNode textNode : nodes) {
//            ImageChunk imageChunk = new ImageChunk(textNode.getBoundingBox());
//            SemanticFigure figure = new SemanticFigure(imageChunk);
//            clusterTableConsumer.accept(new TableToken(imageChunk, figure), figure);
////            if (chunk instanceof TextChunk) {
////                SemanticTextNode semanticTextNode = new SemanticTextNode((TextChunk)chunk);
////                clusterTableConsumer.accept(new TableToken((TextChunk)chunk, semanticTextNode), semanticTextNode);
////            } else if (chunk instanceof ImageChunk) {
////                SemanticFigure semanticFigure = new SemanticFigure((ImageChunk) chunk);
////                clusterTableConsumer.accept(new TableToken((ImageChunk)chunk, semanticFigure), semanticFigure);
////            }
//
//        }
//        //        if (recognitionArea.isValid()) {
////            List<INode> restNodes = new ArrayList<>(recognize());
////            init();
////            restNodes.add(root);
////            for (INode restNode : restNodes) {
////                accept(restNode);
////            }
////        }
//        clusterTableConsumer.processEnd();
//        System.out.println("test");
//    }

    private static void replaceContentsToResult(List<IObject> contents, IObject result) {
        List<IObject> replacedContents = new LinkedList<>();
        Integer index = null;
        int i = 0;
        for (IObject content : contents) {
            if (contains(result.getBoundingBox(), content.getBoundingBox())) {
//            if (content.getBoundingBox().getIntersectionPercent(result.getBoundingBox()) > 0.5) {
                replacedContents.add(content);
                if (index == null) {
                    index = i;
                }
            }
            i++;
        }
        if (index == null) {
            return;
        }
        contents.set(index, result);
        contents.removeAll(replacedContents);
    }

    public static boolean contains(BoundingBox box, BoundingBox box2) {
        if (box instanceof MultiBoundingBox) {
            for (BoundingBox b : ((MultiBoundingBox) box).getBoundingBoxes()) {
                if (b.contains(box2, 0.6, 0.6)) {
                    return true;
                }
            }
        } else {
            return box.contains(box2, 0.6, 0.6);
        }
        return false;
    }
}

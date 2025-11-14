/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.Table;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ClusterTableConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class ClusterTableProcessor {

    private static List<TextChunk> splitTextChunk(TextChunk originalChunk) {
        String text = originalChunk.getValue();
        List<Double> symbolEnds = originalChunk.getSymbolEnds();

        if (symbolEnds == null || symbolEnds.size() < text.length() || text == null || text.trim().isEmpty()) {
            List<TextChunk> result = new ArrayList<>();
            result.add(originalChunk);
            return result;
        }

        List<Integer> columnBoundaries = findColumnBoundaries(originalChunk, text);

        if (columnBoundaries.size() <= 1) {
            List<TextChunk> result = new ArrayList<>();
            result.add(originalChunk);
            return result;
        }

        return createColumnsFromBoundaries(originalChunk, text, columnBoundaries);
    }

    private static List<Integer> findColumnBoundaries(TextChunk chunk, String text) {
        List<Integer> boundaries = new ArrayList<>();
        boundaries.add(0);

        List<Double> spaceWidths = new ArrayList<>();
        List<Integer> spacePositions = new ArrayList<>();

        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ' ') {
                Double width = chunk.getSymbolWidth(i);
                if (width != null) {
                    spaceWidths.add(width);
                    spacePositions.add(i);
                }
            }
        }

        if (spaceWidths.isEmpty()) {
            return boundaries;
        }

        double threshold = chunk.getFontSize() * 0.77;

        for (int i = 0; i < spaceWidths.size(); i++) {
            if (spaceWidths.get(i) > threshold) {
                boundaries.add(spacePositions.get(i));
            }
        }

        boundaries.add(text.length());

        return boundaries;
    }

    private static List<TextChunk> createColumnsFromBoundaries(TextChunk originalChunk, String text, List<Integer> boundaries) {
        List<TextChunk> columns = new ArrayList<>();
        BoundingBox originalBBox = originalChunk.getBoundingBox();

        for (int i = 0; i < boundaries.size() - 1; i++) {
            int start = boundaries.get(i);
            int end = boundaries.get(i + 1);

            if (start >= end) continue;

            String columnText = text.substring(start, end).trim();
            if (columnText.isEmpty()) continue;

            double startX = getSymbolStart(originalChunk, start, originalBBox.getLeftX());
            double endX = getSymbolEnd(originalChunk, end - 1, originalBBox.getRightX());

            BoundingBox columnBBox = new BoundingBox(
                startX,
                originalBBox.getBottomY(),
                endX,
                originalBBox.getTopY()
            );

            TextChunk columnChunk = new TextChunk(
                columnBBox,
                columnText,
                originalChunk.getFontSize(),
                originalChunk.getBaseLine()
            );

            columns.add(columnChunk);
        }

        return columns;
    }

    private static double getSymbolStart(TextChunk chunk, int charIndex, double defaultStart) {
        if (charIndex == 0) {
            return defaultStart;
        }
        if (charIndex - 1 < chunk.getSymbolEnds().size()) {
            return chunk.getSymbolEnds().get(charIndex - 1);
        }
        return defaultStart;
    }

    private static double getSymbolEnd(TextChunk chunk, int charIndex, double defaultEnd) {
        if (charIndex < chunk.getSymbolEnds().size()) {
            return chunk.getSymbolEnds().get(charIndex);
        }
        return defaultEnd;
    }


    public static void processClusterDetectionLists(List<IObject> contents) {
        StaticContainers.setIsDataLoader(false);
        ClusterTableConsumer clusterTableConsumer = new ClusterTableConsumer();
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    continue;
                }
                List<TextChunk> splitChunks = splitTextChunk(textChunk);
//                SemanticTextNode semanticTextNode = new SemanticTextNode(textChunk);
//                clusterTableConsumer.accept(new TableToken(textChunk, semanticTextNode), semanticTextNode);
                for (TextChunk splitChunk : splitChunks) {
                    SemanticTextNode semanticTextNode = new SemanticTextNode(splitChunk);
                    semanticTextNode.setSemanticType(SemanticType.TABLE_CELL);
                    clusterTableConsumer.accept(new TableToken(splitChunk, semanticTextNode), semanticTextNode);
                }
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

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
import org.verapdf.wcag.algorithms.entities.tables.Table;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ClusterTableConsumer;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

import java.util.ArrayList;
import java.util.List;

public class ClusterTableProcessor extends AbstractTableProcessor {

    @Override
    protected List<List<TableBorder>> getTables(List<List<IObject>> contents, List<Integer> pageNumbers) {
        List<List<TableBorder>> tables = new ArrayList<>();
        for (int pageNumber : pageNumbers) {
            tables.add(processClusterDetectionTables(contents.get(pageNumber)));
        }
        return tables;
    }

    public static List<TableBorder> processClusterDetectionTables(List<IObject> contents) {
        ClusterTableConsumer clusterTableConsumer = new ClusterTableConsumer();
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                if (textChunk.isWhiteSpaceChunk() || textChunk.isEmpty()) {
                    continue;
                }
                List<TextChunk> splitChunks = TextChunkUtils.splitTextChunkByWhiteSpaces(textChunk);
                for (TextChunk splitChunk : splitChunks) {
                    SemanticTextNode semanticTextNode = new SemanticTextNode(splitChunk);
                    clusterTableConsumer.accept(new TableToken(splitChunk, semanticTextNode), semanticTextNode);
                }
//            } else if (content instanceof ImageChunk) {
//                SemanticFigure semanticFigure = new SemanticFigure((ImageChunk) content);
//                clusterTableConsumer.accept(new TableToken((ImageChunk) content, semanticFigure), semanticFigure);
            }
        }
        clusterTableConsumer.processEnd();
        List<TableBorder> result = new ArrayList<>();
        for (Table table : clusterTableConsumer.getTables()) {
            TableBorder tableBorder = table.createTableBorderFromTable();
            if (tableBorder != null) {
                result.add(tableBorder);
            }
        }
        return result;
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
}

/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticFigure;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ClusterTableConsumer;

import java.util.List;

public class ClusterTableProcessor {

    public static void processClusterDetectionLists(List<IObject> contents) {
        ClusterTableConsumer clusterTableConsumer = new ClusterTableConsumer();
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                SemanticTextNode semanticTextNode = new SemanticTextNode((TextChunk)content);
                clusterTableConsumer.accept(new TableToken((TextChunk)content, semanticTextNode), semanticTextNode);
            } else if (content instanceof ImageChunk) {
                SemanticFigure semanticFigure = new SemanticFigure((ImageChunk) content);
                clusterTableConsumer.accept(new TableToken((ImageChunk)content, semanticFigure), semanticFigure);
            }
        }
        clusterTableConsumer.processEnd();
        for (PDFList list : clusterTableConsumer.getLists()) {
            DocumentProcessor.replaceContentsToResult(contents, list);
        }
//        for (Table table : clusterTableConsumer.getTables()) {
//            replaceContentsToResult(contents, table);
//            String value = "Table: " + table.getNumberOfColumns() + " columns, " + table.getNumberOfRows() + " rows";
//            map.put(table, new Info(value, getColor(SemanticType.TABLE)));
//        }
//        System.out.println("Cluster tables number: " + clusterTableConsumer.getTables().size());
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

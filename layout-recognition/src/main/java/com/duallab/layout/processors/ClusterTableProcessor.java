package com.duallab.layout.processors;

import com.duallab.layout.ContentInfo;
import com.duallab.layout.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticFigure;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.TableToken;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ClusterTableConsumer;

import java.util.List;

import com.duallab.layout.pdf.PDFWriter;

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
            String value = String.format("List: number of items %s", list.getNumberOfListItems());
            StaticLayoutContainers.getContentInfoMap().put(list, new ContentInfo(value, PDFWriter.getColor(SemanticType.LIST)));
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

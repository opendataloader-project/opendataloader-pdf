package com.duallab.layout.processors;

import com.duallab.layout.Info;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.LinkedList;
import java.util.List;

public class HeadingProcessor {

    public static void processHeadings(List<IObject> contents) {
        List<SemanticTextNode> textNodes = new LinkedList<>();
        for (IObject content : contents) {
            if (content instanceof SemanticTextNode) {
                SemanticTextNode textNode = (SemanticTextNode) content;
                if (!textNode.isSpaceNode()) {
                    textNodes.add(textNode);
                }
            }
        }
        for (int index = 0; index < textNodes.size() - 1; index++) {
            SemanticTextNode textNode = textNodes.get(index);
            double probability = NodeUtils.headingProbability(textNode,
                    index != 0 ? textNodes.get(index - 1) : null,
                    textNodes.get(index + 1) , textNodes.get(index));
            if (probability > 0.75 && textNode.getSemanticType() != SemanticType.LIST) {
                textNode.setSemanticType(SemanticType.HEADING);
                StaticLayoutContainers.getMap().put(textNode, new Info(DocumentProcessor.getContentsValueForTextNode(textNode), PDFWriter.getColor(SemanticType.HEADING)));
            }
        }
    }
}

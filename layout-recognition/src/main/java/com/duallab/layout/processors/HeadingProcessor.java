package com.duallab.layout.processors;

import com.duallab.layout.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.INode;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.text.TextStyle;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

import java.util.*;

public class HeadingProcessor {

    private static final double HEADING_PROBABILITY = 0.75;

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
            if (probability > HEADING_PROBABILITY && textNode.getSemanticType() != SemanticType.LIST) {
                textNode.setSemanticType(SemanticType.HEADING);
            }
        }
        for (int index = 0; index < contents.size(); index++) {
            IObject content = contents.get(index);
            if (content instanceof SemanticTextNode && ((INode)content).getSemanticType() == SemanticType.HEADING) {
                SemanticHeading heading = new SemanticHeading((SemanticTextNode) content);
                contents.set(index, heading);
                StaticLayoutContainers.getHeadings().add(heading);
            }
        }
    }

    public static void detectHeadingsLevels(List<List<IObject>> contents) {
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

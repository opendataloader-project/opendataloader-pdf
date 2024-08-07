package com.duallab.layout.processors;

import com.duallab.layout.Info;
import com.duallab.layout.containers.StaticLayoutContainers;
import com.duallab.layout.pdf.PDFWriter;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;

import java.util.List;

public class ImageProcessor {

    public static void processImages(List<IObject> contents) {
        for (IObject content : contents) {
            if (content instanceof ImageChunk /*|| content instanceof LineChunk*/) {
                String value = String.format("Image: height %.2f, width %.2f", content.getHeight(), content.getWidth());
                StaticLayoutContainers.getMap().put(content, new Info(value, PDFWriter.getColor(SemanticType.FIGURE)));
            }
            if (content instanceof LineArtChunk) {
                String value = String.format("Line Art: height %.2f, width %.2f", content.getHeight(), content.getWidth());
                StaticLayoutContainers.getMap().put(content, new Info(value, PDFWriter.getColor(SemanticType.PART)));
            }
        }
    }
}

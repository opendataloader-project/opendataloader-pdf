package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class HiddenTextProcessor {

    public static List<TextChunk> findHiddenText(String pdfName, List<IObject> contents) throws IOException {
        List<TextChunk> textChunks = new LinkedList<>();
        try (ContrastRatioConsumer contrastRatioConsumer = new ContrastRatioConsumer(pdfName)) {
            for (IObject content : contents) {
                if (content instanceof TextChunk) {
                    TextChunk textChunk = (TextChunk)content;
                    contrastRatioConsumer.calculateContrastRatio(textChunk);
                    if (textChunk.getContrastRatio() < 1.2d) {
                        textChunks.add(textChunk);
                    }
                }
            }
        }
        return textChunks;
    }
}

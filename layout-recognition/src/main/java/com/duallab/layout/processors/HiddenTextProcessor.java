/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.duallab.layout.processors;

import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class HiddenTextProcessor {

    private static final double MIN_CONTRAST_RATIO = 1.2d;
    
    public static List<TextChunk> findHiddenText(String pdfName, List<IObject> contents, String password) throws IOException {
        List<TextChunk> textChunks = new LinkedList<>();
        try (ContrastRatioConsumer contrastRatioConsumer = new ContrastRatioConsumer(pdfName, password)) {
            for (IObject content : contents) {
                if (content instanceof TextChunk) {
                    TextChunk textChunk = (TextChunk)content;
                    contrastRatioConsumer.calculateContrastRatio(textChunk);
                    if (textChunk.getContrastRatio() < MIN_CONTRAST_RATIO) {
                        textChunks.add(textChunk);
                    }
                }
            }
        }
        return textChunks;
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import java.util.LinkedList;
import java.util.List;

/**
 * Processor for detecting hidden text in PDF documents.
 * Identifies text with low contrast ratio against the background.
 */
public class HiddenTextProcessor {
    private static final double MIN_CONTRAST_RATIO = 1.2d;

    /**
     * Finds and marks or filters hidden text based on contrast ratio.
     *
     * @param pdfName the path to the PDF file
     * @param contents the page contents to process
     * @param isFilterHiddenText whether to filter out hidden text or just mark it
     * @param password the PDF password if required
     * @return the processed list of content objects
     */
    public static List<IObject> findHiddenText(String pdfName, List<IObject> contents, boolean isFilterHiddenText,
                                               String password) {
        List<IObject> result = new LinkedList<>();
        ContrastRatioConsumer contrastRatioConsumer = StaticLayoutContainers.getContrastRatioConsumer(pdfName, password, false, null);
        if (contrastRatioConsumer == null) {
            return contents;
        }
        for (IObject content : contents) {
            if (content instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) content;
                contrastRatioConsumer.calculateContrastRatio(textChunk);
                if (textChunk.getContrastRatio() < MIN_CONTRAST_RATIO) {
                    if (!isFilterHiddenText) {
                        textChunk.setHiddenText(true);
                    } else {
                        continue;
                    }
                }
            }
            result.add(content);
        }
        return result;
    }
}

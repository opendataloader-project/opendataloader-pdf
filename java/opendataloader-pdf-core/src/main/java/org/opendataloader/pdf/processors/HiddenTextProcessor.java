/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

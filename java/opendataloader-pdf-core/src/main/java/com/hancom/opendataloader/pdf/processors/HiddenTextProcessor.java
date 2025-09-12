/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.processors;

import com.hancom.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.semanticalgorithms.consumers.ContrastRatioConsumer;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class HiddenTextProcessor {

    private static final double MIN_CONTRAST_RATIO = 1.2d;

    public static List<IObject> findHiddenText(String pdfName, List<IObject> contents, String password) throws IOException {
        List<IObject> result = new LinkedList<>();
        try (ContrastRatioConsumer contrastRatioConsumer = new ContrastRatioConsumer(pdfName, password, false, null)) {
            for (IObject content : contents) {
                if (content instanceof TextChunk) {
                    TextChunk textChunk = (TextChunk) content;
                    contrastRatioConsumer.calculateContrastRatio(textChunk);
                    if (textChunk.getContrastRatio() < MIN_CONTRAST_RATIO) {
                        if (!StaticLayoutContainers.getFilterHiddenText()) {
                            textChunk.setHiddenText(true);
                        } else {
                            continue;
                        }
                    }
                }
                result.add(content);
            }
        }
        return result;
    }
}

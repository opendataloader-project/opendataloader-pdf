/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.IChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processor for filtering and cleaning PDF content.
 * Removes hidden text, out-of-page content, backgrounds, and other artifacts.
 */
public class ContentFilterProcessor {

    private static final Logger LOGGER = Logger.getLogger(ContentFilterProcessor.class.getCanonicalName());

    /**
     * Filters and cleans page contents based on configuration.
     *
     * @param inputPdfName the path to the PDF file
     * @param contents the raw page contents
     * @param pageNumber the page number (0-indexed)
     * @param config the configuration settings
     * @return the filtered list of content objects
     * @throws IOException if unable to process the content
     */
    public static List<IObject> getFilteredContents(String inputPdfName, List<IChunk> contents, int pageNumber,
                                                    Config config) throws IOException {
        List<IObject> pageContents = new ArrayList<>(contents);
        TextProcessor.removeSameTextChunks(pageContents);
        pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        TextProcessor.removeTextDecorationImages(pageContents);
        pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        if (config.getFilterConfig().isFilterTinyText()) {
            TextProcessor.filterTinyText(pageContents);
            pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        }
        if (config.getFilterConfig().isFilterOutOfPage()) {
            filterOutOfPageContents(pageNumber, pageContents);
            pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        }
        TextProcessor.mergeCloseTextChunks(pageContents);
        pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        TextProcessor.trimTextChunksWhiteSpaces(pageContents);
        filterConsecutiveSpaces(pageContents);
        pageContents = HiddenTextProcessor.findHiddenText(inputPdfName, pageContents,
            config.getFilterConfig().isFilterHiddenText(), config.getPassword());
        TextProcessor.replaceUndefinedCharacters(pageContents, config.getReplaceInvalidChars());
        processBackgrounds(pageNumber, pageContents);
        return pageContents;
    }

    /**
     * Detects and removes background elements from page contents.
     *
     * @param pageNumber the page number (0-indexed)
     * @param contents the page contents to process
     */
    public static void processBackgrounds(int pageNumber, List<IObject> contents) {
        BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(pageNumber);
        if (pageBoundingBox == null) {
            return;
        }
        Set<LineArtChunk> backgrounds = new HashSet<>();
        for (IObject content : contents) {
            if (content instanceof LineArtChunk) {
                if (isBackground(content, pageBoundingBox)) {
                    backgrounds.add((LineArtChunk) content);
                }
            }
        }
        if (!backgrounds.isEmpty()) {
            LOGGER.log(Level.WARNING, "Detected background on page " + pageNumber);
            contents.removeAll(backgrounds);
        }
    }

    private static void filterConsecutiveSpaces(List<IObject> pageContents) {
        for (IObject object : pageContents) {
            if (object instanceof TextChunk) {
                ((TextChunk) object).compressSpaces();
            }
        }
    }

    private static boolean isBackground(IObject content, BoundingBox pageBoundingBox) {
        return (content.getBoundingBox().getWidth() > 0.5 * pageBoundingBox.getWidth() &&
            content.getBoundingBox().getHeight() > 0.1 * pageBoundingBox.getHeight()) ||
            (content.getBoundingBox().getWidth() > 0.1 * pageBoundingBox.getWidth() &&
                content.getBoundingBox().getHeight() > 0.5 * pageBoundingBox.getHeight());
    }

    private static void filterOutOfPageContents(int pageNumber, List<IObject> contents) {
        BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(pageNumber);
        if (pageBoundingBox == null) {
            return;
        }
        pageBoundingBox.move(-pageBoundingBox.getLeftX(), -pageBoundingBox.getBottomY());
        for (int index = 0; index < contents.size(); index++) {
            IObject object = contents.get(index);
            if (object != null && pageBoundingBox.notOverlaps(object.getBoundingBox())) {
                contents.set(index, null);
            }
        }
    }
}

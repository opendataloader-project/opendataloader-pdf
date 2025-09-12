package com.hancom.opendataloader.pdf.processors;

import com.hancom.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.IChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ContentFilterProcessor {

    private static final Logger LOGGER = Logger.getLogger(ContentFilterProcessor.class.getCanonicalName());

    public static List<IObject> getFilteredContents(String inputPdfName, List<IChunk> contents, int pageNumber,
                                                    Config config) throws IOException {
        List<IObject> pageContents = new ArrayList<>(contents);
        TextProcessor.removeSameTextChunks(pageContents);
        pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        TextProcessor.removeTextDecorationImages(pageContents);
        pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        TextProcessor.trimTextChunksWhiteSpaces(pageContents);
        TextProcessor.replaceUndefinedCharacters(pageContents, config.getReplaceInvalidChars());
        pageContents = HiddenTextProcessor.findHiddenText(inputPdfName, pageContents, config.getPassword());
        processBackgrounds(pageNumber, pageContents);
        if (config.getFilterConfig().isFilterOutOfPage()) {
            filterOutOfPageContents(pageNumber, pageContents);
        }
        pageContents = DocumentProcessor.removeNullObjectsFromList(pageContents);
        return pageContents;
    }

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
        for (int index = 0; index < contents.size(); index++) {
            IObject object = contents.get(index);
            if (pageBoundingBox.notOverlaps(object.getBoundingBox())) {
                contents.set(index, null);
            }
        }
    }
}

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

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.utils.ImagesUtils;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.IChunk;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.LineArtChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.TextChunkUtils;

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
        pageContents = splitTextChunksByWhiteSpacesInPageContents(pageContents);
        // HiddenText detection moved to DocumentProcessor (sequential post-processing)
        // to avoid ContrastRatioConsumer per-thread PDF rendering overhead
        double replacementCharRatio = TextProcessor.measureReplacementCharRatio(pageContents);
        StaticLayoutContainers.setReplacementCharRatio(pageNumber, replacementCharRatio);
        if (replacementCharRatio >= 0.3) {
            LOGGER.log(Level.WARNING,
                "Page {0}: {1,number,#.#%} of characters are replacement characters (U+FFFD). "
                + "This PDF likely contains CID-keyed fonts without ToUnicode mappings. "
                + "Text extraction may be incomplete. Consider enabling hybrid OCR fallback with --hybrid docling-fast.",
                new Object[]{pageNumber + 1, replacementCharRatio});
        }
        TextProcessor.replaceUndefinedCharacters(pageContents, config.getReplaceInvalidChars());
        if (config.getFilterConfig().isFilterBackgrounds()) {
            // Must run before processBackgrounds, and while pageContents is still flat: it
            // needs to see images/text/other line art as direct siblings to tell a genuine
            // vector figure apart from a decorative background, before table/list structuring
            // moves that sibling content into nested containers.
            convertVectorFigures(pageNumber, pageContents);
            processBackgrounds(pageNumber, pageContents);
        }
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
            if (content instanceof LineArtChunk && isBackground(content, pageBoundingBox)) {
                backgrounds.add((LineArtChunk) content);
            }
        }
        if (!backgrounds.isEmpty()) {
            LOGGER.log(Level.WARNING, "Detected background on page " + (pageNumber + 1));
            contents.removeAll(backgrounds);
        }
    }

    /**
     * Minimum share of a smaller {@link LineArtChunk}'s own area that must sit
     * inside another chunk for it to count as "contained" by that chunk.
     */
    private static final double CONTAINMENT_RATIO = 0.9;

    /**
     * A containing chunk must cover less than this share of its OWN area with
     * the overlap for the relationship to count as "wrapper", not "peer". Two
     * near-duplicate chunks describing the same drawing overlap almost all of
     * both of their areas and fall above this threshold on both sides, so
     * neither is treated as the other's wrapper.
     */
    private static final double PEER_OVERLAP_RATIO = 0.75;

    /**
     * A {@link LineArtChunk} whose overlap with real text covers this share
     * (or more) of its own area is a decorative box drawn around body text,
     * not a figure.
     */
    private static final double TEXT_OVERLAP_RATIO = 0.2;

    /**
     * Converts freestanding vector-drawn figures — {@link LineArtChunk}s with
     * no raster image behind them — into images in place, since nothing else
     * turns a {@link LineArtChunk} into rendered output and {@link #isBackground}
     * would otherwise cause {@link #processBackgrounds} to silently discard the
     * large ones. Adjacent/overlapping genuine chunks (e.g. the outline and
     * fill of one drawing, stored as separate chunks) are merged into a single
     * image; see {@link ImageFigureGroupingProcessor#clusterAndReplaceWithImages}.
     *
     * @param pageNumber the page number (0-indexed)
     * @param contents the page contents to process; genuine vector figures are
     *                  replaced in place with a merged {@link ImageChunk}
     */
    private static void convertVectorFigures(int pageNumber, List<IObject> contents) {
        BoundingBox pageBoundingBox = DocumentProcessor.getPageBoundingBox(pageNumber);
        if (pageBoundingBox == null) {
            return;
        }
        List<Integer> candidateIndices = new ArrayList<>();
        for (int i = 0; i < contents.size(); i++) {
            IObject content = contents.get(i);
            if (content instanceof LineArtChunk
                    && ImagesUtils.isRenderableSize(content.getWidth(), content.getHeight())
                    && isGenuineVectorFigure((LineArtChunk) content, contents, pageBoundingBox)) {
                candidateIndices.add(i);
            }
        }
        ImageFigureGroupingProcessor.clusterAndReplaceWithImages(contents, candidateIndices, pageBoundingBox);
    }

    /**
     * A {@link LineArtChunk} is a genuine, freestanding vector figure — worth
     * rendering as an image — unless one of:
     * <ul>
     *   <li>its bounds coincide with a detected table border: it is that
     *       table's own grid, and rendering it yields a screenshot of the
     *       whole table rather than a figure;</li>
     *   <li>it overlaps an {@link ImageChunk}: a dimension line or border drawn
     *       on top of an already-extracted raster image, not a new figure;</li>
     *   <li>it substantially contains another sizeable {@link LineArtChunk} —
     *       the overlap covers almost all of the other chunk
     *       ({@link #CONTAINMENT_RATIO}) but only a modest share of this one
     *       ({@link #PEER_OVERLAP_RATIO}) — meaning this chunk is itself just a
     *       wrapper drawn around separately meaningful content, such as the
     *       outline of a table whose cells hold the real drawings and text.
     *       (Two near-duplicate chunks describing the same drawing overlap
     *       most of both of their areas and are left alone here as peers, to
     *       be merged by proximity clustering instead.); or</li>
     *   <li>it mostly overlaps real text — a decorative box drawn around body
     *       text, not a drawing.</li>
     * </ul>
     */
    // Package-private (not private) so ContentFilterProcessorTest can exercise the
    // rule directly with hand-built bounding boxes, without needing a real PDDocument.
    static boolean isGenuineVectorFigure(LineArtChunk chunk, List<IObject> pageContents,
                                          BoundingBox pageBoundingBox) {
        BoundingBox box = chunk.getBoundingBox();
        if (isTableGrid(box)) {
            return false;
        }
        double ownArea = box.getWidth() * box.getHeight();
        double overlappingTextArea = 0;
        for (IObject other : pageContents) {
            if (other == chunk) {
                continue;
            }
            double overlap = overlapArea(box, other.getBoundingBox());
            if (overlap <= 0) {
                continue;
            }
            if (other instanceof ImageChunk) {
                return false;
            }
            if (other instanceof LineArtChunk && isSizeableRegion(other.getBoundingBox(), pageBoundingBox)) {
                double otherArea = other.getWidth() * other.getHeight();
                boolean otherIsContained = otherArea > 0 && overlap / otherArea >= CONTAINMENT_RATIO;
                boolean notAPeer = ownArea > 0 && overlap / ownArea < PEER_OVERLAP_RATIO;
                if (otherIsContained && notAPeer) {
                    return false;
                }
            }
            if (other instanceof TextChunk) {
                overlappingTextArea += overlap;
            }
        }
        return ownArea <= 0 || overlappingTextArea / ownArea < TEXT_OVERLAP_RATIO;
    }

    /**
     * True if {@code box} coincides with a detected table border, i.e. the
     * chunk is that table's own grid. Uses the same identity test
     * {@code TableBorderProcessor} applies to recognize a table's own outline;
     * the borders collection is populated during preprocessing, before page
     * content filtering runs.
     */
    private static boolean isTableGrid(BoundingBox box) {
        TableBordersCollection tableBorders = StaticContainers.getTableBordersCollection();
        if (tableBorders == null) {
            return false;
        }
        TableBorder tableBorder = tableBorders.getTableBorder(box);
        return tableBorder != null && BoundingBox.areSameBoundingBoxes(tableBorder.getBoundingBox(), box);
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

    private static boolean isSizeableRegion(BoundingBox box, BoundingBox pageBoundingBox) {
        return box.getWidth() > 0.1 * pageBoundingBox.getWidth()
            && box.getHeight() > 0.1 * pageBoundingBox.getHeight();
    }

    private static double overlapArea(BoundingBox a, BoundingBox b) {
        double left = Math.max(a.getLeftX(), b.getLeftX());
        double right = Math.min(a.getRightX(), b.getRightX());
        double bottom = Math.max(a.getBottomY(), b.getBottomY());
        double top = Math.min(a.getTopY(), b.getTopY());
        if (right <= left || top <= bottom) {
            return 0;
        }
        return (right - left) * (top - bottom);
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

    private static List<IObject> splitTextChunksByWhiteSpacesInPageContents(List<IObject> contents) {
        List<IObject> newContents = new ArrayList<>();
        for (IObject object : contents) {
            if (object instanceof TextChunk) {
                TextChunk textChunk = (TextChunk) object;
                List<TextChunk> splitChunks = TextChunkUtils.splitTextChunkByWhiteSpaces(textChunk);
                newContents.addAll(splitChunks);
            } else {
                newContents.add(object);
            }
        }
        return newContents;
    }
}

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
package org.opendataloader.pdf.utils;

import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.as.ASAtom;
import org.verapdf.gf.model.factory.chunks.Matrix;
import org.verapdf.gf.model.impl.sa.GFSAPage;
import org.verapdf.pd.PDAnnotation;
import org.verapdf.pd.PDDocument;
import org.verapdf.pd.PDPage;
import org.verapdf.pd.actions.PDAction;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The URI links of the PDF over the text, for the Markdown and HTML output of {@code --include-links} (#676).
 *
 * <p>A glyph is in a link if its centre is in the link box, as in the tagged PDF. A chunk without a glyph position
 * for each character gets no link. The box of a link can be wider than its text: Word makes it 2 pt wider on each
 * side, so a parenthesis or a full stop next to the link has its centre in the box. Thus at the start and at the
 * end of a link, whitespace stays out of it, and so does a glyph that is not a letter or a digit and reaches out
 * of the box.
 */
public final class TextLinks {

    private static final Logger LOGGER = Logger.getLogger(TextLinks.class.getCanonicalName());
    private static final String[] SCHEMES = {"http://", "https://", "mailto:"};
    // A producer can round the link box: a glyph of the link can reach this far out of it.
    private static final double EDGE_TOLERANCE = 0.5;

    private final PDDocument document;
    private final Map<Integer, List<Link>> pageLinks = new HashMap<>();

    private TextLinks(PDDocument document) {
        this.document = document;
    }

    /**
     * Gives the links for the Markdown and HTML output of the config, or null if that output has no links.
     *
     * <p>With {@code --sanitize}, the output has no links: a link address can show the text that the sanitizer
     * replaces. When the hybrid backend makes the text, the output has no links: the glyph positions of the
     * backend text are estimates from the box of the element.
     *
     * @param config the configuration of the conversion
     * @return the links, or null
     */
    public static TextLinks forOutput(Config config) {
        if (!config.isIncludeLinks()) {
            return null;
        }
        if (config.getFilterConfig().isFilterSensitiveData()) {
            LOGGER.log(Level.WARNING, "--include-links writes no links with --sanitize: "
                + "a link address can show the text that --sanitize replaces");
            return null;
        }
        if (config.isHybridEnabled() && !Boolean.TRUE.equals(StaticLayoutContainers.isUseStructTree())) {
            LOGGER.log(Level.WARNING, "--include-links writes no links with --hybrid: "
                + "the glyph positions of the backend text are estimates");
            return null;
        }
        PDDocument document = StaticResources.getDocument();
        return document != null ? new TextLinks(document) : null;
    }

    /**
     * Splits the value of a chunk from {@code start} into the text of each link and the text between the links.
     * Without the links of {@code --include-links}, the result is one part without a URI.
     *
     * @param chunk the text chunk
     * @param start the index of the first character of the value
     * @return the parts, in the order of the value
     */
    public static List<Part> split(TextChunk chunk, int start) {
        return split(chunk, start, chunk.getValue().length());
    }

    /**
     * Splits the characters of the chunk value from {@code start} to {@code end} into the text of each link and the
     * text between the links. Without the links of {@code --include-links}, the result is one part without a URI.
     *
     * @param chunk the text chunk
     * @param start the index of the first character
     * @param end the index after the last character
     * @return the parts, in the order of the value
     */
    public static List<Part> split(TextChunk chunk, int start, int end) {
        String value = chunk.getValue();
        TextLinks links = StaticLayoutContainers.getTextLinks();
        String[] uris = links != null ? links.getUris(chunk) : null;
        if (uris == null) {
            return Collections.singletonList(new Part(value.substring(start, end), null));
        }
        List<Part> parts = new ArrayList<>();
        int partEnd;
        for (int from = start; from < end; from = partEnd) {
            partEnd = from + 1;
            while (partEnd < end && Objects.equals(uris[partEnd], uris[from])) {
                partEnd++;
            }
            parts.add(new Part(value.substring(from, partEnd), uris[from]));
        }
        return parts;
    }

    /** The URI of the link over each character of the chunk value, or null if no link is over the chunk. */
    private String[] getUris(TextChunk chunk) {
        String value = chunk.getValue();
        List<Double> ends = chunk.getSymbolEnds();
        if (value == null || value.isEmpty() || ends == null || ends.size() != value.length() + 1) {
            return null;
        }
        Link[] links = null;
        for (Link link : getLinks(chunk.getPageNumber())) {
            if (!isOver(link.getBox(), chunk.getBoundingBox())) {
                continue;
            }
            for (int i = 0; i < value.length(); i++) {
                double centre = (ends.get(i) + ends.get(i + 1)) / 2;
                if (centre >= link.getBox().getLeftX() && centre < link.getBox().getRightX()) {
                    if (links == null) {
                        links = new Link[value.length()];
                    }
                    // A glyph goes to one link only: the first link of the page over it.
                    if (links[i] == null) {
                        links[i] = link;
                    }
                }
            }
        }
        if (links == null) {
            return null;
        }
        leaveOutEdges(value, ends, links);
        String[] uris = new String[links.length];
        for (int i = 0; i < links.length; i++) {
            uris[i] = links[i] != null ? links[i].getUri() : null;
        }
        return uris;
    }

    private static void leaveOutEdges(String value, List<Double> ends, Link[] links) {
        int end;
        for (int start = 0; start < links.length; start = end) {
            end = start + 1;
            while (end < links.length && links[end] == links[start]) {
                end++;
            }
            Link link = links[start];
            if (link == null) {
                continue;
            }
            int first = start;
            int last = end - 1;
            while (first <= last && isOutOfLink(value, ends, first, link)) {
                links[first++] = null;
            }
            while (last >= first && isOutOfLink(value, ends, last, link)) {
                links[last--] = null;
            }
        }
    }

    private static boolean isOutOfLink(String value, List<Double> ends, int index, Link link) {
        char character = value.charAt(index);
        if (Character.isWhitespace(character)) {
            return true;
        }
        return !Character.isLetterOrDigit(character) && (ends.get(index) < link.getBox().getLeftX() - EDGE_TOLERANCE
            || ends.get(index + 1) > link.getBox().getRightX() + EDGE_TOLERANCE);
    }

    private static boolean isOver(BoundingBox linkBox, BoundingBox chunkBox) {
        return linkBox.getIntersectionPercent(chunkBox) > 0.5d || chunkBox.getIntersectionPercent(linkBox) > 0.5d;
    }

    private List<Link> getLinks(Integer pageNumber) {
        return pageLinks.computeIfAbsent(pageNumber, this::readLinks);
    }

    private List<Link> readLinks(Integer pageNumber) {
        List<PDPage> pages = document.getPages();
        if (pageNumber == null || pageNumber < 0 || pageNumber >= pages.size()) {
            return Collections.emptyList();
        }
        PDPage page = pages.get(pageNumber);
        List<PDAnnotation> annotations = page.getAnnotations();
        if (annotations == null) {
            return Collections.emptyList();
        }
        // The text is placed from the CropBox's lower-left corner and turned with /Rotate. Each link box is
        // placed the same way, as in the tagged PDF (#848).
        Matrix pageMatrix = GFSAPage.createCurrentTransformationMatrix(page);
        List<Link> links = new ArrayList<>();
        for (PDAnnotation annotation : annotations) {
            if (!ASAtom.LINK.equals(annotation.getSubtype())) {
                continue;
            }
            String uri = getUri(annotation.getA());
            double[] rect = annotation.getRect();
            if (uri != null && rect != null && rect.length == 4) {
                links.add(new Link(pageMatrix.transformBoundingBox(new BoundingBox(page.getPageNumber(), rect)), uri));
            }
        }
        return links;
    }

    /** The address of a URI action with an http, https, or mailto address, or null. */
    private static String getUri(PDAction action) {
        if (action == null || action.getObject() == null || !ASAtom.URI.equals(action.getSubtype())) {
            return null;
        }
        String uri = action.getStringKey(ASAtom.URI);
        if (uri == null) {
            return null;
        }
        uri = uri.trim();
        String lowerCase = uri.toLowerCase(Locale.ROOT);
        for (String scheme : SCHEMES) {
            if (lowerCase.startsWith(scheme) && lowerCase.length() > scheme.length()) {
                return uri;
            }
        }
        return null;
    }

    /**
     * The link that the text written so far ends in. A generator closes a link only before other text, or at the
     * end of the text: so the line end after a link is the line end of the text without links, and the next line
     * can continue the link (Word writes one link annotation for each line of a link that wraps).
     */
    public static final class OpenLink {
        private String uri;

        /** The URI of the open link, or null if no link is open. */
        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        /** Whether the part goes on in the open link: text of that link, or whitespace in it. */
        public boolean isContinuedBy(Part part) {
            return uri != null && (uri.equals(part.uri) || part.uri == null && part.text.isBlank());
        }
    }

    /** A part of the value of a chunk: the text of a link and its URI, or text without a link. */
    public static final class Part {
        private final String text;
        private final String uri;

        Part(String text, String uri) {
            this.text = text;
            this.uri = uri;
        }

        public String getText() {
            return text;
        }

        /** The URI of the link, or null for text without a link. */
        public String getUri() {
            return uri;
        }
    }

    private static final class Link {
        private final BoundingBox box;
        private final String uri;

        Link(BoundingBox box, String uri) {
            this.box = box;
            this.uri = uri;
        }

        BoundingBox getBox() {
            return box;
        }

        String getUri() {
            return uri;
        }
    }
}

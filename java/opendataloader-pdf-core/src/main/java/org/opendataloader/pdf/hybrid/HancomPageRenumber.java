/*
 * This file is part of the OpenDataLoader PDF project.
 * Copyright (c) Hancom Inc.
 * Licensed under the Apache License, Version 2.0.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Translates a sliced layout response's page numbers back onto the whole
 * document, and merges the slices into one response.
 *
 * <p>The layout module numbers response pages from 0 within the request it was
 * given, while {@link HancomAISchemaTransformer} reads {@code page_number} as an
 * absolute index into the document. Send pages 30-49 as their own PDF and the
 * response comes back numbered 0-19; used as-is, page 30's content lands on page
 * 0. Nothing throws and the output looks well-formed, so this translation is the
 * only thing standing between a sliced request and a silently scrambled
 * document.
 *
 * <p>Renumbering the layout response is also enough to fix everything derived
 * from it: the table, formula and caption passes all read their page number out
 * of these records and use it both for {@code pdf2img}'s page index and for the
 * {@code page_number} they emit.
 */
final class HancomPageRenumber {

    private static final Logger LOGGER = Logger.getLogger(HancomPageRenumber.class.getName());

    private static final String PAGE_NUMBER = "page_number";

    private HancomPageRenumber() {
        // Static utility class
    }

    /**
     * Returns the page records inside a module result.
     *
     * <p>{@code RESULT} arrives as {@code [[page0, page1, ...]]}; a flat array is
     * accepted too, matching the transformer's own tolerance.
     */
    static List<JsonNode> pagesOf(JsonNode moduleResult) {
        List<JsonNode> pages = new ArrayList<>();
        if (moduleResult == null || !moduleResult.isArray()) {
            return pages;
        }
        JsonNode inner = moduleResult.size() > 0 && moduleResult.get(0).isArray()
            ? moduleResult.get(0) : moduleResult;
        for (JsonNode page : inner) {
            if (page.isObject()) {
                pages.add(page);
            }
        }
        return pages;
    }

    /**
     * Rewrites each page record's request-relative {@code page_number} to the
     * absolute page it came from.
     *
     * <p>Position <i>k</i> of {@code slice} holds the absolute page that was sent
     * as relative page <i>k</i>, so the slice list is the mapping. Records whose
     * number falls outside the slice, or repeats a slot already taken, are
     * dropped with a warning: placing them would overwrite a real page's content
     * with another page's, which is worse than losing the page and falling back.
     *
     * @param moduleResult the response for one slice; not modified
     * @param slice        absolute 0-based pages sent, in the order sent
     * @return a new result tree with absolute page numbers
     */
    static JsonNode toAbsolutePages(JsonNode moduleResult, List<Integer> slice,
                                    ObjectMapper mapper) {
        ArrayNode pages = mapper.createArrayNode();
        Set<Integer> claimed = new HashSet<>();

        for (JsonNode page : pagesOf(moduleResult)) {
            int relative = page.has(PAGE_NUMBER) ? page.get(PAGE_NUMBER).asInt(-1) : -1;
            if (relative < 0 || relative >= slice.size()) {
                LOGGER.log(Level.WARNING,
                    "Dropping layout page {0}: outside the {1}-page slice sent",
                    new Object[]{relative, slice.size()});
                continue;
            }
            int absolute = slice.get(relative);
            if (!claimed.add(absolute)) {
                LOGGER.log(Level.WARNING,
                    "Dropping duplicate layout record for page {0}", absolute);
                continue;
            }
            ObjectNode copy = ((ObjectNode) page).deepCopy();
            copy.put(PAGE_NUMBER, absolute);
            pages.add(copy);
        }

        ArrayNode outer = mapper.createArrayNode();
        outer.add(pages);
        return outer;
    }

    /**
     * Concatenates already-renumbered slice results into one response.
     *
     * <p>Sorted by absolute page number. The transformer indexes by
     * {@code page_number} rather than by position, so ordering does not decide
     * where content lands — but the merged JSON is also filed as evidence and
     * read by people, and a server that answers a slice out of order would
     * otherwise leave the document's pages shuffled in the record.
     */
    static JsonNode merge(List<JsonNode> sliceResults, ObjectMapper mapper) {
        ArrayNode pages = mapper.createArrayNode();
        Set<Integer> seen = new HashSet<>();
        List<JsonNode> kept = new ArrayList<>();
        for (JsonNode sliceResult : sliceResults) {
            for (JsonNode page : pagesOf(sliceResult)) {
                int pageNumber = page.has(PAGE_NUMBER) ? page.get(PAGE_NUMBER).asInt(-1) : -1;
                if (pageNumber >= 0 && !seen.add(pageNumber)) {
                    // Two slices claiming one page means the slices overlapped;
                    // keeping both would let the later one silently win.
                    LOGGER.log(Level.WARNING,
                        "Dropping page {0} from a second slice claiming it", pageNumber);
                    continue;
                }
                kept.add(page);
            }
        }
        kept.sort(Comparator.comparingInt(
            page -> page.has(PAGE_NUMBER) ? page.get(PAGE_NUMBER).asInt(-1) : -1));
        for (JsonNode page : kept) {
            pages.add(page);
        }
        ArrayNode outer = mapper.createArrayNode();
        outer.add(pages);
        return outer;
    }
}

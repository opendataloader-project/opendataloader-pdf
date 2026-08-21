/*
 * This file is part of the OpenDataLoader PDF project.
 * Copyright (c) Hancom Inc.
 * Licensed under the Apache License, Version 2.0.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapping a sliced request's page numbers back onto the whole document.
 *
 * <p>The layout module numbers its response pages from 0 <em>within the request
 * it was given</em>, but {@link HancomAISchemaTransformer} reads that number as
 * an absolute page index into the document. Send pages 30-49 as their own PDF
 * and the response comes back numbered 0-19, so without this translation page 30
 * overwrites page 0 — a document that looks fine and attributes every page's
 * content to the wrong page. That silent-corruption path is what these tests
 * exist to close.
 */
class HancomPageRenumberTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Builds a layout response shaped like the real one: an array holding one
     * array of page records, each tagged with a request-relative page number and
     * carrying a marker object so pages can be told apart after renumbering.
     */
    private JsonNode responseWithRelativePages(int... relativePageNumbers) {
        ArrayNode outer = mapper.createArrayNode();
        ArrayNode pages = mapper.createArrayNode();
        for (int rel : relativePageNumbers) {
            pages.addObject()
                .put("page_number", rel)
                .put("marker", "rel" + rel);
        }
        outer.add(pages);
        return outer;
    }

    private List<Integer> absolutePagesOf(JsonNode renumbered) {
        List<Integer> out = new ArrayList<>();
        for (JsonNode page : HancomPageRenumber.pagesOf(renumbered)) {
            out.add(page.get("page_number").asInt());
        }
        return out;
    }

    private List<String> markersOf(JsonNode renumbered) {
        List<String> out = new ArrayList<>();
        for (JsonNode page : HancomPageRenumber.pagesOf(renumbered)) {
            out.add(page.get("marker").asText());
        }
        return out;
    }

    @Test
    void mapsContiguousSliceToAbsolutePages() {
        JsonNode response = responseWithRelativePages(0, 1, 2);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(30, 31, 32), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(30, 31, 32);
        assertThat(markersOf(result)).containsExactly("rel0", "rel1", "rel2");
    }

    /**
     * Triage can route an arbitrary set of pages to the backend, so the slice is
     * not necessarily a contiguous run.
     */
    @Test
    void mapsNonContiguousSlice() {
        JsonNode response = responseWithRelativePages(0, 1, 2);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(5, 9, 40), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(5, 9, 40);
        assertThat(markersOf(result)).containsExactly("rel0", "rel1", "rel2");
    }

    /**
     * The slice order is the mapping, not the sorted order: page k of the
     * response is whatever page the slicer put in position k.
     */
    @Test
    void followsSliceOrderNotSortedOrder() {
        JsonNode response = responseWithRelativePages(0, 1, 2);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(7, 2, 5), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(7, 2, 5);
    }

    /**
     * A page number outside the slice cannot be placed. Keeping it would put
     * some other page's content on a real page, so it is dropped.
     */
    @Test
    void dropsPageNumbersOutsideTheSlice() {
        JsonNode response = responseWithRelativePages(0, 99, 1);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(10, 11, 12), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(10, 11);
        assertThat(markersOf(result)).containsExactly("rel0", "rel1");
    }

    @Test
    void dropsNegativePageNumbers() {
        JsonNode response = responseWithRelativePages(-1, 0);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(4, 5), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(4);
    }

    /**
     * Two response pages claiming the same slot would land on one absolute page
     * and lose the other. Whatever the cause, the second must not overwrite the
     * first silently.
     */
    @Test
    void dropsDuplicateSlotsRatherThanOverwriting() {
        JsonNode response = responseWithRelativePages(0, 0, 1);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(20, 21), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(20, 21);
        assertThat(markersOf(result)).containsExactly("rel0", "rel1");
    }

    /**
     * No absolute page may appear twice across the renumbered result — the
     * page-scrambling failure mode this whole class guards against.
     */
    @Test
    void producesNoDuplicateAbsolutePages() {
        JsonNode response = responseWithRelativePages(0, 1, 2, 3, 4);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(50, 51, 52, 53, 54), mapper);

        List<Integer> pages = absolutePagesOf(result);
        Set<Integer> unique = new HashSet<>(pages);
        assertThat(unique).hasSameSizeAs(pages);
    }

    /**
     * The response tree is shared with the caller's merged JSON and with the
     * evidence report, so renumbering returns a new tree instead of editing it.
     */
    @Test
    void doesNotMutateTheInputTree() {
        JsonNode response = responseWithRelativePages(0, 1, 2);
        String before = response.toString();

        HancomPageRenumber.toAbsolutePages(response, Arrays.asList(60, 61, 62), mapper);

        assertThat(response.toString()).isEqualTo(before);
    }

    /**
     * A single-page slice is the common case for triage-routed pages and must
     * not be special-cased into page 0.
     */
    @Test
    void mapsSinglePageSlice() {
        JsonNode response = responseWithRelativePages(0);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Collections.singletonList(77), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(77);
    }

    /** An identity slice starting at 0 must be a no-op in effect. */
    @Test
    void identitySliceLeavesPageNumbersUnchanged() {
        JsonNode response = responseWithRelativePages(0, 1, 2);

        JsonNode result = HancomPageRenumber.toAbsolutePages(
            response, Arrays.asList(0, 1, 2), mapper);

        assertThat(absolutePagesOf(result)).containsExactly(0, 1, 2);
    }

    /**
     * Object ids identify a region within its page, so they carry no page
     * meaning and must survive untouched.
     */
    @Test
    void leavesObjectIdsAlone() {
        ArrayNode outer = mapper.createArrayNode();
        ArrayNode pages = mapper.createArrayNode();
        ArrayNode objects = pages.addObject().put("page_number", 0).putArray("objects");
        objects.addObject().put("object_id", 3).put("label", 10);
        objects.addObject().put("object_id", 7).put("label", 12);
        outer.add(pages);

        JsonNode result = HancomPageRenumber.toAbsolutePages(outer, Collections.singletonList(15), mapper);

        JsonNode page = HancomPageRenumber.pagesOf(result).get(0);
        assertThat(page.get("page_number").asInt()).isEqualTo(15);
        assertThat(page.get("objects").get(0).get("object_id").asInt()).isEqualTo(3);
        assertThat(page.get("objects").get(1).get("object_id").asInt()).isEqualTo(7);
    }

    /**
     * Merging accumulates slices in request order; the transformer indexes by
     * page number, so the merged result is checked by content rather than order.
     */
    @Test
    void mergesSlicesIntoOneResponse() {
        JsonNode first = HancomPageRenumber.toAbsolutePages(
            responseWithRelativePages(0, 1), Arrays.asList(0, 1), mapper);
        JsonNode second = HancomPageRenumber.toAbsolutePages(
            responseWithRelativePages(0, 1), Arrays.asList(2, 3), mapper);

        JsonNode merged = HancomPageRenumber.merge(Arrays.asList(first, second), mapper);

        assertThat(absolutePagesOf(merged)).containsExactly(0, 1, 2, 3);
    }

    /**
     * A slice answered out of order must not leave the merged record shuffled.
     * Placement is by page number, so the document still reads correctly — but
     * the merged JSON is filed as evidence and read by people.
     */
    @Test
    void mergeSortsByAbsolutePageNumber() {
        JsonNode later = HancomPageRenumber.toAbsolutePages(
            responseWithRelativePages(0, 1), Arrays.asList(10, 11), mapper);
        JsonNode earlier = HancomPageRenumber.toAbsolutePages(
            responseWithRelativePages(0, 1), Arrays.asList(2, 3), mapper);

        JsonNode merged = HancomPageRenumber.merge(Arrays.asList(later, earlier), mapper);

        assertThat(absolutePagesOf(merged)).containsExactly(2, 3, 10, 11);
    }

    @Test
    void mergeOfOneSliceIsThatSlice() {
        JsonNode only = HancomPageRenumber.toAbsolutePages(
            responseWithRelativePages(0, 1), Arrays.asList(8, 9), mapper);

        JsonNode merged = HancomPageRenumber.merge(Collections.singletonList(only), mapper);

        assertThat(absolutePagesOf(merged)).containsExactly(8, 9);
    }
}

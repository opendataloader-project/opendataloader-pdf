/*
 * This file is part of the OpenDataLoader PDF project.
 * Copyright (c) Hancom Inc.
 * Licensed under the Apache License, Version 2.0.
 */
package org.opendataloader.pdf.hybrid;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Slicing a PDF down to a subset of its pages, so a document longer than the
 * backend's per-request page limit can be sent in several requests.
 *
 * <p>Each page is built at a distinct size so a slice can be checked against the
 * pages it was supposed to carry. Page <em>order</em> and page <em>identity</em>
 * are what matter here: a slicer that silently returns the wrong pages produces
 * a plausible-looking document with its content attributed to the wrong pages,
 * which is the failure this class exists to rule out.
 */
class HancomPdfPageSlicerTest {

    /**
     * Builds a PDF whose page <i>i</i> is {@code 100 + i} points wide, making
     * every page individually identifiable by geometry alone.
     */
    private static byte[] pdfWithPages(int count) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < count; i++) {
                doc.addPage(new PDPage(new PDRectangle(100 + i, 200)));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /** Widths of every page in {@code pdfBytes}, in document order. */
    private static List<Integer> pageWidths(byte[] pdfBytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            List<Integer> widths = new java.util.ArrayList<>();
            for (PDPage page : doc.getPages()) {
                widths.add(Math.round(page.getMediaBox().getWidth()));
            }
            return widths;
        }
    }

    private static byte[] sha256(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(bytes);
    }

    @Test
    void extractsContiguousRange() throws Exception {
        byte[] source = pdfWithPages(82);

        byte[] slice = HancomPdfPageSlicer.extractPages(source, range(30, 50));

        assertThat(pageWidths(slice)).hasSize(20);
        // Page 30 of the source is 130pt wide, page 49 is 149pt.
        assertThat(pageWidths(slice)).isEqualTo(range(130, 150));
    }

    /**
     * Slices need not be contiguous: the caller may hand over whatever pages
     * triage routed to the backend.
     */
    @Test
    void extractsNonContiguousPages() throws Exception {
        byte[] source = pdfWithPages(50);

        byte[] slice = HancomPdfPageSlicer.extractPages(source, Arrays.asList(5, 9, 40));

        assertThat(pageWidths(slice)).containsExactly(105, 109, 140);
    }

    /** The requested order is the output order, even when it is not ascending. */
    @Test
    void preservesRequestedOrder() throws Exception {
        byte[] source = pdfWithPages(10);

        byte[] slice = HancomPdfPageSlicer.extractPages(source, Arrays.asList(7, 2, 5));

        assertThat(pageWidths(slice)).containsExactly(107, 102, 105);
    }

    @Test
    void extractsSinglePage() throws Exception {
        byte[] source = pdfWithPages(10);

        byte[] slice = HancomPdfPageSlicer.extractPages(source, Collections.singletonList(0));

        assertThat(pageWidths(slice)).containsExactly(100);
    }

    /**
     * A page number past the end is dropped rather than thrown: one bad index
     * must not cost the caller the whole document, which would fall back to the
     * Java pipeline for every page.
     */
    @Test
    void skipsOutOfRangePagesInsteadOfFailing() throws Exception {
        byte[] source = pdfWithPages(10);

        byte[] slice = HancomPdfPageSlicer.extractPages(source, Arrays.asList(8, 999, -1));

        assertThat(pageWidths(slice)).containsExactly(108);
    }

    /**
     * An empty slice has no meaningful PDF to produce, and a 0-page document
     * would be rejected downstream anyway. Callers must not ask.
     */
    @Test
    void emptySliceIsRejected() throws Exception {
        byte[] source = pdfWithPages(3);

        assertThatThrownBy(() -> HancomPdfPageSlicer.extractPages(source, Collections.emptyList()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * If every requested page is out of range there is nothing to send, and
     * returning an empty PDF would make the backend fail confusingly instead.
     */
    @Test
    void allPagesOutOfRangeIsRejected() throws Exception {
        byte[] source = pdfWithPages(3);

        assertThatThrownBy(() -> HancomPdfPageSlicer.extractPages(source, Arrays.asList(50, 60)))
            .isInstanceOf(IOException.class);
    }

    /**
     * The source array is reused for every other slice of the same document, and
     * for the crop/pdf2img passes that follow, so slicing must not touch it.
     */
    @Test
    void doesNotMutateSourceBytes() throws Exception {
        byte[] source = pdfWithPages(40);
        byte[] before = sha256(source);

        HancomPdfPageSlicer.extractPages(source, range(0, 20));

        assertThat(sha256(source)).isEqualTo(before);
    }

    private static List<Integer> range(int startInclusive, int endExclusive) {
        List<Integer> out = new java.util.ArrayList<>();
        for (int i = startInclusive; i < endExclusive; i++) {
            out.add(i);
        }
        return out;
    }
}

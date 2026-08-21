/*
 * This file is part of the OpenDataLoader PDF project.
 * Copyright (c) Hancom Inc.
 * Licensed under the Apache License, Version 2.0.
 */
package org.opendataloader.pdf.hybrid;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Cuts a PDF down to a subset of its pages so a document longer than the
 * backend's per-request page limit can be sent in several requests.
 *
 * <p>The Hancom AI layout module takes a whole PDF file and has no page-selection
 * parameter, so restricting it to a page range means handing it a smaller PDF.
 * Rendering the pages to images would do it too, but that would strip the text
 * layer and force a digital document down the OCR path, losing both accuracy and
 * speed — so the slice stays a PDF.
 *
 * <p><b>Upload cost.</b> A slice carries every resource its pages draw, so an
 * image shared across pages is copied into each slice that uses it. Total upload
 * is then a multiple of the file. Measured at 20 pages per slice: scanned
 * documents, whose pages each own their image, cost 0.99x (82-page 10MB and
 * 154-page 63MB samples); a 255-page 300MB book that reuses full-page
 * backgrounds across ~25 pages each costs 2.88x, and its largest single slice is
 * still ~298MB. Slice size does not help — the same book costs 2.91x at 50 pages
 * per slice — because the driver is resource sharing, not page count. Sending
 * such a document unsliced is cheaper in bytes, but the backend rejects it on
 * page count, so this is the cost of getting a long document processed at all.
 */
final class HancomPdfPageSlicer {

    private static final Logger LOGGER = Logger.getLogger(HancomPdfPageSlicer.class.getName());

    private HancomPdfPageSlicer() {
        // Static utility class
    }

    /**
     * Builds a new PDF holding only {@code pages} of {@code pdfBytes}.
     *
     * <p>Pages appear in the order requested, which is the order the caller will
     * map the response back onto absolute page numbers. Indexes outside the
     * document are skipped with a warning rather than thrown: one stray index
     * must not cost the caller every page in the slice.
     *
     * @param pdfBytes the source document; not modified
     * @param pages    absolute 0-based page indexes to keep, in the desired order
     * @return the sliced document's bytes
     * @throws IllegalArgumentException if {@code pages} is empty
     * @throws IOException              if the source cannot be read, or no requested
     *                                  page exists in it
     */
    static byte[] extractPages(byte[] pdfBytes, List<Integer> pages) throws IOException {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("page slice must not be empty");
        }

        // Loader.loadPDF(byte[]) may retain the array as the document's backing
        // store, and importPage() copies page dictionaries out of it, so the
        // source is only ever read here — never written.
        try (PDDocument source = Loader.loadPDF(pdfBytes);
             PDDocument slice = new PDDocument()) {

            int pageCount = source.getNumberOfPages();
            int kept = 0;
            for (int pageIndex : pages) {
                if (pageIndex < 0 || pageIndex >= pageCount) {
                    LOGGER.log(Level.WARNING,
                        "Skipping page {0}: outside document (0-{1})",
                        new Object[]{pageIndex, pageCount - 1});
                    continue;
                }
                PDPage imported = slice.importPage(source.getPage(pageIndex));
                // importPage() shares the source page's resource dictionary. The
                // slice is serialized before either document closes, so the
                // shared objects are still live at save() time.
                imported.setResources(source.getPage(pageIndex).getResources());
                kept++;
            }

            if (kept == 0) {
                throw new IOException(
                    "no requested page exists in the document (" + pageCount + " pages)");
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            slice.save(out);
            return out.toByteArray();
        }
    }
}

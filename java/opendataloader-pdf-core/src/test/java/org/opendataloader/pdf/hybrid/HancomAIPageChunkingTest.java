/*
 * This file is part of the OpenDataLoader PDF project.
 * Copyright (c) Hancom Inc.
 * Licensed under the Apache License, Version 2.0.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.HybridResponse;
import org.opendataloader.pdf.hybrid.HybridClient.OutputFormat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Splitting the layout request across several calls when a document is longer
 * than the backend takes at once.
 *
 * <p>The layout module accepts a whole PDF and no page selection, so a document
 * past the backend's limit has to be sliced into shorter PDFs. These tests pin
 * how many calls that produces, how many pages each carries, and that the merged
 * result lands on the right absolute pages — the page-scrambling failure being
 * the one that would otherwise pass unnoticed.
 */
class HancomAIPageChunkingTest {

    /**
     * How long to wait for a request that should already be queued.
     *
     * <p>{@code convert()} has returned before any of these helpers run, so
     * every request the client meant to send is recorded by then and each poll
     * either finds one at once or the queue is genuinely empty. The wait covers
     * scheduling noise only — but it is paid in full on the poll that ends a
     * drain, so it stays short enough not to add seconds to every test while
     * still being far above any plausible hand-off delay.
     */
    private static final int REQUEST_WAIT_MS = 500;

    private MockWebServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    private HancomAIClient clientWithChunk(int chunk) {
        HybridConfig config = new HybridConfig();
        config.setLayoutPageChunk(chunk);
        return new HancomAIClient(
            server.url("").toString().replaceAll("/$", ""),
            new OkHttpClient(), mapper, config);
    }

    private HancomAIClient clientWithOcrStrategy(String ocrStrategy) {
        HybridConfig config = new HybridConfig();
        config.setOcrStrategy(ocrStrategy);
        return new HancomAIClient(
            server.url("").toString().replaceAll("/$", ""),
            new OkHttpClient(), mapper, config);
    }

    /**
     * The module name in the next recorded request's multipart body.
     */
    private String nextRequestModule() throws Exception {
        String body = nextRequestBody();
        int at = body.indexOf("OPEN_API_NAME");
        assertThat(at).as("request carries a module name").isGreaterThanOrEqualTo(0);
        String after = body.substring(at);
        int blank = after.indexOf("\r\n\r\n");
        String value = after.substring(blank + 4);
        return value.substring(0, value.indexOf("\r\n")).trim();
    }

    /**
     * With OCR off the text comes from the PDF content stream, so the layout
     * pass must ask for the cheaper module that returns geometry only. Asserted
     * on the wire because that request is what the cost is paid for.
     */
    @Test
    void ocrOffRequestsTheLayoutOnlyModule() throws Exception {
        enqueueLayoutSlices(1);

        clientWithOcrStrategy(HybridConfig.OCR_OFF)
            .convert(requestFor(pdfWithPages(1), pages1Indexed(1, 1)));

        assertThat(nextRequestModule()).isEqualTo("DOCUMENT_LAYOUT_ANALYSIS");
    }

    /**
     * auto compares stream text against OCR text, so it must keep asking for the
     * module that returns both. force uses the OCR text outright.
     */
    @Test
    void ocrAutoAndForceRequestTheModuleThatReturnsText() throws Exception {
        enqueueLayoutSlices(1);
        clientWithOcrStrategy(HybridConfig.OCR_AUTO)
            .convert(requestFor(pdfWithPages(1), pages1Indexed(1, 1)));
        assertThat(nextRequestModule()).isEqualTo("DOCUMENT_LAYOUT_WITH_OCR");

        enqueueLayoutSlices(1);
        clientWithOcrStrategy(HybridConfig.OCR_FORCE)
            .convert(requestFor(pdfWithPages(1), pages1Indexed(1, 1)));
        assertThat(nextRequestModule()).isEqualTo("DOCUMENT_LAYOUT_WITH_OCR");
    }

    /**
     * Whichever module ran, the transformer reads the layout result under one
     * fixed key. A mismatch here would silently empty its input.
     */
    @Test
    void layoutResultKeyDoesNotDependOnTheModule() throws Exception {
        enqueueLayoutSlices(1);

        HybridClient.HybridResponse response = clientWithOcrStrategy(HybridConfig.OCR_OFF)
            .convert(requestFor(pdfWithPages(1), pages1Indexed(1, 1)));

        assertThat(response.getJson().has(HancomAIClient.LAYOUT_RESULT_KEY)).isTrue();
    }

    /** A PDF whose page <i>i</i> is {@code 100 + i} points wide. */
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

    /**
     * A PDF whose page tree claims {@code claimedCount} pages while holding
     * {@code realCount}. Written through the PDF model rather than by editing
     * bytes, because a saved PDF keeps its page tree in a compressed object
     * stream where the count is not findable as text.
     */
    private static byte[] pdfWithInflatedPageCount(int realCount, int claimedCount)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < realCount; i++) {
                doc.addPage(new PDPage(new PDRectangle(100 + i, 200)));
            }
            COSDictionary pages = (COSDictionary) doc.getDocumentCatalog()
                .getCOSObject().getDictionaryObject(COSName.PAGES);
            pages.setInt(COSName.COUNT, claimedCount);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * A layout response for {@code pageCount} pages, numbered from 0 as the real
     * server numbers them — relative to the request it was given.
     */
    private String layoutResponse(int pageCount) {
        StringBuilder pages = new StringBuilder();
        for (int i = 0; i < pageCount; i++) {
            if (i > 0) {
                pages.append(',');
            }
            pages.append("{\"page_number\":").append(i)
                .append(",\"image_width\":1000,\"image_height\":2000,\"objects\":[]}");
        }
        return "{\"SUCCESS\":true,\"RESULT\":[[" + pages + "]]}";
    }

    /**
     * Enqueues one layout response per slice. The later passes need nothing:
     * every page comes back with an empty {@code objects} array, so the table,
     * formula and caption passes have no region to send.
     */
    private void enqueueLayoutSlices(int... pagesPerSlice) {
        for (int pages : pagesPerSlice) {
            server.enqueue(new MockResponse.Builder().code(200).body(layoutResponse(pages)).build());
        }
    }

    private HybridRequest requestFor(byte[] pdf, Set<Integer> pages1Indexed) {
        return new HybridRequest(pdf, pages1Indexed, EnumSet.allOf(OutputFormat.class));
    }

    private static Set<Integer> pages1Indexed(int fromInclusive, int toInclusive) {
        Set<Integer> pages = new LinkedHashSet<>();
        for (int p = fromInclusive; p <= toInclusive; p++) {
            pages.add(p);
        }
        return pages;
    }

    /**
     * Page widths of each PDF uploaded to the layout endpoint, one list per
     * request. Page <i>i</i> of the source is {@code 100 + i} wide, so these
     * identify exactly which source pages a slice carried — not merely how many.
     */
    private List<List<Integer>> uploadedPageWidths() throws Exception {
        List<List<Integer>> perRequest = new ArrayList<>();
        while (true) {
            RecordedRequest request = server.takeRequest(
                REQUEST_WAIT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (request == null) {
                return perRequest;
            }
            if (request.getTarget() == null || !request.getTarget().contains("/hocr/sdk")) {
                continue;
            }
            byte[] pdf = extractPdfPart(request.getBody().toByteArray());
            if (pdf == null) {
                continue;
            }
            try (PDDocument doc = Loader.loadPDF(pdf)) {
                List<Integer> widths = new ArrayList<>();
                for (PDPage page : doc.getPages()) {
                    widths.add(Math.round(page.getMediaBox().getWidth()));
                }
                perRequest.add(widths);
            }
        }
    }

    /**
     * The next recorded request's body. Bounded: an unbounded wait would hang
     * the suite rather than fail it when an expected request never arrives. This
     * one is only paid when the test is already failing, so it can be generous.
     */
    private String nextRequestBody() throws Exception {
        RecordedRequest request = server.takeRequest(10, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(request).as("expected another request").isNotNull();
        return request.getBody().utf8();
    }

    /** Page counts of the PDFs uploaded to the layout endpoint, per request. */
    private List<Integer> uploadedPageCounts() throws Exception {
        List<Integer> counts = new ArrayList<>();
        for (List<Integer> widths : uploadedPageWidths()) {
            counts.add(widths.size());
        }
        return counts;
    }

    /**
     * Pulls the PDF out of a multipart body by locating its header and trailer,
     * which avoids decoding multipart framing byte by byte.
     */
    private static byte[] extractPdfPart(byte[] body) {
        int start = indexOf(body, "%PDF".getBytes(), 0);
        if (start < 0) {
            return null;
        }
        int end = lastIndexOf(body, "%%EOF".getBytes());
        if (end < 0) {
            return null;
        }
        byte[] pdf = new byte[end + 5 - start];
        System.arraycopy(body, start, pdf, 0, pdf.length);
        return pdf;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static int lastIndexOf(byte[] haystack, byte[] needle) {
        for (int i = haystack.length - needle.length; i >= 0; i--) {
            boolean hit = true;
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                return i;
            }
        }
        return -1;
    }

    private static List<Integer> pageNumbersOf(HybridResponse response) {
        List<Integer> out = new ArrayList<>();
        JsonNode layout = response.getJson().get("DOCUMENT_LAYOUT_WITH_OCR");
        for (JsonNode page : HancomPageRenumber.pagesOf(layout)) {
            out.add(page.get("page_number").asInt());
        }
        return out;
    }

    /**
     * 45 pages at 20 per request is three calls carrying 20, 20 and 5 pages, and
     * the merged result covers all 45 absolute pages once each.
     */
    @Test
    void splitsLongDocumentAcrossRequests() throws Exception {
        enqueueLayoutSlices(20, 20, 5);

        HybridResponse response = clientWithChunk(20)
            .convert(requestFor(pdfWithPages(45), pages1Indexed(1, 45)));

        assertThat(uploadedPageCounts()).containsExactly(20, 20, 5);

        List<Integer> pages = pageNumbersOf(response);
        assertThat(pages).hasSize(45);
        assertThat(pages).containsExactlyElementsOf(range(0, 45));
    }

    /**
     * The whole point of splitting: a later slice's pages must land on their real
     * page numbers, not back at page 0 where the response numbered them.
     */
    @Test
    void laterSlicesKeepTheirAbsolutePageNumbers() throws Exception {
        enqueueLayoutSlices(10, 10);

        HybridResponse response = clientWithChunk(10)
            .convert(requestFor(pdfWithPages(20), pages1Indexed(1, 20)));

        assertThat(pageNumbersOf(response)).containsExactlyElementsOf(range(0, 20));
    }

    /**
     * A document within the limit must behave exactly as before: one call
     * carrying every page, with no re-saving step in between.
     *
     * <p>Compared by page geometry rather than by bytes: a document that went
     * through the slicer unchanged would still differ byte-for-byte, since
     * saving a PDF rewrites its object stream and trailer.
     */
    @Test
    void shortDocumentIsSentWholeInOneRequest() throws Exception {
        enqueueLayoutSlices(3);

        clientWithChunk(20).convert(requestFor(pdfWithPages(3), pages1Indexed(1, 3)));

        assertThat(uploadedPageWidths()).containsExactly(List.of(100, 101, 102));
    }

    /** A single-page document is the corpus norm and must not gain a slicing step. */
    @Test
    void singlePageDocumentIsSentUnchanged() throws Exception {
        enqueueLayoutSlices(1);

        clientWithChunk(20).convert(requestFor(pdfWithPages(1), Collections.singleton(1)));

        assertThat(uploadedPageWidths()).containsExactly(List.of(100));
    }

    /**
     * Triage routes an arbitrary page set to the backend, so a slice can be
     * sparse; the merged pages must be exactly the ones asked for.
     */
    @Test
    void honoursASparsePageSelection() throws Exception {
        enqueueLayoutSlices(2, 1);

        HybridResponse response = clientWithChunk(2)
            .convert(requestFor(pdfWithPages(50), new LinkedHashSet<>(List.of(4, 10, 41))));

        // 1-indexed 4, 10, 41 are absolute 0-based pages 3, 9 and 40.
        assertThat(pageNumbersOf(response)).containsExactly(3, 9, 40);
        // Those exact pages must be the ones uploaded: widths 103, 109 and 140.
        assertThat(uploadedPageWidths()).containsExactly(List.of(103, 109), List.of(140));
    }

    /** An empty page set means the whole document, as the request contract says. */
    @Test
    void emptyPageSelectionMeansEveryPage() throws Exception {
        enqueueLayoutSlices(2, 2);

        HybridResponse response = clientWithChunk(2)
            .convert(requestFor(pdfWithPages(4), Collections.emptySet()));

        assertThat(pageNumbersOf(response)).containsExactlyElementsOf(range(0, 4));
    }

    /**
     * A slice's own bytes hash to nothing recognisable, so the request id has to
     * carry both the source document and the pages the slice holds — otherwise
     * neither a replaying mock nor a server-side log can tell which pages a call
     * was about.
     */
    @Test
    void sliceRequestIdNamesTheAbsolutePageRange() throws Exception {
        enqueueLayoutSlices(2, 2);

        clientWithChunk(2).convert(requestFor(pdfWithPages(4), pages1Indexed(1, 4)));

        List<String> requestIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String body = nextRequestBody();
            int at = body.indexOf("odl-");
            requestIds.add(body.substring(at, body.indexOf("\r\n", at)));
        }
        // Absolute 0-based first and last page of each slice.
        assertThat(requestIds.get(0)).endsWith("-dla-p0-1");
        assertThat(requestIds.get(1)).endsWith("-dla-p2-3");
    }

    /**
     * An unsliced document's request id carries no page-range suffix, so it
     * stays distinguishable from a slice of the same document. The module slug
     * tracks the layout module in use ("dla" for DOCUMENT_LAYOUT_ANALYSIS).
     */
    @Test
    void unslicedRequestIdIsUnchanged() throws Exception {
        enqueueLayoutSlices(2);

        clientWithChunk(20).convert(requestFor(pdfWithPages(2), pages1Indexed(1, 2)));

        String body = nextRequestBody();
        int at = body.indexOf("odl-");
        assertThat(body.substring(at, body.indexOf("\r\n", at))).endsWith("-dla");
    }

    /**
     * A requested page the document does not have must not enter the slice list.
     * The slicer skips pages it cannot find, so a phantom page left in the list
     * would make it longer than the PDF built from it — and because response page
     * <i>k</i> is looked up as slice position <i>k</i>, every page after the gap
     * would be filed under the wrong number. A page missing from the front is
     * the harmful case, since a trailing one shifts nothing.
     */
    @Test
    void pagesOutsideTheDocumentDoNotShiftTheMapping() throws Exception {
        enqueueLayoutSlices(2, 1);

        // Page numbers are 1-indexed, so 0 is not a page. Sorted ascending it
        // lands at the FRONT of the slice, which is the position that shifts
        // every page after it; an out-of-range page at the back shifts nothing.
        HybridResponse response = clientWithChunk(2)
            .convert(requestFor(pdfWithPages(4), new LinkedHashSet<>(List.of(0, 2, 3, 4))));

        // Absolute 0-based 1, 2, 3 — not shifted by the discarded page.
        assertThat(pageNumbersOf(response)).containsExactly(1, 2, 3);
        assertThat(uploadedPageWidths()).containsExactly(List.of(101, 102), List.of(103));
    }

    /**
     * A selection naming only pages the document does not have is a caller
     * error, and there is nothing to send. Falling back to the whole document
     * would answer with pages nobody asked for, which is worse than failing:
     * the caller cannot tell the difference from a document that really does
     * have those pages.
     */
    @Test
    void anExplicitSelectionWithNoValidPagesIsRejected() throws Exception {
        assertThatThrownBy(() -> clientWithChunk(2)
            .convert(requestFor(pdfWithPages(3), new LinkedHashSet<>(List.of(50, 60)))))
            .isInstanceOf(IOException.class);

        // Nothing was uploaded: the request never reached the backend.
        assertThat(uploadedPageCounts()).isEmpty();
    }

    /**
     * An empty selection is the documented way to say "every page", so it must
     * keep meaning that rather than being treated as a selection that resolved
     * to nothing.
     */
    @Test
    void anEmptySelectionStillMeansEveryPage() throws Exception {
        enqueueLayoutSlices(3);

        clientWithChunk(20)
            .convert(requestFor(pdfWithPages(3), Collections.emptySet()));

        assertThat(uploadedPageCounts()).containsExactly(3);
    }

    /** Slicing off means one request regardless of length — the escape hatch. */
    @Test
    void chunkingCanBeDisabled() throws Exception {
        enqueueLayoutSlices(40);

        clientWithChunk(0).convert(requestFor(pdfWithPages(40), pages1Indexed(1, 40)));

        assertThat(uploadedPageCounts()).containsExactly(40);
    }

    /**
     * One slice failing costs that slice's pages, not the document: the pages
     * that did come back are kept and the rest are reported as failed so the
     * caller can put them through the Java pipeline.
     */
    @Test
    void oneFailedSliceKeepsTheOtherPages() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body(layoutResponse(2)).build());
        server.enqueue(new MockResponse.Builder().code(500).body("boom").build());
        server.enqueue(new MockResponse.Builder().code(200).body(layoutResponse(2)).build());

        HybridResponse response = clientWithChunk(2)
            .convert(requestFor(pdfWithPages(6), pages1Indexed(1, 6)));

        assertThat(pageNumbersOf(response)).containsExactly(0, 1, 4, 5);
        // Failed pages are reported 1-indexed, matching the request contract.
        assertThat(response.getFailedPages()).containsExactly(3, 4);
    }

    /**
     * A slice can come back well-formed and yet hold no pages: the layout
     * RESULT is nested, so an empty reply is {@code [[]]}, whose outer array has
     * one element. Counting array entries instead of pages lets that through as
     * a success, and the slice's pages end up in the output as blank pages that
     * are never retried — the document quietly loses them.
     */
    @Test
    void nestedEmptySliceIsReportedAsFailed() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200).body(layoutResponse(2)).build());
        server.enqueue(new MockResponse.Builder().code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[]]}").build());

        HybridResponse response = clientWithChunk(2)
            .convert(requestFor(pdfWithPages(4), pages1Indexed(1, 4)));

        assertThat(pageNumbersOf(response)).containsExactly(0, 1);
        assertThat(response.getFailedPages()).containsExactly(3, 4);
    }

    /**
     * A slice that comes back with fewer pages than it was sent loses its tail.
     * The pages that did arrive are numbered sequentially so they land
     * correctly, but the missing ones must be reported rather than left as
     * blank pages nothing will revisit.
     */
    @Test
    void shortSliceReplyReportsTheMissingPages() throws Exception {
        // Two pages sent, one page back.
        server.enqueue(new MockResponse.Builder().code(200).body(layoutResponse(1)).build());
        server.enqueue(new MockResponse.Builder().code(200).body(layoutResponse(2)).build());

        HybridResponse response = clientWithChunk(2)
            .convert(requestFor(pdfWithPages(4), pages1Indexed(1, 4)));

        assertThat(pageNumbersOf(response)).containsExactly(0, 2, 3);
        assertThat(response.getFailedPages()).containsExactly(2);
    }

    /**
     * A damaged page tree makes PDFBox throw an unchecked exception rather than
     * an IOException: the page count comes from {@code /Count}, which a corrupt
     * document can leave disagreeing with the real tree, so the bounds check
     * passes and the page fetch fails. Catching only IOException would let that
     * escape and cost the document its entire backend pass — the opposite of
     * what slicing per page range is for.
     *
     * <p>Built by editing the page count of a real PDF, because the failure comes
     * from PDFBox's own reaction to the mismatch, not from anything we could
     * usefully stub.
     */
    @Test
    void slicesWithADamagedPageTreeCostOnlyThemselves() throws Exception {
        // Page count inflated to 6 where 4 pages exist. The count is what the
        // bounds check trusts, so pages 5 and 6 are attempted and throw
        // IllegalStateException — the third slice at chunk 2.
        byte[] corrupted = pdfWithInflatedPageCount(4, 6);

        enqueueLayoutSlices(2, 2, 2);

        HybridResponse response = clientWithChunk(2)
            .convert(requestFor(corrupted, pages1Indexed(1, 6)));

        // Every real page still comes back rather than the document failing
        // whole, and only the unloadable pages are reported — stated exactly, so
        // a slice quietly dropping its pages would fail here.
        assertThat(pageNumbersOf(response)).containsExactly(0, 1, 2, 3);
        assertThat(response.getFailedPages()).containsExactly(5, 6);
    }

    /**
     * If no slice comes back there is nothing to transform, and returning an
     * empty document would look like a document with no content. The caller
     * needs the failure so the whole document falls back.
     */
    @Test
    void allSlicesFailingIsAnError() throws Exception {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse.Builder().code(500).body("boom").build());
        }

        assertThatThrownBy(() -> clientWithChunk(2)
            .convert(requestFor(pdfWithPages(6), pages1Indexed(1, 6))))
            .isInstanceOf(IOException.class);
    }

    /**
     * The same nested-empty shape on the unsliced path. A short document is sent
     * whole, so it never goes through the per-slice accounting; without its own
     * page count check it would produce a document with no content instead of
     * falling back to the Java pipeline.
     */
    @Test
    void nestedEmptyResultOnTheUnslicedPathIsAnError() throws Exception {
        server.enqueue(new MockResponse.Builder().code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[]]}").build());

        assertThatThrownBy(() -> clientWithChunk(20)
            .convert(requestFor(pdfWithPages(2), pages1Indexed(1, 2))))
            .isInstanceOf(IOException.class);
    }

    /**
     * A small page selection from a long document must still be sliced down to
     * the pages asked for. Deciding on the selection size alone would send the
     * whole file — 100 pages to get 10 — which is the very thing the page limit
     * rejects, and it makes the backend do ten times the work.
     */
    @Test
    void smallSelectionFromALongDocumentSendsOnlyThosePages() throws Exception {
        enqueueLayoutSlices(3);

        HybridResponse response = clientWithChunk(20)
            .convert(requestFor(pdfWithPages(100), new LinkedHashSet<>(List.of(5, 40, 90))));

        // Only the three requested pages are uploaded, not the whole document.
        assertThat(uploadedPageWidths()).containsExactly(List.of(104, 139, 189));
        assertThat(pageNumbersOf(response)).containsExactly(4, 39, 89);
    }

    /**
     * A document short enough to go up whole still has to account for its pages.
     * The unsliced path returns the backend result directly, so a reply covering
     * fewer pages than the document holds would leave the missing ones as blank
     * pages the Java pipeline never revisits — the same loss the per-slice
     * accounting exists to prevent.
     */
    @Test
    void shortReplyToAnUnslicedRequestReportsTheMissingPages() throws Exception {
        // Two pages sent whole, one page back.
        enqueueLayoutSlices(1);

        HybridResponse response = clientWithChunk(20)
            .convert(requestFor(pdfWithPages(2), pages1Indexed(1, 2)));

        assertThat(pageNumbersOf(response)).containsExactly(0);
        assertThat(response.getFailedPages()).containsExactly(2);
    }

    /**
     * Turning slicing off means "send it in one request", not "ignore which pages
     * were asked for". Uploading the whole file would make the backend process
     * every page and return records for pages the caller never requested, which
     * then reach the crop passes.
     */
    @Test
    void chunkingDisabledStillHonoursThePageSelection() throws Exception {
        enqueueLayoutSlices(2);

        HybridResponse response = clientWithChunk(0)
            .convert(requestFor(pdfWithPages(10), new LinkedHashSet<>(List.of(3, 8))));

        // Only the two requested pages go up, in one request.
        assertThat(uploadedPageWidths()).containsExactly(List.of(102, 107));
        assertThat(pageNumbersOf(response)).containsExactly(2, 7);
    }

    private static List<Integer> range(int startInclusive, int endExclusive) {
        List<Integer> out = new ArrayList<>();
        for (int i = startInclusive; i < endExclusive; i++) {
            out.add(i);
        }
        return out;
    }
}

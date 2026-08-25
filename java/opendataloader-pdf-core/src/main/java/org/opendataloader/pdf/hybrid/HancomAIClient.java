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
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HTTP client for Hancom AI HOCR SDK API.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>pdf2img — convert each page to PNG image</li>
 *   <li>layout on the full PDF — DOCUMENT_LAYOUT_ANALYSIS, or
 *       DOCUMENT_LAYOUT_WITH_OCR when the OCR strategy needs the text
 *       (see {@link #layoutModule()})</li>
 *   <li>TABLE_STRUCTURE_RECOGNITION — crop each Table/Regionlist from page image, send to TSR individually</li>
 *   <li>IMAGE_CAPTIONING_EN — crop each visual region (Figure/Chart/Image) and caption it in English</li>
 *   <li>FORMULA_RECOGNITION — crop each Equation region and read it as LaTeX</li>
 * </ol>
 *
 * @see HancomAISchemaTransformer
 */
public class HancomAIClient implements HybridClient {

    private static final Logger LOGGER = Logger.getLogger(HancomAIClient.class.getCanonicalName());

    public static final String DEFAULT_URL = "http://localhost:18008";

    private static final String SDK_ENDPOINT = "/hocr/sdk";
    private static final String PDF2IMG_ENDPOINT = "/support/pdf2img";
    private static final String PING_ENDPOINT = "/ping";
    private static final String HEALTH_ENDPOINT = "/health";
    private static final int HEALTH_CHECK_TIMEOUT_MS = 3000;
    private static final String DEFAULT_FILENAME = "document.pdf";
    private static final MediaType MEDIA_TYPE_PDF = MediaType.parse("application/pdf");
    private static final MediaType MEDIA_TYPE_PNG = MediaType.parse("image/png");

    // DLA label 7 = Regionlist (may be actual table or a list region)
    private static final int LABEL_REGIONLIST = 7;

    // DLA label 9 = Table
    private static final int LABEL_TABLE = 9;

    // DLA label 10 = Figure
    private static final int LABEL_FIGURE = 10;

    // DLA label 12 = Equation
    private static final int LABEL_EQUATION = 12;

    /** Padding (pixels) added around table crops before sending to TSR. */
    private static final int TSR_CROP_PADDING = 20;

    /**
     * Captioning module. {@code IMAGE_CAPTIONING} and {@code IMAGE_CAPTIONING_EN}
     * are separate engines writing the same response field, so the caption
     * language is decided here and nowhere else.
     */
    private static final String CAPTION_MODULE = "IMAGE_CAPTIONING_EN";

    /**
     * Layout module that also returns OCR text, used when the OCR strategy
     * asks for it.
     */
    private static final String LAYOUT_MODULE_WITH_OCR = "DOCUMENT_LAYOUT_WITH_OCR";

    /**
     * Layout-only module: geometry with no {@code ocrtext} and no
     * {@code words[]}. Roughly 4x cheaper than the OCR variant, which is the
     * whole point of preferring it when the text is going to come from the PDF
     * content stream anyway.
     */
    private static final String LAYOUT_MODULE_DLA_ONLY = "DOCUMENT_LAYOUT_ANALYSIS";

    /**
     * Key the layout result is filed under in the merged response.
     *
     * <p>Deliberately not the wire module name: which layout module runs is a
     * per-call decision (see {@link #layoutModule()}), while this key is a
     * fixed contract with {@link HancomAISchemaTransformer}. Keeping them
     * separate means switching layout modules cannot silently empty the
     * transformer's input.
     */
    static final String LAYOUT_RESULT_KEY = "DOCUMENT_LAYOUT_WITH_OCR";

    /** Formula module; returns LaTeX in a {@code formula} field. */
    private static final String FORMULA_MODULE = "FORMULA_RECOGNITION";

    /**
     * What the server puts in {@code RESULT[0][0].MSG} for a module name it does
     * not know. Such a response still carries HTTP 200 and {@code SUCCESS:true},
     * so without checking this a misspelled module name would look like a
     * document that simply had nothing to caption.
     */
    private static final String MSG_NO_ENGINE = "not existed engine";

    /**
     * Field carrying an object's position within its page's {@code objects}
     * array. Enrichment results are matched back to their region by this rather
     * than by {@code object_id}, which DLA leaves off some objects and repeats
     * across others within a single page.
     */
    static final String OBJECT_INDEX_FIELD = "odl_object_index";

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HybridConfig config;

    private String sourcePdfShaShort = "unknown";

    /**
     * Modules the server reported as absent during one {@code convert} call.
     *
     * <p>A per-call WARNING is easy to lose in a long log, so {@code convert}
     * raises one summary at the end: a module-name typo otherwise looks like a
     * document that simply had nothing to enrich.
     *
     * <p>Deliberately a local passed down the call chain rather than a field.
     * {@link HybridClientFactory} caches one client across documents and
     * {@link #convertAsync} lets calls overlap, so a field would let one
     * document clear or claim another's findings.
     */
    /**
     * A DLA object together with its position in its page's {@code objects}
     * array. Enrichment results are matched back to their region by that
     * position, because {@code object_id} is optional and repeats within a
     * page. The pairing lives here rather than as a field written into the
     * response tree: those nodes are handed back to the caller, and a synthetic
     * key the backend never sent would travel with them.
     */
    private static final class IndexedObject {
        final JsonNode node;
        final int index;

        IndexedObject(JsonNode node, int index) {
            this.node = node;
            this.index = index;
        }
    }

    private static final class MissingEngines {
        private final java.util.Set<String> names = new java.util.LinkedHashSet<>();

        void add(String moduleName) {
            names.add(moduleName);
        }

        boolean isEmpty() {
            return names.isEmpty();
        }

        @Override
        public String toString() {
            return String.join(", ", names);
        }
    }

    private static final java.util.Map<String, String> MODULE_SHORT;
    static {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("DOCUMENT_LAYOUT_WITH_OCR", "dla-ocr");
        m.put("DOCUMENT_LAYOUT_ANALYSIS", "dla");
        m.put("TEXT_RECOGNITION", "ocr");
        m.put("TABLE_STRUCTURE_RECOGNITION", "tsr");
        m.put("IMAGE_CAPTIONING", "caption");
        m.put("IMAGE_CAPTIONING_EN", "caption");
        m.put("FORMULA_RECOGNITION", "formula");
        m.put("CHART_IMAGE_UNDERSTANDING", "chart");
        MODULE_SHORT = java.util.Collections.unmodifiableMap(m);
    }

    /**
     * The layout module this call should use.
     *
     * <p>With {@code --hybrid-hancom-ai-ocr-strategy off} the text comes from
     * the PDF content stream, so paying for OCR would buy nothing: the
     * layout-only module returns the same geometry for a fraction of the cost.
     * {@code auto} needs the OCR text to compare the stream against, and
     * {@code force} uses it outright, so both keep the OCR variant.
     */
    private String layoutModule() {
        return config != null && HybridConfig.OCR_OFF.equals(config.getOcrStrategy())
            ? LAYOUT_MODULE_DLA_ONLY
            : LAYOUT_MODULE_WITH_OCR;
    }

    private static String sha256ShortHex(byte[] data) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder(12);
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", hash[i]));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return "nohash000000";
        }
    }

    // Test hook
    void setSourcePdfShaShort(String s) { this.sourcePdfShaShort = s; }

    public HancomAIClient(HybridConfig config) {
        this.config = config;
        this.baseUrl = config.getEffectiveUrl("hancom-ai");
        this.objectMapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .readTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.getTimeoutMs(), TimeUnit.MILLISECONDS)
            .build();
    }

    // Visible for testing
    HancomAIClient(String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper) {
        this(baseUrl, httpClient, objectMapper, new HybridConfig());
    }

    // Visible for testing
    HancomAIClient(String baseUrl, OkHttpClient httpClient, ObjectMapper objectMapper,
                   HybridConfig config) {
        this.config = config;
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public JsonNode fetchHealth() {
        OkHttpClient healthClient = httpClient.newBuilder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();
        Request request = new Request.Builder()
            .url(baseUrl + HEALTH_ENDPOINT)
            .get()
            .build();
        try (Response response = healthClient.newCall(request).execute()) {
            if (!response.isSuccessful()) return null;
            ResponseBody body = response.body();
            if (body == null) return null;
            return objectMapper.readTree(body.string());
        } catch (IOException e) {
            LOGGER.log(Level.FINE,
                "Hancom AI /health unavailable: {0}", e.getMessage());
            return null;
        }
    }

    @Override
    public void checkAvailability() throws IOException {
        OkHttpClient healthClient = httpClient.newBuilder()
            .connectTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(HEALTH_CHECK_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .build();

        Request request = new Request.Builder()
            .url(baseUrl + PING_ENDPOINT)
            .get()
            .build();

        try (Response response = healthClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Hancom AI server at " + baseUrl +
                    " returned HTTP " + response.code());
            }
        } catch (IOException e) {
            throw new IOException(
                "Hancom AI server is not available at " + baseUrl + "\n"
                + "Check that the server is running and accessible.", e);
        }
    }

    @Override
    public HybridResponse convert(HybridRequest request) throws IOException {
        byte[] pdfBytes = request.getPdfBytes();
        this.sourcePdfShaShort = sha256ShortHex(pdfBytes);
        MissingEngines missingEngines = new MissingEngines();
        LOGGER.log(Level.INFO, "Hancom AI: processing PDF ({0} bytes)", pdfBytes.length);

        // Crop / page-image destination travels with the request, not the
        // cached client's config, so the per-document target is correct even
        // when the client is reused across documents (and is concurrency-safe
        // since nothing shared is mutated).
        CropOutput cropOutput = request.getCropOutput();

        try (PageImageCache pageImageCache = createPageImageCache()) {
            ObjectNode merged = objectMapper.createObjectNode();
            ObjectNode timingsNode = objectMapper.createObjectNode();

            // Step 1: DLA + OCR. This is required — downstream steps have nothing
            // to process without it, so treat an empty response as a failure so the
            // caller can fall back to the Java pipeline instead of silently emitting
            // an empty document.
            List<Integer> failedPages1Indexed = new ArrayList<>();
            JsonNode dlaOcrResult = callLayout(pdfBytes, request.getPageNumbers(),
                failedPages1Indexed);
            // Counted in pages rather than array entries: the result is nested,
            // so an empty reply is [[]] and its outer array holds one element.
            // A document sent whole never goes through the per-slice accounting,
            // so this is the only place that catches it on that path.
            if (HancomPageRenumber.pagesOf(dlaOcrResult).isEmpty()) {
                throw new IOException(
                    "Hancom AI " + layoutModule() + " returned empty result — "
                    + "backend unavailable or rejected the document");
            }
            merged.set(LAYOUT_RESULT_KEY, dlaOcrResult);
            // Timings are filed under the module actually called, not the fixed
            // result key: the two layout modules differ ~2.7x in cost, so a
            // shared label would compare unlike things across runs.
            addTimings(timingsNode, layoutModule(), dlaOcrResult);

            // Step 2: Table Structure — crop each Table region from page image, send to TSR individually
            long tsrStartMs = System.currentTimeMillis();
            ArrayNode tsrResults = recognizeTableStructures(pdfBytes, dlaOcrResult, pageImageCache, cropOutput);
            long tsrMs = System.currentTimeMillis() - tsrStartMs;
            merged.set("TABLE_STRUCTURE_RECOGNITION", tsrResults);

            ObjectNode tsrTiming = objectMapper.createObjectNode();
            tsrTiming.put("total_ms", tsrMs);
            tsrTiming.put("count", tsrResults.size());
            if (tsrResults.size() > 0) {
                tsrTiming.put("avg_ms", tsrMs / tsrResults.size());
            }
            timingsNode.set("TABLE_STRUCTURE_RECOGNITION", tsrTiming);

            // Step 3: Formula recognition — crop each equation, read it as LaTeX.
            // Runs before captioning because captionFigures() evicts each page
            // image it visits; a page holding both an equation and a figure
            // would otherwise need a second full-resolution pdf2img render.
            long formulaStartMs = System.currentTimeMillis();
            ArrayNode formulaResults =
                recognizeFormulas(pdfBytes, dlaOcrResult, pageImageCache, cropOutput,
                    missingEngines);
            long formulaMs = System.currentTimeMillis() - formulaStartMs;
            merged.set("FORMULA_RESULTS", formulaResults);

            ObjectNode formulaTiming = objectMapper.createObjectNode();
            formulaTiming.put("total_ms", formulaMs);
            formulaTiming.put("count", formulaResults.size());
            if (formulaResults.size() > 0) {
                formulaTiming.put("avg_ms", formulaMs / formulaResults.size());
            }
            timingsNode.set("FORMULA_RECOGNITION", formulaTiming);

            // Step 4: Figure captioning — pdf2img → crop figures → caption each
            long captionStartMs = System.currentTimeMillis();
            ArrayNode figureCaptions = captionFigures(pdfBytes, dlaOcrResult, pageImageCache,
                cropOutput, missingEngines);
            long captionMs = System.currentTimeMillis() - captionStartMs;
            merged.set("FIGURE_CAPTIONS", figureCaptions);

            // Evidence-report consumers need the same rendered page image that
            // DLA bboxes are expressed against. TSR/FIGURE fetches already save
            // their pages; this pass fills only pages that were not otherwise
            // rendered.
            saveDlaPageImages(pdfBytes, dlaOcrResult, pageImageCache, cropOutput);

            ObjectNode captionTiming = objectMapper.createObjectNode();
            captionTiming.put("total_ms", captionMs);
            captionTiming.put("count", figureCaptions.size());
            if (figureCaptions.size() > 0) {
                captionTiming.put("avg_ms", captionMs / figureCaptions.size());
            }
            timingsNode.set("IMAGE_CAPTIONING", captionTiming);

            merged.set("timings", timingsNode);

            LOGGER.log(Level.INFO,
                "Hancom AI: completed — {0} table crops, {1} figure captions, {2} formulas",
                new Object[]{tsrResults.size(), figureCaptions.size(), formulaResults.size()});

            if (!missingEngines.isEmpty()) {
                LOGGER.log(Level.SEVERE,
                    "Hancom AI: the server does not provide {0} — everything those "
                    + "modules would have produced is missing from this response. "
                    + "Compare the module names against GET /hocr/sdk/openProcess.",
                    missingEngines.toString());
            }

            return new HybridResponse(null, null, merged, Collections.emptyMap(),
                failedPages1Indexed, timingsNode);
        }
    }

    /**
     * Runs DOCUMENT_LAYOUT_WITH_OCR over the requested pages, splitting the
     * document when it holds more pages than the backend takes at once.
     *
     * <p>The module accepts a whole PDF and offers no page-selection parameter,
     * so a page range is expressed by sending a shorter PDF. Each slice's
     * response is numbered from 0 relative to the slice, and is mapped back onto
     * absolute pages before anything downstream sees it — the table, formula and
     * caption passes all take their page number from these records, so getting
     * this right here is what keeps them right.
     *
     * <p>A document that fits in one slice is sent as-is, byte for byte, so the
     * common single-page case behaves exactly as it did before splitting existed.
     *
     * @param pdfBytes            the whole document
     * @param pageNumbers1Indexed pages to process; empty means every page
     * @param failedPages1Indexed collects pages whose slice failed, 1-indexed
     * @return the merged layout result with absolute page numbers
     */
    private JsonNode callLayout(byte[] pdfBytes, Set<Integer> pageNumbers1Indexed,
                                List<Integer> failedPages1Indexed) throws IOException {
        ResolvedPages resolved = resolvePages(pdfBytes, pageNumbers1Indexed);
        List<Integer> pages0Based = resolved.pages;
        int chunk = config.getLayoutPageChunk();

        // A selection that named pages and kept none of them is a caller error.
        // Sending the document whole here would answer with pages nobody asked
        // for, which the caller cannot tell apart from a document that really
        // does have them.
        if (pages0Based.isEmpty()) {
            throw new IOException(
                "None of the requested pages exist in the document ("
                + resolved.pageCount + " pages)");
        }

        // Send the original bytes only when every page is wanted, which keeps
        // that path identical to how it behaved before slicing existed. Turning
        // splitting off means "do not divide the pages", not "ignore which pages
        // were asked for": uploading the whole file for a subset would return
        // records for pages the caller never requested, and the crop passes
        // would go on to process them.
        //
        // Selection size alone is not enough to decide either: 10 pages of a
        // 100-page document fit under the limit, but sending the file whole would
        // upload all 100 and make the backend process them — the work the page
        // limit exists to refuse.
        boolean wholeDocument = pages0Based.size() == resolved.pageCount;
        if (wholeDocument && (chunk <= 0 || pages0Based.size() <= chunk)) {
            JsonNode whole = callModule(pdfBytes, layoutModule());
            // Page numbers are already absolute on this path — the backend saw
            // the whole file — so there is nothing to renumber, but the pages
            // still have to be accounted for. A reply covering fewer pages than
            // were asked for would otherwise leave the rest as blank pages that
            // the Java pipeline never revisits.
            reportUnplacedPages(HancomPageRenumber.pagesOf(whole), pages0Based,
                failedPages1Indexed);
            return whole;
        }

        // Reaching here with splitting off means a page subset was asked for: it
        // goes as a single slice, so the whole selection is one step. A
        // non-positive step would leave the loop below standing still.
        int step = chunk > 0 ? chunk : pages0Based.size();

        LOGGER.log(Level.INFO, "Hancom AI: sending {0} pages in requests of {1}",
            new Object[]{pages0Based.size(), step});

        List<JsonNode> sliceResults = new ArrayList<>();
        for (int start = 0; start < pages0Based.size(); start += step) {
            List<Integer> slice =
                pages0Based.subList(start, Math.min(start + step, pages0Based.size()));
            JsonNode sliceResult = null;
            try {
                byte[] slicePdf = HancomPdfPageSlicer.extractPages(pdfBytes, slice);
                sliceResult = callModule(slicePdf, layoutModule(), slice);
            } catch (IOException | RuntimeException e) {
                // A slice that fails to build or send is a per-slice problem:
                // losing 20 pages must not cost the other 200, so the failure is
                // contained and those pages fall back to the Java pipeline.
                //
                // RuntimeException is included because a malformed page tree
                // reaches us that way, not as IOException — PDFBox reads the
                // page count from /Count, which a damaged document can leave
                // disagreeing with the real tree, and the page fetch then throws
                // IllegalStateException. Letting that escape would cost the whole
                // document the backend path, which is exactly what slicing per
                // page range is meant to avoid. Errors still propagate.
                LOGGER.log(Level.WARNING, "Layout slice starting at page {0} failed: {1}",
                    new Object[]{slice.get(0) + 1, e.toString()});
            }

            // Counted in pages, not array entries: the layout RESULT is nested,
            // so an empty reply is [[]] — one outer entry holding nothing. An
            // array-size check passes that as a success and the slice's pages
            // reach the output as blank pages that nothing ever retries.
            JsonNode placed = sliceResult == null
                ? null
                : HancomPageRenumber.toAbsolutePages(sliceResult, slice, objectMapper);
            List<JsonNode> placedPages = placed == null
                ? Collections.emptyList()
                : HancomPageRenumber.pagesOf(placed);

            if (!placedPages.isEmpty()) {
                sliceResults.add(placed);
            }

            // Any page of this slice with no record of its own is reported so the
            // caller can retry it, rather than being left as an empty page. This
            // covers a slice that failed outright, one that came back holding no
            // pages, and one that came back short.
            reportUnplacedPages(placedPages, slice, failedPages1Indexed);
        }

        if (sliceResults.isEmpty()) {
            // Nothing came back at all. Returning an empty result here would read
            // as "a document with no content"; the caller needs the failure so the
            // whole document falls back to the Java pipeline.
            return null;
        }
        if (!failedPages1Indexed.isEmpty()) {
            LOGGER.log(Level.WARNING,
                "Hancom AI: {0} of {1} pages failed layout and will fall back",
                new Object[]{failedPages1Indexed.size(), pages0Based.size()});
        }
        return HancomPageRenumber.merge(sliceResults, objectMapper);
    }

    /**
     * Records every expected page that came back without a layout record.
     *
     * <p>A page reported here is retried by the Java pipeline. Left unreported it
     * becomes an empty page in the output, which reads as a page that genuinely
     * had no content — so silence is the worse outcome, whether the pages went
     * missing from one slice or from a whole-document reply.
     *
     * @param placedPages         layout records that arrived, with absolute page numbers
     * @param expectedPages0Based the absolute 0-based pages that were asked for
     * @param failedPages1Indexed collects the unplaced pages, 1-indexed
     */
    private static void reportUnplacedPages(List<JsonNode> placedPages,
                                            List<Integer> expectedPages0Based,
                                            List<Integer> failedPages1Indexed) {
        if (placedPages.size() >= expectedPages0Based.size()) {
            return;
        }
        Set<Integer> placedPageNumbers = new HashSet<>();
        for (JsonNode page : placedPages) {
            placedPageNumbers.add(page.has("page_number")
                ? page.get("page_number").asInt(-1) : -1);
        }
        for (int page0 : expectedPages0Based) {
            if (!placedPageNumbers.contains(page0)) {
                failedPages1Indexed.add(page0 + 1);
            }
        }
    }

    /**
     * The absolute 0-based pages to process, in ascending order.
     *
     * <p>Request page numbers are 1-indexed by contract. An empty set means the
     * whole document, so the page count is read from the PDF itself.
     */
    private ResolvedPages resolvePages(byte[] pdfBytes, Set<Integer> pageNumbers1Indexed)
            throws IOException {
        List<Integer> pages0Based = new ArrayList<>();
        int documentPages;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = document.getNumberOfPages();
            documentPages = pageCount;
            if (pageNumbers1Indexed == null || pageNumbers1Indexed.isEmpty()) {
                for (int page0 = 0; page0 < pageCount; page0++) {
                    pages0Based.add(page0);
                }
                return new ResolvedPages(pages0Based, pageCount);
            }
            // Filtered against the real page count here, before slicing. The
            // slicer skips pages it cannot find, so an unfiltered list would
            // leave the slice list longer than the PDF built from it — and since
            // response page k is looked up as slice.get(k), every page after the
            // gap would be attributed to the wrong page.
            for (int page1 : new TreeSet<>(pageNumbers1Indexed)) {
                int page0 = page1 - 1;
                if (page0 < 0 || page0 >= pageCount) {
                    LOGGER.log(Level.WARNING,
                        "Ignoring requested page {0}: document has {1} pages",
                        new Object[]{page1, pageCount});
                    continue;
                }
                pages0Based.add(page0);
            }
        }
        return new ResolvedPages(pages0Based, documentPages);
    }

    /** Pages to send, alongside how many pages the document actually holds. */
    private static final class ResolvedPages {
        final List<Integer> pages;
        final int pageCount;

        ResolvedPages(List<Integer> pages, int pageCount) {
            this.pages = pages;
            this.pageCount = pageCount;
        }
    }

    /**
     * Creates a PageImageCache based on config.
     */
    private PageImageCache createPageImageCache() throws IOException {
        if ("disk".equalsIgnoreCase(config.getImageCache())) {
            return new DiskPageImageCache();
        }
        return new MemoryPageImageCache();
    }

    @Override
    public CompletableFuture<HybridResponse> convertAsync(HybridRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return convert(request);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to convert via Hancom AI", e);
            }
        });
    }

    /**
     * Captions each Figure found by DLA:
     * 1. Get page images via pdf2img
     * 2. Find Figure objects (label 10) from DLA results
     * 3. Crop each Figure from page image
     * 4. Send cropped image to IMAGE_CAPTIONING
     *
     * @return ArrayNode of {page_number, object_id, bbox, caption}
     */
    private ArrayNode captionFigures(byte[] pdfBytes, JsonNode dlaResult,
                                     PageImageCache pageImageCache, CropOutput cropOutput,
                                     MissingEngines missingEngines) {
        ArrayNode captions = objectMapper.createArrayNode();

        // Extract pages from DLA result
        List<JsonNode> dlaPages = extractPages(dlaResult);
        if (dlaPages.isEmpty()) return captions;

        // Collect visual objects per page. The same graphic is occasionally
        // reported under two of the three visual labels; captioning it twice
        // would spend a GPU call per duplicate and hand the transformer two
        // captions for one picture, so drop the weaker detection here using the
        // same rule the transformer applies.
        Map<Integer, List<IndexedObject>> figuresByPage = new HashMap<>();
        for (JsonNode page : dlaPages) {
            int pageNum = page.has("page_number") ? page.get("page_number").asInt() : -1;
            if (pageNum < 0) continue;

            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            java.util.Set<Integer> duplicates =
                HancomAISchemaTransformer.findDuplicateVisualIndexes(objects);

            int objectIndex = -1;
            for (JsonNode obj : objects) {
                objectIndex++;
                int label = obj.has("label") ? obj.get("label").asInt() : -1;
                if (!HancomAISchemaTransformer.isVisualLabel(label)) continue;
                if (duplicates.contains(objectIndex)) continue;
                figuresByPage.computeIfAbsent(pageNum, k -> new ArrayList<>())
                    .add(new IndexedObject(obj, objectIndex));
            }
        }

        if (figuresByPage.isEmpty()) {
            LOGGER.log(Level.INFO, "Hancom AI: no Figure objects found, skipping captioning");
            return captions;
        }

        LOGGER.log(Level.INFO, "Hancom AI: captioning {0} figures across {1} pages",
            new Object[]{figuresByPage.values().stream().mapToInt(List::size).sum(),
                          figuresByPage.size()});

        for (Map.Entry<Integer, List<IndexedObject>> entry : figuresByPage.entrySet()) {
            int pageNum = entry.getKey();
            List<IndexedObject> figures = entry.getValue();

            // Get page image via cache
            BufferedImage pageImage;
            try {
                pageImage = pageImageCache.getOrFetch(pageNum,
                    idx -> fetchPageImage(pdfBytes, idx, cropOutput));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to get page {0} image: {1}",
                    new Object[]{pageNum, e.getMessage()});
                continue;
            }

            // Caption each figure
            for (IndexedObject figure : figures) {
                JsonNode fig = figure.node;
                JsonNode bboxNode = fig.get("bbox");
                if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() < 4) continue;

                int left = bboxNode.get(0).asInt();
                int top = bboxNode.get(1).asInt();
                int right = bboxNode.get(2).asInt();
                int bottom = bboxNode.get(3).asInt();

                // Clamp to image bounds
                left = Math.max(0, left);
                top = Math.max(0, top);
                right = Math.min(pageImage.getWidth(), right);
                bottom = Math.min(pageImage.getHeight(), bottom);

                if (right <= left || bottom <= top) continue;

                try {
                    BufferedImage cropped = pageImage.getSubimage(left, top, right - left, bottom - top);
                    byte[] croppedPng = imageToPng(cropped);

                    // Save crop if configured
                    if (cropOutput.active()) {
                        int objId = fig.has("object_id") ? fig.get("object_id").asInt() : -1;
                        saveCropFile(cropOutput.directory(), pageNum, objId, "figure", croppedPng);
                    }

                    int objIdForCaption = fig.has("object_id") ? fig.get("object_id").asInt() : -1;
                    CaptionResult captionResult =
                        callImageCaptioning(croppedPng, pageNum, objIdForCaption, missingEngines);
                    String caption = captionResult != null ? captionResult.caption : null;

                    ObjectNode capNode = objectMapper.createObjectNode();
                    capNode.put("page_number", pageNum);
                    capNode.put("object_id", fig.has("object_id") ? fig.get("object_id").asInt() : -1);
                    capNode.put(OBJECT_INDEX_FIELD, figure.index);
                    ArrayNode bboxArr = objectMapper.createArrayNode();
                    bboxArr.add(left).add(top).add(right).add(bottom);
                    capNode.set("bbox", bboxArr);
                    capNode.put("caption", caption != null ? caption : "");
                    if (captionResult != null && captionResult.confidence != null) {
                        capNode.put("confidence", captionResult.confidence);
                    }
                    captions.add(capNode);

                    LOGGER.log(Level.FINE, "Captioned figure page={0} bbox=[{1},{2},{3},{4}]: {5}",
                        new Object[]{pageNum, left, top, right, bottom,
                            caption != null ? caption.substring(0, Math.min(50, caption.length())) : ""});
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to caption figure on page {0}: {1}",
                        new Object[]{pageNum, e.getMessage()});
                }
            }

            // Done with this page — allow cache to reclaim memory
            pageImageCache.evict(pageNum);
        }

        return captions;
    }

    /**
     * For each Table region (label 7) found by DLA, crop the region from the
     * page image and send the crop to TABLE_STRUCTURE_RECOGNITION individually.
     *
     * <p>When regionlist strategy is "list-only", label 7 is always treated as a
     * list and TSR is skipped entirely.
     *
     * @param pdfBytes the original PDF bytes (needed for pdf2img)
     * @param dlaResult the DLA+OCR result containing detected objects
     * @param pageImageCache shared cache for page images
     * @param cropOutput per-document destination for saved table crops
     * @return ArrayNode of per-table results:
     *         [{page_number, object_id, label, dla_bbox, tsr: {cells, num_cells, html, ...}}]
     */
    private ArrayNode recognizeTableStructures(byte[] pdfBytes, JsonNode dlaResult,
                                                PageImageCache pageImageCache, CropOutput cropOutput) {
        ArrayNode results = objectMapper.createArrayNode();

        // In list-only mode, LABEL_REGIONLIST is always rendered as a list and
        // does not need TSR. LABEL_TABLE still needs TSR — without it the
        // transformer's LABEL_TABLE branch returns null and real tables drop out
        // of the structured output entirely.
        boolean skipRegionlistTsr = config.isRegionlistListOnly();
        if (skipRegionlistTsr) {
            LOGGER.log(Level.INFO, "Hancom AI: regionlist strategy is list-only, "
                + "skipping TSR for Regionlist (label 7); Table (label 9) TSR still runs");
        }

        List<JsonNode> dlaPages = extractPages(dlaResult);
        if (dlaPages.isEmpty()) return results;

        for (JsonNode page : dlaPages) {
            int pageNum = page.has("page_number") ? page.get("page_number").asInt() : -1;
            if (pageNum < 0) continue;

            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            // Check if any table/regionlist objects exist on this page
            boolean needsPageImage = false;
            for (JsonNode obj : objects) {
                int label = obj.has("label") ? obj.get("label").asInt() : -1;
                if (label == LABEL_TABLE
                        || (label == LABEL_REGIONLIST && !skipRegionlistTsr)) {
                    needsPageImage = true;
                    break;
                }
            }
            if (!needsPageImage) continue;

            // Get page image
            BufferedImage pageImage;
            try {
                pageImage = pageImageCache.getOrFetch(pageNum,
                    idx -> fetchPageImage(pdfBytes, idx, cropOutput));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to get page {0} image for TSR: {1}",
                    new Object[]{pageNum, e.getMessage()});
                continue;
            }

            int imgWidth = pageImage.getWidth();
            int imgHeight = pageImage.getHeight();

            for (JsonNode obj : objects) {
                int label = obj.has("label") ? obj.get("label").asInt() : -1;
                if (label != LABEL_REGIONLIST && label != LABEL_TABLE) continue;
                if (label == LABEL_REGIONLIST && skipRegionlistTsr) continue;

                JsonNode bboxNode = obj.get("bbox");
                if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() < 4) continue;

                int left = bboxNode.get(0).asInt();
                int top = bboxNode.get(1).asInt();
                int right = bboxNode.get(2).asInt();
                int bottom = bboxNode.get(3).asInt();

                // Add padding around crop
                left = Math.max(0, left - TSR_CROP_PADDING);
                top = Math.max(0, top - TSR_CROP_PADDING);
                right = Math.min(imgWidth, right + TSR_CROP_PADDING);
                bottom = Math.min(imgHeight, bottom + TSR_CROP_PADDING);

                if (right <= left || bottom <= top) continue;

                try {
                    BufferedImage crop = pageImage.getSubimage(left, top, right - left, bottom - top);
                    byte[] cropPng = imageToPng(crop);

                    // Save crop if configured
                    if (cropOutput.active()) {
                        int objectId = obj.has("object_id") ? obj.get("object_id").asInt() : -1;
                        saveCropFile(cropOutput.directory(), pageNum, objectId, "table", cropPng);
                    }

                    // Call TSR with crop image
                    int objId = obj.has("object_id") ? obj.get("object_id").asInt() : -1;
                    JsonNode tsrResult = callModuleImage(cropPng, "TABLE_STRUCTURE_RECOGNITION", pageNum, objId);

                    // Build result entry
                    ObjectNode entry = objectMapper.createObjectNode();
                    entry.put("page_number", pageNum);
                    entry.put("object_id",
                        obj.has("object_id") ? obj.get("object_id").asInt() : -1);
                    entry.put("label", label);

                    // Store the DLA bbox (padded, page-level pixels) for coordinate offset later
                    ArrayNode dlaBbox = objectMapper.createArrayNode();
                    dlaBbox.add(left).add(top).add(right).add(bottom);
                    entry.set("dla_bbox", dlaBbox);

                    // Extract TSR page result. The HOCR envelope wraps results
                    // as RESULT=[[page]], so the page node is where any
                    // top-level self-score lands.
                    List<JsonNode> tsrPages = extractPages(tsrResult);
                    if (!tsrPages.isEmpty()) {
                        JsonNode tsrPage = tsrPages.get(0);
                        JsonNode conf = tsrPage.get("confidence");
                        if (conf != null && conf.isNumber()) {
                            // doubleValue() returns the numeric value directly;
                            // asDouble() has a silent 0.0 fallback we don't want
                            // even though the isNumber() guard makes it unreachable.
                            entry.put("confidence", conf.doubleValue());
                        }
                        entry.set("tsr", tsrPage);
                    } else {
                        entry.set("tsr", objectMapper.createObjectNode());
                    }

                    results.add(entry);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "TSR failed for page {0} object: {1}",
                        new Object[]{pageNum, e.getMessage()});
                }
            }
            // Evict the page image here only if captionFigures() will not revisit
            // this page. captionFigures() iterates pages that contain at least one
            // visual or equation object; for table-only pages the cached
            // full-resolution image would otherwise sit in memory until the
            // try-with-resources in convert() closes the cache — ~25MB per page
            // for the memory cache, which is costly on large table-heavy PDFs.
            boolean revisited = false;
            for (JsonNode obj : objects) {
                int objLabel = obj.has("label") ? obj.get("label").asInt() : -1;
                if (HancomAISchemaTransformer.isVisualLabel(objLabel)
                        || objLabel == LABEL_EQUATION) {
                    revisited = true;
                    break;
                }
            }
            if (!revisited) {
                pageImageCache.evict(pageNum);
            }
        }

        LOGGER.log(Level.INFO, "Hancom AI: TSR processed {0} table crops", results.size());
        return results;
    }

    /**
     * Calls a single HOCR SDK module with image (PNG) input.
     * Similar to {@link #callModule} but sends image data instead of PDF.
     */
    private JsonNode callModuleImage(byte[] pngBytes, String moduleName, int pageNum, int objectId) throws IOException {
        String moduleShort = MODULE_SHORT.getOrDefault(moduleName, moduleName);
        String requestId = "odl-" + sourcePdfShaShort + "-p" + pageNum + "-o" + objectId + "-" + moduleShort;
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID", requestId)
            .addFormDataPart("OPEN_API_NAME", moduleName)
            .addFormDataPart("DATA_FORMAT", "image")
            .addFormDataPart("FILE", "crop.png",
                RequestBody.create(pngBytes, MEDIA_TYPE_PNG))
            .build();

        Request httpRequest = new Request.Builder()
            .url(baseUrl + SDK_ENDPOINT)
            .post(body)
            .build();

        LOGGER.log(Level.FINE, "Calling Hancom AI module (image): {0} [{1}]",
            new Object[]{moduleName, requestId});

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody respBody = response.body();
                String errorMsg = respBody != null ? respBody.string() : "";
                LOGGER.log(Level.WARNING, "Hancom AI module {0} (image) returned HTTP {1}: {2}",
                    new Object[]{moduleName, response.code(), errorMsg});
                return objectMapper.createArrayNode();
            }

            ResponseBody respBody = response.body();
            if (respBody == null) {
                return objectMapper.createArrayNode();
            }

            JsonNode root = objectMapper.readTree(respBody.string());
            boolean success = root.has("SUCCESS") && root.get("SUCCESS").asBoolean();
            if (!success) {
                LOGGER.log(Level.WARNING, "Hancom AI module {0} (image) returned SUCCESS=false: {1}",
                    new Object[]{moduleName, root.has("MSG") ? root.get("MSG").asText() : ""});
                return objectMapper.createArrayNode();
            }

            JsonNode result = root.get("RESULT");
            return result != null ? result : objectMapper.createArrayNode();
        }
    }

    /**
     * Saves full-page render images for every DLA page when evidence image
     * capture is enabled.
     */
    private void saveDlaPageImages(byte[] pdfBytes, JsonNode dlaResult,
                                   PageImageCache pageImageCache, CropOutput cropOutput) {
        if (!cropOutput.active()) return;

        for (JsonNode page : extractPages(dlaResult)) {
            int pageNum = page.has("page_number") ? page.get("page_number").asInt() : -1;
            if (pageNum < 0) continue;
            if (isPageImageFileSaved(cropOutput.directory(), pageNum)) continue;
            try {
                pageImageCache.getOrFetch(pageNum, idx -> fetchPageImage(pdfBytes, idx, cropOutput));
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Failed to save DLA page image for page "
                    + pageNum);
            } finally {
                pageImageCache.evict(pageNum);
            }
        }
    }

    private boolean isPageImageFileSaved(String outputDir, int pageNum) {
        if (outputDir == null) return false;
        File file = new File(new File(outputDir, "page-images"),
            String.format("page-%d.png", pageNum));
        return file.isFile();
    }

    /**
     * Saves a cropped image to disk for debugging.
     */
    private void saveCropFile(String outputDir, int pageNum, int objectId,
                              String labelName, byte[] pngBytes) {
        try {
            File dir = new File(outputDir, "crops");
            dir.mkdirs();
            String filename = String.format("page-%d_%s-o%d.png", pageNum, labelName, objectId);
            Files.write(new File(dir, filename).toPath(), pngBytes);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to save crop file");
        }
    }

    /**
     * Saves a full rendered PDF page image to disk for evidence overlays.
     */
    private void savePageImageFile(String outputDir, int pageNum, byte[] pngBytes) {
        try {
            File dir = new File(outputDir, "page-images");
            dir.mkdirs();
            String filename = String.format("page-%d.png", pageNum);
            Files.write(new File(dir, filename).toPath(), pngBytes);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to save page image file");
        }
    }

    /**
     * Fetches a page image from the pdf2img endpoint.
     */
    private BufferedImage fetchPageImage(byte[] pdfBytes, int pageIndex, CropOutput cropOutput)
            throws IOException {
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID",
                "odl-" + sourcePdfShaShort + "-pdf2img-p" + pageIndex)
            .addFormDataPart("PAGE_INDEX", String.valueOf(pageIndex))
            .addFormDataPart("FILE", DEFAULT_FILENAME,
                RequestBody.create(pdfBytes, MEDIA_TYPE_PDF))
            .build();

        Request httpRequest = new Request.Builder()
            .url(baseUrl + PDF2IMG_ENDPOINT)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("pdf2img returned HTTP " + response.code());
            }

            ResponseBody respBody = response.body();
            if (respBody == null) {
                throw new IOException("pdf2img returned empty body");
            }

            JsonNode root = objectMapper.readTree(respBody.string());
            // Navigate: RESULT[0].RESULT.PAGE_PNG_DATA
            JsonNode resultArr = root.get("RESULT");
            if (resultArr == null || !resultArr.isArray() || resultArr.size() == 0) {
                throw new IOException("pdf2img RESULT is empty");
            }

            JsonNode pageResult = resultArr.get(0);
            JsonNode innerResult = pageResult.get("RESULT");
            if (innerResult == null) {
                throw new IOException("pdf2img inner RESULT is null");
            }

            String pngBase64 = innerResult.has("PAGE_PNG_DATA")
                ? innerResult.get("PAGE_PNG_DATA").asText() : null;
            if (pngBase64 == null || pngBase64.isEmpty()) {
                throw new IOException("pdf2img PAGE_PNG_DATA is empty");
            }

            byte[] pngBytes;
            try {
                pngBytes = Base64.getDecoder().decode(pngBase64);
            } catch (IllegalArgumentException e) {
                // fetchPageImage is declared to throw IOException and callers catch
                // only IOException. Escaping IAE would abort the whole conversion
                // instead of skipping the failed page.
                throw new IOException("pdf2img PAGE_PNG_DATA is not valid Base64", e);
            }
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (image == null) {
                throw new IOException("pdf2img PAGE_PNG_DATA is not a readable image");
            }
            if (cropOutput.active()) {
                savePageImageFile(cropOutput.directory(), pageIndex, pngBytes);
            }
            return image;
        }
    }

    /**
     * Sends a cropped image to IMAGE_CAPTIONING and returns the caption text.
     */
    /** Image-captioning result: caption text + the model's self-reported confidence. */
    static final class CaptionResult {
        final String caption;
        final Double confidence;
        CaptionResult(String caption, Double confidence) {
            this.caption = caption;
            this.confidence = confidence;
        }
    }

    /**
     * For each Equation region (label 12) found by DLA, crops the region from
     * the page image and runs FORMULA_RECOGNITION on it.
     *
     * <p>Without this the transformer falls back to the region's OCR text, which
     * loses the structure of the expression — OCR reads an integral sign as a
     * character, while this returns LaTeX.
     *
     * @return ArrayNode of {page_number, object_id, formula}
     */
    private ArrayNode recognizeFormulas(byte[] pdfBytes, JsonNode dlaResult,
                                        PageImageCache pageImageCache, CropOutput cropOutput,
                                        MissingEngines missingEngines) {
        ArrayNode formulas = objectMapper.createArrayNode();

        List<JsonNode> dlaPages = extractPages(dlaResult);
        if (dlaPages.isEmpty()) return formulas;

        Map<Integer, List<IndexedObject>> equationsByPage = new HashMap<>();
        // Pages captionFigures() will render anyway; those must stay cached.
        java.util.Set<Integer> visualPages = new java.util.HashSet<>();
        for (JsonNode page : dlaPages) {
            int pageNum = page.has("page_number") ? page.get("page_number").asInt() : -1;
            if (pageNum < 0) continue;

            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            int objectIndex = -1;
            for (JsonNode obj : objects) {
                objectIndex++;
                int label = obj.has("label") ? obj.get("label").asInt() : -1;
                if (label == LABEL_EQUATION) {
                    equationsByPage.computeIfAbsent(pageNum, k -> new ArrayList<>())
                        .add(new IndexedObject(obj, objectIndex));
                } else if (HancomAISchemaTransformer.isVisualLabel(label)) {
                    visualPages.add(pageNum);
                }
            }
        }

        if (equationsByPage.isEmpty()) {
            return formulas;
        }

        LOGGER.log(Level.INFO, "Hancom AI: recognizing {0} equations across {1} pages",
            new Object[]{equationsByPage.values().stream().mapToInt(List::size).sum(),
                         equationsByPage.size()});

        for (Map.Entry<Integer, List<IndexedObject>> entry : equationsByPage.entrySet()) {
            int pageNum = entry.getKey();

            BufferedImage pageImage;
            try {
                pageImage = pageImageCache.getOrFetch(pageNum,
                    idx -> fetchPageImage(pdfBytes, idx, cropOutput));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to get page {0} image: {1}",
                    new Object[]{pageNum, e.getMessage()});
                continue;
            }

            for (IndexedObject equation : entry.getValue()) {
                JsonNode eq = equation.node;
                JsonNode bboxNode = eq.get("bbox");
                if (bboxNode == null || !bboxNode.isArray() || bboxNode.size() < 4) continue;

                int left = Math.max(0, bboxNode.get(0).asInt());
                int top = Math.max(0, bboxNode.get(1).asInt());
                int right = Math.min(pageImage.getWidth(), bboxNode.get(2).asInt());
                int bottom = Math.min(pageImage.getHeight(), bboxNode.get(3).asInt());
                if (right <= left || bottom <= top) continue;

                int objectId = eq.has("object_id") ? eq.get("object_id").asInt() : -1;
                try {
                    BufferedImage cropped =
                        pageImage.getSubimage(left, top, right - left, bottom - top);
                    byte[] croppedPng = imageToPng(cropped);

                    if (cropOutput.active()) {
                        saveCropFile(cropOutput.directory(), pageNum, objectId,
                            "equation", croppedPng);
                    }

                    String latex =
                        callFormulaRecognition(croppedPng, pageNum, objectId, missingEngines);
                    if (latex == null) continue;

                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("page_number", pageNum);
                    node.put("object_id", objectId);
                    node.put(OBJECT_INDEX_FIELD, equation.index);
                    // The clamped crop rectangle, as the caption results carry:
                    // evidence tooling shows the region a result came from, and
                    // without it the formula row has nowhere to point.
                    ArrayNode bboxArr = objectMapper.createArrayNode();
                    bboxArr.add(left).add(top).add(right).add(bottom);
                    node.set("bbox", bboxArr);
                    node.put("formula", latex);
                    formulas.add(node);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to recognize formula on page {0}: {1}",
                        new Object[]{pageNum, e.getMessage()});
                }
            }

            // Release the page unless captionFigures() still has to visit it,
            // so equation-only pages do not hold a ~25MB render until convert()
            // closes the cache.
            if (!visualPages.contains(pageNum)) {
                pageImageCache.evict(pageNum);
            }
        }

        return formulas;
    }

    /**
     * Detects the "module does not exist" reply, which arrives as HTTP 200 with
     * a true top-level SUCCESS and is otherwise indistinguishable from a region
     * the engine had nothing to say about.
     */
    private boolean reportsMissingEngine(JsonNode page, String moduleName,
                                         MissingEngines missingEngines) {
        if (page == null) {
            return false;
        }
        boolean noEngine = page.has("MSG")
            && MSG_NO_ENGINE.equalsIgnoreCase(page.get("MSG").asText("").trim());
        if (noEngine) {
            missingEngines.add(moduleName);
            LOGGER.log(Level.WARNING,
                "Hancom AI module {0} is not available on the server "
                + "(response: \"{1}\") — check the module name against /hocr/sdk/openProcess",
                new Object[]{moduleName, page.has("MSG") ? page.get("MSG").asText("") : ""});
            return true;
        }
        return false;
    }

    /**
     * Whether a page declares itself failed.
     *
     * <p>{@code IS_SUCCESS:false} is how the server marks a page it could not
     * process. It accompanies the missing-engine reply but is not limited to
     * it, and a failed page can still carry a populated {@code caption} or
     * {@code formula} field — so the flag has to be honoured on its own rather
     * than inferred from the message.
     */
    private boolean reportsFailure(JsonNode page, String moduleName) {
        if (page == null || !page.has("IS_SUCCESS")) {
            return false;
        }
        if (page.get("IS_SUCCESS").asBoolean(true)) {
            return false;
        }
        LOGGER.log(Level.WARNING,
            "Hancom AI module {0} reported a failed page (response: \"{1}\") — "
            + "ignoring its output",
            new Object[]{moduleName, page.has("MSG") ? page.get("MSG").asText("") : ""});
        return true;
    }

    /**
     * Sends a cropped equation region to FORMULA_RECOGNITION.
     *
     * @return the LaTeX string, or {@code null} when unavailable
     */
    private String callFormulaRecognition(byte[] pngBytes, int pageNum, int objectId,
                                         MissingEngines missingEngines) throws IOException {
        String requestId = "odl-" + sourcePdfShaShort + "-p" + pageNum + "-o" + objectId
            + "-formula";
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID", requestId)
            .addFormDataPart("OPEN_API_NAME", FORMULA_MODULE)
            .addFormDataPart("DATA_FORMAT", "image")
            .addFormDataPart("FILE", "equation.png",
                RequestBody.create(pngBytes, MEDIA_TYPE_PNG))
            .build();

        Request httpRequest = new Request.Builder()
            .url(baseUrl + SDK_ENDPOINT)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) return null;

            ResponseBody respBody = response.body();
            if (respBody == null) return null;

            JsonNode root = objectMapper.readTree(respBody.string());
            if (!root.has("SUCCESS") || !root.get("SUCCESS").asBoolean()) return null;

            JsonNode result = root.get("RESULT");
            if (result == null || !result.isArray() || result.size() == 0) return null;

            JsonNode page = result.get(0);
            if (page.isArray() && page.size() > 0) page = page.get(0);

            if (reportsMissingEngine(page, FORMULA_MODULE, missingEngines)) return null;
            if (reportsFailure(page, FORMULA_MODULE)) return null;

            // Blank counts as nothing recognized: returning whitespace would
            // suppress the transformer's fall back to the region's OCR text and
            // leave an empty formula in its place.
            String formula = page.has("formula") ? page.get("formula").asText("") : null;
            return formula == null || formula.trim().isEmpty() ? null : formula;
        }
    }

    private CaptionResult callImageCaptioning(byte[] pngBytes, int pageNum, int objectId,
                                             MissingEngines missingEngines) throws IOException {
        String requestId = "odl-" + sourcePdfShaShort + "-p" + pageNum + "-o" + objectId + "-caption";
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID", requestId)
            .addFormDataPart("OPEN_API_NAME", CAPTION_MODULE)
            .addFormDataPart("DATA_FORMAT", "image")
            .addFormDataPart("FILE", "figure.png",
                RequestBody.create(pngBytes, MEDIA_TYPE_PNG))
            .build();

        Request httpRequest = new Request.Builder()
            .url(baseUrl + SDK_ENDPOINT)
            .post(body)
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) return null;

            ResponseBody respBody = response.body();
            if (respBody == null) return null;

            JsonNode root = objectMapper.readTree(respBody.string());
            if (!root.has("SUCCESS") || !root.get("SUCCESS").asBoolean()) return null;

            JsonNode result = root.get("RESULT");
            if (result == null || !result.isArray() || result.size() == 0) return null;

            JsonNode page = result.get(0);
            if (page.isArray() && page.size() > 0) page = page.get(0);

            if (reportsMissingEngine(page, CAPTION_MODULE, missingEngines)) return null;
            if (reportsFailure(page, CAPTION_MODULE)) return null;

            String caption = page.has("caption") ? page.get("caption").asText("") : null;
            JsonNode confNode = page.get("confidence");
            Double confidence = confNode != null && confNode.isNumber()
                ? confNode.doubleValue() : null;
            return new CaptionResult(caption, confidence);
        }
    }

    /**
     * Calls a single HOCR SDK module with PDF input.
     */
    private JsonNode callModule(byte[] pdfBytes, String moduleName) throws IOException {
        return callModule(pdfBytes, moduleName, null);
    }

    /**
     * Calls a PDF-input module.
     *
     * <p>{@code slice}, when given, names the absolute pages this PDF holds and is
     * recorded in the REQUEST_ID. The uploaded bytes are a slice, so its hash
     * identifies neither the source document nor the pages within it; carrying
     * both in the request id keeps every call traceable to real page numbers, on
     * the server's side as well as ours.
     */
    private JsonNode callModule(byte[] pdfBytes, String moduleName, List<Integer> slice)
            throws IOException {
        String requestId = "odl-" + sourcePdfShaShort + "-"
            + MODULE_SHORT.getOrDefault(moduleName, moduleName);
        if (slice != null) {
            requestId += "-p" + slice.get(0) + "-" + slice.get(slice.size() - 1);
        }
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID", requestId)
            .addFormDataPart("OPEN_API_NAME", moduleName)
            .addFormDataPart("DATA_FORMAT", "pdf")
            .addFormDataPart("FILE", DEFAULT_FILENAME,
                RequestBody.create(pdfBytes, MEDIA_TYPE_PDF))
            .build();

        Request httpRequest = new Request.Builder()
            .url(baseUrl + SDK_ENDPOINT)
            .post(body)
            .build();

        LOGGER.log(Level.INFO, "Calling Hancom AI module: {0}", moduleName);

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                ResponseBody respBody = response.body();
                String errorMsg = respBody != null ? respBody.string() : "";
                LOGGER.log(Level.WARNING, "Hancom AI module {0} returned HTTP {1}: {2}",
                    new Object[]{moduleName, response.code(), errorMsg});
                return objectMapper.createArrayNode();
            }

            ResponseBody respBody = response.body();
            if (respBody == null) {
                return objectMapper.createArrayNode();
            }

            JsonNode root = objectMapper.readTree(respBody.string());
            boolean success = root.has("SUCCESS") && root.get("SUCCESS").asBoolean();
            if (!success) {
                LOGGER.log(Level.WARNING, "Hancom AI module {0} returned SUCCESS=false: {1}",
                    new Object[]{moduleName, root.has("MSG") ? root.get("MSG").asText() : ""});
                return objectMapper.createArrayNode();
            }

            JsonNode result = root.get("RESULT");
            return result != null ? result : objectMapper.createArrayNode();
        }
    }

    // --- Helpers ---

    private List<JsonNode> extractPages(JsonNode moduleResult) {
        List<JsonNode> pages = new ArrayList<>();
        if (moduleResult == null || !moduleResult.isArray()) return pages;
        JsonNode inner = moduleResult.size() > 0 && moduleResult.get(0).isArray()
            ? moduleResult.get(0) : moduleResult;
        for (JsonNode page : inner) {
            if (page.isObject()) pages.add(page);
        }
        return pages;
    }

    private byte[] imageToPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }

    private void addTimings(ObjectNode timingsNode, String moduleName, JsonNode result) {
        long totalMs = 0;
        int pageCount = 0;
        if (result.isArray() && result.size() > 0) {
            JsonNode pages = result.get(0).isArray() ? result.get(0) : result;
            for (JsonNode page : pages) {
                if (page.has("run_time")) {
                    totalMs += page.get("run_time").asLong();
                    pageCount++;
                }
            }
        }
        ObjectNode timing = objectMapper.createObjectNode();
        timing.put("total_ms", totalMs);
        timing.put("count", pageCount);
        if (pageCount > 0) timing.put("avg_ms", totalMs / pageCount);
        timingsNode.set(moduleName, timing);
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void shutdown() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
        if (httpClient.cache() != null) {
            try { httpClient.cache().close(); } catch (Exception ignored) { }
        }
    }

    // --- Test hooks (package-private) ---

    void invokeCallModule(byte[] pdfBytes, String moduleName) throws IOException {
        this.sourcePdfShaShort = sha256ShortHex(pdfBytes);
        callModule(pdfBytes, moduleName);
    }

    CaptionResult invokeCallImageCaptioning(byte[] pngBytes, int pageNum, int objectId)
            throws IOException {
        return callImageCaptioning(pngBytes, pageNum, objectId, new MissingEngines());
    }

    String invokeCallFormulaRecognition(byte[] pngBytes, int pageNum, int objectId)
            throws IOException {
        return callFormulaRecognition(pngBytes, pageNum, objectId, new MissingEngines());
    }

    JsonNode invokeCaptionFigures(byte[] pdfBytes, JsonNode dlaResult) throws IOException {
        try (PageImageCache cache = createPageImageCache()) {
            return invokeCaptionFigures(pdfBytes, dlaResult, cache);
        }
    }

    JsonNode invokeCaptionFigures(byte[] pdfBytes, JsonNode dlaResult, PageImageCache cache) {
        return captionFigures(pdfBytes, dlaResult, cache, CropOutput.DISABLED,
            new MissingEngines());
    }

    JsonNode invokeRecognizeFormulas(byte[] pdfBytes, JsonNode dlaResult) throws IOException {
        try (PageImageCache cache = createPageImageCache()) {
            return invokeRecognizeFormulas(pdfBytes, dlaResult, cache);
        }
    }

    JsonNode invokeRecognizeFormulas(byte[] pdfBytes, JsonNode dlaResult, PageImageCache cache) {
        return recognizeFormulas(pdfBytes, dlaResult, cache, CropOutput.DISABLED,
            new MissingEngines());
    }
}

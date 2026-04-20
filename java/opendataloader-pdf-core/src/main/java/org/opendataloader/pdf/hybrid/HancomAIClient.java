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
import java.util.List;
import java.util.Map;
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
 *   <li>DOCUMENT_LAYOUT_WITH_OCR — layout analysis + OCR on full PDF</li>
 *   <li>TABLE_STRUCTURE_RECOGNITION — crop each Table/Regionlist from page image, send to TSR individually</li>
 *   <li>IMAGE_CAPTIONING — crop each Figure region from page image, caption individually</li>
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

    /** Padding (pixels) added around table crops before sending to TSR. */
    private static final int TSR_CROP_PADDING = 20;

    private final String baseUrl;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final HybridConfig config;

    private String sourcePdfShaShort = "unknown";

    private static final java.util.Map<String, String> MODULE_SHORT;
    static {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("DOCUMENT_LAYOUT_WITH_OCR", "dla-ocr");
        m.put("DOCUMENT_LAYOUT_ANALYSIS", "dla");
        m.put("TEXT_RECOGNITION", "ocr");
        m.put("TABLE_STRUCTURE_RECOGNITION", "tsr");
        m.put("IMAGE_CAPTIONING", "caption");
        m.put("CHART_IMAGE_UNDERSTANDING", "chart");
        MODULE_SHORT = java.util.Collections.unmodifiableMap(m);
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
        this.config = new HybridConfig();
        this.baseUrl = baseUrl;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
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
        LOGGER.log(Level.INFO, "Hancom AI: processing PDF ({0} bytes)", pdfBytes.length);

        try (PageImageCache pageImageCache = createPageImageCache()) {
            ObjectNode merged = objectMapper.createObjectNode();
            ObjectNode timingsNode = objectMapper.createObjectNode();

            // Step 1: DLA + OCR. This is required — downstream steps have nothing
            // to process without it, so treat an empty response as a failure so the
            // caller can fall back to the Java pipeline instead of silently emitting
            // an empty document.
            JsonNode dlaOcrResult = callModule(pdfBytes, "DOCUMENT_LAYOUT_WITH_OCR");
            if (dlaOcrResult == null || !dlaOcrResult.isArray() || dlaOcrResult.size() == 0) {
                throw new IOException(
                    "Hancom AI DOCUMENT_LAYOUT_WITH_OCR returned empty result — "
                    + "backend unavailable or rejected the document");
            }
            merged.set("DOCUMENT_LAYOUT_WITH_OCR", dlaOcrResult);
            addTimings(timingsNode, "DOCUMENT_LAYOUT_WITH_OCR", dlaOcrResult);

            // Step 2: Table Structure — crop each Table region from page image, send to TSR individually
            long tsrStartMs = System.currentTimeMillis();
            ArrayNode tsrResults = recognizeTableStructures(pdfBytes, dlaOcrResult, pageImageCache);
            long tsrMs = System.currentTimeMillis() - tsrStartMs;
            merged.set("TABLE_STRUCTURE_RECOGNITION", tsrResults);

            ObjectNode tsrTiming = objectMapper.createObjectNode();
            tsrTiming.put("total_ms", tsrMs);
            tsrTiming.put("count", tsrResults.size());
            if (tsrResults.size() > 0) {
                tsrTiming.put("avg_ms", tsrMs / tsrResults.size());
            }
            timingsNode.set("TABLE_STRUCTURE_RECOGNITION", tsrTiming);

            // Step 3: Figure captioning — pdf2img → crop figures → caption each
            long captionStartMs = System.currentTimeMillis();
            ArrayNode figureCaptions = captionFigures(pdfBytes, dlaOcrResult, pageImageCache);
            long captionMs = System.currentTimeMillis() - captionStartMs;
            merged.set("FIGURE_CAPTIONS", figureCaptions);

            ObjectNode captionTiming = objectMapper.createObjectNode();
            captionTiming.put("total_ms", captionMs);
            captionTiming.put("count", figureCaptions.size());
            if (figureCaptions.size() > 0) {
                captionTiming.put("avg_ms", captionMs / figureCaptions.size());
            }
            timingsNode.set("IMAGE_CAPTIONING", captionTiming);

            merged.set("timings", timingsNode);

            LOGGER.log(Level.INFO, "Hancom AI: completed — {0} table crops, {1} figure captions",
                new Object[]{tsrResults.size(), figureCaptions.size()});

            return new HybridResponse(null, null, merged, Collections.emptyMap(),
                Collections.emptyList(), timingsNode);
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
                                     PageImageCache pageImageCache) {
        ArrayNode captions = objectMapper.createArrayNode();

        // Extract pages from DLA result
        List<JsonNode> dlaPages = extractPages(dlaResult);
        if (dlaPages.isEmpty()) return captions;

        // Collect Figure objects per page
        Map<Integer, List<JsonNode>> figuresByPage = new HashMap<>();
        for (JsonNode page : dlaPages) {
            int pageNum = page.has("page_number") ? page.get("page_number").asInt() : -1;
            if (pageNum < 0) continue;

            JsonNode objects = page.get("objects");
            if (objects == null || !objects.isArray()) continue;

            for (JsonNode obj : objects) {
                int label = obj.has("label") ? obj.get("label").asInt() : -1;
                if (label == LABEL_FIGURE) {
                    figuresByPage.computeIfAbsent(pageNum, k -> new ArrayList<>()).add(obj);
                }
            }
        }

        if (figuresByPage.isEmpty()) {
            LOGGER.log(Level.INFO, "Hancom AI: no Figure objects found, skipping captioning");
            return captions;
        }

        LOGGER.log(Level.INFO, "Hancom AI: captioning {0} figures across {1} pages",
            new Object[]{figuresByPage.values().stream().mapToInt(List::size).sum(),
                          figuresByPage.size()});

        for (Map.Entry<Integer, List<JsonNode>> entry : figuresByPage.entrySet()) {
            int pageNum = entry.getKey();
            List<JsonNode> figures = entry.getValue();

            // Get page image via cache
            BufferedImage pageImage;
            try {
                pageImage = pageImageCache.getOrFetch(pageNum,
                    idx -> fetchPageImage(pdfBytes, idx));
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Failed to get page {0} image: {1}",
                    new Object[]{pageNum, e.getMessage()});
                continue;
            }

            // Caption each figure
            for (JsonNode fig : figures) {
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
                    if (config.isSaveCrops() && config.getCropOutputDir() != null) {
                        int objId = fig.has("object_id") ? fig.get("object_id").asInt() : -1;
                        saveCropFile(config.getCropOutputDir(), pageNum, objId, "figure", croppedPng);
                    }

                    int objIdForCaption = fig.has("object_id") ? fig.get("object_id").asInt() : -1;
                    String caption = callImageCaptioning(croppedPng, pageNum, objIdForCaption);

                    ObjectNode capNode = objectMapper.createObjectNode();
                    capNode.put("page_number", pageNum);
                    capNode.put("object_id", fig.has("object_id") ? fig.get("object_id").asInt() : -1);
                    ArrayNode bboxArr = objectMapper.createArrayNode();
                    bboxArr.add(left).add(top).add(right).add(bottom);
                    capNode.set("bbox", bboxArr);
                    capNode.put("caption", caption != null ? caption : "");
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
     * @return ArrayNode of per-table results:
     *         [{page_number, object_id, label, dla_bbox, tsr: {cells, num_cells, html, ...}}]
     */
    private ArrayNode recognizeTableStructures(byte[] pdfBytes, JsonNode dlaResult,
                                                PageImageCache pageImageCache) {
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
                    idx -> fetchPageImage(pdfBytes, idx));
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
                    if (config.isSaveCrops() && config.getCropOutputDir() != null) {
                        int objectId = obj.has("object_id") ? obj.get("object_id").asInt() : -1;
                        saveCropFile(config.getCropOutputDir(), pageNum, objectId, "table", cropPng);
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

                    // Extract TSR page result
                    List<JsonNode> tsrPages = extractPages(tsrResult);
                    if (!tsrPages.isEmpty()) {
                        entry.set("tsr", tsrPages.get(0));
                    } else {
                        entry.set("tsr", objectMapper.createObjectNode());
                    }

                    results.add(entry);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "TSR failed for page {0} object: {1}",
                        new Object[]{pageNum, e.getMessage()});
                }
            }
            // Do NOT evict the page image here — captionFigures() runs next and
            // reuses the same cache. Evicting per-page during TSR would force
            // pdf2img to re-render pages that contain both tables and figures.
            // The try-with-resources in convert() closes the cache at the end
            // of the request.
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
            LOGGER.log(Level.FINE, "Failed to save crop file", e);
        }
    }

    /**
     * Fetches a page image from the pdf2img endpoint.
     */
    private BufferedImage fetchPageImage(byte[] pdfBytes, int pageIndex) throws IOException {
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

            byte[] pngBytes = Base64.getDecoder().decode(pngBase64);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
            if (image == null) {
                throw new IOException("pdf2img PAGE_PNG_DATA is not a readable image");
            }
            return image;
        }
    }

    /**
     * Sends a cropped image to IMAGE_CAPTIONING and returns the caption text.
     */
    private String callImageCaptioning(byte[] pngBytes, int pageNum, int objectId) throws IOException {
        String requestId = "odl-" + sourcePdfShaShort + "-p" + pageNum + "-o" + objectId + "-caption";
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID", requestId)
            .addFormDataPart("OPEN_API_NAME", "IMAGE_CAPTIONING")
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

            return page.has("caption") ? page.get("caption").asText("") : null;
        }
    }

    /**
     * Calls a single HOCR SDK module with PDF input.
     */
    private JsonNode callModule(byte[] pdfBytes, String moduleName) throws IOException {
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("REQUEST_ID",
                "odl-" + sourcePdfShaShort + "-" + MODULE_SHORT.getOrDefault(moduleName, moduleName))
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

    void invokeCallImageCaptioning(byte[] pngBytes, int pageNum, int objectId) throws IOException {
        callImageCaptioning(pngBytes, pageNum, objectId);
    }
}

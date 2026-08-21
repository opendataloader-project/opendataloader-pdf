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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which Hancom AI module the client asks for, and how it reacts when the server
 * reports that a module does not exist.
 *
 * <p>Runs against {@link MockWebServer} rather than a live backend on purpose.
 * The staging server restarted twice while this work was measured (~45-60s
 * outages) and returned 502/503 under concurrent uploads, and captions are model
 * output that can legitimately change. Pinning module selection to a mock keeps
 * these assertions about our wiring instead of about the model.
 */
class HancomAIModuleSelectionTest {

    private MockWebServer server;
    private HancomAIClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        client = new HancomAIClient(
            server.url("").toString().replaceAll("/$", ""),
            new OkHttpClient(),
            new ObjectMapper()
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    /**
     * Captions must come back in English. The Korean engine
     * ({@code IMAGE_CAPTIONING}) and the English one are separate modules that
     * write the same response field, so the language is decided entirely by the
     * module name we send.
     */
    @Test
    void captioning_requestsEnglishModule() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"a red bicycle\"}]]}")
            .build());

        client.invokeCallImageCaptioning(new byte[]{1, 2, 3}, 0, 1);

        RecordedRequest req = server.takeRequest();
        String body = req.getBody().utf8();
        assertThat(body).contains("IMAGE_CAPTIONING_EN");
    }

    /**
     * An unknown module name still returns HTTP 200 with a true top-level
     * SUCCESS; the real failure only shows up as {@code "not existed engine"}
     * inside RESULT. Verified against the live server with the bogus names
     * IMAGE_CAPTIONING_ENG and IMC_EN. Since this change is precisely a module
     * rename, a typo has to surface rather than silently yield no caption.
     */
    @Test
    void captioning_notExistedEngine_returnsNull() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            // Carries a caption as well, so passing this test requires actually
            // detecting the missing engine rather than just finding no caption.
            .body("{\"SUCCESS\":true,\"CODE\":\"SUCCESS\",\"RESULT\":"
                + "[[{\"MSG\":\"not existed engine\",\"IS_SUCCESS\":false,"
                + "\"caption\":\"should not be used\",\"page_number\":0}]]}")
            .build());

        HancomAIClient.CaptionResult result =
            client.invokeCallImageCaptioning(new byte[]{1, 2, 3}, 0, 1);

        assertThat(result).isNull();
    }

    @Test
    void captioning_validResponse_returnsCaption() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"a red bicycle\"}]]}")
            .build());

        HancomAIClient.CaptionResult result =
            client.invokeCallImageCaptioning(new byte[]{1, 2, 3}, 0, 1);

        assertThat(result).isNotNull();
        assertThat(result.caption).isEqualTo("a red bicycle");
    }

    /**
     * Formula regions go to FORMULA_RECOGNITION, which returns LaTeX in a
     * {@code formula} field.
     */
    @Test
    void formula_requestsFormulaRecognitionModule() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"formula\":\"x ^ { 2 }\"}]]}")
            .build());

        client.invokeCallFormulaRecognition(new byte[]{1, 2, 3}, 0, 4);

        RecordedRequest req = server.takeRequest();
        assertThat(req.getBody().utf8()).contains("FORMULA_RECOGNITION");
    }

    @Test
    void formula_returnsLatex() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"formula\":"
                + "\"P _ { \\\\mathrm { o u t } } = \\\\frac { 1 } { 2 }\"}]]}")
            .build());

        String latex = client.invokeCallFormulaRecognition(new byte[]{1, 2, 3}, 0, 4);

        assertThat(latex).isEqualTo("P _ { \\mathrm { o u t } } = \\frac { 1 } { 2 }");
    }

    @Test
    void formula_notExistedEngine_returnsNull() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":"
                + "[[{\"MSG\":\"not existed engine\",\"IS_SUCCESS\":false,"
                + "\"formula\":\"should not be used\"}]]}")
            .build());

        assertThat(client.invokeCallFormulaRecognition(new byte[]{1, 2, 3}, 0, 4)).isNull();
    }

    /**
     * A page marked {@code IS_SUCCESS:false} is not a result, even when it
     * carries a caption and no explanatory message. Trusting the payload would
     * put the server's discarded output into the document's alt text.
     */
    @Test
    void captioning_failedPage_returnsNull() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"IS_SUCCESS\":false,"
                + "\"caption\":\"should not be used\"}]]}")
            .build());

        assertThat(client.invokeCallImageCaptioning(new byte[]{1, 2, 3}, 0, 1)).isNull();
    }

    @Test
    void formula_failedPage_returnsNull() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"IS_SUCCESS\":false,"
                + "\"formula\":\"should not be used\"}]]}")
            .build());

        assertThat(client.invokeCallFormulaRecognition(new byte[]{1, 2, 3}, 0, 4)).isNull();
    }

    /** A page that says nothing about success is a result, not a failure. */
    @Test
    void captioning_pageWithoutSuccessFlag_returnsCaption() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"a red bicycle\"}]]}")
            .build());

        HancomAIClient.CaptionResult result =
            client.invokeCallImageCaptioning(new byte[]{1, 2, 3}, 0, 1);

        assertThat(result).isNotNull();
        assertThat(result.caption).isEqualTo("a red bicycle");
    }

    /** All three subdivided figure classes are captioned; other labels are not. */
    @Test
    void visualLabels_coverFigureChartImage() {
        assertThat(HancomAISchemaTransformer.isVisualLabel(10)).isTrue();
        assertThat(HancomAISchemaTransformer.isVisualLabel(18)).isTrue();
        assertThat(HancomAISchemaTransformer.isVisualLabel(19)).isTrue();

        for (int label = -1; label <= 25; label++) {
            if (label == 10 || label == 18 || label == 19) {
                continue;
            }
            assertThat(HancomAISchemaTransformer.isVisualLabel(label))
                .as("label %d must not be treated as visual", label)
                .isFalse();
        }
    }

    /**
     * Captioning covers every visual class, so a page whose only picture is a
     * Chart or an Image must still produce caption requests. Before the figure
     * subdivision only label 10 was collected, which left charts and photos
     * without alt text.
     */
    @Test
    void captionFigures_coversAllThreeVisualLabels() throws Exception {
        // One page-image render, then one caption call per visual object.
        server.enqueue(pageImageResponse());
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse.Builder()
                .code(200)
                .body("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"c" + i + "\"}]]}")
                .build());
        }

        JsonNode dla = dlaWithObjects(
            "{\"object_id\":1,\"label\":10,\"confidence\":0.9,\"bbox\":[0,0,20,20]}",
            "{\"object_id\":2,\"label\":18,\"confidence\":0.9,\"bbox\":[20,0,40,20]}",
            "{\"object_id\":3,\"label\":19,\"confidence\":0.9,\"bbox\":[40,0,60,20]}"
        );

        JsonNode captions = client.invokeCaptionFigures(new byte[]{9}, dla);

        assertThat(captions.size()).isEqualTo(3);
    }

    /**
     * A graphic reported under two visual labels must be captioned once. Each
     * caption is a GPU round-trip, and two captions for one picture would also
     * leave the transformer picking arbitrarily between them.
     */
    @Test
    void captionFigures_duplicateDetection_isCaptionedOnce() throws Exception {
        server.enqueue(pageImageResponse());
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"one graphic\"}]]}")
            .build());

        // Same region under both labels, as seen on 01030000000107.
        JsonNode dla = dlaWithObjects(
            "{\"object_id\":5,\"label\":10,\"confidence\":0.814,\"bbox\":[10,10,90,90]}",
            "{\"object_id\":8,\"label\":18,\"confidence\":0.450,\"bbox\":[11,10,90,89]}"
        );

        JsonNode captions = client.invokeCaptionFigures(new byte[]{9}, dla);

        assertThat(captions.size()).isEqualTo(1);
        // The surviving detection is the confident one.
        assertThat(captions.get(0).get("object_id").asInt()).isEqualTo(5);
    }

    /**
     * A page holding both an equation and a figure must be rendered once. The
     * page image is a full-resolution 300-DPI render — the most expensive
     * non-GPU call in the pipeline — and each pass evicts the pages it visits,
     * so running formulas after captioning silently re-rendered such pages.
     */
    @Test
    void formulaAndCaptionOnSamePage_rendersPageOnce() throws Exception {
        server.enqueue(pageImageResponse());
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"formula\":\"x ^ { 2 }\"}]]}")
            .build());
        server.enqueue(new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[[{\"caption\":\"a diagram\"}]]}")
            .build());

        JsonNode dla = dlaWithObjects(
            "{\"object_id\":1,\"label\":12,\"confidence\":0.9,\"bbox\":[0,0,40,20]}",
            "{\"object_id\":2,\"label\":19,\"confidence\":0.9,\"bbox\":[50,0,90,20]}"
        );

        try (PageImageCache cache = new MemoryPageImageCache()) {
            client.invokeRecognizeFormulas(new byte[]{9}, dla, cache);
            client.invokeCaptionFigures(new byte[]{9}, dla, cache);
        }

        int pageRenders = 0;
        for (int i = 0; i < server.getRequestCount(); i++) {
            if (server.takeRequest().getTarget().contains("pdf2img")) {
                pageRenders++;
            }
        }
        assertThat(pageRenders).isEqualTo(1);
    }

    /** A page with no visual object must not trigger any captioning traffic. */
    @Test
    void captionFigures_noVisualObjects_makesNoRequests() throws Exception {
        JsonNode dla = dlaWithObjects(
            "{\"object_id\":1,\"label\":2,\"confidence\":0.9,\"bbox\":[0,0,20,20]}"
        );

        JsonNode captions = client.invokeCaptionFigures(new byte[]{9}, dla);

        assertThat(captions.size()).isZero();
        assertThat(server.getRequestCount()).isZero();
    }

    /**
     * A 1x1 PNG, base64-encoded, in the shape {@code /support/pdf2img} returns.
     */
    private static MockResponse pageImageResponse() {
        String png = "iVBORw0KGgoAAAANSUhEUgAAAGQAAABkCAIAAAD/gAIDAAAAO0lEQVR4nO3B"
            + "MQEAAADCoPVPbQ0PoAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAg"
            + "DcDQAABGnpdmQAAAABJRU5ErkJggg==";
        return new MockResponse.Builder()
            .code(200)
            .body("{\"SUCCESS\":true,\"RESULT\":[{\"RESULT\":{\"PAGE_PNG_DATA\":\"" + png + "\"}}]}")
            .build();
    }

    private JsonNode dlaWithObjects(String... objectJson) throws Exception {
        return new ObjectMapper().readTree(
            "[[{\"page_number\":0,\"image_width\":100,\"image_height\":100,"
                + "\"objects\":[" + String.join(",", objectJson) + "]}]]");
    }
}

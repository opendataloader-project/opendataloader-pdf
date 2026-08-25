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

import com.fasterxml.jackson.databind.ObjectMapper;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.OkHttpClient;
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
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Retry and timeout behaviour of {@link HancomAIClient}.
 *
 * <p>A backend that is restarting answers a perfectly good request with a
 * failure, and before retries existed that lost the document's layout outright.
 * These tests pin which failures are worth another attempt and which are not:
 * retrying a permanent rejection costs GPU time per attempt on a document that
 * cannot succeed.
 */
public class HancomAIRetryTest {

    private MockWebServer server;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    /** Backoff is collapsed so the suite does not sleep out the real 50s. */
    private HancomAIClient client() {
        HancomAIClient client = new HancomAIClient(
            server.url("").toString().replaceAll("/$", ""),
            new OkHttpClient(), mapper, new HybridConfig());
        client.setRetryBackoffMsForTest(1L, 1L);
        return client;
    }

    private static byte[] onePagePdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(new PDRectangle(100, 200)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private HybridRequest request(byte[] pdf) {
        return request(pdf, 1);
    }

    /**
     * Request for pages 1..pageCount. Passing only page 1 would make a
     * multi-page fixture pointless: the slicer sends one page and the rest are
     * dropped before any enrichment runs.
     */
    private HybridRequest request(byte[] pdf, int pageCount) {
        Set<Integer> pages = new LinkedHashSet<>();
        for (int p = 1; p <= pageCount; p++) {
            pages.add(p);
        }
        return new HybridRequest(pdf, pages, EnumSet.allOf(OutputFormat.class));
    }

    private static String layoutOk() {
        return "{\"SUCCESS\":true,\"RESULT\":[[{\"page_number\":0,"
            + "\"image_width\":1000,\"image_height\":2000,\"objects\":[]}]]}";
    }

    private void enqueue(int code, String body) {
        server.enqueue(new MockResponse.Builder().code(code).body(body).build());
    }

    /**
     * A restarting backend answers 503 and then works. The document must not be
     * lost to the attempt that arrived mid-restart.
     */
    @Test
    void serverErrorIsRetriedAndTheLaterSuccessIsUsed() throws Exception {
        enqueue(503, "");
        enqueue(200, layoutOk());

        HybridResponse response = client().convert(request(onePagePdf()));

        assertThat(response.getJson().has(HancomAIClient.LAYOUT_RESULT_KEY)).isTrue();
        assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(2);
    }

    /**
     * A 4xx means the request itself is wrong, so repeating it byte for byte
     * cannot help. Exactly one layout call must be made.
     */
    @Test
    void clientErrorIsNotRetried() throws Exception {
        enqueue(400, "");

        assertThatThrownBy(() -> client().convert(request(onePagePdf())))
            .isInstanceOf(IOException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    /**
     * SUCCESS=false arrives both from a restarting backend and from a document
     * the backend genuinely rejects, and the two are indistinguishable. It gets
     * one retry, not the full budget, so a permanently rejected document does
     * not pay for three GPU attempts.
     */
    @Test
    void successFalseGetsASingleRetry() throws Exception {
        String fail = "{\"SUCCESS\":false,\"CODE\":\"FAIL\",\"MSG\":\"처리에 실패했습니다\",\"RESULT\":[[]]}";
        enqueue(200, fail);
        enqueue(200, fail);
        enqueue(200, fail);

        assertThatThrownBy(() -> client().convert(request(onePagePdf())))
            .isInstanceOf(IOException.class);

        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    /**
     * A module the server does not have is a name error. Retrying cannot make
     * the engine appear.
     */
    @Test
    void missingEngineIsNotRetried() throws Exception {
        enqueue(200, "{\"SUCCESS\":false,\"MSG\":\"not existed engine\",\"RESULT\":[[]]}");

        assertThatThrownBy(() -> client().convert(request(onePagePdf())))
            .isInstanceOf(IOException.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    /**
     * Retries are bounded: a backend that is down for good must not be hammered
     * indefinitely.
     */
    @Test
    void serverErrorStopsAfterTheAttemptBudget() throws Exception {
        enqueue(503, "");
        enqueue(503, "");
        enqueue(503, "");
        enqueue(503, "");

        assertThatThrownBy(() -> client().convert(request(onePagePdf())))
            .isInstanceOf(IOException.class);

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    /**
     * A backend refusing everything must not make every page pay the backoff.
     * Measured before the breaker existed: a document whose pdf2img calls all
     * failed spent 50s per page and ran past ten minutes waiting. Once the
     * streak is hit the remaining enrichment calls take one attempt each, so
     * total requests stay far below attempts x pages.
     */
    @Test
    void repeatedEnrichmentFailuresStopBeingRetried() throws Exception {
        // Layout succeeds for several pages so enrichment is attempted on each;
        // every later call fails, so the breaker should trip.
        StringBuilder pages = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            if (i > 0) pages.append(',');
            pages.append("{\"page_number\":").append(i)
                .append(",\"image_width\":1000,\"image_height\":2000,\"objects\":[")
                .append("{\"object_id\":0,\"label\":9,\"bbox\":[0,0,100,100]}]}");
        }
        enqueue(200, "{\"SUCCESS\":true,\"RESULT\":[[" + pages + "]]}");
        for (int i = 0; i < 200; i++) {
            enqueue(500, "boom");
        }

        client().convert(request(twelvePagePdf(), 12));

        // 1 layout + 3 attempts each on pages 0-2 (which trip the streak) + a
        // single attempt on each of the remaining 9. Asserted exactly: a bound
        // like "< 3 x 12" also holds when the breaker does nothing.
        assertThat(server.getRequestCount()).isEqualTo(1 + 3 * 3 + 9);
    }

    private static byte[] twelvePagePdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 0; i < 12; i++) {
                doc.addPage(new PDPage(new PDRectangle(100 + i, 200)));
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    /**
     * A 4xx from pdf2img is the request's own fault and answers the same way
     * however often it is sent; retrying it would cost the backoff on every
     * page of the document.
     */
    @Test
    void pdf2imgClientErrorIsNotRetried() throws Exception {
        enqueue(200, "{\"SUCCESS\":true,\"RESULT\":[[{\"page_number\":0,"
            + "\"image_width\":1000,\"image_height\":2000,\"objects\":["
            + "{\"object_id\":0,\"label\":9,\"bbox\":[0,0,100,100]}]}]]}");
        enqueue(404, "nope");

        client().convert(request(onePagePdf()));

        // Layout, then exactly one pdf2img attempt.
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    /**
     * The per-region enrichment calls are the numerous ones, so a restart lands
     * hardest here: without a retry every table on the page comes back
     * structurally empty and nothing reports it.
     */
    @Test
    void enrichmentModuleCallIsRetried() throws Exception {
        enqueue(200, "{\"SUCCESS\":true,\"RESULT\":[[{\"page_number\":0,"
            + "\"image_width\":1000,\"image_height\":2000,\"objects\":["
            + "{\"object_id\":0,\"label\":9,\"bbox\":[0,0,100,100]}]}]]}");
        // pdf2img succeeds with a 1x1 PNG so the TSR crop is attempted.
        enqueue(200, "{\"SUCCESS\":true,\"RESULT\":[{\"RESULT\":{\"PAGE_PNG_DATA\":\""
            + onePixelPngBase64() + "\"}}]}");
        enqueue(503, "");
        enqueue(503, "");
        enqueue(503, "");

        client().convert(request(onePagePdf()));

        // Layout + pdf2img + three TSR attempts.
        assertThat(server.getRequestCount()).isEqualTo(5);
    }

    private static String onePixelPngBase64() throws IOException {
        java.awt.image.BufferedImage img =
            new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return java.util.Base64.getEncoder().encodeToString(out.toByteArray());
    }

    /**
     * An explicit --hybrid-timeout keeps its exact meaning, 0 (no limit)
     * included, so a deliberate setting is not overridden by the new default
     * ceiling. Asserted on the built client, not on the config that was just
     * set, so the constructor's branch is what is actually pinned.
     */
    @Test
    void explicitTimeoutIsAppliedToTheClient() {
        HybridConfig config = new HybridConfig();
        config.setTimeoutMs(1234);
        HancomAIClient client = new HancomAIClient(config);

        assertThat(client.httpClientForTest().callTimeoutMillis()).isEqualTo(1234);
        assertThat(client.httpClientForTest().connectTimeoutMillis()).isEqualTo(1234);
    }

    /**
     * Unset means a ceiling rather than the old unlimited wait, and it lands on
     * callTimeout: readTimeout must stay 0 or a backend that computes for
     * minutes before answering would be cut off mid-work.
     */
    @Test
    void defaultTimeoutsBoundTheCallButNotTheRead() {
        HancomAIClient client = new HancomAIClient(new HybridConfig());

        assertThat(client.httpClientForTest().callTimeoutMillis()).isEqualTo(3_600_000);
        assertThat(client.httpClientForTest().connectTimeoutMillis()).isEqualTo(10_000);
        assertThat(client.httpClientForTest().readTimeoutMillis()).isZero();
    }

    /** An empty backoff array would index out of bounds at the first retry. */
    @Test
    void emptyTestBackoffIsRejected() {
        HancomAIClient client = new HancomAIClient(new HybridConfig());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
            client::setRetryBackoffMsForTest);
    }
}

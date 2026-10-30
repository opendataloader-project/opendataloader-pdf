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
package org.opendataloader.pdf.issues;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #706: the chunk loop in {@code HybridDocumentProcessor.processBackendPath} splits the backend
 * pages into requests of {@code HybridConfig.getChunkSize()} pages. Before, only a copy of the loop
 * in a test had tests, so a defect in the real loop (a wrong config field, a wrong subList bound)
 * passed CI.
 *
 * <p>These tests run the real loop. The backend is a MockWebServer, and each test reads the
 * {@code page_ranges} field of each convert request. In full mode each page goes to the backend.
 * The sample pages are blank.
 */
class Issue706HybridChunkSizeTest {

    private static final String CONVERT_RESPONSE = "{"
        + "\"status\": \"success\","
        + "\"document\": {\"json_content\": {\"texts\": [], \"pages\": {}}},"
        + "\"processing_time\": 0.1,"
        + "\"errors\": [],"
        + "\"failed_pages\": []"
        + "}";

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        // HybridClientFactory caches clients per backend name, so a client wired
        // to another test's URL would otherwise receive our requests.
        HybridClientFactory.shutdown();

        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if ("/health".equals(request.getTarget())) {
                    return new MockResponse.Builder().code(200).body("ok").build();
                }
                return new MockResponse.Builder()
                    .body(CONVERT_RESPONSE)
                    .addHeader("Content-Type", "application/json")
                    .build();
            }
        });
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            if (server != null) {
                server.close();
            }
        } finally {
            OpenDataLoaderPDF.shutdown();
        }
    }

    @ParameterizedTest(name = "{0} pages, chunk size {1}: {2}")
    @CsvSource(delimiter = '|', value = {
        "25 | 10 | 1-10 11-20 21-25",   // the last request has the remaining pages
        "20 | 10 | 1-10 11-20",         // no empty request after the last full chunk
        "10 | 10 | 1-10",               // exactly one chunk
        "3  | 10 | 1-3",                // fewer pages than one chunk
        "4  | 1  | 1-1 2-2 3-3 4-4",    // one page in each request
    })
    void configuredChunkSizeSetsThePagesOfEachBackendRequest(int pageCount, int chunkSize, String expected)
            throws Exception {
        Config config = new Config();
        config.getHybridConfig().setChunkSize(chunkSize);

        List<String> ranges = convert("issue-706-" + pageCount + "-pages-chunk-" + chunkSize, pageCount, config);

        assertEquals(Arrays.asList(expected.split(" ")), ranges,
            "the backend requests do not follow --hybrid-chunk-size " + chunkSize);
    }

    @Test
    void selectedPagesAreSortedAndSplitByTheConfiguredChunkSize() throws Exception {
        // --pages gives pages with gaps. A chunk has 2 selected pages, but its request has one range
        // from the first to the last page, so the range also covers the pages between (2 in 1-3).
        // This test keeps the current requests. A change of the request format must update it.
        Config config = new Config();
        config.setPages("9,1,5,3,7");
        config.getHybridConfig().setChunkSize(2);

        List<String> ranges = convert("issue-706-selected-pages", 10, config);

        assertEquals(Arrays.asList("1-3", "5-7", "9-9"), ranges,
            "the selected pages are not sorted and split into requests of 2 pages");
    }

    @Test
    void defaultChunkSizeSplitsALongDocument() throws Exception {
        int chunkSize = HybridConfig.DEFAULT_CHUNK_SIZE;

        List<String> ranges = convert("issue-706-default-chunk-size", chunkSize + 1, new Config());

        assertEquals(Arrays.asList("1-" + chunkSize, (chunkSize + 1) + "-" + (chunkSize + 1)), ranges,
            "without --hybrid-chunk-size, the backend requests do not have " + chunkSize + " pages");
    }

    /** Converts a blank PDF in full hybrid mode and returns the page_ranges of each convert request. */
    private List<String> convert(String name, int pageCount, Config config) throws Exception {
        Path dir = TestSamples.emptyDirectory(name);
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (int page = 0; page < pageCount; page++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            document.save(pdf.toFile());
        }

        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        DocumentProcessor.processFile(pdf.toString(), config);

        List<String> ranges = new ArrayList<>();
        int requestCount = server.getRequestCount();
        for (int i = 0; i < requestCount; i++) {
            RecordedRequest request = server.takeRequest();
            if ("/v1/convert/file".equals(request.getTarget())) {
                ranges.add(formField(request.getBody().utf8(), "page_ranges"));
            }
        }
        return ranges;
    }

    private static String formField(String multipartBody, String name) {
        String header = "name=\"" + name + "\"";
        int headerAt = multipartBody.indexOf(header);
        assertTrue(headerAt >= 0, "multipart body has no " + name + " field");
        int separatorAt = multipartBody.indexOf("\r\n\r\n", headerAt);
        assertTrue(separatorAt >= 0, "multipart " + name + " field has no header/value separator");
        int valueStart = separatorAt + 4;
        return multipartBody.substring(valueStart, multipartBody.indexOf("\r\n", valueStart));
    }
}

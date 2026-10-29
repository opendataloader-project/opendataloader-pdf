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

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #623: in hybrid mode, the text that the backend labels {@code page_header} or {@code page_footer}
 * ({@code content_layer: furniture}) is lost, also with --include-header-footer.
 *
 * <p>The native pipeline gives a header or a footer as a header or footer element, and the writers
 * show it only with --include-header-footer. The sample pages are blank, so the output text comes
 * only from the backend response. The response has the shape of the reporter's cover page: a header
 * at the top, one body paragraph, and a footer at the bottom.
 */
class Issue623HybridHeaderFooterTest {

    private static final String HEADER = "HEADER_SENTINEL";
    private static final String BODY = "BODY_SENTINEL";
    private static final String FOOTER = "FOOTER_SENTINEL";
    private static final float PAGE_HEIGHT = PDRectangle.A4.getHeight();

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        // HybridClientFactory caches clients per backend name, so a client wired
        // to another test's URL would otherwise receive our requests.
        HybridClientFactory.shutdown();

        server = new MockWebServer();
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

    @Test
    void backendHeaderAndFooterAreOutputWithIncludeHeaderFooter() throws IOException {
        Output out = convert("include-header-footer", true, 1);
        assertTrue(out.json.contains(BODY), "the body text of the backend is missing: " + out.json);
        assertTrue(out.json.contains(HEADER) && out.json.contains(FOOTER),
            "--include-header-footer lost the page header or footer of the backend: " + out.json);
        assertTrue(out.json.contains("\"type\" : \"header\"") && out.json.contains("\"type\" : \"footer\""),
            "the page header and footer of the backend are not header and footer elements: " + out.json);
        assertTrue(out.markdown.contains(HEADER) && out.markdown.contains(FOOTER),
            "Markdown lost the page header or footer of the backend: " + out.markdown);
    }

    @Test
    void backendHeaderAndFooterStayOutWithoutIncludeHeaderFooter() throws IOException {
        Output out = convert("default", false, 1);
        assertTrue(out.json.contains(BODY), "the body text of the backend is missing: " + out.json);
        assertFalse(out.json.contains(HEADER) || out.json.contains(FOOTER),
            "JSON shows a header or footer without --include-header-footer: " + out.json);
        assertFalse(out.markdown.contains(HEADER) || out.markdown.contains(FOOTER),
            "Markdown shows a header or footer without --include-header-footer: " + out.markdown);
    }

    @Test
    void repeatedBackendHeaderAndFooterAreOneHeaderAndOneFooterOnEachPage() throws IOException {
        // The same header and footer at the same position on each page, as in a report
        Output out = convert("two-pages", true, 2);
        assertEquals(2, count(out.json, HEADER), "each page has its header text one time: " + out.json);
        assertEquals(2, count(out.json, FOOTER), "each page has its footer text one time: " + out.json);
        assertEquals(2, count(out.json, "\"type\" : \"header\""),
            "each page has one header element, not a header in a header: " + out.json);
        assertEquals(2, count(out.json, "\"type\" : \"footer\""),
            "each page has one footer element, not a footer in a footer: " + out.json);
        assertEquals(2, count(out.markdown, HEADER), "Markdown has the header text of each page: " + out.markdown);
        assertEquals(2, count(out.markdown, FOOTER), "Markdown has the footer text of each page: " + out.markdown);
    }

    @Test
    void headerItemsAtSeparatePlacesKeepTheBodyColumnOrder() throws IOException {
        // Docling labels a running line at the top and a vertical stamp in the left margin (as on
        // arXiv papers) as page_header on the same page. The body has two columns.
        List<String> texts = new ArrayList<>();
        texts.add(text(1, "page_header", "furniture", "TOP_HEADER", 200, 800, 400, 785));
        texts.add(text(1, "page_header", "furniture", "SIDE_HEADER", 15, 600, 35, 200));
        List<String> expectedOrder = new ArrayList<>();
        for (String column : List.of("LEFT", "RIGHT")) {
            float left = "LEFT".equals(column) ? 72 : 320;
            for (int row = 0; row < 4; row++) {
                float top = 700 - row * 110;
                texts.add(text(1, "text", "body", column + "_" + (row + 1), left, top, left + 205, top - 92));
                expectedOrder.add(column + "_" + (row + 1));
            }
        }
        for (boolean includeHeaderFooter : new boolean[] {false, true}) {
            Output out = convert("separate-header-items-" + includeHeaderFooter, includeHeaderFooter, 1, texts);
            List<String> order = new ArrayList<>(expectedOrder);
            order.sort((a, b) -> Integer.compare(out.markdown.indexOf(a), out.markdown.indexOf(b)));
            assertTrue(expectedOrder.stream().allMatch(out.markdown::contains), "body text is missing: " + out.markdown);
            assertEquals(expectedOrder, order,
                "the body columns are mixed (--include-header-footer " + includeHeaderFooter + "): " + out.markdown);
        }
    }

    private Output convert(String name, boolean includeHeaderFooter, int pageCount) throws IOException {
        return convert(name, includeHeaderFooter, pageCount, reportLikeTexts(pageCount));
    }

    private Output convert(String name, boolean includeHeaderFooter, int pageCount, List<String> texts)
            throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-623-" + name);
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            for (int page = 0; page < pageCount; page++) {
                document.addPage(new PDPage(PDRectangle.A4));
            }
            document.save(pdf.toFile());
        }
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder()
            .body(convertResponse(pageCount, texts))
            .addHeader("Content-Type", "application/json")
            .build());

        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        config.setIncludeHeaderFooter(includeHeaderFooter);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode("full");
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        DocumentProcessor.processFile(pdf.toString(), config);
        return new Output(Files.readString(dir.resolve(name + ".md")), Files.readString(dir.resolve(name + ".json")));
    }

    private static List<String> reportLikeTexts(int pageCount) {
        List<String> texts = new ArrayList<>();
        for (int page = 1; page <= pageCount; page++) {
            texts.add(text(page, "page_header", "furniture", HEADER, 72, PAGE_HEIGHT - 40, 400, PAGE_HEIGHT - 60));
            texts.add(text(page, "text", "body", BODY, 72, PAGE_HEIGHT - 300, 400, PAGE_HEIGHT - 320));
            texts.add(text(page, "page_footer", "furniture", FOOTER, 72, 60, 400, 40));
        }
        return texts;
    }

    private static String convertResponse(int pageCount, List<String> texts) {
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= pageCount; page++) {
            pages.add("\"" + page + "\": {}");
        }
        return "{"
            + "\"status\": \"success\","
            + "\"document\": {\"json_content\": {"
            + "\"texts\": [" + String.join(",", texts) + "],"
            + "\"pages\": {" + String.join(",", pages) + "}"
            + "}},"
            + "\"processing_time\": 0.5,"
            + "\"errors\": [],"
            + "\"failed_pages\": []"
            + "}";
    }

    private static String text(int page, String label, String layer, String text,
                               float left, float top, float right, float bottom) {
        return "{\"label\": \"" + label + "\", \"content_layer\": \"" + layer + "\", \"text\": \"" + text + "\","
            + "\"prov\": [{\"page_no\": " + page + ", \"bbox\": {\"l\": " + left + ", \"t\": " + top
            + ", \"r\": " + right + ", \"b\": " + bottom + ", \"coord_origin\": \"BOTTOMLEFT\"}}]}";
    }

    private static int count(String text, String part) {
        int count = 0;
        for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
            count++;
        }
        return count;
    }

    private static final class Output {
        final String markdown;
        final String json;

        Output(String markdown, String json) {
            this.markdown = markdown;
            this.json = json;
        }
    }
}

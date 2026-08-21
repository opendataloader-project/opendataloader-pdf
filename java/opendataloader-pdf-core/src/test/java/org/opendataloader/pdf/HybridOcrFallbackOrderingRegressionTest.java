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
package org.opendataloader.pdf;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for the content-stream write-order bug fixed in fork commit
 * {@code a55c694} (branch {@code fix/hybrid-tagged-pdf-text-drop}): OCR-fallback text
 * used to be appended one chunk at a time to the *tail* of a page's content stream,
 * regardless of where it logically belonged, so anything reading raw content-stream
 * order (e.g. a naive text extractor) saw a page's fallback content as one big block
 * tacked onto the end, after every pre-existing native run — even when the fallback
 * text logically came first.
 *
 * <p>Replays a real, captured {@code /v1/convert/file} response from the
 * {@code opendataloader-pdf-hybrid} backend for {@code WetlandsTest.pdf} (a two-page
 * scanned/photo-heavy fact sheet with heavy OCR-fallback content on both pages — see
 * {@code dice-document-pipeline-api}'s {@code implementation_plan.md}, the standing
 * stress-test document for this bug class) via {@link MockWebServer}, so the test needs
 * no live hybrid server and is fully deterministic.
 *
 * <p>Page 2 of this document mixes native-anchored content (a bulleted list, each
 * bullet backed by real PDF text) with OCR-fallback content (the intro sentence above
 * the list, recovered only via the backend). "Here are some clues that wetland might
 * be present:" is fallback text that logically precedes the whole bulleted list,
 * including its first bullet's native-anchored word "peaty." Under the tail-append
 * bug, the fallback sentence — dumped at the very end of the stream — would land
 * *after* "peaty." in physical order. The fix instead splices it in right after its
 * nearest preceding native anchor, so it stays before the list it introduces.
 */
class HybridOcrFallbackOrderingRegressionTest {

    private static final String WETLANDS_PDF = "src/test/resources/hybrid/WetlandsTest.pdf";
    private static final String WETLANDS_HYBRID_RESPONSE = "src/test/resources/hybrid/wetlands-test-hybrid-response.json";

    @TempDir
    Path tempDir;

    private MockWebServer server;
    private File samplePdf;

    @BeforeEach
    void setUp() throws IOException {
        // See HybridBackendFailureIntegrationTest: HybridClientFactory caches clients
        // process-wide by backend name, so clear it before pointing docling-fast at
        // this test's MockWebServer URL.
        HybridClientFactory.shutdown();

        server = new MockWebServer();
        server.start();
        samplePdf = new File(WETLANDS_PDF);
        assertTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
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
    void ocrFallbackTextSplicedNearOwnPosition_notDumpedAtTail() throws IOException {
        // /health probe (Phase 0 checkAvailability)
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        // /v1/convert/file — a real, captured backend response for WetlandsTest.pdf
        // (both pages triage BACKEND=2 for this document; MODE_FULL below makes that
        // routing deterministic instead of depending on triage heuristics).
        String hybridResponseJson = Files.readString(Paths.get(WETLANDS_HYBRID_RESPONSE));
        server.enqueue(new MockResponse.Builder()
            .body(hybridResponseJson)
            .addHeader("Content-Type", "application/json")
            .build());

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateTaggedPDF(true);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode(HybridConfig.MODE_FULL);
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path taggedPdf = tempDir.resolve("WetlandsTest_tagged.pdf");
        assertTrue(Files.exists(taggedPdf), "Expected a tagged PDF at " + taggedPdf);

        try (PDDocument doc = Loader.loadPDF(taggedPdf.toFile())) {
            // setSortByPosition(false) (the default) walks text in raw content-stream
            // order, the same order a naive extractor (or the tail-append bug) would
            // produce — exactly what this regression needs to observe.
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            stripper.setSortByPosition(false);
            String page2Text = stripper.getText(doc);

            int cluesIndex = page2Text.indexOf("Here are some clues");
            int peatyIndex = page2Text.indexOf("peaty");

            assertTrue(cluesIndex >= 0,
                "OCR-fallback intro sentence missing entirely from page 2:\n" + page2Text);
            assertTrue(peatyIndex >= 0,
                "Native-anchored first bullet missing entirely from page 2:\n" + page2Text);
            assertTrue(cluesIndex < peatyIndex,
                "OCR-fallback text that logically precedes the bullet list ended up physically "
                + "*after* the list's own native-anchored content — this is the tail-append bug "
                + "fixed in a55c694 regressing. cluesIndex=" + cluesIndex + " peatyIndex=" + peatyIndex
                + "\npage2Text=" + page2Text);
        }
    }
}

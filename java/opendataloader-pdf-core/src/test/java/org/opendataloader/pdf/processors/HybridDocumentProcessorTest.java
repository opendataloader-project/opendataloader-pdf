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
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.hybrid.HancomAISchemaTransformer;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.OutputFormat;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.hybrid.HybridSchemaTransformer;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageSignals;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Unit tests for HybridDocumentProcessor.
 *
 * <p>Note: Full integration tests require a running docling-fast server.
 * These tests focus on the triage-based routing logic.
 */
public class HybridDocumentProcessorTest {

    @Test
    public void testHybridModeEnabled() {
        Config config = new Config();
        config.setHybrid("docling-fast");

        Assertions.assertTrue(config.isHybridEnabled());
        Assertions.assertEquals("docling-fast", config.getHybrid());
    }

    @Test
    public void testHybridModeDisabled() {
        Config config = new Config();
        config.setHybrid("off");

        Assertions.assertFalse(config.isHybridEnabled());
        Assertions.assertEquals("off", config.getHybrid());
    }

    @Test
    public void testHybridModeDefaultIsOff() {
        Config config = new Config();

        Assertions.assertFalse(config.isHybridEnabled());
        Assertions.assertEquals("off", config.getHybrid());
    }

    @Test
    public void testHybridConfigDefaults() {
        HybridConfig config = new HybridConfig();

        Assertions.assertEquals(HybridConfig.DEFAULT_TIMEOUT_MS, config.getTimeoutMs());
        Assertions.assertEquals(HybridConfig.DEFAULT_MAX_CONCURRENT_REQUESTS, config.getMaxConcurrentRequests());
        Assertions.assertFalse(config.isFallbackToJava(), "fallback should be disabled by default to fail-fast when hybrid server is unavailable");
        Assertions.assertNull(config.getUrl());
    }

    @Test
    public void testHybridConfigEffectiveUrl() {
        HybridConfig config = new HybridConfig();

        // Default URL for docling-fast
        Assertions.assertEquals(HybridConfig.DOCLING_FAST_DEFAULT_URL, config.getEffectiveUrl("docling-fast"));

        // Custom URL overrides default
        config.setUrl("http://custom:8080");
        Assertions.assertEquals("http://custom:8080", config.getEffectiveUrl("docling-fast"));
    }

    @Test
    public void testTriageResultFilterByDecision() {
        Map<Integer, TriageResult> triageResults = new HashMap<>();

        TriageSignals emptySignals = TriageSignals.empty();
        triageResults.put(0, TriageResult.java(0, 0.9, emptySignals));
        triageResults.put(1, TriageResult.backend(1, 0.8, emptySignals));
        triageResults.put(2, TriageResult.java(2, 0.95, emptySignals));
        triageResults.put(3, TriageResult.backend(3, 0.85, emptySignals));

        // Filter by JAVA
        Set<Integer> javaPages = new HashSet<>();
        for (Map.Entry<Integer, TriageResult> entry : triageResults.entrySet()) {
            if (entry.getValue().getDecision() == TriageDecision.JAVA) {
                javaPages.add(entry.getKey());
            }
        }

        // Filter by BACKEND
        Set<Integer> backendPages = new HashSet<>();
        for (Map.Entry<Integer, TriageResult> entry : triageResults.entrySet()) {
            if (entry.getValue().getDecision() == TriageDecision.BACKEND) {
                backendPages.add(entry.getKey());
            }
        }

        Assertions.assertEquals(2, javaPages.size());
        Assertions.assertTrue(javaPages.contains(0));
        Assertions.assertTrue(javaPages.contains(2));

        Assertions.assertEquals(2, backendPages.size());
        Assertions.assertTrue(backendPages.contains(1));
        Assertions.assertTrue(backendPages.contains(3));
    }

    @Test
    public void testPageNumberConversion() {
        // Test 0-indexed to 1-indexed conversion for API
        Set<Integer> zeroIndexed = new HashSet<>();
        zeroIndexed.add(0);
        zeroIndexed.add(2);
        zeroIndexed.add(5);

        Set<Integer> oneIndexed = new HashSet<>();
        for (Integer page : zeroIndexed) {
            oneIndexed.add(page + 1);
        }

        Assertions.assertEquals(3, oneIndexed.size());
        Assertions.assertTrue(oneIndexed.contains(1));
        Assertions.assertTrue(oneIndexed.contains(3));
        Assertions.assertTrue(oneIndexed.contains(6));
    }

    @Test
    public void testShouldProcessPageWithNullFilter() {
        // null filter means process all pages
        Assertions.assertTrue(shouldProcessPage(0, null));
        Assertions.assertTrue(shouldProcessPage(5, null));
        Assertions.assertTrue(shouldProcessPage(100, null));
    }

    @Test
    public void testShouldProcessPageWithFilter() {
        Set<Integer> filter = new HashSet<>();
        filter.add(0);
        filter.add(2);
        filter.add(5);

        Assertions.assertTrue(shouldProcessPage(0, filter));
        Assertions.assertFalse(shouldProcessPage(1, filter));
        Assertions.assertTrue(shouldProcessPage(2, filter));
        Assertions.assertFalse(shouldProcessPage(3, filter));
        Assertions.assertFalse(shouldProcessPage(4, filter));
        Assertions.assertTrue(shouldProcessPage(5, filter));
    }

    @Test
    public void testInvalidHybridBackendThrows() {
        Config config = new Config();
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            config.setHybrid("invalid");
        });
    }

    @Test
    public void testHybridConfigTimeout() {
        HybridConfig config = new HybridConfig();
        config.setTimeoutMs(60000);
        Assertions.assertEquals(60000, config.getTimeoutMs());

        config.setTimeoutMs(0);
        Assertions.assertEquals(0, config.getTimeoutMs());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            config.setTimeoutMs(-1000);
        });
    }

    @Test
    public void testHybridConfigMaxConcurrentRequests() {
        HybridConfig config = new HybridConfig();
        config.setMaxConcurrentRequests(8);
        Assertions.assertEquals(8, config.getMaxConcurrentRequests());

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            config.setMaxConcurrentRequests(0);
        });
    }

    @Test
    public void testHybridConfigFallbackToggle() {
        HybridConfig config = new HybridConfig();

        // Default is false (fail-fast when hybrid server is unavailable)
        Assertions.assertFalse(config.isFallbackToJava());

        config.setFallbackToJava(true);
        Assertions.assertTrue(config.isFallbackToJava());

        config.setFallbackToJava(false);
        Assertions.assertFalse(config.isFallbackToJava());
    }

    // Helper method matching HybridDocumentProcessor logic
    private static boolean shouldProcessPage(int pageNumber, Set<Integer> pagesToProcess) {
        return pagesToProcess == null || pagesToProcess.contains(pageNumber);
    }

    // ===== OutputFormat Tests =====

    @Test
    public void testOutputFormatApiValue() {
        Assertions.assertEquals("json", OutputFormat.JSON.getApiValue());
        Assertions.assertEquals("md", OutputFormat.MARKDOWN.getApiValue());
        Assertions.assertEquals("html", OutputFormat.HTML.getApiValue());
    }

    @Test
    public void testHybridRequestDefaultOutputFormats() {
        byte[] pdfBytes = new byte[]{1, 2, 3};
        HybridRequest request = HybridRequest.allPages(pdfBytes);

        // Default should include all formats
        Set<OutputFormat> formats = request.getOutputFormats();
        Assertions.assertEquals(3, formats.size());
        Assertions.assertTrue(formats.contains(OutputFormat.JSON));
        Assertions.assertTrue(formats.contains(OutputFormat.MARKDOWN));
        Assertions.assertTrue(formats.contains(OutputFormat.HTML));
        Assertions.assertTrue(request.wantsJson());
        Assertions.assertTrue(request.wantsMarkdown());
        Assertions.assertTrue(request.wantsHtml());
    }

    @Test
    public void testHybridRequestWithJsonOnly() {
        byte[] pdfBytes = new byte[]{1, 2, 3};
        Set<OutputFormat> jsonOnly = EnumSet.of(OutputFormat.JSON);
        HybridRequest request = HybridRequest.allPages(pdfBytes, jsonOnly);

        Set<OutputFormat> formats = request.getOutputFormats();
        Assertions.assertEquals(1, formats.size());
        Assertions.assertTrue(formats.contains(OutputFormat.JSON));
        Assertions.assertFalse(formats.contains(OutputFormat.MARKDOWN));
        Assertions.assertTrue(request.wantsJson());
        Assertions.assertFalse(request.wantsMarkdown());
    }

    @Test
    public void testHybridRequestWithMarkdownOnly() {
        byte[] pdfBytes = new byte[]{1, 2, 3};
        Set<OutputFormat> mdOnly = EnumSet.of(OutputFormat.MARKDOWN);
        HybridRequest request = HybridRequest.allPages(pdfBytes, mdOnly);

        Set<OutputFormat> formats = request.getOutputFormats();
        Assertions.assertEquals(1, formats.size());
        Assertions.assertFalse(formats.contains(OutputFormat.JSON));
        Assertions.assertTrue(formats.contains(OutputFormat.MARKDOWN));
        Assertions.assertFalse(request.wantsJson());
        Assertions.assertTrue(request.wantsMarkdown());
    }

    @Test
    public void testHybridRequestEmptyFormatsFallsBackToAll() {
        byte[] pdfBytes = new byte[]{1, 2, 3};
        Set<OutputFormat> empty = EnumSet.noneOf(OutputFormat.class);
        HybridRequest request = HybridRequest.allPages(pdfBytes, empty);

        // Empty should fallback to all formats
        Set<OutputFormat> formats = request.getOutputFormats();
        Assertions.assertEquals(3, formats.size());
        Assertions.assertTrue(formats.contains(OutputFormat.JSON));
        Assertions.assertTrue(formats.contains(OutputFormat.MARKDOWN));
        Assertions.assertTrue(formats.contains(OutputFormat.HTML));
    }

    @Test
    public void testHybridRequestNullFormatsFallsBackToAll() {
        byte[] pdfBytes = new byte[]{1, 2, 3};
        HybridRequest request = HybridRequest.allPages(pdfBytes, null);

        // null should fallback to all formats
        Set<OutputFormat> formats = request.getOutputFormats();
        Assertions.assertEquals(3, formats.size());
        Assertions.assertTrue(formats.contains(OutputFormat.JSON));
        Assertions.assertTrue(formats.contains(OutputFormat.MARKDOWN));
        Assertions.assertTrue(formats.contains(OutputFormat.HTML));
    }

    @Test
    public void testHybridRequestWithHtmlOnly() {
        byte[] pdfBytes = new byte[]{1, 2, 3};
        Set<OutputFormat> htmlOnly = EnumSet.of(OutputFormat.HTML);
        HybridRequest request = HybridRequest.allPages(pdfBytes, htmlOnly);

        Set<OutputFormat> formats = request.getOutputFormats();
        Assertions.assertEquals(1, formats.size());
        Assertions.assertFalse(formats.contains(OutputFormat.JSON));
        Assertions.assertFalse(formats.contains(OutputFormat.MARKDOWN));
        Assertions.assertTrue(formats.contains(OutputFormat.HTML));
        Assertions.assertFalse(request.wantsJson());
        Assertions.assertFalse(request.wantsMarkdown());
        Assertions.assertTrue(request.wantsHtml());
    }

    // ===== HybridConfig Mode Tests =====

    @Test
    public void testHybridConfigModeDefaults() {
        HybridConfig config = new HybridConfig();

        Assertions.assertEquals(HybridConfig.MODE_AUTO, config.getMode());
        Assertions.assertFalse(config.isFullMode());
    }

    @Test
    public void testHybridConfigModeFullMode() {
        HybridConfig config = new HybridConfig();
        config.setMode(HybridConfig.MODE_FULL);

        Assertions.assertEquals(HybridConfig.MODE_FULL, config.getMode());
        Assertions.assertTrue(config.isFullMode());
    }

    @Test
    public void testDoclingBackendEnabled() {
        Config config = new Config();
        config.setHybrid("docling");

        Assertions.assertTrue(config.isHybridEnabled());
        Assertions.assertEquals("docling", config.getHybrid());
    }

    @Test
    public void testDoclingEffectiveUrl() {
        HybridConfig config = new HybridConfig();

        // docling uses same URL as docling-fast
        Assertions.assertEquals(HybridConfig.DOCLING_FAST_DEFAULT_URL, config.getEffectiveUrl("docling"));
        Assertions.assertEquals(HybridConfig.DOCLING_FAST_DEFAULT_URL, config.getEffectiveUrl("docling-fast"));
    }

    @Test
    public void testHancomAIBackendEnabled() {
        Config config = new Config();
        config.setHybrid("hancom-ai");

        Assertions.assertTrue(config.isHybridEnabled());
        Assertions.assertEquals("hancom-ai", config.getHybrid());
    }

    @Test
    public void testCreateTransformerReturnsHancomAISchemaTransformer() throws Exception {
        Config config = new Config();
        config.setHybrid("hancom-ai");

        // createTransformer is private static — use reflection to verify it returns the correct type
        java.lang.reflect.Method method = HybridDocumentProcessor.class.getDeclaredMethod("createTransformer", Config.class);
        method.setAccessible(true);
        HybridSchemaTransformer transformer = (HybridSchemaTransformer) method.invoke(null, config);

        Assertions.assertNotNull(transformer);
        Assertions.assertInstanceOf(HancomAISchemaTransformer.class, transformer);
    }

    // ===== Backend Chunk Splitting Tests =====

    /** Helper that mirrors the chunk-splitting logic in processBackendPath. */
    private static List<List<Integer>> splitIntoChunks(Set<Integer> pageNumbers, int chunkSize) {
        List<Integer> sorted = new ArrayList<>(new TreeSet<>(pageNumbers));
        List<List<Integer>> chunks = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i += chunkSize) {
            chunks.add(sorted.subList(i, Math.min(i + chunkSize, sorted.size())));
        }
        return chunks;
    }

    @Test
    public void testChunkSplitting_zeroPages() {
        List<List<Integer>> chunks = splitIntoChunks(new HashSet<>(),
                HybridDocumentProcessor.BACKEND_CHUNK_SIZE);
        Assertions.assertTrue(chunks.isEmpty());
    }

    @Test
    public void testChunkSplitting_exactlyChunkSize() {
        Set<Integer> pages = new HashSet<>();
        for (int i = 0; i < HybridDocumentProcessor.BACKEND_CHUNK_SIZE; i++) {
            pages.add(i);
        }
        List<List<Integer>> chunks = splitIntoChunks(pages,
                HybridDocumentProcessor.BACKEND_CHUNK_SIZE);

        Assertions.assertEquals(1, chunks.size());
        Assertions.assertEquals(HybridDocumentProcessor.BACKEND_CHUNK_SIZE, chunks.get(0).size());
    }

    @Test
    public void testChunkSplitting_chunkSizePlusOne() {
        int size = HybridDocumentProcessor.BACKEND_CHUNK_SIZE + 1;
        Set<Integer> pages = new HashSet<>();
        for (int i = 0; i < size; i++) {
            pages.add(i);
        }
        List<List<Integer>> chunks = splitIntoChunks(pages,
                HybridDocumentProcessor.BACKEND_CHUNK_SIZE);

        Assertions.assertEquals(2, chunks.size());
        Assertions.assertEquals(HybridDocumentProcessor.BACKEND_CHUNK_SIZE, chunks.get(0).size());
        Assertions.assertEquals(1, chunks.get(1).size());
    }

    @Test
    public void testChunkSplitting_singlePage() {
        Set<Integer> pages = new HashSet<>();
        pages.add(42);
        List<List<Integer>> chunks = splitIntoChunks(pages,
                HybridDocumentProcessor.BACKEND_CHUNK_SIZE);

        Assertions.assertEquals(1, chunks.size());
        Assertions.assertEquals(1, chunks.get(0).size());
        Assertions.assertEquals(42, (int) chunks.get(0).get(0));
    }

    @Test
    public void testChunkSplitting_nonContiguousPages() {
        // Simulate triage routing every 5th page to backend
        Set<Integer> pages = new HashSet<>();
        for (int i = 0; i < 300; i += 5) {
            pages.add(i);
        }
        // 60 pages total → 2 chunks (50 + 10)
        List<List<Integer>> chunks = splitIntoChunks(pages,
                HybridDocumentProcessor.BACKEND_CHUNK_SIZE);

        Assertions.assertEquals(2, chunks.size());
        Assertions.assertEquals(HybridDocumentProcessor.BACKEND_CHUNK_SIZE, chunks.get(0).size());
        Assertions.assertEquals(10, chunks.get(1).size());

        // Verify sorted order
        for (List<Integer> chunk : chunks) {
            for (int i = 1; i < chunk.size(); i++) {
                Assertions.assertTrue(chunk.get(i) > chunk.get(i - 1),
                        "Pages within chunk should be sorted");
            }
        }
    }

    @Test
    public void testChunkSplitting_largeDocument() {
        // 154 pages like the reporter's PDF
        Set<Integer> pages = new HashSet<>();
        for (int i = 0; i < 154; i++) {
            pages.add(i);
        }
        List<List<Integer>> chunks = splitIntoChunks(pages,
                HybridDocumentProcessor.BACKEND_CHUNK_SIZE);

        Assertions.assertEquals(4, chunks.size()); // 50 + 50 + 50 + 4
        Assertions.assertEquals(50, chunks.get(0).size());
        Assertions.assertEquals(50, chunks.get(1).size());
        Assertions.assertEquals(50, chunks.get(2).size());
        Assertions.assertEquals(4, chunks.get(3).size());

        // All pages accounted for
        Set<Integer> allChunked = new HashSet<>();
        for (List<Integer> chunk : chunks) {
            allChunked.addAll(chunk);
        }
        Assertions.assertEquals(pages, allChunked);
    }

    // failFastIfBackendFailedWithoutFallback — fail-fast contract for backend failures.

    @Test
    public void testFailFast_EmptyFailedPages_NoFallback_DoesNotThrow() throws Exception {
        // Contract: no failed pages means processing succeeded — never throw,
        // regardless of fallback flag.
        HybridConfig config = new HybridConfig();
        config.setFallbackToJava(false);

        HybridDocumentProcessor.failFastIfBackendFailedWithoutFallback(new HashSet<>(), config);
    }

    @Test
    public void testFailFast_EmptyFailedPages_WithFallback_DoesNotThrow() throws Exception {
        HybridConfig config = new HybridConfig();
        config.setFallbackToJava(true);

        HybridDocumentProcessor.failFastIfBackendFailedWithoutFallback(new HashSet<>(), config);
    }

    @Test
    public void testFailFast_NonEmptyFailedPages_WithFallback_DoesNotThrow() throws Exception {
        // Contract: when fallback is enabled, failed pages are reprocessed via Java
        // path elsewhere — this helper must stay silent and never throw.
        HybridConfig config = new HybridConfig();
        config.setFallbackToJava(true);

        Set<Integer> failed = new HashSet<>();
        failed.add(0);
        failed.add(3);

        HybridDocumentProcessor.failFastIfBackendFailedWithoutFallback(failed, config);
    }

    @Test
    public void testFailFast_AllPagesFailed_NoFallback_ThrowsWithPageNumbers() {
        // Contract: backend left every page unprocessed and fallback is disabled —
        // throw so the caller (CLI / library wrapper) can surface a non-zero exit
        // instead of producing an empty result silently.
        HybridConfig config = new HybridConfig();
        config.setFallbackToJava(false);

        Set<Integer> failed = new HashSet<>();
        for (int i = 0; i < 21; i++) {
            failed.add(i);
        }

        java.io.IOException ex = Assertions.assertThrows(java.io.IOException.class, () ->
            HybridDocumentProcessor.failFastIfBackendFailedWithoutFallback(failed, config));

        // Message must carry enough information that automation reading stderr can
        // identify which pages failed without re-running with verbose logs.
        Assertions.assertTrue(ex.getMessage().contains("21 page(s)"),
            "message should include failed page count: " + ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("1") && ex.getMessage().contains("21"),
            "message should include 1-indexed page numbers: " + ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("fallback disabled"),
            "message should explain why processing failed: " + ex.getMessage());
    }

    @Test
    public void testFailFast_PartialPagesFailed_NoFallback_ThrowsWithPageNumbers() {
        // Contract: partial failure is treated the same as total failure when
        // fallback is disabled — any unprocessed page must surface as a non-zero
        // exit, otherwise downstream consumers cannot tell a sparse result from
        // a successful one.
        HybridConfig config = new HybridConfig();
        config.setFallbackToJava(false);

        Set<Integer> failed = new HashSet<>();
        failed.add(1); // page 2 (1-indexed)
        failed.add(5); // page 6
        failed.add(20); // page 21

        java.io.IOException ex = Assertions.assertThrows(java.io.IOException.class, () ->
            HybridDocumentProcessor.failFastIfBackendFailedWithoutFallback(failed, config));

        Assertions.assertTrue(ex.getMessage().contains("3 page(s)"),
            "message should include failed page count: " + ex.getMessage());
        Assertions.assertTrue(ex.getMessage().contains("2") && ex.getMessage().contains("6") && ex.getMessage().contains("21"),
            "message should include 1-indexed page numbers: " + ex.getMessage());
    }
}

/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.hybrid.HybridClient.HybridRequest;
import org.opendataloader.pdf.hybrid.HybridClient.OutputFormat;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageSignals;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        Assertions.assertTrue(config.isFallbackToJava());
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

        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            config.setTimeoutMs(0);
        });

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
    public void testHybridConfigFallback() {
        HybridConfig config = new HybridConfig();

        // Default is true
        Assertions.assertTrue(config.isFallbackToJava());

        config.setFallbackToJava(false);
        Assertions.assertFalse(config.isFallbackToJava());

        config.setFallbackToJava(true);
        Assertions.assertTrue(config.isFallbackToJava());
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
    public void testHybridConfigOcrDefaults() {
        HybridConfig config = new HybridConfig();

        Assertions.assertEquals(HybridConfig.OCR_AUTO, config.getOcrMode());
        Assertions.assertFalse(config.isForceOcr());
    }

    @Test
    public void testHybridConfigOcrForce() {
        HybridConfig config = new HybridConfig();
        config.setOcrMode(HybridConfig.OCR_FORCE);

        Assertions.assertEquals(HybridConfig.OCR_FORCE, config.getOcrMode());
        Assertions.assertTrue(config.isForceOcr());
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
}

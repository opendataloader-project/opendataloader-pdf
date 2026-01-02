/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageSignals;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Unit tests for TriageLogger.
 */
public class TriageLoggerTest {

    private TriageLogger triageLogger;
    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        triageLogger = new TriageLogger();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testCreateTriageJsonWithEmptyResults() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();

        ObjectNode json = triageLogger.createTriageJson("test.pdf", "docling", triageResults);

        Assertions.assertEquals("test.pdf", json.get("document").asText());
        Assertions.assertEquals("docling", json.get("hybrid").asText());
        Assertions.assertEquals(0, json.get("triage").size());
        Assertions.assertEquals(0, json.get("summary").get("totalPages").asInt());
        Assertions.assertEquals(0, json.get("summary").get("javaPages").asInt());
        Assertions.assertEquals(0, json.get("summary").get("backendPages").asInt());
    }

    @Test
    public void testCreateTriageJsonWithResults() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals1 = new TriageSignals(2, 45, 0.04, 0, false, false);
        TriageSignals signals2 = new TriageSignals(28, 32, 0.875, 4, true, false);

        triageResults.put(0, TriageResult.java(0, 0.95, signals1));
        triageResults.put(1, TriageResult.backend(1, 0.82, signals2));

        ObjectNode json = triageLogger.createTriageJson("example.pdf", "docling", triageResults);

        Assertions.assertEquals("example.pdf", json.get("document").asText());
        Assertions.assertEquals("docling", json.get("hybrid").asText());

        // Check triage array
        JsonNode triageArray = json.get("triage");
        Assertions.assertEquals(2, triageArray.size());

        // Check first page (page 1, 1-indexed)
        JsonNode page1 = triageArray.get(0);
        Assertions.assertEquals(1, page1.get("page").asInt()); // 1-indexed
        Assertions.assertEquals("JAVA", page1.get("decision").asText());
        Assertions.assertEquals(0.95, page1.get("confidence").asDouble(), 0.001);

        // Check signals for page 1
        JsonNode signals1Json = page1.get("signals");
        Assertions.assertEquals(2, signals1Json.get("lineChunkCount").asInt());
        Assertions.assertEquals(45, signals1Json.get("textChunkCount").asInt());
        Assertions.assertEquals(0.04, signals1Json.get("lineToTextRatio").asDouble(), 0.001);
        Assertions.assertEquals(0, signals1Json.get("alignedLineGroups").asInt());
        Assertions.assertFalse(signals1Json.get("hasTableBorder").asBoolean());
        Assertions.assertFalse(signals1Json.get("hasSuspiciousPattern").asBoolean());

        // Check second page (page 2, 1-indexed)
        JsonNode page2 = triageArray.get(1);
        Assertions.assertEquals(2, page2.get("page").asInt()); // 1-indexed
        Assertions.assertEquals("BACKEND", page2.get("decision").asText());
        Assertions.assertEquals(0.82, page2.get("confidence").asDouble(), 0.001);

        // Check signals for page 2
        JsonNode signals2Json = page2.get("signals");
        Assertions.assertEquals(28, signals2Json.get("lineChunkCount").asInt());
        Assertions.assertEquals(32, signals2Json.get("textChunkCount").asInt());
        Assertions.assertEquals(0.875, signals2Json.get("lineToTextRatio").asDouble(), 0.001);
        Assertions.assertEquals(4, signals2Json.get("alignedLineGroups").asInt());
        Assertions.assertTrue(signals2Json.get("hasTableBorder").asBoolean());
        Assertions.assertFalse(signals2Json.get("hasSuspiciousPattern").asBoolean());

        // Check summary
        JsonNode summary = json.get("summary");
        Assertions.assertEquals(2, summary.get("totalPages").asInt());
        Assertions.assertEquals(1, summary.get("javaPages").asInt());
        Assertions.assertEquals(1, summary.get("backendPages").asInt());
    }

    @Test
    public void testToJsonString() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals = TriageSignals.empty();
        triageResults.put(0, TriageResult.java(0, 0.9, signals));

        String jsonString = triageLogger.toJsonString("test.pdf", "docling", triageResults);

        // Verify it's valid JSON
        JsonNode json = objectMapper.readTree(jsonString);
        Assertions.assertEquals("test.pdf", json.get("document").asText());
        Assertions.assertEquals("docling", json.get("hybrid").asText());
    }

    @Test
    public void testLogToWriter() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals = new TriageSignals(5, 20, 0.2, 1, false, true);
        triageResults.put(0, TriageResult.backend(0, 0.85, signals));

        StringWriter writer = new StringWriter();
        triageLogger.logToWriter(writer, "output.pdf", "docling", triageResults);

        String jsonString = writer.toString();
        JsonNode json = objectMapper.readTree(jsonString);

        Assertions.assertEquals("output.pdf", json.get("document").asText());
        Assertions.assertEquals(1, json.get("triage").size());
        Assertions.assertTrue(json.get("triage").get(0).get("signals").get("hasSuspiciousPattern").asBoolean());
    }

    @Test
    public void testLogToFile(@TempDir Path tempDir) throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals1 = TriageSignals.empty();
        TriageSignals signals2 = new TriageSignals(10, 10, 0.5, 3, true, false);
        TriageSignals signals3 = TriageSignals.empty();

        triageResults.put(0, TriageResult.java(0, 0.9, signals1));
        triageResults.put(1, TriageResult.backend(1, 0.95, signals2));
        triageResults.put(2, TriageResult.java(2, 0.88, signals3));

        triageLogger.logToFile(tempDir, "document.pdf", "docling", triageResults);

        // Verify file was created
        Path outputPath = tempDir.resolve(TriageLogger.DEFAULT_FILENAME);
        Assertions.assertTrue(Files.exists(outputPath));

        // Verify content
        String content = Files.readString(outputPath);
        JsonNode json = objectMapper.readTree(content);

        Assertions.assertEquals("document.pdf", json.get("document").asText());
        Assertions.assertEquals("docling", json.get("hybrid").asText());
        Assertions.assertEquals(3, json.get("triage").size());
        Assertions.assertEquals(3, json.get("summary").get("totalPages").asInt());
        Assertions.assertEquals(2, json.get("summary").get("javaPages").asInt());
        Assertions.assertEquals(1, json.get("summary").get("backendPages").asInt());
    }

    @Test
    public void testPageOrdering() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals = TriageSignals.empty();

        // Add pages in non-sequential order
        triageResults.put(4, TriageResult.java(4, 0.9, signals));
        triageResults.put(0, TriageResult.java(0, 0.9, signals));
        triageResults.put(2, TriageResult.backend(2, 0.8, signals));
        triageResults.put(1, TriageResult.java(1, 0.9, signals));
        triageResults.put(3, TriageResult.backend(3, 0.85, signals));

        ObjectNode json = triageLogger.createTriageJson("test.pdf", "docling", triageResults);
        JsonNode triageArray = json.get("triage");

        // Verify pages are in ascending order (1-indexed)
        Assertions.assertEquals(1, triageArray.get(0).get("page").asInt());
        Assertions.assertEquals(2, triageArray.get(1).get("page").asInt());
        Assertions.assertEquals(3, triageArray.get(2).get("page").asInt());
        Assertions.assertEquals(4, triageArray.get(3).get("page").asInt());
        Assertions.assertEquals(5, triageArray.get(4).get("page").asInt());
    }

    @Test
    public void testDifferentHybridBackends() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals = TriageSignals.empty();
        triageResults.put(0, TriageResult.java(0, 0.9, signals));

        // Test with different backends
        ObjectNode doclingJson = triageLogger.createTriageJson("test.pdf", "docling", triageResults);
        Assertions.assertEquals("docling", doclingJson.get("hybrid").asText());

        ObjectNode hancomJson = triageLogger.createTriageJson("test.pdf", "hancom", triageResults);
        Assertions.assertEquals("hancom", hancomJson.get("hybrid").asText());

        ObjectNode azureJson = triageLogger.createTriageJson("test.pdf", "azure", triageResults);
        Assertions.assertEquals("azure", azureJson.get("hybrid").asText());
    }

    @Test
    public void testSummaryWithAllJavaPages() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals = TriageSignals.empty();

        for (int i = 0; i < 5; i++) {
            triageResults.put(i, TriageResult.java(i, 0.9, signals));
        }

        ObjectNode json = triageLogger.createTriageJson("test.pdf", "docling", triageResults);
        JsonNode summary = json.get("summary");

        Assertions.assertEquals(5, summary.get("totalPages").asInt());
        Assertions.assertEquals(5, summary.get("javaPages").asInt());
        Assertions.assertEquals(0, summary.get("backendPages").asInt());
    }

    @Test
    public void testSummaryWithAllBackendPages() throws IOException {
        Map<Integer, TriageResult> triageResults = new HashMap<>();
        TriageSignals signals = new TriageSignals(10, 5, 0.67, 4, true, true);

        for (int i = 0; i < 3; i++) {
            triageResults.put(i, TriageResult.backend(i, 0.9, signals));
        }

        ObjectNode json = triageLogger.createTriageJson("test.pdf", "docling", triageResults);
        JsonNode summary = json.get("summary");

        Assertions.assertEquals(3, summary.get("totalPages").asInt());
        Assertions.assertEquals(0, summary.get("javaPages").asInt());
        Assertions.assertEquals(3, summary.get("backendPages").asInt());
    }

    @Test
    public void testDefaultFilename() {
        Assertions.assertEquals("triage.json", TriageLogger.DEFAULT_FILENAME);
    }
}

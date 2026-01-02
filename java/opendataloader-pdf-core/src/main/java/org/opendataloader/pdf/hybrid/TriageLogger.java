/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageSignals;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Logger for triage decisions to JSON format for benchmark evaluation.
 *
 * <p>Output format:
 * <pre>
 * {
 *   "document": "example.pdf",
 *   "hybrid": "docling",
 *   "triage": [
 *     {
 *       "page": 1,
 *       "decision": "JAVA",
 *       "confidence": 0.95,
 *       "signals": {
 *         "lineChunkCount": 2,
 *         "textChunkCount": 45,
 *         "lineToTextRatio": 0.04,
 *         "alignedLineGroups": 0,
 *         "hasTableBorder": false,
 *         "hasSuspiciousPattern": false
 *       }
 *     }
 *   ],
 *   "summary": {
 *     "totalPages": 10,
 *     "javaPages": 8,
 *     "backendPages": 2
 *   }
 * }
 * </pre>
 */
public class TriageLogger {

    private static final Logger LOGGER = Logger.getLogger(TriageLogger.class.getCanonicalName());

    /** Default filename for triage log output. */
    public static final String DEFAULT_FILENAME = "triage.json";

    private final ObjectMapper objectMapper;

    /**
     * Creates a new TriageLogger with default settings.
     */
    public TriageLogger() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * Logs triage results to a JSON file.
     *
     * @param outputDir      The output directory path.
     * @param documentName   The name of the processed document.
     * @param hybridBackend  The hybrid backend used (e.g., "docling").
     * @param triageResults  Map of page number to triage result.
     * @throws IOException If writing the file fails.
     */
    public void logToFile(
            Path outputDir,
            String documentName,
            String hybridBackend,
            Map<Integer, TriageResult> triageResults) throws IOException {

        Path outputPath = outputDir.resolve(DEFAULT_FILENAME);
        Files.createDirectories(outputDir);

        ObjectNode root = createTriageJson(documentName, hybridBackend, triageResults);

        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            objectMapper.writeValue(writer, root);
        }

        LOGGER.log(Level.INFO, "Triage log written to {0}", outputPath);
    }

    /**
     * Writes triage results to a Writer.
     *
     * @param writer         The Writer to write to.
     * @param documentName   The name of the processed document.
     * @param hybridBackend  The hybrid backend used (e.g., "docling").
     * @param triageResults  Map of page number to triage result.
     * @throws IOException If writing fails.
     */
    public void logToWriter(
            Writer writer,
            String documentName,
            String hybridBackend,
            Map<Integer, TriageResult> triageResults) throws IOException {

        ObjectNode root = createTriageJson(documentName, hybridBackend, triageResults);
        objectMapper.writeValue(writer, root);
    }

    /**
     * Creates the triage JSON structure.
     *
     * @param documentName   The name of the processed document.
     * @param hybridBackend  The hybrid backend used.
     * @param triageResults  Map of page number to triage result.
     * @return The root ObjectNode containing all triage data.
     */
    public ObjectNode createTriageJson(
            String documentName,
            String hybridBackend,
            Map<Integer, TriageResult> triageResults) {

        ObjectNode root = objectMapper.createObjectNode();
        root.put("document", documentName);
        root.put("hybrid", hybridBackend);

        // Create triage array
        ArrayNode triageArray = objectMapper.createArrayNode();
        int javaCount = 0;
        int backendCount = 0;

        List<Map.Entry<Integer, TriageResult>> sortedEntries = triageResults.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        for (Map.Entry<Integer, TriageResult> entry : sortedEntries) {
            int pageNumber = entry.getKey();
            TriageResult result = entry.getValue();

            ObjectNode pageNode = objectMapper.createObjectNode();
            pageNode.put("page", pageNumber + 1); // Convert to 1-indexed for output
            pageNode.put("decision", result.getDecision().name());
            pageNode.put("confidence", result.getConfidence());

            // Add signals
            ObjectNode signalsNode = createSignalsNode(result.getSignals());
            pageNode.set("signals", signalsNode);

            triageArray.add(pageNode);

            // Count decisions
            if (result.getDecision() == TriageDecision.JAVA) {
                javaCount++;
            } else {
                backendCount++;
            }
        }

        root.set("triage", triageArray);

        // Create summary
        ObjectNode summaryNode = objectMapper.createObjectNode();
        summaryNode.put("totalPages", triageResults.size());
        summaryNode.put("javaPages", javaCount);
        summaryNode.put("backendPages", backendCount);
        root.set("summary", summaryNode);

        return root;
    }

    /**
     * Creates a JSON node for triage signals.
     *
     * @param signals The triage signals.
     * @return The ObjectNode containing signal data.
     */
    private ObjectNode createSignalsNode(TriageSignals signals) {
        ObjectNode signalsNode = objectMapper.createObjectNode();
        signalsNode.put("lineChunkCount", signals.getLineChunkCount());
        signalsNode.put("textChunkCount", signals.getTextChunkCount());
        signalsNode.put("lineToTextRatio", signals.getLineToTextRatio());
        signalsNode.put("alignedLineGroups", signals.getAlignedLineGroups());
        signalsNode.put("hasTableBorder", signals.hasTableBorder());
        signalsNode.put("hasSuspiciousPattern", signals.hasSuspiciousPattern());
        return signalsNode;
    }

    /**
     * Converts triage results to JSON string.
     *
     * @param documentName   The name of the processed document.
     * @param hybridBackend  The hybrid backend used.
     * @param triageResults  Map of page number to triage result.
     * @return JSON string representation.
     * @throws IOException If serialization fails.
     */
    public String toJsonString(
            String documentName,
            String hybridBackend,
            Map<Integer, TriageResult> triageResults) throws IOException {

        ObjectNode root = createTriageJson(documentName, hybridBackend, triageResults);
        return objectMapper.writeValueAsString(root);
    }
}

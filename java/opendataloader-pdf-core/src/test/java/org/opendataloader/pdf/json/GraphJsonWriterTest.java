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
package org.opendataloader.pdf.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.graph.*;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.quality.TriageDecision;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GraphJsonWriterTest {

    private static final ObjectMapper PLAIN_MAPPER = new ObjectMapper();

    private static TriageDecision passTriage(double score) {
        return new TriageDecision(TriageDecision.Outcome.PASS, score,
                Collections.emptyList(), Collections.emptyList(), null);
    }

    @Test
    void testWritesSidecarFile(@TempDir Path tmpDir) throws Exception {
        ExtractionResult result = ExtractionResult.ofEnrichedNodes(Collections.emptyList());
        TriageDecision triage = passTriage(0.9);

        new GraphJsonWriter().write("paper", tmpDir, result, triage);

        Path expected = tmpDir.resolve("paper-graph.json");
        assertTrue(expected.toFile().exists(), "paper-graph.json should exist");
    }

    @Test
    void testSectionsPresent(@TempDir Path tmpDir) throws Exception {
        List<GraphNode> nodes = List.of(
                new HeadingNode(1, "Introduction", null, 1, null, 1L, 1.0),
                new HeadingNode(2, "Methods",      null, 2, null, 2L, 1.0)
        );
        ExtractionResult result = ExtractionResult.ofEnrichedNodes(nodes);

        new GraphJsonWriter().write("doc", tmpDir, result, passTriage(0.85));

        JsonNode root = PLAIN_MAPPER.readTree(tmpDir.resolve("doc-graph.json").toFile());
        JsonNode sections = root.get("sections");
        assertNotNull(sections);
        assertEquals(2, sections.size());
        assertEquals("Introduction", sections.get(0).get("text").asText());
    }

    @Test
    void testEquationNumberInOutput(@TempDir Path tmpDir) throws Exception {
        List<GraphNode> nodes = List.of(
                new EquationNode("E=mc^2", true, "3.1", null, 3, null, 10L, 0.95)
        );
        ExtractionResult result = ExtractionResult.ofEnrichedNodes(nodes);

        new GraphJsonWriter().write("eq", tmpDir, result, passTriage(0.9));

        JsonNode root = PLAIN_MAPPER.readTree(tmpDir.resolve("eq-graph.json").toFile());
        JsonNode equations = root.get("equations");
        assertNotNull(equations);
        assertEquals(1, equations.size());
        assertEquals("3.1", equations.get(0).get("equation number").asText());
    }

    @Test
    void testReferencesStructured(@TempDir Path tmpDir) throws Exception {
        List<GraphNode> nodes = List.of(
                new ReferenceEntryNode("ref1", "Smith et al. 2020", null, 5, null, 20L, 1.0)
        );
        ExtractionResult result = ExtractionResult.ofEnrichedNodes(nodes);

        new GraphJsonWriter().write("ref", tmpDir, result, passTriage(0.9));

        JsonNode root = PLAIN_MAPPER.readTree(tmpDir.resolve("ref-graph.json").toFile());
        JsonNode references = root.get("references");
        assertNotNull(references);
        assertEquals(1, references.size());
    }

    @Test
    void testCaptionKindFilter(@TempDir Path tmpDir) throws Exception {
        List<GraphNode> nodes = List.of(
                new CaptionNode("figure", "Figure 1", null, "A figure caption", null, 1, null, 100L, 1.0),
                new CaptionNode("table",  "Table 1",  null, "A table caption",  null, 2, null, 101L, 1.0)
        );
        ExtractionResult result = ExtractionResult.ofEnrichedNodes(nodes);

        new GraphJsonWriter().write("cap", tmpDir, result, passTriage(0.9));

        JsonNode root = PLAIN_MAPPER.readTree(tmpDir.resolve("cap-graph.json").toFile());
        JsonNode figures = root.get("figures");
        assertNotNull(figures);
        assertEquals(1, figures.size(), "Only figure captions should appear");
        assertEquals("A figure caption", figures.get(0).get("caption").asText());
    }

    @Test
    void testTriageBlockPresent(@TempDir Path tmpDir) throws Exception {
        ExtractionResult result = ExtractionResult.ofEnrichedNodes(Collections.emptyList());
        TriageDecision triage = passTriage(91.5);

        new GraphJsonWriter().write("triage", tmpDir, result, triage);

        JsonNode root = PLAIN_MAPPER.readTree(tmpDir.resolve("triage-graph.json").toFile());
        JsonNode triageNode = root.get("triage");
        assertNotNull(triageNode);
        assertEquals("PASS", triageNode.get("outcome").asText());
        assertEquals(91.5, triageNode.get("composite_score").asDouble(), 0.001);
    }
}

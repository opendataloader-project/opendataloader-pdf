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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.opendataloader.pdf.graph.*;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.quality.TriageDecision;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the enriched document graph to a {@code {stem}-graph.json} sidecar file.
 *
 * <p>This is the primary mechanism for surfacing structured scientific paper data
 * (sections, equations with numbers, figures, references, citations, triage outcome)
 * to Python MCP clients.
 *
 * <p>Output format:
 * <pre>
 * {
 *   "title": null,
 *   "authors": [],
 *   "page_count": 10,
 *   "sections": [{"level":1, "text":"Introduction", "page":1}],
 *   "equations": [{"id":"...", "latex":"...", "display_mode":true, "page":1, "equation number":"3.1"}],
 *   "figures": [{"id":"...", "caption":"...", "page":2}],
 *   "references": [...],
 *   "citations": [...],
 *   "triage": {"outcome":"PASS", "composite_score":0.91, "gate_failures":[]}
 * }
 * </pre>
 */
public class GraphJsonWriter {

    private final ObjectMapper mapper = ObjectMapperHolder.getObjectMapper();

    public void write(String stem, Path outputDir, ExtractionResult extraction,
                      TriageDecision triage) throws IOException {
        ObjectNode root = mapper.createObjectNode();

        root.putNull("title");
        root.set("authors", mapper.createArrayNode());
        root.put("page_count", extraction.getPageCount());

        List<GraphNode> nodes = extraction.getEnrichedGraphNodes();

        ArrayNode sections   = mapper.createArrayNode();
        ArrayNode equations  = mapper.createArrayNode();
        ArrayNode figures    = mapper.createArrayNode();
        ArrayNode references = mapper.createArrayNode();
        ArrayNode citations  = mapper.createArrayNode();

        for (GraphNode node : nodes) {
            if (node instanceof HeadingNode) {
                HeadingNode h = (HeadingNode) node;
                ObjectNode s = mapper.createObjectNode();
                s.put("level", h.getLevel());
                s.put("text",  h.getText());
                Integer hPage = h.getPage();
                if (hPage != null) { s.put("page", hPage); } else { s.putNull("page"); }
                sections.add(s);

            } else if (node instanceof EquationNode) {
                EquationNode eq = (EquationNode) node;
                ObjectNode e = mapper.createObjectNode();
                Long eqRawId = eq.getRawId();
                if (eqRawId != null) { e.put("id", eqRawId); } else { e.putNull("id"); }
                e.put("latex",        eq.getLatex());
                e.put("display_mode", eq.isDisplayMode());
                Integer eqPage = eq.getPage();
                if (eqPage != null) { e.put("page", eqPage); } else { e.putNull("page"); }
                if (eq.getNumber() != null) {
                    e.put("equation number", eq.getNumber());
                }
                equations.add(e);

            } else if (node instanceof CaptionNode) {
                CaptionNode cap = (CaptionNode) node;
                if ("figure".equalsIgnoreCase(cap.getKind())) {
                    ObjectNode f = mapper.createObjectNode();
                    Long capRawId = cap.getRawId();
                    if (capRawId != null) { f.put("id", capRawId); } else { f.putNull("id"); }
                    f.put("caption", cap.getText());
                    Integer capPage = cap.getPage();
                    if (capPage != null) { f.put("page", capPage); } else { f.putNull("page"); }
                    figures.add(f);
                }

            } else if (node instanceof ReferenceEntryNode) {
                ObjectNode r = mapper.valueToTree(node);
                references.add(r);

            } else if (node instanceof CitationNode) {
                ObjectNode c = mapper.valueToTree(node);
                citations.add(c);
            }
        }

        root.set("sections",   sections);
        root.set("equations",  equations);
        root.set("figures",    figures);
        root.set("references", references);
        root.set("citations",  citations);

        if (triage != null) {
            ObjectNode t = mapper.createObjectNode();
            t.put("outcome",         triage.getOutcome().name());
            t.put("composite_score", triage.getCompositeScore());
            ArrayNode failures = mapper.createArrayNode();
            if (triage.getGateFailureReasons() != null) {
                triage.getGateFailureReasons().forEach(failures::add);
            }
            t.set("gate_failures", failures);
            root.set("triage", t);
        } else {
            root.putNull("triage");
        }

        Path out = outputDir.resolve(stem + "-graph.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
    }
}

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for PDFDLOSP-7: when `--use-struct-tree` is enabled,
 * recognised content items must carry a stable `id` so downstream consumers
 * can correlate elements across passes (caption linking, hybrid backends,
 * external pipelines). Prior to the fix only the items that flowed through
 * HeaderFooterProcessor (e.g. footers) had an id; paragraphs, images,
 * headings and list containers produced via the tagged-PDF code path were
 * silently emitted without one.
 */
class PdfDlospStructTreeIdsIntegrationTest {

    private static final String SAMPLE_PDF =
            "../../samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-01_Magazine-danish.pdf";

    /**
     * Element types for which every emitted instance must carry an id.
     * Sub-elements that are conventionally addressed via their parent's id
     * (text chunks inside a footer, individual table cells) are excluded.
     *
     * Note: the Magazine fixture exercises paragraph/heading/image/list/list
     * item/footer/caption. `table` and `header` are listed for completeness
     * but are not present in this PDF; they remain in the set so a future
     * fixture that produces them will be checked automatically.
     */
    private static final Set<String> ID_MANDATORY_TYPES = new HashSet<>(Arrays.asList(
            "paragraph",
            "heading",
            "image",
            "list",
            "list item",
            "footer",
            "header",
            "caption",
            "table"
    ));

    @TempDir
    Path tempDir;

    @Test
    void structTreeMode_emitsIdForEveryRecognisedContentItem() throws Exception {
        List<String> missing = runAndCollectMissingIds(true);
        assertTrue(missing.isEmpty(),
                "Every content item of an id-mandatory type must carry an `id` " +
                        "field when --use-struct-tree is enabled. Offending paths: " + missing);
    }

    /**
     * Locks in the no-regression half of the PDFDLOSP-7 fix: the fix is
     * deliberately localised to the tagged-PDF path so non-tagged output is
     * unchanged. The default code path already gives every top-level
     * paragraph/heading/image/list/footer/caption an id via
     * DocumentProcessor.setIDs; list items have never been emitted with an id
     * in this mode (they are still addressed via their parent list) and that
     * is preserved here. If a future refactor accidentally promotes the
     * recursive id-fill into shared code and changes that, this test catches
     * it by failing on the top-level types instead.
     */
    @Test
    void nonStructTreeMode_topLevelItemsStillCarryIds() throws Exception {
        List<String> missing = runAndCollectMissingIds(false);
        // Filter out list items: in default mode they have always been emitted
        // without an id (PDFDLOSP separate latent bug, intentionally out of
        // scope for this fix).
        List<String> material = new ArrayList<>();
        for (String path : missing) {
            if (!path.contains("type=list item")) {
                material.add(path);
            }
        }
        assertTrue(material.isEmpty(),
                "Non-tagged mode must still emit ids for top-level " +
                        "paragraph/heading/image/list/footer/caption. Offending paths: " + material);
    }

    private List<String> runAndCollectMissingIds(boolean useStructTree) throws Exception {
        File source = new File(SAMPLE_PDF);
        assertTrue(source.exists(), "Sample PDF not found at " + source.getAbsolutePath());

        Path inputPdf = tempDir.resolve("input-" + useStructTree + ".pdf");
        Files.copy(source.toPath(), inputPdf);

        Path outputDir = tempDir.resolve("output-" + useStructTree);
        Files.createDirectories(outputDir);

        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        config.setGenerateJSON(true);
        config.setUseStructTree(useStructTree);

        DocumentProcessor.processFile(inputPdf.toAbsolutePath().toString(), config);

        Path resultJson = outputDir.resolve(inputPdf.getFileName().toString().replace(".pdf", ".json"));
        assertTrue(Files.exists(resultJson),
                "Expected JSON output at " + resultJson.toAbsolutePath());

        JsonNode root = new ObjectMapper().readTree(resultJson.toFile());
        List<String> missing = new ArrayList<>();
        collectIdMissingPaths(root, "$", missing);
        return missing;
    }

    private static void collectIdMissingPaths(JsonNode node, String path, List<String> missing) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode typeNode = node.get("type");
            if (typeNode != null && typeNode.isTextual()
                    && ID_MANDATORY_TYPES.contains(typeNode.asText())
                    && !node.has("id")) {
                missing.add(path + " (type=" + typeNode.asText() + ")");
            }
            node.fields().forEachRemaining(entry ->
                    collectIdMissingPaths(entry.getValue(),
                            path + "." + entry.getKey(), missing));
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                collectIdMissingPaths(node.get(i), path + "[" + i + "]", missing);
            }
        }
    }
}

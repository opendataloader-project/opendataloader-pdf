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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end guard for off-page text filtering through the real ForkJoinPool
 * processing pipeline.
 *
 * <p>The fixture {@code off-page-text.pdf} (see generate-off-page-text-pdf.py)
 * draws {@value #ON_PAGE} at y=700 (inside the [0 0 612 792] page) and
 * {@value #OFF_PAGE} at y=900 (above the page top, outside the crop box).
 *
 * <p>"Off-page" means positioned outside the crop box; this is unrelated to the
 * contrast-based hidden-text filter (it is handled by the off-page filter only).
 *
 * <p>This exercises the regression where {@code filterOutOfPageContents} ran on
 * ForkJoinPool worker threads but {@code getPageBoundingBox()} returned null
 * there (StaticResources is a ThreadLocal populated only on the main thread), so
 * the filter silently no-opped and off-page text leaked into the output. A unit
 * test that calls the filter directly would NOT catch this — it must go through
 * the parallel pipeline via {@link DocumentProcessor#processFile}.
 */
class OffPageTextFilterIntegrationTest {

    private static final String TEST_PDF =
        new File("src/test/resources/off-page-text.pdf").getAbsolutePath();
    private static final String OUTPUT_BASENAME = "off-page-text";

    private static final String ON_PAGE = "ON_PAGE_TEXT";
    private static final String OFF_PAGE = "OFF_PAGE_TEXT";

    @TempDir
    Path filterOnDir;

    @TempDir
    Path filterOffDir;

    @Test
    void offPageTextIsRemovedByDefault() throws IOException {
        String text = runAndCollectText(filterOnDir, true);

        assertTrue(text.contains(ON_PAGE),
            "On-page text must be preserved; got: " + text);
        assertFalse(text.contains(OFF_PAGE),
            "Off-page text (outside the crop box) must be filtered out when the " +
            "off-page safety filter is enabled (default). This guards the " +
            "ForkJoinPool ThreadLocal regression. Got: " + text);
    }

    @Test
    void offPageTextIsExtractedWhenFilterDisabled() throws IOException {
        // Proves the fixture is meaningful: opendataloader DOES extract the
        // off-page run, so removal in the other test is attributable to the
        // off-page filter (not to the parser dropping it).
        String text = runAndCollectText(filterOffDir, false);

        assertTrue(text.contains(ON_PAGE), "On-page text must be present; got: " + text);
        assertTrue(text.contains(OFF_PAGE),
            "With the off-page filter disabled the off-page run must remain, " +
            "otherwise this fixture cannot detect the regression. Got: " + text);
    }

    private String runAndCollectText(Path outputDir, boolean filterOutOfPage) throws IOException {
        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        config.setGenerateJSON(true);
        config.getFilterConfig().setFilterOutOfPage(filterOutOfPage);

        DocumentProcessor.processFile(TEST_PDF, config);

        Path jsonOutput = outputDir.resolve(OUTPUT_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output missing at " + jsonOutput);

        JsonNode root = new ObjectMapper().readTree(Files.readString(jsonOutput));
        StringBuilder sb = new StringBuilder();
        collectContent(root, sb);
        return sb.toString();
    }

    private static void collectContent(JsonNode node, StringBuilder sb) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            JsonNode content = node.get("content");
            if (content != null && content.isTextual()) {
                sb.append(content.asText()).append('\n');
            }
            node.elements().forEachRemaining(child -> collectContent(child, sb));
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                collectContent(child, sb);
            }
        }
    }
}

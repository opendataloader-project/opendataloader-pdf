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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the contract: --include-header-footer must affect JSON output the
 * same way it affects markdown/text/html. When disabled (default), JSON must
 * not contain header/footer elements; when enabled, it must.
 */
class IncludeHeaderFooterJsonIntegrationTest {

    private static final String SAMPLE_PDF =
        "../../samples/pdf/pdfua-1-reference-suite-1-1/PDFUA-Ref-2-04_Presentation.pdf";
    private static final String OUTPUT_BASENAME = "PDFUA-Ref-2-04_Presentation";

    @TempDir
    Path withFlagDir;

    @TempDir
    Path withoutFlagDir;

    private File samplePdf;

    @BeforeEach
    void setUp() {
        samplePdf = new File(SAMPLE_PDF);
        assertTrue(samplePdf.exists(), "Sample PDF not found at " + samplePdf.getAbsolutePath());
    }

    @Test
    void jsonOutputRespectsIncludeHeaderFooterFlag() throws IOException {
        int countWithFlag = runAndCountHeaderFooter(withFlagDir, true);
        int countWithoutFlag = runAndCountHeaderFooter(withoutFlagDir, false);

        assertTrue(countWithFlag > 0,
            "Sample PDF must have at least one detected header/footer for this " +
            "test to be meaningful; got " + countWithFlag + ". " +
            "If detection regressed, pick a different fixture PDF.");
        assertEquals(0, countWithoutFlag,
            "JSON output must not contain header/footer elements when " +
            "--include-header-footer is disabled (default). " +
            "Got " + countWithoutFlag + " element(s).");
    }

    private int runAndCountHeaderFooter(Path outputDir, boolean includeHeaderFooter) throws IOException {
        Config config = new Config();
        config.setOutputFolder(outputDir.toString());
        config.setGenerateJSON(true);
        config.setIncludeHeaderFooter(includeHeaderFooter);

        DocumentProcessor.processFile(samplePdf.getAbsolutePath(), config);

        Path jsonOutput = outputDir.resolve(OUTPUT_BASENAME + ".json");
        assertTrue(Files.exists(jsonOutput), "JSON output missing at " + jsonOutput);

        JsonNode root = new ObjectMapper().readTree(Files.readString(jsonOutput));
        return countHeaderFooterTypes(root);
    }

    private static int countHeaderFooterTypes(JsonNode node) {
        if (node == null) {
            return 0;
        }
        int count = 0;
        if (node.isObject()) {
            JsonNode type = node.get("type");
            if (type != null) {
                String typeName = type.asText();
                if ("header".equals(typeName) || "footer".equals(typeName)) {
                    count++;
                }
            }
            java.util.Iterator<JsonNode> iter = node.elements();
            while (iter.hasNext()) {
                count += countHeaderFooterTypes(iter.next());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                count += countHeaderFooterTypes(child);
            }
        }
        return count;
    }
}

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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Issue767IntegrationTest {

    private static final String REPRO_PDF = "/issue-767-repro.pdf";

    @TempDir
    Path tempDir;

    @Test
    void jsonOutputOmitsNullCharacterFromUnmappedToUnicodeEntry() throws IOException {
        Path inputPdf = tempDir.resolve("issue-767-repro.pdf");
        try (InputStream sample = getClass().getResourceAsStream(REPRO_PDF)) {
            if (sample == null) {
                throw new IOException("Missing regression PDF resource: " + REPRO_PDF);
            }
            Files.copy(sample, inputPdf);
        }

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setGenerateJSON(true);

        DocumentProcessor.processFile(inputPdf.toString(), config);

        String json = Files.readString(tempDir.resolve("issue-767-repro.json"));
        assertFalse(json.contains("\\u0000"), "JSON text should not retain a null character");

        JsonNode root = new ObjectMapper().readTree(json);
        var contentValues = root.findValues("content");
        assertFalse(contentValues.stream().anyMatch(content -> content.asText().contains("\u0000")),
            "Parsed JSON text should not contain a null character");
        assertTrue(contentValues.stream()
                .anyMatch(content -> content.asText().equals("Relevant terms here are e-mail, the EEA-agreement, "
                    + "GDPR-fines, the IT department, AI-agents, AI-systems, AI-policy, AI-generated summaries and "
                    + "ISO 27001-certification.")),
            "JSON text should preserve the full paragraph with the null character omitted");
    }
}

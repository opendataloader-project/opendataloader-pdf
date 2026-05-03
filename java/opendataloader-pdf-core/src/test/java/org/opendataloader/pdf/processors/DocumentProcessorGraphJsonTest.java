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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DocumentProcessorGraphJsonTest {

    @Test
    void testGraphJsonSidecarWrittenWhenFormatRequested(@TempDir Path tmpDir)
            throws Exception {
        File pdf = new File("src/test/resources/cid-font-no-tounicode.pdf");
        if (!pdf.exists()) {
            return;
        }

        Config config = new Config();
        config.setOutputFolder(tmpDir.toString());
        config.setGenerateJSON(false);
        config.setGenerateGraphJson(true);

        DocumentProcessor.processFile(pdf.getAbsolutePath(), config);

        File sidecar = tmpDir.resolve("cid-font-no-tounicode-graph.json").toFile();
        assertTrue(sidecar.exists(), "graph-json sidecar should be written");
        assertTrue(sidecar.length() > 10, "sidecar should be non-empty");
    }
}

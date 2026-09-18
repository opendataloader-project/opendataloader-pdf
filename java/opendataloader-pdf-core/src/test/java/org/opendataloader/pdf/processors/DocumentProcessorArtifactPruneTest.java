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
import org.opendataloader.pdf.api.OutputWriter;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.LinesCollection;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedSet;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DocumentProcessorArtifactPruneTest {

    private static final String SAMPLE_PDF = "../../samples/pdf/1901.03003.pdf";

    @TempDir
    Path tempDir;

    @Test
    void localExtractionPrunesArtifactsAndKeepsLaterProcessingWorking() throws Exception {
        File pdf = new File(SAMPLE_PDF);
        assumeTrue(pdf.exists(), "Sample PDF not found: " + pdf.getAbsolutePath());

        Config config = new Config();
        config.setOutputFolder(tempDir.toString());
        config.setDetectStrikethrough(true);
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setImageOutput("off");

        int artifactsBefore;
        try {
            DocumentProcessor.preprocessing(pdf.getAbsolutePath(), config);
            artifactsBefore = artifactCount();
        } finally {
            closeCurrentPdf();
        }

        try {
            ExtractionResult result = DocumentProcessor.extractContents(pdf.getAbsolutePath(), config);
            assertFalse(result.getContents().isEmpty(), "Expected extracted contents");

            int artifactsAfter = artifactCount();
            assertTrue(artifactsAfter < artifactsBefore,
                "Expected pruning to reduce veraPDF artifacts: before=" + artifactsBefore
                    + " after=" + artifactsAfter);

            LinesCollection lines = StaticContainers.getLinesCollection();
            assertNotNull(lines, "Line cache must remain available after pruning");
            int cachedLines = 0;
            int pages = StaticContainers.getDocument().getNumberOfPages();
            for (int page = 0; page < pages; page++) {
                for (Object artifact : StaticContainers.getDocument().getArtifacts(page)) {
                    assertFalse(artifact instanceof LineChunk,
                        "Line artifacts should be pruned after strikethrough processing");
                }

                SortedSet<LineChunk> horizontal = lines.getHorizontalLines(page);
                SortedSet<LineChunk> vertical = lines.getVerticalLines(page);
                SortedSet<LineChunk> squares = lines.getSquares(page);
                assertSame(horizontal, lines.getHorizontalLines(page));
                assertSame(vertical, lines.getVerticalLines(page));
                assertSame(squares, lines.getSquares(page));
                cachedLines += horizontal.size() + vertical.size() + squares.size();
            }
            assertTrue(cachedLines > 0, "Expected cached lines to survive artifact pruning");

            OutputWriter.writeOutputs(pdf.getAbsolutePath(), result, config);
            Path markdown = tempDir.resolve("1901.03003.md");
            assertTrue(Files.isRegularFile(markdown) && Files.size(markdown) > 0,
                "Later Markdown generation should still work after pruning");
        } finally {
            closeCurrentPdf();
        }
    }

    private static int artifactCount() {
        int count = 0;
        int pages = StaticContainers.getDocument().getNumberOfPages();
        for (int page = 0; page < pages; page++) {
            List<?> artifacts = StaticContainers.getDocument().getArtifacts(page);
            count += artifacts == null ? 0 : artifacts.size();
        }
        return count;
    }

    private static void closeCurrentPdf() throws IOException {
        PDDocument document = StaticResources.getDocument();
        if (document != null) {
            document.close();
        }
        StaticResources.clear();
        StaticContainers.updateContainers(null);
        StaticLayoutContainers.clearContainers();
    }
}

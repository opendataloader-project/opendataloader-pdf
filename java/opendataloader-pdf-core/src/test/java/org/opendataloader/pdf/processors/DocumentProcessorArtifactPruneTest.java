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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OutputWriter;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.LinesCollection;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
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

    @Test
    void hiddenTextFilteringAlsoPrunesDiscardedArtifacts() throws Exception {
        Path pdf = tempDir.resolve("hidden-text.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                writeText(stream, "PUBLIC", 50, 700, false);
                writeText(stream, "SECRET", 50, 600, true);
            }
            document.save(pdf.toFile());
        }

        Config config = new Config();
        config.setImageOutput("off");
        config.getFilterConfig().setFilterHiddenText(true);

        try {
            ExtractionResult result = DocumentProcessor.extractContents(pdf.toString(), config);
            String outputText = result.getContents().toString();
            assertTrue(outputText.contains("PUBLIC"), "Visible text should remain in extracted contents");
            assertFalse(outputText.contains("SECRET"), "Hidden text should be filtered from contents");

            assertFalse(StaticContainers.getDocument().getArtifacts(0).stream()
                    .filter(TextChunk.class::isInstance)
                    .map(TextChunk.class::cast)
                    .anyMatch(chunk -> chunk.getValue().contains("SECRET")),
                "Hidden text should not remain in veraPDF artifacts");
        } finally {
            closeCurrentPdf();
        }
    }

    private static void writeText(PDPageContentStream stream, String value, float x, float y,
                                  boolean hidden) throws IOException {
        stream.setNonStrokingColor(hidden ? 1f : 0f, hidden ? 1f : 0f, hidden ? 1f : 0f);
        stream.beginText();
        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        stream.newLineAtOffset(x, y);
        stream.showText(value);
        stream.endText();
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
        org.verapdf.pd.PDDocument document = StaticResources.getDocument();
        if (document != null) {
            document.close();
        }
        StaticResources.clear();
        StaticContainers.updateContainers(null);
        StaticLayoutContainers.clearContainers();
    }
}

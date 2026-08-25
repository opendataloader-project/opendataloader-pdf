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
import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end check that {@code --content-safety-off background} keeps vector
 * graphics that the size heuristic would otherwise classify as a page
 * background.
 *
 * <p>The fixture draws one filled rectangle covering most of the page, which
 * {@code ContentFilterProcessor.isBackground} matches (width &gt; 50% and
 * height &gt; 10% of the page). With the filter on, that {@code LineArtChunk}
 * is removed before tagging; with it off, it survives into the contents.
 */
class BackgroundFilterTest {

    /** One-page PDF whose only mark is a page-covering filled rectangle. */
    private static Path writePageSizedVectorPdf(Path dir) throws IOException {
        Path file = dir.resolve("page-sized-vector.pdf");
        try (org.apache.pdfbox.pdmodel.PDDocument doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            org.apache.pdfbox.pdmodel.PDPage page = new org.apache.pdfbox.pdmodel.PDPage(
                    org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER);
            doc.addPage(page);
            try (org.apache.pdfbox.pdmodel.PDPageContentStream cs =
                         new org.apache.pdfbox.pdmodel.PDPageContentStream(doc, page)) {
                // Covers 90% of the width and 92% of the height, so isBackground()
                // matches on both of its clauses.
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f);
                cs.addRect(30, 30, 550, 730);
                cs.fill();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static long countLineArt(Path pdf, boolean filterBackgrounds, Path outDir)
            throws IOException {
        Config config = new Config();
        config.setOutputFolder(outDir.toString());
        config.setGenerateJSON(false);
        config.getFilterConfig().setFilterBackgrounds(filterBackgrounds);

        DocumentProcessor.preprocessing(pdf.toString(), config);
        List<org.verapdf.wcag.algorithms.entities.IObject> page =
                ContentFilterProcessor.getFilteredContents(
                        pdf.toString(),
                        org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers
                                .getDocument().getArtifacts(0),
                        0,
                        config);
        return page.stream()
                .filter(o -> o instanceof org.verapdf.wcag.algorithms.entities.content.LineArtChunk)
                .count();
    }

    @Test
    void disablingTheBackgroundFilterKeepsThePageSizedVector() throws IOException {
        Path dir = Files.createTempDirectory("bgfilter");
        Path pdf = writePageSizedVectorPdf(dir);

        long kept = countLineArt(pdf, false, dir);
        long removed = countLineArt(pdf, true, dir);

        assertTrue(kept > removed,
                "with the background filter off, at least one more LineArtChunk should survive; "
                        + "kept=" + kept + " removed=" + removed);
        assertTrue(kept > 0, "the page-covering rectangle should be present when the filter is off");

        deleteRecursively(dir.toFile());
    }

    private static void deleteRecursively(File f) {
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}

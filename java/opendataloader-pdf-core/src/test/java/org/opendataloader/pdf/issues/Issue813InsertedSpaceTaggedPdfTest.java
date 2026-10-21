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
package org.opendataloader.pdf.issues;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #813: the tagged PDF of a document failed with {@code IndexOutOfBoundsException: Index -1}
 * in {@code AutoTaggingProcessor.mergeAdjacentTextShows}, and no tagged PDF was written.
 *
 * <p>The trigger is a space that veraPDF inserts between two strings of one {@code TJ}. The space
 * is not in the content stream, so its stream info has the operator index -1. veraPDF inserts it
 * when the gap is larger than 0.17 times the font size. The space stays in the text chunk only
 * if it is not wider than 0.21 times the font size: a wider space splits the chunk
 * ({@code TextChunkUtils.splitTextChunkByWhiteSpaces}), and the inserted space is then dropped.
 * pdfTeX, which makes most arXiv papers, writes word spacing as a {@code TJ} offset. On the line
 * that fails in the reporter's document, the body text is 10.9091 pt and the offsets are -198 and
 * -199 (0.198 em, 2.16 pt), between the two limits (1.85 pt and 2.29 pt).
 *
 * <p>The sample is synthetic: one page with two paragraphs of body text. Each line is one
 * {@code TJ} with an offset of -198 between the words. A short text alone on a page is tagged as
 * a heading, and the heading path does not reach the merge, so the sample needs body text. The
 * output stays in {@code target/test-samples/issue-813/}.
 */
class Issue813InsertedSpaceTaggedPdfTest {

    private static final float FONT_SIZE = 10.9091f;
    private static final float LEADING = 13;
    // Word spacing of the failing line: over the insert limit (0.17 em), under the split limit (0.21 em).
    private static final float WORD_GAP = -198;
    private static final String[][] LINES = {
        {"Autoregressive", "models", "dominate", "the", "current", "landscape", "of", "large", "language"},
        {"models,", "but", "they", "generate", "one", "token", "at", "a", "time", "and", "cannot", "revise"},
        {"earlier", "output.", "Diffusion", "models", "refine", "all", "positions", "in", "parallel", "and"},
        {"can", "correct", "a", "mistake", "in", "a", "later", "step.", "This", "report", "describes", "a"},
        {"diffusion", "variant", "of", "an", "open", "model", "and", "the", "data", "that", "trained", "it."},
        {"We", "compare", "it", "with", "the", "base", "model", "on", "common", "tasks."},
        {},
        {"The", "first", "section", "gives", "the", "method.", "The", "second", "section", "gives", "the"},
        {"training", "setup,", "and", "the", "third", "section", "gives", "the", "results", "on", "each"},
        {"benchmark.", "The", "last", "section", "discusses", "the", "limits", "of", "the", "approach"},
        {"and", "the", "work", "that", "remains.", "All", "numbers", "are", "averages", "of", "three"},
        {"runs", "with", "different", "seeds,", "and", "the", "tables", "give", "the", "spread", "of"},
        {"each", "value", "where", "it", "is", "larger", "than", "one", "point."},
    };

    private static Path writeKernedWords(Path dir) throws IOException {
        Path file = dir.resolve("kerned.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.TIMES_ROMAN);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(font, FONT_SIZE);
                cs.newLineAtOffset(72, 500);
                for (String[] words : LINES) {
                    if (words.length == 0) {
                        cs.newLineAtOffset(0, -LEADING);
                        continue;
                    }
                    Object[] tj = new Object[words.length * 2 - 1];
                    for (int i = 0; i < words.length; i++) {
                        tj[i * 2] = words[i];
                        if (i > 0) {
                            tj[i * 2 - 1] = WORD_GAP;
                        }
                    }
                    cs.showTextWithPositioning(tj);
                    cs.newLineAtOffset(0, -LEADING);
                }
                cs.endText();
            }
            doc.save(file.toFile());
        }
        return file;
    }

    private static int countMarkedContentSequences(File pdf) throws IOException {
        int count = 0;
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            for (Object token : new PDFStreamParser(doc.getPage(0)).parse()) {
                if (token instanceof Operator && "BDC".equals(((Operator) token).getName())) {
                    count++;
                }
            }
        }
        return count;
    }


    @Test
    void insertedSpaceDoesNotStopTheTaggedPdf() throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-813");
        Path source = writeKernedWords(dir);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);

        DocumentProcessor.processFile(source.toString(), config);

        File tagged = dir.resolve("kerned_tagged.pdf").toFile();
        assertThat(tagged).as("tagged PDF").isFile();
        assertThat(countMarkedContentSequences(tagged)).as("marked-content sequences on the text").isPositive();
    }
}

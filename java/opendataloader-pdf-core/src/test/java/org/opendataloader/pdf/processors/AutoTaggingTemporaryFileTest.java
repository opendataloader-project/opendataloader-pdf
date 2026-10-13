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

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.AutoTagger;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.TaggingResult;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tagging writes one or more new content streams for each page. It must not leave veraPDF
 * temporary files ({@code tmp_pdf_file*.pdf}) in {@code java.io.tmpdir}. Before the fix, each new
 * stream left one file, also after the document was closed and after the JVM exited. The tagged
 * file must still decode: each page has its text in a marked-content sequence with an MCID.
 *
 * <p>The core pom gives the test JVM its own {@code java.io.tmpdir} ({@code target/}), so files of
 * other processes do not count.
 *
 * <p>Each page has a different paragraph with no number. Text that repeats on each page, also with
 * a page number that changes, is a header or a footer. It becomes an artifact ({@code /Artifact BMC})
 * and gets no MCID.
 */
class AutoTaggingTemporaryFileTest {

    private static final String[] PARAGRAPHS = {
        "Rivers carry fresh water from the hills to the sea.",
        "A small library opens early on each weekday morning.",
        "The bridge over the canal was painted green last spring.",
    };


    @Test
    void closedTaggingResultLeavesNoTemporaryFiles() throws IOException {
        Path directory = TestSamples.emptyDirectory("auto-tagging-temporary-file");
        Path input = directory.resolve("input.pdf");
        writeSample(input);

        Set<String> before = veraPdfTemporaryFiles();
        Path output = directory.resolve("input_tagged.pdf");
        try (TaggingResult result = AutoTagger.tag(input.toString(), new Config(), null)) {
            result.saveTo(output.toString());
        }

        assertThat(veraPdfTemporaryFiles()).isSubsetOf(before);
        assertTaggedPdfDecodes(output);
    }

    @Test
    void taggedPdfOutputLeavesNoTemporaryFiles() throws IOException {
        Path directory = TestSamples.emptyDirectory("auto-tagging-temporary-file-output");
        Path input = directory.resolve("input.pdf");
        writeSample(input);
        Config config = new Config();
        config.setOutputFolder(directory.resolve("output").toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);

        Set<String> before = veraPdfTemporaryFiles();
        DocumentProcessor.processFile(input.toString(), config);

        assertThat(veraPdfTemporaryFiles()).isSubsetOf(before);
        assertTaggedPdfDecodes(directory.resolve("output").resolve("input_tagged.pdf"));
    }

    private static void assertTaggedPdfDecodes(Path pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            assertThat(document.getNumberOfPages()).isEqualTo(PARAGRAPHS.length);
            for (int number = 1; number <= PARAGRAPHS.length; number++) {
                PDPage page = document.getPage(number - 1);
                try (InputStream contents = page.getContents()) {
                    // Tagging puts the paragraph in a sequence "/<type> <</MCID n>> BDC ... EMC".
                    assertThat(new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1))
                        .as("decoded stream of page %d", number)
                        .contains(paragraph(number), "/MCID", "BDC", "EMC");
                }
            }
        }
    }

    private static String paragraph(int number) {
        return PARAGRAPHS[number - 1];
    }

    private static void writeSample(Path pdf) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (int number = 1; number <= PARAGRAPHS.length; number++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(font, 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(paragraph(number));
                    content.endText();
                }
            }
            document.save(pdf.toFile());
        }
    }

    private static Set<String> veraPdfTemporaryFiles() {
        Set<String> names = new HashSet<>();
        File[] files = new File(System.getProperty("java.io.tmpdir"))
            .listFiles((folder, name) -> name.startsWith("tmp_pdf_file") && name.endsWith(".pdf"));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }
}

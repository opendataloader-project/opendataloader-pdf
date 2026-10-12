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
import org.apache.pdfbox.text.PDFTextStripper;
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
 * file must still decode: each page has its text and its marked content.
 *
 * <p>The core pom gives the test JVM its own {@code java.io.tmpdir} ({@code target/}), so files of
 * other processes do not count.
 */
class AutoTaggingTemporaryFileTest {

    private static final int PAGES = 3;
    private static final String TEXT = "A paragraph of a synthetic sample for tagging.";

    @Test
    void closedTaggingResultLeavesNoTemporaryFiles() throws IOException {
        Path directory = TestSamples.emptyDirectory("auto-tagging-temporary-file");
        Path input = directory.resolve("input.pdf");
        TestSamples.writeTextPdf(input, PAGES, TEXT);

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
        TestSamples.writeTextPdf(input, PAGES, TEXT);
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
            assertThat(document.getNumberOfPages()).isEqualTo(PAGES);
            assertThat(new PDFTextStripper().getText(document)).contains(TEXT);
            for (PDPage page : document.getPages()) {
                try (InputStream contents = page.getContents()) {
                    // EMC ends each marked-content sequence (BDC or BMC) that tagging adds.
                    assertThat(new String(contents.readAllBytes(), StandardCharsets.ISO_8859_1))
                        .as("decoded page stream").contains(TEXT, "EMC");
                }
            }
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

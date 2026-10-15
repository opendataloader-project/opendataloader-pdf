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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.AutoTagger;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OutputWriter;
import org.opendataloader.pdf.api.TaggingResult;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.opendataloader.pdf.issues.Issue779ExtractionCloseTest.veraPdfTemporaryFiles;
import static org.opendataloader.pdf.issues.Issue779ExtractionCloseTest.writeSample;

/**
 * Issue #783: if {@code DocumentProcessor.extractContents} throws after it opened the veraPDF
 * document, the caller gets no {@code ExtractionResult} and cannot close the document.
 * {@code AutoTagger.tag(String, Config, Float)} has the same problem. Before the fix, the input
 * file stayed open, and the temporary file ({@code tmp_pdf_file*.pdf}) of the embedded font
 * program stayed in {@code java.io.tmpdir}, also after the JVM exited.
 *
 * <p>The failure is the one of the issue: hybrid mode without a backend and without the Java
 * fallback. The health check throws {@code IOException} after the document is open. Port 1 on
 * the loopback address has no server. The sample is the synthetic sample of #779: its CFF font
 * program is larger than the in-memory limit of veraPDF.
 */
class Issue783FailedExtractionCloseTest {

    @AfterAll
    static void shutdownHybridClients() {
        AutoTagger.shutdown();
    }

    @Test
    void tagReleasesTheDocumentWhenTheExtractionThrows() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-783-tag");
        Path input = writeSample(directory.resolve("input.pdf"));

        Set<String> before = veraPdfTemporaryFiles();
        assertThatThrownBy(() -> {
            try (TaggingResult ignored = AutoTagger.tag(input.toString(), hybridWithoutBackend(), null)) {
                ignored.saveTo(directory.resolve("input_tagged.pdf").toString());
            }
        }).isInstanceOf(IOException.class);

        assertReleased(before);
    }

    @Test
    void extractContentsReleasesTheDocumentWhenItThrows() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-783-extract");
        Path input = writeSample(directory.resolve("input.pdf"));

        Set<String> before = veraPdfTemporaryFiles();
        assertThatThrownBy(() -> close(DocumentProcessor.extractContents(input.toString(), hybridWithoutBackend())))
            .isInstanceOf(IOException.class);

        assertReleased(before);
    }

    @Test
    void failureBeforeTheDocumentOpensKeepsTheEarlierExtraction() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-783-earlier");
        Path first = writeSample(directory.resolve("first.pdf"));
        Path notPdf = Files.write(directory.resolve("not-a-pdf.pdf"), "not a PDF".getBytes(StandardCharsets.US_ASCII));
        Config config = new Config();
        config.setOutputFolder(directory.resolve("output").toString());
        config.setGenerateJSON(true);

        ExtractionResult earlier = DocumentProcessor.extractContents(first.toString(), config);
        try {
            org.verapdf.pd.PDDocument earlierDocument = StaticResources.getDocument();
            assertThatThrownBy(() -> close(DocumentProcessor.extractContents(notPdf.toString(), config)))
                .isInstanceOf(IOException.class);

            assertThat(StaticResources.getDocument()).as("veraPDF document after the failed extraction")
                .isSameAs(earlierDocument);
            OutputWriter.writeOutputs(first.toString(), earlier, config);
        } finally {
            close(earlier);
        }
        assertThat(directory.resolve("output").resolve("first.json")).exists();
    }

    private static Config hybridWithoutBackend() {
        Config config = new Config();
        config.setHybrid("docling-fast");
        config.getHybridConfig().setUrl("http://127.0.0.1:1");
        config.getHybridConfig().setFallbackToJava(false);
        return config;
    }

    private static void assertReleased(Set<String> before) {
        assertThat(StaticResources.getDocument()).as("veraPDF document after the failure").isNull();
        assertThat(StaticContainers.getDocument()).as("layout document after the failure").isNull();
        assertThat(veraPdfTemporaryFiles()).as("veraPDF temporary files after the failure").isSubsetOf(before);
    }

    /** Closes the extraction through {@link AutoCloseable}, so the test also compiles on 2.5.13. */
    private static void close(ExtractionResult extraction) throws Exception {
        Object result = extraction;
        if (result instanceof AutoCloseable) {
            ((AutoCloseable) result).close();
        }
    }
}

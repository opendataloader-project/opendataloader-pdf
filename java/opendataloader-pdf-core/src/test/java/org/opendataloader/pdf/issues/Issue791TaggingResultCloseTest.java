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

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.AutoTagger;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OutputWriter;
import org.opendataloader.pdf.api.TaggingResult;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.verapdf.pd.PDDocument;
import org.verapdf.tools.StaticResources;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #791: {@code TaggingResult.close()} called veraPDF {@code PDDocument.close()} on the
 * calling thread. That call always clears {@code StaticResources}, which is thread-local. If a
 * later extraction on the same thread was current, it lost its document, and its JSON output
 * contained only the file name.
 *
 * <p>The sample is synthetic: two copies of a one-page PDF with one line of text. The output
 * stays in {@code target/test-samples/issue-791}.
 */
class Issue791TaggingResultCloseTest {

    private static final String TEXT = "Closing an earlier tagging result keeps this text.";

    @Test
    void closeOfAnEarlierTaggingResultKeepsTheLaterDocumentOpen() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-791");
        Path first = directory.resolve("first.pdf");
        Path second = directory.resolve("second.pdf");
        TestSamples.writeTextPdf(first, 1, TEXT);
        TestSamples.writeTextPdf(second, 1, TEXT);
        Path output = directory.resolve("output");
        Config config = new Config();
        config.setOutputFolder(output.toString());
        config.setGenerateJSON(true);

        try (ExtractionResult earlier = DocumentProcessor.extractContents(first.toString(), config)) {
            TaggingResult taggedEarlier = AutoTagger.tag(earlier, null);
            PDDocument earlierDocument = taggedEarlier.getDocument();
            try (ExtractionResult later = DocumentProcessor.extractContents(second.toString(), config)) {
                PDDocument laterDocument = StaticResources.getDocument();

                taggedEarlier.close();   // While the later extraction is current.

                assertThat(StaticResources.getDocument())
                    .as("veraPDF document after close of the earlier tagging result")
                    .isSameAs(laterDocument);
                assertThat(earlierDocument.getDocument()).as("earlier document after its close").isNull();
                OutputWriter.writeOutputs(second.toString(), later, config);
            } finally {
                taggedEarlier.close();
            }
        }

        String json = new String(Files.readAllBytes(output.resolve("second.json")), StandardCharsets.UTF_8);
        assertThat(json).as("JSON of the later extraction").contains(TEXT);
    }
}

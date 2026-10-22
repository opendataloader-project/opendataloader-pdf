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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Issue #791: {@code TaggingResult.close()} called veraPDF {@code PDDocument.close()} on the
 * calling thread. That call always clears {@code StaticResources}, which is thread-local. If a
 * later extraction on the same thread was current, it lost its document, and its JSON output
 * contained only the file name.
 *
 * <p>The sample is synthetic: two one-page PDFs, each with a different line of text. The output
 * stays in {@code target/test-samples/issue-791}.
 */
class Issue791TaggingResultCloseTest {

    private static final String FIRST_TEXT = "The earlier tagging result holds this text.";
    private static final String SECOND_TEXT = "Closing an earlier tagging result keeps this text.";
    private static final String CLOSE_FAILURE = "close failed";

    @Test
    void closeOfAnEarlierTaggingResultKeepsTheLaterDocumentOpen() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-791");
        Path first = directory.resolve("first.pdf");
        Path second = directory.resolve("second.pdf");
        TestSamples.writeTextPdf(first, 1, FIRST_TEXT);
        TestSamples.writeTextPdf(second, 1, SECOND_TEXT);
        Path output = directory.resolve("output");
        Config config = new Config();
        config.setOutputFolder(output.toString());
        config.setGenerateJSON(true);

        try (ExtractionResult earlier = DocumentProcessor.extractContents(first.toString(), config);
             TaggingResult taggedEarlier = AutoTagger.tag(earlier, null)) {
            PDDocument earlierDocument = taggedEarlier.getDocument();
            try (ExtractionResult later = DocumentProcessor.extractContents(second.toString(), config)) {
                PDDocument laterDocument = StaticResources.getDocument();

                taggedEarlier.close();   // While the later extraction is current.

                assertThat(StaticResources.getDocument())
                    .as("veraPDF document after close of the earlier tagging result")
                    .isSameAs(laterDocument);
                assertThat(earlierDocument.getDocument()).as("earlier document after its close").isNull();
                OutputWriter.writeOutputs(second.toString(), later, config);
            }
        }

        String json = new String(Files.readAllBytes(output.resolve("second.json")), StandardCharsets.UTF_8);
        assertThat(json).as("JSON of the later extraction").contains(SECOND_TEXT).doesNotContain(FIRST_TEXT);
    }

    @Test
    void closeDocumentOfNullDoesNothing() throws Exception {
        assertThat(StaticResources.getDocument()).as("no current document before the first call").isNull();
        assertThatCode(() -> DocumentProcessor.closeDocument(null))
            .as("closeDocument(null) without a current document")
            .doesNotThrowAnyException();
        Path pdf = TestSamples.emptyDirectory("issue-791-null").resolve("b.pdf");
        TestSamples.writeTextPdf(pdf, 1, SECOND_TEXT);
        try (ExtractionResult later = DocumentProcessor.extractContents(pdf.toString(), new Config())) {
            PDDocument current = StaticResources.getDocument();
            assertThatCode(() -> DocumentProcessor.closeDocument(null))
                .as("closeDocument(null) with a current document")
                .doesNotThrowAnyException();
            assertThat(StaticResources.getDocument()).as("current document after the call").isSameAs(current);
            assertThat(current.getDocument()).as("current document is still open").isNotNull();
        }
    }

    @Test
    void failureOfTheCloseReachesTheCallerOrTheLog() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-791-failure");
        Path first = directory.resolve("first.pdf");
        Path second = directory.resolve("second.pdf");
        TestSamples.writeTextPdf(first, 1, FIRST_TEXT);
        TestSamples.writeTextPdf(second, 1, SECOND_TEXT);
        List<LogRecord> records = new CopyOnWriteArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger root = Logger.getLogger("");
        boolean visible = false;
        try (ExtractionResult later = DocumentProcessor.extractContents(second.toString(), new Config())) {
            FailingDocument failing = new FailingDocument(first.toString());
            root.addHandler(handler);
            try {
                new TaggingResult(failing, 0, 0, null).close();
            } catch (RuntimeException e) {
                visible = hasCloseFailure(e);
            } finally {
                root.removeHandler(handler);
                failing.realClose();
            }
        }
        visible = visible || records.stream().anyMatch(r -> hasCloseFailure(r.getThrown()));
        assertThat(visible).as("the failure reaches the caller, or a log record carries it").isTrue();
    }

    private static boolean hasCloseFailure(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (CLOSE_FAILURE.equals(c.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static final class FailingDocument extends PDDocument {
        private volatile boolean fail = true;

        FailingDocument(String path) throws IOException {
            super(path);
        }

        @Override
        public void close() {
            if (fail) {
                throw new IllegalStateException(CLOSE_FAILURE);
            }
            super.close();
        }

        void realClose() throws InterruptedException {
            fail = false;
            Thread closer = new Thread(this::close);   // Not on the test thread: close() clears its StaticResources.
            closer.start();
            closer.join();
        }
    }
}

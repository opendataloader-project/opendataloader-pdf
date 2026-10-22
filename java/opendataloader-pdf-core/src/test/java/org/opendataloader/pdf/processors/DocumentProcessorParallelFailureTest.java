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

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A runtime failure during the page processing of the local path is reported as an
 * {@link IOException} with the original failure as its cause.
 */
class DocumentProcessorParallelFailureTest {

    @TempDir
    Path tempDir;

    @Test
    void runtimeFailureDuringPageProcessingIsWrappedInIOException() throws Exception {
        Path pdf = tempDir.resolve("one-page.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Hello");
                stream.endText();
            }
            document.save(pdf.toFile());
        }
        IllegalStateException failure = new IllegalStateException("failure inside page processing");
        Config config = new Config() {
            @Override
            public boolean isClusterTableMethod() {
                throw failure;
            }
        };
        config.setOutputFolder(tempDir.toString());

        IOException thrown = assertThrows(IOException.class,
            () -> DocumentProcessor.extractContents(pdf.toString(), config));

        assertEquals("Parallel page processing failed", thrown.getMessage());
        assertSame(failure, thrown.getCause());
    }
}

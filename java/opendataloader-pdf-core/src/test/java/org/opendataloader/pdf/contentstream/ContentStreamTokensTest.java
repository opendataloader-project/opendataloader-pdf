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
package org.opendataloader.pdf.contentstream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.autotagging.ChunksWriter;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.cos.COSBase;
import org.verapdf.cos.COSObjType;
import org.verapdf.operator.Operator;
import org.verapdf.pd.PDContentStream;
import org.verapdf.tools.StaticResources;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ContentStreamTokensTest {

    @TempDir
    Path tempDir;

    @Test
    void readsTheOperandsAndOperatorsOfAPageInStreamOrder() throws Exception {
        Path pdf = tempDir.resolve("rect.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(10, 20, 30, 40);
                content.fill();
            }
            document.save(pdf.toFile());
        }
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        PDContentStream page = StaticResources.getDocument().getPages().get(0).getContent();

        List<Object> tokens = ContentStreamTokens.getTokens(page);

        List<String> operators = tokens.stream().filter(Operator.class::isInstance)
            .map(token -> ((Operator) token).getOperator()).collect(Collectors.toList());
        assertThat(operators).containsExactly("re", "f");
        assertThat(tokens).hasSize(6);
        assertThat(tokens.indexOf(tokens.stream().filter(Operator.class::isInstance).findFirst().get())).isEqualTo(4);
    }

    @Test
    void readsAContentsArrayAsOneStreamInItsOrder() throws Exception {
        Path pdf = tempDir.resolve("two-streams.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.addRect(10, 20, 30, 40);
                content.fill();
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page,
                PDPageContentStream.AppendMode.APPEND, false)) {
                content.moveTo(0, 0);
                content.lineTo(100, 100);
                content.stroke();
            }
            document.save(pdf.toFile());
        }
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        PDContentStream page = StaticResources.getDocument().getPages().get(0).getContent();
        assertThat(page.getContents().getType()).isEqualTo(COSObjType.COS_ARRAY);

        List<Object> tokens = ContentStreamTokens.getTokens(page);

        List<String> operators = tokens.stream().filter(Operator.class::isInstance)
            .map(token -> ((Operator) token).getOperator()).collect(Collectors.toList());
        assertThat(operators).containsExactly("re", "f", "m", "l", "S");
        assertThat(tokens).hasSize(13);
        assertThat(tokens.subList(0, 4)).allMatch(COSBase.class::isInstance);
        assertThat(tokens.subList(0, 4).stream().map(token -> ((COSBase) token).getReal()))
            .containsExactly(10.0, 20.0, 30.0, 40.0);
    }

    @Test
    @SuppressWarnings("deprecation")
    void theOldChunksWriterMethodReadsTheSameTokens() throws Exception {
        Path pdf = tempDir.resolve("line.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.moveTo(0, 0);
                content.lineTo(100, 100);
                content.stroke();
            }
            document.save(pdf.toFile());
        }
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        PDContentStream page = StaticResources.getDocument().getPages().get(0).getContent();

        assertThat(ChunksWriter.getTokens(page).toString()).isEqualTo(ContentStreamTokens.getTokens(page).toString());
    }

    @Test
    void noStreamHasNoTokens() {
        assertThat(ContentStreamTokens.getTokens(null)).isEmpty();
    }
}

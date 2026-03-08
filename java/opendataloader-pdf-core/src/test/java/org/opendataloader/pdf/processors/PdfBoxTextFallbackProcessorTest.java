/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.processors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfBoxTextFallbackProcessorTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        StaticLayoutContainers.clearContainers();
    }

    @Test
    void splitPageTextIntoParagraphsKeepsStructuredLinesSeparate() {
        String pageText = String.join("\n",
            "TABLE OF CONTENTS",
            "INTRODUCTION............................1",
            "THE ASANAS..............................39",
            "EPILOGUE................................102");

        List<String> paragraphs = PdfBoxTextFallbackProcessor.splitPageTextIntoParagraphs(pageText);

        assertTrue(paragraphs.size() >= 4);
        assertTrue(paragraphs.contains("INTRODUCTION............................1"));
    }

    @Test
    void recoverBlockTextsForTestingSplitsLeadingHeadingFromBody() {
        List<String> blocks = PdfBoxTextFallbackProcessor.recoverBlockTextsForTesting(
            "FOREWORD by B K S Iyengar The Hatha yoga pradipika of Svatmarama is one of the most important yoga texts.");

        assertEquals(List.of(
            "H:FOREWORD by B K S Iyengar",
            "P:The Hatha yoga pradipika of Svatmarama is one of the most important yoga texts."
        ), blocks);
    }

    @Test
    void backfillIfDocumentEmptyRecoversTextFromPdfBox() throws Exception {
        Path pdfPath = tempDir.resolve("fallback.pdf");
        createSimplePdf(pdfPath);

        StaticLayoutContainers.clearContainers();

        List<List<IObject>> contents = new ArrayList<>();
        contents.add(new ArrayList<>());

        PdfBoxTextFallbackProcessor.backfillIfDocumentEmpty(pdfPath.toString(), contents, null);

        assertFalse(contents.get(0).isEmpty());
        assertTrue(contents.get(0).get(0) instanceof SemanticParagraph);

        String recoveredText = ((SemanticParagraph) contents.get(0).get(0)).getValue();
        assertTrue(recoveredText.contains("First paragraph line one"));
        assertTrue(recoveredText.contains("Second paragraph."));
    }

    @Test
    void backfillIfDocumentEmptyRecoversLeadingHeadingBlocks() throws Exception {
        Path pdfPath = tempDir.resolve("heading-fallback.pdf");
        createHeadingPdf(pdfPath);

        StaticLayoutContainers.clearContainers();

        List<List<IObject>> contents = new ArrayList<>();
        contents.add(new ArrayList<>());

        PdfBoxTextFallbackProcessor.backfillIfDocumentEmpty(pdfPath.toString(), contents, null);

        assertFalse(contents.get(0).isEmpty());
        assertTrue(contents.get(0).get(0) instanceof SemanticHeading);
        assertTrue(contents.get(0).get(1) instanceof SemanticParagraph);
        assertTrue(((SemanticHeading) contents.get(0).get(0)).getValue().contains("INTRODUCTION"));
    }

    private static void createSimplePdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("First paragraph line one");
                stream.newLineAtOffset(0, -16);
                stream.showText("continues here.");
                stream.newLineAtOffset(0, -32);
                stream.showText("Second paragraph.");
                stream.endText();
            }

            document.save(pdfPath.toFile());
        }
    }

    private static void createHeadingPdf(Path pdfPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("INTRODUCTION");
                stream.newLineAtOffset(0, -16);
                stream.showText("This is the opening paragraph.");
                stream.endText();
            }

            document.save(pdfPath.toFile());
        }
    }
}

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
package org.opendataloader.pdf.autotagging;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.cos.COSKey;
import org.verapdf.tools.StaticResources;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A form drawn from a form that is drawn twice is drawn twice; drawn on two pages is drawn twice. */
class FormUsesTest {

    @TempDir
    Path dir;

    @Test
    void usesFollowTheFormsThatDrawThem() throws Exception {
        Path pdf = dir.resolve("nested.pdf");
        try (PDDocument document = new PDDocument()) {
            PDAppearanceStream inner = new PDAppearanceStream(document);
            inner.setBBox(new PDRectangle(0, 0, 10, 10));
            try (PDPageContentStream content = new PDPageContentStream(document, inner)) {
                content.addRect(0, 0, 10, 10);
                content.fill();
            }
            PDAppearanceStream outer = new PDAppearanceStream(document);
            outer.setBBox(new PDRectangle(0, 0, 10, 10));
            outer.setResources(new org.apache.pdfbox.pdmodel.PDResources());
            try (PDPageContentStream content = new PDPageContentStream(document, outer)) {
                content.drawForm(inner);
            }
            PDAppearanceStream single = new PDAppearanceStream(document);
            single.setBBox(new PDRectangle(0, 0, 10, 10));
            try (PDPageContentStream content = new PDPageContentStream(document, single)) {
                content.addRect(0, 0, 5, 5);
                content.fill();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.drawForm(outer);
                content.drawForm(outer);
                content.drawForm(single);
            }
            document.save(pdf.toFile());
        }
        StaticLayoutContainers.clearContainers();
        DocumentProcessor.preprocessing(pdf.toString(), new Config());

        Map<COSKey, Integer> uses = FormUses.of(StaticResources.getDocument().getPages());

        List<Integer> counts = new ArrayList<>(uses.values());
        Collections.sort(counts);
        assertEquals(List.of(1, 2, 2), counts, "the form drawn once, the outer form twice, the inner form once per outer draw");
    }
}

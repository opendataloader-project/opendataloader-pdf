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

import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSKey;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A form drawn from a form that is drawn twice is drawn twice; drawn on two pages is drawn twice. */
class FormUsesTest {

    @TempDir
    Path dir;

    private static PDFormXObject form(PDDocument document, String content, PDResources resources) throws IOException {
        PDFormXObject form = new PDFormXObject(document);
        form.setBBox(new PDRectangle(0, 0, 10, 10));
        if (resources != null) {
            form.setResources(resources);
        }
        try (OutputStream out = form.getContentStream().createOutputStream()) {
            out.write(content.getBytes(StandardCharsets.ISO_8859_1));
        }
        return form;
    }

    private static PDPage page(PDDocument document, PDResources resources, String content) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        page.setResources(resources);
        document.addPage(page);
        try (org.apache.pdfbox.pdmodel.PDPageContentStream stream = new org.apache.pdfbox.pdmodel.PDPageContentStream(
            document, page, org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode.OVERWRITE, false)) {
            stream.appendRawCommands(content);
        }
        return page;
    }

    private static PDResources resources(Object... namesAndForms) {
        PDResources resources = new PDResources();
        for (int i = 0; i < namesAndForms.length; i += 2) {
            resources.put(COSName.getPDFName((String) namesAndForms[i]), (PDFormXObject) namesAndForms[i + 1]);
        }
        return resources;
    }

    private Path save(PDDocument document) throws IOException {
        Path pdf = dir.resolve("forms.pdf");
        document.save(pdf.toFile());
        return pdf;
    }

    /** The forms are scanned from the saved file as veraPDF reads it, without extraction. */
    private static org.verapdf.pd.PDDocument opened;

    private static FormUses open(Path pdf) throws Exception {
        opened = new org.verapdf.pd.PDDocument(pdf.toString());
        return FormUses.of(opened.getPages());
    }

    /** The object key of the form a page of the opened document names. */
    private static COSKey keyOf(int page, String name) {
        return opened.getPages().get(page).getResources()
            .getXObject(ASAtom.getASAtom(name)).getObject().getKey();
    }

    @Test
    void usesFollowTheFormsThatDrawThem() throws Exception {
        Path pdf;
        try (PDDocument document = new PDDocument()) {
            PDFormXObject inner = form(document, "0 0 10 10 re f", null);
            PDFormXObject outer = form(document, "/In Do", resources("In", inner));
            PDFormXObject single = form(document, "0 0 5 5 re f", null);
            page(document, resources("Out", outer, "In", inner, "One", single), "/Out Do /Out Do /One Do");
            pdf = save(document);
        }

        FormUses uses = open(pdf);

        assertEquals(2, uses.usesOf(keyOf(0, "Out")), "the outer form is drawn twice");
        assertEquals(2, uses.usesOf(keyOf(0, "In")), "the inner form is drawn once per outer draw");
        assertEquals(1, uses.usesOf(keyOf(0, "One")), "the form drawn once");
    }

    /**
     * Layers of 3 forms that each draw the whole next layer: 3^14 paths to the last layer. The
     * counts must come from one pass over the forms, not one visit per path, and must reach the
     * bottom: the scan used to stop 8 levels down, and a form below it counted 0.
     */
    @Test
    @Timeout(30)
    void layersOfFormsAreCountedWithoutVisitingEveryPath() throws Exception {
        int layers = 14;
        int width = 3;
        Path pdf;
        try (PDDocument document = new PDDocument()) {
            PDFormXObject[][] forms = new PDFormXObject[layers][width];
            for (int layer = layers - 1; layer >= 0; layer--) {
                for (int i = 0; i < width; i++) {
                    StringBuilder content = new StringBuilder("0 0 5 5 re f ");
                    List<Object> drawn = new ArrayList<>();
                    if (layer + 1 < layers) {
                        for (int j = 0; j < width; j++) {
                            content.append("/L").append(j).append(" Do ");
                            drawn.add("L" + j);
                            drawn.add(forms[layer + 1][j]);
                        }
                    }
                    forms[layer][i] = form(document, content.toString(), resources(drawn.toArray()));
                }
            }
            List<Object> top = new ArrayList<>();
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < width; i++) {
                content.append("/T").append(i).append(" Do ");
                top.add("T" + i);
                top.add(forms[0][i]);
            }
            top.add("Bottom");
            top.add(forms[layers - 1][0]);
            content.append("/Bottom Do");
            page(document, resources(top.toArray()), content.toString());
            pdf = save(document);
        }

        FormUses uses = open(pdf);

        assertEquals(1000, uses.usesOf(keyOf(0, "Bottom")), "capped, and not 0 as below the old depth cutoff");
        assertEquals(1, uses.usesOf(keyOf(0, "T0")), "a form of the top layer is drawn by the page once");
    }

    /** A chain deeper than the scan goes: nothing is known of what is below, so nothing is single. */
    @Test
    void aFormBelowTheDepthTheScanGoesIsNotTakenForSingle() throws Exception {
        int depth = FormUses.MAX_DEPTH + 6;
        Path pdf;
        try (PDDocument document = new PDDocument()) {
            PDFormXObject last = form(document, "0 0 5 5 re f", null);
            PDFormXObject next = last;
            for (int i = 0; i < depth; i++) {
                next = form(document, "/Next Do", resources("Next", next));
            }
            page(document, resources("Chain", next, "Last", last), "/Chain Do /Last Do");
            pdf = save(document);
        }

        FormUses uses = open(pdf);

        assertTrue(uses.usesOf(keyOf(0, "Last")) > 1, "drawn through the chain and directly");
    }

    /**
     * A form with no /Resources finds what it draws in the resources of the page: drawn from two
     * pages, it draws a different form from each, and each of those is drawn once.
     */
    @Test
    void aFormWithoutResourcesIsCountedForEachCaller() throws Exception {
        Path pdf;
        try (PDDocument document = new PDDocument()) {
            PDFormXObject a = form(document, "0 0 5 5 re f", null);
            PDFormXObject b = form(document, "0 0 6 6 re f", null);
            PDFormXObject open = form(document, "/Child Do", null);
            page(document, resources("Open", open, "Child", a), "/Open Do");
            page(document, resources("Open", open, "Child", b), "/Open Do");
            pdf = save(document);
        }

        FormUses uses = open(pdf);

        assertEquals(2, uses.usesOf(keyOf(0, "Open")));
        assertEquals(1, uses.usesOf(keyOf(0, "Child")), "the first page's child, drawn once");
        assertEquals(1, uses.usesOf(keyOf(1, "Child")), "the second page's child, drawn once");
    }

    @Test
    @Timeout(30)
    void aFormThatDrawsItselfIsCountedAndTheScanEnds() throws Exception {
        Path pdf;
        try (PDDocument document = new PDDocument()) {
            PDFormXObject loop = form(document, "0 0 5 5 re f /Self Do", resources("Self", form(document, "", null)));
            page(document, resources("Loop", loop), "/Loop Do");
            pdf = save(document);
        }
        // A cycle cannot be written from objects that have no number yet: point the form at itself
        // in the saved file.
        Path cyclic = dir.resolve("cyclic.pdf");
        try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf.toFile())) {
            org.apache.pdfbox.cos.COSDictionary xObjects = (org.apache.pdfbox.cos.COSDictionary) document.getPage(0)
                .getResources().getCOSObject().getDictionaryObject(COSName.XOBJECT);
            org.apache.pdfbox.cos.COSStream loop = (org.apache.pdfbox.cos.COSStream) xObjects.getDictionaryObject("Loop");
            org.apache.pdfbox.cos.COSDictionary own = (org.apache.pdfbox.cos.COSDictionary) ((org.apache.pdfbox.cos.COSDictionary)
                loop.getDictionaryObject(COSName.RESOURCES)).getDictionaryObject(COSName.XOBJECT);
            own.setItem("Self", xObjects.getItem("Loop"));
            document.save(cyclic.toFile());
        }

        FormUses uses = open(cyclic);

        assertEquals(1, uses.usesOf(keyOf(0, "Loop")));
    }
}

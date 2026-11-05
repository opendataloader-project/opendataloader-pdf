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
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.contentstream.ContentStreamTokens;
import org.opendataloader.pdf.entities.DrawnTextChunk;
import org.verapdf.as.ASAtom;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Forms that draw forms, and names that mean different forms in different resource dictionaries.
 * The forms are written with the names the cases need; a piece of content points at the paint
 * operator of one of them.
 */
class NestedFormTaggingTest {

    @TempDir
    Path dir;

    private static final String SOURCE_TAGGED = "/Figure <</MCID 0>> BDC 0 0 10 10 re f EMC";

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

    private static PDResources resources(Object... namesAndForms) {
        PDResources resources = new PDResources();
        for (int i = 0; i < namesAndForms.length; i += 2) {
            resources.put(COSName.getPDFName((String) namesAndForms[i]), (PDFormXObject) namesAndForms[i + 1]);
        }
        return resources;
    }

    private static void page(PDDocument document, PDResources resources, String content) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        page.setResources(resources);
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page, PDPageContentStream.AppendMode.OVERWRITE, false)) {
            stream.appendRawCommands(content);
        }
    }

    private void open(PDDocument document) throws Exception {
        Path pdf = dir.resolve("nested.pdf");
        document.save(pdf.toFile());
        StaticLayoutContainers.clearContainers();
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
    }

    /** Index of the last paint operator of the form the first page names, as veraPDF reads it. */
    private static int paintIndex(String name) {
        org.verapdf.pd.images.PDXForm form = (org.verapdf.pd.images.PDXForm) StaticResources.getDocument().getPages().get(0)
            .getResources().getXObject(ASAtom.getASAtom(name));
        if (form == null) {
            form = (org.verapdf.pd.images.PDXForm) org.opendataloader.pdf.processors.AutoTaggingProcessor.findXObject(
                StaticResources.getDocument().getPages().get(0).getResources(), ASAtom.getASAtom(name), 0);
        }
        List<Object> tokens = ContentStreamTokens.getTokens(form);
        int paint = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof org.verapdf.operator.Operator
                && "f".equals(((org.verapdf.operator.Operator) tokens.get(i)).getOperator())) {
                paint = i;
            }
        }
        assertTrue(paint >= 0, "form " + name + " paints");
        return paint;
    }

    private static List<IObject> paragraphOn(String formName, int operatorIndex) {
        DrawnTextChunk chunk = new DrawnTextChunk(new BoundingBox(0, 100, 500, 112, 512), "\u25BC");
        chunk.getStreamInfos().add(new StreamInfo(operatorIndex, formName, 0, 0));
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(chunk));
        paragraph.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return List.of(paragraph);
    }

    private Path tag(List<List<IObject>> contents) throws IOException {
        AutoTaggingProcessor.tagDocument(StaticResources.getDocument(), contents, 1.7f);
        Path out = dir.resolve("nested_tagged.pdf");
        StaticResources.getDocument().saveAs(out.toString());
        return out;
    }

    private static COSStream formNamed(PDDocument document, String outer, String name) {
        COSDictionary xObjects = (COSDictionary) document.getPage(0).getResources().getCOSObject()
            .getDictionaryObject(COSName.XOBJECT);
        COSStream form = (COSStream) xObjects.getDictionaryObject(outer);
        if (name == null) {
            return form;
        }
        COSDictionary inner = (COSDictionary) ((COSDictionary) form.getDictionaryObject(COSName.RESOURCES))
            .getDictionaryObject(COSName.XOBJECT);
        return (COSStream) inner.getDictionaryObject(name);
    }

    private static List<Object> tokensOf(COSStream form) throws IOException {
        return new PDFStreamParser(new PDFormXObject(form)).parse();
    }

    private static boolean carriesMcid(List<Object> tokens) {
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator && "BDC".equals(((Operator) tokens.get(i)).getName())
                && tokens.get(i - 1) instanceof COSDictionary && ((COSDictionary) tokens.get(i - 1)).containsKey(COSName.MCID)) {
                return true;
            }
        }
        return false;
    }

    private static boolean paintsOnlyInArtifact(List<Object> tokens) {
        List<String> open = new ArrayList<>();
        int paints = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof Operator)) {
                continue;
            }
            String name = ((Operator) tokens.get(i)).getName();
            if ("BMC".equals(name)) {
                open.add(((COSName) tokens.get(i - 1)).getName());
            } else if ("BDC".equals(name)) {
                open.add(((COSName) tokens.get(i - 2)).getName());
            } else if ("EMC".equals(name)) {
                open.remove(open.size() - 1);
            } else if ("f".equals(name)) {
                paints++;
                if (!open.equals(List.of("Artifact"))) {
                    return false;
                }
            }
        }
        return paints > 0;
    }

    private static List<Integer> parentTreeKeys(PDDocument document) {
        COSDictionary root = (COSDictionary) document.getDocumentCatalog().getCOSObject()
            .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
        COSArray nums = (COSArray) ((COSDictionary) root.getDictionaryObject(COSName.getPDFName("ParentTree")))
            .getDictionaryObject(COSName.getPDFName("Nums"));
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < nums.size(); i += 2) {
            keys.add(((org.apache.pdfbox.cos.COSInteger) nums.getObject(i)).intValue());
        }
        return keys;
    }

    /**
     * {@code /O} draws {@code /S}, and the page draws {@code /O} twice, so {@code /S} is drawn twice.
     * {@code /O} holds nothing of its own, and the source is tagged: {@code /S} has an MCID and a
     * {@code /StructParents}. Once {@code /S} is given up, {@code /O} still has to be entered, or
     * {@code /S} keeps both and fails PDF/UA-1 7.20 test 2.
     */
    @Test
    void anOuterFormThatOnlyDrawsASharedFormIsEntered() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDFormXObject shared = form(document, SOURCE_TAGGED, null);
            shared.getCOSObject().setInt(COSName.getPDFName("StructParents"), 5);
            PDFormXObject outer = form(document, "/S Do", resources("S", shared));
            page(document, resources("O", outer), "/O Do /O Do");
            open(document);
        }
        Path out = tag(List.of(paragraphOn("S", paintIndex("S"))));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            COSStream shared = formNamed(document, "O", "S");
            assertNull(shared.getItem(COSName.getPDFName("StructParents")), "7.20 test 2");
            List<Object> tokens = tokensOf(shared);
            assertTrue(!carriesMcid(tokens), "the source's MCID is gone");
            assertTrue(paintsOnlyInArtifact(tokens), "its paint is an artifact: " + tokens);
        }
    }

    /** A form entered only for the form it draws makes no parent-tree entry for itself. */
    @Test
    void anOuterFormKeepsNoStructParentsOfItsOwn() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDFormXObject inner = form(document, "0 0 10 10 re f", null);
            PDFormXObject outer = form(document, "/S Do", resources("S", inner));
            outer.getCOSObject().setInt(COSName.getPDFName("StructParents"), 77);
            page(document, resources("O", outer), "/O Do");
            open(document);
        }
        Path out = tag(List.of(paragraphOn("S", paintIndex("S"))));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertNull(formNamed(document, "O", null).getItem(COSName.getPDFName("StructParents")));
            assertNotNull(formNamed(document, "O", "S").getItem(COSName.getPDFName("StructParents")), "the form with the piece keeps its own");
        }
    }

    /**
     * The page's {@code /Fm0} holds a piece. {@code /O}, drawn after it, has an {@code /Fm0} of its
     * own that is another form with a paint at another index. The key (page, "Fm0") is the first
     * one's: reading it for the second form took the first one's parent-tree entry away.
     */
    @Test
    void aNameThatMeansAnotherFormDoesNotTakeTheOwnersKey() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDFormXObject owner = form(document, "0 0 10 10 re f", null);
            PDFormXObject other = form(document, "0 0 1 1 re 0 0 2 2 re f", null);
            PDFormXObject outer = form(document, "/Fm0 Do", resources("Fm0", other));
            page(document, resources("Fm0", owner, "O", outer), "/Fm0 Do /O Do");
            open(document);
        }
        Path out = tag(List.of(paragraphOn("Fm0", paintIndex("Fm0"))));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            COSStream owner = formNamed(document, "Fm0", null);
            COSStream other = formNamed(document, "O", "Fm0");
            assertTrue(carriesMcid(tokensOf(owner)), "the owner keeps its piece");
            COSBase ownerKey = owner.getItem(COSName.getPDFName("StructParents"));
            assertNotNull(ownerKey);
            assertTrue(parentTreeKeys(document).contains(((org.apache.pdfbox.cos.COSInteger) ownerKey).intValue()),
                "and its parent-tree entry");
            assertNull(other.getItem(COSName.getPDFName("StructParents")), "the other form has none");
            assertTrue(!carriesMcid(tokensOf(other)));
        }
    }
}

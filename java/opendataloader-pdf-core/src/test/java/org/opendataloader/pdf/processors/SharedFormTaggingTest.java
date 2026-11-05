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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.contentstream.ContentStreamTokens;
import org.opendataloader.pdf.entities.DrawnTextChunk;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A form that the page draws more than once.
 *
 * <p>A budget's list bullet is a triangle form, {@code /Fm0}, drawn 3 times on a page, each draw
 * in its own Figure in the source. The form got {@code /StructParents} and its stream was
 * rewritten at each draw, the second and third time from the stream already rewritten, whose
 * operator indexes had moved: the MCIDs were dropped, {@code /StructParents} stayed, and the
 * structure kept MCRs and a parent-tree entry for nothing. PDF/UA-1 7.20 test 2 (veraPDF
 * {@code isUniqueSemanticParent}: the form has {@code /StructParents} and is referenced more
 * than once) failed on the 2nd and 3rd draw.
 */
class SharedFormTaggingTest {

    @TempDir
    Path dir;

    private String formName;
    private int fillIndex;

    /** {@code pages} pages that each draw the one triangle form {@code draws} times. */
    private void openPages(int pages, int draws) throws Exception {
        Path pdf = dir.resolve("triangles.pdf");
        try (PDDocument document = new PDDocument()) {
            PDAppearanceStream form = new PDAppearanceStream(document);
            form.setBBox(new PDRectangle(0, 0, 12, 12));
            try (PDPageContentStream content = new PDPageContentStream(document, form)) {
                content.setNonStrokingColor(0f, 0f, 0f);
                content.moveTo(0, 0);
                content.lineTo(12, 0);
                content.lineTo(6, 12);
                content.closePath();
                content.fill();
            }
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    for (int i = 0; i < draws; i++) {
                        COSDictionary properties = new COSDictionary();
                        properties.setInt(COSName.MCID, i);
                        content.beginMarkedContent(COSName.getPDFName("Figure"), PDPropertyList.create(properties));
                        content.saveGraphicsState();
                        content.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(100 + 30 * i, 500));
                        content.drawForm(form);
                        content.restoreGraphicsState();
                        content.endMarkedContent();
                    }
                }
            }
            document.save(pdf.toFile());
        }
        StaticLayoutContainers.clearContainers();
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        org.verapdf.pd.PDPage first = StaticResources.getDocument().getPages().get(0);
        formName = first.getResources().getXObjectNames().iterator().next().getValue();
        List<Object> tokens = ContentStreamTokens.getTokens(
            (org.verapdf.pd.images.PDXForm) first.getResources().getXObject(org.verapdf.as.ASAtom.getASAtom(formName)));
        fillIndex = -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof org.verapdf.operator.Operator
                && "f".equals(((org.verapdf.operator.Operator) tokens.get(i)).getOperator())) {
                fillIndex = i;
            }
        }
        assertTrue(fillIndex >= 0);
    }

    /** A paragraph whose only content is the form's fill, as OCR of a drawn glyph gives it. */
    private List<IObject> paragraphOnForm(int pageNumber, int operatorIndex) {
        DrawnTextChunk chunk = new DrawnTextChunk(new BoundingBox(pageNumber, 100, 500, 112, 512), "▼");
        chunk.getStreamInfos().add(new StreamInfo(operatorIndex, formName, 0, 0));
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(chunk));
        paragraph.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return List.of(paragraph);
    }

    /** Real text, not outlines read by OCR: it has no /ActualText to keep an element alive. */
    private List<IObject> textParagraphOnForm(int pageNumber, int operatorIndex) {
        TextChunk chunk = new TextChunk(new BoundingBox(pageNumber, 100, 500, 112, 512), "Notice", 12.0, 12.0);
        chunk.adjustSymbolEndsToBoundingBox(null);
        chunk.getStreamInfos().add(new StreamInfo(operatorIndex, formName, 0, 6));
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(chunk));
        paragraph.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return List.of(paragraph);
    }

    private static int elementsOfType(COSBase node, String type) {
        if (node instanceof org.apache.pdfbox.cos.COSObject) {
            node = ((org.apache.pdfbox.cos.COSObject) node).getObject();
        }
        int count = 0;
        if (node instanceof COSArray) {
            for (COSBase kid : (COSArray) node) {
                count += elementsOfType(kid, type);
            }
        } else if (node instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) node;
            if (type.equals(dictionary.getNameAsString(COSName.S))) {
                count++;
            }
            count += elementsOfType(dictionary.getDictionaryObject(COSName.K), type);
        }
        return count;
    }

    private Path tag(List<List<IObject>> contents) throws IOException {
        AutoTaggingProcessor.tagDocument(StaticResources.getDocument(), contents, 1.7f);
        Path out = dir.resolve("triangles_tagged.pdf");
        StaticResources.getDocument().saveAs(out.toString());
        return out;
    }

    private static List<Object> tokensOf(org.apache.pdfbox.contentstream.PDContentStream stream) throws IOException {
        return new PDFStreamParser(stream).parse();
    }

    private static COSStream formOf(PDDocument document, int page) {
        COSDictionary xObjects = (COSDictionary) document.getPage(page).getResources().getCOSObject()
            .getDictionaryObject(COSName.XOBJECT);
        return (COSStream) xObjects.getDictionaryObject(xObjects.keySet().iterator().next());
    }

    /** MCIDs that the marked content of these tokens carries. */
    private static List<Integer> mcidsIn(List<Object> tokens) {
        List<Integer> mcids = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator && "BDC".equals(((Operator) tokens.get(i)).getName())
                && tokens.get(i - 1) instanceof COSDictionary) {
                mcids.add(((COSDictionary) tokens.get(i - 1)).getInt(COSName.MCID));
            }
        }
        return mcids;
    }

    /** Names of the marked-content sequences open at each {@code Do}, innermost last. */
    private static List<String> sequencesAtDo(List<Object> tokens) {
        List<String> open = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof Operator)) {
                continue;
            }
            String name = ((Operator) tokens.get(i)).getName();
            if ("BMC".equals(name) || "BDC".equals(name)) {
                open.add(((COSName) tokens.get(i - (("BDC".equals(name)) ? 2 : 1))).getName());
            } else if ("EMC".equals(name)) {
                open.remove(open.size() - 1);
            } else if ("Do".equals(name)) {
                found.add(String.join("/", open));
            }
        }
        return found;
    }

    private static int mcrsInto(COSBase node, COSStream form) {
        if (node instanceof org.apache.pdfbox.cos.COSObject) {
            node = ((org.apache.pdfbox.cos.COSObject) node).getObject();
        }
        int count = 0;
        if (node instanceof COSArray) {
            for (COSBase kid : (COSArray) node) {
                count += mcrsInto(kid, form);
            }
        } else if (node instanceof COSDictionary) {
            COSDictionary dictionary = (COSDictionary) node;
            if (COSName.getPDFName("MCR").equals(dictionary.getCOSName(COSName.TYPE))
                && dictionary.getDictionaryObject(COSName.getPDFName("Stm")) == form) {
                return 1;
            }
            count += mcrsInto(dictionary.getDictionaryObject(COSName.K), form);
        }
        return count;
    }

    private static COSBase structTreeRoot(PDDocument document) {
        return document.getDocumentCatalog().getCOSObject().getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
    }

    /** Keys of the parent tree, from its flat /Nums. */
    private static List<Integer> parentTreeKeys(PDDocument document) {
        COSDictionary tree = (COSDictionary) ((COSDictionary) structTreeRoot(document))
            .getDictionaryObject(COSName.getPDFName("ParentTree"));
        COSArray nums = (COSArray) tree.getDictionaryObject(COSName.getPDFName("Nums"));
        List<Integer> keys = new ArrayList<>();
        for (int i = 0; i < nums.size(); i += 2) {
            keys.add(((org.apache.pdfbox.cos.COSInteger) nums.getObject(i)).intValue());
        }
        return keys;
    }

    @Test
    void aFormDrawnThreeTimesCarriesNoStructureAndIsArtifactEverywhere() throws Exception {
        openPages(1, 3);
        Path out = tag(List.of(paragraphOnForm(0, fillIndex)));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            COSStream form = formOf(document, 0);
            assertNull(form.getItem(COSName.getPDFName("StructParents")), "7.20 test 2: no /StructParents on a shared form");
            List<Object> formTokens = tokensOf(new PDFormXObject(form));
            assertEquals(List.of(), mcidsIn(formTokens), "no MCID in the form");
            assertTrue(sequencesAtDo(tokensOf(document.getPage(0))).stream().allMatch("Artifact"::equals),
                "every draw is an artifact: " + sequencesAtDo(tokensOf(document.getPage(0))));
            assertEquals(3, sequencesAtDo(tokensOf(document.getPage(0))).size());
            assertEquals(0, mcrsInto(structTreeRoot(document), form), "no MCR into the form");
            assertEquals(List.of(), parentTreeKeys(document), "no parent-tree entry for the form");
            // The form's own paint is wrapped, so the page stays clean for 7.1 test 3.
            long fillsInArtifact = 0;
            List<String> open = new ArrayList<>();
            for (int i = 0; i < formTokens.size(); i++) {
                if (!(formTokens.get(i) instanceof Operator)) {
                    continue;
                }
                String name = ((Operator) formTokens.get(i)).getName();
                if ("BMC".equals(name)) {
                    open.add(((COSName) formTokens.get(i - 1)).getName());
                } else if ("BDC".equals(name)) {
                    open.add(((COSName) formTokens.get(i - 2)).getName());
                } else if ("EMC".equals(name)) {
                    open.remove(open.size() - 1);
                } else if ("f".equals(name) && open.equals(List.of("Artifact"))) {
                    fillsInArtifact++;
                }
            }
            assertEquals(1, fillsInArtifact, "the fill is inside one /Artifact sequence");
        }
    }

    @Test
    void aFormDrawnOnTwoPagesIsOneObjectAndSharedToo() throws Exception {
        openPages(2, 1);
        Path out = tag(List.of(paragraphOnForm(0, fillIndex), paragraphOnForm(1, fillIndex)));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            COSStream form = formOf(document, 0);
            assertNull(form.getItem(COSName.getPDFName("StructParents")));
            assertEquals(List.of(), mcidsIn(tokensOf(new PDFormXObject(form))));
            assertEquals(0, mcrsInto(structTreeRoot(document), form));
            assertEquals(List.of(), parentTreeKeys(document));
        }
    }

    /** What a form drawn once keeps: the MCID in its stream, the MCR, the parent-tree entry. */
    @Test
    void aFormDrawnOnceStaysTagged() throws Exception {
        openPages(1, 1);
        Path out = tag(List.of(paragraphOnForm(0, fillIndex)));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            COSStream form = formOf(document, 0);
            assertNotNull(form.getItem(COSName.getPDFName("StructParents")));
            assertEquals(List.of(0), mcidsIn(tokensOf(new PDFormXObject(form))));
            assertEquals(1, mcrsInto(structTreeRoot(document), form));
            assertTrue(parentTreeKeys(document).contains(form.getInt(COSName.getPDFName("StructParents"))));
        }
    }

    /**
     * A piece that names an operator of the form the writer never reaches (here a path
     * construction operator, whose paint comes later) leaves the form with no MCID. It must not
     * keep {@code /StructParents} or an entry in the parent tree.
     */
    @Test
    void aFormWhoseContentCarriesNoMcidGetsNoStructParents() throws Exception {
        openPages(1, 1);
        Path out = tag(List.of(paragraphOnForm(0, 0)));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            COSStream form = formOf(document, 0);
            assertNull(form.getItem(COSName.getPDFName("StructParents")));
            assertEquals(List.of(), mcidsIn(tokensOf(new PDFormXObject(form))));
            assertEquals(0, mcrsInto(structTreeRoot(document), form));
            assertEquals(List.of(), parentTreeKeys(document));
            assertEquals(List.of("Artifact"), sequencesAtDo(tokensOf(document.getPage(0))));
        }
    }

    /**
     * A paragraph whose only content was in a form that is drawn more than once is left with
     * nothing: a reader is told of a paragraph that says nothing. It is taken out, as it is when
     * it is made with nothing in it.
     */
    @Test
    void aParagraphThatLosesItsOnlyContentIsRemoved() throws Exception {
        openPages(1, 3);
        Path out = tag(List.of(textParagraphOnForm(0, fillIndex)));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals(0, elementsOfType(structTreeRoot(document), "P"), "no empty paragraph");
        }
    }

    @Test
    void aParagraphOnAFormDrawnOnceKeepsItsContent() throws Exception {
        openPages(1, 1);
        Path out = tag(List.of(textParagraphOnForm(0, fillIndex)));

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals(1, elementsOfType(structTreeRoot(document), "P"));
            assertEquals(1, mcrsInto(structTreeRoot(document), formOf(document, 0)));
        }
    }

    /**
     * A page whose first element was in a shared form, and whose second is page text: a link to
     * the page must still name an element of it. The release takes the first element out after
     * the last is made, so no later element took its place, and the PDF 2.0 link lost /Dest.
     */
    @Test
    void aLinkToAPageWhoseFirstElementIsGivenUpNamesTheNextOne() throws Exception {
        Path pdf = dir.resolve("linked.pdf");
        try (PDDocument document = new PDDocument()) {
            PDAppearanceStream form = new PDAppearanceStream(document);
            form.setBBox(new PDRectangle(0, 0, 12, 12));
            try (PDPageContentStream content = new PDPageContentStream(document, form)) {
                content.moveTo(0, 0);
                content.lineTo(12, 0);
                content.lineTo(6, 12);
                content.closePath();
                content.fill();
            }
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int i = 0; i < 2; i++) {
                    content.saveGraphicsState();
                    content.transform(org.apache.pdfbox.util.Matrix.getTranslateInstance(100 + 30 * i, 500));
                    content.drawForm(form);
                    content.restoreGraphicsState();
                }
                content.beginText();
                content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(
                    org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 400);
                content.showText("Hello");
                content.endText();
            }
            org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink link =
                new org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink();
            link.setRectangle(new PDRectangle(72, 300, 100, 20));
            org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination dest =
                new org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination();
            dest.setPage(page);
            link.setDestination(dest);
            page.getAnnotations().add(link);
            document.save(pdf.toFile());
        }
        StaticLayoutContainers.clearContainers();
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        org.verapdf.pd.PDPage first = StaticResources.getDocument().getPages().get(0);
        formName = first.getResources().getXObjectNames().iterator().next().getValue();
        List<Object> formTokens = ContentStreamTokens.getTokens(
            (org.verapdf.pd.images.PDXForm) first.getResources().getXObject(org.verapdf.as.ASAtom.getASAtom(formName)));
        int fill = -1;
        for (int i = 0; i < formTokens.size(); i++) {
            if (formTokens.get(i) instanceof org.verapdf.operator.Operator
                && "f".equals(((org.verapdf.operator.Operator) formTokens.get(i)).getOperator())) {
                fill = i;
            }
        }
        List<Object> pageTokens = ContentStreamTokens.getTokens(first.getContent());
        int show = -1;
        for (int i = 0; i < pageTokens.size(); i++) {
            if (pageTokens.get(i) instanceof org.verapdf.operator.Operator
                && "Tj".equals(((org.verapdf.operator.Operator) pageTokens.get(i)).getOperator())) {
                show = i;
            }
        }
        assertTrue(fill >= 0 && show >= 0);
        TextChunk hello = new TextChunk(new BoundingBox(0, 72, 400, 100, 412), "Hello", 12.0, 12.0);
        hello.adjustSymbolEndsToBoundingBox(null);
        hello.getStreamInfos().add(new StreamInfo(show, null, 0, 5));
        SemanticParagraph second = new SemanticParagraph();
        second.add(new TextLine(hello));
        second.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        List<IObject> page = new ArrayList<>(textParagraphOnForm(0, fill));
        page.add(second);

        AutoTaggingProcessor.tagDocument(StaticResources.getDocument(), List.of(page), 2.0f);
        Path out = dir.resolve("linked_tagged.pdf");
        StaticResources.getDocument().saveAs(out.toString());

        try (PDDocument document = Loader.loadPDF(out.toFile())) {
            assertEquals(1, elementsOfType(structTreeRoot(document), "P"), "the paragraph on the form is gone");
            COSDictionary annotation = document.getPage(0).getAnnotations().get(0).getCOSObject();
            COSBase destination = annotation.getDictionaryObject(COSName.DEST);
            assertNotNull(destination, "the link keeps its destination");
            COSBase target = ((COSArray) destination).getObject(0);
            assertEquals("P", ((COSDictionary) target).getNameAsString(COSName.S), "it names the paragraph left");
        }
    }
}

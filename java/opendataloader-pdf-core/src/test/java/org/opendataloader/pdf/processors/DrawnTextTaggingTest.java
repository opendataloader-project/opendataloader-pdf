package org.opendataloader.pdf.processors;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.autotagging.ChunksWriter;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.entities.DrawnTextChunk;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.operator.Operator;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Text a page draws as filled paths, one per letter, which OCR has read: a
 * poster lettered in outlines (01030000000141). The paths are the element's
 * content and the OCR text its /ActualText, so a reader hears the words and a
 * viewer highlights where they are. Text with no content on the page at all
 * makes no element: a heading with nothing in it is announced and then silent.
 */
class DrawnTextTaggingTest {

    @TempDir
    Path tempDir;

    private List<Integer> fills;

    private void openPosterOfTwoLetters() throws Exception {
        Path pdf = tempDir.resolve("outlines.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (int letter = 0; letter < 2; letter++) {
                    content.moveTo(100 + 20 * letter, 500);
                    content.lineTo(115 + 20 * letter, 500);
                    content.lineTo(107 + 20 * letter, 520);
                    content.closePath();
                    content.fill();
                }
            }
            document.save(pdf.toFile());
        }
        StaticLayoutContainers.clearContainers();
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        List<Object> tokens = ChunksWriter.getTokens(StaticResources.getDocument().getPages().get(0).getContent());
        fills = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator && "f".equals(((Operator) tokens.get(i)).getOperator())) {
                fills.add(i);
            }
        }
    }

    private static <T extends org.verapdf.wcag.algorithms.entities.SemanticTextNode> T node(T node, TextChunk chunk) {
        node.add(new TextLine(chunk));
        node.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());
        return node;
    }

    private DrawnTextChunk drawn(String text) {
        DrawnTextChunk chunk = new DrawnTextChunk(new BoundingBox(0, 100, 500, 135, 520), text);
        for (int fill : fills) {
            chunk.getStreamInfos().add(new StreamInfo(fill, null, 0, 0));
        }
        return chunk;
    }

    /** Text OCR read where the page draws nothing a tag can hold. */
    private static TextChunk unanchored(String text) {
        TextChunk chunk = new TextChunk(new BoundingBox(0, 100, 300, 300, 320), text, 12.0, 12.0);
        chunk.adjustSymbolEndsToBoundingBox(null);
        return chunk;
    }

    private static List<COSObject> kidsOf(COSObject element) {
        List<COSObject> kids = new ArrayList<>();
        COSObject k = element.getKey(ASAtom.K);
        if (k == null || k.empty()) {
            return kids;
        }
        if (k.getType() == COSObjType.COS_ARRAY) {
            for (int i = 0; i < k.size(); i++) {
                kids.add(k.at(i));
            }
        } else {
            kids.add(k);
        }
        return kids;
    }

    private static COSObject documentElement() {
        return kidsOf(StaticResources.getDocument().getStructTreeRoot().getObject()).get(0);
    }

    private static List<COSObject> elements(String type) {
        List<COSObject> found = new ArrayList<>();
        for (COSObject kid : kidsOf(documentElement())) {
            if (kid.getType() == COSObjType.COS_DICT && type.equals(kid.getNameKeyStringValue(ASAtom.S))) {
                found.add(kid);
            }
        }
        return found;
    }

    private static String actualTextOf(COSObject element) {
        COSObject value = element.getKey(ASAtom.ACTUAL_TEXT);
        return value == null || value.empty() ? null : value.getString();
    }

    @Test
    void outlinedLettersAreTaggedWithTheirText() throws Exception {
        openPosterOfTwoLetters();
        List<IObject> page = List.of(node(new SemanticParagraph(), drawn("AB")));

        AutoTaggingProcessor.tagDocument(StaticResources.getDocument(), List.of(page), 1.7f);

        List<COSObject> paragraphs = elements("P");
        assertEquals(1, paragraphs.size());
        assertEquals("AB", actualTextOf(paragraphs.get(0)));
        assertTrue(kidsOf(paragraphs.get(0)).size() >= 1, "the outlines are its content");
    }

    /** A note with nothing in it is taken back, and so is its /ID: an ID tree entry would name an orphan. */
    @Test
    void anEmptyNoteLeavesNoIdBehind() throws Exception {
        openPosterOfTwoLetters();
        List<IObject> page = List.of(node(new org.opendataloader.pdf.entities.SemanticFootnote(), unanchored("1 Source")));

        AutoTaggingProcessor.tagDocument(StaticResources.getDocument(), List.of(page), 1.7f);

        assertEquals(List.of(), elements("Note"));
        java.lang.reflect.Field ids = AutoTaggingProcessor.class.getDeclaredField("idToStructElement");
        ids.setAccessible(true);
        assertTrue(((Map<?, ?>) ids.get(null)).isEmpty(), "IDs: " + ids.get(null));
    }

    /** A list whose items hold nothing makes no item, and then no list. */
    @Test
    void aListOfNothingMakesNoList() throws Exception {
        openPosterOfTwoLetters();
        org.verapdf.wcag.algorithms.entities.lists.ListItem item =
            new org.verapdf.wcag.algorithms.entities.lists.ListItem(new BoundingBox(0, 100, 300, 300, 320), 1L);
        item.add(new TextLine(unanchored("1. Source")));
        item.setLabelLength(2);
        org.verapdf.wcag.algorithms.entities.lists.PDFList list = new org.verapdf.wcag.algorithms.entities.lists.PDFList();
        list.add(item);
        list.setRecognizedStructureId(StaticLayoutContainers.incrementContentId());

        AutoTaggingProcessor.tagDocument(StaticResources.getDocument(), List.of(List.of(list)), 1.7f);

        assertEquals(List.of(), elements("L"));
    }

    @Test
    void textWithNothingOnThePageMakesNoElement() throws Exception {
        openPosterOfTwoLetters();
        SemanticHeading heading = node(new SemanticHeading(), unanchored("10 THINGS"));
        heading.setHeadingLevel(1);
        List<IObject> page = List.of(
            heading,
            node(new SemanticParagraph(), unanchored("Copyright protects creative work")));

        AutoTaggingProcessor.tagDocument(StaticResources.getDocument(), List.of(page), 1.7f);

        assertEquals(List.of(), elements("H1"));
        assertEquals(List.of(), elements("P"));
    }
}

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
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.verapdf.operator.Operator;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A backend reads text off the page image. When the page draws that text as
 * outlines there is no text operator to match it to, and the node was left
 * holding only what OCR read, which the tagger cannot tag (01030000000141, 2
 * empty H1 and 20 empty P). The outlines inside the node's box are its content.
 */
class HybridDrawnTextTest {

    @TempDir
    Path tempDir;

    private List<Integer> fills;

    private void openTwoOutlinedLetters() throws Exception {
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
                // A panel behind the words: bigger than the line, so not a letter of it.
                content.addRect(50, 450, 300, 120);
                content.fill();
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

    private static SemanticParagraph backendParagraph(String text, BoundingBox box) {
        TextChunk chunk = new TextChunk(box, text, 12.0, 12.0);
        chunk.adjustSymbolEndsToBoundingBox(null);
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(chunk));
        return paragraph;
    }

    private static void enrich(List<IObject> backendPage) throws Exception {
        Method method = HybridDocumentProcessor.class.getDeclaredMethod("enrichBackendResults",
            Map.class, Map.class, HybridConfig.class, Map.class);
        method.setAccessible(true);
        Map<Integer, List<IObject>> backend = new HashMap<>(Map.of(0, backendPage));
        method.invoke(null, backend, Map.of(0, List.of()), new HybridConfig(), new HashMap<>());
    }

    private static List<TextChunk> chunksOf(SemanticParagraph paragraph) {
        List<TextChunk> chunks = new ArrayList<>();
        for (TextLine line : paragraph.getFirstColumn().getLines()) {
            chunks.addAll(line.getTextChunks());
        }
        return chunks;
    }

    /**
     * Two backend boxes over one line of real text: the first takes the text,
     * the second matches nothing. An underline in its box is not its words, and
     * taking it made a second element that read the line again.
     */
    @Test
    void aNodeOverRealTextTakesNoOutlines() throws Exception {
        openTwoOutlinedLetters();
        TextChunk real = new TextChunk(new BoundingBox(0, 100, 498, 200, 512), "Hello", 12.0, 12.0);
        real.adjustSymbolEndsToBoundingBox(null);
        real.getStreamInfos().add(new StreamInfo(9999, null, 0, 5));
        SemanticParagraph first = backendParagraph("Hello", new BoundingBox(0, 95, 490, 205, 525));
        SemanticParagraph second = backendParagraph("Hello", new BoundingBox(0, 96, 491, 204, 524));

        Method method = HybridDocumentProcessor.class.getDeclaredMethod("enrichBackendResults",
            Map.class, Map.class, HybridConfig.class, Map.class);
        method.setAccessible(true);
        Map<Integer, List<IObject>> backend = new HashMap<>(Map.of(0, new ArrayList<>(List.of(first, second))));
        method.invoke(null, backend, Map.of(0, List.of(real)), new HybridConfig(), new HashMap<>());

        assertFalse(chunksOf(first).get(0) instanceof DrawnTextChunk);
        assertFalse(chunksOf(second).get(0) instanceof DrawnTextChunk, "the letters under the real text are not its words");
    }

    @Test
    void ocrTextOverOutlinesTakesTheOutlinesInItsBox() throws Exception {
        openTwoOutlinedLetters();
        SemanticParagraph overLetters = backendParagraph("AB", new BoundingBox(0, 98, 498, 137, 522));
        SemanticParagraph overNothing = backendParagraph("elsewhere", new BoundingBox(0, 100, 200, 300, 220));

        enrich(new ArrayList<>(List.of(overLetters, overNothing)));

        List<TextChunk> chunks = chunksOf(overLetters);
        assertEquals(1, chunks.size());
        DrawnTextChunk drawn = assertInstanceOf(DrawnTextChunk.class, chunks.get(0));
        assertEquals("AB", drawn.getValue());
        List<Integer> operators = new ArrayList<>();
        for (StreamInfo info : drawn.getStreamInfos()) {
            operators.add(info.getOperatorIndex());
        }
        assertEquals(fills.subList(0, 2), operators, "the letters, not the panel behind them");

        assertFalse(chunksOf(overNothing).get(0) instanceof DrawnTextChunk);
    }
}

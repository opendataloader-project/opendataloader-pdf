package org.opendataloader.pdf.autotagging;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.verapdf.cos.COSBase;
import org.verapdf.operator.Operator;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PaintBoundsTest {

    @TempDir
    Path tempDir;

    private static final double EPSILON = 0.01;

    /** Token indexes of the operators named {@code name}, in a token list. */
    private static List<Integer> indexesOf(List<Object> tokens, String name) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator && name.equals(((Operator) tokens.get(i)).getOperator())) {
                indexes.add(i);
            }
        }
        return indexes;
    }

    private static void assertBox(double left, double bottom, double right, double top, BoundingBox box) {
        assertNotNull(box);
        assertEquals(left, box.getLeftX(), EPSILON);
        assertEquals(bottom, box.getBottomY(), EPSILON);
        assertEquals(right, box.getRightX(), EPSILON);
        assertEquals(top, box.getTopY(), EPSILON);
    }

    @Test
    void eachPaintIsBoundedWhereItLandsOnThePage() throws Exception {
        Path pdf = tempDir.resolve("paths.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.saveGraphicsState();
                content.transform(new Matrix(2, 0, 0, 2, 100, 100));
                content.moveTo(10, 10);
                content.lineTo(20, 10);
                content.curveTo(25, 15, 25, 25, 20, 30);
                content.closePath();
                content.fill();
                content.restoreGraphicsState();
                content.addRect(300, 300, 50, 20);
                content.stroke();
            }
            document.save(pdf.toFile());
        }
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        List<Object> tokens = ChunksWriter.getTokens(StaticResources.getDocument().getPages().get(0).getContent());

        Map<OperatorStreamKey, Map<Integer, BoundingBox>> bounds = PaintBounds.of(0);

        Map<Integer, BoundingBox> page = bounds.get(new OperatorStreamKey(0, null));
        // The curve's control points lie outside the shape; the box still holds them.
        assertBox(120, 120, 150, 160, page.get(indexesOf(tokens, "f").get(0)));
        assertBox(300, 300, 350, 320, page.get(indexesOf(tokens, "S").get(0)));
        assertEquals(page.get(indexesOf(tokens, "f").get(0)).getPageNumber(), 0);
    }

    /**
     * A rotation then a scale does not commute with the scale then the
     * rotation, so this fails if a nested cm is composed the wrong way round.
     */
    @Test
    void nestedTransformationsComposeInsideOut() throws Exception {
        Path pdf = tempDir.resolve("rotated.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.saveGraphicsState();
                content.transform(new Matrix(0, 1, -1, 0, 300, 300));
                content.saveGraphicsState();
                content.transform(new Matrix(2, 0, 0, 2, 0, 0));
                content.addRect(0, 0, 10, 5);
                content.fill();
                content.restoreGraphicsState();
                content.restoreGraphicsState();
            }
            document.save(pdf.toFile());
        }
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        List<Object> tokens = ChunksWriter.getTokens(StaticResources.getDocument().getPages().get(0).getContent());

        Map<Integer, BoundingBox> page = PaintBounds.of(0).get(new OperatorStreamKey(0, null));

        // Scaled to 20 x 10, then turned a quarter about (300, 300).
        assertBox(290, 300, 300, 320, page.get(indexesOf(tokens, "f").get(0)));
    }

    @Test
    void aFormsPaintsAreBoundedWhereTheFormIsDrawn() throws Exception {
        Path pdf = tempDir.resolve("form.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            PDFormXObject form = new PDFormXObject(document);
            form.setBBox(new PDRectangle(0, 0, 100, 100));
            form.setMatrix(new java.awt.geom.AffineTransform(1, 0, 0, 1, 5, 0));
            try (org.apache.pdfbox.pdmodel.PDFormContentStream inner = new org.apache.pdfbox.pdmodel.PDFormContentStream(form)) {
                inner.addRect(0, 0, 10, 10);
                inner.fill();
            }
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.saveGraphicsState();
                content.transform(Matrix.getTranslateInstance(200, 400));
                content.drawForm(form);
                content.restoreGraphicsState();
            }
            document.save(pdf.toFile());
        }
        DocumentProcessor.preprocessing(pdf.toString(), new Config());
        List<Object> tokens = ChunksWriter.getTokens(StaticResources.getDocument().getPages().get(0).getContent());
        int doIndex = indexesOf(tokens, "Do").get(0);
        String formName = ((COSBase) tokens.get(doIndex - 1)).getString();

        Map<OperatorStreamKey, Map<Integer, BoundingBox>> bounds = PaintBounds.of(0);

        Map<Integer, BoundingBox> inForm = bounds.get(new OperatorStreamKey(0, formName));
        assertNotNull(inForm, "keys: " + bounds.keySet().size());
        assertEquals(1, inForm.size());
        assertBox(205, 400, 215, 410, inForm.values().iterator().next());
    }
}

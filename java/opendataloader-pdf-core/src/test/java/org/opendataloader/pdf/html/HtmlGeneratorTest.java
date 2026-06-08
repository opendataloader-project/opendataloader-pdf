package org.opendataloader.pdf.html;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opendataloader.pdf.entities.SemanticFormula;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.NodeUtils;

class HtmlGeneratorTest {
    /**
     * Creates a TextChunk with the given style properties.
     * Assumptions about internal representation:
     * - setFontColor(double[]) expects RGB in range [0.0, 1.0].
     * - setItalicAngle(0.0) → isItalic() == false, non‑zero → true.
     * - setFontWeight(double) is stored and getRoundedFontWeight() rounds it.
     */
    private TextChunk createChunk(String text, boolean strikethrough, boolean italic,
                                  boolean colorNonBlack, double fontWeight, double fontSize) {
        BoundingBox dummyBox = new BoundingBox(0, 0, 10, 20, 30);
        TextChunk chunk = new TextChunk(dummyBox, text, fontSize, 100.0);

        if (strikethrough) {
            chunk.setIsStrikethroughText();      // sets the flag to true
        }
        // italic angle: 10.0 for italic, 0.0 for normal
        chunk.setItalicAngle(italic ? 10.0 : 0.0);

        // color: red (non‑black) or black, normalised to [0,1]
        if (colorNonBlack) {
            chunk.setFontColor(new double[] { 1.0, 0.0, 0.0 });
        } else {
            chunk.setFontColor(new double[] { 0.0, 0.0, 0.0 });
        }

        chunk.setFontWeight(fontWeight);
        return chunk;
    }

    /**
     * Builds the expected style attribute value for a given combination.
     * The order matches getTextStyle(): strikethrough → italic → color → weight.
     */
    private String expectedStyle(boolean strikethrough, boolean italic,
                                 boolean colorNonBlack, double fontWeight, double fontSize) {
        StringBuilder style = new StringBuilder();
        if (strikethrough) {
            style.append("text-decoration: line-through; ");
        }
        if (italic) {
            style.append("font-style: italic; ");
        }
        if (fontSize != 12.0) {
            //Converting pt font-size into px
            double fontSizeInPx = fontSize * 4.0 / 3.0;
            style.append("font-size: ").append(String.format("%.3f", fontSizeInPx)).append("px; ");
        }
        if (colorNonBlack) {
            style.append("color: rgb(255, 0, 0); ");
        }
        int roundedWeight = (int) Math.round(fontWeight);
        if (roundedWeight != 400) {
            style.append("font-weight: ").append(roundedWeight).append("; ");
        }
        return style.toString().trim();
    }

    static Stream<Arguments> styleCombinations() {
        List<Arguments> args = new ArrayList<>();
        boolean[] bools = { false, true };
        double[] weights = { 400.0, 700.0 };   // 400 = default, 700 = bold
        double[] sizes = { 12.0, 32.0 }; // 12 = default in pt
        for (boolean s : bools) {
            for (boolean i : bools) {
                for (boolean c : bools) {
                    for (double w : weights) {
                        for (double fs : sizes) {
                            args.add(Arguments.of(s, i, c, w, fs));
                        }
                    }
                }
            }
        }
        return args.stream();
    }

    @ParameterizedTest(name = "strikethrough={0}, italic={1}, color={2}, weight={3}")
    @MethodSource("styleCombinations")
    void testAllStyleCombinations(boolean strikethrough, boolean italic,
                                  boolean colorNonBlack, double fontWeight, double fontSize) {
        TextChunk chunk = createChunk("A", strikethrough, italic, colorNonBlack, fontWeight, fontSize);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        String expected;
        if (strikethrough || italic || colorNonBlack || (int) Math.round(fontWeight) != 400 || !NodeUtils.areCloseNumbers(fontSize, 12.0)) {
            String styleAttr = expectedStyle(strikethrough, italic, colorNonBlack, fontWeight, fontSize);
            expected = "<span style=\"" + styleAttr + "\">A</span>";
        } else {
            expected = "A";
        }
        assertEquals(expected, sb.toString());
    }

    @Test
    void testEmptyLine() {
        TextLine line = new TextLine();
        StringBuilder sb = new StringBuilder();
        HtmlGenerator.getTextFromLineForHTML(line, sb);
        assertEquals("", sb.toString());
    }

    @Test
    void testNullColor() {
        // To simulate null getTextColor(), we can either:
        // - subclass TextChunk and override getTextColor() to return null, OR
        // - assume that setting fontColor to null in a real implementation yields null.
        // Here we use a custom stub that returns null from getTextColor().
        TextChunk chunk = new TextChunk(new BoundingBox(0,0,10,20,30), "x", 12, 100.0) {
            @Override
            public Color getTextColor() {
                return null;
            }
        };
        chunk.setFontWeight(400);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();
        HtmlGenerator.getTextFromLineForHTML(line, sb);
        assertEquals("x", sb.toString());
    }

    @Test
    void testBlackWithAlphaStillCountsAsBlack() {
        // The bitmask (0x00FFFFFF) ignores alpha, so even a translucent black should be treated as black.
        // We simulate this by overriding getTextColor() to return a Color with alpha != 255.
        Color blackAlpha = new Color(0, 0, 0, 128);
        TextChunk chunk = new TextChunk(new BoundingBox(0,0,10,20,30), "y", 12, 100.0) {
            @Override
            public Color getTextColor() {
                return blackAlpha;
            }
        };
        chunk.setFontWeight(400.0);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();
        HtmlGenerator.getTextFromLineForHTML(line, sb);
        assertEquals("y", sb.toString());
    }

    @Test
    void testFontWeightZero() {
        TextChunk chunk = createChunk("z", false, false, false, 0.0, 12.0);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();
        HtmlGenerator.getTextFromLineForHTML(line, sb);
        assertEquals("<span style=\"font-weight: 100;\">z</span>", sb.toString());
    }

    @Test
    void testMultipleChunksMixedStyles() {
        TextChunk helloChunk = new TextChunk(new BoundingBox(0,0,10,20,30), "Hello", 12, 100.0);
        helloChunk.setItalicAngle(1.0);
        helloChunk.setFontColor(new double[] {0.0, 0.0, 1.0});   // blue
        helloChunk.setFontWeight(400.0);

        TextChunk spaceChunk = createChunk(" ", false, false, false, 400.0, 12.0);
        TextChunk worldChunk = createChunk("World", true, true, true, 700.0, 12.0);

        TextLine line = new TextLine(helloChunk);
        line.add(spaceChunk);
        line.add(worldChunk);
        StringBuilder sb = new StringBuilder();
        HtmlGenerator.getTextFromLineForHTML(line, sb);

        String expected = "<span style=\"font-style: italic; color: rgb(0, 0, 255);\">Hello</span>"
            + " "
            + "<span style=\"text-decoration: line-through; font-style: italic; "
            + "color: rgb(255, 0, 0); font-weight: 700;\">World</span>";
        assertEquals(expected, sb.toString());
    }

    @Test
    void testStyleOrderIsStable() {
        TextChunk chunk = new TextChunk(new BoundingBox(0,0,10,20,30), "order", 12, 100.0);
        chunk.setIsStrikethroughText();
        chunk.setItalicAngle(1.0);
        chunk.setFontColor(new double[] {0.0, 1.0, 0.0});
        chunk.setFontWeight(300.0);

        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();
        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("<span style=\"text-decoration: line-through; font-style: italic; "
                + "color: rgb(0, 255, 0); font-weight: 300;\">order</span>",
            sb.toString());
    }

    @Test
    void testPdfTextIsEscapedForHtmlBodyContext() {
        TextChunk chunk = createChunk("<script>alert(1)</script>&", false, false, false, 400.0, 12.0);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;&amp;", sb.toString());
    }

    @Test
    void testStyledPdfTextIsEscapedInsideSpan() {
        TextChunk chunk = createChunk("<img src=x onerror=alert(1)>", false, true, false, 400.0, 12.0);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals(
            "<span style=\"font-style: italic;\">&lt;img src=x onerror=alert(1)&gt;</span>",
            sb.toString());
    }

    @Test
    void testHtmlTextEscapingHandlesTitleCharactersAndNull() {
        assertEquals("report &lt;draft&gt; &amp; notes", HtmlGenerator.escapeHtmlText("report <draft> & notes"));
        assertEquals("", HtmlGenerator.escapeHtmlText(null));
    }

    @Test
    void testAmpersandIsEscapedExactlyOnce() {
        TextChunk chunk = new TextChunk(new BoundingBox(0, 0, 10, 20, 30),
            "&lt; & &amp;", 12, 100.0);
        chunk.setFontWeight(400.0);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("&amp;lt; &amp; &amp;amp;", sb.toString());
    }

    @Test
    void testFormulaLatexIsEscaped() {
        String formulaLatex = "x < y & z";
        String result = HtmlGenerator.escapeHtmlText(formulaLatex);
        assertEquals("x &lt; y &amp; z", result);
    }

    @Test
    void testHtmlAttributeEscapingHandlesQuotesAndNewlines() {
        assertEquals("quote&quot; and null byte",
            HtmlGenerator.escapeHtmlAttribute("quote\" and\u0000\nnull byte"));
    }
}

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
    private TextChunk createChunk(String text, boolean strikethrough, boolean underlined) {
        BoundingBox dummyBox = new BoundingBox(0, 0, 10, 20, 30);
        TextChunk chunk = new TextChunk(dummyBox, text, 12, 100.0);

        if (strikethrough) {
            chunk.setIsStrikethroughText();      // sets the flag to true
        }

        if (underlined) {
            chunk.setIsUnderlinedText();      // sets the flag to true
        }
        return chunk;
    }

    /**
     * Builds the expected style attribute value for a given combination.
     * The order matches getTextStyle(): strikethrough → italic → color → weight.
     */
    private String expectedStyle(boolean strikethrough, boolean underlined) {
        StringBuilder style = new StringBuilder();
        if (strikethrough && underlined) {
            style.append("text-decoration: line-through underline; ");
        } else if (strikethrough) {
            style.append("text-decoration: line-through; ");
        } else if (underlined) {
            style.append("text-decoration: underline; ");
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

    @ParameterizedTest(name = "strikethrough={0}, underlined={1}")
    @MethodSource("styleCombinations")
    void testAllStyleCombinations(boolean strikethrough, boolean underlined) {
        TextChunk chunk = createChunk("A", strikethrough, underlined);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        String expected;
        if (strikethrough || underlined) {
            String styleAttr = expectedStyle(strikethrough, underlined);
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
    void testPdfTextIsEscapedForHtmlBodyContext() {
        TextChunk chunk = createChunk("<script>alert(1)</script>&", false, false);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals("&lt;script&gt;alert(1)&lt;/script&gt;&amp;", sb.toString());
    }

    @Test
    void testStyledPdfTextIsEscapedInsideSpan() {
        TextChunk chunk = createChunk("<img src=x onerror=alert(1)>", false, true);
        TextLine line = new TextLine(chunk);
        StringBuilder sb = new StringBuilder();

        HtmlGenerator.getTextFromLineForHTML(line, sb);

        assertEquals(
            "<span style=\"text-decoration: underline;\">&lt;img src=x onerror=alert(1)&gt;</span>",
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

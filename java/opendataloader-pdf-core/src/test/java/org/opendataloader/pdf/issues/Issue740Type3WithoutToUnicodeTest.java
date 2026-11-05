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
package org.opendataloader.pdf.issues;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSFloat;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #740: the text of a Type3 font without {@code /ToUnicode} is lost.
 *
 * <p>The glyph names in the {@code /Differences} of the font are not standard names. Thus veraPDF finds
 * no Unicode value for the glyphs. Up to veraPDF parser 1.31.68, veraPDF gave U+FFFD for each glyph, and
 * the default {@code --replace-invalid-chars} changed it to a space. A page with only this font gave no
 * elements. From parser 1.31.69, a Type3 glyph without a Unicode value gives its character code if the
 * code is printable ASCII (0x20 to 0x7E).
 *
 * <p>The samples are synthetic. Each sample has one page and a Type3 font. Each glyph of the font fills a
 * rectangle. The measurements are those of the report: font size 24, glyph width 1000, font matrix 0.001,
 * and a space code without a glyph. The output stays in {@code target/test-samples/issue-740-*}.
 */
class Issue740Type3WithoutToUnicodeTest {

    private static final COSName HELVETICA = COSName.getPDFName("F1");
    private static final String FIRST_LINE = "The next line uses a Type3 font.";
    private static final String LAST_LINE = "This line comes after the Type3 line.";
    /** The character codes in the content stream: printable ASCII, and a space that has no glyph. */
    private static final String CODES = "abba baab";
    /** Glyph names that are not in the Adobe Glyph List. */
    private static final String[] NON_STANDARD_NAMES = {"shape1", "shape2"};

    @Test
    void textOfAType3FontWithoutToUnicodeIsKept() throws IOException {
        Path pdf = writeSample("issue-740-only-type3", false,
            type3(new int[] {'a', 'b'}, NON_STANDARD_NAMES, null), ascii(CODES));

        assertThat(String.join("\n", contents(pdf))).as("text of the Type3 font without ToUnicode")
            .contains(CODES);
    }

    @Test
    void type3LineBetweenOtherLinesKeepsItsText() throws IOException {
        Path pdf = writeSample("issue-740-type3-between-lines", true,
            type3(new int[] {'a', 'b'}, NON_STANDARD_NAMES, null), ascii(CODES));

        String text = String.join("\n", contents(pdf));
        assertThat(text).as("text of the Type3 line between two Helvetica lines").contains(CODES);
        assertThat(text.indexOf(FIRST_LINE)).as("reading order: the first Helvetica line")
            .isGreaterThanOrEqualTo(0).isLessThan(text.indexOf(CODES));
        assertThat(text.indexOf(LAST_LINE)).as("reading order: the last Helvetica line")
            .isGreaterThan(text.indexOf(CODES));
    }

    @Test
    void toUnicodeStillGivesTheText() throws IOException {
        String cmap = "/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
            + "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n"
            + "/CMapName /T3U def /CMapType 2 def\n"
            + "1 begincodespacerange <00> <FF> endcodespacerange\n"
            + "3 beginbfchar <20> <0020> <61> <0058> <62> <0059> endbfchar\n"
            + "endcmap CMapName currentdict /CMap defineresource pop end end\n";
        Path pdf = writeSample("issue-740-type3-tounicode", false,
            type3(new int[] {'a', 'b'}, NON_STANDARD_NAMES, cmap), ascii(CODES));

        String text = String.join("\n", contents(pdf));
        assertThat(text).as("text from the ToUnicode map").contains("XYYX YXXY");
        assertThat(text).as("character codes in place of the ToUnicode values").doesNotContain("ab");
    }

    @Test
    void standardGlyphNamesStillGiveTheText() throws IOException {
        Path pdf = writeSample("issue-740-type3-standard-names", false,
            type3(new int[] {'a', 'b'}, new String[] {"X", "Y"}, null), ascii(CODES));

        String text = String.join("\n", contents(pdf));
        assertThat(text).as("text from the standard glyph names").contains("XYYX YXXY");
        assertThat(text).as("character codes in place of the glyph names").doesNotContain("ab");
    }

    @Test
    void codesOutsidePrintableAsciiGiveNoText() throws IOException {
        Path pdf = writeSample("issue-740-type3-control-and-high-codes", true,
            type3(new int[] {0x01, 0xC8}, NON_STANDARD_NAMES, null), new byte[] {0x01, (byte) 0xC8, (byte) 0xC8, 0x01});

        List<String> contents = contents(pdf);
        String text = String.join("\n", contents);
        assertThat(text).as("the Helvetica lines").contains(FIRST_LINE).contains(LAST_LINE);
        for (String content : contents) {
            assertThat(content.chars().filter(Character::isISOControl).count())
                .as("control characters from the codes 0x01 in: %s", content).isZero();
            assertThat(content).as("a Latin-1 character from the code 0xC8").doesNotContain("È");
        }
    }

    /** Converts the sample to JSON and gives the content of each element, in output order. */
    private static List<String> contents(Path pdf) throws IOException {
        Config config = new Config();
        config.setOutputFolder(pdf.getParent().toString());
        config.setGenerateJSON(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        String name = pdf.getFileName().toString().replaceFirst("\\.pdf$", ".json");
        JsonNode root = new ObjectMapper().readTree(Files.readString(pdf.resolveSibling(name)));
        List<String> contents = new ArrayList<>();
        for (JsonNode content : root.findValues("content")) {
            contents.add(content.asText());
        }
        return contents;
    }

    /**
     * One page. A Type3 line at the report's position (72, 700), in 24 pt. If {@code helveticaAround}, a
     * Helvetica line comes before it and after it.
     */
    private static Path writeSample(String name, boolean helveticaAround, Type3 font, byte[] codes)
            throws IOException {
        Path file = TestSamples.emptyDirectory(name).resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDResources resources = new PDResources();
            resources.put(HELVETICA, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            resources.getCOSObject().getCOSDictionary(COSName.FONT).setItem("T3", font.build(doc.getDocument()));
            page.setResources(resources);

            StringBuilder content = new StringBuilder();
            if (helveticaAround) {
                content.append("BT /F1 12 Tf 72 740 Td (").append(FIRST_LINE).append(") Tj ET\n");
            }
            content.append("BT /T3 24 Tf 72 700 Td <").append(hex(codes)).append("> Tj ET\n");
            if (helveticaAround) {
                content.append("BT /F1 12 Tf 72 660 Td (").append(LAST_LINE).append(") Tj ET\n");
            }
            PDStream stream = new PDStream(doc);
            try (OutputStream out = stream.createOutputStream()) {
                out.write(content.toString().getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            doc.save(file.toFile());
        }
        return file;
    }

    private static Type3 type3(int[] codes, String[] glyphNames, String toUnicode) {
        return new Type3(codes, glyphNames, toUnicode);
    }

    /** A Type3 font: one glyph for each code, a {@code /Differences} encoding, and an optional ToUnicode map. */
    private static final class Type3 {
        private final int[] codes;
        private final String[] glyphNames;
        private final String toUnicode;

        Type3(int[] codes, String[] glyphNames, String toUnicode) {
            this.codes = codes;
            this.glyphNames = glyphNames;
            this.toUnicode = toUnicode;
        }

        COSDictionary build(COSDocument cos) throws IOException {
            COSDictionary font = new COSDictionary();
            font.setItem(COSName.TYPE, COSName.FONT);
            font.setItem(COSName.SUBTYPE, COSName.getPDFName("Type3"));
            font.setItem(COSName.FONT_BBOX, new PDRectangle(0, 0, 750, 750).getCOSArray());
            font.setItem(COSName.FONT_MATRIX, array(new COSFloat(0.001f), COSInteger.ZERO, COSInteger.ZERO,
                new COSFloat(0.001f), COSInteger.ZERO, COSInteger.ZERO));

            COSDictionary procedures = new COSDictionary();
            COSArray differences = new COSArray();
            for (int i = 0; i < codes.length; i++) {
                procedures.setItem(glyphNames[i],
                    stream(cos, "1000 0 0 0 750 750 d1 0 " + (375 * i) + " 375 375 re f\n"));
                differences.add(COSInteger.get(codes[i]));
                differences.add(COSName.getPDFName(glyphNames[i]));
            }
            font.setItem(COSName.CHAR_PROCS, procedures);
            COSDictionary encoding = new COSDictionary();
            encoding.setItem(COSName.TYPE, COSName.ENCODING);
            encoding.setItem(COSName.DIFFERENCES, differences);
            font.setItem(COSName.ENCODING, encoding);

            int first = Arrays.stream(codes).min().getAsInt();
            int last = Arrays.stream(codes).max().getAsInt();
            COSArray widths = new COSArray();
            for (int code = first; code <= last; code++) {
                final int c = code;
                widths.add(COSInteger.get(Arrays.stream(codes).anyMatch(x -> x == c) ? 1000 : 0));
            }
            font.setInt(COSName.FIRST_CHAR, first);
            font.setInt(COSName.LAST_CHAR, last);
            font.setItem(COSName.WIDTHS, widths);
            font.setItem(COSName.RESOURCES, new COSDictionary());
            if (toUnicode != null) {
                font.setItem(COSName.TO_UNICODE, stream(cos, toUnicode));
            }
            return font;
        }
    }

    private static byte[] ascii(String text) {
        return text.getBytes(StandardCharsets.US_ASCII);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) {
            out.append(String.format("%02X", b & 0xFF));
        }
        return out.toString();
    }

    private static COSArray array(COSBase... items) {
        return new COSArray(Arrays.asList(items));
    }

    private static COSStream stream(COSDocument cos, String text) throws IOException {
        COSStream stream = cos.createCOSStream();
        try (OutputStream out = stream.createOutputStream()) {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
        }
        return stream;
    }
}

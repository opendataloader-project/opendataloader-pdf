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
package org.opendataloader.pdf.regression;

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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the veraPDF Type3 ascent bug.
 *
 * <p>A Type3 font has no font descriptor, so veraPDF gets the ascent from the glyph procedures.
 * With a glyph procedure that starts with {@code d0} (a colored glyph, without a bounding box), the
 * ascent stays null. The veraPDF parser 1.31.48 unboxed it in
 * {@code Type3CharProcParser.getAscent()} and failed with
 * {@code NullPointerException: Cannot invoke "java.lang.Double.doubleValue()" because "this.ascent" is null}.
 * opendataloader-pdf 2.5.4 to 2.5.6 used this parser, and failed on the first page of such a document.
 *
 * <p>Fixed in the veraPDF parser 1.31.51. This test makes sure that the fix does not regress.
 * The build does not read {@code verapdf.parser.version}. To test against an older parser,
 * temporarily change {@code verapdf.version} in {@code java/pom.xml} to a veraPDF release that
 * uses that parser. This also changes other veraPDF artifacts. With parser 1.31.48, the
 * {@code d0} cases fail and the {@code d1} case passes.
 *
 * <p>The samples are synthetic. The output stays in {@code target/test-samples/type3-*}.
 */
class Type3D0GlyphRegressionTest {

    private static final COSName F1 = COSName.getPDFName("F1");
    private static final String TYPE3_TEXT = "ABBA AB BA";

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "type3-d0,           750 0 d0,               false",
        "type3-d0-resources, 750 0 d0,               true",
        "type3-d1,           750 0 0 0 700 700 d1,   false",
    })
    void type3FontDoesNotStopTheDocument(String name, String glyphStart, boolean withResources)
            throws IOException {
        Path dir = TestSamples.emptyDirectory(name);
        Path source = writeType3Document(dir, name, glyphStart, withResources);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(true);
        config.setGenerateTaggedPDF(true);

        DocumentProcessor.processFile(source.toString(), config);

        Path json = dir.resolve(name + ".json");
        assertThat(json.toFile()).as("JSON").isFile();
        assertThat(new String(Files.readAllBytes(json), StandardCharsets.UTF_8)).as("JSON text")
            .contains(TYPE3_TEXT);
        assertThat(dir.resolve(name + "_tagged.pdf").toFile()).as("tagged PDF").isFile();
    }

    /** One page with a line in Helvetica, a line in a Type3 font, and one more line in Helvetica. */
    private static Path writeType3Document(Path dir, String name, String glyphStart, boolean withResources)
            throws IOException {
        Path file = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            COSDictionary type3 = type3Font(doc.getDocument(), glyphStart, withResources);

            PDResources resources = new PDResources();
            resources.put(F1, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
            resources.getCOSObject().getCOSDictionary(COSName.FONT).setItem("T3", type3);
            page.setResources(resources);
            PDStream content = new PDStream(doc);
            write(content, "BT /F1 12 Tf 72 730 Td (The line below is drawn with a Type3 font.) Tj ET\n"
                + "BT /T3 24 Tf 72 680 Td (" + TYPE3_TEXT + ") Tj ET\n"
                + "BT /F1 12 Tf 72 640 Td (A paragraph follows the Type3 line.) Tj ET\n");
            page.setContents(content);
            doc.save(file.toFile());
        }
        return file;
    }

    /** A Type3 font with the glyphs A and B. Each glyph procedure starts with {@code glyphStart}. */
    private static COSDictionary type3Font(COSDocument cos, String glyphStart, boolean withResources)
            throws IOException {
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.getPDFName("Type3"));
        font.setItem(COSName.FONT_BBOX, new PDRectangle(0, 0, 750, 750).getCOSArray());
        font.setItem(COSName.FONT_MATRIX, array(new COSFloat(0.001f), COSInteger.ZERO, COSInteger.ZERO,
            new COSFloat(0.001f), COSInteger.ZERO, COSInteger.ZERO));
        COSDictionary procedures = new COSDictionary();
        for (String glyph : new String[] {"a", "b"}) {
            procedures.setItem(glyph, stream(cos, glyphStart + "\n0 0 700 700 re f\n"));
        }
        font.setItem(COSName.CHAR_PROCS, procedures);
        COSDictionary encoding = new COSDictionary();
        encoding.setItem(COSName.TYPE, COSName.ENCODING);
        encoding.setItem(COSName.DIFFERENCES,
            array(COSInteger.get(65), COSName.getPDFName("a"), COSName.getPDFName("b")));
        font.setItem(COSName.ENCODING, encoding);
        font.setInt(COSName.FIRST_CHAR, 65);
        font.setInt(COSName.LAST_CHAR, 66);
        font.setItem(COSName.WIDTHS, array(COSInteger.get(750), COSInteger.get(750)));
        font.setItem(COSName.TO_UNICODE, stream(cos,
            "/CIDInit /ProcSet findresource begin 12 dict begin begincmap\n"
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def\n"
                + "/CMapName /T3U def /CMapType 2 def\n"
                + "1 begincodespacerange <00> <FF> endcodespacerange\n"
                + "2 beginbfchar <41> <0041> <42> <0042> endbfchar\n"
                + "endcmap CMapName currentdict /CMap defineresource pop end end\n"));
        if (withResources) {
            // Graphics resources without /Font, as in the documents of the original report.
            COSDictionary graphicsState = new COSDictionary();
            graphicsState.setItem(COSName.TYPE, COSName.EXT_G_STATE);
            graphicsState.setFloat(COSName.CA, 0.5f);
            COSDictionary graphicsStates = new COSDictionary();
            graphicsStates.setItem("GS1", graphicsState);
            COSDictionary glyphResources = new COSDictionary();
            glyphResources.setItem(COSName.EXT_G_STATE, graphicsStates);
            font.setItem(COSName.RESOURCES, glyphResources);
        }
        return font;
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

    private static void write(PDStream stream, String text) throws IOException {
        try (OutputStream out = stream.createOutputStream()) {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }
}

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

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1CFont;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.AutoTagger;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OutputWriter;
import org.opendataloader.pdf.api.TaggingResult;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.processors.ExtractionResult;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #779: after {@code DocumentProcessor.extractContents}, the caller must be able to close
 * the veraPDF document. Before the fix, no public method closed it. The input file stayed open,
 * and the temporary file ({@code tmp_pdf_file*.pdf}) of the embedded font program stayed in
 * {@code java.io.tmpdir}, also after the JVM exited.
 *
 * <p>The trigger is a CFF font program ({@code /FontFile3}, {@code /Subtype /Type1C}) that is
 * larger than 10240 bytes when decoded. veraPDF keeps a smaller font program in memory. For a
 * larger one, it uses a temporary file that stays until the document closes. The font program
 * of the reporter was 13845 bytes. TrueType font programs do not use this path.
 *
 * <p>The sample is synthetic: one page with one line of text in a simple Type 1 font. The test
 * makes the CFF font program: each glyph is a set of squares, 13837 bytes in total. The surefire
 * configuration gives the test JVM its own {@code java.io.tmpdir}, so files of other processes
 * do not count. The output stays in {@code target/test-samples/issue-779*}.
 */
class Issue779ExtractionCloseTest {

    private static final String TEXT = "The quick brown fox jumps over the lazy dog. 0123456789";
    private static final String FONT_NAME = "SyntheticSquares";
    // veraPDF keeps a decoded stream in memory up to this size, and uses a temporary file above it.
    private static final int VERAPDF_IN_MEMORY_LIMIT = 10240;
    // StandardEncoding gives the codes 32 to 126 the glyph names with the string IDs 1 to 95.
    private static final int FIRST_CODE = 32;
    private static final int LAST_CODE = 126;
    private static final int GLYPH_WIDTH = 500;
    private static final int SQUARES_PER_GLYPH = 17;
    private static final int SQUARE_SIZE = 40;

    @Test
    void closeReleasesTheTemporaryFiles() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-779");
        Path input = writeSample(directory.resolve("input.pdf"));
        Config config = jsonConfig(directory.resolve("output"));

        Set<String> before = veraPdfTemporaryFiles();
        ExtractionResult extraction = DocumentProcessor.extractContents(input.toString(), config);
        try {
            OutputWriter.writeOutputs(input.toString(), extraction, config);
            // Positive control: the check after close proves something only if this happens.
            assertThat(createdSince(before)).as("veraPDF temporary files while the extraction is open").isNotEmpty();
        } finally {
            close(extraction);
        }

        assertThat(veraPdfTemporaryFiles()).as("veraPDF temporary files after close").isSubsetOf(before);
        assertThat(StaticResources.getDocument()).as("veraPDF document after close").isNull();
        assertThat(directory.resolve("output").resolve("input.json")).exists();
    }

    @Test
    void closeAfterTheTaggingResultReleasesTheOtherResources() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-779-tagging");
        Path input = writeSample(directory.resolve("input.pdf"));

        Set<String> before = veraPdfTemporaryFiles();
        ExtractionResult extraction = DocumentProcessor.extractContents(input.toString(), new Config());
        Set<String> extractionFiles = createdSince(before);
        try {
            assertThat(extractionFiles).as("veraPDF temporary files while the extraction is open").isNotEmpty();
            try (TaggingResult tagged = AutoTagger.tag(extraction, null)) {
                tagged.saveTo(directory.resolve("input_tagged.pdf").toString());
            }
        } finally {
            close(extraction);   // The TaggingResult closed the same document first.
        }

        // Only the files of the extraction: tagging adds files that the document does not own (#781).
        assertThat(veraPdfTemporaryFiles()).as("veraPDF temporary files of the extraction after close")
            .doesNotContainAnyElementsOf(extractionFiles);
        assertThat(StaticContainers.getDocument()).as("layout document after close").isNull();
    }

    @Test
    void closeOfAnEarlierExtractionKeepsTheLaterDocumentOpen() throws Exception {
        Path directory = TestSamples.emptyDirectory("issue-779-two-extractions");
        Path first = writeSample(directory.resolve("first.pdf"));
        Path second = writeSample(directory.resolve("second.pdf"));
        Config config = jsonConfig(directory.resolve("output"));

        Set<String> before = veraPdfTemporaryFiles();
        ExtractionResult earlier = DocumentProcessor.extractContents(first.toString(), config);
        org.verapdf.pd.PDDocument earlierDocument = StaticResources.getDocument();
        try {
            ExtractionResult later = DocumentProcessor.extractContents(second.toString(), config);
            try {
                org.verapdf.pd.PDDocument laterDocument = StaticResources.getDocument();

                close(earlier);   // One close, while the later extraction is current.

                assertThat(StaticResources.getDocument()).as("veraPDF document after close of the earlier extraction")
                    .isSameAs(laterDocument);
                assertThat(earlierDocument.getDocument()).as("earlier document after its close").isNull();
                OutputWriter.writeOutputs(second.toString(), later, config);
            } finally {
                close(later);
            }
        } finally {
            close(earlier);   // Cleanup only: the check after the first close shows that it closed the document.
        }

        assertThat(veraPdfTemporaryFiles()).as("veraPDF temporary files after close").isSubsetOf(before);
        assertThat(StaticResources.getDocument()).as("veraPDF document after close").isNull();
        assertThat(directory.resolve("output").resolve("second.json")).exists();
    }

    /**
     * Closes the extraction through {@link AutoCloseable}. Thus the test also compiles on a build
     * without {@code ExtractionResult.close()}, and fails there with the symptom of #779.
     */
    private static void close(ExtractionResult extraction) throws Exception {
        Object result = extraction;
        if (result instanceof AutoCloseable) {
            ((AutoCloseable) result).close();
        }
    }

    private static Config jsonConfig(Path output) {
        Config config = new Config();
        config.setOutputFolder(output.toString());
        config.setGenerateJSON(true);
        return config;
    }

    static Path writeSample(Path pdf) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDFont font = new PDType1CFont(fontDictionary(document));
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            page.setResources(new PDResources());
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.newLineAtOffset(72, 700);
                content.showText(TEXT);
                content.endText();
            }
            document.save(pdf.toFile());
        }
        assertThat(decodedFontProgramLength(pdf)).as("decoded font program of the sample")
            .isGreaterThan(VERAPDF_IN_MEMORY_LIMIT);
        return pdf;
    }

    /** A simple Type 1 font with an embedded CFF font program and its built-in encoding. */
    private static COSDictionary fontDictionary(PDDocument document) throws IOException {
        COSStream fontFile = document.getDocument().createCOSStream();
        fontFile.setItem(COSName.SUBTYPE, COSName.getPDFName("Type1C"));
        try (OutputStream out = fontFile.createOutputStream(COSName.FLATE_DECODE)) {
            out.write(cffFontProgram());
        }
        COSDictionary descriptor = new COSDictionary();
        descriptor.setItem(COSName.TYPE, COSName.FONT_DESC);
        descriptor.setName(COSName.FONT_NAME, FONT_NAME);
        descriptor.setInt(COSName.FLAGS, 32);   // nonsymbolic
        descriptor.setItem(COSName.FONT_BBOX, new PDRectangle(0, 0, 1000, 1000).getCOSArray());
        descriptor.setInt(COSName.ITALIC_ANGLE, 0);
        descriptor.setInt(COSName.ASCENT, 1000);
        descriptor.setInt(COSName.DESCENT, 0);
        descriptor.setInt(COSName.CAP_HEIGHT, 700);
        descriptor.setInt(COSName.STEM_V, 80);
        descriptor.setItem(COSName.FONT_FILE3, fontFile);

        COSArray widths = new COSArray();
        for (int code = FIRST_CODE; code <= LAST_CODE; code++) {
            widths.add(COSInteger.get(GLYPH_WIDTH));
        }
        COSDictionary font = new COSDictionary();
        font.setItem(COSName.TYPE, COSName.FONT);
        font.setItem(COSName.SUBTYPE, COSName.TYPE1);
        font.setName(COSName.BASE_FONT, FONT_NAME);
        font.setInt(COSName.FIRST_CHAR, FIRST_CODE);
        font.setInt(COSName.LAST_CHAR, LAST_CODE);
        font.setItem(COSName.WIDTHS, widths);
        font.setItem(COSName.FONT_DESC, descriptor);
        return font;
    }

    /**
     * A bare CFF font program: {@code .notdef} and one glyph for each code of StandardEncoding
     * from 32 to 126. The default charset (ISOAdobe) gives glyph {@code i} the string ID
     * {@code i}, so the glyph names agree with StandardEncoding.
     */
    private static byte[] cffFontProgram() throws IOException {
        List<byte[]> glyphs = new ArrayList<>();
        glyphs.add(new byte[] {14});   // .notdef: endchar
        for (int code = FIRST_CODE; code <= LAST_CODE; code++) {
            glyphs.add(squaresGlyph());
        }
        byte[] header = {1, 0, 4, 4};
        byte[] names = index(Collections.singletonList(FONT_NAME.getBytes(StandardCharsets.US_ASCII)));
        byte[] strings = index(Collections.emptyList());
        byte[] globalSubroutines = index(Collections.emptyList());
        byte[] charStrings = index(glyphs);
        byte[] privateDict = concat(dictNumber(GLYPH_WIDTH), new byte[] {20});   // defaultWidthX

        int topDictIndexLength = index(Collections.singletonList(topDict(0, 0, 0))).length;
        int charStringsOffset = header.length + names.length + topDictIndexLength + strings.length
            + globalSubroutines.length;
        int privateOffset = charStringsOffset + charStrings.length;
        byte[] topDicts = index(Collections.singletonList(
            topDict(charStringsOffset, privateDict.length, privateOffset)));
        return concat(header, names, topDicts, strings, globalSubroutines, charStrings, privateDict);
    }

    /** CharStrings (17) and Private (18), with 5-byte integers, so that the length is fixed. */
    private static byte[] topDict(int charStringsOffset, int privateLength, int privateOffset) {
        return concat(fixedInteger(charStringsOffset), new byte[] {17},
            fixedInteger(privateLength), fixedInteger(privateOffset), new byte[] {18});
    }

    /** A Type 2 charstring: closed squares, each one as four lines, at most 48 operands for each rlineto. */
    private static byte[] squaresGlyph() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(139 + 50);
        out.write(139);
        out.write(21);   // 50 0 rmoveto
        int[] square = {SQUARE_SIZE, 0, 0, SQUARE_SIZE, -SQUARE_SIZE, 0, 0, -SQUARE_SIZE};
        int operands = 0;
        for (int number = 0; number < SQUARES_PER_GLYPH; number++) {
            for (int delta : square) {
                out.write(139 + delta);
                operands++;
            }
            if (operands == 48 || number == SQUARES_PER_GLYPH - 1) {
                out.write(5);   // rlineto
                operands = 0;
            }
        }
        out.write(14);   // endchar
        return out.toByteArray();
    }

    private static byte[] index(List<byte[]> items) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(items.size() >> 8);
        out.write(items.size());
        if (items.isEmpty()) {
            return out.toByteArray();
        }
        int last = 1;
        for (byte[] item : items) {
            last += item.length;
        }
        int offsetSize = last <= 0xFF ? 1 : last <= 0xFFFF ? 2 : 3;
        out.write(offsetSize);
        int offset = 1;
        writeOffset(out, offset, offsetSize);
        for (byte[] item : items) {
            offset += item.length;
            writeOffset(out, offset, offsetSize);
        }
        for (byte[] item : items) {
            out.write(item, 0, item.length);
        }
        return out.toByteArray();
    }

    private static void writeOffset(ByteArrayOutputStream out, int offset, int offsetSize) {
        for (int shift = 8 * (offsetSize - 1); shift >= 0; shift -= 8) {
            out.write(offset >> shift);
        }
    }

    /** A DICT number from 108 to 1131, in two bytes. */
    private static byte[] dictNumber(int value) {
        return new byte[] {(byte) ((value - 108) / 256 + 247), (byte) ((value - 108) % 256)};
    }

    private static byte[] fixedInteger(int value) {
        return new byte[] {29, (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value};
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.write(part, 0, part.length);
        }
        return out.toByteArray();
    }

    private static int decodedFontProgramLength(Path pdf) throws IOException {
        try (PDDocument saved = Loader.loadPDF(pdf.toFile())) {
            PDResources resources = saved.getPage(0).getResources();
            PDFont font = resources.getFont(resources.getFontNames().iterator().next());
            return font.getFontDescriptor().getFontFile3().toByteArray().length;
        }
    }

    private static Set<String> createdSince(Set<String> before) {
        Set<String> created = veraPdfTemporaryFiles();
        created.removeAll(before);
        return created;
    }

    static Set<String> veraPdfTemporaryFiles() {
        Set<String> names = new HashSet<>();
        File[] files = new File(System.getProperty("java.io.tmpdir"))
            .listFiles((folder, name) -> name.startsWith("tmp_pdf_file") && name.endsWith(".pdf"));
        if (files != null) {
            for (File file : files) {
                names.add(file.getName());
            }
        }
        return names;
    }
}

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

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Form XObject and an Image XObject with the same resource name stopped the tagged PDF with
 * {@code ClassCastException: PDXImage cannot be cast to PDXForm}, and no tagged PDF was written.
 *
 * <p>The tagged PDF finds an XObject of a {@code Do} operator by its page and its name. In the
 * sample, the page calls the Form {@code /Fm0}, and the Form calls its own {@code /Fm0}, which is
 * an Image. Inside the Form, the name thus finds the Image. The tagged PDF must rewrite only the
 * content stream of a Form, and must not set {@code /StructParents} on an Image.
 *
 * <p>The sample is synthetic. The output stays in {@code target/test-samples/xobject-name-collision/}.
 */
class XObjectNameCollisionTaggedPdfTest {

    private static final COSName FM0 = COSName.getPDFName("Fm0");
    private static final COSName F1 = COSName.getPDFName("F1");

    private static Path writeFormAndImageWithOneName(Path dir) throws IOException {
        Path file = dir.resolve("form-image-same-name.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            BufferedImage pixels = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < 4; x++) {
                for (int y = 0; y < 4; y++) {
                    pixels.setRGB(x, y, 0xC81E1E);
                }
            }
            PDImageXObject image = LosslessFactory.createFromImage(doc, pixels);

            PDFormXObject form = new PDFormXObject(doc);
            form.setBBox(new PDRectangle(0, 0, 300, 100));
            PDResources formResources = new PDResources();
            formResources.put(F1, font);
            // The same name as the Form in the page resources.
            formResources.put(FM0, image);
            form.setResources(formResources);
            write(form.getContentStream(),
                "BT /F1 12 Tf 10 50 Td (Text inside the form XObject.) Tj ET\n"
                    + "q 40 0 0 40 10 0 cm /Fm0 Do Q\n");

            PDResources pageResources = new PDResources();
            pageResources.put(F1, font);
            pageResources.put(FM0, form);
            page.setResources(pageResources);
            PDStream content = new PDStream(doc);
            write(content, "BT /F1 14 Tf 72 760 Td (A form and an image share one name.) Tj ET\n"
                + "q 1 0 0 1 72 600 cm /Fm0 Do Q\n"
                + "BT /F1 12 Tf 72 560 Td (This paragraph follows the form.) Tj ET\n");
            page.setContents(content);
            doc.save(file.toFile());
        }
        return file;
    }

    private static void write(PDStream stream, String text) throws IOException {
        try (OutputStream out = stream.createOutputStream()) {
            out.write(text.getBytes(StandardCharsets.US_ASCII));
        }
    }

    @Test
    void imageWithTheNameOfItsFormDoesNotStopTheTaggedPdf() throws IOException {
        Path dir = TestSamples.emptyDirectory("xobject-name-collision");
        Path source = writeFormAndImageWithOneName(dir);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(true);
        config.setGenerateTaggedPDF(true);

        DocumentProcessor.processFile(source.toString(), config);

        assertThat(dir.resolve("form-image-same-name.json").toFile()).as("JSON").isFile();
        File tagged = dir.resolve("form-image-same-name_tagged.pdf").toFile();
        assertThat(tagged).as("tagged PDF").isFile();
        try (PDDocument doc = Loader.loadPDF(tagged)) {
            PDPage page = doc.getPage(0);
            COSDictionary form = dictionary(xObjects(page.getResources().getCOSObject()).getDictionaryObject(FM0));
            COSDictionary image = dictionary(xObjects(dictionary(form.getDictionaryObject(COSName.RESOURCES)))
                .getDictionaryObject(FM0));

            assertThat(form.getDictionaryObject(COSName.SUBTYPE)).as("subtype of the page /Fm0")
                .isEqualTo(COSName.FORM);
            assertThat(image.getDictionaryObject(COSName.SUBTYPE)).as("subtype of the form /Fm0")
                .isEqualTo(COSName.IMAGE);
            assertThat(form.getDictionaryObject(COSName.STRUCT_PARENTS)).as("/StructParents of the form")
                .isNotNull();
            assertThat(image.getDictionaryObject(COSName.STRUCT_PARENTS)).as("/StructParents of the image")
                .isNull();
            assertThat(doOperators(page)).as("Do operators of the page").containsExactly("Fm0");
            assertThat(doOperators(new PDFormXObject((COSStream) form))).as("Do operators of the form")
                .containsExactly("Fm0");
        }
    }

    private static COSDictionary xObjects(COSDictionary resources) {
        return dictionary(resources.getDictionaryObject(COSName.XOBJECT));
    }

    private static COSDictionary dictionary(COSBase base) {
        return (COSDictionary) (base instanceof COSObject ? ((COSObject) base).getObject() : base);
    }

    /** The XObject names of the Do operators in {@code stream}. */
    private static List<String> doOperators(PDContentStream stream) throws IOException {
        List<String> names = new ArrayList<>();
        List<Object> tokens = new PDFStreamParser(stream).parse();
        for (int i = 1; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator && "Do".equals(((Operator) tokens.get(i)).getName())) {
                names.add(((COSName) tokens.get(i - 1)).getName());
            }
        }
        return names;
    }
}

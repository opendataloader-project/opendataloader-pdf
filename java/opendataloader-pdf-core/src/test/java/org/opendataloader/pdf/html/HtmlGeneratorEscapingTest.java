package org.opendataloader.pdf.html;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.SemanticHeading;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

class HtmlGeneratorEscapingTest {

    @Test
    void writesEscapedHeadingText() throws Exception {
        Path tempDir = Files.createTempDirectory("html-escape-test");
        File testPdf = new File("../../samples/pdf/lorem.pdf");
        Config config = new Config();
        HtmlGenerator generator = new HtmlGenerator(testPdf, tempDir.toString(), config);
        try {
            SemanticHeading heading = new SemanticHeading();
            TextLine line = new TextLine(new TextChunk(new BoundingBox(0, 0, 0, 10, 10), "<script>alert('x')</script>", "Font", 10, 400, 0, 10.0, new double[]{0.0}, null, 0));
            heading.add(line);
            generator.writeHeading(heading);
        } finally {
            generator.close();
        }
        Path htmlPath = tempDir.resolve("lorem.html");
        String content = Files.readString(htmlPath);
        Assertions.assertThat(content).contains("&lt;script&gt;alert(&#x27;x&#x27;)&lt;/script&gt;");
    }
}

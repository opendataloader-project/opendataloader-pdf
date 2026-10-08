package org.opendataloader.pdf.api;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.testsupport.TestSamples;
import org.verapdf.pd.PDDocument;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AutoTaggerTest {

    private static String testPdf;

    /**
     * Writes a synthetic untagged PDF to {@code target/test-samples/auto-tagger/}: one page with
     * two paragraphs. Any untagged PDF is enough.
     */
    @BeforeAll
    static void writeSample() throws IOException {
        Path pdf = TestSamples.emptyDirectory("auto-tagger").resolve("untagged.pdf");
        TestSamples.writeTextPdf(pdf, 1,
            "First paragraph of a synthetic sample.", "Second paragraph of a synthetic sample.");
        testPdf = pdf.toString();
    }

    @Test
    void tagReturnsDocumentWithStructTree() throws Exception {
        Config config = new Config();

        try (TaggingResult result = AutoTagger.tag(testPdf, config, null)) {
            PDDocument doc = result.getDocument();
            assertThat(doc).isNotNull();
            assertThat(doc.getCatalog().getKey(org.verapdf.as.ASAtom.STRUCT_TREE_ROOT).empty())
                .as("Tagged PDF should have StructTreeRoot")
                .isFalse();
        }
    }

    @Test
    void tagTimingsArePositive() throws Exception {
        Config config = new Config();

        try (TaggingResult result = AutoTagger.tag(testPdf, config, null)) {
            assertThat(result.getExtractionNs()).isGreaterThan(0);
            assertThat(result.getTaggingNs()).isGreaterThan(0);
        }
    }

    @Test
    void saveToWritesFile(@TempDir Path tempDir) throws Exception {
        Config config = new Config();
        String outputPath = tempDir.resolve("output_tagged.pdf").toString();

        try (TaggingResult result = AutoTagger.tag(testPdf, config, null)) {
            result.saveTo(outputPath);
        }

        assertThat(new File(outputPath)).exists();
        assertThat(new File(outputPath).length()).isGreaterThan(0);
    }

    @Test
    void tagIgnoresOutputFormatFlags() throws Exception {
        Config config = new Config();
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);

        try (TaggingResult result = AutoTagger.tag(testPdf, config, null)) {
            assertThat(result.getDocument()).isNotNull();
        }
    }
}

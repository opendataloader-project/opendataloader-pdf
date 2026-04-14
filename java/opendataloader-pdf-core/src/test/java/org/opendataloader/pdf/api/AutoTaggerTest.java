package org.opendataloader.pdf.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.verapdf.pd.PDDocument;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AutoTaggerTest {

    private static final String TEST_PDF = new File("src/test/resources/cid-font-no-tounicode.pdf").getAbsolutePath();

    @Test
    void tagReturnsDocumentWithStructTree() throws Exception {
        Config config = new Config();

        try (TaggingResult result = AutoTagger.tag(TEST_PDF, config)) {
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

        try (TaggingResult result = AutoTagger.tag(TEST_PDF, config)) {
            assertThat(result.getExtractionNs()).isGreaterThan(0);
            assertThat(result.getTaggingNs()).isGreaterThan(0);
        }
    }

    @Test
    void saveToWritesFile(@TempDir Path tempDir) throws Exception {
        Config config = new Config();
        String outputPath = tempDir.resolve("output_tagged.pdf").toString();

        try (TaggingResult result = AutoTagger.tag(TEST_PDF, config)) {
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

        try (TaggingResult result = AutoTagger.tag(TEST_PDF, config)) {
            assertThat(result.getDocument()).isNotNull();
        }
    }
}

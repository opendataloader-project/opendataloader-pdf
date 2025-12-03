package org.opendataloader.pdf.processors;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.verapdf.tools.StaticResources;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

class DocumentProcessorMultiFileIT {

    @Test
    void processesMultiplePdfsSequentiallyWithoutResourceLeaks() throws IOException {
        Path moduleDir = Paths.get("").toAbsolutePath();
        Path samplesDir = moduleDir.resolve("../../samples/pdf").normalize();
        Path source1 = samplesDir.resolve("lorem.pdf");
        Path source2 = samplesDir.resolve("2408.02509v1.pdf");

        Assertions.assertThat(Files.exists(source1)).as("sample lorem.pdf exists").isTrue();
        Assertions.assertThat(Files.exists(source2)).as("sample 2408.02509v1.pdf exists").isTrue();

        Path tempBase = Files.createTempDirectory("odl-pdf-multi-" + UUID.randomUUID());

        Path pdf1 = tempBase.resolve("doc1.pdf");
        Path pdf2 = tempBase.resolve("doc2.pdf");
        Files.copy(source1, pdf1);
        Files.copy(source2, pdf2);

        Config cfg1 = new Config();
        cfg1.setGenerateJSON(true);
        cfg1.setGeneratePDF(false);
        cfg1.setGenerateMarkdown(false);
        cfg1.setGenerateHtml(false);
        cfg1.setGenerateText(false);
        cfg1.setOutputFolder(tempBase.resolve("out1").toString());

        DocumentProcessor.processFile(pdf1.toString(), cfg1);

        Assertions.assertThat(StaticResources.getDocument()).as("PDDocument cleared after first run").isNull();
        Assertions.assertThat(StaticLayoutContainers.getHeadings()).as("Headings cleared after first run").isNotNull().isEmpty();
        Assertions.assertThat(StaticLayoutContainers.getCurrentContentId()).as("Content ID reset after first run").isEqualTo(1L);

        boolean deleted1 = Files.deleteIfExists(pdf1);
        Assertions.assertThat(deleted1).as("First PDF file can be deleted (no lock)").isTrue();

        Config cfg2 = new Config();
        cfg2.setGenerateJSON(true);
        cfg2.setGeneratePDF(false);
        cfg2.setGenerateMarkdown(false);
        cfg2.setGenerateHtml(false);
        cfg2.setGenerateText(false);
        cfg2.setOutputFolder(tempBase.resolve("out2").toString());

        DocumentProcessor.processFile(pdf2.toString(), cfg2);

        Assertions.assertThat(StaticResources.getDocument()).as("PDDocument cleared after second run").isNull();
        Assertions.assertThat(StaticLayoutContainers.getHeadings()).as("Headings cleared after second run").isNotNull().isEmpty();
        Assertions.assertThat(StaticLayoutContainers.getCurrentContentId()).as("Content ID reset after second run").isEqualTo(1L);

        boolean deleted2 = Files.deleteIfExists(pdf2);
        Assertions.assertThat(deleted2).as("Second PDF file can be deleted (no lock)").isTrue();

        // Also ensure WCAG StaticContainers reset state between runs
        Assertions.assertThat(StaticContainers.getDocument()).as("WCAG document cleared between runs").isNull();
    }
}


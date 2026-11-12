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
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.AutoTagger;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.TaggingResult;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #926: in the tagged PDF of a PDF 2.0 document, the root {@code Document} element belongs
 * to the PDF 2.0 namespace (PDF/UA-2, ISO 14289-2:2024, clause 8.2.5.2). Synthetic samples: one
 * page of Helvetica text, saved as PDF 2.0 or PDF 1.7.
 */
class Issue926PdfTwoDocumentNamespaceTest {

    private static final String PDF2_NAMESPACE = "http://iso.org/pdf2/ssn";
    private static final COSName NS = COSName.getPDFName("NS");
    private static final COSName NAMESPACES = COSName.getPDFName("Namespaces");

    @Test
    void taggedPdfOfPdf20DocumentPutsDocumentInPdf20Namespace() throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-926-pdf20");
        Path tagged = tag(dir, writeSample(dir, "pdf20.pdf", 2.0f));

        try (PDDocument doc = Loader.loadPDF(tagged.toFile())) {
            assertThat(namespaceOf(rootDocument(doc)))
                .as("the root Document element has no PDF 2.0 namespace (/NS)")
                .isEqualTo(PDF2_NAMESPACE);
        }
    }

    @Test
    void inMemoryTaggingForPdf20PutsDocumentInPdf20Namespace() throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-926-in-memory");
        Path pdf = writeSample(dir, "pdf17.pdf", 1.7f);
        Path tagged = dir.resolve("pdf17_tagged-as-pdf20.pdf");
        try (TaggingResult result = AutoTagger.tag(pdf.toString(), new Config(), 2.0f)) {
            result.saveTo(tagged.toString());
        }

        try (PDDocument doc = Loader.loadPDF(tagged.toFile())) {
            assertThat(namespaceOf(rootDocument(doc)))
                .as("the root Document element has no PDF 2.0 namespace (/NS)")
                .isEqualTo(PDF2_NAMESPACE);
        }
    }

    @Test
    void taggedPdfOfPdf17DocumentHasNoNamespaces() throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-926-pdf17");
        Path tagged = tag(dir, writeSample(dir, "pdf17.pdf", 1.7f));

        try (PDDocument doc = Loader.loadPDF(tagged.toFile())) {
            assertThat(structTreeRoot(doc).containsKey(NAMESPACES))
                .as("a PDF 1.7 tagged PDF has no /Namespaces")
                .isFalse();
            assertThat(rootDocument(doc).containsKey(NS))
                .as("the root Document element of a PDF 1.7 tagged PDF has no /NS")
                .isFalse();
        }
    }

    private static Path writeSample(Path dir, String name, float version) throws IOException {
        Path pdf = dir.resolve(name);
        try (PDDocument doc = TestSamples.textDocument(1,
                "A synthetic paragraph for the namespace test.", "A second synthetic paragraph.")) {
            doc.setVersion(version);
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    private static Path tag(Path dir, Path source) throws IOException {
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);
        String base = source.getFileName().toString().replaceFirst("\\.pdf$", "");
        Path tagged = dir.resolve(base + "_tagged.pdf");
        assertThat(tagged).as("the tagged PDF").isRegularFile();
        return tagged;
    }

    private static COSDictionary structTreeRoot(PDDocument doc) {
        COSDictionary root = doc.getDocumentCatalog().getCOSObject().getCOSDictionary(COSName.STRUCT_TREE_ROOT);
        assertThat(root).as("the /StructTreeRoot").isNotNull();
        return root;
    }

    /** The only kid of the structure tree root. */
    private static COSDictionary rootDocument(PDDocument doc) {
        COSBase kids = structTreeRoot(doc).getDictionaryObject(COSName.K);
        if (kids instanceof COSArray) {
            assertThat(((COSArray) kids).size()).as("kids of the structure tree root").isEqualTo(1);
            kids = ((COSArray) kids).getObject(0);
        }
        assertThat(kids).as("the root structure element").isInstanceOf(COSDictionary.class);
        COSDictionary document = (COSDictionary) kids;
        assertThat(document.getNameAsString(COSName.S)).as("the root structure type").isEqualTo("Document");
        return document;
    }

    /** The namespace name of an element's /NS, or null if it has none. */
    private static String namespaceOf(COSDictionary element) {
        COSDictionary ns = element.getCOSDictionary(NS);
        return ns == null ? null : ns.getString(NS);
    }
}

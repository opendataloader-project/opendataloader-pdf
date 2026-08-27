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
package org.opendataloader.pdf.processors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code /ParentTree} number tree must have ascending keys.
 *
 * <p>ISO 32000-1 7.9.7: {@code /Nums} keys have to ascend, because a reader
 * binary-searches them. Page keys and annotation keys are allocated from one
 * counter ({@code currentStructParent++} in {@code processAnnotations}) and
 * interleave, so writing each group in its own pass produced descending runs —
 * the search then missed entries that were present, and Acrobat showed no
 * structure tag for the page's content.
 *
 * <p>The fixture needs link annotations to exercise this. Without them there is
 * only one group and any order is trivially ascending. Measured over a
 * 200-document corpus: every one of the 63 documents with unsorted keys had link
 * annotations, and every one of the 137 sorted ones had none.
 *
 * <p>The assertion is on the <em>written</em> array, not on a lookup. A linear
 * scan finds the entry either way, which is exactly how 63 documents read as
 * healthy while a conforming reader's binary search failed on them.
 */
class ParentTreeOrderTest {

    /**
     * A tagged PDF with text and several links per page.
     *
     * <p>More than one link per page matters: the annotation keys for a page are
     * allocated after that page's content keys, so a single link per page can
     * leave the concatenation accidentally ascending.
     */
    private static Path writeTaggedPdfWithLinks(Path dir, int pageCount, int linksPerPage)
            throws IOException {
        Path file = dir.resolve("links.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int page = 0; page < pageCount; page++) {
                PDPage pdPage = new PDPage(PDRectangle.LETTER);
                doc.addPage(pdPage);
                try (PDPageContentStream cs = new PDPageContentStream(doc, pdPage)) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    cs.newLineAtOffset(72, 720);
                    cs.showText("Page " + (page + 1) + " body text above the links.");
                    cs.endText();
                    for (int link = 0; link < linksPerPage; link++) {
                        cs.beginText();
                        cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        cs.newLineAtOffset(72, 680 - link * 30);
                        cs.showText("link target " + link);
                        cs.endText();
                    }
                }
                for (int link = 0; link < linksPerPage; link++) {
                    PDAnnotationLink annotation = new PDAnnotationLink();
                    PDRectangle box = new PDRectangle();
                    box.setLowerLeftX(72);
                    box.setLowerLeftY(676 - link * 30);
                    box.setUpperRightX(300);
                    box.setUpperRightY(694 - link * 30);
                    annotation.setRectangle(box);
                    PDActionURI action = new PDActionURI();
                    action.setURI("https://example.invalid/" + page + "/" + link);
                    annotation.setAction(action);
                    pdPage.getAnnotations().add(annotation);
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    /** The keys of {@code /ParentTree}'s {@code /Nums}, in the order written. */
    private static List<Integer> numsKeys(File pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            COSBase structTreeRoot = resolve(doc.getDocumentCatalog().getCOSObject()
                    .getDictionaryObject(COSName.getPDFName("StructTreeRoot")));
            assertNotNull(structTreeRoot, "the document should carry a structure tree");

            COSBase parentTree = resolve(((COSDictionary) structTreeRoot)
                    .getDictionaryObject(COSName.getPDFName("ParentTree")));
            assertNotNull(parentTree, "the structure tree should carry a ParentTree");

            COSBase nums = resolve(((COSDictionary) parentTree)
                    .getDictionaryObject(COSName.getPDFName("Nums")));
            assertNotNull(nums, "the ParentTree should carry Nums");

            List<Integer> keys = new ArrayList<>();
            COSArray array = (COSArray) nums;
            // Nums is a flat [key value key value ...] array.
            for (int i = 0; i < array.size(); i += 2) {
                COSBase key = resolve(array.get(i));
                if (key instanceof COSInteger) {
                    keys.add(((COSInteger) key).intValue());
                }
            }
            return keys;
        }
    }

    private static COSBase resolve(COSBase base) {
        return base instanceof COSObject ? ((COSObject) base).getObject() : base;
    }

    private static Path tempDir() throws IOException {
        return Files.createTempDirectory("parenttree");
    }

    private static File tag(Path dir, Path source) throws IOException {
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(false);
        config.setGenerateTaggedPDF(true);
        DocumentProcessor.processFile(source.toString(), config);

        String base = source.getFileName().toString().replaceFirst("\\.pdf$", "");
        File tagged = dir.resolve(base + "_tagged.pdf").toFile();
        assertTrue(tagged.isFile(), "expected a tagged PDF at " + tagged);
        return tagged;
    }

    @Test
    void numsKeysAscendWhenAnnotationKeysInterleaveWithPageKeys() throws IOException {
        Path dir = tempDir();
        try {
            // One page, one link is the minimal reproduction. Annotations are
            // numbered in processAnnotations *before* the page's own content
            // key, so the annotation gets the lower number while the page group
            // was written first: a real corpus document emits exactly [1, 0].
            List<Integer> keys = numsKeys(tag(dir, writeTaggedPdfWithLinks(dir, 1, 1)));

            assertFalse(keys.isEmpty(), "Nums should hold at least one entry");
            assertTrue(keys.size() >= 2,
                    "the fixture needs both a page key and an annotation key, or there is "
                            + "only one group and any order is trivially ascending: " + keys);
            for (int i = 1; i < keys.size(); i++) {
                assertTrue(keys.get(i - 1) < keys.get(i),
                        "Nums keys must ascend (ISO 32000-1 7.9.7) — a reader binary-searches "
                                + "them, so a descending run makes a present entry unreachable. "
                                + "Got " + keys);
            }
        } finally {
            deleteRecursively(dir.toFile());
        }
    }

    /**
     * A document with no annotations has one key group, and must stay ascending.
     *
     * <p>Such a document can legitimately produce an empty {@code /Nums} — with
     * nothing to register there is nothing to order — so emptiness is accepted
     * and only the ordering is asserted.
     */
    @Test
    void numsKeysAscendWithoutAnnotations() throws IOException {
        Path dir = tempDir();
        try {
            List<Integer> keys = numsKeys(tag(dir, writeTaggedPdfWithLinks(dir, 3, 0)));

            for (int i = 1; i < keys.size(); i++) {
                assertTrue(keys.get(i - 1) < keys.get(i), "Got " + keys);
            }
        } finally {
            deleteRecursively(dir.toFile());
        }
    }

    /** Every key appears once: a duplicate would make the lookup ambiguous. */
    @Test
    void numsKeysAreUnique() throws IOException {
        Path dir = tempDir();
        try {
            List<Integer> keys = numsKeys(tag(dir, writeTaggedPdfWithLinks(dir, 4, 3)));

            assertEquals(keys.size(), new java.util.HashSet<>(keys).size(),
                    "a repeated key makes the reader's lookup ambiguous: " + keys);
        } finally {
            deleteRecursively(dir.toFile());
        }
    }

    private static void assertEquals(int expected, int actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}

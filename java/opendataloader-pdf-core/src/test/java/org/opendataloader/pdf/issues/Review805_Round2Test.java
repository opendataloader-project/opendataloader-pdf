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

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDListAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round 2 of the review of #805: the cases that the round-1 tests do not cover. Each test asserts
 * the proposed behavior of round 1 (rules 1-5), with --use-struct-tree and Markdown output.
 * The samples are synthetic tagged PDFs from {@link Review805_ListMarkersTest.TaggedListPdf}.
 */
class Review805_Round2Test {

    private static List<String> markdownLines(Path pdf, boolean useStructTree) throws IOException {
        Config config = new Config();
        config.setOutputFolder(pdf.getParent().toString());
        config.setGenerateJSON(false);
        config.setGenerateMarkdown(true);
        config.setUseStructTree(useStructTree);
        DocumentProcessor.processFile(pdf.toString(), config);
        Path markdown = pdf.resolveSibling(pdf.getFileName().toString().replace(".pdf", ".md"));
        return Files.readAllLines(markdown, StandardCharsets.UTF_8);
    }

    /** A P element with one line of text, after the items written so far. */
    private static void paragraph(Review805_ListMarkersTest.TaggedListPdf sample, String text) throws IOException {
        PDStructureElement p = new PDStructureElement(StandardStructureTypes.P, sample.documentElement);
        sample.documentElement.appendKid(p);
        int mcid = sample.parents.size();
        COSDictionary properties = new COSDictionary();
        properties.setInt(COSName.MCID, mcid);
        sample.content.beginMarkedContent(COSName.P, PDPropertyList.create(properties));
        sample.content.beginText();
        sample.content.setFont(sample.helvetica, 12);
        sample.content.newLineAtOffset(72, sample.y);
        sample.content.showText(text);
        sample.content.endText();
        sample.content.endMarkedContent();
        p.setPage(sample.page);
        p.getCOSObject().setInt(COSName.K, mcid);
        sample.parents.add(p);
        sample.y -= 30;
    }

    private static Path pdf(String name) {
        return TestSamples.emptyDirectory(name).resolve(name + ".pdf");
    }

    /** N1, rule 4: a paragraph that starts with "1." after an ordered list keeps its number in a viewer. */
    @Test
    void paragraphWithANumberAfterAnOrderedListKeepsItsNumberWhenRendered() throws IOException {
        Path pdf = pdf("review-805-r2-paragraph-after-list");
        try (Review805_ListMarkersTest.TaggedListPdf sample = new Review805_ListMarkersTest.TaggedListPdf()) {
            PDStructureElement list = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(list, 72, "1.", "Alpha item");
            sample.item(list, 72, "2.", "Beta item");
            sample.y -= 20;
            paragraph(sample, "1. Background");
            sample.save(pdf);
        }

        List<String> lines = markdownLines(pdf, true);
        Map<String, Integer> rendered = Review805_ListMarkersTest.renderedNumbers(lines);

        assertThat(rendered.get("Background")).as("shown number of the paragraph '1. Background' (" + lines + ")")
                .isIn(null, 1);
        // null alone would also pass if the "1." were dropped, so the line must still show it.
        assertThat(lines).as("the paragraph still starts with its number (" + lines + ")")
                .anyMatch(l -> l.matches("^1\\\\?\\. Background$"));
    }

    /** N1b, rule 4: a paragraph that starts with "1." before an ordered list does not renumber the list. */
    @Test
    void paragraphWithANumberBeforeAnOrderedListDoesNotRenumberTheList() throws IOException {
        Path pdf = pdf("review-805-r2-paragraph-before-list");
        try (Review805_ListMarkersTest.TaggedListPdf sample = new Review805_ListMarkersTest.TaggedListPdf()) {
            paragraph(sample, "1. Introduction");
            PDStructureElement list = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(list, 72, "1.", "Alpha item");
            sample.item(list, 72, "2.", "Beta item");
            sample.save(pdf);
        }

        List<String> lines = markdownLines(pdf, true);
        Map<String, Integer> rendered = Review805_ListMarkersTest.renderedNumbers(lines);

        assertThat(rendered.get("Alpha item")).as("shown number of 'Alpha item' (" + lines + ")").isIn(null, 1);
        assertThat(rendered.get("Beta item")).as("shown number of 'Beta item' (" + lines + ")").isIn(null, 2);
    }

    /** N2, rule 1: with /ListNumbering /Disc, the bullet label is removed, as on main, also when it is a letter. */
    @Test
    void discListRemovesALetterBulletAsOnMain() throws IOException {
        Path pdf = pdf("review-805-r2-disc-letter");
        try (Review805_ListMarkersTest.TaggedListPdf sample = new Review805_ListMarkersTest.TaggedListPdf()) {
            PDStructureElement list = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DISC);
            sample.item(list, 72, "o", "First item");
            sample.item(list, 72, "o", "Second item");
            sample.save(pdf);
        }

        List<String> lines = markdownLines(pdf, true);

        assertThat(lines).as("Markdown: the label is removed (" + lines + ")")
                .anyMatch(l -> l.matches("^-\\s+First item$")).anyMatch(l -> l.matches("^-\\s+Second item$"));
    }

    /** N6 (M3), ISO 32000-1 Table 347: an explicit /ListNumbering /None means the same as no attribute. */
    @Test
    void explicitNoneListNumberingIsTheSameAsNoAttribute() throws IOException {
        Path none = pdf("review-805-r2-explicit-none");
        Path absent = pdf("review-805-r2-absent");
        for (Path target : new Path[] {none, absent}) {
            try (Review805_ListMarkersTest.TaggedListPdf sample = new Review805_ListMarkersTest.TaggedListPdf()) {
                PDStructureElement list = sample.list(sample.documentElement,
                        target == none ? PDListAttributeObject.LIST_NUMBERING_NONE : null);
                sample.item(list, 72, "•", "First item");
                sample.item(list, 72, "•", "Second item");
                sample.save(target);
            }
        }

        assertThat(markdownLines(none, true)).as("Markdown with an explicit /None").isEqualTo(markdownLines(absent, true));
    }

    /**
     * A {@code Lbl} of a bullet and two spaces in separate chunks, with a body that starts with "(". The line
     * of the item has one space after the bullet, so the label length (3) reaches into the body.
     */
    @Test
    void bulletLabelWithSeparateSpacesKeepsTheParenthesisOfTheBody() throws IOException {
        Path pdf = pdf("review-805-r2-split-label");
        try (Review805_ListMarkersTest.TaggedListPdf sample = new Review805_ListMarkersTest.TaggedListPdf()) {
            PDStructureElement list = sample.list(sample.documentElement, null);
            sample.splitLabelItem(list, 72, new String[] {"\u2022", "  "}, "(first) item");
            sample.splitLabelItem(list, 72, new String[] {"\u2022", "  "}, "(second) item");
            sample.save(pdf);
        }

        List<String> lines = markdownLines(pdf, true);

        assertThat(lines).as("Markdown: a bullet list that keeps the parenthesis (" + lines + ")")
                .anyMatch(l -> l.matches("^-\\s+\\(first\\) item$")).anyMatch(l -> l.matches("^-\\s+\\(second\\) item$"));
    }

    /** M5, rule 4: a nested ordered list that starts at 3 is still a list in a viewer, with its numbers. */
    @Test
    void nestedOrderedListThatStartsAtThreeKeepsItsNumbersWhenRendered() throws IOException {
        Path pdf = pdf("review-805-r2-nested-from-three");
        try (Review805_ListMarkersTest.TaggedListPdf sample = new Review805_ListMarkersTest.TaggedListPdf()) {
            PDStructureElement outer = sample.list(sample.documentElement, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            PDStructureElement body = sample.item(outer, 72, "1.", "Parent item");
            PDStructureElement inner = sample.list(body, PDListAttributeObject.LIST_NUMBERING_DECIMAL);
            sample.item(inner, 108, "3.", "Child three");
            sample.item(inner, 108, "4.", "Child four");
            sample.item(outer, 72, "2.", "Second parent");
            sample.save(pdf);
        }

        List<String> lines = markdownLines(pdf, true);
        Map<String, Integer> rendered = Review805_ListMarkersTest.renderedNumbers(lines);
        boolean numberShown = Integer.valueOf(3).equals(rendered.get("Child three"));
        boolean numberAsText = lines.stream().anyMatch(l -> l.matches("^ *- 3\\. Child three$"));

        assertThat(numberShown || numberAsText)
                .as("a viewer shows 3 for 'Child three': as its list number, or as text after '- ' (" + lines + ")").isTrue();
    }

}

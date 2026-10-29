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

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #827: the text of an unordered list item that starts with "#" or with a number and "." becomes a
 * heading or a nested ordered list in the Markdown output.
 *
 * <p>The Markdown output writes each item after "- ". A CommonMark reader shows "- # text" as a heading
 * in the item, and "- 1. text" as a nested ordered list. The expected output escapes the marker with a
 * backslash (CommonMark 0.31.2, 2.4 Backslash escapes), so the item is one bullet with the whole text.
 *
 * <p>Each sample has the form of the issue: left-aligned lines in Helvetica 11, each line a "•" item.
 * The samples and the outputs stay in {@code target/test-samples/}, in the folders whose names start
 * with {@code issue-827}.
 */
class Issue827ListItemMarkerTest {

    private static final String PLAN = "• Plan of the site office";
    private static final String COST = "• Cost of the plan";

    @Test
    void hashItemIsOneBulletWithTheWholeText() throws IOException {
        assertEquals(List.of("- \\# of users in the plan", "- Plan of the site office", "- Cost of the plan"),
            markdownLines("hash", false, "• # of users in the plan", PLAN, COST),
            "the item text starts a heading");
    }

    @Test
    void numberedItemIsOneBulletWithTheWholeText() throws IOException {
        assertEquals(List.of("- 1\\. Introduction to the plan", "- Plan of the site office", "- Cost of the plan"),
            markdownLines("number", false, "• 1. Introduction to the plan", PLAN, COST),
            "the item text starts a nested list");
    }

    /** The other heading and list markers of CommonMark; the last item is only the marker. */
    @Test
    void otherHeadingAndListMarkersAreEscaped() throws IOException {
        assertEquals(List.of(
                "- \\## of users in the plan",
                "- 1\\) Introduction to the plan",
                "- 2024\\. Annual plan of the office",
                "- \\- of users in the plan",
                "- \\+ of users in the plan",
                "- \\* of users in the plan",
                "- \\#"),
            markdownLines("markers", false,
                "• ## of users in the plan",
                "• 1) Introduction to the plan",
                "• 2024. Annual plan of the office",
                "• - of users in the plan",
                "• + of users in the plan",
                "• * of users in the plan",
                "• #"),
            "the item text starts a heading or a nested list");
    }

    /** {@code --markdown-with-html} writes lists with the same code. */
    @Test
    void hashItemIsOneBulletWithTheWholeTextInMarkdownWithHtml() throws IOException {
        assertEquals(List.of("- \\# of users in the plan", "- Plan of the site office", "- Cost of the plan"),
            markdownLines("hash-html", true, "• # of users in the plan", PLAN, COST),
            "the item text starts a heading");
    }

    /**
     * No space after the marker, seven "#", or a number of ten digits: CommonMark does not start a block,
     * so the text stays as it is.
     */
    @Test
    void textThatDoesNotStartABlockStaysAsItIs() throws IOException {
        assertEquals(List.of(
                "- #hashtag of the plan",
                "- 1.5 million users of the plan",
                "- ####### of users in the plan",
                "- 1234567890. Ten digits of the plan",
                "- Plan of the site office"),
            markdownLines("not-markers", false,
                "• #hashtag of the plan",
                "• 1.5 million users of the plan",
                "• ####### of users in the plan",
                "• 1234567890. Ten digits of the plan",
                PLAN));
    }

    /** An ordered list keeps its numbers in the item text. This fix does not change it. */
    @Test
    void orderedListKeepsItsNumbers() throws IOException {
        assertEquals(List.of("- 1. Introduction to the plan", "- 2. Plan of the site office", "- 3. Cost of the plan"),
            markdownLines("ordered", false,
                "1. Introduction to the plan", "2. Plan of the site office", "3. Cost of the plan"));
    }

    /** Converts a one-page sample with the given lines, and returns the lines of the Markdown that are not empty. */
    private static List<String> markdownLines(String name, boolean useHtml, String... lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-827-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        writeSample(pdf, lines);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateMarkdown(true);
        config.setUseHTMLInMarkdown(useHtml);
        DocumentProcessor.processFile(pdf.toString(), config);
        return Arrays.stream(Files.readString(directory.resolve(name + ".md")).split("\n"))
            .filter(line -> !line.isBlank())
            .collect(Collectors.toList());
    }

    private static void writeSample(Path pdf, String... lines) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
                content.newLineAtOffset(72, 700);
                for (String line : lines) {
                    content.showText(line);
                    content.newLineAtOffset(0, -16);
                }
                content.endText();
            }
            document.save(pdf.toFile());
        }
    }
}

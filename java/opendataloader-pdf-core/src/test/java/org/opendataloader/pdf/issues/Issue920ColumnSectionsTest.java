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

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #920: a gap at the same height in two columns, where one column starts a new section and the other column goes on.
 *
 * <p>Each column has sections with a heading. A gap between two sections of one column is at the same height as a
 * gap inside a section of the other column, and it is larger than the gutter. The columns are read one after the
 * other: the section of the other column goes on below the gap. Before this change, the page was cut at the gap
 * first, and the output went from the top of one column to the top of the other column.
 *
 * <p>The samples have the measurements of the page in the issue: a landscape page, two columns of about 325 pt
 * with a gutter of about 22 pt, text in 9 pt, headings in 11 pt bold, 12 pt between the lines, and a gap of
 * about 28 pt at the same height in both columns. The samples and the outputs stay in
 * {@code target/test-samples/}, in the folders whose names start with {@code issue-920}.
 */
class Issue920ColumnSectionsTest {

    private static final float LEFT = 48;
    private static final float RIGHT = 396;
    private static final float BODY = 9;
    private static final float HEADING = 11;
    private static final float LINE = 12;
    private static final String SYMPTOM = "the columns are read across the gap";
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);

    /**
     * The left column has sections 3 and 4; the right column has section 5, which goes on below the gap. The order
     * is 3, 4, 5, and the rest of 5.
     */
    @Test
    void sectionThatGoesOnBelowTheGapIsReadAfterTheOtherColumn() throws IOException {
        List<Line> lines = new ArrayList<>();
        section(lines, LEFT, 470, "3. Renewal of the contract", "Alpha", 4);
        section(lines, LEFT, 372, "4. Loss that is paid back", "Bravo", 4);
        section(lines, RIGHT, 470, "5. Main points of the product", "Charlie", 4);
        body(lines, RIGHT, 360, "Delta", 4);

        assertOrder(convert("left-column-new-section", lines), "Renewal", "Alpha", "Loss", "Bravo", "Main points",
            "Charlie", "Delta");
    }

    /**
     * The same layout, mirrored: the left column goes on below the gap, and the right column starts section 6
     * there. The order is 3, the rest of 3, 5, 6.
     */
    @Test
    void leftSectionThatGoesOnBelowTheGapIsReadBeforeTheRightColumn() throws IOException {
        List<Line> lines = new ArrayList<>();
        section(lines, LEFT, 470, "3. Renewal of the contract", "Alpha", 4);
        body(lines, LEFT, 360, "Bravo", 4);
        section(lines, RIGHT, 470, "5. Main points of the product", "Charlie", 4);
        section(lines, RIGHT, 372, "6. Rules that also apply", "Delta", 4);

        assertOrder(convert("right-column-new-section", lines), "Renewal", "Alpha", "Bravo", "Main points",
            "Charlie", "Rules", "Delta");
    }

    /**
     * Guard: both columns start a new section below the gap, as in a grid of four boxes. The page is read part by
     * part: the top row, then the bottom row.
     */
    @Test
    void gridOfSectionsIsReadRowByRow() throws IOException {
        List<Line> lines = new ArrayList<>();
        section(lines, LEFT, 470, "1. First box", "Alpha", 4);
        section(lines, RIGHT, 470, "2. Second box", "Bravo", 4);
        section(lines, LEFT, 372, "3. Third box", "Charlie", 4);
        section(lines, RIGHT, 372, "4. Fourth box", "Delta", 4);

        assertOrder(convert("grid", lines), "First box", "Alpha", "Second box", "Bravo", "Third box", "Charlie",
            "Fourth box", "Delta");
    }

    /**
     * Guard: text that goes across both columns above the gap, then a heading of a new part in the left column only,
     * as on a banner that goes across the page. The text above the gap has no heading in the right column, so the
     * page is read part by part: the text above, then the new part.
     */
    @Test
    void headingOfANewPartBelowTextInBothColumnsStartsTheNewPart() throws IOException {
        List<Line> lines = new ArrayList<>();
        body(lines, LEFT, 470, "Alpha", 5);
        body(lines, RIGHT, 470, "Bravo", 4);
        lines.add(new Line(LEFT, 372, true, HEADING, "Practice questions"));
        body(lines, LEFT, 352, "Charlie", 4);
        body(lines, RIGHT, 365, "Delta", 4);

        assertOrder(convert("new-part", lines), "Alpha", "Bravo", "Practice questions", "Charlie", "Delta");
    }

    /** A heading at the baseline, and lines of body text below it, all with the marker word. */
    private static void section(List<Line> lines, float x, float baseline, String heading, String marker, int count) {
        lines.add(new Line(x, baseline, true, HEADING, heading));
        body(lines, x, baseline - 20, marker, count);
    }

    /** Words of body text: each line is about as wide as a column of the page in the issue, about 315 pt. */
    private static final String[] TEXT = {
        "text of this part of the column starts here, and it goes on for a few lines,",
        "and each of these lines is about as wide as the column on the page in the issue,",
        "so that the gap between the two columns is as small as the gap on that page, and",
        "the gap between two parts of a column is larger than it, as it is on that page.",
        "The last line of this part of the column ends here, a bit before the column end."};

    /** Lines of body text from the baseline down. The first line starts with the marker word to find the part. */
    private static void body(List<Line> lines, float x, float baseline, String marker, int count) {
        for (int i = 0; i < count; i++) {
            String text = i == 0 ? marker + " " + TEXT[0] : TEXT[i % TEXT.length];
            lines.add(new Line(x, baseline - i * LINE, false, BODY, text));
        }
    }

    private static void assertOrder(String output, String... texts) {
        int previous = -1;
        for (String text : texts) {
            int index = output.indexOf(text);
            assertTrue(index >= 0, "the output does not have \"" + text + "\": " + output);
            assertTrue(index > previous, SYMPTOM + " (\"" + text + "\" is out of order): " + output);
            previous = index;
        }
    }

    private static String convert(String name, List<Line> lines) throws IOException {
        Path directory = TestSamples.emptyDirectory("issue-920-" + name);
        Path pdf = directory.resolve(name + ".pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(750, 520));
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                for (Line line : lines) {
                    content.beginText();
                    content.setFont(line.bold ? BOLD : REGULAR, line.size);
                    content.newLineAtOffset(line.x, line.baseline);
                    content.showText(line.text);
                    content.endText();
                }
            }
            document.save(pdf.toFile());
        }
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setReadingOrder(Config.READING_ORDER_XYCUT);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new ObjectMapper().readTree(directory.resolve(name + ".json").toFile()).toString();
    }

    /** A line of text at its place, in the bold or the regular font. */
    private static final class Line {
        final float x;
        final float baseline;
        final boolean bold;
        final float size;
        final String text;

        Line(float x, float baseline, boolean bold, float size, String text) {
            this.x = x;
            this.baseline = baseline;
            this.bold = bold;
            this.size = size;
            this.text = text;
        }
    }
}

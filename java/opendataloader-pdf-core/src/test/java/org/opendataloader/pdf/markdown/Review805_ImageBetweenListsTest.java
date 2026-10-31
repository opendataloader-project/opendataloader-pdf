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
package org.opendataloader.pdf.markdown;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.ImageChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.geometry.MultiBoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.listLabelsDetection.NumberingStyleNames;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review of #805, round 2, case C1: two ordered lists with an object between them that writes
 * nothing (a Figure whose image file is not written) stay two lists in a CommonMark viewer.
 */
class Review805_ImageBetweenListsTest {

    private static final class Gen extends MarkdownGenerator {
        final StringWriter out;

        Gen(StringWriter out, Config config) {
            super(out, config);
            this.out = out;
        }

        /** Same loop as writeToMarkdown, without the page loop (no StaticContainers document). */
        String render(List<IObject> contents) throws IOException {
            for (IObject content : contents) {
                if (!isSupportedContent(content)) {
                    continue;
                }
                write(content);
                writeContentsSeparator();
            }
            markdownWriter.flush();
            return out.toString();
        }
    }

    private static Gen generator() {
        Config config = new Config();
        config.setUseStructTree(true);
        return new Gen(new StringWriter(), config);
    }

    private static ListItem item(String label, String body, boolean strikeLabel) {
        ListItem item = new ListItem(new MultiBoundingBox(), null);
        TextLine line = new TextLine();
        TextChunk labelChunk = new TextChunk(label);
        if (strikeLabel) {
            labelChunk.setIsStrikethroughText();
        }
        line.add(labelChunk);
        line.add(new TextChunk(body));
        item.add(line);
        item.setLabelLength(label.length());
        return item;
    }

    private static PDFList list(String style, String[] labels, String[] bodies) {
        PDFList list = new PDFList();
        list.setNumberingStyle(style);
        for (int i = 0; i < labels.length; i++) {
            list.add(item(labels[i], bodies[i], false));
        }
        return list;
    }

    private static final java.util.regex.Pattern ORDERED = java.util.regex.Pattern.compile("^(\\d{1,9})([.)]) (.*)$");

    @Test
    void orderedListsWithAnEmptyFigureBetweenStayTwoListsWhenRendered() throws IOException {
        Gen gen = generator();
        gen.isImageSupported = true; // image output on; the file for this chunk does not exist
        PDFList first = list(NumberingStyleNames.ARABIC_NUMBERS, new String[]{"1.", "2."}, new String[]{"a", "b"});
        ImageChunk image = new ImageChunk(new BoundingBox(0, 0, 0, 10, 10));
        PDFList second = list(NumberingStyleNames.ARABIC_NUMBERS, new String[]{"1."}, new String[]{"c"});
        String[] lines = gen.render(List.of(first, image, second)).split("\n", -1);

        int b = -1;
        int c = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].endsWith("b")) b = i;
            if (lines[i].endsWith("c")) c = i;
        }
        assertTrue(b >= 0 && c > b, String.join("\n", lines));
        java.util.regex.Matcher itemB = ORDERED.matcher(lines[b]);
        java.util.regex.Matcher itemC = ORDERED.matcher(lines[c]);
        boolean sameListKind = itemB.matches() && itemC.matches() && itemB.group(2).equals(itemC.group(2));
        boolean blockBetween = false;
        for (int i = b + 1; i < c; i++) {
            String line = lines[i];
            if (!line.trim().isEmpty() && !line.startsWith(" ") && !ORDERED.matcher(line).matches()) {
                blockBetween = true; // CommonMark: a paragraph or an HTML block after a blank line ends the list
            }
        }
        assertTrue(!sameListKind || blockBetween,
                "item 'c' joins the list of 'a' and 'b', so a viewer shows 3 for it:\n" + String.join("\n", lines));
    }
}

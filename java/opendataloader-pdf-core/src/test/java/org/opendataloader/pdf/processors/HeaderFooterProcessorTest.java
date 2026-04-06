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

import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.verapdf.tools.StaticResources;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.List;

public class HeaderFooterProcessorTest {

    private void initContainers() {
        StaticContainers.setIsDataLoader(true);
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticResources.setDocument(null);
        StaticLayoutContainers.clearContainers();
    }

    @Test
    public void testProcessHeadersAndFooters() {
        initContainers();
        List<List<IObject>> contents = new ArrayList<>();
        List<IObject> page1Contents = new ArrayList<>();
        page1Contents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 30.0, 20.0, 40.0),
            "Header", 10, 30.0)));
        page1Contents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 20.0, 20.0, 30.0),
            "Text", 10, 20.0)));
        page1Contents.add(new TextLine(new TextChunk(new BoundingBox(0, 10.0, 10.0, 20.0, 20.0),
            "Footer1", 10, 10.0)));
        List<IObject> page2Contents = new ArrayList<>();
        page2Contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 30.0, 20.0, 40.0),
            "Header", 10, 30.0)));
        page2Contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 20.0, 20.0, 30.0),
            "Different Text", 10, 20.0)));
        page2Contents.add(new TextLine(new TextChunk(new BoundingBox(1, 10.0, 10.0, 20.0, 20.0),
            "Footer2", 10, 10.0)));
        contents.add(page1Contents);
        contents.add(page2Contents);
        HeaderFooterProcessor.processHeadersAndFooters(contents, false);

        Assertions.assertEquals(3, contents.get(0).size());
        Assertions.assertEquals(3, contents.get(1).size());

        Assertions.assertTrue(contents.get(0).get(0) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.HEADER, ((SemanticHeaderOrFooter) contents.get(0).get(0)).getSemanticType());
        Assertions.assertTrue(contents.get(1).get(0) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.HEADER, ((SemanticHeaderOrFooter) contents.get(1).get(0)).getSemanticType());
        Assertions.assertTrue(contents.get(0).get(2) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.FOOTER, ((SemanticHeaderOrFooter) contents.get(0).get(2)).getSemanticType());
        Assertions.assertTrue(contents.get(1).get(2) instanceof SemanticHeaderOrFooter);
        Assertions.assertEquals(SemanticType.FOOTER, ((SemanticHeaderOrFooter) contents.get(1).get(2)).getSemanticType());
    }

    /**
     * Tests that body text repeated on adjacent pages is not absorbed into the footer.
     * Reproduces #385: pages 19-20 of CERAGEM PDF have identical note text
     * "※ 출수 중 출수 버튼을 터치하면 출수가 정지됩니다." at y=116 above the actual
     * footer at y=34. The note was incorrectly classified as footer because it matched
     * across pages. Page height is 595 (A4-like).
     */
    @Test
    public void testRepeatedBodyTextNotAbsorbedIntoFooter() {
        initContainers();
        // Simulate 4 pages (17-20) with A4-like height (595pt)
        // Page bounding box: [0, 0, 420, 595]
        // Footer line at y=35 (bottom), body note at y=117 (well above footer)
        double pageHeight = 595.0;
        double footerY = 35.0;
        double bodyNoteY = 117.0;

        List<List<IObject>> contents = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            List<IObject> pageContents = new ArrayList<>();
            // Body heading at top
            pageContents.add(new TextLine(new TextChunk(
                new BoundingBox(page, 37.0, pageHeight - 60, 300.0, pageHeight - 30),
                "Section " + (page + 1), 12, pageHeight - 30)));
            // Body paragraph in middle
            pageContents.add(new TextLine(new TextChunk(
                new BoundingBox(page, 37.0, pageHeight / 2, 300.0, pageHeight / 2 + 30),
                "Body content page " + (page + 1), 10, pageHeight / 2 + 30)));

            // Repeated body note — same text on pages 2 and 3 (simulating pages 19-20)
            if (page == 2 || page == 3) {
                pageContents.add(new TextLine(new TextChunk(
                    new BoundingBox(page, 223.0, bodyNoteY, 360.0, bodyNoteY + 18),
                    "※ Repeated note text", 6.5, bodyNoteY + 18)));
            }

            // Actual footer line (repeating pattern across all pages)
            String footerText = (page % 2 == 0)
                ? "CGM BALANCE " + (page + 17)
                : (page + 17) + " CERAGEM BALANCE USER MANUAL";
            pageContents.add(new TextLine(new TextChunk(
                new BoundingBox(page, 37.0, footerY, 280.0, footerY + 9),
                footerText, 7.5, footerY + 9)));

            contents.add(pageContents);
        }

        HeaderFooterProcessor.processHeadersAndFooters(contents, false);

        // Verify: each page should have footer detected
        for (int page = 0; page < 4; page++) {
            List<IObject> pageContent = contents.get(page);
            IObject lastElement = pageContent.get(pageContent.size() - 1);
            Assertions.assertTrue(lastElement instanceof SemanticHeaderOrFooter,
                "Page " + page + ": last element should be footer");
            SemanticHeaderOrFooter footer = (SemanticHeaderOrFooter) lastElement;
            Assertions.assertEquals(SemanticType.FOOTER, footer.getSemanticType());

            // Critical: footer should contain only 1 element (the actual footer line),
            // NOT the repeated body note
            Assertions.assertEquals(1, footer.getContents().size(),
                "Page " + page + ": footer should contain only the footer line, " +
                "not absorb the repeated body note. Got " + footer.getContents().size() + " elements.");
        }

        // Verify: the repeated note text on pages 2-3 should still be in body content
        for (int page = 2; page <= 3; page++) {
            List<IObject> pageContent = contents.get(page);
            boolean foundNote = false;
            for (IObject obj : pageContent) {
                if (!(obj instanceof SemanticHeaderOrFooter) && obj instanceof TextLine) {
                    TextLine line = (TextLine) obj;
                    if (line.getValue().contains("Repeated note")) {
                        foundNote = true;
                        break;
                    }
                }
            }
            Assertions.assertTrue(foundNote,
                "Page " + page + ": repeated note text should remain in body, not be absorbed into footer");
        }
    }

    /**
     * Positive control: two closely spaced footer lines (gap < 30pt) should be
     * grouped into a single footer. Ensures the proximity check does not reject
     * legitimate multi-line footers.
     */
    @Test
    public void testCloseFooterLinesAreGrouped() {
        initContainers();
        List<List<IObject>> contents = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            List<IObject> pageContents = new ArrayList<>();
            // Body text at top
            pageContents.add(new TextLine(new TextChunk(
                new BoundingBox(page, 37.0, 500.0, 300.0, 530.0),
                "Body text page " + (page + 1), 10, 530.0)));

            // Two footer lines close together (11pt gap between nearest edges)
            // Line 1: y=[55, 67]  Line 2: y=[35, 44]  gap = 55-44 = 11pt
            pageContents.add(new TextLine(new TextChunk(
                new BoundingBox(page, 37.0, 55.0, 280.0, 67.0),
                "Copyright 2026", 7.5, 67.0)));
            pageContents.add(new TextLine(new TextChunk(
                new BoundingBox(page, 37.0, 35.0, 280.0, 44.0),
                "Company Footer", 7.5, 44.0)));

            contents.add(pageContents);
        }

        HeaderFooterProcessor.processHeadersAndFooters(contents, false);

        for (int page = 0; page < 3; page++) {
            List<IObject> pageContent = contents.get(page);
            IObject lastElement = pageContent.get(pageContent.size() - 1);
            Assertions.assertTrue(lastElement instanceof SemanticHeaderOrFooter,
                "Page " + page + ": last element should be footer");
            SemanticHeaderOrFooter footer = (SemanticHeaderOrFooter) lastElement;
            Assertions.assertEquals(SemanticType.FOOTER, footer.getSemanticType());
            Assertions.assertEquals(2, footer.getContents().size(),
                "Page " + page + ": footer should contain both close footer lines (gap=11pt < 30pt)");
        }
    }
}

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.autotagging.PagePaints;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.lists.ListItem;
import org.verapdf.wcag.algorithms.entities.lists.PDFList;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The OCR-fallback rewrite shifts the stream positions of the chunks that enrichment put into the
 * page, and finds the chunks that need new text, with its own walk of the content tree
 * ({@code forEachIObject}). Enrichment has a walk of its own ({@code enrichTextStreamInfosRecursive}).
 * If the first misses a container the second enters, a chunk keeps a stale position, or gets no
 * text, only on pages that hold that container. This test builds a page with a paragraph, a table
 * (with a cell that spans two columns) and a list (a line and nested content) and checks that every
 * chunk enrichment placed is reached by the rewrite's walk.
 */
class HybridWalkerParityTest {

    @BeforeEach
    void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
    }

    @Test
    void everyChunkEnrichmentPlacesIsReachedByTheRewriteWalk() throws Exception {
        List<TextChunk> nativeChunks = new ArrayList<>();
        List<IObject> page = new ArrayList<>();

        page.add(paragraphOver(nativeChunks, "Plain paragraph", 72, 700, 200, 712));
        page.add(tableWithAMergedCell(nativeChunks));
        page.add(listWithLineAndNestedContent(nativeChunks));

        Set<Integer> placed = new HashSet<>();
        Method enrich = HybridDocumentProcessor.class.getDeclaredMethod("enrichTextStreamInfosRecursive",
            List.class, List.class, Set.class, PagePaints.class);
        enrich.setAccessible(true);
        enrich.invoke(null, page, nativeChunks, placed, null);

        // The fixture is right only if enrichment took every chunk.
        assertEquals(nativeChunks.size(), placed.size(),
            "enrichment did not place every native chunk, so the fixture does not exercise each container");

        Set<TextChunk> reached = Collections.newSetFromMap(new IdentityHashMap<>());
        Method walk = HybridDocumentProcessor.class.getDeclaredMethod("forEachIObject", List.class, Consumer.class);
        walk.setAccessible(true);
        Consumer<IObject> collect = object -> {
            if (object instanceof TextChunk) {
                reached.add((TextChunk) object);
            }
        };
        walk.invoke(null, page, collect);

        for (TextChunk chunk : nativeChunks) {
            assertTrue(reached.contains(chunk),
                "the rewrite's walk does not reach \"" + chunk.getValue() + "\", which enrichment placed");
        }
    }

    private static SemanticParagraph paragraphOver(List<TextChunk> nativeChunks, String text,
                                                   double left, double bottom, double right, double top) {
        nativeChunks.add(nativeChunk(text, left, bottom, right, top, nativeChunks.size()));
        return backendParagraph(text, left, bottom, right, top);
    }

    /** A 2 x 2 table; the top row is one cell that spans both columns. */
    private static TableBorder tableWithAMergedCell(List<TextChunk> nativeChunks) {
        TableBorder table = new TableBorder(2, 2);
        table.setBoundingBox(new BoundingBox(0, 72, 560, 300, 640));

        TableBorderRow top = new TableBorderRow(0, 2, 0L);
        top.setBoundingBox(new BoundingBox(0, 72, 600, 300, 640));
        TableBorderCell merged = new TableBorderCell(0, 0, 1, 2, 0L);
        merged.setBoundingBox(new BoundingBox(0, 72, 600, 300, 640));
        top.getCells()[0] = merged;
        top.getCells()[1] = merged;
        merged.addContentObject(paragraphOver(nativeChunks, "Merged cell text", 80, 610, 200, 622));
        table.getRows()[0] = top;

        TableBorderRow bottom = new TableBorderRow(0, 2, 0L);
        bottom.setBoundingBox(new BoundingBox(0, 72, 560, 300, 600));
        TableBorderCell left = new TableBorderCell(1, 0, 1, 1, 0L);
        left.setBoundingBox(new BoundingBox(0, 72, 560, 180, 600));
        TableBorderCell right = new TableBorderCell(1, 1, 1, 1, 0L);
        right.setBoundingBox(new BoundingBox(0, 180, 560, 300, 600));
        bottom.getCells()[0] = left;
        bottom.getCells()[1] = right;
        left.addContentObject(paragraphOver(nativeChunks, "Lower left cell", 80, 570, 170, 582));
        table.getRows()[1] = bottom;
        return table;
    }

    /** A list whose one item has a line of its own and a paragraph of nested content. */
    private static PDFList listWithLineAndNestedContent(List<TextChunk> nativeChunks) {
        ListItem item = new ListItem(new BoundingBox(), 1L);
        nativeChunks.add(nativeChunk("List item line", 72, 500, 200, 512, nativeChunks.size()));
        item.add(new TextLine(backendChunk("List item line", 72, 500, 200, 512)));
        item.setContents(new ArrayList<>(List.of(
            paragraphOver(nativeChunks, "Nested item paragraph", 90, 480, 220, 492))));
        PDFList list = new PDFList();
        list.add(item);
        return list;
    }

    private static TextChunk nativeChunk(String text, double left, double bottom, double right, double top,
                                         int operatorIndex) {
        TextChunk chunk = backendChunk(text, left, bottom, right, top);
        chunk.getStreamInfos().add(new StreamInfo(operatorIndex, null, 0, text.length()));
        return chunk;
    }

    private static TextChunk backendChunk(String text, double left, double bottom, double right, double top) {
        TextChunk chunk = new TextChunk(new BoundingBox(0, left, bottom, right, top), text, top - bottom, bottom);
        chunk.adjustSymbolEndsToBoundingBox(null);
        return chunk;
    }

    private static SemanticParagraph backendParagraph(String text, double left, double bottom, double right,
                                                      double top) {
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(backendChunk(text, left, bottom, right, top)));
        return paragraph;
    }
}

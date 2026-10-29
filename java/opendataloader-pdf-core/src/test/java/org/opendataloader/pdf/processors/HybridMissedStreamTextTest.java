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
import org.opendataloader.pdf.entities.SemanticPicture;
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticHeaderOrFooter;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;

import org.verapdf.wcag.algorithms.entities.SemanticTextNode;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.enums.SemanticType;
import org.verapdf.wcag.algorithms.entities.content.TextColumn;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;
import org.verapdf.wcag.algorithms.semanticalgorithms.utils.StreamInfo;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Stream text the backend returned no element for (PDFDLOSP-41).
 *
 * <p>Docling under --force-ocr, with an OCR engine that cannot read Hangul, returned no
 * text item for a Korean paragraph. Its chunks matched no backend element, were left out
 * of the page, and the tagger wrote them as /Artifact.
 */
class HybridMissedStreamTextTest {

    @BeforeEach
    void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
    }

    private static TextChunk streamChunk(String text, double left, double bottom, double right, double top,
                                         int operatorIndex) {
        TextChunk chunk = new TextChunk(new BoundingBox(0, left, bottom, right, top), text, top - bottom, bottom);
        chunk.adjustSymbolEndsToBoundingBox(null);
        chunk.getStreamInfos().add(new StreamInfo(operatorIndex, null, 0, text.length()));
        return chunk;
    }

    private static SemanticParagraph backendParagraph(String text, double left, double bottom, double right,
                                                      double top) {
        TextChunk chunk = new TextChunk(new BoundingBox(0, left, bottom, right, top), text, top - bottom, bottom);
        chunk.adjustSymbolEndsToBoundingBox(null);
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(chunk));
        return paragraph;
    }

    private static List<IObject> enrich(List<IObject> backendPage, List<IObject> javaPage) throws Exception {
        Map<Integer, List<IObject>> backendResults = new HashMap<>();
        backendResults.put(0, backendPage);
        Map<Integer, List<IObject>> filteredContents = new HashMap<>();
        filteredContents.put(0, javaPage);
        Method method = HybridDocumentProcessor.class.getDeclaredMethod("enrichBackendResults",
            Map.class, Map.class, HybridConfig.class, Map.class);
        method.setAccessible(true);
        method.invoke(null, backendResults, filteredContents, new HybridConfig(), new IdentityHashMap<>());
        return backendResults.get(0);
    }

    private static List<TextChunk> chunksOf(SemanticTextNode node) {
        List<TextChunk> chunks = new ArrayList<>();
        for (TextColumn column : node.getColumns()) {
            for (TextLine line : column.getLines()) {
                chunks.addAll(line.getTextChunks());
            }
        }
        return chunks;
    }

    @Test
    void streamTextTheBackendLeftOutBecomesAParagraph() throws Exception {
        TextChunk english = streamChunk("Heading", 72, 700, 200, 712, 1);
        TextChunk korean = streamChunk("피켓팅", 72, 400, 140, 412, 2);
        List<IObject> backendPage = new ArrayList<>(List.of(backendParagraph("Heading", 72, 700, 200, 712)));

        List<IObject> page = enrich(backendPage, new ArrayList<>(List.of(english, korean)));

        assertEquals(2, page.size(), "the left-out text is on the page");
        List<TextChunk> recovered = chunksOf((SemanticTextNode) page.get(1));
        assertEquals(1, recovered.size());
        assertSame(korean, recovered.get(0), "the stream chunk itself, carrying its StreamInfo");
    }

    @Test
    void streamTextInsideAnElementTheBackendReturnedStaysThere() throws Exception {
        TextChunk label = streamChunk("축", 120, 320, 130, 330, 3);
        SemanticPicture chart = new SemanticPicture(new BoundingBox(0, 100, 300, 300, 500), 1, "chart");
        List<IObject> backendPage = new ArrayList<>(List.of(chart));

        List<IObject> page = enrich(backendPage, new ArrayList<>(List.of(label)));

        assertEquals(1, page.size(), "text the backend drew a picture around is not made a paragraph");
    }

    @Test
    void streamTextInsideAPageHeaderTheBackendReturnedStaysThere() throws Exception {
        TextChunk running = streamChunk("국회도서관", 72, 780, 160, 790, 4);
        SemanticHeaderOrFooter header = new SemanticHeaderOrFooter(SemanticType.HEADER);
        header.addContents(Collections.singletonList(backendParagraph("국회도서관", 72, 780, 160, 790)));
        List<IObject> backendPage = new ArrayList<>(List.of(header));

        List<IObject> page = enrich(backendPage, new ArrayList<>(List.of(running)));

        assertEquals(1, page.size(), "a running header is not made a body paragraph");
    }
}

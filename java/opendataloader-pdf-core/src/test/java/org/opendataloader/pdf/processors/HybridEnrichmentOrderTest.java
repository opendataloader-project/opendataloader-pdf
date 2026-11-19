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
import org.opendataloader.pdf.hybrid.HybridConfig;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.SemanticParagraph;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.content.TextLine;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * If enriching a later page fails, the caller falls back to the Java path, which has to find the
 * pages' content streams as they were. So the OCR-fallback text is added only after every page has
 * been enriched. Page 0 holds OCR-only text (it would be written into its content stream), page 1 a
 * table whose row is missing, which makes its enrichment throw. The failure that comes out must be
 * the one from page 1, and the OCR-fallback writing must never have started: it catches its own
 * failures and logs a warning, so a warning in the log means page 0 was rewritten too early.
 */
class HybridEnrichmentOrderTest {

    @BeforeEach
    void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
    }

    @Test
    void aLaterPagesFailedEnrichmentIsReachedBeforeAnyPageIsRewritten() throws Exception {
        TextChunk ocrOnly = new TextChunk(new BoundingBox(0, 72, 700, 200, 712), "OCR only text", 12, 700);
        ocrOnly.adjustSymbolEndsToBoundingBox(null);
        SemanticParagraph paragraph = new SemanticParagraph();
        paragraph.add(new TextLine(ocrOnly));

        TableBorder tableWithoutItsRow = new TableBorder(1, 1);
        tableWithoutItsRow.setBoundingBox(new BoundingBox(1, 72, 500, 300, 600));

        Map<Integer, List<IObject>> backendResults = new HashMap<>();
        backendResults.put(0, new ArrayList<>(List.of(paragraph)));
        backendResults.put(1, new ArrayList<>(List.of(tableWithoutItsRow)));
        Map<Integer, List<IObject>> filteredContents = new HashMap<>();
        filteredContents.put(0, new ArrayList<>());
        filteredContents.put(1, new ArrayList<>());

        List<LogRecord> warnings = new ArrayList<>();
        Handler collector = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    warnings.add(record);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        Logger logger = Logger.getLogger(HybridDocumentProcessor.class.getCanonicalName());
        logger.addHandler(collector);

        Method enrich = HybridDocumentProcessor.class.getDeclaredMethod("enrichBackendResults",
            Map.class, Map.class, HybridConfig.class, Map.class);
        enrich.setAccessible(true);
        InvocationTargetException thrown;
        try {
            thrown = assertThrows(InvocationTargetException.class,
                () -> enrich.invoke(null, backendResults, filteredContents, new HybridConfig(), new IdentityHashMap<>()));
        } finally {
            logger.removeHandler(collector);
        }

        StringBuilder frames = new StringBuilder();
        for (StackTraceElement frame : thrown.getCause().getStackTrace()) {
            frames.append(frame.getMethodName()).append('\n');
        }
        assertTrue(frames.toString().contains("enrichTextStreamInfosRecursive"),
            "the failure is not the later page's enrichment:\n" + thrown.getCause() + "\n" + frames);
        assertFalse(warnings.stream().anyMatch(r -> r.getMessage().contains("synthesize invisible text")),
            "an earlier page was rewritten before the later page was enriched: " + warnings);
    }
}

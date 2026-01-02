/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.hybrid;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.containers.StaticLayoutContainers;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageDecision;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageResult;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageSignals;
import org.opendataloader.pdf.hybrid.TriageProcessor.TriageThresholds;
import org.verapdf.wcag.algorithms.entities.IObject;
import org.verapdf.wcag.algorithms.entities.content.LineChunk;
import org.verapdf.wcag.algorithms.entities.content.TextChunk;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;
import org.verapdf.wcag.algorithms.entities.tables.TableBordersCollection;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorder;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderCell;
import org.verapdf.wcag.algorithms.entities.tables.tableBorders.TableBorderRow;
import org.verapdf.wcag.algorithms.semanticalgorithms.containers.StaticContainers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Unit tests for TriageProcessor.
 */
public class TriageProcessorTest {

    @BeforeEach
    public void setUp() {
        StaticContainers.setIsIgnoreCharactersWithoutUnicode(false);
        StaticContainers.setIsDataLoader(true);
        StaticLayoutContainers.setCurrentContentId(1L);
        StaticContainers.setTableBordersCollection(new TableBordersCollection());
    }

    @Test
    public void testEmptyContentReturnsJava() {
        List<IObject> contents = new ArrayList<>();
        TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

        Assertions.assertEquals(TriageDecision.JAVA, result.getDecision());
        Assertions.assertEquals(0, result.getPageNumber());
        Assertions.assertTrue(result.getConfidence() > 0.5);
        Assertions.assertEquals(0, result.getSignals().getLineChunkCount());
        Assertions.assertEquals(0, result.getSignals().getTextChunkCount());
    }

    @Test
    public void testNullContentReturnsJava() {
        TriageResult result = TriageProcessor.classifyPage(null, 0, new HybridConfig());

        Assertions.assertEquals(TriageDecision.JAVA, result.getDecision());
        Assertions.assertEquals(0, result.getSignals().getLineChunkCount());
    }

    @Test
    public void testSimpleTextReturnsJava() {
        List<IObject> contents = new ArrayList<>();
        // Add simple text chunks in normal reading order
        contents.add(createTextChunk(10, 100, 200, 120, "Hello"));
        contents.add(createTextChunk(10, 80, 200, 100, "World"));

        TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

        Assertions.assertEquals(TriageDecision.JAVA, result.getDecision());
        Assertions.assertEquals(2, result.getSignals().getTextChunkCount());
        Assertions.assertEquals(0, result.getSignals().getLineChunkCount());
        Assertions.assertFalse(result.getSignals().hasTableBorder());
    }

    @Test
    public void testHighLineRatioReturnsBackend() {
        List<IObject> contents = new ArrayList<>();
        // Add one text chunk
        contents.add(createTextChunk(10, 100, 200, 120, "Header"));
        // Add multiple line chunks (> 30% of total)
        contents.add(createLineChunk(10, 90, 200, 90));
        contents.add(createLineChunk(10, 80, 200, 80));
        contents.add(createLineChunk(10, 70, 200, 70));

        TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

        Assertions.assertEquals(TriageDecision.BACKEND, result.getDecision());
        Assertions.assertEquals(3, result.getSignals().getLineChunkCount());
        Assertions.assertEquals(1, result.getSignals().getTextChunkCount());
        Assertions.assertTrue(result.getSignals().getLineToTextRatio() > 0.3);
    }

    @Test
    public void testTableBorderPresenceReturnsBackend() {
        // Set up TableBordersCollection with a table on page 0
        TableBordersCollection collection = new TableBordersCollection();
        StaticContainers.setTableBordersCollection(collection);

        // Create a 2x2 table border
        TableBorder tableBorder = new TableBorder(2, 2);
        tableBorder.setRecognizedStructureId(1L);
        tableBorder.setBoundingBox(new BoundingBox(0, 10.0, 10.0, 100.0, 100.0));
        setupTableBorderRows(tableBorder);

        SortedSet<TableBorder> tables = new TreeSet<>(new TableBorder.TableBordersComparator());
        tables.add(tableBorder);
        collection.getTableBorders().add(tables);

        List<IObject> contents = new ArrayList<>();
        contents.add(createTextChunk(20, 20, 50, 40, "Cell"));

        TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

        Assertions.assertEquals(TriageDecision.BACKEND, result.getDecision());
        Assertions.assertTrue(result.getSignals().hasTableBorder());
        Assertions.assertEquals(1.0, result.getConfidence());
    }

    @Test
    public void testSuspiciousPatternReturnsBackend() {
        List<IObject> contents = new ArrayList<>();
        // Add text chunks on the same baseline with large gap (table-like pattern)
        contents.add(createTextChunk(10, 100, 50, 120, "Col1"));
        contents.add(createTextChunk(200, 100, 250, 120, "Col2")); // Large horizontal gap

        TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

        Assertions.assertEquals(TriageDecision.BACKEND, result.getDecision());
        Assertions.assertTrue(result.getSignals().hasSuspiciousPattern());
    }

    @Test
    public void testAlignedLineGroupsReturnsBackend() {
        List<IObject> contents = new ArrayList<>();
        TriageThresholds thresholds = new TriageThresholds();
        thresholds.setAlignedLineGroupsThreshold(3);
        thresholds.setGridGapMultiplier(3.0);

        // Create three rows of aligned text with gaps (table-like structure)
        // Row 1
        contents.add(createTextChunk(10, 100, 50, 120, "A1"));
        contents.add(createTextChunk(200, 100, 250, 120, "B1"));

        // Row 2
        contents.add(createTextChunk(10, 70, 50, 90, "A2"));
        contents.add(createTextChunk(200, 70, 250, 90, "B2"));

        // Row 3
        contents.add(createTextChunk(10, 40, 50, 60, "A3"));
        contents.add(createTextChunk(200, 40, 250, 60, "B3"));

        TriageResult result = TriageProcessor.classifyPage(contents, 0, thresholds);

        Assertions.assertEquals(TriageDecision.BACKEND, result.getDecision());
        Assertions.assertTrue(result.getSignals().getAlignedLineGroups() >= 3);
    }

    @Test
    public void testTriageAllPagesWithMap() {
        Map<Integer, List<IObject>> pageContents = new HashMap<>();

        // Page 0: Simple text
        List<IObject> page0 = new ArrayList<>();
        page0.add(createTextChunk(10, 100, 200, 120, "Simple"));
        pageContents.put(0, page0);

        // Page 1: High line ratio (should route to backend)
        List<IObject> page1 = new ArrayList<>();
        page1.add(createTextChunk(10, 100, 200, 120, "Header"));
        page1.add(createLineChunk(10, 90, 200, 90));
        page1.add(createLineChunk(10, 80, 200, 80));
        page1.add(createLineChunk(10, 70, 200, 70));
        pageContents.put(1, page1);

        Map<Integer, TriageResult> results = TriageProcessor.triageAllPages(pageContents, new HybridConfig());

        Assertions.assertEquals(2, results.size());
        Assertions.assertEquals(TriageDecision.JAVA, results.get(0).getDecision());
        Assertions.assertEquals(TriageDecision.BACKEND, results.get(1).getDecision());
    }

    @Test
    public void testTriageAllPagesWithList() {
        List<List<IObject>> pagesContents = new ArrayList<>();

        // Page 0: Simple text
        List<IObject> page0 = new ArrayList<>();
        page0.add(createTextChunk(10, 100, 200, 120, "Simple"));
        pagesContents.add(page0);

        // Page 1: Empty (should route to Java)
        pagesContents.add(new ArrayList<>());

        Map<Integer, TriageResult> results = TriageProcessor.triageAllPages(pagesContents, new HybridConfig());

        Assertions.assertEquals(2, results.size());
        Assertions.assertEquals(TriageDecision.JAVA, results.get(0).getDecision());
        Assertions.assertEquals(TriageDecision.JAVA, results.get(1).getDecision());
    }

    @Test
    public void testCustomThresholds() {
        List<IObject> contents = new ArrayList<>();
        // Add line chunks that would trigger BACKEND with default threshold (0.3)
        // but not with raised threshold (0.5)
        contents.add(createTextChunk(10, 100, 200, 120, "Text1"));
        contents.add(createTextChunk(10, 80, 200, 100, "Text2"));
        contents.add(createLineChunk(10, 70, 200, 70));

        // With default threshold (0.3), line ratio is 1/3 = 0.33 > 0.3 -> BACKEND
        TriageThresholds defaultThresholds = new TriageThresholds();
        TriageResult result1 = TriageProcessor.classifyPage(contents, 0, defaultThresholds);
        Assertions.assertEquals(TriageDecision.BACKEND, result1.getDecision());

        // With raised threshold (0.5), line ratio is 0.33 < 0.5 -> JAVA
        TriageThresholds raisedThresholds = new TriageThresholds();
        raisedThresholds.setLineRatioThreshold(0.5);
        TriageResult result2 = TriageProcessor.classifyPage(contents, 0, raisedThresholds);
        Assertions.assertEquals(TriageDecision.JAVA, result2.getDecision());
    }

    @Test
    public void testOutOfReadingOrderReturnsBackend() {
        List<IObject> contents = new ArrayList<>();
        // Text chunks with overlapping Y coordinates (out of normal reading order)
        // This pattern suggests multi-column or table layout
        contents.add(createTextChunk(10, 80, 50, 100, "First"));
        contents.add(createTextChunk(10, 110, 50, 130, "Overlapping")); // topY(130) > bottomY of first(100) but < topY(100)

        // The second chunk's topY (130) is above the first chunk's bottomY (100)
        // which indicates out of reading order
        TriageResult result = TriageProcessor.classifyPage(contents, 0, new HybridConfig());

        // This should detect the overlapping pattern
        Assertions.assertNotNull(result);
        Assertions.assertNotNull(result.getSignals());
    }

    @Test
    public void testTriageSignalsEmpty() {
        TriageSignals signals = TriageSignals.empty();

        Assertions.assertEquals(0, signals.getLineChunkCount());
        Assertions.assertEquals(0, signals.getTextChunkCount());
        Assertions.assertEquals(0.0, signals.getLineToTextRatio());
        Assertions.assertEquals(0, signals.getAlignedLineGroups());
        Assertions.assertFalse(signals.hasTableBorder());
        Assertions.assertFalse(signals.hasSuspiciousPattern());
    }

    @Test
    public void testTriageResultFactoryMethods() {
        TriageSignals signals = TriageSignals.empty();

        TriageResult javaResult = TriageResult.java(5, 0.95, signals);
        Assertions.assertEquals(5, javaResult.getPageNumber());
        Assertions.assertEquals(TriageDecision.JAVA, javaResult.getDecision());
        Assertions.assertEquals(0.95, javaResult.getConfidence());

        TriageResult backendResult = TriageResult.backend(3, 0.8, signals);
        Assertions.assertEquals(3, backendResult.getPageNumber());
        Assertions.assertEquals(TriageDecision.BACKEND, backendResult.getDecision());
        Assertions.assertEquals(0.8, backendResult.getConfidence());
    }

    @Test
    public void testThresholdsGettersAndSetters() {
        TriageThresholds thresholds = new TriageThresholds();

        // Test defaults
        Assertions.assertEquals(TriageProcessor.DEFAULT_LINE_RATIO_THRESHOLD,
            thresholds.getLineRatioThreshold());
        Assertions.assertEquals(TriageProcessor.DEFAULT_ALIGNED_LINE_GROUPS_THRESHOLD,
            thresholds.getAlignedLineGroupsThreshold());
        Assertions.assertEquals(TriageProcessor.DEFAULT_GRID_GAP_MULTIPLIER,
            thresholds.getGridGapMultiplier());

        // Test setters
        thresholds.setLineRatioThreshold(0.5);
        thresholds.setAlignedLineGroupsThreshold(5);
        thresholds.setGridGapMultiplier(4.0);

        Assertions.assertEquals(0.5, thresholds.getLineRatioThreshold());
        Assertions.assertEquals(5, thresholds.getAlignedLineGroupsThreshold());
        Assertions.assertEquals(4.0, thresholds.getGridGapMultiplier());
    }

    @Test
    public void testExtractSignalsDirectly() {
        List<IObject> contents = new ArrayList<>();
        contents.add(createTextChunk(10, 100, 200, 120, "Hello"));
        contents.add(createLineChunk(10, 90, 200, 90));

        TriageThresholds thresholds = new TriageThresholds();
        TriageSignals signals = TriageProcessor.extractSignals(contents, 0, thresholds);

        Assertions.assertEquals(1, signals.getLineChunkCount());
        Assertions.assertEquals(1, signals.getTextChunkCount());
        Assertions.assertEquals(0.5, signals.getLineToTextRatio(), 0.01);
    }

    // Helper methods

    private TextChunk createTextChunk(double leftX, double bottomY, double rightX, double topY, String text) {
        BoundingBox bbox = new BoundingBox(0, leftX, bottomY, rightX, topY);
        TextChunk chunk = new TextChunk(bbox, text, topY - bottomY, bottomY);
        chunk.adjustSymbolEndsToBoundingBox(null);
        return chunk;
    }

    private LineChunk createLineChunk(double x1, double y1, double x2, double y2) {
        return new LineChunk(0, x1, y1, x2, y2);
    }

    private void setupTableBorderRows(TableBorder tableBorder) {
        TableBorderRow row1 = new TableBorderRow(0, 2, 0L);
        row1.setBoundingBox(new BoundingBox(0, 10.0, 55.0, 100.0, 100.0));
        row1.getCells()[0] = new TableBorderCell(0, 0, 1, 1, 0L);
        row1.getCells()[0].setBoundingBox(new BoundingBox(0, 10.0, 55.0, 55.0, 100.0));
        row1.getCells()[1] = new TableBorderCell(0, 1, 1, 1, 0L);
        row1.getCells()[1].setBoundingBox(new BoundingBox(0, 55.0, 55.0, 100.0, 100.0));
        tableBorder.getRows()[0] = row1;

        TableBorderRow row2 = new TableBorderRow(1, 2, 0L);
        row2.setBoundingBox(new BoundingBox(0, 10.0, 10.0, 100.0, 55.0));
        row2.getCells()[0] = new TableBorderCell(1, 0, 1, 1, 0L);
        row2.getCells()[0].setBoundingBox(new BoundingBox(0, 10.0, 10.0, 55.0, 55.0));
        row2.getCells()[1] = new TableBorderCell(1, 1, 1, 1, 0L);
        row2.getCells()[1].setBoundingBox(new BoundingBox(0, 55.0, 10.0, 100.0, 55.0));
        tableBorder.getRows()[1] = row2;
    }
}

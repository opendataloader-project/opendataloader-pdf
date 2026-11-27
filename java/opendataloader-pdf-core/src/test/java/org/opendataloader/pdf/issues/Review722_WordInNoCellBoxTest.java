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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import mockwebserver3.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * #722: a word of a table that lies in the box of no cell.
 *
 * <p>docling-fast gives each cell the box of the text it read there, and that box can miss a
 * word of the cell. Here the boxes of the label column of the wide table of
 * {@link Review722_HybridNodeTextTest} end after 40pt: "area" and "rate" are more than 5pt
 * outside them, in the table and in no other element. Each goes to the cell whose slot in
 * the grid holds it, as before the cell boxes were used, so that it stays in the output and
 * in the structure tree of the tagged PDF.
 */
class Review722_WordInNoCellBoxTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        HybridClientFactory.shutdown();
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            if (server != null) {
                server.close();
            }
        } finally {
            OpenDataLoaderPDF.shutdown();
            HybridClientFactory.shutdown();
        }
    }

    @Test
    void aWordOutsideTheBoxOfItsCellStaysInTheTable(@TempDir Path dir) throws Exception {
        String[][] cells = Review722_HybridNodeTextTest.T1;
        float[] cols = Review722_HybridNodeTextTest.T1_COLS;
        float top = Review722_HybridNodeTextTest.T1_TOP;
        Path pdf = Review722_HybridNodeTextTest.writeTable(dir.resolve("tight-boxes.pdf"), cells, cols, top, false);
        Review722_HybridNodeTextTest.enqueue(server, withNarrowLabelBoxes(
            Review722_HybridNodeTextTest.tableResponse(cells, cols, top), 40));

        Path md = Review722_HybridNodeTextTest.convert(server, dir, pdf, true);

        assertEquals(Review722_HybridNodeTextTest.toLists(cells), Review722_HybridNodeTextTest.markdownCells(md),
            "Markdown table cells:\n" + Files.readString(md));
        Review722_HybridNodeTextTest.Tagged tagged = Review722_HybridNodeTextTest.readTagged(md);
        assertEquals(List.of(), tagged.outside, "table text outside the structure tree; TD/TH: " + tagged.cells);
        assertEquals(Review722_HybridNodeTextTest.flat(cells), tagged.cells, "text of each TD/TH from its MCIDs");
    }

    /** Cuts the box of each cell of the first column to its left {@code width} points. */
    private static String withNarrowLabelBoxes(String response, double width) throws IOException {
        JsonNode body = MAPPER.readTree(response);
        for (JsonNode table : body.path("document").path("json_content").path("tables")) {
            for (JsonNode cell : table.path("data").path("table_cells")) {
                if (cell.path("start_col_offset_idx").asInt() == 0) {
                    ObjectNode bbox = (ObjectNode) cell.get("bbox");
                    bbox.put("r", bbox.get("l").asDouble() + width);
                }
            }
        }
        return MAPPER.writeValueAsString(body);
    }
}

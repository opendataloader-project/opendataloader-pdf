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
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.OpenDataLoaderPDF;
import org.opendataloader.pdf.hybrid.HybridClientFactory;
import org.opendataloader.pdf.processors.DocumentProcessor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review tests for #722: the text of a backend node in hybrid mode (docling-fast).
 *
 * <p>Each sample is a synthetic PDF, Helvetica, one text-show operator per cell or line. The
 * backend response is what docling-fast returns for it: each table cell has its text and its own
 * box ({@code table_cells[].bbox}, TOPLEFT origin), each text item its {@code prov} box. The
 * expected output is the text that the PDF draws in each node, each word once, in every format.
 *
 * <p>The tests need no backend and no network: each one builds its PDF and the docling-fast response,
 * and serves the response with MockWebServer. To run them, put this file in
 * {@code java/opendataloader-pdf-core/src/test/java/org/opendataloader/pdf/issues/} and run, in
 * {@code java/}: {@code mvn -pl opendataloader-pdf-core -am test -Dtest=Review722_HybridNodeTextTest}.
 * The tagged-PDF checks read which marked content the structure tree references.
 */
class Review722_HybridNodeTextTest {

    static final float PAGE_H = PDRectangle.LETTER.getHeight();
    static final float FONT_SIZE = 10;
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

    // ---- Tables. The expected value of each cell is the text that the PDF draws in it. ----

    /** Wide label column, three narrow number columns, numbers right-aligned. */
    static final float[] T1_COLS = {72, 222, 272, 322, 372};
    static final float T1_TOP = 700;
    static final String[][] T1 = {
        {"Item", "2019", "2020", "2021"},
        {"Production area", "1,649", "1,652", "1,810"},
        {"Yield rate", "12.5%", "13.1%", "14.0%"},
    };

    /** Narrow number column next to the label column, numbers left-aligned. */
    static final float[] T2_COLS = {72, 152, 212, 372};
    static final float T2_TOP = 600;
    static final String[][] T2 = {
        {"Item", "Value", "Note"},
        {"Total area", "1,649", "see note"},
    };

    /** As T2; the label "Items" is as long as the value next to it. */
    static final String[][] T3 = {
        {"Items", "2019", "Note"},
        {"Total area", "1,649", "see note"},
    };

    static final float ROW_H = 20;

    @Test
    void eachNumberStaysInItsOwnColumn(@TempDir Path dir) throws Exception {
        Path pdf = writeTable(dir.resolve("similar-neighbour.pdf"), T1, T1_COLS, T1_TOP, false);
        enqueue(server, tableResponse(T1, T1_COLS, T1_TOP));
        assertEquals(toLists(T1), markdownCells(convert(server, dir, pdf, false)),
            "Markdown table cells (expected: the text drawn in each cell)");
    }

    @Test
    void aHeaderCellDoesNotTakeALongerNeighbour(@TempDir Path dir) throws Exception {
        Path pdf = writeTable(dir.resolve("header-neighbour.pdf"), T2, T2_COLS, T2_TOP, true);
        enqueue(server, tableResponse(T2, T2_COLS, T2_TOP));
        List<List<String>> rows = markdownCells(convert(server, dir, pdf, false));
        assertEquals(List.of(T2[0]), rows.isEmpty() ? List.of() : rows.get(0),
            "the header row in Markdown (expected: the text drawn in each cell): " + rows);
    }

    @Test
    void aLabelCellDoesNotTakeTheNumberNextToIt(@TempDir Path dir) throws Exception {
        Path pdf = writeTable(dir.resolve("shorter-neighbour.pdf"), T2, T2_COLS, T2_TOP, true);
        enqueue(server, tableResponse(T2, T2_COLS, T2_TOP));
        List<List<String>> rows = markdownCells(convert(server, dir, pdf, false));
        assertEquals(List.of(T2[1]), rows.size() < 2 ? List.of() : rows.get(1),
            "the body row in Markdown (expected: the text drawn in each cell): " + rows);
    }

    @Test
    void aLabelCellAsLongAsItsNeighbourKeepsOnlyItsOwnText(@TempDir Path dir) throws Exception {
        Path pdf = writeTable(dir.resolve("equal-neighbour.pdf"), T3, T2_COLS, T2_TOP, true);
        enqueue(server, tableResponse(T3, T2_COLS, T2_TOP));
        assertEquals(toLists(T3), markdownCells(convert(server, dir, pdf, false)),
            "Markdown table cells (expected: the text drawn in each cell)");
    }

    @Test
    void noTextOfTheTableLeavesTheStructureTree(@TempDir Path dir) throws Exception {
        Path pdf = writeTable(dir.resolve("tagged-cells.pdf"), T1, T1_COLS, T1_TOP, false);
        enqueue(server, tableResponse(T1, T1_COLS, T1_TOP));
        Tagged tagged = readTagged(convert(server, dir, pdf, true));
        assertEquals(List.of(), tagged.outside, "table text outside the structure tree; TD/TH: " + tagged.cells);
    }

    @Test
    void noTextOfTheShortTableLeavesTheStructureTree(@TempDir Path dir) throws Exception {
        Path pdf = writeTable(dir.resolve("tagged-short.pdf"), T2, T2_COLS, T2_TOP, true);
        enqueue(server, tableResponse(T2, T2_COLS, T2_TOP));
        Tagged tagged = readTagged(convert(server, dir, pdf, true));
        assertEquals(List.of(), tagged.outside, "table text outside the structure tree; TD/TH: " + tagged.cells);
    }

    @Test
    void eachTaggedCellHoldsTheTextOfItsOwnCell(@TempDir Path dir) throws Exception {
        Path pdf = writeTable(dir.resolve("tagged-own.pdf"), T1, T1_COLS, T1_TOP, false);
        enqueue(server, tableResponse(T1, T1_COLS, T1_TOP));
        Tagged tagged = readTagged(convert(server, dir, pdf, true));
        assertEquals(flat(T1), tagged.cells, "text of each TD/TH from its MCIDs; outside the tree: " + tagged.outside);
    }

    // ---- Text outside tables ----

    static final String[][] LIST = {{"1.", "Yes"}, {"2.", "No"}, {"3.", "Maybe"}};
    static final String INTRO = "The answers in the form are these three values:";
    static final float[] LIST_BASELINES = {500, 484, 468};

    @Test
    void shortNumberedListItemsKeepTheirNumbers(@TempDir Path dir) throws Exception {
        Path pdf = writeShortList(dir.resolve("short-list.pdf"));
        enqueue(server, listResponse());
        Path md = convert(server, dir, pdf, true);
        String markdown = Files.readString(md);
        assertEquals(List.of(List.of("1 Yes", "2 No", "3 Maybe")), numberedLists(markdown),
            "numbered lists as a CommonMark reader shows them:\n" + markdown);
        assertEquals(List.of(), readTagged(md).outside, "list text outside the structure tree");
    }

    static final String HEADING = "Results";
    static final String LINE1 = "Yield rose in each region.";
    static final String LINE2 = "The new irrigation plan explains most of the change this year.";

    @Test
    void noWordOfTheParagraphBelowAHeadingIsLost(@TempDir Path dir) throws Exception {
        Path pdf = writeHeadingParagraph(dir.resolve("heading-paragraph.pdf"));
        enqueue(server, headingParagraphResponse());
        Path md = convert(server, dir, pdf, true);
        String markdown = Files.readString(md);
        for (String word : (HEADING + " " + LINE1 + " " + LINE2).split(" ")) {
            assertEquals(1, count(markdown, word), "the word '" + word + "' once in Markdown:\n" + markdown);
        }
        assertEquals(List.of(), readTagged(md).outside, "text outside the structure tree");
    }

    @Test
    void aHeadingHoldsOnlyItsOwnWords(@TempDir Path dir) throws Exception {
        Path pdf = writeHeadingParagraph(dir.resolve("heading-own.pdf"));
        enqueue(server, headingParagraphResponse());
        String markdown = Files.readString(convert(server, dir, pdf, false));
        List<String> headings = new ArrayList<>();
        StringBuilder body = new StringBuilder();
        for (String line : markdown.split("\n")) {
            String t = line.trim();
            if (t.startsWith("#")) {
                headings.add(t.replaceFirst("^#+\\s*", ""));
            } else if (!t.isEmpty()) {
                body.append(body.length() > 0 ? " " : "").append(t);
            }
        }
        assertEquals(List.of(HEADING), headings, "headings in Markdown:\n" + markdown);
        assertEquals(LINE1 + " " + LINE2, body.toString(), "the paragraph in Markdown:\n" + markdown);
    }

    @Test
    void everyTextElementInJsonKeepsItsFont(@TempDir Path dir) throws Exception {
        Path pdf = writeHeadingParagraph(dir.resolve("font.pdf"));
        enqueue(server, headingParagraphResponse());
        Path md = convert(server, dir, pdf, false);
        JsonNode json = MAPPER.readTree(md.resolveSibling("font.json").toFile());
        List<String> missing = new ArrayList<>();
        withoutFont(json, missing);
        assertEquals(List.of(), missing, "JSON text elements without a font name or a text color (schema.json textProperties)");
    }

    // ---- Samples ----

    static Path writeTable(Path file, String[][] cells, float[] cols, float top, boolean leftNumbers)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float bottom = top - ROW_H * cells.length;
                for (int r = 0; r <= cells.length; r++) {
                    cs.moveTo(cols[0], top - r * ROW_H);
                    cs.lineTo(cols[cols.length - 1], top - r * ROW_H);
                }
                for (float x : cols) {
                    cs.moveTo(x, top);
                    cs.lineTo(x, bottom);
                }
                cs.stroke();
                for (int r = 0; r < cells.length; r++) {
                    float baseline = top - r * ROW_H - 14;
                    for (int c = 0; c < cells[r].length; c++) {
                        String s = cells[r][c];
                        float w = font.getStringWidth(s) / 1000 * FONT_SIZE;
                        float x;
                        if (c == 0 || leftNumbers && c == 1) {
                            x = cols[c] + 4;
                        } else if (leftNumbers) {
                            x = (cols[c] + cols[c + 1]) / 2 - w / 2;
                        } else {
                            x = cols[c + 1] - 4 - w;
                        }
                        show(cs, font, FONT_SIZE, x, baseline, s);
                    }
                }
            }
            save(doc, file);
        }
        return file;
    }

    static Path writeShortList(Path file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                show(cs, font, FONT_SIZE, 72, 530, INTRO);
                for (int i = 0; i < LIST.length; i++) {
                    show(cs, font, FONT_SIZE, 72, LIST_BASELINES[i], LIST[i][0] + " " + LIST[i][1]);
                }
            }
            save(doc, file);
        }
        return file;
    }

    static Path writeHeadingParagraph(Path file) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                show(cs, bold, 14, 72, 604, HEADING);
                show(cs, font, 7, 72, 596, LINE1);
                show(cs, font, 7, 72, 587, LINE2);
            }
            save(doc, file);
        }
        return file;
    }

    private static void save(PDDocument doc, Path file) throws IOException {
        doc.getDocumentInformation().setProducer("review-722");
        doc.save(file.toFile());
    }

    private static void show(PDPageContentStream cs, PDType1Font font, float size, float x, float y, String s)
            throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(s);
        cs.endText();
    }

    // ---- Backend responses (docling-fast) ----

    static String tableResponse(String[][] cells, float[] cols, float top) throws IOException {
        int rows = cells.length;
        int ncols = cells[0].length;
        ObjectNode table = MAPPER.createObjectNode();
        table.put("label", "table");
        table.putArray("prov").add(prov(cols[0], top, cols[ncols], top - ROW_H * rows));
        ObjectNode data = table.putObject("data");
        data.put("num_rows", rows);
        data.put("num_cols", ncols);
        ArrayNode grid = data.putArray("grid");
        ArrayNode tableCells = data.putArray("table_cells");
        for (int r = 0; r < rows; r++) {
            ArrayNode gridRow = grid.addArray();
            for (int c = 0; c < ncols; c++) {
                ObjectNode cell = MAPPER.createObjectNode();
                ObjectNode bbox = cell.putObject("bbox");
                bbox.put("l", cols[c]);
                bbox.put("t", PAGE_H - (top - r * ROW_H));
                bbox.put("r", cols[c + 1]);
                bbox.put("b", PAGE_H - (top - (r + 1) * ROW_H));
                bbox.put("coord_origin", "TOPLEFT");
                cell.put("row_span", 1);
                cell.put("col_span", 1);
                cell.put("start_row_offset_idx", r);
                cell.put("end_row_offset_idx", r + 1);
                cell.put("start_col_offset_idx", c);
                cell.put("end_col_offset_idx", c + 1);
                cell.put("text", cells[r][c]);
                cell.put("column_header", r == 0);
                cell.put("row_header", false);
                gridRow.add(cell.deepCopy());
                tableCells.add(cell);
            }
        }
        ObjectNode json = MAPPER.createObjectNode();
        json.putArray("texts");
        json.putArray("tables").add(table);
        json.putObject("pages").putObject("1");
        return wrap(json);
    }

    /** docling moves the marker of a list item out of its text ("1." in marker, "Yes" in text). */
    static String listResponse() throws IOException {
        ObjectNode json = MAPPER.createObjectNode();
        ArrayNode texts = json.putArray("texts");
        addText(texts, "text", INTRO, prov(72, 541, 330, 527));
        for (int i = 0; i < LIST.length; i++) {
            ObjectNode item = addText(texts, "list_item", LIST[i][1],
                prov(72, LIST_BASELINES[i] + 10, 130, LIST_BASELINES[i] - 3));
            item.put("orig", LIST[i][0] + " " + LIST[i][1]);
            item.put("marker", LIST[i][0]);
            item.put("enumerated", true);
        }
        json.putObject("pages").putObject("1");
        return wrap(json);
    }

    static String headingParagraphResponse() throws IOException {
        ObjectNode json = MAPPER.createObjectNode();
        ArrayNode texts = json.putArray("texts");
        addText(texts, "section_header", HEADING, prov(72, 618, 128, 601)).put("level", 1);
        addText(texts, "text", LINE1 + " " + LINE2, prov(72, 604, 300, 585));
        json.putObject("pages").putObject("1");
        return wrap(json);
    }

    private static ObjectNode addText(ArrayNode texts, String label, String text, ObjectNode prov) {
        ObjectNode node = texts.addObject();
        node.put("label", label);
        node.put("text", text);
        node.putArray("prov").add(prov);
        return node;
    }

    private static ObjectNode prov(float l, float t, float r, float b) {
        ObjectNode prov = MAPPER.createObjectNode();
        prov.put("page_no", 1);
        ObjectNode bbox = prov.putObject("bbox");
        bbox.put("l", l);
        bbox.put("t", t);
        bbox.put("r", r);
        bbox.put("b", b);
        bbox.put("coord_origin", "BOTTOMLEFT");
        return prov;
    }

    private static String wrap(ObjectNode jsonContent) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("status", "success");
        body.putObject("document").set("json_content", jsonContent);
        body.put("processing_time", 0.5);
        body.putArray("errors");
        body.putArray("failed_pages");
        return MAPPER.writeValueAsString(body);
    }

    // ---- Conversion and output readers ----

    static void enqueue(MockWebServer server, String convertBody) {
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());
        server.enqueue(new MockResponse.Builder().body(convertBody)
            .addHeader("Content-Type", "application/json").build());
    }

    /** Converts with docling-fast in full mode; returns the Markdown file (JSON and tagged PDF beside it). */
    static Path convert(MockWebServer server, Path dir, Path pdf, boolean tagged) throws Exception {
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        config.setGenerateTaggedPDF(tagged);
        config.setHybrid("docling-fast");
        config.getHybridConfig().setMode("full");
        config.getHybridConfig().setUrl(server.url("").toString().replaceAll("/$", ""));
        DocumentProcessor.processFile(pdf.toString(), config);
        return dir.resolve(pdf.getFileName().toString().replace(".pdf", ".md"));
    }

    static List<List<String>> markdownCells(Path md) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (String line : Files.readString(md).split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|") || trimmed.matches("^\\|(\\s*:?-+:?\\s*\\|)+$")) {
                continue;
            }
            List<String> row = new ArrayList<>();
            for (String part : trimmed.substring(1, trimmed.length() - 1).split("\\|", -1)) {
                row.add(part.replace("<br>", " ").replaceAll("\\s+", " ").trim());
            }
            rows.add(row);
        }
        return rows;
    }

    static List<List<String>> toLists(String[][] cells) {
        List<List<String>> out = new ArrayList<>();
        for (String[] row : cells) {
            out.add(List.of(row));
        }
        return out;
    }

    /**
     * The numbered lists as a CommonMark reader shows them: an ordered list numbers its items from
     * the number of its first item; a bullet item that starts with "N." shows that number. Any other
     * line ends the list.
     */
    static List<List<String>> numberedLists(String markdown) {
        Pattern ordered = Pattern.compile("^(\\d+)([.)])\\s+(.*\\S)\\s*$");
        Pattern bullet = Pattern.compile("^([-*+])\\s+(\\d+)[.)]\\s+(.*\\S)\\s*$");
        List<List<String>> lists = new ArrayList<>();
        List<String> current = null;
        String kind = null;
        int start = 0;
        for (String raw : markdown.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            Matcher b = bullet.matcher(line);
            Matcher o = ordered.matcher(line);
            if (b.matches()) {
                if (!("bullet" + b.group(1)).equals(kind)) {
                    current = new ArrayList<>();
                    lists.add(current);
                    kind = "bullet" + b.group(1);
                }
                current.add(Integer.parseInt(b.group(2)) + " " + b.group(3));
            } else if (o.matches()) {
                if (!("ordered" + o.group(2)).equals(kind)) {
                    current = new ArrayList<>();
                    lists.add(current);
                    kind = "ordered" + o.group(2);
                    start = Integer.parseInt(o.group(1));
                }
                current.add((start + current.size()) + " " + o.group(3));
            } else {
                kind = null;
            }
        }
        return lists;
    }

    static int count(String text, String word) {
        Matcher m = Pattern.compile("(?<![\\w])" + Pattern.quote(word) + "(?![\\w])").matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    /** The elements of a table, row by row. */
    static List<String> flat(String[][] cells) {
        List<String> out = new ArrayList<>();
        for (String[] row : cells) {
            out.addAll(List.of(row));
        }
        return out;
    }

    /** Adds the content of each JSON text element that has no font name or no text color. */
    static void withoutFont(JsonNode node, List<String> out) {
        if (node.isObject()) {
            if (node.has("content") && node.has("font size")
                    && (!node.path("font").isTextual() || !node.has("text color"))) {
                out.add(node.get("content").asText());
            }
            node.elements().forEachRemaining(child -> withoutFont(child, out));
        } else if (node.isArray()) {
            node.elements().forEachRemaining(child -> withoutFont(child, out));
        }
    }

    /**
     * The text of each TD/TH (from its MCIDs), and the shown text that is outside the structure tree:
     * in an artifact, with no MCID, or with an MCID that no structure element references.
     */
    static final class Tagged {
        final List<String> cells = new ArrayList<>();
        final List<String> outside = new ArrayList<>();
    }

    static Tagged readTagged(Path md) throws IOException {
        Path pdf = md.resolveSibling(md.getFileName().toString().replace(".md", "_tagged.pdf"));
        assertTrue(Files.isRegularFile(pdf), "tagged PDF " + pdf);
        Tagged out = new Tagged();
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            COSBase root = doc.getDocumentCatalog().getCOSObject()
                .getDictionaryObject(COSName.getPDFName("StructTreeRoot"));
            assertTrue(root instanceof COSDictionary, "the tagged PDF has a structure tree");
            List<Integer> referenced = new ArrayList<>();
            addMcids(((COSDictionary) root).getDictionaryObject(COSName.K), referenced);
            Map<Integer, StringBuilder> mcidText = new HashMap<>();
            readContent(doc, mcidText, new HashSet<>(referenced), out.outside);
            collectCells(root, mcidText, out.cells);
        }
        return out;
    }

    private static void readContent(PDDocument doc, Map<Integer, StringBuilder> mcidText, Set<Integer> referenced,
                                    List<String> outside) throws IOException {
        PDPage page = doc.getPage(0);
        COSBase props = page.getResources() == null ? null
            : page.getResources().getCOSObject().getDictionaryObject(COSName.PROPERTIES);
        List<Object> tokens = new PDFStreamParser(page).parse();
        Deque<Object[]> stack = new ArrayDeque<>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!(tokens.get(i) instanceof Operator)) {
                continue;
            }
            String op = ((Operator) tokens.get(i)).getName();
            if ("BDC".equals(op)) {
                String tag = tokens.get(i - 2) instanceof COSName ? ((COSName) tokens.get(i - 2)).getName() : "?";
                Object operand = tokens.get(i - 1);
                if (operand instanceof COSName && props instanceof COSDictionary) {
                    operand = ((COSDictionary) props).getDictionaryObject((COSName) operand);
                }
                Integer mcid = null;
                if (operand instanceof COSDictionary) {
                    COSBase m = ((COSDictionary) operand).getDictionaryObject(COSName.MCID);
                    if (m instanceof COSInteger) {
                        mcid = ((COSInteger) m).intValue();
                    }
                }
                stack.push(new Object[]{tag, mcid});
            } else if ("BMC".equals(op)) {
                stack.push(new Object[]{((COSName) tokens.get(i - 1)).getName(), null});
            } else if ("EMC".equals(op) && !stack.isEmpty()) {
                stack.pop();
            } else if (("Tj".equals(op) && tokens.get(i - 1) instanceof COSString)
                    || ("TJ".equals(op) && tokens.get(i - 1) instanceof COSArray)) {
                String s = shownText(tokens.get(i - 1));
                if (s.isBlank()) {
                    continue;
                }
                boolean artifact = false;
                Integer mcid = null;
                for (Object[] e : stack) {
                    if ("Artifact".equals(e[0])) {
                        artifact = true;
                    }
                    if (mcid == null && e[1] != null) {
                        mcid = (Integer) e[1];
                    }
                }
                if (artifact || mcid == null || !referenced.contains(mcid)) {
                    outside.add(s);
                } else {
                    StringBuilder sb = mcidText.computeIfAbsent(mcid, k -> new StringBuilder());
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(s);
                }
            }
        }
    }

    private static String shownText(Object operand) {
        if (operand instanceof COSString) {
            return ((COSString) operand).getString();
        }
        StringBuilder sb = new StringBuilder();
        for (COSBase b : (COSArray) operand) {
            if (b instanceof COSString) {
                sb.append(((COSString) b).getString());
            }
        }
        return sb.toString();
    }

    private static void collectCells(COSBase node, Map<Integer, StringBuilder> mcidText, List<String> out) {
        COSBase base = node instanceof COSObject ? ((COSObject) node).getObject() : node;
        if (base instanceof COSArray) {
            for (COSBase kid : (COSArray) base) {
                collectCells(kid, mcidText, out);
            }
            return;
        }
        if (!(base instanceof COSDictionary)) {
            return;
        }
        COSDictionary dict = (COSDictionary) base;
        COSName type = dict.getCOSName(COSName.S);
        if (type != null && ("TD".equals(type.getName()) || "TH".equals(type.getName()))) {
            List<Integer> mcids = new ArrayList<>();
            addMcids(dict.getDictionaryObject(COSName.K), mcids);
            StringBuilder sb = new StringBuilder();
            for (Integer mcid : mcids) {
                StringBuilder t = mcidText.get(mcid);
                if (t != null) {
                    if (sb.length() > 0) {
                        sb.append(' ');
                    }
                    sb.append(t);
                }
            }
            out.add(sb.toString().replaceAll("\\s+", " ").trim());
            return;
        }
        COSBase kids = dict.getDictionaryObject(COSName.K);
        if (kids != null) {
            collectCells(kids, mcidText, out);
        }
    }

    private static void addMcids(COSBase node, List<Integer> mcids) {
        COSBase base = node instanceof COSObject ? ((COSObject) node).getObject() : node;
        if (base instanceof COSInteger) {
            mcids.add(((COSInteger) base).intValue());
        } else if (base instanceof COSArray) {
            for (COSBase kid : (COSArray) base) {
                addMcids(kid, mcids);
            }
        } else if (base instanceof COSDictionary) {
            COSDictionary dict = (COSDictionary) base;
            COSBase mcid = dict.getDictionaryObject(COSName.MCID);
            if (mcid instanceof COSInteger) {
                mcids.add(((COSInteger) mcid).intValue());
            } else {
                addMcids(dict.getDictionaryObject(COSName.K), mcids);
            }
        }
    }
}

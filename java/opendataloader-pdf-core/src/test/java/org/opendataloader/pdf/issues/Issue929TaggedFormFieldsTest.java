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
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.PDPrintFieldAttributeObject;
import org.apache.pdfbox.pdmodel.documentinterchange.taggedpdf.StandardStructureTypes;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #929: with {@code --use-struct-tree}, a non-interactive form field (a {@code Form} structure
 * element with {@code PrintField} attributes, ISO 32000-1 14.8.5.6) gave only its label as paragraph
 * text. The output did not tell a check box from a radio button, or whether it was checked.
 *
 * <p>The samples are synthetic tagged PDFs with the structure and the measurements of a page that a
 * browser prints: a {@code Form} element owns the drawn control (a 14.5 pt box or circle), and the
 * label is the {@code NonStruct} element after it in the same parent, on the same line. The radio
 * buttons are in a {@code NonStruct} group. The expected output comes from this design and from the
 * format that the maintainers decided in the issue:
 *
 * <ul>
 *   <li>JSON: a {@code form field} element with {@code field role} ({@code check box},
 *       {@code radio button}), {@code checked} ({@code on}, {@code off}, {@code neutral}; {@code off}
 *       if the key is missing; {@code checked} and {@code Checked} are read), {@code description}
 *       (from {@code Desc}), and the label in {@code content}.</li>
 *   <li>Markdown: a task list item, {@code - [x] label} or {@code - [ ] label}.</li>
 *   <li>The label is the text in the {@code Form} element; else the next element in the same parent,
 *       if its text is on the line of the control; else {@code Desc}. The label is not also a
 *       paragraph.</li>
 *   <li>{@code pb}, {@code tv}, the default mode, and the HTML and text outputs keep their output.</li>
 * </ul>
 *
 * <p>The samples and the outputs stay in {@code target/test-samples/issue-929-*}.
 */
class Issue929TaggedFormFieldsTest {

    private static final float FONT_SIZE = 10.5f;
    private static final float CONTROL_SIZE = 14.5f;
    private static final float CONTROL_LEFT = 29.5f;
    private static final float LABEL_LEFT = 51;
    private static final float ROW_PITCH = 19.35f;
    /** The baseline of a label above the bottom of its control, as on the printed page. */
    private static final float LABEL_RISE = 3.3f;
    private static final String PRINT_FIELD = PDPrintFieldAttributeObject.OWNER_PRINT_FIELD;

    @Test
    void checkBoxesAndRadioButtonsAreFormFieldsInTheJson() throws IOException {
        Output output = convert("issue-929-order-form", Issue929TaggedFormFieldsTest::writeOrderForm, true);

        assertThat(formFields(output.json)).as("form field elements in the JSON").containsExactly(
            "check box|on|Option one|Option one",
            "check box|off|Option two|Option two",
            "radio button|on|Pay now|Pay now",
            "radio button|off|Pay later|Pay later");
    }

    @Test
    void checkBoxesAndRadioButtonsAreTaskListItemsInTheMarkdown() throws IOException {
        Output output = convert("issue-929-order-form-md", Issue929TaggedFormFieldsTest::writeOrderForm, true);

        assertThat(output.markdownLines()).as("task list items in the Markdown").containsSubsequence(
            "Options", "- [x] Option one", "- [ ] Option two", "Payment", "- [x] Pay now", "- [ ] Pay later");
        for (String label : new String[]{"Option one", "Option two", "Pay now", "Pay later"}) {
            assertThat(count(output.markdown, label)).as("task list items in the Markdown: \"%s\" once", label)
                .isEqualTo(1);
        }
    }

    @Test
    void textInTheFormElementIsTheLabel() throws IOException {
        Output output = convert("issue-929-label-inside", Issue929TaggedFormFieldsTest::writeLabelInsideForm, true);

        assertThat(output.markdownLines()).as("label in the Form element").contains("- [x] Inside label");
        assertThat(formFields(output.json)).as("label in the Form element")
            .containsExactly("check box|on|Inside label|Inside label");
    }

    @Test
    void fieldWithoutALabelOnItsLineTakesDesc() throws IOException {
        Output output = convert("issue-929-desc-only", Issue929TaggedFormFieldsTest::writeDescOnly, true);

        assertThat(output.markdownLines()).as("label from Desc").contains("- [x] Desc only");
        assertThat(formFields(output.json)).as("label from Desc").containsExactly("check box|on|Desc only|Desc only");
    }

    @Test
    void checkedStateIsReadInBothSpellingsWithOffAsDefault() throws IOException {
        Output output = convert("issue-929-states", Issue929TaggedFormFieldsTest::writeStates, true);

        assertThat(formFields(output.json)).as("checked state of the form field").containsExactly(
            "check box|on|Upper spelling|Upper spelling",
            "check box|neutral|Neutral state|Neutral state",
            "radio button|off|No state key|No state key");
        assertThat(output.markdownLines()).as("checked state of the form field")
            .containsSubsequence("- [x] Upper spelling", "- [ ] Neutral state", "- [ ] No state key");
    }

    @Test
    void descIsFilteredLikeThePageText() throws IOException {
        Output output = convert("issue-929-filtered-desc", Issue929TaggedFormFieldsTest::writeSensitiveDesc, true,
            config -> config.getFilterConfig().setFilterSensitiveData(true));

        assertThat(formFields(output.json)).as("filtered Desc of the form field")
            .containsExactly("check box|on|Write to email@example.com|Write to email@example.com");
        assertThat(output.markdown).as("filtered Desc of the form field").doesNotContain("pat.doe@example.org");
    }

    @Test
    void textBelowTheFieldStaysAParagraph() throws IOException {
        Output output = convert("issue-929-text-below", Issue929TaggedFormFieldsTest::writeDescOnly, true);

        List<String> paragraphs = output.json.findParents("type").stream()
            .filter(node -> "paragraph".equals(node.path("type").asText()))
            .map(node -> node.path("content").asText())
            .collect(Collectors.toList());
        assertThat(paragraphs).contains("A paragraph further down");
    }

    @Test
    void otherRolesAndFormsWithoutPrintFieldKeepTheirOutput() throws IOException {
        Output output = convert("issue-929-other-roles", Issue929TaggedFormFieldsTest::writeOtherRoles, true);

        assertThat(formFields(output.json)).isEmpty();
        assertThat(output.markdownLines()).noneMatch(line -> line.startsWith("- ["));
        assertThat(output.markdown).contains("Push label", "Text label", "Plain label");
    }

    @Test
    void htmlAndTextShowOnlyTheLabelsOfThePage() throws IOException {
        assertHtmlAndText("issue-929-html-text-order-form", Issue929TaggedFormFieldsTest::writeOrderForm,
            "Option one", "Option two", "Pay now", "Pay later");
        assertHtmlAndText("issue-929-html-text-desc-only", Issue929TaggedFormFieldsTest::writeDescOnly,
            "A paragraph further down");
    }

    /** The HTML and the text have the text of the page and no field state, and no text from Desc. */
    private static void assertHtmlAndText(String name, Sample sample, String... pageText) throws IOException {
        Path directory = TestSamples.emptyDirectory(name);
        Path pdf = directory.resolve(name + ".pdf");
        sample.write(pdf);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(false);
        config.setGenerateHtml(true);
        config.setGenerateText(true);
        config.setUseStructTree(true);
        DocumentProcessor.processFile(pdf.toString(), config);

        for (String extension : new String[]{".html", ".txt"}) {
            String output = Files.readString(directory.resolve(name + extension), StandardCharsets.UTF_8);
            assertThat(output).as(name + extension).contains(pageText);
            assertThat(output).as(name + extension).doesNotContain("<input", "[x]", "[ ]", "Desc only");
        }
    }

    @Test
    void defaultModeKeepsItsOutput() throws IOException {
        Output output = convert("issue-929-default-mode", Issue929TaggedFormFieldsTest::writeOrderForm, false);

        assertThat(formFields(output.json)).isEmpty();
        assertThat(output.markdownLines()).noneMatch(line -> line.startsWith("- ["));
        assertThat(output.markdown).contains("Option one", "Option two", "Pay now", "Pay later");
    }

    // ---------------------------------------------------------------- samples

    @FunctionalInterface
    private interface Sample {
        void write(Path pdf) throws IOException;
    }

    /** The order form: two check boxes, then a group of two radio buttons. The first of each is on. */
    private static void writeOrderForm(Path pdf) throws IOException {
        try (TaggedPage page = new TaggedPage()) {
            PDStructureElement options = page.element(StandardStructureTypes.P, page.root);
            page.text(options, 30, 764, "Options");
            float bottom = 737.17f;
            String[][] boxes = {{"Option one", "on"}, {"Option two", null}};
            for (String[] box : boxes) {
                PDStructureElement form = page.element(StandardStructureTypes.FORM, page.root);
                form.getCOSObject().setItem(COSName.A, splitAttributes(PDPrintFieldAttributeObject.ROLE_CB, box[1], box[0]));
                page.control(form, bottom, false, box[1] != null);
                page.text(page.element("NonStruct", page.root), LABEL_LEFT, bottom + LABEL_RISE, box[0]);
                bottom -= ROW_PITCH;
            }
            PDStructureElement payment = page.element(StandardStructureTypes.P, page.root);
            page.text(payment, 30, 698, "Payment");
            PDStructureElement group = page.element("NonStruct", page.root);
            bottom = 671.32f;
            String[][] radios = {{"Pay now", "on"}, {"Pay later", null}};
            for (String[] radio : radios) {
                PDStructureElement form = page.element(StandardStructureTypes.FORM, group);
                form.getCOSObject().setItem(COSName.A, splitAttributes(PDPrintFieldAttributeObject.ROLE_RB, radio[1], radio[0]));
                page.control(form, bottom, true, radio[1] != null);
                page.text(page.element("NonStruct", group), LABEL_LEFT, bottom + LABEL_RISE, radio[0]);
                bottom -= ROW_PITCH;
            }
            page.save(pdf);
        }
    }

    /** A check box whose Form element owns the control and its label. */
    private static void writeLabelInsideForm(Path pdf) throws IOException {
        try (TaggedPage page = new TaggedPage()) {
            PDStructureElement form = page.element(StandardStructureTypes.FORM, page.root);
            form.getCOSObject().setItem(COSName.A, splitAttributes(PDPrintFieldAttributeObject.ROLE_CB, "on", "Inside label"));
            page.control(form, 737.17f, false, true);
            page.text(form, LABEL_LEFT, 737.17f + LABEL_RISE, "Inside label");
            page.save(pdf);
        }
    }

    /** A check box with no text on its line. A paragraph 60 pt below it is the next element. */
    private static void writeDescOnly(Path pdf) throws IOException {
        try (TaggedPage page = new TaggedPage()) {
            PDStructureElement form = page.element(StandardStructureTypes.FORM, page.root);
            form.getCOSObject().setItem(COSName.A, splitAttributes(PDPrintFieldAttributeObject.ROLE_CB, "on", "Desc only"));
            page.control(form, 737.17f, false, true);
            PDStructureElement paragraph = page.element(StandardStructureTypes.P, page.root);
            page.text(paragraph, 30, 680, "A paragraph further down");
            page.save(pdf);
        }
    }

    /** One attribute dictionary for each field: {@code Checked} (PDF 2.0), {@code neutral}, no key. */
    private static void writeStates(Path pdf) throws IOException {
        try (TaggedPage page = new TaggedPage()) {
            Object[][] fields = {
                {PDPrintFieldAttributeObject.ROLE_CB, "Checked", "on", "Upper spelling"},
                {PDPrintFieldAttributeObject.ROLE_CB, "checked", "neutral", "Neutral state"},
                {PDPrintFieldAttributeObject.ROLE_RB, null, null, "No state key"}};
            float bottom = 737.17f;
            for (Object[] field : fields) {
                PDStructureElement form = page.element(StandardStructureTypes.FORM, page.root);
                COSDictionary attribute = new COSDictionary();
                attribute.setName(COSName.O, PRINT_FIELD);
                attribute.setName(COSName.getPDFName("Role"), (String) field[0]);
                if (field[1] != null) {
                    attribute.setName(COSName.getPDFName((String) field[1]), (String) field[2]);
                }
                attribute.setString(COSName.getPDFName("Desc"), (String) field[3]);
                form.getCOSObject().setItem(COSName.A, attribute);
                page.control(form, bottom, PDPrintFieldAttributeObject.ROLE_RB.equals(field[0]), "on".equals(field[2]));
                page.text(page.element("NonStruct", page.root), LABEL_LEFT, bottom + LABEL_RISE, (String) field[3]);
                bottom -= ROW_PITCH;
            }
            page.save(pdf);
        }
    }

    /** A check box with no label on the page and an e-mail address in its Desc. */
    private static void writeSensitiveDesc(Path pdf) throws IOException {
        try (TaggedPage page = new TaggedPage()) {
            PDStructureElement form = page.element(StandardStructureTypes.FORM, page.root);
            form.getCOSObject().setItem(COSName.A,
                splitAttributes(PDPrintFieldAttributeObject.ROLE_CB, "on", "Write to pat.doe@example.org"));
            page.control(form, 737.17f, false, true);
            page.save(pdf);
        }
    }

    /** A push button, a text value, and a Form element without PrintField attributes, each with a label. */
    private static void writeOtherRoles(Path pdf) throws IOException {
        try (TaggedPage page = new TaggedPage()) {
            String[][] fields = {{PDPrintFieldAttributeObject.ROLE_PB, "Push label"},
                {PDPrintFieldAttributeObject.ROLE_TV, "Text label"}, {null, "Plain label"}};
            float bottom = 737.17f;
            for (String[] field : fields) {
                PDStructureElement form = page.element(StandardStructureTypes.FORM, page.root);
                if (field[0] != null) {
                    form.getCOSObject().setItem(COSName.A, splitAttributes(field[0], null, field[1]));
                }
                page.control(form, bottom, false, false);
                page.text(page.element("NonStruct", page.root), LABEL_LEFT, bottom + LABEL_RISE, field[1]);
                bottom -= ROW_PITCH;
            }
            page.save(pdf);
        }
    }

    /** The PrintField attributes as a browser writes them: one attribute dictionary for each key. */
    private static COSArray splitAttributes(String role, String checked, String desc) {
        COSArray attributes = new COSArray();
        COSDictionary roleAttribute = new COSDictionary();
        roleAttribute.setName(COSName.O, PRINT_FIELD);
        roleAttribute.setName(COSName.getPDFName("Role"), role);
        attributes.add(roleAttribute);
        if (checked != null) {
            COSDictionary checkedAttribute = new COSDictionary();
            checkedAttribute.setName(COSName.O, PRINT_FIELD);
            checkedAttribute.setName(COSName.getPDFName("checked"), checked);
            attributes.add(checkedAttribute);
        }
        COSDictionary descAttribute = new COSDictionary();
        descAttribute.setName(COSName.O, PRINT_FIELD);
        descAttribute.setString(COSName.getPDFName("Desc"), desc);
        attributes.add(descAttribute);
        return attributes;
    }

    /** A one-page tagged PDF under a {@code Document} element; each drawing is a marked-content sequence. */
    private static final class TaggedPage implements AutoCloseable {
        private final PDDocument document = new PDDocument();
        private final PDPage page = new PDPage(PDRectangle.LETTER);
        private final PDStructureTreeRoot treeRoot = new PDStructureTreeRoot();
        private final PDStructureElement root;
        private final COSArray parents = new COSArray();
        private final PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        private final PDPageContentStream content;

        TaggedPage() throws IOException {
            document.addPage(page);
            page.setStructParents(0);
            document.getDocumentCatalog().setStructureTreeRoot(treeRoot);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            document.getDocumentCatalog().setMarkInfo(markInfo);
            root = new PDStructureElement(StandardStructureTypes.DOCUMENT, treeRoot);
            treeRoot.appendKid(root);
            content = new PDPageContentStream(document, page);
        }

        PDStructureElement element(String type, PDStructureNode parent) {
            PDStructureElement element = new PDStructureElement(type, parent);
            parent.appendKid(element);
            return element;
        }

        void text(PDStructureElement element, float x, float baseline, String text) throws IOException {
            begin(element);
            content.beginText();
            content.setFont(font, FONT_SIZE);
            content.newLineAtOffset(x, baseline);
            content.showText(text);
            content.endText();
            content.endMarkedContent();
        }

        /** A box (with a tick if on) or a circle (with a dot if on), 14.5 pt high, at the left margin. */
        void control(PDStructureElement element, float bottom, boolean radio, boolean on) throws IOException {
            begin(element);
            content.setLineWidth(1);
            if (radio) {
                float radius = CONTROL_SIZE / 2;
                circle(CONTROL_LEFT + radius, bottom + radius, radius);
                content.stroke();
                if (on) {
                    circle(CONTROL_LEFT + radius, bottom + radius, radius / 2);
                    content.fill();
                }
            } else {
                content.addRect(CONTROL_LEFT, bottom, CONTROL_SIZE, CONTROL_SIZE);
                content.stroke();
                if (on) {
                    content.moveTo(CONTROL_LEFT + 3, bottom + 7);
                    content.lineTo(CONTROL_LEFT + 6, bottom + 3.5f);
                    content.lineTo(CONTROL_LEFT + 11.5f, bottom + 11);
                    content.stroke();
                }
            }
            content.endMarkedContent();
        }

        private void circle(float centerX, float centerY, float radius) throws IOException {
            float k = 0.5523f * radius;
            content.moveTo(centerX + radius, centerY);
            content.curveTo(centerX + radius, centerY + k, centerX + k, centerY + radius, centerX, centerY + radius);
            content.curveTo(centerX - k, centerY + radius, centerX - radius, centerY + k, centerX - radius, centerY);
            content.curveTo(centerX - radius, centerY - k, centerX - k, centerY - radius, centerX, centerY - radius);
            content.curveTo(centerX + k, centerY - radius, centerX + radius, centerY - k, centerX + radius, centerY);
            content.closePath();
        }

        private void begin(PDStructureElement element) throws IOException {
            int mcid = parents.size();
            COSDictionary properties = new COSDictionary();
            properties.setInt(COSName.MCID, mcid);
            content.beginMarkedContent(COSName.getPDFName(element.getStructureType()), PDPropertyList.create(properties));
            element.setPage(page);
            COSBase kids = element.getCOSObject().getDictionaryObject(COSName.K);
            if (kids == null) {
                element.getCOSObject().setInt(COSName.K, mcid);
            } else if (kids instanceof COSArray) {
                ((COSArray) kids).add(COSInteger.get(mcid));
            } else {
                COSArray array = new COSArray();
                array.add(kids);
                array.add(COSInteger.get(mcid));
                element.getCOSObject().setItem(COSName.K, array);
            }
            parents.add(element);
        }

        void save(Path pdf) throws IOException {
            content.close();
            COSArray nums = new COSArray();
            nums.add(COSInteger.get(0));
            nums.add(parents);
            COSDictionary parentTree = new COSDictionary();
            parentTree.setItem(COSName.NUMS, nums);
            treeRoot.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
            treeRoot.getCOSObject().setInt(COSName.PARENT_TREE_NEXT_KEY, 1);
            document.save(pdf.toFile());
        }

        @Override
        public void close() throws IOException {
            document.close();
        }
    }

    // ---------------------------------------------------------------- conversion

    private static final class Output {
        private final JsonNode json;
        private final String markdown;

        Output(JsonNode json, String markdown) {
            this.json = json;
            this.markdown = markdown;
        }

        List<String> markdownLines() {
            return markdown.lines().map(String::trim).filter(line -> !line.isEmpty()).collect(Collectors.toList());
        }
    }

    private static Output convert(String name, Sample sample, boolean useStructTree) throws IOException {
        return convert(name, sample, useStructTree, config -> { });
    }

    private static Output convert(String name, Sample sample, boolean useStructTree,
                                  java.util.function.Consumer<Config> options) throws IOException {
        Path directory = TestSamples.emptyDirectory(name);
        Path pdf = directory.resolve(name + ".pdf");
        sample.write(pdf);
        Config config = new Config();
        config.setOutputFolder(directory.toString());
        config.setGenerateJSON(true);
        config.setGenerateMarkdown(true);
        config.setUseStructTree(useStructTree);
        options.accept(config);
        DocumentProcessor.processFile(pdf.toString(), config);
        JsonNode json = new ObjectMapper().readTree(directory.resolve(name + ".json").toFile());
        String markdown = Files.readString(directory.resolve(name + ".md"), StandardCharsets.UTF_8);
        return new Output(json, markdown);
    }

    /** Each {@code form field} element as {@code field role|checked|description|content}, in order. */
    private static List<String> formFields(JsonNode json) {
        List<String> fields = new ArrayList<>();
        for (JsonNode node : json.findParents("type")) {
            if ("form field".equals(node.path("type").asText())) {
                fields.add(node.path("field role").asText() + "|" + node.path("checked").asText() + "|"
                    + node.path("description").asText() + "|" + node.path("content").asText());
            }
        }
        return fields;
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + part.length())) {
            n++;
        }
        return n;
    }
}

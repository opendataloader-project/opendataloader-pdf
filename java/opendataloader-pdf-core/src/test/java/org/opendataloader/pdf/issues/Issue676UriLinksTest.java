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

import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.ParseException;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDMarkInfo;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionGoTo;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination;
import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.api.cli.CLIOptions;
import org.opendataloader.pdf.processors.DocumentProcessor;
import org.opendataloader.pdf.testsupport.TestSamples;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #676: "hyperlinks and links are not handled properly in converted md". The Markdown and the
 * HTML output had the text of a link, but not its address. With {@code --include-links}, a URI link
 * of the PDF becomes a Markdown link and an HTML {@code <a>} element.
 *
 * <p>The reporter's documents are tagged PDFs from Word, with URI link annotations over text in
 * paragraphs and list items. The samples copy their measurements: body text of 12 pt, a line pitch
 * of 14.5 pt, and a link box of 16 pt from 4 pt below the baseline, 2 pt to the left and to the
 * right of the glyphs. Word writes one link annotation for each line of a link that wraps: the box
 * of the first line goes to the right margin (542 pt), and the box of the second line is 24 pt high.
 * In the reporter's documents, a parenthesis or a full stop next to a link had its centre in the box,
 * and a link that wraps after a hyphen kept the hyphen and got a space.
 *
 * <p>The samples are synthetic. The output stays in {@code target/test-samples/issue-676-<name>/}.
 */
class Issue676UriLinksTest {

    private static final float FONT_SIZE = 12;
    // The body text of the second document of the reporter is 11.88 pt. In 11 pt Helvetica, a parenthesis is
    // 3.7 pt wide, so its centre is in a link box 2 pt wider than the link text.
    private static final float SMALL_FONT_SIZE = 11;
    private static final float LEADING = 14.5f;
    private static final float ITEM_PITCH = 18;
    private static final float LEFT = 72;
    private static final float TOP = 700;
    private static final float RIGHT_MARGIN = 542;
    private static final float LINK_EDGE = 2;
    private static final float LINK_BELOW = 4;
    private static final float LINK_HEIGHT = 16;
    private static final float WRAPPED_LINK_HEIGHT = 24;

    private static final String NOTES_URI = "https://example.com/notes?lang=en&page=2";
    private static final String NOTES_MD_DESTINATION = "https://example.com/notes?lang=en&amp;page=2";
    private static final String NOTES_HTML_HREF = "https://example.com/notes?lang=en&amp;page=2";
    private static final String[] PARAGRAPH = {
        "The server keeps the settings of each test in one file on the disk.",
        "Read the release notes before you install the new version of it.",
        "The notes give the changes, the known problems, and the fixed bugs.",
    };
    private static final String[] WRAPPED = {
        "The setup has three parts, and each part has its own steps and checks.",
        "Follow the steps of the deployment guide for the",
        "cloud setup before you start the server.",
    };
    private static final String[] BRACKETS = {
        "The server keeps the settings of each test in one file on the disk.",
        "Read the notes [v2] before you install the new version of it.",
        "The notes give the changes, the known problems, and the fixed bugs.",
    };
    /** A second paragraph after the sample lines, so the page has body text. */
    private static final String GUIDE_URI = "https://example.com/guide";
    private static final String IAM_URI = "https://example.com/iam";
    private static final String CONSOLE_URI = "https://example.com/configure-access-to-serial-console.html";
    private static final String[] PARENTHESES = {
        "The console of the cloud gives a page for each user and each group.",
        "Open the console (https://example.com/iam). Then sign in as the admin.",
        "The page of a user gives the keys, the groups, and the last sign-in.",
    };
    private static final String[] HYPHENATED = {
        "The guide gives the steps to connect to the serial console of a server.",
        "Read the steps at https://example.com/configure-access-to-",
        "serial-console.html before you connect to the server.",
    };
    // An icon in a line, as the button icons of #642: 11 pt, on the baseline, 3 pt after the words.
    private static final float ICON_SIZE = 11;
    private static final float ICON_GAP = 3;
    private static final String RETRY_URI = "https://example.com/retry";
    private static final String[] ICON_LINES = {
        "The upload page shows the state of each file that you send to the server.",
        "If the upload fails, click the Retry button",
        "The page also gives the size of each file and the time of the upload.",
    };
    private static final String ICON_LINE_END = "to start the upload again.";
    private static final String[] BODY = {
        "Each test writes a report at the end of the run. The report gives the time,",
        "the number of the checks, and the result of each check. Keep the reports",
        "of the last month, so that you can compare the results of two runs.",
    };
    private static final float BODY_GAP = 24;
    private static final String[] ITEMS = {
        "• Download the installer from the portal.",
        "• Copy the license file to the server.",
        "• Start the server and open the web page.",
    };
    private static final Consumer<Config> INCLUDE_LINKS = Issue676UriLinksTest::includeLinks;
    private static final Consumer<Config> SANITIZE = config -> config.getFilterConfig().setFilterSensitiveData(true);

    @Test
    void markdownWritesTheLinkOverItsText() throws IOException {
        Output out = convert("paragraph", writeParagraph(uri(NOTES_URI)), INCLUDE_LINKS);
        assertTrue(out.markdown.contains("Read the [release notes](" + NOTES_MD_DESTINATION + ") before you install"),
            "the Markdown output lost the link over the text: " + out.markdown);
    }

    @Test
    void htmlWritesTheLinkOverItsText() throws IOException {
        Output out = convert("paragraph-html", writeParagraph(uri(NOTES_URI)), INCLUDE_LINKS);
        assertTrue(
            out.html.contains("Read the <a href=\"" + NOTES_HTML_HREF + "\">release notes</a> before you install"),
            "the HTML output lost the link over the text: " + out.html);
    }

    /** Word writes one annotation for each line of the link. The Markdown has one link. */
    @Test
    void markdownWritesOneLinkForALinkOverTwoLines() throws IOException {
        Output out = convert("wrapped", (dir, name) -> writeWrapped(dir, name, WRAPPED, "deployment", "cloud setup",
            GUIDE_URI), INCLUDE_LINKS);
        assertTrue(out.markdown.contains(
                "Follow the steps of the [deployment guide for the cloud setup](" + GUIDE_URI + ") before you start"),
            "the Markdown output lost the link over two lines: " + out.markdown);
    }

    /** The label of a list item is cut from the text by its length, before the link. */
    @Test
    void markdownWritesTheLinkInAListItem() throws IOException {
        Output out = convert("list-item", (dir, name) -> writeLines(dir, name, FONT_SIZE, ITEMS, ITEM_PITCH,
            0, "installer", uri("https://example.com/installer")), INCLUDE_LINKS);
        assertTrue(out.markdown.contains("- Download the [installer](https://example.com/installer) from the portal."),
            "the Markdown list item lost the link: " + out.markdown);
    }

    /** Brackets in the link text and parentheses in the address do not end the link. */
    @Test
    void markdownKeepsBracketsAndParenthesesInTheLink() throws IOException {
        Output out = convert("brackets", (dir, name) -> writeLines(dir, name, FONT_SIZE, BRACKETS, LEADING,
            1, "notes [v2]", uri("https://example.com/setup_(v2)")), INCLUDE_LINKS);
        assertTrue(
            out.markdown.contains("Read the [notes \\[v2\\]](https://example.com/setup_\\(v2\\)) before you install"),
            "the Markdown output lost the link with brackets and parentheses: " + out.markdown);
    }

    /** Word's link box is 2 pt wider than the link text: the parentheses next to the link stay out of it. */
    @Test
    void markdownLeavesPunctuationNextToTheLinkOutOfIt() throws IOException {
        Output out = convert("parentheses", (dir, name) -> writeLines(dir, name, SMALL_FONT_SIZE, PARENTHESES, LEADING,
            1, IAM_URI, uri(IAM_URI)), INCLUDE_LINKS);
        assertTrue(out.markdown.contains("Open the console ([" + IAM_URI + "](" + IAM_URI + ")). Then sign in"),
            "the Markdown output lost the link without the punctuation next to it: " + out.markdown);
    }

    /** A link that wraps after a hyphen: only the link markup is new, the text is the text without links. */
    @Test
    void linkMarkupIsTheOnlyChangeOfTheText() throws IOException {
        Sample sample = (dir, name) -> writeWrapped(dir, name, HYPHENATED, "https", "serial-console.html", CONSOLE_URI);
        Output plain = convert("hyphen-plain", sample, config -> { });
        Output linked = convert("hyphen-links", sample, INCLUDE_LINKS);
        assertEquals(plain.markdown, withoutMarkdownLinks(linked.markdown),
            "the Markdown links changed the text: " + linked.markdown);
        assertEquals(withoutTitle(plain.html), withoutTitle(linked.html.replaceAll("<a href=\"[^\"]*\">|</a>", "")),
            "the HTML links changed the text: " + linked.html);
    }

    /** The command line: --include-links of the CLI turns the links on. */
    @Test
    void commandLineOptionWritesTheLinks() throws IOException {
        Output out = convert("command-line", writeParagraph(uri(NOTES_URI)),
            config -> applyCommandLine(config, "--include-links"));
        assertTrue(out.markdown.contains("Read the [release notes](" + NOTES_MD_DESTINATION + ") before you install"),
            "the Markdown output of the command line lost the link over the text: " + out.markdown);
    }

    /** An image in the line right after a link (#642) is not in the link: the link ends before it. */
    @Test
    void markdownEndsTheLinkBeforeAnImageInTheLine() throws IOException {
        Output out = convert("icon", Issue676UriLinksTest::writeLinkBeforeIcon, INCLUDE_LINKS);
        assertTrue(out.markdown.contains("click the [Retry button](" + RETRY_URI + ") ![]("),
            "the Markdown output lost the link before an image in the line: " + out.markdown);
    }

    /** The reporter's path: a tagged PDF with a {@code Link} element in a {@code P}, and --use-struct-tree. */
    @Test
    void structTreeMarkdownWritesTheLinkOverItsText() throws IOException {
        Output out = convert("struct-tree", Issue676UriLinksTest::writeTaggedParagraph,
            INCLUDE_LINKS.andThen(config -> config.setUseStructTree(true)));
        assertTrue(out.markdown.contains("Read the [release notes](" + NOTES_MD_DESTINATION + ") before you install."),
            "the structure-tree Markdown output lost the link over the text: " + out.markdown);
    }

    @Test
    void withoutTheOptionTheOutputHasNoLink() throws IOException {
        Output out = convert("no-option", writeParagraph(uri(NOTES_URI)), config -> { });
        assertTrue(out.markdown.contains("Read the release notes before you install"),
            "the text of the link is missing: " + out.markdown);
        assertFalse(out.markdown.contains("]("),
            "the Markdown output has a link without --include-links: " + out.markdown);
        assertFalse(out.html.contains("<a "), "the HTML output has a link without --include-links: " + out.html);
    }

    /** A link address can show text that --sanitize replaces, so the output has no address. */
    @Test
    void sanitizeWritesNoLinkAddress() throws IOException {
        Output out = convert("sanitize", writeParagraph(uri(NOTES_URI)), INCLUDE_LINKS.andThen(SANITIZE));
        assertTrue(out.markdown.contains("Read the release notes before you install"),
            "the text of the link is missing: " + out.markdown);
        assertFalse(out.markdown.contains("example.com"),
            "the Markdown output shows a link address with --sanitize: " + out.markdown);
        assertFalse(out.html.contains("example.com"),
            "the HTML output shows a link address with --sanitize: " + out.html);
    }

    /** Only http, https, and mailto addresses become links. */
    @Test
    void javascriptLinkStaysText() throws IOException {
        Output out = convert("script-link", writeParagraph(uri("javascript:alert(1)")), INCLUDE_LINKS);
        assertTrue(out.markdown.contains("Read the release notes before you install"),
            "the text of the link is missing: " + out.markdown);
        assertFalse(out.markdown.contains("javascript"), "the Markdown output has a javascript link: " + out.markdown);
        assertFalse(out.html.contains("<a "), "the HTML output has a javascript link: " + out.html);
    }

    /** A link to a place in the document is not a URI link. It stays text. */
    @Test
    void internalLinkStaysText() throws IOException {
        Output out = convert("internal", writeParagraph(page -> {
            PDActionGoTo goTo = new PDActionGoTo();
            PDPageFitDestination destination = new PDPageFitDestination();
            destination.setPage(page);
            goTo.setDestination(destination);
            return goTo;
        }), INCLUDE_LINKS);
        assertTrue(out.markdown.contains("Read the release notes before you install"),
            "the text of the link is missing: " + out.markdown);
        assertFalse(out.markdown.contains("]("), "the Markdown output has a link to a page: " + out.markdown);
        assertFalse(out.html.contains("<a "), "the HTML output has a link to a page: " + out.html);
    }

    /**
     * Sets --include-links by reflection: the base build has no such option. There the output stays
     * as the issue reports it, and the assertions after the call fail with the symptom.
     */
    private static void includeLinks(Config config) {
        try {
            Config.class.getMethod("setIncludeLinks", boolean.class).invoke(config, true);
        } catch (NoSuchMethodException e) {
            // No --include-links: the output has no link.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Applies the options of a command line to the config. The base build does not know --include-links: there the
     * config stays as it is, as the issue reports it.
     */
    private static void applyCommandLine(Config config, String... arguments) {
        try {
            CLIOptions.applyAllTo(config, new DefaultParser().parse(CLIOptions.defineOptions(), arguments));
        } catch (ParseException e) {
            // An option that the CLI does not know: the output has no link.
        }
    }

    /** A URI action with the address, for a link on a page. */
    private static Function<PDPage, PDAction> uri(String address) {
        return page -> {
            PDActionURI action = new PDActionURI();
            action.setURI(address);
            return action;
        };
    }

    /** Writes a PDF to the directory, and gives its path. */
    private interface Sample {
        Path write(Path dir, String name) throws IOException;
    }

    /** The paragraph sample: a link with the action over "release notes" on the second line. */
    private static Sample writeParagraph(Function<PDPage, PDAction> action) {
        return (dir, name) -> writeLines(dir, name, FONT_SIZE, PARAGRAPH, LEADING, 1, "release notes", action);
    }

    /** The Markdown without the link markup: the brackets and the destination of each link, and the escapes. */
    private static String withoutMarkdownLinks(String markdown) {
        return markdown.replaceAll("\\]\\((?:\\\\.|[^\\\\)])*\\)", "")
            .replaceAll("(?<!\\\\)\\[", "")
            .replaceAll("\\\\([\\[\\]\\\\])", "$1");
    }

    /** The HTML without its title: the title has the file name. */
    private static String withoutTitle(String html) {
        return html.replaceAll("<title>.*</title>", "");
    }

    /**
     * Writes the lines and {@link #BODY}, and a link with the action over the text {@code linkText} of the line
     * {@code linkLine}.
     */
    private static Path writeLines(Path dir, String name, float fontSize, String[] lines, float pitch, int linkLine,
                                   String linkText, Function<PDPage, PDAction> action) throws IOException {
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < lines.length; i++) {
                    showText(cs, font, fontSize, LEFT, TOP - i * pitch, lines[i]);
                }
                showBody(cs, font, TOP - (lines.length - 1) * pitch - BODY_GAP);
            }
            String line = lines[linkLine];
            float left = LEFT + width(font, fontSize, line.substring(0, line.indexOf(linkText)));
            float right = left + width(font, fontSize, linkText);
            float baseline = TOP - linkLine * pitch;
            addLink(page, left - LINK_EDGE, baseline - LINK_BELOW, right + LINK_EDGE,
                baseline - LINK_BELOW + LINK_HEIGHT, action.apply(page));
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /**
     * A link with the URI from {@code firstText} to the end of the second line, and over {@code secondText} at the
     * start of the third line.
     */
    private static Path writeWrapped(Path dir, String name, String[] lines, String firstText, String secondText,
                                     String address) throws IOException {
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                for (int i = 0; i < lines.length; i++) {
                    showText(cs, font, LEFT, TOP - i * LEADING, lines[i]);
                }
                showBody(cs, font, TOP - (lines.length - 1) * LEADING - BODY_GAP);
            }
            String first = lines[1];
            float firstLeft = LEFT + width(font, first.substring(0, first.indexOf(firstText)));
            float firstBottom = TOP - LEADING - LINK_BELOW;
            addLink(page, firstLeft - LINK_EDGE, firstBottom, RIGHT_MARGIN, firstBottom + LINK_HEIGHT,
                uri(address).apply(page));
            float secondRight = LEFT + width(font, secondText);
            addLink(page, LEFT - LINK_EDGE, firstBottom - WRAPPED_LINK_HEIGHT, secondRight + LINK_EDGE, firstBottom,
                uri(address).apply(page));
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** A link over "Retry button" at the end of the words of the second line, and an icon after it. */
    private static Path writeLinkBeforeIcon(Path dir, String name) throws IOException {
        Path pdf = dir.resolve(name + ".pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDImageXObject icon = LosslessFactory.createFromImage(doc, pattern(22));
            String words = ICON_LINES[1];
            float baseline = TOP - LEADING;
            float iconLeft = LEFT + width(font, words) + ICON_GAP;
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                showText(cs, font, LEFT, TOP, ICON_LINES[0]);
                showText(cs, font, LEFT, baseline, words);
                cs.drawImage(icon, iconLeft, baseline, ICON_SIZE, ICON_SIZE);
                showText(cs, font, iconLeft + ICON_SIZE + ICON_GAP, baseline, ICON_LINE_END);
                showText(cs, font, LEFT, TOP - 2 * LEADING, ICON_LINES[2]);
                showBody(cs, font, TOP - 2 * LEADING - BODY_GAP);
            }
            float linkLeft = LEFT + width(font, words.substring(0, words.indexOf("Retry button")));
            addLink(page, linkLeft - LINK_EDGE, baseline - LINK_BELOW, LEFT + width(font, words) + LINK_EDGE,
                baseline - LINK_BELOW + LINK_HEIGHT, uri(RETRY_URI).apply(page));
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** A square image of two colors, as the icons of #642. */
    private static BufferedImage pattern(int pixels) {
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < pixels; x++) {
            for (int y = 0; y < pixels; y++) {
                image.setRGB(x, y, (x + y) % 2 == 0 ? 0x2E7D32 : 0xFFFFFF);
            }
        }
        return image;
    }

    /**
     * A tagged PDF with one {@code P} on one line, as Word writes it: MCID 0 "Read the ", a {@code Link}
     * with MCID 1 "release notes" and the link annotation, and MCID 2 " before you install.".
     */
    private static Path writeTaggedParagraph(Path dir, String name) throws IOException {
        Path pdf = dir.resolve(name + ".pdf");
        String before = "Read the ";
        String linkText = "release notes";
        String after = " before you install.";
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            PDType1Font helvetica = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDResources resources = new PDResources();
            String font = resources.add(helvetica).getName();
            page.setResources(resources);
            float linkLeft = LEFT + width(helvetica, before);
            float linkRight = linkLeft + width(helvetica, linkText);
            String content = "/P <</MCID 0>> BDC\n" + text(font, LEFT, before) + "EMC\n"
                + "/Link <</MCID 1>> BDC\n" + text(font, linkLeft, linkText) + "EMC\n"
                + "/P <</MCID 2>> BDC\n" + text(font, linkRight, after) + "EMC\n";
            PDStream stream = new PDStream(doc);
            try (OutputStream os = stream.createOutputStream()) {
                os.write(content.getBytes(StandardCharsets.US_ASCII));
            }
            page.setContents(stream);
            page.setStructParents(0);
            PDAnnotationLink annotation = addLink(page, linkLeft - LINK_EDGE, TOP - LINK_BELOW,
                linkRight + LINK_EDGE, TOP - LINK_BELOW + LINK_HEIGHT, uri(NOTES_URI).apply(page));
            annotation.setStructParent(1);

            PDStructureTreeRoot root = new PDStructureTreeRoot();
            PDStructureElement paragraph = new PDStructureElement("P", root);
            paragraph.setPage(page);
            PDStructureElement link = new PDStructureElement("Link", paragraph);
            link.setPage(page);
            COSDictionary objectReference = new COSDictionary();
            objectReference.setItem(COSName.TYPE, COSName.getPDFName("OBJR"));
            objectReference.setItem(COSName.getPDFName("Obj"), annotation);
            COSArray linkKids = new COSArray();
            linkKids.add(COSInteger.get(1));
            linkKids.add(objectReference);
            link.getCOSObject().setItem(COSName.K, linkKids);
            COSArray paragraphKids = new COSArray();
            paragraphKids.add(COSInteger.get(0));
            paragraphKids.add(link);
            paragraphKids.add(COSInteger.get(2));
            paragraph.getCOSObject().setItem(COSName.K, paragraphKids);
            root.appendKid(paragraph);

            COSArray pageParents = new COSArray();
            pageParents.add(paragraph);
            pageParents.add(link);
            pageParents.add(paragraph);
            COSArray nums = new COSArray();
            nums.add(COSInteger.get(0));
            nums.add(pageParents);
            nums.add(COSInteger.get(1));
            nums.add(link);
            COSDictionary parentTree = new COSDictionary();
            parentTree.setItem(COSName.NUMS, nums);
            root.getCOSObject().setItem(COSName.PARENT_TREE, parentTree);
            root.getCOSObject().setInt(COSName.PARENT_TREE_NEXT_KEY, 2);
            doc.getDocumentCatalog().setStructureTreeRoot(root);
            PDMarkInfo markInfo = new PDMarkInfo();
            markInfo.setMarked(true);
            doc.getDocumentCatalog().setMarkInfo(markInfo);
            doc.getDocumentCatalog().setLanguage("en");
            doc.save(pdf.toFile());
        }
        return pdf;
    }

    /** Adds a link annotation with the box and the action to the page. */
    private static PDAnnotationLink addLink(PDPage page, float left, float bottom, float right, float top,
                                            PDAction action) throws IOException {
        PDAnnotationLink link = new PDAnnotationLink();
        link.setRectangle(new PDRectangle(left, bottom, right - left, top - bottom));
        link.setAction(action);
        page.getAnnotations().add(link);
        return link;
    }

    /** Writes {@link #BODY} from the baseline down. */
    private static void showBody(PDPageContentStream cs, PDType1Font font, float baseline) throws IOException {
        for (int i = 0; i < BODY.length; i++) {
            showText(cs, font, LEFT, baseline - i * LEADING, BODY[i]);
        }
    }

    /** A text object that shows the text at {@code x} on the baseline {@link #TOP}. */
    private static String text(String font, float x, String text) {
        return "BT /" + font + " " + FONT_SIZE + " Tf " + x + " " + TOP + " Td (" + text + ") Tj ET\n";
    }

    /** Shows the text in {@link #FONT_SIZE}. */
    private static void showText(PDPageContentStream cs, PDType1Font font, float x, float baseline, String text)
            throws IOException {
        showText(cs, font, FONT_SIZE, x, baseline, text);
    }

    /** Shows the text with its start on the baseline at {@code x}. */
    private static void showText(PDPageContentStream cs, PDType1Font font, float fontSize, float x, float baseline,
                                 String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, baseline);
        cs.showText(text);
        cs.endText();
    }

    /** The width of the text in {@link #FONT_SIZE}. */
    private static float width(PDType1Font font, String text) throws IOException {
        return width(font, FONT_SIZE, text);
    }

    /** The width of the text in the font size. */
    private static float width(PDType1Font font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }

    /** Writes the sample, converts it to Markdown and HTML with the options, and gives both outputs. */
    private static Output convert(String name, Sample sample, Consumer<Config> options) throws IOException {
        Path dir = TestSamples.emptyDirectory("issue-676-" + name);
        Path pdf = sample.write(dir, name);
        Config config = new Config();
        config.setOutputFolder(dir.toString());
        config.setGenerateMarkdown(true);
        config.setGenerateHtml(true);
        options.accept(config);
        DocumentProcessor.processFile(pdf.toString(), config);
        return new Output(Files.readString(dir.resolve(name + ".md")), Files.readString(dir.resolve(name + ".html")));
    }

    /** The Markdown and the HTML output of a sample. */
    private static final class Output {
        final String markdown;
        final String html;

        Output(String markdown, String html) {
            this.markdown = markdown;
            this.html = html;
        }
    }
}

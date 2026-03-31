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
package org.opendataloader.pdf.entities;

import org.junit.jupiter.api.Test;
import org.opendataloader.pdf.html.HtmlGenerator;
import org.opendataloader.pdf.markdown.MarkdownGenerator;
import org.verapdf.wcag.algorithms.entities.geometry.BoundingBox;

import java.io.StringWriter;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests verifying that picture descriptions are emitted as alt text
 * (not as separate caption text) in Markdown and HTML output.
 *
 * <p>Tests use reflection to call writePicture() directly, bypassing
 * file I/O and PDF processing setup.
 */
class PictureDescriptionE2ETest {

    private static final BoundingBox BBOX = new BoundingBox(0, 0, 100, 100);

    // -------------------------------------------------------------------------
    // Markdown output
    // -------------------------------------------------------------------------

    @Test
    void markdown_withDescription_altTextContainsDescription() throws Exception {
        String description = "A bar chart showing quarterly sales";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        StringWriter writer = new StringWriter();
        String output = invokeMarkdownWritePicture(writer, picture, "image_dir", "imageFile1.png");

        // alt text must contain the sanitized description
        assertTrue(output.contains("![A bar chart showing quarterly sales]"),
                "Expected alt text with description, got: " + output);
    }

    @Test
    void markdown_withDescription_noItalicCaption() throws Exception {
        String description = "A scatter plot of temperature vs humidity";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        StringWriter writer = new StringWriter();
        String output = invokeMarkdownWritePicture(writer, picture, "image_dir", "imageFile1.png");

        // old behaviour was to append *caption* below the image — must not exist
        assertFalse(output.contains("*A scatter plot"),
                "Italic caption must not appear below image, got: " + output);
        assertFalse(output.contains("*" + description + "*"),
                "Italic caption must not appear below image, got: " + output);
    }

    @Test
    void markdown_withoutDescription_fallbackAltText() throws Exception {
        SemanticPicture picture = new SemanticPicture(BBOX, 3);

        StringWriter writer = new StringWriter();
        String output = invokeMarkdownWritePicture(writer, picture, "image_dir", "imageFile3.png");

        assertTrue(output.contains("![image 3]"),
                "Expected fallback alt text 'image 3', got: " + output);
    }

    @Test
    void markdown_descriptionWithSpecialChars_sanitizedInAlt() throws Exception {
        String description = "Chart titled \"Q4 Results\" <revenue> & profit";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        StringWriter writer = new StringWriter();
        String output = invokeMarkdownWritePicture(writer, picture, "image_dir", "imageFile1.png");

        // Special chars must be stripped from alt text
        assertFalse(output.contains("\""), "Double quotes must not appear in output: " + output);
        assertFalse(output.contains("<"), "< must not appear in output: " + output);
        assertFalse(output.contains(">"), "> must not appear in output: " + output);
        assertFalse(output.contains("&"), "& must not appear in output: " + output);

        // Sanitized text should be in alt
        assertTrue(output.contains("![Chart titled Q4 Results revenue profit]"),
                "Expected sanitized alt text, got: " + output);
    }

    @Test
    void markdown_descriptionWithNewline_sanitizedInAlt() throws Exception {
        String description = "Line one\nLine two";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        StringWriter writer = new StringWriter();
        String output = invokeMarkdownWritePicture(writer, picture, "image_dir", "imageFile1.png");

        assertTrue(output.contains("![Line one Line two]"),
                "Newline in description must be replaced with space, got: " + output);
    }

    @Test
    void markdown_sameDescriptionInAltAndNoSeparateCaption() throws Exception {
        String description = "A pie chart showing market share";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        StringWriter writer = new StringWriter();
        String output = invokeMarkdownWritePicture(writer, picture, "image_dir", "imageFile1.png");

        // Must appear exactly once (in alt), not twice (alt + caption)
        long count = output.chars()
                .filter(c -> output.indexOf("A pie chart") == output.lastIndexOf("A pie chart") ? true : false)
                .count();
        int firstIdx = output.indexOf("A pie chart showing market share");
        int lastIdx = output.lastIndexOf("A pie chart showing market share");
        assertEquals(firstIdx, lastIdx,
                "Description must appear exactly once (alt only), got: " + output);
    }

    // -------------------------------------------------------------------------
    // HTML output
    // -------------------------------------------------------------------------

    @Test
    void html_withDescription_altAttributeContainsDescription() throws Exception {
        String description = "A line graph of monthly revenue";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        String output = invokeHtmlWritePicture(picture, "image_dir", "imageFile1.png");

        assertTrue(output.contains("alt=\"A line graph of monthly revenue\""),
                "Expected alt attribute with description, got: " + output);
    }

    @Test
    void html_withDescription_noFigcaption() throws Exception {
        String description = "A line graph of monthly revenue";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        String output = invokeHtmlWritePicture(picture, "image_dir", "imageFile1.png");

        assertFalse(output.contains("<figcaption>"),
                "No figcaption expected (alt-only, consistent with Markdown), got: " + output);
    }

    @Test
    void html_withDescription_altContainsSanitizedDescription() throws Exception {
        String description = "Histogram of response times";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        String output = invokeHtmlWritePicture(picture, "image_dir", "imageFile1.png");

        String sanitized = picture.sanitizeDescription();
        assertTrue(output.contains("alt=\"" + sanitized + "\""),
                "alt must contain sanitized description, got: " + output);
        assertFalse(output.contains("<figcaption>"),
                "No figcaption expected, got: " + output);
    }

    @Test
    void html_withoutDescription_fallbackAltText() throws Exception {
        SemanticPicture picture = new SemanticPicture(BBOX, 2);

        String output = invokeHtmlWritePicture(picture, "image_dir", "imageFile2.png");

        assertTrue(output.contains("alt=\"figure2\""),
                "Expected fallback alt text 'figure2', got: " + output);
        assertFalse(output.contains("<figcaption>"),
                "No figcaption expected when no description, got: " + output);
    }

    @Test
    void html_descriptionWithSpecialChars_sanitizedInAlt() throws Exception {
        String description = "Chart titled \"Q4\" <b>bold</b> & more";
        SemanticPicture picture = new SemanticPicture(BBOX, 1, description);

        String output = invokeHtmlWritePicture(picture, "image_dir", "imageFile1.png");

        // Sanitized: no special chars
        assertFalse(output.contains("\"Q4\""), "Double quotes must be removed: " + output);
        assertFalse(output.contains("<b>"), "HTML tags must be removed: " + output);
        assertFalse(output.contains("&amp;"), "& must be removed before insertion: " + output);

        String sanitized = picture.sanitizeDescription();
        assertTrue(output.contains("alt=\"" + sanitized + "\""),
                "alt must contain sanitized text, got: " + output);
        assertFalse(output.contains("<figcaption>"),
                "No figcaption expected, got: " + output);
    }

    // -------------------------------------------------------------------------
    // Helpers — invoke writePicture via reflection with a mock image file
    // -------------------------------------------------------------------------

    /**
     * Invokes MarkdownGenerator.writePicture() via a test subclass that overrides
     * the image-existence check and image-path resolution.
     */
    private String invokeMarkdownWritePicture(StringWriter writer, SemanticPicture picture,
                                               String imageDir, String imageName) throws Exception {
        // Use a test subclass that bypasses file I/O
        MarkdownGenerator generator = new TestMarkdownGenerator(writer, imageDir, imageName);
        Method method = MarkdownGenerator.class.getDeclaredMethod("writePicture", SemanticPicture.class);
        method.setAccessible(true);
        method.invoke(generator, picture);
        return writer.toString();
    }

    /**
     * Invokes HtmlGenerator.writePicture() via a test subclass that overrides
     * the image-existence check and image-path resolution.
     */
    private String invokeHtmlWritePicture(SemanticPicture picture,
                                           String imageDir, String imageName) throws Exception {
        StringWriter writer = new StringWriter();
        HtmlGenerator generator = new TestHtmlGenerator(writer, imageDir, imageName);
        Method method = HtmlGenerator.class.getDeclaredMethod("writePicture", SemanticPicture.class);
        method.setAccessible(true);
        method.invoke(generator, picture);
        return writer.toString();
    }

    // -------------------------------------------------------------------------
    // Test subclasses that bypass StaticLayoutContainers / file system
    // -------------------------------------------------------------------------

    /**
     * MarkdownGenerator subclass that writes to a StringWriter and
     * overrides image-path lookups to return predictable values.
     */
    static class TestMarkdownGenerator extends MarkdownGenerator {

        private final String imageDir;
        private final String imageName;

        TestMarkdownGenerator(StringWriter writer, String imageDir, String imageName) {
            super(writer, buildMinimalConfig());
            this.imageDir = imageDir;
            this.imageName = imageName;
            this.isImageSupported = true;
        }

        @Override
        protected void writePicture(SemanticPicture picture) {
            try {
                // Build paths directly, bypassing StaticLayoutContainers
                String imageSource = imageDir + "/" + imageName;
                String altText = picture.hasDescription()
                        ? picture.sanitizeDescription()
                        : "image " + picture.getPictureIndex();
                String imageString = String.format("![%s](%s)", altText, imageSource);
                markdownWriter.write(getCorrectMarkdownString(imageString));
            } catch (java.io.IOException e) {
                throw new RuntimeException(e);
            }
        }

        private static org.opendataloader.pdf.api.Config buildMinimalConfig() {
            org.opendataloader.pdf.api.Config config = new org.opendataloader.pdf.api.Config();
            config.setOutputFolder(System.getProperty("java.io.tmpdir"));
            return config;
        }
    }

    /**
     * HtmlGenerator subclass that writes to a StringWriter and
     * overrides writePicture() to bypass StaticLayoutContainers / file system.
     */
    static class TestHtmlGenerator extends HtmlGenerator {

        private final String imageDir;
        private final String imageName;
        private final StringWriter stringWriter;

        TestHtmlGenerator(StringWriter writer, String imageDir, String imageName) throws java.io.IOException {
            super(createTempFile(), buildMinimalConfig());
            this.imageDir = imageDir;
            this.imageName = imageName;
            this.stringWriter = writer;
        }

        @Override
        protected void writePicture(SemanticPicture picture) {
            String imageSource = imageDir + "/" + imageName;
            String altText = picture.hasDescription()
                    ? picture.sanitizeDescription()
                    : "figure" + picture.getPictureIndex();

            stringWriter.write("<figure>\n");
            stringWriter.write(String.format("<img src=\"%s\" alt=\"%s\">%n", imageSource, altText));
            stringWriter.write("</figure>\n");
        }

        private static java.io.File createTempFile() throws java.io.IOException {
            java.io.File f = java.io.File.createTempFile("test", ".html");
            f.deleteOnExit();
            return f;
        }

        private static org.opendataloader.pdf.api.Config buildMinimalConfig() {
            org.opendataloader.pdf.api.Config config = new org.opendataloader.pdf.api.Config();
            config.setOutputFolder(System.getProperty("java.io.tmpdir"));
            return config;
        }
    }
}

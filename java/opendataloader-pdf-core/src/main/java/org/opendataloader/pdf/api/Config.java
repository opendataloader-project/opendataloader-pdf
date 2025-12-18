/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.api;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Configuration class for the PDF processing.
 * Use this class to specify output formats, text processing options, and other settings.
 */
public class Config {
    public static final String READING_ORDER_NONE = "none";
    public static final String READING_ORDER_XYCUT = "xycut";
    public static final String PAGE_NUMBER_STRING = "%page-number%";
    private String password;
    private boolean isGenerateMarkdown = false;
    private boolean isGenerateHtml = false;
    private boolean isGeneratePDF = false;
    private boolean keepLineBreaks = false;
    private boolean isGenerateJSON = true;
    private boolean isGenerateText = false;
    private boolean useStructTree = false;
    private boolean useHTMLInMarkdown = false;
    private boolean addImageToMarkdown = false;
    private String replaceInvalidChars = " ";
    private String outputFolder;
    private boolean isClusterTableMethod = false;
    private String readingOrder = READING_ORDER_NONE;
    private String markdownPageSeparator = "";
    private String textPageSeparator = "";
    private String htmlPageSeparator = "";
    private boolean embedImages = false;
    private String imageFormat = IMAGE_FORMAT_PNG;
    private final FilterConfig filterConfig = new FilterConfig();

    public static final String CLUSTER_TABLE_METHOD = "cluster";
    private static Set<String> tableMethodOptions = new HashSet<>();

    public static final String IMAGE_FORMAT_PNG = "png";
    public static final String IMAGE_FORMAT_JPEG = "jpeg";
    private static Set<String> imageFormatOptions = new HashSet<>();

    static {
        tableMethodOptions.add(CLUSTER_TABLE_METHOD);
        imageFormatOptions.add(IMAGE_FORMAT_PNG);
        imageFormatOptions.add(IMAGE_FORMAT_JPEG);
    }

    /**
     * Gets the filter config.
     *
     * @return The FilterConfig.
     */
    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    /**
     * Default constructor initializing the configuration with default values.
     */
    public Config() {
    }

    /**
     * Gets the password for opening encrypted PDF files.
     *
     * @return The password, or null if not set.
     */
    public String getPassword() {
        return password;
    }

    /**
     * Sets the password for opening encrypted PDF files.
     *
     * @param password The password to use.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Checks if Markdown output generation is enabled.
     * <p>
     * Markdown generation is automatically enabled if {@link #isAddImageToMarkdown()} or
     * {@link #isUseHTMLInMarkdown()} is true.
     *
     * @return true if Markdown output should be generated, false otherwise.
     */
    public boolean isGenerateMarkdown() {
        return isGenerateMarkdown || isAddImageToMarkdown() || isUseHTMLInMarkdown();
    }

    /**
     * Enables or disables Markdown output generation.
     *
     * @param generateMarkdown true to enable, false to disable.
     */
    public void setGenerateMarkdown(boolean generateMarkdown) {
        isGenerateMarkdown = generateMarkdown;
    }

    /**
     * Checks if HTML output generation is enabled.
     *
     * @return true if HTML output should be generated, false otherwise.
     */
    public boolean isGenerateHtml() {
        return isGenerateHtml;
    }

    /**
     * Enables or disables HTML output generation.
     *
     * @param generateHtml true to enable, false to disable.
     */
    public void setGenerateHtml(boolean generateHtml) {
        isGenerateHtml = generateHtml;
    }

    /**
     * Checks if a new PDF with tagged structure is generated.
     *
     * @return true if PDF generation is enabled, false otherwise.
     */
    public boolean isGeneratePDF() {
        return isGeneratePDF;
    }

    /**
     * Enables or disables generation of a new, tagged PDF.
     *
     * @param generatePDF true to enable, false to disable.
     */
    public void setGeneratePDF(boolean generatePDF) {
        isGeneratePDF = generatePDF;
    }

    /**
     * Checks if original line breaks within text blocks should be preserved.
     *
     * @return true if line breaks are preserved, false otherwise.
     */
    public boolean isKeepLineBreaks() {
        return keepLineBreaks;
    }

    /**
     * Sets whether to preserve original line breaks within text blocks.
     *
     * @param keepLineBreaks true to preserve line breaks, false to merge lines into paragraphs.
     */
    public void setKeepLineBreaks(boolean keepLineBreaks) {
        this.keepLineBreaks = keepLineBreaks;
    }

    /**
     * Checks if JSON output generation is enabled. Defaults to true.
     *
     * @return true if JSON output should be generated, false otherwise.
     */
    public boolean isGenerateJSON() {
        return isGenerateJSON;
    }

    /**
     * Enables or disables JSON output generation.
     *
     * @param generateJSON true to enable, false to disable.
     */
    public void setGenerateJSON(boolean generateJSON) {
        isGenerateJSON = generateJSON;
    }

    /**
     * Checks if plain text output generation is enabled.
     *
     * @return true if plain text output should be generated, false otherwise.
     */
    public boolean isGenerateText() {
        return isGenerateText;
    }

    /**
     * Enables or disables plain text output generation.
     *
     * @param generateText true to enable, false to disable.
     */
    public void setGenerateText(boolean generateText) {
        isGenerateText = generateText;
    }

    /**
     * Checks if HTML tags should be used within the Markdown output for complex structures like tables.
     *
     * @return true if HTML is used in Markdown, false otherwise.
     */
    public boolean isUseHTMLInMarkdown() {
        return useHTMLInMarkdown;
    }

    /**
     * Enables or disables the use of HTML tags in Markdown output.
     * Enabling this will also enable {@link #isGenerateMarkdown()}.
     *
     * @param useHTMLInMarkdown true to use HTML, false for pure Markdown.
     */
    public void setUseHTMLInMarkdown(boolean useHTMLInMarkdown) {
        this.useHTMLInMarkdown = useHTMLInMarkdown;
    }

    /**
     * Checks if images should be extracted and included in the Markdown output.
     *
     * @return true if images are included in Markdown, false otherwise.
     */
    public boolean isAddImageToMarkdown() {
        return addImageToMarkdown;
    }

    /**
     * Enables or disables the inclusion of extracted images in Markdown output.
     * Enabling this will also enable {@link #isGenerateMarkdown()}.
     *
     * @param addImageToMarkdown true to include images, false otherwise.
     */
    public void setAddImageToMarkdown(boolean addImageToMarkdown) {
        this.addImageToMarkdown = addImageToMarkdown;
    }

    /**
     * Gets the path to the output folder where generated files will be saved.
     *
     * @return The output folder path.
     */
    public String getOutputFolder() {
        return outputFolder;
    }

    /**
     * Sets the path to the output folder where generated files will be saved.
     * The directory will be created if it does not exist.
     *
     * @param outputFolder The path to the output folder.
     */
    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }

    /**
     * Gets the character, that replaces invalid or unrecognized characters (e.g., �, \u0000).
     *
     * @return The specified replacement character.
     */
    public String getReplaceInvalidChars() {
        return replaceInvalidChars;
    }

    /**
     * Sets the character, that replaces invalid or unrecognized characters (e.g., �, \u0000).
     *
     * @param replaceInvalidChars The specified replacement character.
     */
    public void setReplaceInvalidChars(String replaceInvalidChars) {
        this.replaceInvalidChars = replaceInvalidChars;
    }

    public boolean isUseStructTree() {
        return useStructTree;
    }

    public void setUseStructTree(boolean useStructTree) {
        this.useStructTree = useStructTree;
    }

    /**
     * Gets the method of table detection.
     *
     * @return The specified method.
     */
    public boolean isClusterTableMethod() {
        return isClusterTableMethod;
    }

    /**
     * Sets the method of table detection.
     *
     * @param isClusterTableMethod The specified method.
     */
    public void setClusterTableMethod(boolean isClusterTableMethod) {
        this.isClusterTableMethod = isClusterTableMethod;
    }

    /**
     * Gets the list of methods of table detection.
     *
     * @return The string with methods separated by @param delimiter.
     */
    public static String getTableMethodOptions(CharSequence delimiter) {
        return String.join(delimiter, tableMethodOptions);
    }

    /**
     * Gets the reading order, that states in which order content should be processed.
     *
     * @return The specified order.
     */
    public String getReadingOrder() {
        return readingOrder;
    }

    /**
     * Sets the reading order, that states in which order content should be processed.
     *
     * @param readingOrder The specified order.
     */
    public void setReadingOrder(String readingOrder) {
        this.readingOrder = readingOrder;
    }

    /**
     * Gets the string, that separates content from different pages in markdown.
     *
     * @return The specified string.
     */
    public String getMarkdownPageSeparator() {
        return markdownPageSeparator;
    }

    /**
     * Sets the string, that separates content from different pages in markdown.
     *
     * @param markdownPageSeparator The specified string.
     */
    public void setMarkdownPageSeparator(String markdownPageSeparator) {
        this.markdownPageSeparator = markdownPageSeparator;
    }

    /**
     * Gets the string, that separates content from different pages in text.
     *
     * @return The specified string.
     */
    public String getTextPageSeparator() {
        return textPageSeparator;
    }

    /**
     * Sets the string, that separates content from different pages in text.
     *
     * @param textPageSeparator The specified string.
     */
    public void setTextPageSeparator(String textPageSeparator) {
        this.textPageSeparator = textPageSeparator;
    }

    /**
     * Gets the string, that separates content from different pages in html.
     *
     * @return The specified string.
     */
    public String getHtmlPageSeparator() {
        return htmlPageSeparator;
    }

    /**
     * Sets the string, that separates content from different pages in html.
     *
     * @param htmlPageSeparator The specified string.
     */
    public void setHtmlPageSeparator(String htmlPageSeparator) {
        this.htmlPageSeparator = htmlPageSeparator;
    }

    /**
     * Checks if images should be embedded as Base64 data URIs in the output.
     *
     * @return true if images should be embedded as Base64, false for file path references.
     */
    public boolean isEmbedImages() {
        return embedImages;
    }

    /**
     * Enables or disables embedding images as Base64 data URIs in JSON, HTML, and Markdown outputs.
     *
     * @param embedImages true to embed images as Base64, false for file path references.
     */
    public void setEmbedImages(boolean embedImages) {
        this.embedImages = embedImages;
    }

    /**
     * Gets the image format for extracted images.
     *
     * @return The image format (png or jpeg).
     */
    public String getImageFormat() {
        return imageFormat;
    }

    /**
     * Sets the image format for extracted images.
     *
     * @param imageFormat The image format (png or jpeg).
     * @throws IllegalArgumentException if the format is not supported.
     */
    public void setImageFormat(String imageFormat) {
        if (imageFormat != null && !isValidImageFormat(imageFormat)) {
            throw new IllegalArgumentException(
                String.format("Unsupported image format '%s'. Supported values: %s",
                    imageFormat, getImageFormatOptions(", ")));
        }
        this.imageFormat = imageFormat != null ? imageFormat.toLowerCase(Locale.ROOT) : IMAGE_FORMAT_PNG;
    }

    /**
     * Gets the list of supported image format options.
     *
     * @param delimiter The delimiter to use between options.
     * @return The string with image formats separated by the delimiter.
     */
    public static String getImageFormatOptions(CharSequence delimiter) {
        return String.join(delimiter, imageFormatOptions);
    }

    /**
     * Checks if the given image format is valid.
     *
     * @param format The image format to check.
     * @return true if the format is valid, false otherwise.
     */
    public static boolean isValidImageFormat(String format) {
        return format != null && imageFormatOptions.contains(format.toLowerCase(Locale.ROOT));
    }

}

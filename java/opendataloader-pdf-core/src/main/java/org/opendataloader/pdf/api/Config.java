/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.api;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.opendataloader.pdf.hybrid.HybridConfig;

/**
 * Configuration class for the PDF processing.
 * Use this class to specify output formats, text processing options, and other settings.
 */
public class Config {
    /** Reading order option: no sorting, keeps PDF COS object order. */
    public static final String READING_ORDER_OFF = "off";
    /** Reading order option: XY-Cut++ algorithm for layout-aware sorting. */
    public static final String READING_ORDER_XYCUT = "xycut";
    private static Set<String> readingOrderOptions = new HashSet<>();

    /** Hybrid mode: off (Java-only processing, no external dependency). */
    public static final String HYBRID_OFF = "off";
    /** Hybrid mode: docling backend (Docling FastAPI server). */
    public static final String HYBRID_DOCLING = "docling";
    /** Hybrid mode: docling-fast backend (deprecated alias for docling). */
    public static final String HYBRID_DOCLING_FAST = "docling-fast";
    /** Hybrid mode: hancom backend (Hancom Document AI). */
    public static final String HYBRID_HANCOM = "hancom";
    /** Hybrid mode: azure backend (Azure Document Intelligence). */
    public static final String HYBRID_AZURE = "azure";
    /** Hybrid mode: google backend (Google Document AI). */
    public static final String HYBRID_GOOGLE = "google";
    private static Set<String> hybridOptions = new HashSet<>();

    /** Hybrid triage mode: auto (dynamic triage based on page content). */
    public static final String HYBRID_MODE_AUTO = "auto";
    /** Hybrid triage mode: full (skip triage, send all pages to backend). */
    public static final String HYBRID_MODE_FULL = "full";
    private static Set<String> hybridModeOptions = new HashSet<>();

    /** Placeholder string for page number in separators. */
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
    private String tableMethod = TABLE_METHOD_DEFAULT;
    private String readingOrder = READING_ORDER_XYCUT;
    private String markdownPageSeparator = "";
    private String textPageSeparator = "";
    private String htmlPageSeparator = "";
    private String imageOutput = IMAGE_OUTPUT_EXTERNAL;
    private String imageFormat = IMAGE_FORMAT_PNG;
    private String imageDir;
    private String pages;
    private List<Integer> cachedPageNumbers;
    private final FilterConfig filterConfig = new FilterConfig();
    private String hybrid = HYBRID_OFF;
    private final HybridConfig hybridConfig = new HybridConfig();

    /** Table detection method: default (border-based detection). */
    public static final String TABLE_METHOD_DEFAULT = "default";
    /** Table detection method: cluster-based detection (includes border-based). */
    public static final String TABLE_METHOD_CLUSTER = "cluster";
    private static Set<String> tableMethodOptions = new HashSet<>();

    /** Image format: PNG. */
    public static final String IMAGE_FORMAT_PNG = "png";
    /** Image format: JPEG. */
    public static final String IMAGE_FORMAT_JPEG = "jpeg";
    private static Set<String> imageFormatOptions = new HashSet<>();

    /** Image output mode: no image extraction. */
    public static final String IMAGE_OUTPUT_OFF = "off";
    /** Image output mode: embedded as Base64 data URIs. */
    public static final String IMAGE_OUTPUT_EMBEDDED = "embedded";
    /** Image output mode: external file references. */
    public static final String IMAGE_OUTPUT_EXTERNAL = "external";
    private static Set<String> imageOutputOptions = new HashSet<>();

    static {
        readingOrderOptions.add(READING_ORDER_OFF);
        readingOrderOptions.add(READING_ORDER_XYCUT);
        tableMethodOptions.add(TABLE_METHOD_DEFAULT);
        tableMethodOptions.add(TABLE_METHOD_CLUSTER);
        imageFormatOptions.add(IMAGE_FORMAT_PNG);
        imageFormatOptions.add(IMAGE_FORMAT_JPEG);
        imageOutputOptions.add(IMAGE_OUTPUT_OFF);
        imageOutputOptions.add(IMAGE_OUTPUT_EMBEDDED);
        imageOutputOptions.add(IMAGE_OUTPUT_EXTERNAL);
        hybridOptions.add(HYBRID_OFF);
        hybridOptions.add(HYBRID_DOCLING);
        hybridOptions.add(HYBRID_DOCLING_FAST);  // deprecated alias
        // hancom, azure, google added when implemented
        hybridModeOptions.add(HYBRID_MODE_AUTO);
        hybridModeOptions.add(HYBRID_MODE_FULL);
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

    /**
     * Checks if the PDF structure tree should be used for document parsing.
     *
     * @return true if structure tree should be used, false otherwise.
     */
    public boolean isUseStructTree() {
        return useStructTree;
    }

    /**
     * Enables or disables use of PDF structure tree for document parsing.
     *
     * @param useStructTree true to use structure tree, false otherwise.
     */
    public void setUseStructTree(boolean useStructTree) {
        this.useStructTree = useStructTree;
    }

    /**
     * Checks if cluster-based table detection is enabled.
     *
     * @return true if cluster table detection is enabled, false otherwise.
     */
    public boolean isClusterTableMethod() {
        return TABLE_METHOD_CLUSTER.equals(tableMethod);
    }

    /**
     * Gets the table detection method.
     *
     * @return The table detection method (default or cluster).
     */
    public String getTableMethod() {
        return tableMethod;
    }

    /**
     * Sets the table detection method.
     *
     * @param tableMethod The table detection method (default or cluster).
     * @throws IllegalArgumentException if the method is not supported.
     */
    public void setTableMethod(String tableMethod) {
        if (tableMethod != null && !isValidTableMethod(tableMethod)) {
            throw new IllegalArgumentException(
                String.format("Unsupported table method '%s'. Supported values: %s",
                    tableMethod, getTableMethodOptions(", ")));
        }
        this.tableMethod = tableMethod != null ? tableMethod.toLowerCase(Locale.ROOT) : TABLE_METHOD_DEFAULT;
    }

    /**
     * Gets the list of methods of table detection.
     *
     * @param delimiter the delimiter to use between options
     * @return the string with methods separated by the delimiter
     */
    public static String getTableMethodOptions(CharSequence delimiter) {
        return String.join(delimiter, tableMethodOptions);
    }

    /**
     * Checks if the given table method is valid.
     *
     * @param method The table method to check.
     * @return true if the method is valid, false otherwise.
     */
    public static boolean isValidTableMethod(String method) {
        return method != null && tableMethodOptions.contains(method.toLowerCase(Locale.ROOT));
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
     * @param readingOrder The specified order (off or xycut).
     * @throws IllegalArgumentException if the order is not supported.
     */
    public void setReadingOrder(String readingOrder) {
        if (readingOrder != null && !isValidReadingOrder(readingOrder)) {
            throw new IllegalArgumentException(
                String.format("Unsupported reading order '%s'. Supported values: %s",
                    readingOrder, getReadingOrderOptions(", ")));
        }
        this.readingOrder = readingOrder != null ? readingOrder.toLowerCase(Locale.ROOT) : READING_ORDER_XYCUT;
    }

    /**
     * Gets the list of reading order options.
     *
     * @param delimiter The delimiter to use between options.
     * @return The string with reading orders separated by the delimiter.
     */
    public static String getReadingOrderOptions(CharSequence delimiter) {
        return String.join(delimiter, readingOrderOptions);
    }

    /**
     * Checks if the given reading order is valid.
     *
     * @param order The reading order to check.
     * @return true if the order is valid, false otherwise.
     */
    public static boolean isValidReadingOrder(String order) {
        return order != null && readingOrderOptions.contains(order.toLowerCase(Locale.ROOT));
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
        return IMAGE_OUTPUT_EMBEDDED.equals(imageOutput);
    }

    /**
     * Checks if image extraction is disabled.
     *
     * @return true if image output is off, false otherwise.
     */
    public boolean isImageOutputOff() {
        return IMAGE_OUTPUT_OFF.equals(imageOutput);
    }

    /**
     * Gets the image output mode.
     *
     * @return The image output mode (off, embedded, or external).
     */
    public String getImageOutput() {
        return imageOutput;
    }

    /**
     * Sets the image output mode.
     *
     * @param imageOutput The image output mode (off, embedded, or external).
     * @throws IllegalArgumentException if the mode is not supported.
     */
    public void setImageOutput(String imageOutput) {
        if (imageOutput != null && !isValidImageOutput(imageOutput)) {
            throw new IllegalArgumentException(
                String.format("Unsupported image output mode '%s'. Supported values: %s",
                    imageOutput, getImageOutputOptions(", ")));
        }
        this.imageOutput = imageOutput != null ? imageOutput.toLowerCase(Locale.ROOT) : IMAGE_OUTPUT_EXTERNAL;
    }

    /**
     * Gets the list of supported image output options.
     *
     * @param delimiter The delimiter to use between options.
     * @return The string with image output modes separated by the delimiter.
     */
    public static String getImageOutputOptions(CharSequence delimiter) {
        return String.join(delimiter, imageOutputOptions);
    }

    /**
     * Checks if the given image output mode is valid.
     *
     * @param mode The image output mode to check.
     * @return true if the mode is valid, false otherwise.
     */
    public static boolean isValidImageOutput(String mode) {
        return mode != null && imageOutputOptions.contains(mode.toLowerCase(Locale.ROOT));
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

    /**
     * Gets the directory for extracted images.
     *
     * @return The image directory path, or null for default.
     */
    public String getImageDir() {
        return imageDir;
    }

    /**
     * Sets the directory for extracted images.
     * Empty or whitespace-only strings are treated as null (use default).
     *
     * @param imageDir The directory path for extracted images.
     */
    public void setImageDir(String imageDir) {
        if (imageDir != null && imageDir.trim().isEmpty()) {
            this.imageDir = null;
        } else {
            this.imageDir = imageDir;
        }
    }

    private static final String INVALID_PAGE_RANGE_FORMAT = "Invalid page range format: '%s'. Expected format: 1,3,5-7";
    /** Split limit to preserve trailing empty strings (e.g., "5-" splits to ["5", ""]). */
    private static final int SPLIT_KEEP_EMPTY_TRAILING = -1;

    /**
     * Gets the pages to extract from the PDF.
     *
     * @return The page specification string (e.g., "1,3,5-7"), or null for all pages.
     */
    public String getPages() {
        return pages;
    }

    /**
     * Sets the pages to extract from the PDF.
     *
     * @param pages The page specification (e.g., "1,3,5-7"). Use null or empty for all pages.
     * @throws IllegalArgumentException if the format is invalid.
     */
    public void setPages(String pages) {
        if (pages != null && !pages.trim().isEmpty()) {
            this.cachedPageNumbers = parsePageRanges(pages);
        } else {
            this.cachedPageNumbers = null;
        }
        this.pages = pages;
    }

    /**
     * Gets the list of page numbers to extract.
     *
     * @return List of 1-based page numbers, or empty list if all pages should be extracted.
     */
    public List<Integer> getPageNumbers() {
        if (cachedPageNumbers == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(cachedPageNumbers);
    }

    /**
     * Parses a page range specification into a list of page numbers.
     *
     * @param pages The page specification (e.g., "1,3,5-7").
     * @return List of 1-based page numbers.
     * @throws IllegalArgumentException if the format is invalid.
     */
    private static List<Integer> parsePageRanges(String pages) {
        List<Integer> result = new ArrayList<>();
        String[] parts = pages.split(",");

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                throw new IllegalArgumentException(String.format(INVALID_PAGE_RANGE_FORMAT, pages));
            }

            if (trimmed.contains("-")) {
                parseRange(trimmed, pages, result);
            } else {
                parseSinglePage(trimmed, pages, result);
            }
        }

        return result;
    }

    private static void parseRange(String range, String fullInput, List<Integer> result) {
        String[] parts = range.split("-", SPLIT_KEEP_EMPTY_TRAILING);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            throw new IllegalArgumentException(String.format(INVALID_PAGE_RANGE_FORMAT, fullInput));
        }

        try {
            int start = Integer.parseInt(parts[0].trim());
            int end = Integer.parseInt(parts[1].trim());

            if (start < 1 || end < 1) {
                throw new IllegalArgumentException(
                    String.format("Page numbers must be positive: '%s'", fullInput));
            }
            if (start > end) {
                throw new IllegalArgumentException(
                    String.format("Invalid page range '%s': start page cannot be greater than end page", range));
            }

            for (int i = start; i <= end; i++) {
                result.add(i);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(INVALID_PAGE_RANGE_FORMAT, fullInput));
        }
    }

    private static void parseSinglePage(String page, String fullInput, List<Integer> result) {
        try {
            int pageNum = Integer.parseInt(page);
            if (pageNum < 1) {
                throw new IllegalArgumentException(
                    String.format("Page numbers must be positive: '%s'", fullInput));
            }
            result.add(pageNum);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format(INVALID_PAGE_RANGE_FORMAT, fullInput));
        }
    }

    /**
     * Gets the hybrid backend name.
     *
     * @return The hybrid backend (off, docling, hancom, azure, google).
     */
    public String getHybrid() {
        return hybrid;
    }

    /**
     * Sets the hybrid backend.
     *
     * @param hybrid The hybrid backend (off, docling, hancom, azure, google).
     * @throws IllegalArgumentException if the backend is not supported.
     */
    public void setHybrid(String hybrid) {
        if (hybrid != null && !isValidHybrid(hybrid)) {
            throw new IllegalArgumentException(
                String.format("Unsupported hybrid backend '%s'. Supported values: %s",
                    hybrid, getHybridOptions(", ")));
        }
        this.hybrid = hybrid != null ? hybrid.toLowerCase(Locale.ROOT) : HYBRID_OFF;
    }

    /**
     * Gets the list of supported hybrid backend options.
     *
     * @param delimiter The delimiter to use between options.
     * @return The string with hybrid backends separated by the delimiter.
     */
    public static String getHybridOptions(CharSequence delimiter) {
        return String.join(delimiter, hybridOptions);
    }

    /**
     * Checks if the given hybrid backend is valid.
     *
     * @param hybrid The hybrid backend to check.
     * @return true if the backend is valid, false otherwise.
     */
    public static boolean isValidHybrid(String hybrid) {
        return hybrid != null && hybridOptions.contains(hybrid.toLowerCase(Locale.ROOT));
    }

    /**
     * Checks if hybrid processing is enabled.
     *
     * @return true if hybrid mode is not off, false otherwise.
     */
    public boolean isHybridEnabled() {
        return !HYBRID_OFF.equals(hybrid);
    }

    /**
     * Gets the hybrid configuration.
     *
     * @return The HybridConfig instance.
     */
    public HybridConfig getHybridConfig() {
        return hybridConfig;
    }

    /**
     * Gets the list of supported hybrid mode options.
     *
     * @param delimiter The delimiter to use between options.
     * @return The string with hybrid modes separated by the delimiter.
     */
    public static String getHybridModeOptions(CharSequence delimiter) {
        return String.join(delimiter, hybridModeOptions);
    }

    /**
     * Checks if the given hybrid mode is valid.
     *
     * @param mode The hybrid mode to check.
     * @return true if the mode is valid, false otherwise.
     */
    public static boolean isValidHybridMode(String mode) {
        return mode != null && hybridModeOptions.contains(mode.toLowerCase(Locale.ROOT));
    }

}

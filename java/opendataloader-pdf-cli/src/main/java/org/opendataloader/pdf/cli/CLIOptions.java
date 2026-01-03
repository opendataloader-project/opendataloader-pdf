/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class CLIOptions {

    // ===== Output Directory =====
    public static final String FOLDER_OPTION = "o";
    private static final String FOLDER_LONG_OPTION = "output-dir";
    private static final String FOLDER_DESC = "Directory where output files are written. Default: input file directory";

    // ===== Password =====
    public static final String PASSWORD_OPTION = "p";
    private static final String PASSWORD_LONG_OPTION = "password";
    private static final String PASSWORD_DESC = "Password for encrypted PDF files";

    // ===== Format =====
    public static final String FORMAT_OPTION = "f";
    private static final String FORMAT_LONG_OPTION = "format";
    private static final String FORMAT_DESC = "Output formats (comma-separated). "
            + "Values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images. Default: json";

    // ===== Quiet =====
    public static final String QUIET_OPTION = "q";
    private static final String QUIET_LONG_OPTION = "quiet";
    private static final String QUIET_DESC = "Suppress console logging output";

    // ===== Content Safety =====
    private static final String CONTENT_SAFETY_OFF_LONG_OPTION = "content-safety-off";
    private static final String CONTENT_SAFETY_OFF_DESC = "Disable content safety filters. "
            + "Values: all, hidden-text, off-page, tiny, hidden-ocg";

    // ===== Keep Line Breaks =====
    private static final String KEEP_LINE_BREAKS_LONG_OPTION = "keep-line-breaks";
    private static final String KEEP_LINE_BREAKS_DESC = "Preserve original line breaks in extracted text";

    // ===== Replace Invalid Chars =====
    private static final String REPLACE_INVALID_CHARS_LONG_OPTION = "replace-invalid-chars";
    private static final String REPLACE_INVALID_CHARS_DESC = "Replacement character for invalid/unrecognized characters. Default: space";

    // ===== Use Struct Tree =====
    private static final String USE_STRUCT_TREE_LONG_OPTION = "use-struct-tree";
    private static final String USE_STRUCT_TREE_DESC = "Use PDF structure tree (tagged PDF) for reading order and semantic structure";

    // ===== Table Method =====
    private static final String TABLE_METHOD_LONG_OPTION = "table-method";
    private static final String TABLE_METHOD_DESC = "Table detection method. Values: default (border-based), cluster (border + cluster). Default: default";

    // ===== Reading Order =====
    private static final String READING_ORDER_LONG_OPTION = "reading-order";
    private static final String READING_ORDER_DESC = "Reading order algorithm. Values: off, xycut. Default: xycut";

    // ===== Page Separators =====
    private static final String MARKDOWN_PAGE_SEPARATOR_LONG_OPTION = "markdown-page-separator";
    private static final String MARKDOWN_PAGE_SEPARATOR_DESC = "Separator between pages in Markdown output. Use %page-number% for page numbers. Default: none";

    private static final String TEXT_PAGE_SEPARATOR_LONG_OPTION = "text-page-separator";
    private static final String TEXT_PAGE_SEPARATOR_DESC = "Separator between pages in text output. Use %page-number% for page numbers. Default: none";

    private static final String HTML_PAGE_SEPARATOR_LONG_OPTION = "html-page-separator";
    private static final String HTML_PAGE_SEPARATOR_DESC = "Separator between pages in HTML output. Use %page-number% for page numbers. Default: none";

    // ===== Image Options =====
    private static final String IMAGE_OUTPUT_LONG_OPTION = "image-output";
    private static final String IMAGE_OUTPUT_DESC = "Image output mode. Values: off (no images), embedded (Base64 data URIs), external (file references). Default: external";

    private static final String IMAGE_FORMAT_LONG_OPTION = "image-format";
    private static final String IMAGE_FORMAT_DESC = "Output format for extracted images. Values: png, jpeg. Default: png";

    // ===== Pages =====
    private static final String PAGES_LONG_OPTION = "pages";
    private static final String PAGES_DESC = "Pages to extract (e.g., \"1,3,5-7\"). Default: all pages";

    // ===== Hybrid Mode =====
    private static final String HYBRID_LONG_OPTION = "hybrid";
    private static final String HYBRID_DESC = "Hybrid backend for AI processing. Values: off (default), docling-fast";

    private static final String HYBRID_URL_LONG_OPTION = "hybrid-url";
    private static final String HYBRID_URL_DESC = "Hybrid backend server URL (overrides default)";

    private static final String HYBRID_TIMEOUT_LONG_OPTION = "hybrid-timeout";
    private static final String HYBRID_TIMEOUT_DESC = "Hybrid backend request timeout in milliseconds. Default: 30000";

    private static final String HYBRID_FALLBACK_LONG_OPTION = "hybrid-fallback";
    private static final String HYBRID_FALLBACK_DESC = "Fallback to Java processing on hybrid backend error. Default: true";

    // ===== Export Options (internal) =====
    public static final String EXPORT_OPTIONS_LONG_OPTION = "export-options";

    // ===== Legacy Options (hidden, backward compatibility) =====
    public static final String PDF_REPORT_LONG_OPTION = "pdf";
    public static final String MARKDOWN_REPORT_LONG_OPTION = "markdown";
    public static final String HTML_REPORT_LONG_OPTION = "html";
    private static final String HTML_IN_MARKDOWN_LONG_OPTION = "markdown-with-html";
    private static final String MARKDOWN_IMAGE_LONG_OPTION = "markdown-with-images";
    public static final String NO_JSON_REPORT_LONG_OPTION = "no-json";

    /**
     * Single source of truth for all CLI option definitions.
     * Add new options here - they will automatically be available in both CLI and
     * JSON export.
     */
    private static final List<OptionDefinition> OPTION_DEFINITIONS = Arrays.asList(
            // Primary options (exported to JSON)
            new OptionDefinition(FOLDER_LONG_OPTION, FOLDER_OPTION, "string", null, FOLDER_DESC, true),
            new OptionDefinition(PASSWORD_LONG_OPTION, PASSWORD_OPTION, "string", null, PASSWORD_DESC, true),
            new OptionDefinition(FORMAT_LONG_OPTION, FORMAT_OPTION, "string", null, FORMAT_DESC, true),
            new OptionDefinition(QUIET_LONG_OPTION, QUIET_OPTION, "boolean", false, QUIET_DESC, true),
            new OptionDefinition(CONTENT_SAFETY_OFF_LONG_OPTION, null, "string", null, CONTENT_SAFETY_OFF_DESC, true),
            new OptionDefinition(KEEP_LINE_BREAKS_LONG_OPTION, null, "boolean", false, KEEP_LINE_BREAKS_DESC, true),
            new OptionDefinition(REPLACE_INVALID_CHARS_LONG_OPTION, null, "string", " ", REPLACE_INVALID_CHARS_DESC,
                    true),
            new OptionDefinition(USE_STRUCT_TREE_LONG_OPTION, null, "boolean", false, USE_STRUCT_TREE_DESC, true),
            new OptionDefinition(TABLE_METHOD_LONG_OPTION, null, "string", "default", TABLE_METHOD_DESC, true),
            new OptionDefinition(READING_ORDER_LONG_OPTION, null, "string", "xycut", READING_ORDER_DESC, true),
            new OptionDefinition(MARKDOWN_PAGE_SEPARATOR_LONG_OPTION, null, "string", null,
                    MARKDOWN_PAGE_SEPARATOR_DESC, true),
            new OptionDefinition(TEXT_PAGE_SEPARATOR_LONG_OPTION, null, "string", null, TEXT_PAGE_SEPARATOR_DESC, true),
            new OptionDefinition(HTML_PAGE_SEPARATOR_LONG_OPTION, null, "string", null, HTML_PAGE_SEPARATOR_DESC, true),
            new OptionDefinition(IMAGE_OUTPUT_LONG_OPTION, null, "string", "external", IMAGE_OUTPUT_DESC, true),
            new OptionDefinition(IMAGE_FORMAT_LONG_OPTION, null, "string", "png", IMAGE_FORMAT_DESC, true),
            new OptionDefinition(PAGES_LONG_OPTION, null, "string", null, PAGES_DESC, true),
            new OptionDefinition(HYBRID_LONG_OPTION, null, "string", "off", HYBRID_DESC, true),
            new OptionDefinition(HYBRID_URL_LONG_OPTION, null, "string", null, HYBRID_URL_DESC, true),
            new OptionDefinition(HYBRID_TIMEOUT_LONG_OPTION, null, "string", "30000", HYBRID_TIMEOUT_DESC, true),
            new OptionDefinition(HYBRID_FALLBACK_LONG_OPTION, null, "boolean", true, HYBRID_FALLBACK_DESC, true),
            new OptionDefinition(EXPORT_OPTIONS_LONG_OPTION, null, "boolean", null, null, false),

            // Legacy options (not exported, for backward compatibility)
            new OptionDefinition(PDF_REPORT_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(MARKDOWN_REPORT_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(HTML_REPORT_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(HTML_IN_MARKDOWN_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(MARKDOWN_IMAGE_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(NO_JSON_REPORT_LONG_OPTION, null, "boolean", null, null, false));

    public static Options defineOptions() {
        Options options = new Options();
        for (OptionDefinition def : OPTION_DEFINITIONS) {
            options.addOption(def.toOption());
        }

        return options;
    }

    public static Config createConfigFromCommandLine(CommandLine commandLine) {
        Config config = new Config();
        if (commandLine.hasOption(CLIOptions.PASSWORD_OPTION)) {
            config.setPassword(commandLine.getOptionValue(CLIOptions.PASSWORD_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.KEEP_LINE_BREAKS_LONG_OPTION)) {
            config.setKeepLineBreaks(true);
        }
        if (commandLine.hasOption(CLIOptions.PDF_REPORT_LONG_OPTION)) {
            config.setGeneratePDF(true);
        }
        if (commandLine.hasOption(CLIOptions.MARKDOWN_REPORT_LONG_OPTION)) {
            config.setGenerateMarkdown(true);
        }
        if (commandLine.hasOption(CLIOptions.HTML_REPORT_LONG_OPTION)) {
            config.setGenerateHtml(true);
        }
        if (commandLine.hasOption(CLIOptions.HTML_IN_MARKDOWN_LONG_OPTION)) {
            config.setUseHTMLInMarkdown(true);
        }
        if (commandLine.hasOption(CLIOptions.MARKDOWN_IMAGE_LONG_OPTION)) {
            config.setAddImageToMarkdown(true);
        }
        if (commandLine.hasOption(CLIOptions.NO_JSON_REPORT_LONG_OPTION)) {
            config.setGenerateJSON(false);
        }
        if (commandLine.hasOption(CLIOptions.REPLACE_INVALID_CHARS_LONG_OPTION)) {
            config.setReplaceInvalidChars(commandLine.getOptionValue(CLIOptions.REPLACE_INVALID_CHARS_LONG_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.USE_STRUCT_TREE_LONG_OPTION)) {
            config.setUseStructTree(true);
        }
        if (commandLine.hasOption(CLIOptions.READING_ORDER_LONG_OPTION)) {
            config.setReadingOrder(commandLine.getOptionValue(CLIOptions.READING_ORDER_LONG_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.MARKDOWN_PAGE_SEPARATOR_LONG_OPTION)) {
            config.setMarkdownPageSeparator(commandLine.getOptionValue(CLIOptions.MARKDOWN_PAGE_SEPARATOR_LONG_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.TEXT_PAGE_SEPARATOR_LONG_OPTION)) {
            config.setTextPageSeparator(commandLine.getOptionValue(CLIOptions.TEXT_PAGE_SEPARATOR_LONG_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.HTML_PAGE_SEPARATOR_LONG_OPTION)) {
            config.setHtmlPageSeparator(commandLine.getOptionValue(CLIOptions.HTML_PAGE_SEPARATOR_LONG_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.FOLDER_OPTION)) {
            config.setOutputFolder(commandLine.getOptionValue(CLIOptions.FOLDER_OPTION));
        } else {
            String argument = commandLine.getArgs()[0];
            File file = new File(argument);
            file = new File(file.getAbsolutePath());
            config.setOutputFolder(file.isDirectory() ? file.getAbsolutePath() : file.getParent());
        }
        applyContentSafetyOption(config, commandLine);
        applyFormatOption(config, commandLine);
        applyTableMethodOption(config, commandLine);
        applyImageOptions(config, commandLine);
        applyPagesOption(config, commandLine);
        applyHybridOptions(config, commandLine);
        return config;
    }

    private static void applyImageOptions(Config config, CommandLine commandLine) {
        if (commandLine.hasOption(IMAGE_OUTPUT_LONG_OPTION)) {
            String outputValue = commandLine.getOptionValue(IMAGE_OUTPUT_LONG_OPTION);
            if (outputValue == null || outputValue.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Option --image-output requires a value. Supported values: %s",
                                Config.getImageOutputOptions(", ")));
            }
            String output = outputValue.trim().toLowerCase(Locale.ROOT);
            if (!Config.isValidImageOutput(output)) {
                throw new IllegalArgumentException(
                        String.format("Unsupported image output mode '%s'. Supported values: %s",
                                output, Config.getImageOutputOptions(", ")));
            }
            config.setImageOutput(output);
        }
        if (commandLine.hasOption(IMAGE_FORMAT_LONG_OPTION)) {
            String formatValue = commandLine.getOptionValue(IMAGE_FORMAT_LONG_OPTION);
            if (formatValue == null || formatValue.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "Option --image-format requires a value. Supported values: png, jpeg");
            }
            String format = formatValue.trim().toLowerCase(Locale.ROOT);
            if (!Config.isValidImageFormat(format)) {
                throw new IllegalArgumentException(
                        String.format("Unsupported image format '%s'. Supported values: png, jpeg", format));
            }
            config.setImageFormat(format);
        }
    }

    private static void applyPagesOption(Config config, CommandLine commandLine) {
        if (commandLine.hasOption(PAGES_LONG_OPTION)) {
            config.setPages(commandLine.getOptionValue(PAGES_LONG_OPTION));
        }
    }

    private static void applyTableMethodOption(Config config, CommandLine commandLine) {
        if (commandLine.hasOption(TABLE_METHOD_LONG_OPTION)) {
            String methodValue = commandLine.getOptionValue(TABLE_METHOD_LONG_OPTION);
            if (methodValue == null || methodValue.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Option --table-method requires a value. Supported values: %s",
                                Config.getTableMethodOptions(", ")));
            }
            String method = methodValue.trim().toLowerCase(Locale.ROOT);
            if (!Config.isValidTableMethod(method)) {
                throw new IllegalArgumentException(
                        String.format("Unsupported table method '%s'. Supported values: %s",
                                method, Config.getTableMethodOptions(", ")));
            }
            config.setTableMethod(method);
        }
    }

    private static void applyContentSafetyOption(Config config, CommandLine commandLine) {
        if (!commandLine.hasOption(CONTENT_SAFETY_OFF_LONG_OPTION)) {
            return;
        }

        String[] optionValues = commandLine.getOptionValues(CONTENT_SAFETY_OFF_LONG_OPTION);
        if (optionValues == null || optionValues.length == 0) {
            throw new IllegalArgumentException(
                    "Option --content-safety-off requires at least one value. Supported values: all, hidden-text, off-page, tiny, hidden-ocg");
        }

        Set<String> values = parseOptionValues(optionValues);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Option --content-safety-off requires at least one value. Supported values: all, hidden-text, off-page, tiny, hidden-ocg");
        }

        for (String value : values) {
            switch (value) {
                case "hidden-text":
                    config.getFilterConfig().setFilterHiddenText(false);
                    break;
                case "off-page":
                    config.getFilterConfig().setFilterOutOfPage(false);
                    break;
                case "tiny":
                    config.getFilterConfig().setFilterTinyText(false);
                    break;
                case "hidden-ocg":
                    config.getFilterConfig().setFilterHiddenOCG(false);
                    break;
                case "all":
                    config.getFilterConfig().setFilterHiddenText(false);
                    config.getFilterConfig().setFilterOutOfPage(false);
                    config.getFilterConfig().setFilterTinyText(false);
                    config.getFilterConfig().setFilterHiddenOCG(false);
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Unsupported value '%s'. Supported values: all, hidden-text, off-page, tiny, hidden-ocg",
                            value));
            }
        }
    }

    private static void applyFormatOption(Config config, CommandLine commandLine) {
        if (!commandLine.hasOption(FORMAT_OPTION)) {
            return;
        }

        String[] optionValues = commandLine.getOptionValues(FORMAT_OPTION);
        if (optionValues == null || optionValues.length == 0) {
            throw new IllegalArgumentException(
                    "Option --format requires at least one value. Supported values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images");
        }

        Set<String> values = parseOptionValues(optionValues);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Option --format requires at least one value. Supported values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images");
        }

        config.setGenerateJSON(false);

        for (String value : values) {
            switch (value) {
                case "json":
                    config.setGenerateJSON(true);
                    break;
                case "html":
                    config.setGenerateHtml(true);
                    break;
                case "text":
                    config.setGenerateText(true);
                    break;
                case "pdf":
                    config.setGeneratePDF(true);
                    break;
                case "markdown":
                    config.setGenerateMarkdown(true);
                    break;
                case "markdown-with-html":
                    config.setUseHTMLInMarkdown(true);
                    break;
                case "markdown-with-images":
                    config.setAddImageToMarkdown(true);
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Unsupported format '%s'. Supported values: json, text, html, pdf, markdown, markdown-with-html, markdown-with-images",
                            value));
            }
        }
    }

    private static Set<String> parseOptionValues(String[] optionValues) {
        Set<String> values = new LinkedHashSet<>();
        for (String rawValue : optionValues) {
            if (rawValue == null) {
                continue;
            }
            String[] splitValues = rawValue.split(",");
            for (String candidate : splitValues) {
                String format = candidate.trim().toLowerCase(Locale.ROOT);
                if (!format.isEmpty()) {
                    values.add(format);
                }
            }
        }
        return values;
    }

    private static void applyHybridOptions(Config config, CommandLine commandLine) {
        if (commandLine.hasOption(HYBRID_LONG_OPTION)) {
            String hybridValue = commandLine.getOptionValue(HYBRID_LONG_OPTION);
            if (hybridValue == null || hybridValue.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Option --hybrid requires a value. Supported values: %s",
                                Config.getHybridOptions(", ")));
            }
            String hybrid = hybridValue.trim().toLowerCase(Locale.ROOT);
            if (!Config.isValidHybrid(hybrid)) {
                throw new IllegalArgumentException(
                        String.format("Unsupported hybrid backend '%s'. Supported values: %s",
                                hybrid, Config.getHybridOptions(", ")));
            }
            config.setHybrid(hybrid);
        }
        if (commandLine.hasOption(HYBRID_URL_LONG_OPTION)) {
            String url = commandLine.getOptionValue(HYBRID_URL_LONG_OPTION);
            if (url != null && !url.trim().isEmpty()) {
                config.getHybridConfig().setUrl(url.trim());
            }
        }
        if (commandLine.hasOption(HYBRID_TIMEOUT_LONG_OPTION)) {
            String timeoutValue = commandLine.getOptionValue(HYBRID_TIMEOUT_LONG_OPTION);
            if (timeoutValue != null && !timeoutValue.trim().isEmpty()) {
                try {
                    int timeout = Integer.parseInt(timeoutValue.trim());
                    config.getHybridConfig().setTimeoutMs(timeout);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException(
                            String.format("Invalid timeout value '%s'. Must be a positive integer.", timeoutValue));
                }
            }
        }
        if (commandLine.hasOption(HYBRID_FALLBACK_LONG_OPTION)) {
            config.getHybridConfig().setFallbackToJava(true);
        }
    }

    /**
     * Exports CLI option definitions as JSON for code generation.
     * This is used to generate Node.js, Python, and documentation from a single
     * source of truth.
     *
     * @param out The output stream to write JSON to
     */
    public static void exportOptionsAsJson(PrintStream out) {
        List<OptionDefinition> exportable = OPTION_DEFINITIONS.stream()
                .filter(d -> d.exported)
                .collect(Collectors.toList());

        // Build JSON manually to avoid external dependencies
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"options\": [\n");

        for (int i = 0; i < exportable.size(); i++) {
            OptionDefinition opt = exportable.get(i);
            json.append("    {\n");
            json.append("      \"name\": \"").append(opt.longName).append("\",\n");
            json.append("      \"shortName\": ").append(opt.shortName == null ? "null" : "\"" + opt.shortName + "\"")
                    .append(",\n");
            json.append("      \"type\": \"").append(opt.type).append("\",\n");
            json.append("      \"required\": false,\n");
            if (opt.defaultValue == null) {
                json.append("      \"default\": null,\n");
            } else if (opt.defaultValue instanceof Boolean) {
                json.append("      \"default\": ").append(opt.defaultValue).append(",\n");
            } else {
                json.append("      \"default\": \"").append(escapeJson(opt.defaultValue.toString())).append("\",\n");
            }
            json.append("      \"description\": \"").append(escapeJson(opt.description)).append("\"\n");
            json.append("    }");
            if (i < exportable.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }

        json.append("  ]\n");
        json.append("}\n");

        out.print(json.toString());
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * Internal class to hold option definition for both CLI and JSON export.
     * Single source of truth for all option metadata.
     */
    private static class OptionDefinition {
        final String longName;
        final String shortName;
        final String type; // "string" | "boolean"
        final Object defaultValue;
        final String description;
        final boolean exported; // Whether to include in JSON export

        OptionDefinition(String longName, String shortName, String type, Object defaultValue, String description,
                boolean exported) {
            this.longName = longName;
            this.shortName = shortName;
            this.type = type;
            this.defaultValue = defaultValue;
            this.description = description;
            this.exported = exported;
        }

        /** Creates an Apache Commons CLI Option from this definition. */
        Option toOption() {
            boolean hasArg = "string".equals(type);
            return new Option(shortName, longName, hasArg, description);
        }
    }

}

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
package org.opendataloader.pdf.api.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.opendataloader.pdf.api.Config;
import org.opendataloader.pdf.hybrid.HybridConfig;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter that maps Apache Commons CLI options to {@link Config} / {@link HybridConfig}.
 *
 * <p><b>Stable API for downstream tools</b> (e.g. opendataloader-pdfua) — these four
 * members are the supported integration surface and will not break compatibly:
 * <ul>
 *   <li>{@link #defineOptions()} — get a fully populated {@code Options} instance</li>
 *   <li>{@link #addAllTo(Options)} — add core options into an externally-built {@code Options}</li>
 *   <li>{@link #applyAllTo(Config, CommandLine)} — populate a {@code Config} from a parsed line</li>
 *   <li>{@link #FOLDER_OPTION} — short option name for {@code --output-dir}</li>
 * </ul>
 *
 * <p><b>Everything else is internal.</b> The numerous other public {@code static} members
 * (option-name constants, helpers like {@code createConfigFromCommandLine},
 * {@code exportOptionsAsJson}) exist for the CLI module ({@code CLIMain}) and the
 * options-export tooling that drives Node/Python binding generation. Their visibility
 * is {@code public} only for cross-package access within this codebase; they are
 * <i>not</i> part of the supported API and may be renamed, moved, or removed in any
 * release. Downstream consumers depending on them do so at their own risk.
 *
 * <p>Pdfua's usage pattern (build your own {@code Options}, add core's, parse, then
 * populate {@code Config}):
 * <pre>{@code
 *   Options options = new Options();
 *   options.addOption(...);              // your tool-specific options
 *   CLIOptions.addAllTo(options);        // add core's options
 *   CommandLine cmd = parser.parse(options, args);
 *   Config core = new Config();
 *   CLIOptions.applyAllTo(core, cmd);
 * }</pre>
 */
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
    public static final String FORMAT_LONG_OPTION = "format";
    private static final String FORMAT_DESC = "Output formats (comma-separated). "
            + "Values: json, text, html, pdf, markdown, tagged-pdf. Default: json. "
            + "For HTML inside Markdown use --markdown-with-html. "
            + "For image extraction control use --image-output.";

    // ===== Quiet =====
    public static final String QUIET_OPTION = "q";
    private static final String QUIET_LONG_OPTION = "quiet";
    private static final String QUIET_DESC = "Suppress console logging output";

    // ===== Content Safety =====
    private static final String CONTENT_SAFETY_OFF_LONG_OPTION = "content-safety-off";
    private static final String CONTENT_SAFETY_OFF_DESC = "Disable content safety filters. "
            + "Values: all, hidden-text, off-page, tiny, hidden-ocg";

    // ===== Sanitize =====
    private static final String SANITIZE_LONG_OPTION = "sanitize";
    private static final String SANITIZE_DESC = "Enable sensitive data sanitization. "
            + "Replaces emails, phone numbers, IPs, credit cards, and URLs with placeholders";

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

    private static final String IMAGE_DIR_LONG_OPTION = "image-dir";
    private static final String IMAGE_DIR_DESC = "Directory for extracted images";

    // ===== Pages =====
    private static final String PAGES_LONG_OPTION = "pages";
    private static final String PAGES_DESC = "Pages to extract (e.g., \"1,3,5-7\"). Default: all pages";

    // ===== Include Header Footer =====
    private static final String INCLUDE_HEADER_FOOTER_LONG_OPTION = "include-header-footer";
    private static final String INCLUDE_HEADER_FOOTER_DESC = "Include page headers and footers in output";

    // ===== Detect Strikethrough =====
    private static final String DETECT_STRIKETHROUGH_LONG_OPTION = "detect-strikethrough";
    private static final String DETECT_STRIKETHROUGH_DESC = "Detect strikethrough text and wrap with ~~ in Markdown output or <del></del> tag in HTML output (experimental)";

    // ===== Hybrid Mode =====
    private static final String HYBRID_LONG_OPTION = "hybrid";
    private static final String HYBRID_DESC = "Hybrid backend (requires a running server). "
            + "Quick start: pip install \"opendataloader-pdf[hybrid]\" && opendataloader-pdf-hybrid --port 5002. "
            + "For remote servers use --hybrid-url. Values: off (default), docling-fast, hancom-ai";

    private static final String HYBRID_MODE_LONG_OPTION = "hybrid-mode";
    private static final String HYBRID_MODE_DESC = "Hybrid triage mode. Values: auto (default, dynamic triage), full (skip triage, all pages to backend)";

    // Deprecated: OCR settings are now configured on the hybrid server
    private static final String HYBRID_OCR_LONG_OPTION = "hybrid-ocr";
    private static final String HYBRID_OCR_DESC = "[Deprecated] OCR settings are now configured on the hybrid server (--ocr-lang, --force-ocr)";

    private static final String HYBRID_URL_LONG_OPTION = "hybrid-url";
    private static final String HYBRID_URL_DESC = "Hybrid backend server URL (overrides default)";

    private static final String HYBRID_TIMEOUT_LONG_OPTION = "hybrid-timeout";
    private static final String HYBRID_TIMEOUT_DESC = "Hybrid backend request timeout in milliseconds (0 = no timeout). Default: 0";

    private static final String HYBRID_FALLBACK_LONG_OPTION = "hybrid-fallback";
    private static final String HYBRID_FALLBACK_DESC = "Opt in to Java fallback on hybrid backend error (default: disabled)";

    // ===== Hybrid hancom-ai backend-specific =====
    private static final String HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_LONG_OPTION =
            "hybrid-hancom-ai-regionlist-strategy";
    private static final String HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_DESC =
            "DLA label 7 (regionlist) handling. Requires --hybrid=hancom-ai. "
            + "Values: table-first (default; check TSR overlap), list-only (skip TSR, always treat as list)";

    private static final String HYBRID_HANCOM_AI_OCR_STRATEGY_LONG_OPTION =
            "hybrid-hancom-ai-ocr-strategy";
    private static final String HYBRID_HANCOM_AI_OCR_STRATEGY_DESC =
            "OCR strategy. Requires --hybrid=hancom-ai. "
            + "Values: off (stream-only), auto (default; stream first, OCR fallback), force (OCR-only)";

    private static final String HYBRID_HANCOM_AI_IMAGE_CACHE_LONG_OPTION =
            "hybrid-hancom-ai-image-cache";
    private static final String HYBRID_HANCOM_AI_IMAGE_CACHE_DESC =
            "Page image cache backing. Requires --hybrid=hancom-ai. "
            + "Values: memory (default), disk";

    private static final String HYBRID_HANCOM_AI_SAVE_CROPS_LONG_OPTION =
            "hybrid-hancom-ai-save-crops";
    private static final String HYBRID_HANCOM_AI_SAVE_CROPS_DESC =
            "Persist cropped figure images to disk for debugging. Requires --hybrid=hancom-ai";

    private static final String HYBRID_HANCOM_AI_CROP_OUTPUT_DIR_LONG_OPTION =
            "hybrid-hancom-ai-crop-output-dir";
    private static final String HYBRID_HANCOM_AI_CROP_OUTPUT_DIR_DESC =
            "Output directory for --hybrid-hancom-ai-save-crops. Requires --hybrid=hancom-ai";

    // ===== Stdout Output =====
    private static final String TO_STDOUT_LONG_OPTION = "to-stdout";
    private static final String TO_STDOUT_DESC = "Write output to stdout instead of file (single format only)";

    // ===== Threads =====
    private static final String THREADS_LONG_OPTION = "threads";
    private static final String THREADS_DESC = "Number of worker threads for per-page processing. "
            + "Default: 1 (sequential, stable). Values >1 (experimental) run pages in parallel for faster throughput; "
            + "output may vary slightly on some PDFs. Capped at the number of available CPU cores. "
            + "Applies to the native Java pipeline only; ignored in --hybrid mode";

    // ===== Markdown modifiers =====
    public static final String HTML_IN_MARKDOWN_LONG_OPTION = "markdown-with-html";
    private static final String HTML_IN_MARKDOWN_DESC =
            "Allow HTML tags inside Markdown output for complex structures such as multi-row-span tables. "
                    + "Implies --format markdown.";

    // ===== Export Options (internal) =====
    public static final String EXPORT_OPTIONS_LONG_OPTION = "export-options";

    // ===== Legacy Options (hidden, backward compatibility) =====
    public static final String PDF_REPORT_LONG_OPTION = "pdf";
    public static final String MARKDOWN_REPORT_LONG_OPTION = "markdown";
    public static final String HTML_REPORT_LONG_OPTION = "html";
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
            new OptionDefinition(SANITIZE_LONG_OPTION, null, "boolean", false, SANITIZE_DESC, true),
            new OptionDefinition(KEEP_LINE_BREAKS_LONG_OPTION, null, "boolean", false, KEEP_LINE_BREAKS_DESC, true),
            new OptionDefinition(REPLACE_INVALID_CHARS_LONG_OPTION, null, "string", " ", REPLACE_INVALID_CHARS_DESC,
                    true),
            new OptionDefinition(USE_STRUCT_TREE_LONG_OPTION, null, "boolean", false, USE_STRUCT_TREE_DESC, true),
            new OptionDefinition(TABLE_METHOD_LONG_OPTION, null, "string", "default", TABLE_METHOD_DESC, true),
            new OptionDefinition(READING_ORDER_LONG_OPTION, null, "string", "xycut", READING_ORDER_DESC, true),
            new OptionDefinition(MARKDOWN_PAGE_SEPARATOR_LONG_OPTION, null, "string", null,
                    MARKDOWN_PAGE_SEPARATOR_DESC, true),
            new OptionDefinition(HTML_IN_MARKDOWN_LONG_OPTION, null, "boolean", false,
                    HTML_IN_MARKDOWN_DESC, true),
            new OptionDefinition(TEXT_PAGE_SEPARATOR_LONG_OPTION, null, "string", null, TEXT_PAGE_SEPARATOR_DESC, true),
            new OptionDefinition(HTML_PAGE_SEPARATOR_LONG_OPTION, null, "string", null, HTML_PAGE_SEPARATOR_DESC, true),
            new OptionDefinition(IMAGE_OUTPUT_LONG_OPTION, null, "string", "external", IMAGE_OUTPUT_DESC, true),
            new OptionDefinition(IMAGE_FORMAT_LONG_OPTION, null, "string", "png", IMAGE_FORMAT_DESC, true),
            new OptionDefinition(IMAGE_DIR_LONG_OPTION, null, "string", null, IMAGE_DIR_DESC, true),
            new OptionDefinition(PAGES_LONG_OPTION, null, "string", null, PAGES_DESC, true),
            new OptionDefinition(INCLUDE_HEADER_FOOTER_LONG_OPTION, null, "boolean", false,
                    INCLUDE_HEADER_FOOTER_DESC, true),
            new OptionDefinition(DETECT_STRIKETHROUGH_LONG_OPTION, null, "boolean", false,
                    DETECT_STRIKETHROUGH_DESC, true),
            new OptionDefinition(HYBRID_LONG_OPTION, null, "string", "off", HYBRID_DESC, true),
            new OptionDefinition(HYBRID_MODE_LONG_OPTION, null, "string", "auto", HYBRID_MODE_DESC, true),
            new OptionDefinition(HYBRID_URL_LONG_OPTION, null, "string", null, HYBRID_URL_DESC, true),
            new OptionDefinition(HYBRID_TIMEOUT_LONG_OPTION, null, "string", "0", HYBRID_TIMEOUT_DESC, true),
            new OptionDefinition(HYBRID_FALLBACK_LONG_OPTION, null, "boolean", false, HYBRID_FALLBACK_DESC, true),
            new OptionDefinition(HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_LONG_OPTION, null, "string",
                    "table-first", HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_DESC, true),
            new OptionDefinition(HYBRID_HANCOM_AI_OCR_STRATEGY_LONG_OPTION, null, "string",
                    "auto", HYBRID_HANCOM_AI_OCR_STRATEGY_DESC, true),
            new OptionDefinition(HYBRID_HANCOM_AI_IMAGE_CACHE_LONG_OPTION, null, "string",
                    "memory", HYBRID_HANCOM_AI_IMAGE_CACHE_DESC, true),
            new OptionDefinition(TO_STDOUT_LONG_OPTION, null, "boolean", false, TO_STDOUT_DESC, true),
            new OptionDefinition(THREADS_LONG_OPTION, null, "string", "1", THREADS_DESC, true),
            new OptionDefinition(EXPORT_OPTIONS_LONG_OPTION, null, "boolean", null, null, false),

            // Legacy options (not exported, for backward compatibility)
            new OptionDefinition(HYBRID_OCR_LONG_OPTION, null, "string", null, HYBRID_OCR_DESC, false),
            new OptionDefinition(PDF_REPORT_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(MARKDOWN_REPORT_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(HTML_REPORT_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(MARKDOWN_IMAGE_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(NO_JSON_REPORT_LONG_OPTION, null, "boolean", null, null, false),
            new OptionDefinition(HYBRID_HANCOM_AI_SAVE_CROPS_LONG_OPTION, null, "boolean",
                    false, HYBRID_HANCOM_AI_SAVE_CROPS_DESC, false),
            new OptionDefinition(HYBRID_HANCOM_AI_CROP_OUTPUT_DIR_LONG_OPTION, null, "string",
                    null, HYBRID_HANCOM_AI_CROP_OUTPUT_DIR_DESC, false));

    public static Options defineOptions() {
        Options options = new Options();
        addAllTo(options);
        return options;
    }

    /**
     * Registers every core CLI option onto an external {@link Options} instance.
     * Used by downstream CLIs (e.g. opendataloader-pdfua) that want to inherit
     * the entire core option set and add their own options on top.
     *
     * @param options the Options instance to populate
     */
    public static void addAllTo(Options options) {
        for (OptionDefinition def : OPTION_DEFINITIONS) {
            options.addOption(def.toOption());
        }
    }

    public static Config createConfigFromCommandLine(CommandLine commandLine) {
        Config config = new Config();
        if (commandLine.hasOption(CLIOptions.FOLDER_OPTION)) {
            config.setOutputFolder(commandLine.getOptionValue(CLIOptions.FOLDER_OPTION));
        } else {
            String argument = commandLine.getArgs()[0];
            File file = new File(argument);
            file = new File(file.getAbsolutePath());
            config.setOutputFolder(file.isDirectory() ? file.getAbsolutePath() : file.getParent());
        }
        applyAllTo(config, commandLine);
        return config;
    }

    /**
     * Applies every core CLI option from the parsed command line onto the given Config.
     * Caller is responsible for setting required Config state that is not represented
     * by a CLI option (e.g. output folder when no positional input file is provided).
     *
     * Used by downstream CLIs that build their own Options + Config and want core
     * options applied without paying for the positional-arg-based output-folder
     * fallback that {@link #createConfigFromCommandLine} performs.
     *
     * @param config       Config to populate
     * @param commandLine  parsed CommandLine
     */
    public static void applyAllTo(Config config, CommandLine commandLine) {
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
            config.setGenerateMarkdown(true);
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
        if (commandLine.hasOption(INCLUDE_HEADER_FOOTER_LONG_OPTION)) {
            config.setIncludeHeaderFooter(true);
        }
        if (commandLine.hasOption(DETECT_STRIKETHROUGH_LONG_OPTION)) {
            config.setDetectStrikethrough(true);
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
        applyContentSafetyOption(config, commandLine);
        applySanitizeOption(config, commandLine);
        applyFormatOption(config, commandLine);
        applyTableMethodOption(config, commandLine);
        applyImageOptions(config, commandLine);
        applyPagesOption(config, commandLine);
        applyHybridOptions(config, commandLine);
        applyThreadsOption(config, commandLine);
        config.normalize();
    }

    private static void applyThreadsOption(Config config, CommandLine commandLine) {
        if (!commandLine.hasOption(THREADS_LONG_OPTION)) {
            return;
        }
        String value = commandLine.getOptionValue(THREADS_LONG_OPTION);
        int requested;
        try {
            requested = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("Option --threads requires an integer >= 1, got '%s'", value));
        }
        if (requested < 1) {
            throw new IllegalArgumentException(
                    String.format("Option --threads requires an integer >= 1, got %d", requested));
        }
        config.setThreads(requested);
        int applied = config.getThreads();
        if (applied < requested) {
            System.err.println(String.format(
                    "Warning: --threads=%d exceeds available CPU cores; capped to %d.",
                    requested, applied));
        }
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
        if (commandLine.hasOption(IMAGE_DIR_LONG_OPTION)) {
            config.setImageDir(commandLine.getOptionValue(IMAGE_DIR_LONG_OPTION));
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
                case "sensitive-data":
                    System.err.println("Warning: '--content-safety-off sensitive-data' is deprecated and has no effect. "
                            + "Sensitive data sanitization is now opt-in. "
                            + "Use '--sanitize' to enable masking.");
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

    private static void applySanitizeOption(Config config, CommandLine commandLine) {
        if (commandLine.hasOption(SANITIZE_LONG_OPTION)) {
            config.getFilterConfig().setFilterSensitiveData(true);
        }
    }

    private static void applyFormatOption(Config config, CommandLine commandLine) {
        if (!commandLine.hasOption(FORMAT_OPTION)) {
            return;
        }

        String[] optionValues = commandLine.getOptionValues(FORMAT_OPTION);
        if (optionValues == null || optionValues.length == 0) {
            throw new IllegalArgumentException(
                    "Option --format requires at least one value. Supported values: json, text, html, pdf, markdown, tagged-pdf");
        }

        Set<String> values = parseOptionValues(optionValues);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(
                    "Option --format requires at least one value. Supported values: json, text, html, pdf, markdown, tagged-pdf");
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
                    System.err.println("[WARN] --format markdown-with-html is deprecated and will be removed "
                            + "in the next major release. Use --format markdown --markdown-with-html instead.");
                    config.setUseHTMLInMarkdown(true);
                    break;
                case "markdown-with-images":
                    System.err.println("[WARN] --format markdown-with-images is deprecated and will be removed "
                            + "in the next major release. Use --format markdown with --image-output "
                            + "(off|embedded|external) instead.");
                    config.setGenerateMarkdown(true);
                    break;
                case "tagged-pdf":
                    config.setGenerateTaggedPDF(true);
                    break;
                default:
                    throw new IllegalArgumentException(String.format(
                            "Unsupported format '%s'. Supported values: json, text, html, pdf, markdown, tagged-pdf",
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
        if (commandLine.hasOption(HYBRID_MODE_LONG_OPTION)) {
            String modeValue = commandLine.getOptionValue(HYBRID_MODE_LONG_OPTION);
            if (modeValue == null || modeValue.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        String.format("Option --hybrid-mode requires a value. Supported values: %s",
                                Config.getHybridModeOptions(", ")));
            }
            String mode = modeValue.trim().toLowerCase(Locale.ROOT);
            if (!Config.isValidHybridMode(mode)) {
                throw new IllegalArgumentException(
                        String.format("Unsupported hybrid mode '%s'. Supported values: %s",
                                mode, Config.getHybridModeOptions(", ")));
            }
            config.getHybridConfig().setMode(mode);
        }
        if (commandLine.hasOption(HYBRID_OCR_LONG_OPTION)) {
            // Deprecated: OCR settings are now configured on the hybrid server
            System.err.println("Warning: --hybrid-ocr is deprecated. "
                    + "Configure OCR settings on the hybrid server instead (--ocr-lang, --force-ocr).");
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
                            String.format("Invalid timeout value '%s'. Must be a non-negative integer.", timeoutValue));
                }
            }
        }
        if (commandLine.hasOption(HYBRID_FALLBACK_LONG_OPTION)) {
            config.getHybridConfig().setFallbackToJava(true);
        }
        if (commandLine.hasOption(HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_LONG_OPTION)) {
            String value = commandLine.getOptionValue(HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_LONG_OPTION);
            if (value != null && !value.trim().isEmpty()) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!HybridConfig.REGIONLIST_TABLE_FIRST.equals(normalized)
                        && !HybridConfig.REGIONLIST_LIST_ONLY.equals(normalized)) {
                    throw new IllegalArgumentException(String.format(
                            "Option --%s: unsupported value '%s'. Supported values: %s, %s",
                            HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_LONG_OPTION, normalized,
                            HybridConfig.REGIONLIST_TABLE_FIRST, HybridConfig.REGIONLIST_LIST_ONLY));
                }
                config.getHybridConfig().setRegionlistStrategy(normalized);
            }
        }
        if (commandLine.hasOption(HYBRID_HANCOM_AI_OCR_STRATEGY_LONG_OPTION)) {
            String value = commandLine.getOptionValue(HYBRID_HANCOM_AI_OCR_STRATEGY_LONG_OPTION);
            if (value != null && !value.trim().isEmpty()) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!HybridConfig.OCR_OFF.equals(normalized)
                        && !HybridConfig.OCR_AUTO.equals(normalized)
                        && !HybridConfig.OCR_FORCE.equals(normalized)) {
                    throw new IllegalArgumentException(String.format(
                            "Option --%s: unsupported value '%s'. Supported values: %s, %s, %s",
                            HYBRID_HANCOM_AI_OCR_STRATEGY_LONG_OPTION, normalized,
                            HybridConfig.OCR_OFF, HybridConfig.OCR_AUTO, HybridConfig.OCR_FORCE));
                }
                config.getHybridConfig().setOcrStrategy(normalized);
            }
        }
        if (commandLine.hasOption(HYBRID_HANCOM_AI_IMAGE_CACHE_LONG_OPTION)) {
            String value = commandLine.getOptionValue(HYBRID_HANCOM_AI_IMAGE_CACHE_LONG_OPTION);
            if (value != null && !value.trim().isEmpty()) {
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (!"memory".equals(normalized) && !"disk".equals(normalized)) {
                    throw new IllegalArgumentException(String.format(
                            "Option --%s: unsupported value '%s'. Supported values: memory, disk",
                            HYBRID_HANCOM_AI_IMAGE_CACHE_LONG_OPTION, normalized));
                }
                config.getHybridConfig().setImageCache(normalized);
            }
        }
        if (commandLine.hasOption(HYBRID_HANCOM_AI_SAVE_CROPS_LONG_OPTION)) {
            config.getHybridConfig().setSaveCrops(true);
        }
        if (commandLine.hasOption(HYBRID_HANCOM_AI_CROP_OUTPUT_DIR_LONG_OPTION)) {
            String value = commandLine.getOptionValue(HYBRID_HANCOM_AI_CROP_OUTPUT_DIR_LONG_OPTION);
            if (value != null && !value.trim().isEmpty()) {
                config.getHybridConfig().setCropOutputDir(value.trim());
            }
        }
        if (commandLine.hasOption(TO_STDOUT_LONG_OPTION)) {
            config.setOutputStdout(true);
        }
        // Keep in sync with all HYBRID_HANCOM_AI_*_LONG_OPTION constants above.
        boolean usesHancomAiOnly =
                commandLine.hasOption(HYBRID_HANCOM_AI_REGIONLIST_STRATEGY_LONG_OPTION) ||
                commandLine.hasOption(HYBRID_HANCOM_AI_OCR_STRATEGY_LONG_OPTION) ||
                commandLine.hasOption(HYBRID_HANCOM_AI_IMAGE_CACHE_LONG_OPTION) ||
                commandLine.hasOption(HYBRID_HANCOM_AI_SAVE_CROPS_LONG_OPTION) ||
                commandLine.hasOption(HYBRID_HANCOM_AI_CROP_OUTPUT_DIR_LONG_OPTION);
        if (usesHancomAiOnly && !Config.HYBRID_HANCOM_AI.equals(config.getHybrid())) {
            throw new IllegalArgumentException(
                    "Options --hybrid-hancom-ai-* require --hybrid=hancom-ai (got --hybrid="
                    + config.getHybrid() + ")");
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

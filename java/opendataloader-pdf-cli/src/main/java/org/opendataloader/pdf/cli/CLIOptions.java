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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public class CLIOptions {

    private static final String CONTENT_SAFETY_OFF_LONG_OPTION = "content-safety-off";
    private static final String CONTENT_SAFETY_OFF_ALL_ARGUMENT = "all";
    private static final String CONTENT_SAFETY_OFF_HIDDEN_TEXT_ARGUMENT = "hidden-text";
    private static final String CONTENT_SAFETY_OFF_OFF_PAGE_ARGUMENT = "off-page";
    private static final String CONTENT_SAFETY_OFF_TINY_TEXT_ARGUMENT = "tiny";
    private static final String CONTENT_SAFETY_OFF_HIDDEN_OCG_ARGUMENT = "hidden-ocg";
    private static final String CONTENT_SAFETY_OFF_SUPPORTED_LIST = "all, hidden-text, off-page, tiny, hidden-ocg";

    public static final String PASSWORD_OPTION = "p";
    private static final String PASSWORD_LONG_OPTION = "password";

    public static final String FOLDER_OPTION = "o";
    private static final String FOLDER_LONG_OPTION = "output-dir";

    public static final String FORMAT_OPTION = "f";
    private static final String FORMAT_LONG_OPTION = "format";
    private static final String FORMAT_SUPPORTED_LIST = "json, text, html, pdf, markdown, markdown-with-html, markdown-with-images";

    public static final String QUIET_OPTION = "q";
    private static final String QUIET_LONG_OPTION = "quiet";

    private static final String HTML_IN_MARKDOWN_LONG_OPTION = "markdown-with-html";

    private static final String KEEP_LINE_BREAKS_LONG_OPTION = "keep-line-breaks";

    public static final String PDF_REPORT_LONG_OPTION = "pdf";

    public static final String MARKDOWN_REPORT_LONG_OPTION = "markdown";

    public static final String HTML_REPORT_LONG_OPTION = "html";

    private static final String MARKDOWN_IMAGE_LONG_OPTION = "markdown-with-images";

    public static final String NO_JSON_REPORT_LONG_OPTION = "no-json";

    private static final String REPLACE_INVALID_CHARS_LONG_OPTION = "replace-invalid-chars";

    private static final String USE_STRUCT_TREE_LONG_OPTION = "use-struct-tree";

    public static final String PYTHON_OPTION = "py";
    private static final String PYTHON_LONG_OPTION = "python";

//    public static final String TATR_OPTION = "tatr";
//    private static final String TATR_LONG_OPTION = "tatrpath";

    public static Options defineOptions() {
        Options options = new Options();
        Option contentSafetyOff = new Option(null, CONTENT_SAFETY_OFF_LONG_OPTION, true, "Disables one or more content safety filters. Accepts a comma-separated list of filter names. Arguments: all, hidden-text, off-page, tiny, hidden-ocg");
        contentSafetyOff.setRequired(false);
        contentSafetyOff.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(contentSafetyOff);
        Option password = new Option(PASSWORD_OPTION, PASSWORD_LONG_OPTION, true, "Specifies the password for an encrypted PDF");
        password.setRequired(false);
        options.addOption(password);
        Option htmlInMarkdown = new Option(null, HTML_IN_MARKDOWN_LONG_OPTION, false, "Sets the data extraction output format to Markdown with rendering complex elements like tables as HTML for better structure");
        htmlInMarkdown.setRequired(false);
        options.addOption(htmlInMarkdown);
        Option format = new Option(FORMAT_OPTION, FORMAT_LONG_OPTION, true, String.format("Comma-separated list of output formats to generate. Supported values: %s", FORMAT_SUPPORTED_LIST));
        format.setRequired(false);
        format.setArgs(Option.UNLIMITED_VALUES);
        options.addOption(format);
        Option quiet = new Option(QUIET_OPTION, QUIET_LONG_OPTION, false, "Suppresses console logging output");
        quiet.setRequired(false);
        options.addOption(quiet);
        Option keepLineBreaks = new Option(null, KEEP_LINE_BREAKS_LONG_OPTION, false, "Preserves original line breaks in the extracted text");
        keepLineBreaks.setRequired(false);
        options.addOption(keepLineBreaks);
        Option pdfOutput = new Option(null, PDF_REPORT_LONG_OPTION, false, "Generates a new PDF file where the extracted layout data is visualized as annotations");
        pdfOutput.setRequired(false);
        options.addOption(pdfOutput);
        Option markdownOutput = new Option(null, MARKDOWN_REPORT_LONG_OPTION, false, "Sets the data extraction output format to Markdown");
        markdownOutput.setRequired(false);
        options.addOption(markdownOutput);
        Option generateHtml = new Option(null, HTML_REPORT_LONG_OPTION, false, "Sets the data extraction output format to HTML");
        generateHtml.setRequired(false);
        options.addOption(generateHtml);
        Option imageSupport = new Option(null, MARKDOWN_IMAGE_LONG_OPTION, false, "Sets the data extraction output format to Markdown with extracting images from the PDF and includes them as links");
        imageSupport.setRequired(false);
        options.addOption(imageSupport);
        Option noJson = new Option(null, NO_JSON_REPORT_LONG_OPTION, false, "Disables the JSON output format.");
        noJson.setRequired(false);
        options.addOption(noJson);
        Option folder = new Option(FOLDER_OPTION, FOLDER_LONG_OPTION, true, "Specifies the output directory for generated files (default the folder of the input PDF)");
        folder.setRequired(false);
        options.addOption(folder);
        Option replaceInvalidChars = new Option(null, REPLACE_INVALID_CHARS_LONG_OPTION, true, "Replaces invalid or unrecognized characters (e.g., �, \\u0000) with the specified character (whitespace is used, if this parameter not specified)");
        replaceInvalidChars.setRequired(false);
        options.addOption(replaceInvalidChars);
        Option useStructTree = new Option(null, USE_STRUCT_TREE_LONG_OPTION, false, "Enable processing structure tree (disabled by default)");
        useStructTree.setRequired(false);
        options.addOption(useStructTree);
        Option python = new Option(PYTHON_OPTION, PYTHON_LONG_OPTION, true, "Python executable to use");
        python.setRequired(false);
        options.addOption(python);
//        Option tatr = new Option(TATR_OPTION, TATR_LONG_OPTION, true, "Path to TATR repository");
//        tatr.setRequired(true);
//        options.addOption(tatr);
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
        return config;
    }

    private static void applyContentSafetyOption(Config config, CommandLine commandLine) {
        if (!commandLine.hasOption(CONTENT_SAFETY_OFF_LONG_OPTION)) {
            return;
        }


        String[] optionValues = commandLine.getOptionValues(CONTENT_SAFETY_OFF_LONG_OPTION);
        if (optionValues == null || optionValues.length == 0) {
            throw new IllegalArgumentException(String.format("Option --content-safety-off requires at least one value. Supported values: %s", CONTENT_SAFETY_OFF_SUPPORTED_LIST));
        }

        Set<String> values = parseOptionValues(optionValues);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(String.format("Option --content-safety-off requires at least one value. Supported values: %s", CONTENT_SAFETY_OFF_SUPPORTED_LIST));
        }

        for (String value : values) {
            switch (value) {
                case CONTENT_SAFETY_OFF_HIDDEN_TEXT_ARGUMENT:
                    config.getFilterConfig().setFilterHiddenText(false);
                    break;
                case CONTENT_SAFETY_OFF_OFF_PAGE_ARGUMENT:
                    config.getFilterConfig().setFilterOutOfPage(false);
                    break;
                case CONTENT_SAFETY_OFF_TINY_TEXT_ARGUMENT:
                    config.getFilterConfig().setFilterTinyText(false);
                    break;
                case CONTENT_SAFETY_OFF_HIDDEN_OCG_ARGUMENT:
                    config.getFilterConfig().setFilterHiddenOCG(false);
                    break;
                case CONTENT_SAFETY_OFF_ALL_ARGUMENT:
                    config.getFilterConfig().setFilterHiddenText(false);
                    config.getFilterConfig().setFilterOutOfPage(false);
                    config.getFilterConfig().setFilterTinyText(false);
                    config.getFilterConfig().setFilterHiddenOCG(false);
                    break;
                default:
                    throw new IllegalArgumentException(String.format("Unsupported value '%s'. Supported values: %s", value, CONTENT_SAFETY_OFF_SUPPORTED_LIST));
            }
        }
    }

    private static void applyFormatOption(Config config, CommandLine commandLine) {
        if (!commandLine.hasOption(FORMAT_OPTION)) {
            return;
        }

        String[] optionValues = commandLine.getOptionValues(FORMAT_OPTION);
        if (optionValues == null || optionValues.length == 0) {
            throw new IllegalArgumentException(String.format("Option --format requires at least one value. Supported values: %s", FORMAT_SUPPORTED_LIST));
        }

        Set<String> values = parseOptionValues(optionValues);
        if (values.isEmpty()) {
            throw new IllegalArgumentException(String.format("Option --format requires at least one value. Supported values: %s", FORMAT_SUPPORTED_LIST));
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
                    throw new IllegalArgumentException(String.format("Unsupported format '%s'. Supported values: %s", value, FORMAT_SUPPORTED_LIST));
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

}

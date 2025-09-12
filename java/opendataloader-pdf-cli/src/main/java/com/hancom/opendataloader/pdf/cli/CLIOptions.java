/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.cli;

import com.hancom.opendataloader.pdf.api.Config;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public class CLIOptions {

    private static final String CONTENT_SAFETY_OFF_LONG_OPTION = "content-safety-off";
    private static final String CONTENT_SAFETY_OFF_ALL_ARGUMENT = "all";
    private static final String CONTENT_SAFETY_OFF_HIDDEN_TEXT_ARGUMENT = "hidden-text";
    private static final String CONTENT_SAFETY_OFF_OFF_PAGE_ARGUMENT = "off-page";

    public static final String PASSWORD_OPTION = "p";
    private static final String PASSWORD_LONG_OPTION = "password";

    public static final String FOLDER_OPTION = "o";
    private static final String FOLDER_LONG_OPTION = "output-dir";

    private static final String HTML_IN_MARKDOWN_LONG_OPTION = "markdown-with-html";

    private static final String KEEP_LINE_BREAKS_LONG_OPTION = "keep-line-breaks";

    public static final String PDF_REPORT_LONG_OPTION = "pdf";

    public static final String MARKDOWN_REPORT_LONG_OPTION = "markdown";

    public static final String HTML_REPORT_LONG_OPTION = "html";

    private static final String MARKDOWN_IMAGE_LONG_OPTION = "markdown-with-images";

    private static final String REPLACE_INVALID_CHARS_LONG_OPTION = "replace-invalid-chars";

    public static Options defineOptions() {
        Options options = new Options();
        Option contentSafetyOff = new Option(null, CONTENT_SAFETY_OFF_LONG_OPTION, true, "Disables one or more content safety filters. Accepts a comma-separated list of filter names. Arguments: all, hidden-text, off-page");
        contentSafetyOff.setRequired(false);
        options.addOption(contentSafetyOff);
        Option password = new Option(PASSWORD_OPTION, PASSWORD_LONG_OPTION, true, "Specifies the password for an encrypted PDF");
        password.setRequired(false);
        options.addOption(password);
        Option htmlInMarkdown = new Option(null, HTML_IN_MARKDOWN_LONG_OPTION, false, "Sets the data extraction output format to Markdown with rendering complex elements like tables as HTML for better structure");
        htmlInMarkdown.setRequired(false);
        options.addOption(htmlInMarkdown);
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
        Option folder = new Option(FOLDER_OPTION, FOLDER_LONG_OPTION, true, "Specifies the output directory for generated files (default the folder of the input PDF)");
        folder.setRequired(false);
        options.addOption(folder);
        Option replaceInvalidChars = new Option(null, REPLACE_INVALID_CHARS_LONG_OPTION, true, "Replaces invalid or unrecognized characters (e.g., �, \\u0000) with the specified character (whitespace is used, if this parameter not specified)");
        replaceInvalidChars.setRequired(false);
        options.addOption(replaceInvalidChars);
        return options;
    }

    public static Config createConfigFromCommandLine(CommandLine commandLine) {
        Config config = new Config();
        if (commandLine.hasOption(CLIOptions.PASSWORD_OPTION)) {
            config.setPassword(commandLine.getOptionValue(CLIOptions.PASSWORD_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.CONTENT_SAFETY_OFF_LONG_OPTION)) {
            String[] argumentString = commandLine.getOptionValues(CLIOptions.CONTENT_SAFETY_OFF_LONG_OPTION);
            Set<String> arguments = Arrays.stream(String.join(",", argumentString).split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
            if (!arguments.isEmpty()) {
                if (arguments.contains(CONTENT_SAFETY_OFF_ALL_ARGUMENT)) {
                    //setting all the arguments
                    config.setFindHiddenText(true);
                    config.setOffPage(true);
                } else {
                    if (arguments.contains(CONTENT_SAFETY_OFF_HIDDEN_TEXT_ARGUMENT)) {
                        config.setFindHiddenText(true);
                    }
                    if (arguments.contains(CONTENT_SAFETY_OFF_OFF_PAGE_ARGUMENT)) {
                        config.setOffPage(true);
                    }
                }
            }
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
        if (commandLine.hasOption(CLIOptions.REPLACE_INVALID_CHARS_LONG_OPTION)) {
            config.setReplaceInvalidChars(commandLine.getOptionValue(CLIOptions.REPLACE_INVALID_CHARS_LONG_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.FOLDER_OPTION)) {
            config.setOutputFolder(commandLine.getOptionValue(CLIOptions.FOLDER_OPTION));
        } else {
            String argument = commandLine.getArgs()[0];
            File file = new File(argument);
            file = new File(file.getAbsolutePath());
            config.setOutputFolder(file.isDirectory() ? file.getAbsolutePath() : file.getParent());
        }
        return config;
    }
}

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

public class CLIOptions {

    public static final String HIDDEN_TEXT_OPTION = "ht";
    private static final String HIDDEN_TEXT_LONG_OPTION = "findhiddentext";

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
        Option findHiddenText = new Option(HIDDEN_TEXT_OPTION, HIDDEN_TEXT_LONG_OPTION, false, "Find hidden text");
        findHiddenText.setRequired(false);
        options.addOption(findHiddenText);
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
        if (commandLine.hasOption(CLIOptions.HIDDEN_TEXT_OPTION)) {
            config.setFindHiddenText(true);
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

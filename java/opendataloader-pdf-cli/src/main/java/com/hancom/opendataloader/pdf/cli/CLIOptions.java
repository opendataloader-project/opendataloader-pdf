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

    public static final String HTML_IN_MARKDOWN_OPTION = "htmlmd";
    private static final String HTML_IN_MARKDOWN_LONG_OPTION = "htmlinmarkdown";

    public static final String KEEP_LINE_BREAKS_OPTION = "klb";
    private static final String KEEP_LINE_BREAKS_LONG_OPTION = "keeplinebreaks";

    public static final String PDF_REPORT_OPTION = "pdf";
    public static final String MARKDOWN_REPORT_OPTION = "markdown";
    public static final String HTML_REPORT_OPTION = "html";

    public static final String MARKDOWN_IMAGE_OPTION = "im";
    private static final String MARKDOWN_IMAGE_LONG_OPTION = "addimagetomarkdown";

    public static final String FOLDER_OPTION = "f";
    private static final String FOLDER_LONG_OPTION = "folder";

    public static Options defineOptions() {
        Options options = new Options();
        Option findHiddenText = new Option(HIDDEN_TEXT_OPTION, HIDDEN_TEXT_LONG_OPTION, false, "Find hidden text");
        findHiddenText.setRequired(false);
        options.addOption(findHiddenText);
        Option password = new Option(PASSWORD_OPTION, PASSWORD_LONG_OPTION, true, "Specifies password");
        password.setRequired(false);
        options.addOption(password);
        Option htmlInMarkdown = new Option(HTML_IN_MARKDOWN_OPTION, HTML_IN_MARKDOWN_LONG_OPTION, false, "Use html in markdown");
        htmlInMarkdown.setRequired(false);
        options.addOption(htmlInMarkdown);
        Option keepLineBreaks = new Option(KEEP_LINE_BREAKS_OPTION, KEEP_LINE_BREAKS_LONG_OPTION, false, "keep line breaks");
        keepLineBreaks.setRequired(false);
        options.addOption(keepLineBreaks);
        Option pdfOutput = new Option(PDF_REPORT_OPTION, PDF_REPORT_OPTION, false, "Generates pdf output");
        pdfOutput.setRequired(false);
        options.addOption(pdfOutput);
        Option markdownOutput = new Option(MARKDOWN_REPORT_OPTION, MARKDOWN_REPORT_OPTION, false, "Generates markdown output");
        markdownOutput.setRequired(false);
        options.addOption(markdownOutput);
        Option generateHtml = new Option(HTML_REPORT_OPTION, HTML_REPORT_OPTION, false, "Generates html output");
        generateHtml.setRequired(false);
        options.addOption(generateHtml);
        Option imageSupport = new Option(MARKDOWN_IMAGE_OPTION, MARKDOWN_IMAGE_LONG_OPTION, false, "Add images to markdown");
        imageSupport.setRequired(false);
        options.addOption(imageSupport);
        Option folder = new Option(FOLDER_OPTION, FOLDER_LONG_OPTION, true, "Specify output folder (default the folder of the input PDF)");
        folder.setRequired(false);
        options.addOption(folder);
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
        if (commandLine.hasOption(CLIOptions.KEEP_LINE_BREAKS_OPTION)) {
            config.setKeepLineBreaks(true);
        }
        if (commandLine.hasOption(CLIOptions.PDF_REPORT_OPTION)) {
            config.setGeneratePDF(true);
        }
        if (commandLine.hasOption(CLIOptions.MARKDOWN_REPORT_OPTION)) {
            config.setGenerateMarkdown(true);
        }
        if (commandLine.hasOption(CLIOptions.HTML_REPORT_OPTION)) {
            config.setGenerateHtml(true);
        }
        if (commandLine.hasOption(CLIOptions.HTML_IN_MARKDOWN_OPTION)) {
            config.setUseHTMLInMarkdown(true);
        }
        if (commandLine.hasOption(CLIOptions.MARKDOWN_IMAGE_OPTION)) {
            config.setAddImageToMarkdown(true);
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

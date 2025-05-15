/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.utils;

import com.hancom.opendataloader.pdf.cli.CLIOptions;
import org.apache.commons.cli.CommandLine;

import java.io.File;

public class Config {
    private String password;
    private boolean findHiddenText = false;
    private boolean isGenerateMarkdown = false;
    private boolean isGeneratePDF = false;
    private boolean keepLineBreaks = false;
    private boolean isGenerateJSON = true;
    private boolean useHTMLInMarkdown = false;
    private boolean addImageToMarkdown = false;
    private String outputFolder;
    private String pythonExecutable;
    private String tatrFolder;

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
        if (commandLine.hasOption(CLIOptions.HTML_OPTION)) {
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
        if (commandLine.hasOption(CLIOptions.PYTHON_OPTION)) {
            config.setPythonExecutable(commandLine.getOptionValue(CLIOptions.PYTHON_OPTION));
        } else {
            config.setPythonExecutable("python");
        }
//        if (commandLine.hasOption((CLIOptions.TATR_OPTION))) {
//            config.setTatrFolder(commandLine.getOptionValue(CLIOptions.TATR_OPTION));
//        }
        return config;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isFindHiddenText() {
        return findHiddenText;
    }

    public void setFindHiddenText(boolean findHiddenText) {
        this.findHiddenText = findHiddenText;
    }

    public boolean isGenerateMarkdown() {
        return isGenerateMarkdown || isAddImageToMarkdown() || isUseHTMLInMarkdown();
    }

    public void setGenerateMarkdown(boolean generateMarkdown) {
        isGenerateMarkdown = generateMarkdown;
    }

    public boolean isGeneratePDF() {
        return isGeneratePDF;
    }

    public void setGeneratePDF(boolean generatePDF) {
        isGeneratePDF = generatePDF;
    }

    public boolean isKeepLineBreaks() {
        return keepLineBreaks;
    }

    public void setKeepLineBreaks(boolean keepLineBreaks) {
        this.keepLineBreaks = keepLineBreaks;
    }

    public boolean isGenerateJSON() {
        return isGenerateJSON;
    }

    public void setGenerateJSON(boolean generateJSON) {
        isGenerateJSON = generateJSON;
    }

    public boolean isUseHTMLInMarkdown() {
        return useHTMLInMarkdown;
    }

    public void setUseHTMLInMarkdown(boolean useHTMLInMarkdown) {
        this.useHTMLInMarkdown = useHTMLInMarkdown;
    }

    public boolean isAddImageToMarkdown() {
        return addImageToMarkdown;
    }

    public void setAddImageToMarkdown(boolean addImageToMarkdown) {
        this.addImageToMarkdown = addImageToMarkdown;
    }

    public String getOutputFolder() {
        return outputFolder;
    }

    public void setOutputFolder(String outputFolder) {
        this.outputFolder = outputFolder;
    }

    public String getPythonExecutable() {
        return pythonExecutable;
    }

    public void setPythonExecutable(String pythonExecutable) {
        this.pythonExecutable = pythonExecutable;
    }

    public String getTatrFolder() {
        return tatrFolder;
    }

    public void setTatrFolder(String tatrFolder) {
        this.tatrFolder = tatrFolder;
    }
}

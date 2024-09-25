package com.duallab.layout.processors;

import com.duallab.layout.cli.CLIOptions;
import org.apache.commons.cli.CommandLine;

public class Config {
    private String password;
    private boolean findHiddenText = false;
    private boolean isGenerateMarkdown = false;
    private boolean isGeneratePDF = false;
    private boolean isTextFormatted = false;
    private boolean isGenerateJSON = true;
    private boolean useHTMLInMarkdown = false;
    private boolean addImageToMarkdown = false;
    
    public static Config createConfigFromCommandLine(CommandLine commandLine) {
        Config config = new Config();
        if (commandLine.hasOption(CLIOptions.PASSWORD_OPTION)) {
            config.setPassword(commandLine.getOptionValue(CLIOptions.PASSWORD_OPTION));
        }
        if (commandLine.hasOption(CLIOptions.HIDDEN_TEXT_OPTION)) {
            config.setFindHiddenText(true);
        }
        if (commandLine.hasOption(CLIOptions.FORMATTED_TEXT_OPTION)) {
            config.setTextFormatted(true);
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
        return isGenerateMarkdown;
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

    public boolean isTextFormatted() {
        return isTextFormatted;
    }

    public void setTextFormatted(boolean textFormatted) {
        isTextFormatted = textFormatted;
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
}

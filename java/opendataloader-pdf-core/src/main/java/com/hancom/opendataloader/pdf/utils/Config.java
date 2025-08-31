/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.utils;

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
}

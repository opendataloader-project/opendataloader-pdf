package com.duallab.layout.markdown;

import com.duallab.layout.containers.StaticLayoutContainers;

import java.io.IOException;

public class MarkdownGeneratorFactory {
    public static MarkdownGenerator getMarkdownGenerator(String pdfFileName, String outputFileName,
                                                         boolean isHtmlEnabled) throws IOException {
        if (isHtmlEnabled) {
            return new MarkdownHTMLGenerator(pdfFileName, outputFileName);
        } else {
            return new MarkdownGenerator(pdfFileName, outputFileName);
        }
    }
}

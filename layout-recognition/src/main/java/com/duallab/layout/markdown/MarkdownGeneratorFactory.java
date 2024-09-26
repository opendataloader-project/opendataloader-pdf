package com.duallab.layout.markdown;

import java.io.File;
import java.io.IOException;

public class MarkdownGeneratorFactory {
    public static MarkdownGenerator getMarkdownGenerator(File inputPdf, String outputFileName,
                                                         boolean isHtmlEnabled) throws IOException {
        if (isHtmlEnabled) {
            return new MarkdownHTMLGenerator(inputPdf, outputFileName);
        } else {
            return new MarkdownGenerator(inputPdf, outputFileName);
        }
    }
}

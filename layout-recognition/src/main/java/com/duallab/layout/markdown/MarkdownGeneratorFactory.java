package com.duallab.layout.markdown;

import com.duallab.layout.processors.Config;

import java.io.File;
import java.io.IOException;

public class MarkdownGeneratorFactory {
    public static MarkdownGenerator getMarkdownGenerator(File inputPdf, String outputFileName,
                                                         Config config) throws IOException {
        if (config.isUseHTMLInMarkdown()) {
            return new MarkdownHTMLGenerator(inputPdf, outputFileName, config);
        } else {
            return new MarkdownGenerator(inputPdf, outputFileName, config);
        }
    }
}

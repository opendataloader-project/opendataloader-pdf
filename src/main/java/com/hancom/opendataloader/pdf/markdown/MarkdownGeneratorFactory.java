/*
 * Licensees holding valid commercial licenses may use this software in
 * accordance with the terms contained in a written agreement between
 * you and Dual Lab srl. Alternatively, the terms and conditions that were
 * accepted by the licensee when buying and/or downloading the
 * software do apply.
 */
package com.hancom.opendataloader.pdf.markdown;

import com.hancom.opendataloader.pdf.utils.Config;

import java.io.File;
import java.io.IOException;

public class MarkdownGeneratorFactory {
    public static MarkdownGenerator getMarkdownGenerator(File inputPdf, String outputFileName,
                                                         Config config) throws IOException {
        if (config.isUseHTMLInMarkdown()) {
            return new MarkdownHTMLGenerator(inputPdf, outputFileName, config);
        }
        return new MarkdownGenerator(inputPdf, outputFileName, config);
    }
}

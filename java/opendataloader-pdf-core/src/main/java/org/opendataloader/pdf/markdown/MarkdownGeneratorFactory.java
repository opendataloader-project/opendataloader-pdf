/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.markdown;

import org.opendataloader.pdf.api.Config;

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

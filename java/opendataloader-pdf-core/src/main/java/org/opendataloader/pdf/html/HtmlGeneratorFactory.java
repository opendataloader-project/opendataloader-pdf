/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package org.opendataloader.pdf.html;

import org.opendataloader.pdf.api.Config;

import java.io.File;
import java.io.IOException;

/**
 * Factory class for creating HtmlGenerator instances.
 */
public class HtmlGeneratorFactory {

    /**
     * Creates a new HtmlGenerator for the specified PDF file.
     *
     * @param inputPdf the input PDF file
     * @param config the configuration settings
     * @return a new HtmlGenerator instance
     * @throws IOException if unable to create the generator
     */
    public static HtmlGenerator getHtmlGenerator(File inputPdf, Config config) throws IOException {
        return new HtmlGenerator(inputPdf, config);
    }
}

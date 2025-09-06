/*
 * Copyright 2025 Hancom Inc.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.hancom.opendataloader.pdf.html;

import com.hancom.opendataloader.pdf.utils.Config;

import java.io.File;
import java.io.IOException;

public class HtmlGeneratorFactory {
    public static HtmlGenerator getHtmlGenerator(File inputPdf, String outputFolder, Config config) throws IOException {
        return new HtmlGenerator(inputPdf, outputFolder, config);
    }
}

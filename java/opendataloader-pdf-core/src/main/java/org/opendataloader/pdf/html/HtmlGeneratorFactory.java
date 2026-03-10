/*
 * Copyright 2025 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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

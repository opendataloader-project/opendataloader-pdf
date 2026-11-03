/*
 * Copyright 2025-2026 Hancom Inc.
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
package org.opendataloader.pdf.contentstream;

import org.verapdf.as.io.ASInputStream;
import org.verapdf.cos.COSObjType;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSStream;
import org.verapdf.parser.PDFStreamParser;
import org.verapdf.pd.PDContentStream;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Reads the tokens of a content stream: operands and operators in stream order.
 *
 * <p>The index of a token in the returned list is the operator index that a
 * {@code StreamInfo} and {@link org.opendataloader.pdf.autotagging.OperatorStreamKey} refer to.
 */
public final class ContentStreamTokens {

    private static final Logger LOGGER = Logger.getLogger(ContentStreamTokens.class.getName());

    private ContentStreamTokens() {
    }

    /**
     * The tokens of a page or form content stream.
     *
     * @param pdContentStream the page or form; may be null
     * @return the tokens, or an empty list when there is no stream or it cannot be read
     */
    public static List<Object> getTokens(PDContentStream pdContentStream) {
        if (pdContentStream != null) {
            try {
                COSObject contentStream = pdContentStream.getContents();
                if (contentStream.getType() == COSObjType.COS_STREAM || contentStream.getType() == COSObjType.COS_ARRAY) {
                    try (ASInputStream opStream = contentStream.getDirectBase().getData(COSStream.FilterFlags.DECODE)) {
                        try (PDFStreamParser streamParser = new PDFStreamParser(opStream)) {
                            streamParser.parseTokens();
                            return streamParser.getTokens();
                        }
                    }
                }
            } catch (IOException e) {
                LOGGER.warning("Failed to read content stream tokens: " + e.getMessage());
            }
        }
        return Collections.emptyList();
    }
}

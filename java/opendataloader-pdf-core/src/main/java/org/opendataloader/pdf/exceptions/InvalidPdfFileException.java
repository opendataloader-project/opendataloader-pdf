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
package org.opendataloader.pdf.exceptions;

import java.io.IOException;

/**
 * Thrown when an input file cannot be processed as a PDF. Two failure modes
 * share this exception type, distinguished by message wording:
 * <ul>
 *   <li><em>Missing header</em> — the {@code %PDF-} magic number is absent
 *       from the first 1024 bytes (JPEG renamed to {@code .pdf}, empty file,
 *       arbitrary text). Detected before veraPDF is invoked.</li>
 *   <li><em>Corrupted or truncated content</em> — the magic number is
 *       present but the body fails to parse (interrupted download, missing
 *       trailing xref, garbage payload). Detected when {@code new PDDocument}
 *       throws {@link IOException}; the original veraPDF exception is
 *       preserved via {@link #getCause()} for diagnostics.</li>
 * </ul>
 *
 * <p>This is a checked subtype of {@link IOException} so callers that already
 * handle {@code IOException} keep compiling, while callers that want to
 * distinguish "not a usable PDF" from other I/O failures can catch this type
 * specifically.
 *
 * <p>Public entry points that may surface this exception:
 * <ul>
 *   <li>{@code OpenDataLoaderPDF.processFile(String, Config)}</li>
 *   <li>{@code DocumentProcessor.processFile(String, Config)}</li>
 *   <li>{@code DocumentProcessor.processFileWithResult(String, Config)}</li>
 *   <li>{@code DocumentProcessor.extractContents(String, Config)}</li>
 *   <li>{@code DocumentProcessor.preprocessing(String, Config)}</li>
 *   <li>{@code AutoTagger.tag(String, Config, Float)}</li>
 * </ul>
 */
public class InvalidPdfFileException extends IOException {

    private static final long serialVersionUID = 1L;

    public InvalidPdfFileException(String message) {
        super(message);
    }

    public InvalidPdfFileException(String message, Throwable cause) {
        super(message, cause);
    }
}

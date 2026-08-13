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
 * Thrown when the JVM temporary directory is not writable.
 *
 * <p>PDF processing streams anything larger than a small in-memory threshold
 * through a temporary file — Standard 14 font metrics, embedded font programs,
 * CMaps and decoded content streams all take that path. A PDF stream has no
 * size bound while memory does, so spilling to disk is by design and cannot be
 * avoided by buffering.
 *
 * <p>This is an environment failure rather than a problem with any one input
 * file: every file in a batch would hit it. Callers processing multiple files
 * should stop rather than retry per file.
 *
 * <p>Read-only containers, restricted CI runners, sandboxes and a misconfigured
 * {@code TMPDIR} are the usual causes.
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
public class TempDirectoryNotWritableException extends IOException {

    private static final long serialVersionUID = 1L;

    public TempDirectoryNotWritableException(String message, Throwable cause) {
        super(message, cause);
    }
}
